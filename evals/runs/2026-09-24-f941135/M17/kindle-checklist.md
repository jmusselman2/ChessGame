# M17 Kindle Checklist

## Result

**PASS**, using the preferred physical Kindle for non-mutating layout and game
evidence and a disposable emulator for the stateful live paths.

## Physical Kindle

- [x] Discovered first with `adb devices -l`: serial `G090MJ0574130HMR`, model
  `KFDOWI`, API 22 / Android 5.1.1.
- [x] Protected the installed owner app and its data. The existing
  `com.jmussel.chessgame` remained versionCode 9000, versionName `splash-test`,
  with unchanged `lastUpdateTime=2026-09-25 02:53:30`.
- [x] Installed only the separately identified test package and removed it
  after verification.
- [x] Repaired a sleeping-screen test-harness failure by waking and unlocking
  the device, then reran the exact affected class successfully.
- [x] `GameLayoutUiTest`: 9/9 passed, covering the documented windows,
  orientation modes, 44/48 dp floors, square taps, independent history/panel
  scrolling, short windows, large fonts, glyph size, controls, and promotion.
- [x] `OnlineGameLayoutUiTest`: 4/4 passed, covering black-side taps, active
  controls, loading/failure Back, and read-only finished games in both modes.
- [x] The owner app was not replaced, downgraded, uninstalled, cleared, or
  used for the stateful live tests.

## Disposable live device evidence

- [x] Started `ChessPlayerM5Api36` as a no-window read-only instance, installed
  a current debug build named `m17-eval`, and discarded the overlay afterward.
- [x] `LocalGameRotationTest` passed against the live HTTPS beta: `e2e4 e7e5`
  survived activity recreation, Back discarded it, and reopening Local game
  started a clean position.
- [x] Opened a fresh three-member group in the app; the current account was
  marked You and non-friend members had Play.
- [x] Played the non-friend member, moved `e2e4` on the board, received the
  peer's `e7e5` live, and observed version 2 and Your move.
- [x] Returned to the dashboard and observed the game under YOUR TURN; the
  peer's dashboard API returned the same game at version 2.
- [x] Left the group through its confirmation. The group disappeared, the
  required "games are unaffected" message appeared, and the game remained on
  both dashboards.
- [x] Deleted the temporary token file and killed the read-only emulator.

M17.1's explicit real-person acceptance is a one-time owner-recorded event in
the authoritative backlog, not something an independent evaluator can recreate
without another person and their device. Fresh verification proved the current
beta APK is signable, installable in form, versioned, HTTPS-only, and fails
loudly when signing inputs are incomplete; it does not claim a new real-person
distribution event.
