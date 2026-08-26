import fs from 'node:fs';
import vm from 'node:vm';

const fail = [];
const pass = [];
const need = ['index.html','styles.css','app.js','sw.js','manifest.webmanifest','logo-splash.png','icons/icon-192.png','icons/icon-512.png','icons/icon-maskable-512.png'];
for (const f of need) {
  if (!fs.existsSync(f) || fs.statSync(f).size === 0) fail.push(`arquivo ausente/vazio: ${f}`);
}
if (!fail.length) pass.push('arquivos obrigatórios presentes');

const manifest = JSON.parse(fs.readFileSync('manifest.webmanifest','utf8'));
if (manifest.name !== 'Elo Bar' || manifest.short_name !== 'Elo Bar') fail.push('manifest deve manter nome Elo Bar');
if (manifest.display !== 'standalone') fail.push('manifest deve usar display=standalone');
if (manifest.background_color !== '#121212' || manifest.theme_color !== '#121212') fail.push('manifest deve usar #121212');
if (manifest.orientation !== 'portrait-primary') fail.push('manifest deve priorizar portrait-primary');

function pngSize(file) {
  const b = fs.readFileSync(file);
  if (b.length < 24 || b.toString('hex',0,8) !== '89504e470d0a1a0a') return null;
  return { w:b.readUInt32BE(16), h:b.readUInt32BE(20) };
}
for (const [file,w,h] of [['icons/icon-192.png',192,192],['icons/icon-512.png',512,512],['icons/icon-maskable-512.png',512,512]]) {
  const s = pngSize(file);
  if (!s || s.w !== w || s.h !== h) fail.push(`${file} deve ser ${w}x${h}`);
}
if (!fail.some(x => x.includes('deve ser'))) pass.push('dimensões dos ícones validadas');

const html = fs.readFileSync('index.html','utf8');
const app = fs.readFileSync('app.js','utf8');
const sw = fs.readFileSync('sw.js','utf8');
for (const [file,src] of [['app.js',app],['sw.js',sw]]) {
  try { new vm.Script(src,{filename:file}); pass.push(`sintaxe JS: ${file}`); }
  catch (e) { fail.push(`sintaxe inválida em ${file}: ${e.message}`); }
}
const prod = 'https://script.google.com/macros/s/AKfycbyd7UHyQFJA4SsFZuKWmAO___NnfGXq0oNB0M0NWnG2hhLmPHcKTL_ck4yDgB4IqSkOnQ/exec';
if (!app.includes(prod)) fail.push('app.js não aponta para o /exec oficial');
if (!app.includes("const SHELL_VERSION='1.0.0'")) fail.push('shell version 1.0.0 ausente');
if (/location\.reload\s*\(/.test(app)) fail.push('casca não pode usar location.reload()');
if (!app.includes('FRAME_LOAD_TIMEOUT_MS') || !app.includes("loadFrame('manual-retry')")) fail.push('timeout/retry ausente');
if (!app.includes('RESUME_REFRESH_MS') || !app.includes('ELO_SHELL_REFRESH_DATA')) fail.push('refresh de retomada ausente');
if (!html.includes('interactive-widget=resizes-content')) fail.push('tratamento de teclado ausente');
if (!html.includes('frame-src https://script.google.com https://*.googleusercontent.com')) fail.push('CSP do iframe oficial ausente');
if (!html.includes('theme-color" content="#121212"')) fail.push('theme #121212 ausente');
if (!sw.includes("key.startsWith('elo-bar-shell-')") || !sw.includes('caches.delete')) fail.push('limpeza de cache versionado ausente');
if (sw.includes('script.google.com/macros')) fail.push('service worker não deve conhecer/cachear o Apps Script');
if (!sw.includes('if(url.origin!==self.location.origin)return')) fail.push('service worker deve ignorar cross-origin');

for (const p of pass) console.log(`✓ ${p}`);
if (fail.length) {
  for (const e of fail) console.error(`✗ ${e}`);
  console.error(`\nPWA_CHECK_FAIL: ${fail.length} erro(s)`);
  process.exit(1);
}
console.log('\nPWA_CHECK_OK');
