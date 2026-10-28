import { useEffect, useState } from 'react';
import { collection, doc, getDoc, limit, onSnapshot, orderBy, query, runTransaction, serverTimestamp, setDoc, where } from 'firebase/firestore';
import { database } from '../firebase';
import { addDays, localDay, type Policy, type Proposal, type Report } from './engine';
import { formatMinor } from '../model';

export function Review({ uid, datasetId }: { uid: string; datasetId: string }) {
  const [reports, setReports] = useState<Report[]>([]), [selected, setSelected] = useState('');
  const [cadence, setCadence] = useState('weekly');
  const [proposals, setProposals] = useState<Proposal[]>([]), [commands, setCommands] = useState<Record<string, string>>({});
  const [habits, setHabits] = useState<{ id: string; revision: number; data: Record<string, unknown> }[]>([]);
  const [policies, setPolicies] = useState<Record<string, Policy>>({});
  const [checked, setChecked] = useState<number[]>([]), [health, setHealth] = useState<Record<string, unknown>>({});
  const [error, setError] = useState(''), [busy, setBusy] = useState('');
  const base = `users/${uid}/datasets/${datasetId}`;
  const report = reports.find(r => r.id === selected) ?? reports[0];
  const fail = () => setError('No se pudo completar la operación. Revisa conexión, cuenta y permisos.');
  useEffect(() => {
    if (!database) return;
    const stops = [
      onSnapshot(doc(database, base, 'agentState', 'current'), s => setHealth(s.data() ?? {}), fail),
      onSnapshot(query(collection(database, base, 'reports'), where('cadence', '==', cadence), orderBy('periodStart', 'desc'), limit(12)), s => setReports(s.docs.map(d => d.data() as Report)), fail),
      onSnapshot(query(collection(database, base, 'habits'), limit(200)), s => setHabits(s.docs.filter(d => !d.data().deleted).map(d => ({ id: d.id, revision: d.data().revision, data: d.data().data }))), fail),
      onSnapshot(query(collection(database, base, 'habitPolicies'), limit(200)), s => setPolicies(Object.fromEntries(s.docs.map(d => [d.id, d.data() as Policy]))), fail),
    ];
    return () => stops.forEach(stop => stop());
  }, [base, cadence]);
  useEffect(() => {
    setProposals([]); setCommands({});
    if (!database || !report) return;
    const stops = report.proposalIds.map(id => onSnapshot(doc(database!, base, 'proposals', id), s => {
      if (s.exists()) setProposals(p => [...p.filter(x => x.id !== id), s.data() as Proposal]);
    }, fail));
    stops.push(onSnapshot(doc(database, base, 'actionChecks', report.id), s => setChecked(s.data()?.checked ?? []), fail));
    report.proposalIds.forEach(id => stops.push(onSnapshot(doc(database!, base, 'commands', id), s => {
      if (s.exists()) setCommands(c => ({ ...c, [id]: String(s.data().status) }));
    }, fail)));
    return () => stops.forEach(stop => stop());
  }, [base, report?.id, report?.proposalIds.join(',')]);
  async function decide(p: Proposal, decision: 'approved' | 'rejected') {
    if (!database || busy) return;
    setBusy(p.id); setError('');
    try {
      await runTransaction(database, async tx => {
        const ref = doc(database!, base, 'proposals', p.id);
        const current = await tx.get(ref);
        const habit = await tx.get(doc(database!, base, 'habits', p.habitId));
        const value = current.data() as Proposal;
        if (!current.exists() || value.status !== 'pending') throw new Error('La propuesta ya tiene una decisión.');
        if (localDay() >= value.expiresOn) throw new Error('La propuesta venció.');
        if (decision === 'approved' && (!habit.exists() || habit.data()?.deleted || habit.data()?.revision !== value.baseRevision)) throw new Error('El hábito cambió; genera una nueva propuesta.');
        const decidedAt = serverTimestamp();
        tx.set(doc(database!, base, 'decisions', p.id), { proposalId: p.id, decision, decidedBy: uid, decidedAt });
        tx.update(ref, { status: decision });
        if (decision === 'approved') tx.set(doc(database!, base, 'commands', p.id), {
          proposalId: p.id, habitId: value.habitId, localHabitId: value.localHabitId, recordKey: value.recordKey,
          expectedRevision: value.baseRevision, expectedTargetMs: value.fromMinutes * 60000,
          targetDurationMs: value.toMinutes * 60000,
          effectiveFrom: value.effectiveFrom > localDay() ? value.effectiveFrom : addDays(localDay(), 1),
          status: 'queued', createdAt: decidedAt,
        });
      });
    } catch (e) { setError(e instanceof Error && !('code' in e) ? e.message : 'No se confirmó la decisión. Revisa la conexión y los permisos.'); }
    finally { setBusy(''); }
  }
  async function toggle(habit: typeof habits[number]) {
    if (!database) return;
    try {
      // Read the current source from the server, rather than trusting an old UI revision.
      const fresh = await getDoc(doc(database, base, 'habits', habit.id));
      if (!fresh.exists() || fresh.metadata.fromCache) throw new Error('Sin conexión');
      await setDoc(doc(database, base, 'habitPolicies', habit.id), { enabled: !policies[habit.id]?.enabled, trackedFrom: localDay(), targetMs: Number(fresh.data().data.targetDurationMs), baseRevision: fresh.data().revision });
    } catch { fail(); }
  }
  function download() {
    if (!report) return;
    const bundle = { contractsVersion: 1, rulesVersion: report.rulesVersion, goal: 'TrackMaster: revisiones con aprobación humana y servicios gratuitos', period: [report.periodStart, report.periodEnd], decisions: proposals.filter(p => p.status !== 'pending').map(p => ({ kind: 'habit_target_change', decision: p.status, fromMinutes: p.fromMinutes, toMinutes: p.toMinutes })), acceptance: ['No cambios sin aprobación', 'Máximo 60 minutos', 'No enviar contenido privado a IA'] };
    const text = `# Contexto de desarrollo TrackMaster\n\n${JSON.stringify(bundle, null, 2)}\n`;
    const url = URL.createObjectURL(new Blob([text], { type: 'text/markdown' }));
    const a = document.createElement('a'); a.href = url; a.download = `${report.id}-contexto.md`; a.click(); URL.revokeObjectURL(url);
  }
  return <section className="records-panel assistant-review">
    <div className="panel-heading"><div><h2>Tu revisión</h2><p>Propuestas con evidencia. Tú decides los cambios.</p></div><button className="secondary" onClick={() => window.print()}>Imprimir</button></div>
    {error && <p role="alert" className="error">{error}</p>}
    <div className="filters"><label>Revisión<select value={cadence} onChange={e => { setCadence(e.target.value); setSelected(''); }}><option value="daily">Diaria</option><option value="weekly">Semanal</option><option value="monthly">Mensual</option></select></label><label>Reporte<select value={report?.id ?? ''} onChange={e => setSelected(e.target.value)}>{reports.map(r => <option key={r.id} value={r.id}>{r.periodStart} · {r.phase === 'provisional' ? 'Provisional' : 'Definitivo'}</option>)}</select></label><button className="secondary" disabled={!report} onClick={download}>Exportar contexto</button></div>
    {!report ? <div className="empty"><h3>Aún no hay revisiones</h3><p>Los reportes aparecerán cuando el asistente de tu PC se conecte y termine su primera revisión.</p></div> : <>
      <div className="notice">{report.periodStart} — {report.periodEnd} · {report.phase === 'provisional' ? 'Domingo todavía en curso' : 'Período cerrado'} · {report.coverage === 'partial' ? 'Datos parciales' : 'Datos completos'} · {report.narrativeSource === 'template' ? 'Resumen local' : 'Resumen con IA'}</div>
      <p>{report.summary}</p>
      {report.comparison && <p>Respecto a {report.comparison.previousPeriod}: enfoque {Math.round(report.comparison.focusSecondsDelta / 60)} min; días de escritura {report.comparison.writingDaysDelta}; cumplimiento {report.comparison.habitRatePointsDelta == null ? 'sin comparación suficiente' : `${report.comparison.habitRatePointsDelta} puntos porcentuales`}.</p>}
      <div className="assistant-metrics"><span>Enfoque: {Math.round(report.metrics.focusSeconds / 60)} min</span><span>Tareas a tiempo: {report.metrics.tasks.completed}/{report.metrics.tasks.planned}</span><span>Diario: {report.metrics.writingDays} días</span></div>
      {Object.entries(report.metrics.money).map(([currency, m]) => <p key={currency}>{currency}: ingresos {formatMinor(m.incomeMinor, currency)} · gastos {formatMinor(m.expenseMinor, currency)} · neto {formatMinor(m.netMinor, currency)}</p>)}
      <ul>{report.actions.map((a, i) => <li key={i}><label><input type="checkbox" checked={checked.includes(i)} onChange={() => {
        if (database) void setDoc(doc(database, base, 'actionChecks', report.id), { checked: checked.includes(i) ? checked.filter(x => x !== i) : [...checked, i] }).catch(fail);
      }} /> {a}</label></li>)}</ul>
      <h3>Hábitos</h3><div className="record-list">{report.evidence.map(e => <div className="record-row" key={e.habitId}><strong>{String(habits.find(h => h.id === e.habitId)?.data.name ?? e.alias)}</strong><span>{e.completed}/{e.eligible} cumplidos · {e.unknown} desconocidos</span></div>)}</div>
      <h3>Propuestas</h3>{!proposals.length && <p>No hay ajustes pendientes para este período.</p>}
      {proposals.map(p => <article className="assistant-proposal" key={p.id}>
        <h4>{String(habits.find(h => h.id === p.habitId)?.data.name ?? p.evidence.alias)}: {p.fromMinutes} → {p.toMinutes} minutos</h4>
        <p>{p.evidence.completed}/{p.evidence.eligible} días cumplidos. Vigencia propuesta: {p.effectiveFrom}.</p>
        <p aria-live="polite">{commands[p.id] === 'applied' ? 'Aplicada' : commands[p.id] === 'conflict' ? 'Conflicto: el hábito cambió' : p.status === 'approved' ? 'Aprobada — pendiente del móvil' : p.status === 'rejected' ? 'Rechazada' : localDay() >= p.expiresOn ? 'Vencida' : 'Pendiente'}</p>
        {p.status === 'pending' && localDay() < p.expiresOn && <div><button className="primary" disabled={Boolean(busy)} onClick={() => void decide(p, 'approved')}>Aprobar</button> <button className="secondary" disabled={Boolean(busy)} onClick={() => void decide(p, 'rejected')}>Rechazar</button></div>}
      </article>)}
    </>}
    <p aria-live="polite">Asistente: {health.status === 'ok' ? 'Última ejecución completada' : health.status === 'running' ? 'Revisión en curso' : health.status === 'error' ? 'La última revisión necesita atención' : 'Esperando configuración'} · Lecturas de la ejecución: {String(health.reads ?? '—')}</p>
    <details><summary>Hábitos con ajustes semanales</summary><p>Activarlos inicia el seguimiento desde hoy. Los datos anteriores sin meta verificable no se tratarán como fallos.</p>{habits.filter(h => ['TIMER', 'STOPWATCH'].includes(String(h.data.progressType))).map(h => <p key={h.id}><button className="secondary" onClick={() => void toggle(h)}>{policies[h.id]?.enabled ? 'Desactivar' : 'Activar'}</button> {String(h.data.name)}</p>)}</details>
  </section>;
}
