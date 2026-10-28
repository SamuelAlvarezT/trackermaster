export const ZONE = 'America/Bogota';
export const RULES = 'fixed-step-v1';
export type Cadence = 'daily' | 'weekly' | 'monthly';
export type RecordRow = { id: string; revision: number; eventDay: number; deleted?: boolean; data: Record<string, unknown> };
export type Policy = { enabled: boolean; trackedFrom: string; targetMs: number; baseRevision: number };
export type Evidence = { habitId: string; alias: string; fromMinutes: number; completed: number; eligible: number; unknown: number; revision: number; rate: number };
export type Proposal = { id: string; reportId: string; habitId: string; recordKey: string; localHabitId: string; baseRevision: number; fromMinutes: number; toMinutes: number; status: 'pending' | 'approved' | 'rejected'; effectiveFrom: string; expiresOn: string; evidence: Evidence; rulesVersion: string };
export type Report = { id: string; cadence: Cadence; periodStart: string; periodEnd: string; phase: 'provisional' | 'final'; decisionCutoff: string; rulesVersion: string; coverage: 'complete' | 'partial'; evidence: Evidence[]; metrics: { tasks: { planned: number; completed: number; missed: number; unplanned: number; unknown?: number }; focusSeconds: number; journalEntries: number; writingDays: number; money: Record<string, { incomeMinor: string; expenseMinor: string; netMinor: string }> }; summary: string; actions: string[]; narrativeSource: 'template' | 'gemini'; proposalIds: string[]; comparison?: { previousPeriod: string; focusSecondsDelta: number; writingDaysDelta: number; habitRatePointsDelta: number | null; netDeltaByCurrency: Record<string, string> } };
export const dayNumber = (day: string) => Math.floor(Date.parse(day + 'T00:00:00Z') / 86400000);
export const addDays = (day: string, amount: number) => new Date((dayNumber(day) + amount) * 86400000).toISOString().slice(0, 10);
export function localDay(now = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONE, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}
export function monday(day: string): string { const d = new Date(day + 'T12:00Z').getUTCDay(); return addDays(day, -((d + 6) % 7)); }
const n = (value: unknown) => Number(value ?? 0);
const yes = (value: unknown) => value === true || value === '1' || value === 1;
export function due(data: Record<string, unknown>, day: string): boolean {
  if (yes(data.archived)) return false;
  const weekday = (new Date(day + 'T12:00Z').getUTCDay() + 6) % 7;
  switch (data.scheduleType) {
    case 'DAILY': case 'CUSTOM': return true;
    case 'WEEKLY': return n(data.scheduleDaysBitmask) ? (n(data.scheduleDaysBitmask) & (1 << weekday)) !== 0 : weekday === 0;
    case 'SPECIFIC_DAYS': return (n(data.scheduleDaysBitmask) & (1 << weekday)) !== 0;
    case 'MONTHLY': return day.endsWith('-01');
    case 'YEARLY': return day.endsWith('-01-01');
    default: return false;
  }
}
export function buildReport(input: { cadence: Cadence; start: string; end: string; cutoff: string; provisional?: boolean; fresh: boolean; completeThrough?: string; habits: RecordRow[]; logs: RecordRow[]; tasks: RecordRow[]; sessions: RecordRow[]; transactions: RecordRow[]; accounts: RecordRow[]; journal: RecordRow[]; policies: Record<string, Policy>; previous?: Evidence[]; propose?: boolean }): { report: Report; proposals: Proposal[] } {
  const id = `${input.cadence}-${input.start}`;
  const range = (r: RecordRow) => !r.deleted && r.eventDay >= dayNumber(input.start) && r.eventDay <= dayNumber(input.end);
  const evidence: Evidence[] = [];
  const proposals: Proposal[] = [];
  for (const [index, habit] of input.habits.filter(h => !h.deleted && !yes(h.data.archived)).entries()) {
    const policy = input.policies[habit.id];
    const timeHabit = ['TIMER', 'STOPWATCH'].includes(String(habit.data.progressType));
    let completed = 0, eligible = 0, unknown = 0;
    for (let day = input.start; day <= input.cutoff; day = addDays(day, 1)) {
      if (!due(habit.data, day)) continue;
      const logs = input.logs.filter(l => !l.deleted && String(l.data.habitId) === String(habit.data.id) && l.eventDay === dayNumber(day));
      const log = logs.sort((a, b) => b.revision - a.revision)[0];
      const target = n(log?.data.targetDurationMsAtLog);
      const tracked = policy?.enabled && day >= policy.trackedFrom && policy.targetMs === n(habit.data.targetDurationMs) && policy.baseRevision === habit.revision;
      if (!input.fresh || (input.completeThrough && day > input.completeThrough) || (timeHabit && !(target > 0) && !tracked)) { unknown++; continue; }
      eligible++;
      if (timeHabit ? n(log?.data.durationMs) >= (target || policy.targetMs) || (yes(log?.data.completed) && log?.data.completionSource === 'manual') : yes(log?.data.completed)) completed++;
    }
    const rate = eligible ? completed / eligible : 0;
    const e: Evidence = { habitId: habit.id, alias: `Hábito ${index + 1}`, fromMinutes: n(habit.data.targetDurationMs) / 60000, completed, eligible, unknown, revision: habit.revision, rate };
    evidence.push(e);
    if (input.cadence !== 'weekly' || input.propose === false || !policy?.enabled || !timeHabit || !input.fresh || eligible < 4 || unknown || policy.baseRevision !== habit.revision || policy.targetMs !== n(habit.data.targetDurationMs)) continue;
    const previous = input.previous?.find(p => p.habitId === habit.id);
    let target = e.fromMinutes;
    if (target > 60) target = 60;
    else if (rate < .6) target -= 5;
    else if (rate >= .9 && previous && previous.rate >= .9 && previous.unknown === 0 && previous.eligible >= 4 && previous.fromMinutes === e.fromMinutes && previous.revision === e.revision) target += 5;
    target = Math.max(5, Math.min(60, target));
    if (target !== e.fromMinutes) proposals.push({ id: `${id}-${habit.id}`, reportId: id, habitId: habit.id, recordKey: String(habit.data.recordKey ?? ''), localHabitId: String(habit.data.id), baseRevision: habit.revision, fromMinutes: e.fromMinutes, toMinutes: target, status: 'pending', effectiveFrom: addDays(input.start, 7), expiresOn: addDays(input.start, 14), evidence: e, rulesVersion: RULES });
  }
  const tasks = { planned: 0, completed: 0, missed: 0, unplanned: 0, unknown: 0 };
  for (const t of input.tasks.filter(t => !t.deleted)) {
    const planned = String(t.data.plannedDate ?? '');
    if (!planned) { if (!yes(t.data.completed)) tasks.unplanned++; continue; }
    if (planned < input.start || planned > input.cutoff || t.data.cancelledAtEpoch != null) continue;
    tasks.planned++;
    const finished = n(t.data.completedAtEpoch);
    if (yes(t.data.completed) && finished <= 0) tasks.unknown++;
    else if (yes(t.data.completed) && localDay(new Date(finished)) <= planned) tasks.completed++;
    else if (input.fresh && (!input.completeThrough || planned <= input.completeThrough)) tasks.missed++;
    else tasks.unknown++;
  }
  const currencies = new Map(input.accounts.filter(a => !a.deleted).map(a => [String(a.data.id), String(a.data.currencyCode)]));
  const money: Report['metrics']['money'] = {};
  for (const t of input.transactions.filter(range)) {
    if (t.data.transferId || !/^-?\d+$/.test(String(t.data.amountMinor))) continue;
    const currency = currencies.get(String(t.data.accountId));
    if (!currency || !/^[A-Z]{3}$/.test(currency)) continue;
    const item = money[currency] ??= { incomeMinor: '0', expenseMinor: '0', netMinor: '0' };
    const field = t.data.type === 'INCOME' ? 'incomeMinor' : 'expenseMinor';
    item[field] = String(BigInt(item[field]) + BigInt(String(t.data.amountMinor)));
    item.netMinor = String(BigInt(item.incomeMinor) - BigInt(item.expenseMinor));
  }
  const journal = input.journal.filter(range);
  const report: Report = { id, cadence: input.cadence, periodStart: input.start, periodEnd: input.end, phase: input.provisional ? 'provisional' : 'final', decisionCutoff: input.cutoff, rulesVersion: RULES, coverage: input.fresh && evidence.every(e => !e.unknown) ? 'complete' : 'partial', evidence, metrics: { tasks, money, focusSeconds: input.sessions.filter(range).reduce((sum, s) => sum + Math.max(0, n(s.data.actualSeconds)), 0), journalEntries: journal.length, writingDays: new Set(journal.map(j => j.eventDay)).size }, summary: `Se completaron ${evidence.reduce((s, e) => s + e.completed, 0)} de ${evidence.reduce((s, e) => s + e.eligible, 0)} compromisos de hábitos evaluables. ${tasks.completed} tareas a tiempo. ${input.fresh ? '' : 'La sincronización necesita actualizarse.'}`, actions: ['Revisar los compromisos pendientes antes de añadir nuevos.', 'Reservar un bloque de enfoque para la próxima prioridad.', 'Revisar las propuestas con su evidencia antes de decidir.'], narrativeSource: 'template', proposalIds: proposals.map(p => p.id) };
  if (input.completeThrough && input.completeThrough < input.cutoff) report.coverage = 'partial';
  if (tasks.unknown) report.coverage = 'partial';
  return { report, proposals };
}
/** Closed allowlist: never include source text, names, ids, notes or journal content. */
export function modelInput(report: Report) {
  return { cadence: report.cadence, coverage: report.coverage, habits: report.evidence.map(e => ({ alias: e.alias, targetMinutes: e.fromMinutes, completed: e.completed, eligible: e.eligible, unknown: e.unknown })), metrics: report.metrics, comparison: report.comparison };
}
export function compareReport(report: Report, previous: Report): void {
  const rate = (r: Report) => { const eligible = r.evidence.reduce((s, e) => s + e.eligible, 0); return eligible ? r.evidence.reduce((s, e) => s + e.completed, 0) / eligible : null; };
  const currentRate = rate(report), priorRate = rate(previous);
  report.comparison = { previousPeriod: previous.periodStart, focusSecondsDelta: report.metrics.focusSeconds - previous.metrics.focusSeconds,
    writingDaysDelta: report.metrics.writingDays - previous.metrics.writingDays,
    habitRatePointsDelta: report.coverage === 'complete' && previous.coverage === 'complete' && currentRate != null && priorRate != null ? Math.round((currentRate - priorRate) * 100) : null,
    netDeltaByCurrency: Object.fromEntries(Object.entries(report.metrics.money).filter(([currency]) => previous.metrics.money[currency]).map(([currency, item]) => [currency, String(BigInt(item.netMinor) - BigInt(previous.metrics.money[currency].netMinor))])) };
}
export function pendingPeriods(now: Date): { cadence: Cadence; start: string; end: string; cutoff: string; provisional: boolean; propose: boolean }[] {
  const day = localDay(now), week = monday(day);
  const hour = Number(new Intl.DateTimeFormat('en-US', { timeZone: ZONE, hour: '2-digit', hourCycle: 'h23' }).format(now));
  const periods: ReturnType<typeof pendingPeriods> = [];
  for (let i = 1; i <= 30; i++) { if (i === 1 && hour < 8) continue; const d = addDays(day, -i); periods.push({ cadence: 'daily', start: d, end: d, cutoff: d, provisional: false, propose: false }); }
  const sunday = day === addDays(week, 6);
  if (sunday && hour >= 20) periods.push({ cadence: 'weekly', start: week, end: day, cutoff: addDays(week, 5), provisional: true, propose: true });
  for (let i = 1; i <= 12; i++) { if (i === 1 && day === week && hour < 8) continue; const start = addDays(week, -7 * i); periods.push({ cadence: 'weekly', start, end: addDays(start, 6), cutoff: addDays(start, 5), provisional: false, propose: i === 1 && !(sunday && hour >= 20) }); }
  for (let i = 1; i <= 12; i++) { if (i === 1 && day.endsWith('-01') && hour < 8) continue; const date = new Date(day.slice(0, 7) + '-01T12:00Z'); date.setUTCMonth(date.getUTCMonth() - i); const start = date.toISOString().slice(0, 10); const next = new Date(date); next.setUTCMonth(next.getUTCMonth() + 1); const end = addDays(next.toISOString().slice(0, 10), -1); periods.push({ cadence: 'monthly', start, end, cutoff: end, provisional: false, propose: false }); }
  // Current reviews must run before historical catch-up can consume the read budget.
  const first = new Map<Cadence, string>();
  for (const p of periods) if (!first.has(p.cadence)) first.set(p.cadence, p.start);
  return [...periods.filter(p => first.get(p.cadence) === p.start), ...periods.filter(p => first.get(p.cadence) !== p.start)];
}
