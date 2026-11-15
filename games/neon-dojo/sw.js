// Cache offline para jugar sin red una vez instalado.
const C='neondojo-v1';
const CORE=['./','./index.html','./manifest.webmanifest','./icon.svg'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(C).then(c=>c.addAll(CORE)));self.skipWaiting();});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==C).map(x=>caches.delete(x)))));self.clients.claim();});
self.addEventListener('fetch',e=>{
  if(e.request.method!=='GET') return;
  e.respondWith(caches.match(e.request).then(r=>r||fetch(e.request).then(res=>{
    const u=e.request.url;
    if(res.ok&&(u.startsWith(self.location.origin)||u.includes('fonts.g'))){const cp=res.clone();caches.open(C).then(c=>c.put(e.request,cp));}
    return res;
  }).catch(()=>caches.match('./index.html'))));
});
