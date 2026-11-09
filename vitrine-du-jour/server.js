// Mini serveur : sert index.html et relaie /api/* vers Siamois (pas de CORS côté API, donc proxy).
// Usage : node server.js [https://siamois.mom.fr/siamois]   -> http://localhost:8099
const http = require('http'), https = require('https'), fs = require('fs'), path = require('path');
const target = new URL(process.argv[2] || process.env.SIAMOIS_URL || 'https://siamois.mom.fr/siamois');
const lib = target.protocol === 'https:' ? https : http;
http.createServer((req, res) => {
  if (req.url.startsWith('/api/')) {
    const headers = { ...req.headers, host: target.host }; delete headers.origin; delete headers.referer;
    const p = lib.request({ hostname: target.hostname, port: target.port || undefined, path: target.pathname.replace(/\/$/, '') + req.url, method: req.method, headers },
      r => { res.writeHead(r.statusCode, r.headers); r.pipe(res); });
    p.on('error', e => { res.writeHead(502); res.end(String(e)); });
    return req.pipe(p);
  }
  res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' });
  fs.createReadStream(path.join(__dirname, 'index.html')).pipe(res);
}).listen(8099, () => console.log('Vitrine sur http://localhost:8099  ->  ' + target.href));
