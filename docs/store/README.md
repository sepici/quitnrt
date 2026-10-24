# Store documents

What Google Play needs for The Last Hit: Quit NRT, kept with the code so it changes with it
(PLAN §18, ROADMAP phase 9, slice 4).

| File | What it is |
|---|---|
| `privacy-policy.md` | The privacy policy, ready to host at thelasthit.app. No data collected. |
| `data-safety.md` | The Data safety form's answers, page by page, with the reasons, plus the rest of App content. |
| `health-apps.md` | The Health apps declaration's answers. |
| `listing.md` | The store listing: name, short and full description, category, content rating, and the screenshots. |
| `screenshots/` | Eight framed phone screenshots and the feature graphic. |
| `tools/` | `store_art.py`, which makes the screenshots from the Roborazzi renders, and `ttf.py`, which draws their captions. |

Before the first upload, fill in `{contact email}` in the privacy policy and the listing, and host
the policy. If the app ever gains a permission, a library or a feature that touches data, update
`privacy-policy.md` and `data-safety.md` in the same change.
