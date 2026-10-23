// Usage: node shot.mjs <input.html> <output.png> [light|dark] [width] [height]
// Renders a local HTML file at phone size (default 412x915 CSS px, 2x scale) and saves a PNG.
// Playwright is loaded from $PLAYWRIGHT_DIR (a folder with node_modules/playwright),
// falling back to /opt/node-tools.
import { createRequire } from 'module';
import path from 'path';
import { pathToFileURL } from 'url';
const base = process.env.PLAYWRIGHT_DIR || '/opt/node-tools';
const require = createRequire(path.join(base, 'noop.js'));
const { chromium } = require('playwright');

const [, , input, output, scheme = 'light', w = '412', h = '915'] = process.argv;
if (!input || !output) { console.error('usage: node shot.mjs in.html out.png [light|dark] [w] [h]'); process.exit(1); }
const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' }).catch(() => chromium.launch());
const page = await browser.newPage({ viewport: { width: +w, height: +h }, deviceScaleFactor: 2, colorScheme: scheme });
await page.goto(pathToFileURL(path.resolve(input)).href);
await page.waitForTimeout(300);
await page.screenshot({ path: output, fullPage: true });
await browser.close();
console.log('saved', output);
