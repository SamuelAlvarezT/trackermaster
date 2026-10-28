import { createHash } from 'node:crypto';
import { readFileSync, mkdirSync, writeFileSync, existsSync, unlinkSync, openSync, closeSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';
import { initializeApp, deleteApp, type FirebaseApp } from 'firebase/app';
import { getAuth, signInWithEmailAndPassword } from 'firebase/auth';
import { collection, doc, getDoc, getDocs, getFirestore, limit, orderBy, query, runTransaction, serverTimestamp, terminate, Timestamp, updateDoc, where, type Firestore, type QueryConstraint } from 'firebase/firestore';
import { buildReport, compareReport, dayNumber, modelInput, pendingPeriods, localDay, addDays, monday, type Cadence, type Policy, type RecordRow, type Report } from '../src/assistant/engine.ts';

const stateDir = resolve(process.env.TRACKMASTER_STATE_DIR ?? './agent-state');
mkdirSync(stateDir, { recursive: true });
const lock = resolve(stateDir, 'process.lock');
// Local lock has a bounded lifetime so a terminated run cannot permanently stop recovery.
if (existsSync(lock) && Date.now() - Number(readFileSync(lock, 'utf8')) < 15 * 60000) { console.log('Otra revisión está en curso.'); process.exit(0); }
if (existsSync(lock)) unlinkSync(lock);
try { const fd = openSync(lock, 'wx'); writeFileSync(fd, String(Date.now())); closeSync(fd); }
catch { console.log('Otra revisión está en curso.'); process.exit(0); }
let activeApp: FirebaseApp | undefined, activeDb: Firestore | undefined;
try {
  const configPath = resolve(process.env.TRACKMASTER_CONFIG ?? './agent/config.json');
  const config = JSON.parse(readFileSync(configPath, 'utf8'));
  const secretPath = resolve(config.secretFile);
  if (process.platform !== 'win32') throw new Error('Las credenciales protegidas requieren Windows.');
  const encoded = Buffer.from(secretPath, 'utf8').toString('base64');
  const script = `[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); $p=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('${encoded}')); $s=Get-Content -LiteralPath $p -Raw | ConvertTo-SecureString; $b=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($s); try {[Runtime.InteropServices.Marshal]::PtrToStringBSTR($b)} finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b)}`;
  const secrets = JSON.parse(execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', script], { encoding: 'utf8', windowsHide: true }));
  const app = initializeApp(config.firebase);
  activeApp = app;
  const auth = getAuth(app);
  const identity = await signInWithEmailAndPassword(auth, secrets.email, secrets.password);
  if (identity.user.uid !== config.agentUid || identity.user.uid === config.ownerUid) throw new Error('Identidad de agente incorrecta.');
  const db = getFirestore(app);
  activeDb = db;
  const meta = await getDoc(doc(db, 'users', config.ownerUid, 'meta', 'sync'));
  if (!meta.exists()) throw new Error('Android todavía no ha publicado un dataset.');
  const dataset = meta.data().datasetId;
  const base = `users/${config.ownerUid}/datasets/${dataset}`;
  const fresh = Date.now() - (meta.data().lastCompletedAt?.toMillis() ?? 0) < 86400000;
  const completeThrough = addDays(localDay(new Date(meta.data().lastCompletedAt?.toMillis() ?? 0)), -1);
  if (process.argv[2] === 'health') { console.log(JSON.stringify({ connected: true, fresh, completeThrough, dataset, localDay: localDay() })); process.exitCode = fresh ? 0 : 2; }
  else {
    let periods = pendingPeriods(new Date());
    if (process.argv[2] === 'generate') {
      const cadence = process.argv[process.argv.indexOf('--cadence') + 1] as Cadence;
      const start = process.argv[process.argv.indexOf('--period') + 1];
      if (!['daily', 'weekly', 'monthly'].includes(cadence) || !/^\d{4}-\d{2}-\d{2}$/.test(start) || !Number.isFinite(Date.parse(start + 'T00:00Z')) || start > localDay()) throw new Error('Período inválido.');
      const end = cadence === 'daily' ? start : cadence === 'weekly' ? addDays(start, 6) : addDays(new Date(Date.UTC(Number(start.slice(0, 4)), Number(start.slice(5, 7)), 1)).toISOString().slice(0, 10), -1);
      if (cadence === 'weekly' && monday(start) !== start) throw new Error('La semana debe empezar en lunes.');
      const closedDay = addDays(localDay(), -1);
      if (start > closedDay) throw new Error('El período todavía no tiene días cerrados.');
      const intendedCutoff = cadence === 'weekly' ? addDays(start, 5) : end;
      periods = [{ cadence, start, end, cutoff: intendedCutoff > closedDay ? closedDay : intendedCutoff, provisional: end > closedDay,
        propose: cadence === 'weekly' && (pendingPeriods(new Date()).find(p => p.cadence === 'weekly' && p.start === start)?.propose ?? false) }];
    } else if (process.argv[2] !== 'run-due') throw new Error('Usa run-due, generate o health.');
    const lease = doc(db, base, 'agentState', 'current');
    const leased = await runTransaction(db, async tx => { const s = await tx.get(lease); if ((s.data()?.leaseUntil ?? 0) > Date.now()) return false; tx.set(lease, { leaseUntil: Date.now() + 10 * 60000, lastStartedAt: serverTimestamp(), status: 'running' }, { merge: true }); return true; });
    if (!leased) throw new Error('Otra ejecución conserva el bloqueo del dataset.');
    try {
      let reads = 0, calls = 0;
      const budgetPath = resolve(stateDir, 'budget.json');
      let budget = existsSync(budgetPath) ? JSON.parse(readFileSync(budgetPath, 'utf8')) : { day: localDay(), calls: 0 };
      if (budget.day !== localDay()) budget = { day: localDay(), calls: 0 };
      const fetchRows = async (kind: string, start?: string, end?: string, field = 'eventDay', extra: QueryConstraint[] = []): Promise<RecordRow[]> => {
        if (reads >= 1800) throw new Error('Límite local de lecturas alcanzado; recuperar en la próxima ejecución.');
        const constraints: QueryConstraint[] = [...extra, ...(start ? [where(field, '>=', field === 'eventDay' ? dayNumber(start) : start), where(field, '<=', field === 'eventDay' ? dayNumber(end!) : end!), orderBy(field)] : [])];
        const cap = Math.min(start ? 1200 : 200, 1800 - reads);
        const s = await getDocs(query(collection(db, base, kind), ...constraints, limit(cap + 1)));
        reads += s.size;
        if (s.size > cap) throw new Error('El período supera el límite de lectura; no se genera un informe truncado.');
        return s.docs.map(d => ({ ...d.data(), id: d.id, data: { ...d.data().data, recordKey: d.data().recordKey } } as RecordRow));
      };
      const habits = await fetchRows('habits'), accounts = await fetchRows('accounts');
      const unfinishedTasks = await fetchRows('tasks', undefined, undefined, 'eventDay', [where('data.completed', '==', '0')]);
      const policiesSnapshot = await getDocs(query(collection(db, base, 'habitPolicies'), limit(200)));
      const policies = Object.fromEntries(policiesSnapshot.docs.map(d => [d.id, d.data() as Policy]));
      for (const period of periods) {
        if (reads > 1650) break;
        const ref = doc(db, base, 'reports', `${period.cadence}-${period.start}`);
        const existing = await getDoc(ref); reads++;
        if (existing.exists() && existing.data().phase === 'final' && existing.data().sourceCompletedAt === meta.data().lastCompletedAt?.toMillis() && process.argv[2] !== 'generate') continue;
        const priorMonth = new Date(period.start + 'T12:00Z'); priorMonth.setUTCDate(1); priorMonth.setUTCMonth(priorMonth.getUTCMonth() - 1);
        const priorStart = period.cadence === 'monthly' ? priorMonth.toISOString().slice(0, 10) : addDays(period.start, period.cadence === 'weekly' ? -7 : -1);
        const prior = await getDoc(doc(db, base, 'reports', `${period.cadence}-${priorStart}`)); reads++;
        const logs = await fetchRows('habit_logs', period.start, period.end);
        const datedTasks = await fetchRows('tasks', period.start, period.end, 'data.plannedDate');
        const tasks = [...new Map([...unfinishedTasks, ...datedTasks].map(t => [t.id, t])).values()];
        const sessions = await fetchRows('focus_sessions', period.start, period.end);
        const transactions = await fetchRows('transactions', period.start, period.end);
        const journal = await fetchRows('journal_metrics', period.start, period.end);
        const built = buildReport({ ...period, fresh, completeThrough, habits, accounts, tasks, logs, sessions, transactions, journal, policies, previous: prior?.data()?.evidence });
        if (period.cadence === 'weekly' && !period.provisional) {
          const final = buildReport({ ...period, cutoff: period.end, fresh, completeThrough, habits, accounts, tasks, logs, sessions, transactions, journal, policies, propose: false });
          built.report.evidence = final.report.evidence;
          built.report.metrics = final.report.metrics;
          built.report.summary = final.report.summary;
          built.report.coverage = final.report.coverage;
        }
        if (prior.exists()) compareReport(built.report, prior.data() as Report);
        // The decision evidence frozen on Sunday survives Monday's finalization.
        if (existing.exists()) { built.report.proposalIds = existing.data().proposalIds; built.proposals = []; }
        const inputHash = createHash('sha256').update(JSON.stringify({ metrics: built.report.metrics, evidence: built.report.evidence, coverage: built.report.coverage, phase: built.report.phase, comparison: built.report.comparison })).digest('hex');
        if (existing.data()?.inputHash === inputHash) {
          await updateDoc(ref, { sourceCompletedAt: meta.data().lastCompletedAt?.toMillis() ?? 0 });
          continue;
        }
        if (secrets.geminiKey && budget.calls < 6 && calls < 6) {
          for (let attempt = 0; attempt < 2 && budget.calls < 6 && calls < 6; attempt++) {
            calls++; budget.calls++; writeFileSync(budgetPath, JSON.stringify(budget));
            try {
              const payload = JSON.stringify(modelInput(built.report));
              if (payload.length > 18000) break;
              const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${config.model ?? 'gemini-2.5-flash-lite'}:generateContent`, { method: 'POST', signal: AbortSignal.timeout(60000), headers: { 'Content-Type': 'application/json', 'x-goog-api-key': secrets.geminiKey }, body: JSON.stringify({ contents: [{ parts: [{ text: `Redacta en español un resumen prudente sin añadir cifras, nombres, causas ni diagnósticos. JSON con summary y actions (máximo 3 acciones concretas). No propongas modificaciones de minutos. Datos: ${payload}` }] }], generationConfig: { responseMimeType: 'application/json', maxOutputTokens: 1500 } }) });
              if (!response.ok) { if (response.status === 429) break; continue; }
              const output = await response.json() as { candidates?: { content: { parts: { text: string }[] } }[] };
              const parsed = JSON.parse(output.candidates?.[0]?.content.parts.map(p => p.text).join('') ?? '{}');
              if (typeof parsed.summary !== 'string' || parsed.summary.length > 2000 || /\d/.test(parsed.summary) || !Array.isArray(parsed.actions) || parsed.actions.length > 3 || !parsed.actions.every((a: unknown) => typeof a === 'string' && a.length <= 300 && !/\d/.test(a))) break;
              built.report.summary = parsed.summary; built.report.actions = parsed.actions; built.report.narrativeSource = 'gemini'; break;
            } catch { /* Quota/network/invalid output: preserve a working local report. */ }
          }
        }
        await runTransaction(db, async tx => {
          const current = await tx.get(ref);
          // Concurrent approval must not be lost when the Sunday report is finalized.
          if (current.exists()) built.report.proposalIds = current.data().proposalIds;
          tx.set(ref, { ...built.report, inputHash, sourceCompletedAt: meta.data().lastCompletedAt?.toMillis() ?? 0, generatedAt: serverTimestamp() });
          if (!current.exists()) for (const p of built.proposals) tx.set(doc(db, base, 'proposals', p.id), { ...p, expiresAt: Timestamp.fromDate(new Date(p.expiresOn + 'T00:00:00-05:00')) });
        });
        console.log(`Revisión guardada: ${built.report.id} (${built.report.narrativeSource}).`);
      }
      await runTransaction(db, async tx => { tx.set(lease, { leaseUntil: 0, lastCompletedAt: serverTimestamp(), status: 'ok', reads, modelCalls: calls }, { merge: true }); });
    } catch (e) { await runTransaction(db, async tx => { tx.set(lease, { leaseUntil: 0, status: 'error', lastFailedAt: serverTimestamp() }, { merge: true }); }); throw e; }
  }
} catch { console.error('El asistente no terminó. Revisa configuración, credenciales, conexión, cuota y permisos. No se cambiaron metas automáticamente.'); process.exitCode = 1; }
finally { if (activeDb) await terminate(activeDb); if (activeApp) await deleteApp(activeApp); if (existsSync(lock)) unlinkSync(lock); }
