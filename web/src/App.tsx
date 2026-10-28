import { useEffect, useMemo, useState } from 'react';
import { onAuthStateChanged, type User } from 'firebase/auth';
import { collection, doc, documentId, limit, onSnapshot, orderBy, query, startAfter, where,
  type DocumentData, type QueryConstraint, type QueryDocumentSnapshot, type Timestamp } from 'firebase/firestore';
import { auth, baseConfigured, configured, database, discoverOwner, login, logout, ownerUid } from './firebase';
import { Review } from './assistant/Review';
import { datedKinds, dateDay, displayDay, fieldLabels, formatMinor, labels, modules,
  recordTitle, safeJournal, today, type CloudRecord } from './model';

const PAGE_SIZE = 25;
type SyncMeta = { datasetId: string; lastCompletedAt?: Timestamp };
const monthStart = () => today().slice(0, 7) + '-01';
type Cursor = QueryDocumentSnapshot<DocumentData>;

function readableError(error: unknown): string {
  const code = (error as { code?: string })?.code;
  if (code?.includes('permission-denied')) return 'Acceso denegado. Comprueba la cuenta propietaria y las reglas de Firebase.';
  if (code?.includes('failed-precondition')) return 'La consulta necesita los índices de Firestore. Publica firestore.indexes.json y espera a que estén listos.';
  if (code?.includes('resource-exhausted')) return 'Se alcanzó la cuota de Firebase. La última información sincronizada se conserva.';
  if (code?.includes('unavailable')) return 'No hay conexión con Firebase. Vuelve a intentarlo cuando tengas internet.';
  if (code?.includes('popup-closed')) return 'Se cerró la ventana de inicio de sesión.';
  return error instanceof Error && !code ? error.message : 'No se pudo cargar la información. Revisa la conexión y la configuración.';
}

export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [authLoading, setAuthLoading] = useState(configured);
  const [meta, setMeta] = useState<SyncMeta | null>(null);
  const [metaLoading, setMetaLoading] = useState(false);
  const [moduleId, setModuleId] = useState('habits');
  const currentModule = modules.find(m => m.id === moduleId)!;
  const [kind, setKind] = useState<string>('habits');
  const [from, setFrom] = useState(monthStart);
  const [to, setTo] = useState(today);
  const [allDates, setAllDates] = useState(false);
  const [cursors, setCursors] = useState<Cursor[]>([]);
  const [lastCursor, setLastCursor] = useState<Cursor | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [records, setRecords] = useState<CloudRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [fromCache, setFromCache] = useState(false);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<CloudRecord | null>(null);
  const [lookups, setLookups] = useState<Record<string, Record<string, string>>>({});
  const [reload, setReload] = useState(0);
  const [discoveredUid, setDiscoveredUid] = useState('');
  const [reviewOpen, setReviewOpen] = useState(false);
  const dateError = datedKinds.has(kind) && !allDates && (!from || !to || from > to);

  useEffect(() => auth ? onAuthStateChanged(auth, u => {
    setUser(u?.uid === ownerUid ? u : null); setAuthLoading(false);
    setMeta(null); setRecords([]); setSelected(null); setLookups({}); setCursors([]);
    if (u && ownerUid && u.uid !== ownerUid) { setError('Usa la cuenta propietaria de TrackMaster.'); void logout(); }
  }) : undefined, []);

  useEffect(() => {
    if (!user || !database) return;
    setMetaLoading(true); setError('');
    return onSnapshot(doc(database, 'users', user.uid, 'meta', 'sync'), snapshot => {
      const data = snapshot.data();
      if (data && data.schemaVersion !== 1) { setError('Esta versión de datos requiere actualizar el dashboard.'); setMeta(null); }
      else setMeta(data ? data as SyncMeta : null);
      setMetaLoading(false);
    }, e => { setError(readableError(e)); setMetaLoading(false); });
  }, [user, reload]);

  const datasetId = meta?.datasetId;
  useEffect(() => { setCursors([]); setSelected(null); }, [kind, from, to, allDates, datasetId]);
  const cursor = cursors.at(-1);
  useEffect(() => {
    if (!user || !database || !datasetId || dateError) { setRecords([]); return; }
    setLoading(true); setRecords([]); setHasMore(false); setError('');
    const constraints: QueryConstraint[] = [where('deleted', '==', false)];
    if (datedKinds.has(kind) && !allDates) {
      constraints.push(where('eventDay', '>=', dateDay(from)), where('eventDay', '<=', dateDay(to)));
    }
    constraints.push(orderBy('eventDay', 'desc'), orderBy(documentId(), 'desc'));
    if (cursor) constraints.push(startAfter(cursor));
    constraints.push(limit(PAGE_SIZE + 1));
    const q = query(collection(database, 'users', user.uid, 'datasets', datasetId, kind), ...constraints);
    return onSnapshot(q, { includeMetadataChanges: true }, snapshot => {
      const page = snapshot.docs.slice(0, PAGE_SIZE);
      const valid: CloudRecord[] = [];
      page.forEach(d => {
        const record = d.data();
        if (record.schemaVersion === 1) valid.push({ ...record, id: d.id } as CloudRecord);
      });
      if (valid.length !== page.length) setError('Hay registros con una versión de datos no compatible.');
      setRecords(valid); setLastCursor(page.at(-1) ?? null);
      setHasMore(snapshot.docs.length > PAGE_SIZE); setLoading(false); setFromCache(snapshot.metadata.fromCache);
    }, e => { setLoading(false); setError(readableError(e)); });
  }, [user, datasetId, kind, from, to, allDates, cursor, dateError, reload]);

  useEffect(() => {
    if (!user || !database || !datasetId) return;
    // Small reference collections only; avoid downloading the user's entire history.
    const stops = ['accounts', 'expense_categories', 'habits', 'money_spaces', 'money_pockets'].map(table =>
      onSnapshot(query(collection(database!, 'users', user.uid, 'datasets', datasetId, table),
        where('deleted', '==', false), limit(200)), snapshot => {
        const entries = Object.fromEntries(snapshot.docs.map(d => [String(d.data().data.id), String(d.data().data.name)]));
        setLookups(previous => ({ ...previous, [table]: entries, ...(table === 'accounts' ? {
          accountsCurrency: Object.fromEntries(snapshot.docs.map(d => [String(d.data().data.id), String(d.data().data.currencyCode)]))
        } : {}) }));
      }, e => setError(readableError(e))));
    return () => stops.forEach(stop => stop());
  }, [user, datasetId, reload]);

  const lookupField = (key: string, value: unknown): string => {
    const relationTables: Record<string, string> = { accountId: 'accounts', categoryId: 'expense_categories', habitId: 'habits',
      spaceId: 'money_spaces', pocketId: 'money_pockets' };
    const table = relationTables[key];
    return table && lookups[table]?.[String(value)] ? `${lookups[table][String(value)]} (#${value})` : String(value ?? '—');
  };
  const selectedLive = useMemo(() => selected ? records.find(r => r.id === selected.id) ?? null : null, [selected, records]);
  async function connect() { setError(''); try { await login(); } catch (e) { setError(readableError(e)); } }

  if (!user) return <main className="welcome">
    <div className="welcome-brand"><span className="logo">t.</span> TrackMaster</div>
    <section className="welcome-card">
      <span className="eyebrow">TU ESPACIO EN PC</span>
      <h1>Tu día, con<br /><em>más perspectiva.</em></h1>
      <p>Hábitos, finanzas y notas en un mismo lugar. Consulta lo que sincronizaste desde tu móvil, cuando quieras.</p>
      <div className="welcome-preview"><span>◉ Hábitos</span><span>↗ Finanzas</span><span>▤ Diario</span></div>
      {configured ? <button className="primary" onClick={connect} disabled={authLoading}>{authLoading ? 'Comprobando sesión…' : 'Continuar con Google →'}</button>
        : <div className="notice">Firebase aún no está configurado. Sigue la guía <code>docs/cloud-sync/SETUP.md</code> y completa <code>web/.env.local</code>.
          {import.meta.env.DEV && baseConfigured && !ownerUid && <p><button className="secondary" onClick={async () => {
            try { setDiscoveredUid(await discoverOwner()); } catch (e) { setError(readableError(e)); }
          }}>Identificar mi cuenta de Google</button></p>}
          {discoveredUid && <p>Tu UID: <code>{discoveredUid}</code>. Cópialo a VITE_OWNER_UID y owner_uid.</p>}
        </div>}
      {error && <p role="alert" className="error">{error}</p>}
      <small>Una cuenta. Tus datos. Sin conectar el teléfono.</small>
    </section><footer>TRACKMASTER / PERSONAL DASHBOARD</footer>
  </main>;

  return <div className="app-shell">
    <aside className="sidebar">
      <a className="brand" href="#"><span className="logo">t.</span><span>TrackMaster<small>Tu espacio personal</small></span></a>
      <div className="nav-label">MI DÍA A DÍA</div>
      <nav aria-label="Módulos">{modules.map(m => <button key={m.id} className={!reviewOpen && moduleId === m.id ? 'nav-item active' : 'nav-item'}
        onClick={() => { setReviewOpen(false); setModuleId(m.id); setKind(m.kinds[0]); setCursors([]); setSelected(null); }}>
        <span>{m.symbol}</span>{m.label}<span className="nav-arrow">↗</span>
      </button>)}</nav>
      <button className={reviewOpen ? 'nav-item active' : 'nav-item'} onClick={() => setReviewOpen(v => !v)}>✦ Asistente personal</button>
      <div className="sidebar-note"><span>☁</span><strong>Del móvil a tu escritorio</strong><p>Esta vista consulta los datos enviados a la nube. Los archivos permanecen en tu móvil.</p></div>
      <div className="account"><span className="avatar">{(user.displayName || user.email || 'T')[0].toUpperCase()}</span><div><strong>{user.displayName || 'Mi cuenta'}</strong><small>Vista de consulta</small></div><button aria-label="Cerrar sesión" title="Cerrar sesión" onClick={() => void logout()}>↪</button></div>
    </aside>
    <main className="workspace">
      <header className="topbar"><span>MI ESPACIO <span className="divider">/</span> {reviewOpen ? 'ASISTENTE' : currentModule.label.toUpperCase()}</span><span className="readonly">{reviewOpen ? '✦ Tú apruebas los cambios' : '◌ Solo lectura'}</span></header>
      <div className="page-heading"><div><span className="eyebrow">UN POCO DE PERSPECTIVA</span><h1>{reviewOpen ? 'Tu asistente personal' : currentModule.label}</h1><p>{reviewOpen ? 'Revisa tu progreso y decide tus próximos ajustes.' : 'Lo que has registrado, a tu ritmo.'}</p></div><button className="secondary" onClick={() => setReload(v => v + 1)}>↻ Actualizar</button></div>
      <section className="sync-banner" aria-label="Estado de sincronización"><span className="sync-dot" /><div><strong>{fromCache ? 'Información en caché del navegador' : 'Última información sincronizada'}</strong><span>{meta?.lastCompletedAt ? meta.lastCompletedAt.toDate().toLocaleString('es') : 'Esperando el primer envío completo desde Android'}</span></div><span className="cloud-icon">☁</span></section>
      {reviewOpen && datasetId ? <Review uid={user.uid} datasetId={datasetId} /> : <section className="records-panel">
        <div className="panel-heading"><div><h2>Tu actividad</h2><p>Explora tus registros y abre uno para ver sus detalles.</p></div><span className="count-badge">{records.length} en esta página</span></div>
        <div className="filters"><label>Ver<select value={kind} onChange={e => { setKind(e.target.value); setCursors([]); }}>{currentModule.kinds.map(k => <option key={k} value={k}>{labels[k]}</option>)}</select></label>
          {datedKinds.has(kind) && <><label>Desde<input type="date" value={from} disabled={allDates} onChange={e => { setFrom(e.target.value); setCursors([]); }} /></label><label>Hasta<input type="date" value={to} disabled={allDates} onChange={e => { setTo(e.target.value); setCursors([]); }} /></label><label className="check"><input type="checkbox" checked={allDates} onChange={e => { setAllDates(e.target.checked); setCursors([]); }} /> Todo el historial</label></>}
        </div>
        {dateError && <p className="error" role="alert">Selecciona un intervalo de fechas válido.</p>}
        {error && <p className="error" role="alert">{error}</p>}
        {loading || metaLoading ? <div className="empty">Cargando tus registros…</div> : !datasetId ? <div className="empty"><span>☁</span><h3>Tu espacio está listo</h3><p>Conecta tu cuenta desde los ajustes de Android. Tus datos aparecerán cuando termine el primer envío.</p></div>
          : !records.length ? <div className="empty"><span>◌</span><h3>Un espacio para lo que viene</h3><p>No hay registros en esta vista. Prueba otro período o vuelve después de sincronizar el móvil.</p></div>
          : <div className="record-list">{records.map(record => <button className="record-row" key={record.id} onClick={() => setSelected(record)}>
            <span className="record-symbol">{currentModule.symbol}</span><span className="record-text"><strong>{recordTitle(record)}</strong><small>{datedKinds.has(kind) ? displayDay(record.eventDay) : labels[kind]}{record.data.accountId ? ` · ${lookupField('accountId', record.data.accountId)}` : ''}</small></span>
            <span className="record-value">{record.data.amountMinor != null ? formatMinor(String(record.data.amountMinor), String(record.data.currencyCode || lookups.accountsCurrency?.[String(record.data.accountId)] || '')) : record.data.completed === '1' ? 'Completado' : ''}</span><span className="row-arrow">↗</span>
          </button>)}</div>}
        <div className="pagination"><span>Página {cursors.length + 1} · Hasta {PAGE_SIZE} registros</span><div><button className="secondary" disabled={!cursors.length || loading} onClick={() => setCursors(c => c.slice(0, -1))}>← Anterior</button><button className="secondary" disabled={!hasMore || loading || !lastCursor} onClick={() => lastCursor && setCursors(c => [...c, lastCursor])}>Siguiente →</button></div></div>
      </section>}
      <footer className="page-footer">Hecho para llevar tu día con intención.<span>TRACKMASTER</span></footer>
    </main>
    {selectedLive && <div className="modal-backdrop" onClick={() => setSelected(null)}><section role="dialog" aria-modal="true" aria-label="Detalle del registro" className="detail" onClick={e => e.stopPropagation()}>
      <button className="close" onClick={() => setSelected(null)} aria-label="Cerrar detalle">×</button><span className="eyebrow">{labels[selectedLive.kind]}</span><h2>{recordTitle(selectedLive)}</h2>
      {selectedLive.data.richTextHtml && <div className="journal-content" dangerouslySetInnerHTML={{ __html: safeJournal(String(selectedLive.data.richTextHtml)) }} />}
      <dl>{Object.entries(selectedLive.data).filter(([key]) => key !== 'richTextHtml').map(([key, value]) => <div key={key}><dt>{fieldLabels[key] || key}</dt><dd>{key.endsWith('LocalOnly') ? (value ? 'Disponible solo en el móvil' : 'Sin archivo') : lookupField(key, value)}</dd></div>)}</dl>
    </section></div>}
  </div>;
}
