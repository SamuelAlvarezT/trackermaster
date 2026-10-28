import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

const fixture = vi.hoisted(() => ({ dataset: true }));
vi.mock('./firebase', () => ({ auth: {}, database: {}, ownerUid: 'owner', configured: true,
  baseConfigured: true, login: vi.fn(), logout: vi.fn(), discoverOwner: vi.fn() }));
vi.mock('firebase/auth', () => ({ onAuthStateChanged: (_: unknown, callback: (user: unknown) => void) => {
  callback({ uid: 'owner', displayName: 'Mi cuenta', email: 'personal@example.test' }); return () => {};
} }));
vi.mock('firebase/firestore', () => {
  const reference = (_: unknown, ...parts: string[]) => ({ path: parts.join('/') });
  const constraint = (...args: unknown[]) => args;
  return {
    doc: reference, collection: reference, query: (ref: unknown) => ref,
    documentId: () => '__name__', limit: constraint, orderBy: constraint, where: constraint, startAfter: constraint,
    onSnapshot: (ref: { path: string }, ...args: unknown[]) => {
      const callback = args.find(a => typeof a === 'function') as (data: unknown) => void;
      if (ref.path.endsWith('/meta/sync')) {
        callback({ data: () => fixture.dataset ? { schemaVersion: 1, datasetId: 'personal',
          lastCompletedAt: { toDate: () => new Date('2026-10-06T12:00:00Z') } } : undefined });
      } else {
        const kind = ref.path.split('/').at(-1)!;
        const payload = kind === 'tasks' ? { id: '1', title: 'Escribir una página', completed: '1' }
          : kind === 'journal_entries' ? { id: '1', title: 'Un buen día', richTextHtml: '<p>Contenido <strong>seguro</strong></p><script>alert(1)</script><img src="https://tracker.test">' }
          : { id: '1', name: 'Leer cada día', currencyCode: 'USD' };
        callback({ docs: [{ id: 'record-one', data: () => ({ schemaVersion: 1, kind, deleted: false,
          eventDay: 20732, data: payload }) }], metadata: { fromCache: false } });
      }
      return () => {};
    },
  };
});

let root: Root;
let container: HTMLDivElement;
beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  fixture.dataset = true;
  container = document.createElement('div'); document.body.append(container); root = createRoot(container);
});
afterEach(async () => { await act(async () => root.unmount()); container.remove(); });
async function render() { await act(async () => { root.render(<App />); }); }
async function click(element: Element) { await act(async () => { element.dispatchEvent(new MouseEvent('click', { bubbles: true })); }); }
function nav(label: string) { return [...container.querySelectorAll('nav button')].find(b => b.textContent?.includes(label))!; }

describe('read-only dashboard', () => {
  it('shows the last completed sync and navigates to tasks', async () => {
    await render();
    expect(container.textContent).toContain('Última información sincronizada');
    expect(container.textContent).toContain('Leer cada día');
    await click(nav('Tareas'));
    expect(container.querySelector('h1')?.textContent).toBe('Tareas');
    expect(container.textContent).toContain('Escribir una página');
    expect(container.textContent).toContain('Solo lectura');
  });
  it('renders journal details without executable or remote content', async () => {
    await render(); await click(nav('Diario')); await click(container.querySelector('.record-row')!);
    const dialog = container.querySelector('[role="dialog"]')!;
    expect(dialog.textContent).toContain('Un buen día');
    expect(dialog.querySelector('.journal-content strong')?.textContent).toBe('seguro');
    expect(dialog.querySelector('script,img')).toBeNull();
    await click(dialog.querySelector('[aria-label="Cerrar detalle"]')!);
    expect(container.querySelector('[role="dialog"]')).toBeNull();
  });
  it('explains initial upload rather than pretending an empty dataset is synchronized', async () => {
    fixture.dataset = false; await render();
    expect(container.textContent).toContain('Esperando el primer envío completo');
    expect(container.textContent).toContain('Conecta tu cuenta desde los ajustes de Android');
  });
});
