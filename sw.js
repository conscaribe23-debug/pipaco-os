/* PipaCo OS PRO — service worker
   Sube VERSION en cada publicación para que los teléfonos muestren
   "Hay una nueva versión, toca para actualizar". */
const VERSION = '2.3.0';
const CACHE = 'pipaco-os-' + VERSION;
const CORE = [
  './',
  './index.html',
  './manifest.webmanifest',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-192.png',
  './icons/icon-maskable-512.png',
  './icons/apple-touch-icon.png',
  './icons/favicon-64.png'
];

self.addEventListener('install', (e) => {
  // No hacemos skipWaiting aquí: la página pregunta al usuario (banner).
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(CORE.map((u) => new Request(u, { cache: 'reload' })))));
});

self.addEventListener('activate', (e) => {
  e.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter((k) => k.startsWith('pipaco-os-') && k !== CACHE).map((k) => caches.delete(k)));
    await self.clients.claim();
  })());
});

self.addEventListener('message', (e) => {
  if (e.data && e.data.type === 'SKIP_WAITING') self.skipWaiting();
  if (e.data && e.data.type === 'GET_VERSION' && e.source) e.source.postMessage({ type: 'VERSION', version: VERSION });
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return; // WhatsApp, etc.: no se tocan
  if (url.pathname.includes('/demo/') || url.pathname.endsWith('/demo')) return; // la demo pública es independiente: la red la sirve tal cual

  // Páginas: la versión guardada de ESTA versión de la app (rápida y sin internet).
  if (req.mode === 'navigate') {
    e.respondWith((async () => {
      const c = await caches.open(CACHE);
      const hit = await c.match('./index.html') || await c.match('./');
      if (hit) return hit;
      try {
        const res = await fetch(req);
        if (res.ok) c.put('./index.html', res.clone());
        return res;
      } catch (err) {
        return new Response('<h1 style="font-family:system-ui">Sin conexión</h1><p>Abre PipaCo una vez con internet para guardarla en este teléfono.</p>', { status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8' } });
      }
    })());
    return;
  }

  // Archivos (íconos, manifest): primero caché, luego red y se guarda.
  e.respondWith((async () => {
    const c = await caches.open(CACHE);
    const hit = await c.match(req, { ignoreSearch: true });
    if (hit) return hit;
    try {
      const res = await fetch(req);
      if (res.ok && res.type === 'basic') c.put(req, res.clone());
      return res;
    } catch (err) {
      return new Response('', { status: 504 });
    }
  })());
});
