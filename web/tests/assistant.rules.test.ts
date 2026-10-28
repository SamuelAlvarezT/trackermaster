import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { beforeAll, beforeEach, afterAll, describe, it } from 'vitest';
import { assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, serverTimestamp, setDoc, Timestamp, updateDoc, writeBatch } from 'firebase/firestore';
let env: RulesTestEnvironment;
const owner = 'owner', agent = 'agent', base = `users/${owner}/datasets/dataset`, key = 'a'.repeat(64);
const proposal = { id: 'proposal', reportId: 'weekly', habitId: 'habit', localHabitId: '1', recordKey: '31', status: 'pending', rulesVersion: 'fixed-step-v1', fromMinutes: 40, toMinutes: 35, baseRevision: 3, effectiveFrom: '2026-10-12', expiresAt: Timestamp.fromMillis(Date.now() + 86400000) };
const command = () => ({ proposalId: 'proposal', habitId: 'habit', localHabitId: '1', recordKey: '31', expectedRevision: 3, expectedTargetMs: 2400000, targetDurationMs: 2100000, effectiveFrom: '2026-10-12', status: 'queued', createdAt: serverTimestamp() });
beforeAll(async () => { env = await initializeTestEnvironment({ projectId: 'demo-trackmaster-assistant', firestore: { host: '127.0.0.1', port: Number(process.env.TRACKMASTER_TEST_PORT ?? 8085), rules: readFileSync('../firestore.rules', 'utf8') } }); });
beforeEach(async () => { await env.clearFirestore(); await env.withSecurityRulesDisabled(async c => {
  const db = c.firestore(); await setDoc(doc(db, 'privateCloud/owner'), { uid: owner, writerKey: key }); await setDoc(doc(db, 'privateCloud/agent'), { uid: agent, ownerUid: owner });
  await setDoc(doc(db, base, 'habits', 'habit'), { revision: 3, deleted: false, data: { id: '1', targetDurationMs: '2400000' } });
  await setDoc(doc(db, base, 'proposals', 'proposal'), proposal);
}); });
afterAll(async () => { await env?.cleanup(); });
async function approve(extra: Record<string, unknown> = {}) {
  const db = env.authenticatedContext(owner).firestore(), batch = writeBatch(db);
  batch.update(doc(db, base, 'proposals', 'proposal'), { status: 'approved' });
  batch.set(doc(db, base, 'decisions', 'proposal'), { proposalId: 'proposal', decision: 'approved', decidedBy: owner, decidedAt: serverTimestamp() });
  batch.set(doc(db, base, 'commands', 'proposal'), { ...command(), ...extra }); return batch.commit();
}
describe('assistant approval boundaries', () => {
  it('accepts atomic owner approval and denies repetition', async () => { await assertSucceeds(approve()); await assertFails(approve()); });
  it('denies unauthenticated access and agent approval', async () => { await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), base, 'proposals', 'proposal'))); await assertFails(updateDoc(doc(env.authenticatedContext(agent).firestore(), base, 'proposals', 'proposal'), { status: 'approved' })); });
  it('denies forged commands and targets above cap', async () => { await assertFails(setDoc(doc(env.authenticatedContext(owner).firestore(), base, 'commands', 'proposal'), command())); await assertFails(approve({ targetDurationMs: 3660000 })); });
  it('denies stale habit revision', async () => { await env.withSecurityRulesDisabled(c => updateDoc(doc(c.firestore(), base, 'habits', 'habit'), { revision: 4 })); await assertFails(approve()); });
  it('agent can read metadata but not journal text or writer secrets', async () => { const db = env.authenticatedContext(agent).firestore(); await assertSucceeds(getDocs(collection(db, base, 'journal_metrics'))); await assertFails(getDocs(collection(db, base, 'journal_entries'))); await assertFails(getDoc(doc(db, 'privateCloud/owner'))); });
  it('only Android proof can acknowledge application', async () => { await approve(); const db = env.authenticatedContext(owner).firestore(); await assertFails(updateDoc(doc(db, base, 'commands', 'proposal'), { status: 'applied', appliedAt: serverTimestamp(), writeToken: 'missing' })); const batch = writeBatch(db), token = randomUUID(); batch.set(doc(db, 'users', owner, 'writerProof', token), { key, at: serverTimestamp() }); batch.update(doc(db, base, 'commands', 'proposal'), { status: 'applied', appliedAt: serverTimestamp(), writeToken: token }); await assertSucceeds(batch.commit()); });
});
