import { defineConfig } from 'vitest/config';
export default defineConfig({ test: { environment: 'node', include: ['tests/*.rules.test.ts', 'tests/firestore.rules.test.ts'], testTimeout: 60000, hookTimeout: 60000, maxWorkers: 1 } });
