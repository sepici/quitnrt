// Builds tokens.json, tokens.css and pixel SVGs for each design direction.
// Usage: node build.mjs
import fs from 'fs';
import path from 'path';

// Paths are relative to this file: docs/design/tools/build.mjs writes into docs/design/directions.
// Fonts aren't kept in the repo; unpack the @fontsource packages into FONTSRC to rebuild them.
const HERE = path.dirname(new URL(import.meta.url).pathname);
const OUT = path.join(HERE, '..', 'directions');
const FONTSRC = process.env.FONTSRC || path.join(HERE, '..', 'fonts-src');
fs.mkdirSync(path.join(OUT, 'fonts'), { recursive: true });

// ---------- WCAG contrast ----------
function lum(hex) {
  const h = hex.replace('#', '');
  const c = [0, 2, 4].map(i => parseInt(h.slice(i, i + 2), 16) / 255)
    .map(v => (v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4)));
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
}
function ratio(a, b) {
  const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
}
const MIN = { text: 4.5, large: 3, ui: 3 };

// ---------- directions ----------
const directions = {
  hearth: {
    name: 'Hearth',
    notes: 'Accent (ember) is for the one primary action per screen, meter fills and the active tab. Pixel font Jersey 10 sets the hero number, HP, hits and game badges; everything else is Bricolage Grotesque. Game elements (HP blocks, badges, stage) are square with pixel-notched corners; app elements use the radius scale. Pixelify Sans was rejected because its 5 reads as S.',
    summary: 'Warm paper, cocoa ink and one ember accent. Soft cards, a ring for the daily guide, chunky pixel numbers.',
    font: {
      body: { family: 'Bricolage Grotesque', pkg: 'bricolage-grotesque', weights: ['400-normal', '500-normal', '600-normal', '700-normal', '800-normal'] },
      pixel: { family: 'Jersey 10', pkg: 'jersey-10', weights: ['400-normal'] },
    },
    type: {
      display: { font: 'pixel', size: 64, line: 56, use: 'hero number in the daily guide ring' },
      headline: { font: 'body', size: 26, line: 32, weight: 700, tracking: -0.3 },
      title: { font: 'body', size: 17, line: 24, weight: 600 },
      body: { font: 'body', size: 15, line: 22, weight: 400 },
      label: { font: 'body', size: 13, line: 18, weight: 600 },
      caption: { font: 'body', size: 12, line: 16, weight: 500 },
      pixelNumber: { font: 'pixel', size: 30, line: 28, use: 'HP, hits, game numbers' },
    },
    radius: { sm: 6, md: 12, lg: 18, game: 0, note: 'Cards 18, controls and tiles 12, tags 6. Game elements are square.' },
    space: [4, 8, 12, 16, 20, 24, 32, 48],
    sprite: { o: '#2A1610', d: '#6E2415', m: '#B8472D', l: '#E37D4E', b: '#F2C68E', a: '#D99A2E', e: '#FFE45E', h: '#FFF4E2' },
    colors: {
      light: {
        bg: '#F4EDE3', surface: '#FFFAF3', surface2: '#EFE5D7', line: '#E3D6C4', lineStrong: '#9A846D',
        text: '#2B2119', text2: '#66533F', accent: '#B3441D', accentText: '#A33D18', onAccent: '#FFF8F0',
        accentSoft: '#F6DECC', accentInk: '#7A2C10', track: '#F1DCCB',
        disabledBg: '#EAE1D4', disabledText: '#6B5B4B',
        hpFull: '#B8472D', hpEmpty: '#EADBC8', hpOutline: '#2A1610', stripBg: '#F7E9D9',
        stageSky: '#EBD8BF', stageText: '#2B2119',
      },
      dark: {
        bg: '#1A1411', surface: '#251D18', surface2: '#30261F', line: '#3B2F27', lineStrong: '#85705E',
        text: '#F4EADD', text2: '#C6B4A0', accent: '#EE8A5C', accentText: '#F29C73', onAccent: '#2A120A',
        accentSoft: '#4A2A1C', accentInk: '#F8C9AE', track: '#3F2C21',
        disabledBg: '#2F2620', disabledText: '#AC9C8C',
        hpFull: '#E0784A', hpEmpty: '#3A2C23', hpOutline: '#0F0A08', stripBg: '#2B201A',
        stageSky: '#241A15', stageText: '#F4EADD',
      },
    },
    pairs: [
      ['text', 'bg', 'text', 'headings and body on page'],
      ['text', 'surface', 'text', 'body on cards'],
      ['text', 'surface2', 'text', 'quick-log tile labels'],
      ['text2', 'bg', 'text', 'secondary text on page'],
      ['text2', 'surface', 'text', 'secondary text on cards'],
      ['text2', 'surface2', 'text', 'tile product names'],
      ['accentText', 'surface', 'text', 'text buttons on cards'],
      ['accentText', 'bg', 'text', 'Edit link on page'],
      ['onAccent', 'accent', 'text', 'Log and Hit button labels'],
      ['accentInk', 'accentSoft', 'text', 'tags and the 1 hit ready badge'],
      ['disabledText', 'disabledBg', 'text', '5x attack label and reason (informational, so held to 4.5)'],
      ['text', 'stripBg', 'text', 'demon strip text'],
      ['text2', 'stripBg', 'text', 'demon strip HP numbers'],
      ['stageText', 'stageSky', 'text', 'flavour line on backdrop'],
      ['accent', 'surface', 'ui', 'ring and week bar fill against card'],
      ['accent', 'track', 'ui', 'meter fill against its track'],
      ['lineStrong', 'surface', 'ui', 'control outlines'],
      ['hpFull', 'stripBg', 'ui', 'full HP block against strip'],
      ['hpFull', 'hpEmpty', 'ui', 'full HP block against empty block'],
      ['text', 'surface', 'ui', 'active nav indicator'],
    ],
  },

  signal: {
    name: 'Signal',
    notes: 'No cards: sections are split by a 2 px ink rule (major) or a 1 px hairline. Accent (cyan) fills the Log and Hit buttons and the meters; the pale highlight sits behind ink text only. Hero numbers stay in Atkinson Hyperlegible Next. Silkscreen is caps-only, so it is used for HUD numbers and HP labels at 16 or 24 px, never for sentences or names. The game screen is dark in both themes.',
    summary: 'Cool ink on white with a cyan signal colour. Ruled sections, square corners, a segmented guide that mirrors the HP bar.',
    font: {
      body: { family: 'Atkinson Hyperlegible Next', pkg: 'atkinson-hyperlegible-next', weights: ['400-normal', '500-normal', '700-normal', '800-normal'] },
      pixel: { family: 'Silkscreen', pkg: 'silkscreen', weights: ['400-normal', '700-normal'] },
    },
    type: {
      display: { font: 'body', size: 72, line: 64, weight: 800, tracking: -2, use: 'hero number, body font on purpose' },
      headline: { font: 'body', size: 28, line: 32, weight: 800, tracking: -0.5 },
      title: { font: 'body', size: 16, line: 20, weight: 700 },
      body: { font: 'body', size: 16, line: 24, weight: 400 },
      label: { font: 'body', size: 13, line: 16, weight: 700, tracking: 0.2 },
      caption: { font: 'body', size: 13, line: 18, weight: 400 },
      pixelNumber: { font: 'pixel', size: 16, line: 16, use: 'HUD numbers, multiples of 8 px only' },
    },
    radius: { sm: 2, md: 2, lg: 2, game: 0, note: 'One radius, 2 px, everywhere. Sections are separated by rules, not cards.' },
    space: [4, 8, 12, 16, 20, 24, 32, 40],
    sprite: { o: '#0B0F16', d: '#23425A', m: '#35698A', l: '#64A8C2', b: '#C2D7DF', a: '#E8EEF2', e: '#7FF0FF', h: '#FFFFFF' },
    colors: {
      light: {
        bg: '#FFFFFF', surface: '#FFFFFF', surface2: '#F1F3F6', line: '#DCE1E7', lineStrong: '#0E1217',
        text: '#0E1217', text2: '#48515D', accent: '#006B85', accentText: '#00627A', onAccent: '#FFFFFF',
        highlight: '#8FE6F2', highlightInk: '#0E1217', track: '#DFE5EB',
        disabledBg: '#EEF1F4', disabledText: '#555E6B', disabledLine: '#7A8592',
        hud: '#0E1217', hudText: '#FFFFFF', hudText2: '#AAB4C1', hpFull: '#5AD2E6', hpEmpty: '#28313C',
        stage: '#0B1220', stageText: '#E9F1F5', dialogBg: '#0B0F16',
      },
      dark: {
        bg: '#0A0D11', surface: '#0A0D11', surface2: '#151A21', line: '#262D37', lineStrong: '#E9EEF3',
        text: '#F1F4F7', text2: '#A4AEBB', accent: '#5AD2E6', accentText: '#6FDAEB', onAccent: '#03151A',
        highlight: '#5AD2E6', highlightInk: '#03151A', track: '#232B35',
        disabledBg: '#151A21', disabledText: '#929DAB', disabledLine: '#66717F',
        hud: '#141A22', hudText: '#F1F4F7', hudText2: '#A4AEBB', hpFull: '#5AD2E6', hpEmpty: '#2A3340',
        stage: '#0B1220', stageText: '#E9F1F5', dialogBg: '#0B0F16',
      },
    },
    pairs: [
      ['text', 'bg', 'text', 'all primary text'],
      ['text', 'surface2', 'text', 'text on tinted rows'],
      ['text2', 'bg', 'text', 'secondary text'],
      ['text2', 'surface2', 'text', 'secondary text on tinted rows'],
      ['accentText', 'bg', 'text', 'text links'],
      ['onAccent', 'accent', 'text', 'Log and Hit button labels'],
      ['highlightInk', 'highlight', 'text', '1 hit ready marker'],
      ['disabledText', 'disabledBg', 'text', '5x attack label and reason (informational, so held to 4.5)'],
      ['hudText', 'hud', 'text', 'demon band name'],
      ['hudText2', 'hud', 'text', 'demon band numbers'],
      ['stageText', 'stage', 'text', 'HUD name and numbers on the game screen'],
      ['stageText', 'dialogBg', 'text', 'flavour line in the dialogue box'],
      ['highlightInk', 'highlight', 'text', 'nav badge count'],
      ['accent', 'bg', 'ui', 'segment fill against page'],
      ['accent', 'track', 'ui', 'filled segment against empty segment'],
      ['lineStrong', 'bg', 'ui', 'section rules and chip outlines'],
      ['disabledLine', 'disabledBg', 'ui', 'dashed outline of the disabled 5x attack button'],
      ['hpFull', 'hud', 'ui', 'HP block against band'],
      ['hpFull', 'hpEmpty', 'ui', 'full HP block against empty block'],
      ['hpFull', 'stage', 'ui', 'HP block against game screen'],
    ],
  },

  fieldbook: {
    name: 'Fieldbook',
    notes: 'Labels are Literata italic; headings Literata semibold; hero numbers Literata so the pixel font stays in the game layer. VT323 sets HP, banked hits and game badges at 20 to 36 px (it shows notches above about 60 px). Accent (pine) is for Log, Hit, meter fills, links and the active tab. Gary sits in a specimen card on Today and a framed plate in the lair.',
    summary: 'Pale sage paper, a pine accent and a screen serif. A naturalist notebook, with Gary filed as a specimen.',
    font: {
      body: { family: 'Literata', pkg: 'literata', weights: ['400-normal', '400-italic', '500-normal', '600-normal', '700-normal'] },
      pixel: { family: 'VT323', pkg: 'vt323', weights: ['400-normal'] },
    },
    type: {
      display: { font: 'body', size: 72, line: 64, weight: 600, tracking: -1.5, use: 'hero number in the serif; VT323 notches at large sizes, so it stays in the game layer' },
      headline: { font: 'body', size: 28, line: 34, weight: 600, tracking: -0.4 },
      title: { font: 'body', size: 17, line: 24, weight: 600 },
      body: { font: 'body', size: 16, line: 24, weight: 400 },
      label: { font: 'body', size: 13, line: 18, weight: 500, style: 'italic for labels' },
      caption: { font: 'body', size: 13, line: 18, weight: 400 },
      pixelNumber: { font: 'pixel', size: 28, line: 24, use: 'HP, hits, game numbers' },
    },
    radius: { sm: 4, md: 8, lg: 8, game: 0, note: 'Cards and controls 8, tags 4. Game elements are square.' },
    space: [4, 8, 12, 20, 28, 40],
    sprite: { o: '#1A2219', d: '#2F4A33', m: '#4E7550', l: '#86A774', b: '#E3DEB2', a: '#B5733B', e: '#F2C14E', h: '#F5F4E4' },
    colors: {
      light: {
        bg: '#EEEFE6', surface: '#F8F8F2', surface2: '#E3E6DA', line: '#D2D6C8', lineStrong: '#7D877A',
        text: '#222A24', text2: '#525D55', accent: '#2E6A4C', accentText: '#2A6346', onAccent: '#F8F8F2',
        accentSoft: '#D9E7DC', accentInk: '#1E4A34', track: '#DCE2D5',
        disabledBg: '#E3E6DA', disabledText: '#525D55',
        hpFull: '#4E7550', hpEmpty: '#E3E6DA', hpOutline: '#1A2219',
        stageSky: '#DCE5D6', stageText: '#222A24', specimen: '#F3F2E6',
      },
      dark: {
        bg: '#131815', surface: '#1A201C', surface2: '#242C27', line: '#2F3832', lineStrong: '#748277',
        text: '#E3E8E0', text2: '#A9B4AA', accent: '#8CC9A3', accentText: '#97D0AD', onAccent: '#0E2418',
        accentSoft: '#22392C', accentInk: '#BCE4CA', track: '#283830',
        disabledBg: '#242C27', disabledText: '#A3AEA4',
        hpFull: '#86A774', hpEmpty: '#2A332D', hpOutline: '#0B0F0C',
        stageSky: '#18211C', stageText: '#E3E8E0', specimen: '#1E2520',
      },
    },
    pairs: [
      ['text', 'bg', 'text', 'headings and body on page'],
      ['text', 'surface', 'text', 'body on cards'],
      ['text2', 'bg', 'text', 'secondary text on page'],
      ['text2', 'surface', 'text', 'secondary text on cards'],
      ['text2', 'surface2', 'text', 'week cells, tinted rows'],
      ['accentText', 'bg', 'text', 'text links on page'],
      ['accentText', 'surface', 'text', 'text links on cards'],
      ['onAccent', 'accent', 'text', 'Log and Hit button labels'],
      ['accentInk', 'accentSoft', 'text', 'tags and the 1 hit ready badge'],
      ['disabledText', 'disabledBg', 'text', '5x attack label and reason (informational, so held to 4.5)'],
      ['text', 'specimen', 'text', 'specimen card text'],
      ['text2', 'specimen', 'text', 'specimen card secondary'],
      ['stageText', 'stageSky', 'text', 'flavour line on backdrop'],
      ['accent', 'surface', 'ui', 'meter fill against card'],
      ['accent', 'track', 'ui', 'meter fill against its track'],
      ['lineStrong', 'surface', 'ui', 'chip outlines and ruler ticks'],
      ['hpFull', 'specimen', 'ui', 'full HP block against specimen card'],
      ['hpFull', 'hpEmpty', 'ui', 'full HP block against empty block'],
    ],
  },
};

// ---------- pixel helpers ----------
function gridToSvg(rows, pal, scale = 1, extraAttrs = '') {
  const h = rows.length, w = rows[0].length;
  let rects = '';
  for (let y = 0; y < h; y++) {
    let x = 0;
    while (x < w) {
      const ch = rows[y][x];
      if (ch === '.' || !pal[ch]) { x++; continue; }
      let run = 1;
      while (x + run < w && rows[y][x + run] === ch) run++;
      rects += `<rect x="${x}" y="${y}" width="${run}" height="1" fill="${pal[ch]}"/>`;
      x += run;
    }
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w * scale}" height="${h * scale}" shape-rendering="crispEdges" ${extraAttrs}>${rects}</svg>`;
}
function blank(w, h, ch = '.') { return Array.from({ length: h }, () => Array(w).fill(ch)); }
function rand(seed) { let s = seed; return () => { s = (s * 1664525 + 1013904223) % 4294967296; return s / 4294967296; }; }
function rock(g, cx, cy, rx, ry, cols, floorY) {
  // Lumpy boulder: mask from a wobbly ellipse, 1 px outline, diagonal light from top left.
  const raw = (x, y) => {
    if (y > floorY) return false;
    const dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry;
    const wob = (0.08 * Math.sin(x * 1.7 + cy) + 0.06 * Math.cos(y * 2.3 + cx)) * Math.max(0, 1 - dx * dx);
    return dx * dx + dy * dy <= 1 + wob;
  };
  // drop 1 px stubs so ends of the boulder stay clean
  const inside = (x, y) => raw(x, y) && (raw(x - 1, y) || raw(x + 1, y)) && (raw(x, y - 1) || (raw(x, y + 1) || y === floorY)) && !(y === floorY && !raw(x, y - 1));
  for (let y = Math.floor(cy - ry - 1); y <= floorY; y++) {
    for (let x = Math.floor(cx - rx - 1); x <= Math.ceil(cx + rx + 1); x++) {
      if (y < 0 || y >= g.length || x < 0 || x >= g[0].length || !inside(x, y)) continue;
      const edge = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([a, b]) => !inside(x + a, y + b) && !(y + b > floorY));
      const dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry;
      const t = dx * 0.6 + dy;
      let c = cols.mid;
      if (t < -0.55) c = cols.hi; else if (t > 0.35) c = cols.lo;
      if ((x * 7 + y * 13) % 11 === 0 && c === cols.mid) c = cols.lo;
      g[y][x] = edge ? cols.o : c;
    }
  }
}
function ridge(g, base, amp, period, phase, ch, seed, step = 2) {
  const r = rand(seed);
  let h = base;
  for (let x = 0; x < g[0].length; x++) {
    if (x % step === 0) h = Math.round(base + amp * Math.sin((x + phase) / period) + (r() - 0.5) * 2);
    for (let y = h; y < g.length; y++) if (y >= 0) g[y][x] = ch;
  }
}
function outline(g, fill, out) {
  const h = g.length, w = g[0].length, o = blank(w, h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    if (g[y][x] !== '.') { o[y][x] = g[y][x]; continue; }
    const n = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([a, b]) => g[y + b]?.[x + a] && g[y + b][x + a] !== '.');
    if (n) o[y][x] = out;
  }
  return o;
}

// Wooden sword, 16 x 16, shared shape, palette per direction.
function sword() {
  const g = blank(16, 16);
  for (let t = 0; t <= 7; t++) { const cx = 12 - t, cy = 3 + t; g[cy][cx] = 'l'; g[cy][cx + 1] = 'm'; }
  g[2][13] = 'h';
  [[3, 9], [4, 10], [5, 11], [6, 12], [7, 13]].forEach(([x, y]) => (g[y][x] = 'd'));
  [[4, 12], [3, 13]].forEach(([x, y]) => (g[y][x] = 'g'));
  g[14][2] = 'd';
  return outline(g, null, 'o').map(r => r.join(''));
}

// Backdrops, 96 x 64 logical pixels, shown at 4x (384 x 256 CSS px).
const W = 96, H = 64;
function backdrop(dir, mode) {
  const g = blank(W, H, 'S');
  const r = rand(dir === 'hearth' ? 7 : dir === 'signal' ? 11 : 23);
  if (dir === 'signal') {
    for (let i = 0; i < 46; i++) { const x = Math.floor(r() * W), y = Math.floor(r() * 30); g[y][x] = r() > 0.75 ? '*' : '+'; }
    ridge(g, 40, 3, 7, 3, 'F', 3, 2);
    ridge(g, 47, 2, 5, 11, 'N', 5, 1);
    for (let x = 0; x < W; x++) for (let y = 54; y < H; y++) g[y][x] = (y === 54 ? 'L' : 'G');
    for (let y = 56; y < H; y += 3) for (let x = 0; x < W; x++) if (g[y][x] === 'G') g[y][x] = 'g';
    for (let x = 4; x < W; x += 12) for (let y = 55; y < H; y++) g[y][x] = 'g';
    rock(g, 48, 56, 22, 7, { o: 'o', hi: 'h', mid: 'm', lo: 'd' }, 58);
    rock(g, 12, 54, 8, 6, { o: 'o', hi: 'h', mid: 'm', lo: 'd' }, 58);
    rock(g, 86, 55, 7, 5, { o: 'o', hi: 'h', mid: 'm', lo: 'd' }, 58);
  } else if (dir === 'hearth') {
    // cave mouth framing, rocks, ground
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const edge = Math.min(x, W - 1 - x);
      const archTop = 8 + Math.round(18 * Math.pow(1 - Math.min(1, (W / 2 - Math.abs(x - W / 2)) / (W / 2)), 2) * 2.2);
      if (y < archTop || edge < 5 + Math.round(3 * Math.sin(y / 4))) g[y][x] = r() < 0.09 ? 'w' : 'W';
    }
    ridge(g, 42, 2, 6, 5, 'F', 9, 2);
    for (let x = 0; x < W; x++) for (let y = 52; y < H; y++) g[y][x] = 'G';
    for (let i = 0; i < 40; i++) { const x = Math.floor(r() * W), y = 53 + Math.floor(r() * 11); g[y][x] = 'g'; }
    rock(g, 48, 55, 21, 7, { o: 'o', hi: 'h', mid: 'm', lo: 'd' }, 58);
    rock(g, 14, 52, 9, 7, { o: 'o', hi: 'h', mid: 'm', lo: 'd' }, 57);
    rock(g, 84, 53, 8, 6, { o: 'o', hi: 'h', mid: 'm', lo: 'd' }, 57);
    if (mode === 'dark') for (let i = 0; i < 14; i++) { const x = 8 + Math.floor(r() * (W - 16)), y = 14 + Math.floor(r() * 26); if (g[y][x] === 'S') g[y][x] = '*'; }
  } else {
    if (mode === 'dark') {
      const mx = 76, my = 12;
      for (let y = -4; y <= 4; y++) for (let x = -4; x <= 4; x++) if (x * x + y * y <= 17) g[my + y][mx + x] = '*';
      for (let i = 0; i < 18; i++) { const x = Math.floor(r() * W), y = Math.floor(r() * 26); if (g[y][x] === 'S') g[y][x] = '+'; }
    } else {
      for (let i = 0; i < 3; i++) { const cx = 12 + i * 30 + Math.floor(r() * 10), cy = 8 + Math.floor(r() * 8); for (let x = -5; x <= 5; x++) g[cy][cx + x] = '+'; for (let x = -3; x <= 2; x++) g[cy - 1][cx + x] = '+'; }
    }
    ridge(g, 34, 4, 9, 0, 'F', 13, 2);
    ridge(g, 44, 3, 6, 20, 'N', 17, 2);
    for (let x = 0; x < W; x++) for (let y = 53; y < H; y++) g[y][x] = 'G';
    for (let i = 0; i < 30; i++) { const x = Math.floor(r() * W), y = 52 + Math.floor(r() * 11); g[y][x] = 'g'; if (g[y - 1]) g[y - 1][x] = 'g'; }
    rock(g, 48, 56, 20, 7, { o: 'o', hi: 'h', mid: 'm', lo: 'd' }, 58);
    rock(g, 15, 53, 8, 6, { o: 'o', hi: 'h', mid: 'm', lo: 'd' }, 57);
    rock(g, 82, 54, 9, 6, { o: 'o', hi: 'h', mid: 'm', lo: 'd' }, 57);
  }
  return g.map(row => row.join(''));
}
const stagePal = {
  hearth: {
    light: { S: '#EBD8BF', W: '#9C7556', w: '#8A6448', F: '#D3B892', N: '#C4A47E', G: '#B8946D', g: '#A88560', o: '#4A3122', h: '#C9A47C', m: '#A88158', d: '#7F5C3D', L: '#B8946D', '*': '#F0875A', '+': '#F0875A' },
    dark: { S: '#241A15', W: '#120D0A', w: '#1A120E', F: '#2E221B', N: '#33261E', G: '#38291F', g: '#2C2018', o: '#0F0A08', h: '#6A4A34', m: '#4E3727', d: '#36261B', L: '#38291F', '*': '#F0875A', '+': '#F0875A' },
  },
  signal: {
    light: { S: '#0B1220', '*': '#E8F6FA', '+': '#5E7A8E', F: '#14213A', N: '#1A2C45', L: '#5AD2E6', G: '#0E1828', g: '#1B3047', o: '#05080D', h: '#3F6584', m: '#2A4864', d: '#1B3047' },
    dark: { S: '#0B1220', '*': '#E8F6FA', '+': '#5E7A8E', F: '#14213A', N: '#1A2C45', L: '#5AD2E6', G: '#0E1828', g: '#1B3047', o: '#05080D', h: '#3F6584', m: '#2A4864', d: '#1B3047' },
  },
  fieldbook: {
    light: { S: '#DCE5D6', '+': '#EEF2EA', '*': '#F5F4E4', F: '#C3CFB6', N: '#A9BA98', G: '#93A983', g: '#7D9A6C', o: '#3B4636', h: '#BAC2AE', m: '#9AA48E', d: '#78826E', L: '#93A983' },
    dark: { S: '#18211C', '+': '#56645A', '*': '#E3E8DA', F: '#212D26', N: '#28372E', G: '#2D3E33', g: '#365040', o: '#0B0F0C', h: '#56635A', m: '#3F4B43', d: '#2E3832', L: '#2D3E33' },
  },
};

// ---------- fonts ----------
function copyFonts(spec) {
  const faces = [];
  for (const role of ['body', 'pixel']) {
    const f = spec[role];
    for (const w of f.weights) {
      const file = `${f.pkg}-latin-${w}.woff2`;
      const src = path.join(FONTSRC, f.pkg, 'package/files', file);
      fs.copyFileSync(src, path.join(OUT, 'fonts', file));
      const [weight, style] = w.split('-');
      faces.push(`@font-face{font-family:'${f.family}';src:url('../fonts/${file}') format('woff2');font-weight:${weight};font-style:${style};font-display:block}`);
    }
  }
  return faces.join('\n');
}

// ---------- build ----------
const report = [];
for (const [slug, d] of Object.entries(directions)) {
  const dir = path.join(OUT, slug);
  fs.mkdirSync(dir, { recursive: true });

  const contrast = {};
  for (const mode of ['light', 'dark']) {
    const c = d.colors[mode];
    contrast[mode] = d.pairs.map(([fg, bg, kind, use]) => {
      if (!c[fg] || !c[bg]) throw new Error(`${slug} ${mode} missing ${fg}/${bg}`);
      const r = Math.round(ratio(c[fg], c[bg]) * 100) / 100;
      const pass = r >= MIN[kind];
      if (!pass) report.push(`FAIL ${slug} ${mode} ${fg} ${c[fg]} on ${bg} ${c[bg]} = ${r} (needs ${MIN[kind]})`);
      return { fg, bg, fgHex: c[fg], bgHex: c[bg], kind, min: MIN[kind], ratio: r, pass, use };
    });
  }
  // sprite against its likely backgrounds (outline must separate the creature from stage and strip)
  const spriteChecks = {};
  for (const mode of ['light', 'dark']) {
    const sky = stagePal[slug][mode].S;
    const silhouette = (bgHex) => {
      const o = +ratio(d.sprite.o, bgHex).toFixed(2), m = +ratio(d.sprite.m, bgHex).toFixed(2), l = +ratio(d.sprite.l, bgHex).toFixed(2);
      return { outline: o, mid: m, light: l, ratio: Math.max(o, m), carriedBy: o >= m ? 'outline' : 'body (mid)' };
    };
    spriteChecks[mode] = [
      { pair: 'silhouette vs stage sky', ...silhouette(sky), min: 3 },
      { pair: 'silhouette vs page bg', ...silhouette(d.colors[mode].bg), min: 3 },
      { pair: 'mid m vs light l', ratio: +ratio(d.sprite.m, d.sprite.l).toFixed(2), note: 'shading step, not a WCAG pair' },
    ].map(x => ({ ...x, pass: x.min ? x.ratio >= x.min : undefined }));
  }

  for (const m of ['light','dark']) for (const x of spriteChecks[m]) if (x.pass === false) report.push(`SPRITE ${slug} ${m} ${x.pair} ${JSON.stringify(x)}`);
  const tokens = {
    name: d.name,
    slug,
    summary: d.summary,
    colors: d.colors,
    type: { fonts: { body: { family: d.font.body.family, license: 'OFL-1.1', source: `@fontsource/${d.font.body.pkg}` }, pixel: { family: d.font.pixel.family, license: 'OFL-1.1', source: `@fontsource/${d.font.pixel.pkg}` } }, scale: d.type },
    radius: d.radius,
    spacing: d.space,
    notes: d.notes,
    sprite: d.sprite,
    spriteStage: stagePal[slug],
    contrast: { method: 'WCAG 2.x relative luminance ratio, computed by design/tools/build.mjs', thresholds: MIN, ...contrast, sprite: spriteChecks },
  };
  fs.writeFileSync(path.join(dir, 'tokens.json'), JSON.stringify(tokens, null, 2));

  // CSS variables
  const vars = c => Object.entries(c).map(([k, v]) => `--${k}:${v};`).join('');
  const css = `/* generated by build.mjs */\n${copyFonts(d.font)}\n` +
    `:root{color-scheme:light dark;--font-body:'${d.font.body.family}',sans-serif;--font-pixel:'${d.font.pixel.family}',monospace;` +
    `--r-sm:${d.radius.sm}px;--r-md:${d.radius.md}px;--r-lg:${d.radius.lg}px;` +
    Object.entries(d.sprite).map(([k, v]) => `--sp-${k}:${v};`).join('') + vars(d.colors.light) + `}\n` +
    `@media (prefers-color-scheme: dark){:root{${vars(d.colors.dark)}}}\n`;
  fs.writeFileSync(path.join(dir, 'tokens.css'), css);

  // pixel SVGs
  for (const mode of ['light', 'dark']) {
    fs.writeFileSync(path.join(dir, `backdrop-${mode}.svg`), gridToSvg(backdrop(slug, mode), stagePal[slug][mode], 4, 'preserveAspectRatio="xMidYMax slice"'));
  }
  const woodPal = { o: d.sprite.o, l: '#D9A86A', m: '#B07A43', d: '#7A4E28', g: '#5A3A22', h: '#F3D7A8' };
  fs.writeFileSync(path.join(dir, 'gear-wooden-sword.svg'), gridToSvg(sword(), woodPal, 2));
}
console.log(report.length ? report.join('\n') : 'all contrast pairs pass');
