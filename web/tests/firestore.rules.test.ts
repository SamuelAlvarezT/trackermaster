import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, query, serverTimestamp, setDoc, updateDoc, where, writeBatch } from 'firebase/firestore';

let env: RulesTestEnvironment;
const key = 'a'.repeat(64); // Emulator fixture only, never a production credential.
const owner = 'personal-owner';
const path = `users/${owner}/datasets/dataset/tasks/task-one`;
const record = (revision = 1, deleted = false) => ({ schemaVersion: 1, kind: 'tasks',
  recordKey: '31', revision, deleted, eventDay: 20732, data: deleted ? {} : { id: '1', title: 'Personal', completed: '0' },
  updatedAt: serverTimestamp() });
async function mobileWrite(revision = 1, deleted = false, writer = owner, secret = key) {
  const db = env.authenticatedContext(writer).firestore();
  const token = randomUUID();
  const batch = writeBatch(db);
  batch.set(doc(db, `users/${owner}/writerProof/${token}`), { key: secret, at: serverTimestamp() });
  batch.set(doc(db, path), { ...record(revision, deleted), writeToken: token });
  return batch.commit();
}
beforeAll(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-trackmaster', firestore: {
    host: '127.0.0.1', port: Number(process.env.TRACKMASTER_TEST_PORT ?? 8085), rules: readFileSync('../firestore.rules', 'utf8'),
  }});
});
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), 'privateCloud/owner'), { uid: owner, writerKey: key });
  });
});
afterAll(async () => { await env?.cleanup(); });

describe('owner read access and Android-only writer capability', () => {
  it('accepts a full Android page within the batched rule access limits', async () => {
    const db = env.authenticatedContext(owner).firestore();
    const token = randomUUID();
    const batch = writeBatch(db);
    batch.set(doc(db, `users/${owner}/writerProof/${token}`), { key, at: serverTimestamp() });
    for (let i = 0; i < 50; i++) {
      batch.set(doc(db, `users/${owner}/datasets/dataset/tasks/task-${i}`), { ...record(i + 1), writeToken: token });
    }
    await assertSucceeds(batch.commit());
  });
  it('accepts a mobile batch, then allows reading without a connected phone', async () => {
    await assertSucceeds(mobileWrite());
    await assertSucceeds(getDoc(doc(env.authenticatedContext(owner).firestore(), path)));
    await assertFails(getDoc(doc(env.authenticatedContext('someone-else').firestore(), path)));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), path)));
  });
  it('denies writes from the web even when signed into the owner Google account', async () => {
    const db = env.authenticatedContext(owner).firestore();
    await assertFails(setDoc(doc(db, path), { ...record(), writeToken: 'missing' }));
    await assertFails(mobileWrite(1, false, owner, 'b'.repeat(64)));
    await assertFails(mobileWrite(1, false, 'someone-else'));
  });
  it('keeps the writer key and proofs unreadable, and prevents refreshing old proofs', async () => {
    await mobileWrite();
    const db = env.authenticatedContext(owner).firestore();
    await assertFails(getDoc(doc(db, 'privateCloud/owner')));
    await assertFails(getDocs(collection(db, `users/${owner}/writerProof`)));
    const snapshot = await getDoc(doc(db, path));
    const token = snapshot.data()!.writeToken;
    await assertFails(updateDoc(doc(db, `users/${owner}/writerProof/${token}`), { at: serverTimestamp() }));
    await assertFails(setDoc(doc(db, path), { ...record(2), writeToken: token }));
  });
  it('preserves deletion and rejects delayed updates with lower revisions', async () => {
    await assertSucceeds(mobileWrite(1));
    await assertSucceeds(mobileWrite(3, true));
    await assertFails(mobileWrite(2));
    const db = env.authenticatedContext(owner).firestore();
    const q = query(collection(db, `users/${owner}/datasets/dataset/tasks`), where('deleted', '==', false));
    const snapshot = await assertSucceeds(getDocs(q));
    if (snapshot.size !== 0) throw new Error('Deleted task appeared in the dashboard');
    await assertSucceeds(mobileWrite(4));
  });
  it('publishes sync metadata only with a fresh proof and fails closed without owner configuration', async () => {
    const db = env.authenticatedContext(owner).firestore();
    const token = randomUUID();
    const batch = writeBatch(db);
    batch.set(doc(db, `users/${owner}/writerProof/${token}`), { key, at: serverTimestamp() });
    batch.set(doc(db, `users/${owner}/meta/sync`), { schemaVersion: 1, datasetId: 'dataset', writeToken: token, lastCompletedAt: serverTimestamp() });
    await assertSucceeds(batch.commit());
    await assertFails(setDoc(doc(db, `users/${owner}/meta/sync`), { datasetId: 'tampered' }));
    await env.clearFirestore();
    await assertFails(mobileWrite());
  });
});
