import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));

/**
 * Allow `?raw` imports of the .xmi models that live one level up
 * (../models/*.xmi). Vite refuses to serve files outside the project root
 * unless the path is explicitly allow-listed.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    fs: {
      allow: [resolve(here, '..')],
    },
    // Forward API calls to the M2AG web server during `npm run dev`.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
