import DOMPurify from 'dompurify';

export interface CloudRecord {
  id: string;
  schemaVersion: number;
  kind: string;
  eventDay: number;
  deleted: boolean;
  data: Record<string, string | number | boolean | null>;
}
export const modules = [
  { id: 'habits', label: 'Hábitos', symbol: '◉', kinds: ['habits', 'habit_logs', 'achievements'] },
  { id: 'mood', label: 'Ánimo', symbol: '☀', kinds: ['mood_entries', 'mood_tags'] },
  { id: 'money', label: 'Finanzas', symbol: '↗', kinds: ['transactions', 'accounts', 'budgets', 'money_plans', 'money_plan_occurrences', 'expense_categories', 'money_spaces', 'money_pockets', 'money_transfers', 'exchange_rates', 'money_receipts'] },
  { id: 'tasks', label: 'Tareas', symbol: '✓', kinds: ['tasks'] },
  { id: 'journal', label: 'Diario', symbol: '▤', kinds: ['journal_entries', 'attachments'] },
  { id: 'focus', label: 'Enfoque', symbol: '◷', kinds: ['focus_sessions'] },
] as const;
export const labels: Record<string, string> = {
  habits: 'Hábitos', habit_logs: 'Registros de hábitos', achievements: 'Logros',
  mood_entries: 'Registros de ánimo', mood_tags: 'Etiquetas', transactions: 'Movimientos',
  accounts: 'Cuentas', budgets: 'Presupuestos', expense_categories: 'Categorías',
  money_spaces: 'Espacios', money_pockets: 'Bolsillos', money_transfers: 'Transferencias',
  exchange_rates: 'Tipos de cambio', money_receipts: 'Recibos', money_plans: 'Planes financieros',
  money_plan_occurrences: 'Historial de planes', tasks: 'Tareas', journal_entries: 'Entradas del diario',
  attachments: 'Adjuntos', focus_sessions: 'Sesiones de enfoque',
};
export const datedKinds = new Set(['habit_logs', 'mood_entries', 'transactions', 'money_plans',
  'money_plan_occurrences', 'money_receipts', 'tasks', 'journal_entries', 'focus_sessions', 'achievements']);
export const fieldLabels: Record<string, string> = {
  id: 'ID', title: 'Título', name: 'Nombre', note: 'Nota', description: 'Descripción',
  amountMinor: 'Importe en unidades menores', initialBalanceMinor: 'Saldo inicial en unidades menores',
  limitAmountMinor: 'Límite en unidades menores', currencyCode: 'Moneda', accountId: 'Cuenta',
  categoryId: 'Categoría', habitId: 'Hábito', entryId: 'Entrada', planId: 'Plan',
  level: 'Nivel de ánimo', completed: 'Completado', archived: 'Archivado', active: 'Activo',
  actualSeconds: 'Segundos de enfoque', plannedMinutes: 'Minutos previstos',
  value: 'Progreso', type: 'Tipo', status: 'Estado', frequency: 'Frecuencia',
};
export function safeJournal(html: string): string {
  return DOMPurify.sanitize(html, { ALLOWED_TAGS: ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 's', 'ul', 'ol', 'li', 'blockquote', 'h1', 'h2', 'h3', 'pre', 'code'], ALLOWED_ATTR: [] });
}
export function dateDay(date: string): number { return Math.floor(Date.parse(`${date}T00:00:00Z`) / 86400000); }
export function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
export function displayDay(day: number): string {
  return new Intl.DateTimeFormat('es', { dateStyle: 'medium', timeZone: 'UTC' }).format(new Date(day * 86400000));
}
/** Exact decimal text: do not turn SQLite Long money into a JS Number. */
export function formatMinor(value: string, currency: string): string {
  if (!/^-?\d+$/.test(value)) return `${value} ${currency}`;
  let digits = 2;
  try { digits = new Intl.NumberFormat('es', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits ?? 2; }
  catch { /* Unknown currency: retain the value and currency label. */ }
  const amount = BigInt(value);
  const abs = amount < 0n ? -amount : amount;
  const divisor = 10n ** BigInt(digits);
  const whole = (abs / divisor).toLocaleString('es');
  const fraction = digits ? `,${String(abs % divisor).padStart(digits, '0')}` : '';
  return `${amount < 0n ? '−' : ''}${whole}${fraction} ${currency}`;
}
export function recordTitle(record: CloudRecord): string {
  const d = record.data;
  return String(d.title || d.name || d.note || d.category || (d.level ? `Ánimo · ${d.level}/5` : '') || `${labels[record.kind]} · ${d.id ?? d.planId ?? ''}`);
}
