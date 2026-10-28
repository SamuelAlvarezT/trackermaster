import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
  plugins: [react()],
  build: {
    // Public browser modules resolve through the pinned Google CDN import map in index.html.
    // Avoid Firebase's large Rollup graph on low-memory developer PCs.
    // esbuild minifies the output; avoid Rollup scope analysis on this dependency graph.
    rollupOptions: { external: [/^firebase\/(app|auth|firestore)$/], treeshake: false },
  },
});
