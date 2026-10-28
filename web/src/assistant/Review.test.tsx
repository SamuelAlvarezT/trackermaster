import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { Review } from './Review';
import { buildReport, dayNumber } from './engine';
const fixture = vi.hoisted(() => ({ report: {} as Record<string, unknown>, proposal: {} as Record<string, unknown>, revision: 3, writes: [] as { path: string; value: Record<string, unknown> }[] }));
vi.mock('../firebase', () => ({ database: {} }));
vi.mock('firebase/firestore', () => {
  const ref = (_: unknown, ...parts: string[]) => ({ path: parts.join('/') });
  const snap = (value: Record<string, unknown> | undefined) => ({ exists: () => value != null, data: () => value, metadata: { fromCache: false } });
  return { doc: ref, collection: ref, orderBy: vi.fn(), where: vi.fn(), limit: vi.fn(), query: (r: unknown) => r, serverTimestamp: () => 'timestamp',
    getDoc: async () => snap({ revision: fixture.revision, data: { targetDurationMs: '2400000' } }), setDoc: vi.fn(),
    onSnapshot: (r: { path: string }, callback: (s: unknown) => void) => {
      if (r.path.endsWith('/reports')) callback({ docs: [{ data: () => fixture.report }] });
      else if (r.path.endsWith('/habits')) callback({ docs: [{ id: 'h', data: () => ({ revision: fixture.revision, data: { name: 'Duolingo', progressType: 'TIMER' } }) }] });
      else if (r.path.endsWith('/habitPolicies')) callback({ docs: [] });
      else if (r.path.includes('/proposals/')) callback(snap(fixture.proposal));
      else callback(snap(undefined));
      return () => {};
    },
    runTransaction: async (_: unknown, body: (tx: unknown) => Promise<void>) => body({
      get: async (r: { path: string }) => r.path.includes('/habits/') ? snap({ revision: fixture.revision }) : snap(fixture.proposal),
      set: (r: { path: string }, value: Record<string, unknown>) => fixture.writes.push({ path: r.path, value }),
      update: (r: { path: string }, value: Record<string, unknown>) => fixture.writes.push({ path: r.path, value }),
    }),
  };
});
let root: Root, container: HTMLDivElement;
beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  fixture.revision = 3; fixture.writes = [];
  const built = buildReport({ cadence: 'weekly', start: '2026-10-05', end: '2026-10-11', cutoff: '2026-10-10', fresh: true,
    habits: [{ id: 'h', revision: 3, eventDay: 0, data: { id: '1', recordKey: '31', progressType: 'TIMER', scheduleType: 'DAILY', targetDurationMs: '2400000' } }],
    logs: [{ id: 'l', revision: 1, eventDay: dayNumber('2026-10-05'), data: { habitId: '1', targetDurationMsAtLog: '2400000', durationMs: '2400000' } }],
    policies: { h: { enabled: true, baseRevision: 3, trackedFrom: '2026-10-05', targetMs: 2400000 } }, tasks: [], accounts: [], sessions: [], transactions: [], journal: [] });
  fixture.report = built.report as unknown as Record<string, unknown>; fixture.proposal = { ...built.proposals[0], expiresOn: '2099-01-01' };
  container = document.createElement('div'); document.body.append(container); root = createRoot(container);
});
afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
async function render() { await act(async () => root.render(<Review uid="owner" datasetId="dataset" />)); }
async function clickApprove() { await act(async () => { [...container.querySelectorAll('button')].find(b => b.textContent === 'Aprobar')!.click(); }); }
describe('review approval', () => {
  it('shows evidence and five-minute proposal', async () => { await render(); expect(container.textContent).toContain('Duolingo: 40 → 35 minutos'); expect(container.textContent).toContain('1/6 días cumplidos'); }, 30000);
  it('writes decision and command without overwriting the mobile mirror', async () => { await render(); await clickApprove(); expect(fixture.writes.map(w => w.path.split('/').at(-2))).toEqual(['decisions', 'proposals', 'commands']); expect(fixture.writes.at(-1)?.value.targetDurationMs).toBe(2100000); });
  it('refuses approval when the habit has changed', async () => { await render(); fixture.revision = 4; await clickApprove(); expect(fixture.writes).toHaveLength(0); expect(container.textContent).toContain('El hábito cambió'); });
});
