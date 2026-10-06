import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import {defineConfig} from 'vite';

export default defineConfig(() => {
  return {
    plugins: [
      react(),
      tailwindcss(),
      {
        name: 'kill-stale-pwa-sw',
        configureServer(server) {
          server.middlewares.use((req, res, next) => {
            if (req.url && (req.url.startsWith('/dev-sw.js') || req.url.startsWith('/sw.js'))) {
              res.setHeader('Content-Type', 'application/javascript');
              res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate');
              res.end(`
                self.addEventListener('install', () => self.skipWaiting());
                self.addEventListener('activate', (e) => {
                  e.waitUntil((async () => {
                    if ('caches' in self) {
                      const keys = await caches.keys();
                      await Promise.all(keys.map(k => caches.delete(k)));
                    }
                    await self.registration.unregister();
                    const list = await self.clients.matchAll({ type: 'window' });
                    for (const c of list) c.navigate(c.url);
                  })());
                });
              `);
              return;
            }
            next();
          });
        },
      },
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.'),
      },
    },
    server: {
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modify—file watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== 'true',
      // Disable file watching when DISABLE_HMR is true to save CPU during agent edits.
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
    },
  };
});
