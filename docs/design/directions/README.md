# Visual directions

Three directions for The Last Hit: Quit NRT. Each folder has `tokens.json` (colours for light and dark, type, radii, spacing, sprite palette, WCAG results), `today.html`, `lair.html` and light and dark screenshots of both. All text and UI pairs pass (body 4.5:1, large text and UI 3:1).

## Hearth (`hearth/`)
- Mood: warm, kind and homely, like a kitchen notebook by the fire.
- Palette: cream paper `#F4EDE3`, cocoa ink `#2B2119`, ember accent `#B3441D` (dark theme: `#1A1411` with `#EE8A5C`). Ember dragon with gold horns.
- Type: Bricolage Grotesque for the app, Jersey 10 for the hero number and the game layer.
- Shape: soft cards (radius 18 and 12), a ring for the daily guide, a 2 by 2 grid of quick-log tiles. Game pieces are square.
- Why: the warmest and least clinical option. Gentle for people who feel judged about their NRT use, and the chunky pixel numbers link the plan to the game.

## Signal (`signal/`)
- Mood: crisp, cool and high contrast, like a well-made transit sign with an arcade screen tucked in.
- Palette: white and ink `#0E1217`, cyan signal `#006B85` in light and `#5AD2E6` in dark on near-black `#0A0D11`. Steel-blue dragon with glowing cyan eyes.
- Type: Atkinson Hyperlegible Next (built for legibility) for everything, Silkscreen for HUD numbers only.
- Shape: no cards, sections split by ink rules, a single 2 px radius. The daily guide is 14 blocks of 1 mg, the same visual language as the 12 HP blocks.
- Why: fastest to read at a glance and the most robust for low vision. It suits people who want a tool rather than a companion.

## Fieldbook (`fieldbook/`)
- Mood: calm and quietly bookish, a naturalist's notebook with Gary filed as a specimen.
- Palette: sage paper `#EEEFE6`, green-black ink `#222A24`, pine accent `#2E6A4C` (dark theme: forest `#131815` with `#8CC9A3`). Moss dragon with rust horns and amber eyes.
- Type: Literata (a screen serif with italics for labels) for the app, VT323 for HP, hits and badges.
- Shape: radius 8, a ruler for the daily guide, 7 day cells for the week, a diary-style entry list, a framed plate in the lair.
- Why: the softest contrast and the most grown-up tone. The week cells show each day against the guide, which supports the weekly "week met" rule.

## Notes
- Pixelify Sans was tested and dropped: its 5 reads as an S, which matters for "5× attack" and "Needs 5 hits".
- The creature is a placeholder (`creature.png`, pixelated, over a dashed box). Drop the sprite into each folder and re-render.
- Backdrops and the wooden sword are generated as pixel grids by `../tools/build.mjs`, which also writes `tokens.css` and recomputes contrast. Run `node ../tools/build.mjs` after changing any colour.
- Fonts (all OFL 1.1, from npm `@fontsource`) are in `fonts/`.

## In this repo
- The font files are not committed. The screenshots show the real fonts; opening the HTML here falls back to system fonts. `tools/build.mjs` can bundle them again from the `@fontsource` npm packages (set `FONTSRC`).
- The dragon sprite these mockups use lives in `art/creatures/dragon.json`.
