# Copy style

How The Last Hit talks. These rules apply to every piece of text in the app: screens,
notifications, the widget, the watch, and the store listing.

`./gradlew checkCopy` enforces the mechanical rules (dashes and banned phrases) on every
`strings.xml`. The rest needs a human, or the copy agent, to follow.

## Voice

- **A level-headed friend who's been through it.** Warm and direct, but never gushing.
- **Plain and specific.** "You've had 8 mg today" beats "You're making great progress".
- **Kind about slips.** Never shame, never guilt, and never imply someone failed. A missed week is information.
- **Humour belongs to the demon.** The lair, the creature's lines and the gear can be dry and a bit silly. Logging, health and plan text stays straight.

## Mechanics

- **British English:** programme, colour, favourite, realise, centre.
- **Sentence case** for everything, including buttons, titles and tabs: "Log a dose", not "Log A Dose".
- **No em dashes (—) or en dashes (–).** Use a full stop, a comma, a colon or brackets instead. For ranges, write "1 to 5".
- **Hyphens only where English needs them:** "4-7-8 breathing", "16-hour patch".
- **Digits for numbers, and a space before units:** "2 mg", "15:00", "12 weeks", "5×".
- **Time follows the phone's setting.** The examples here use 24-hour time.
- **No emoji.** Pixel icons do that job.
- **One exclamation mark per screen at most, and only in the game layer.**
- **No rhetorical questions** ("Ready to crush your goals?"), no "not just X, it's Y", and no lists of three adjectives.
- **Contractions are fine:** you're, it's, don't.
- **Buttons say what they do:** "Log", "Hit", "Repeat this week". Not "Continue" or "OK" when a verb would be clearer.

## Banned words and phrases

These read as marketing or machine-written: journey, unlock your, empower, seamless,
dive in, elevate, game-changer, let's, embark, delve, unleash, supercharge, effortless,
harness, leverage, revolutionise, tapestry, crush it, you've got this, we're here for you,
in today's. The full list the build checks is in `config/copy-lint/banned.txt`.

## Examples

| Situation | Write | Not |
|---|---|---|
| Week met | Week 3 met. 1 hit ready for Gary. | Amazing job! You're crushing your journey! 🎉 |
| Week missed | Missed this one. Meet next week and you get it back. | Don't give up! Every setback is a setup for a comeback. |
| Over the label maximum | That's more than the pack's daily limit of 15. Worth a word with a pharmacist. | WARNING: You have exceeded the safe limit. |
| Empty history | Nothing logged yet. Your first dose will show up here. | Your journey starts here! Log your first dose to unlock insights. |
| Lair flavour | Gary staggers. | Gary the demon is SHAKING with FEAR! |
