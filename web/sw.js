/*
 * 서비스 워커 — 앱 셸만 캐시한다 (오프라인에서도 열리게).
 * 지도 타일·폰트는 외부 도메인이라 캐시하지 않고 네트워크에 맡긴다.
 * 기록 데이터는 localStorage에 있으므로 이 캐시와 무관하다.
 */
var CACHE = 'eottadwotji-shell-v2'; // 셸 파일이 바뀌면 올린다 (구버전 캐시는 activate에서 지운다)
var SHELL = [
  './',
  './index.html',
  './manifest.webmanifest',
  './icon-192.png',
  './icon-512.png',
  './apple-touch-icon.png',
  './car.jpg'
];

self.addEventListener('install', function (e) {
  e.waitUntil(
    caches.open(CACHE).then(function (c) { return c.addAll(SHELL); })
      .then(function () { return self.skipWaiting(); })
  );
});

self.addEventListener('activate', function (e) {
  e.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.map(function (k) {
        return k === CACHE ? null : caches.delete(k);
      }));
    }).then(function () { return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function (e) {
  var req = e.request;
  if (req.method !== 'GET') return;
  var sameOrigin = new URL(req.url).origin === self.location.origin;
  if (!sameOrigin) return; // 타일·폰트는 브라우저 기본 캐시에 맡긴다

  // 앱 셸은 네트워크 우선 + 실패 시 캐시 (업데이트가 바로 반영되게)
  e.respondWith(
    fetch(req).then(function (res) {
      var copy = res.clone();
      caches.open(CACHE).then(function (c) { c.put(req, copy); });
      return res;
    }).catch(function () {
      return caches.match(req).then(function (hit) {
        return hit || caches.match('./index.html');
      });
    })
  );
});
