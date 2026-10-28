import { initializeApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider, signInWithPopup, signOut } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';

const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};
export const ownerUid = import.meta.env.VITE_OWNER_UID ?? '';
export const baseConfigured = Object.values(config).every(Boolean);
export const configured = baseConfigured && Boolean(ownerUid);
const app = baseConfigured ? initializeApp(config) : null;
export const auth = app ? getAuth(app) : null;
export const database = app ? getFirestore(app) : null;
export async function login() {
  if (!auth) throw new Error('Configura Firebase antes de iniciar sesión.');
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });
  const result = await signInWithPopup(auth, provider);
  if (result.user.uid !== ownerUid) {
    await signOut(auth);
    throw new Error('Usa la cuenta propietaria de TrackMaster.');
  }
}
export async function logout() { if (auth) await signOut(auth); }
// Local setup only: discover the UID without granting access to any Firestore data.
export async function discoverOwner(): Promise<string> {
  if (!import.meta.env.DEV || !auth) throw new Error('Disponible solo durante la configuración local.');
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: 'select_account' });
  const result = await signInWithPopup(auth, provider);
  const uid = result.user.uid;
  await signOut(auth);
  return uid;
}
