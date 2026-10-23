// Usage: node measure.mjs <html> <css selector>  -> prints widths and boxes of matching elements
import { createRequire } from 'module';
import path from 'path';
const require = createRequire('/opt/node-tools/node_modules/');
const { chromium } = require('playwright');
const [, , input, sel] = process.argv;
const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' }).catch(() => chromium.launch());
const page = await browser.newPage({ viewport: { width: 412, height: 915 } });
await page.goto('file://' + path.resolve(input));
await page.waitForTimeout(300);
console.log(JSON.stringify(await page.$$eval(sel, els => els.map(e => { const r = e.getBoundingClientRect(); return [e.textContent.trim().slice(0, 20), Math.round(r.x), Math.round(r.y), Math.round(r.width), Math.round(r.height)]; }))));
await browser.close();
