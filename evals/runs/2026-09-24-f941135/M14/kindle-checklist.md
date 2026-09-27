# M14 Kindle Checklist

## Result

**PASS**, with the complete two-client portion run on protected read-only API
36 emulator instances after preferring the connected Kindle.

## Physical Kindle

- [x] Discovered first with `adb devices -l`: serial `G090MJ0574130HMR`, model
  `KFDOWI`, API 22 / Android 5.1.1.
- [x] Protected the installed owner app and its data. The existing
  `com.jmussel.chessgame` remained versionCode 9000, versionName `splash-test`;
  it was not replaced, downgraded, uninstalled, cleared, or launched.
- [x] Installed only the separately identified
  `com.jmussel.chessgame.test` package after confirming compatibility.
- [x] Ran 20 instrumentation tests covering portrait/tall and landscape/wide
  layouts, large font, scrolling, touch targets, promotion, local checkmate
  lockout, draw-claim presentation, and active/read-only online-game layouts.
- [x] Removed only the test package after the run and reconfirmed the owner
  package/version remained unchanged.
- [x] Excluded `LocalGameRotationTest` because it intentionally mutates app
  state and would violate the requirement to preserve the existing install.

## Two-client acceptance

- [x] Repaired evaluator-environment failures before judging production:
  package-manager stalls, a Pixel Launcher ANR, and an offline wiped emulator.
- [x] Used two simultaneous read-only `ChessPlayerM5Api36` instances (API 36 /
  Android 16), connected to the live HTTPS beta.
- [x] Completed fresh onboarding and username claim on both clients.
- [x] Added a mutual friendship and opened a series.
- [x] Observed realtime YOUR TURN/THEIR TURN changes and opponent moves without
  manual refresh.
- [x] Accepted Undo and observed both clients return to the same canonical
  position.
- [x] Completed and claimed threefold repetition; both clients showed the same
  terminal result.
- [x] Opened the automatic rematch and verified reversed colours.
- [x] Cancelled resignation once without mutation, then confirmed it and
  observed matching win/loss presentation.
- [x] Observed exactly one further rematch with reversed colours and correct
  dashboard turn state.
- [x] Reviewed completed history with colour, result, move count, final board,
  and no mutating game controls.
- [x] Force-stopped/restarted one client and verified the anonymous session and
  dashboard were restored.
- [x] Completed local Fool's mate and verified terminal lockout.
- [x] Stopped both emulator instances and discarded their read-only overlays.

The Kindle was used wherever the requirement could be exercised without
risking its existing installation. Two independent clients were indispensable
for the online play-through, so the protected emulator instances supplied that
evidence after the physical-device preference was honored.
