# Merge Blocks – 2048 Puzzle

A complete, ready-to-publish Android game: a 4x4 merge-number puzzle
(2048-style) upgraded with boosters, trap tiles, combos and juicy animations.
The base game *mechanic* is not protected by copyright and the original 2048
was released under the MIT license, so clones are legal — this project uses
its own code, name, colors, and layout, plus a handful of professionally
designed icons under a permissive open license (see **Credits** below —
important if you publish this app).

## Gameplay features

- **Animated everything** — tiles slide and pop, merges burst particles,
  floating "+points" text, screen shake, haptic feedback, combo banner.
- **Combo system** — consecutive merging swipes multiply merge points up to
  ×4, shown as a pulsing "COMBO ×N" banner.
- **Trap tiles** that create pressure and strategy:
  - 🪨 **Blocker** — an immovable stone that splits the board; crumbles on
    its own after 10 moves, or smash it with the hammer.
  - 💣 **Bomb** — slides around but never merges; its fuse ticks down every
    move and at zero it explodes, destroying itself and its neighbours.
- **Boosters** (start with a few; refill by watching a rewarded ad — a
  second strong ad hook besides "continue"):
  - ↩ **Undo** — take back the last action.
  - 🔨 **Hammer** — tap any tile (including traps) to smash it.
  - 🔀 **Shuffle** — reshuffle all movable tiles when you're cornered.
  - 💥 **Mega Bomb** — arm it, then tap the board to obliterate a whole
    3×3 area (traps included); destroyed tiles pay out a score bonus.
- **Continue after game over** by watching a rewarded ad (clears traps and
  the smallest tiles).

## Screens & flow

A full front-end, no cloud/backend — all state (best score, boosters, the
saved game) lives in local SharedPreferences:

`Splash` (company / engine / legal logos) → `Title` (visual anchor,
tap-to-start) → `Main Menu` (New Game · Continue · Settings · Extra Content)
→ `Loading` (asset progress bar + rotating tips) → `Gameplay`.

- **Continue** resumes the exact board saved on the device; **New Game**
  starts fresh.
- **Settings** — vibration and screen-shake toggles (wired live into the
  game), reset best score, restore free boosters.
- **Extra Content** — how to play, traps, boosters and credits.

**Internet is required to play.** Since the app is ad-supported, a
connection check gates the splash screen and the loading screen; offline
users get a "No Connection / Retry" screen. If you'd rather let people play
offline (usually better for retention and reviews), remove the
`Connectivity.isOnline(...)` gate in `SplashActivity` and `LoadingActivity`.
Because the app now touches the network by design, keep `INTERNET` +
`ACCESS_NETWORK_STATE` in the manifest and declare data sharing (AdMob) in
the Play **Data safety** form.

Monetization is **AdMob only** (no in-app purchases, no ad spend needed),
and ads are shown *smartly* — players are never force-fed an ad mid-run;
the strong hooks are opt-in because the player wants to keep going:

| Ad type      | Where it shows                                  |
|--------------|-------------------------------------------------|
| Banner       | Bottom of the **gameplay** screen only, and only once a real ad has loaded (the slot stays collapsed otherwise — no empty ad band). No banners on splash / title / menu / loading / settings. |
| Interstitial | Only on **Try Again**, rate-limited (`MIN_INTERSTITIAL_GAP_MS`, ≥90s apart) and never right after a rewarded ad |
| Rewarded (opt-in) | **Extra Life** and **Free Power-ups** on the "no moves" panel, plus refilling any empty booster |

**No moves ≠ game over.** When the board locks, the player gets an adaptive
**NO MOVES!** panel instead of a death screen:

- If they still own power-ups, those are offered right there to break free
  (Undo/Shuffle act immediately; Hammer/Mega arm a tap). The board is
  re-checked after each — the run continues if a move opened up.
- If they're out (or want more), they can **watch an ad for an Extra Life**
  (revive) or **watch an ad for Power-ups** (refill) — shown only when an ad
  is actually loaded, so there are never dead buttons — or just **Try Again**.

This creates several layered, *chosen* ad moments (extra life, power-up
refills, empty-booster refills) rather than forced interruptions. The one
non-rewarded ad, the interstitial, fires only at the natural Try-Again break
and is rate-limited. GDPR/EEA consent is handled via Google's UMP SDK (the
consent form shows automatically where required).

---

## 1. Build it

1. Install [Android Studio](https://developer.android.com/studio) (free).
2. Open this folder (**File → Open**). Gradle syncs automatically.
3. Press **Run** to try it on an emulator or your phone.
   Test ads will show immediately — the project ships with Google's public
   **test ad IDs**.

## 2. Create your AdMob account (free)

1. Sign up at [admob.google.com](https://admob.google.com).
2. **Apps → Add app** → Android → "app not listed on a store yet".
3. Create **three ad units**: Banner, Interstitial, Rewarded.

## 3. Paste your real ad IDs (4 places)

| What | File | Replace |
|------|------|---------|
| App ID (`ca-app-pub-…~…`) | `app/src/main/AndroidManifest.xml` | the `APPLICATION_ID` meta-data value |
| Banner unit ID | `AdManager.kt` → `BANNER_ID` | test ID |
| Interstitial unit ID | `AdManager.kt` → `INTERSTITIAL_ID` | test ID |
| Rewarded unit ID | `AdManager.kt` → `REWARDED_ID` | test ID |

⚠️ **Never publish with the test IDs** (no revenue) and **never click your
own real ads** (AdMob bans the account).

## 4. Make the app yours

- Change `applicationId` in `app/build.gradle.kts` to something unique like
  `com.yourname.mergeblocks`. **This cannot be changed after publishing.**
- Optionally rename the app in `app/src/main/res/values/strings.xml`.

## 5. Publish on Google Play

1. Create a [Play Console](https://play.google.com/console) account —
   **one-time $25 fee** (the only unavoidable cost).
2. In Android Studio: **Build → Generate Signed App Bundle** → create a
   keystore (back it up! losing it means you can never update the app) →
   build a release `.aab`.
3. In Play Console create the app, upload the `.aab`, and fill in:
   - Store listing (title, description, screenshots — take them from the
     emulator, 2–8 phone screenshots + a 512×512 icon + 1024×500 banner).
   - **Privacy policy URL** — required because the app shows ads. Host
     `PRIVACY_POLICY.md` for free on GitHub Pages and paste the URL.
   - Data safety form: declare that the app shares device identifiers with
     third parties (Google AdMob) for advertising.
   - Content rating questionnaire (this game rates *Everyone*).
   - Ads declaration: **yes, contains ads**.
4. Submit for review (usually a few days for a first app).
5. Back in AdMob, link the app to its Play Store listing and set up
   `app-ads.txt` if you attach a developer website (optional but improves
   fill rate).

## 6. Free growth (no ad spend)

- Title/description keywords: "2048", "merge", "number puzzle", "block puzzle".
- Small APK (~2 MB) and offline-friendly gameplay help conversion.
- Reply to reviews and ship small updates — both boost ranking.
- Realistic expectation: ad revenue scales with installs; a puzzle game
  earns roughly $1–5 per 1,000 daily active users per day from this ad mix.
  Publishing several small games and keeping the ones that get traction is
  the standard playbook.

## Credits (required if you publish)

The bomb, padlock, hammer, undo and shuffle icons (`app/src/main/res/drawable/
the game bomb/lock/hammer/undo/shuffle plus the mega-bomb, cog, play, book,
trophy, info and wifi icons in `app/src/main/res/drawable/ic_*.xml`) are from
[Game-Icons.net](https://game-icons.net), licensed **CC BY 3.0** — free to use
commercially, but attribution is legally required. Everything else (code, tile
colors, layout, logo emblem, app icon) is original to this project. The
in-app **Extra Content → Credits** screen already shows this attribution, so
the requirement is satisfied in-app; keep it there (or also add the line to
your Play Store description).

## Project layout

```
app/src/main/java/com/gamekun/mergeblocks/
  Game2048.kt          # pure game logic + traps, mega bomb, save/load (no Android deps)
  GameView.kt          # canvas rendering, animations, tap-targeting boosters
  AdManager.kt         # UMP consent + banner/interstitial/rewarded ads
  GameSettings.kt      # vibration / screen-shake flags (SharedPreferences)
  GameStore.kt         # shared prefs keys + best/save/booster helpers
  Connectivity.kt      # online check for the internet gate
  SplashActivity.kt    # company / engine / legal logos
  TitleActivity.kt     # visual anchor + tap-to-start
  MenuActivity.kt      # New / Continue / Settings / Extra Content
  LoadingActivity.kt   # asset progress bar + tips, launches gameplay
  SettingsActivity.kt  # toggles + local data resets
  ExtraContentActivity.kt # how-to-play, traps, boosters, credits
  MainActivity.kt      # gameplay wiring, score/save persistence, boosters
```
