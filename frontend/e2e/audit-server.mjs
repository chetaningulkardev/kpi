import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { resolve, extname, sep } from 'node:path';

const root = resolve('dist/frontend/browser');
const types = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon' };
createServer(async (req, res) => {
  const pathname = new URL(req.url, 'http://localhost').pathname;
  // This runtime never proxies to a real API/database.
  if (pathname.startsWith('/api/')) { res.writeHead(503); res.end('API must be mocked'); return; }
  const file = resolve(root, `.${decodeURIComponent(pathname)}`);
  if (file !== root && !file.startsWith(root + sep)) { res.writeHead(403); res.end(); return; }
  try {
    const content = await readFile(extname(file) ? file : resolve(root, 'index.html'));
    res.writeHead(200, { 'Content-Type': types[extname(file)] || (extname(file) ? 'application/octet-stream' : 'text/html') });
    res.end(content);
  } catch { res.writeHead(404); res.end(); }
}).listen(4300, '127.0.0.1');
