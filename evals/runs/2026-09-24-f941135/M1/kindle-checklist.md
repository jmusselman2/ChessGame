# M1 Device Checklist

- **Run:** `2026-09-24-f941135`
- **Milestone:** M1
- **Device testing:** applicable to M1.3's launch criterion

## Discovery and Selection

`adb devices -l` initially found one physical device:

- Serial: `G090MJ0574130HMR`
- Manufacturer/model: Amazon KFDOWI
- Android/API: 5.1.1 / 22

No ChessGame owner package or evaluator package was installed when checked.
The Kindle disconnected before the APK installation command, so no package or
device setting was changed on it and no cleanup was necessary.

Existing emulator inventory was then used because the preferred physical
device was unavailable. `ChessPlayer1` booted as:

- Serial: `emulator-5554`
- Model: `sdk_gphone16k_x86_64`
- Android/API: 17 / 37

## Isolation and Launch

The emulator already contained the owner's `com.jmussel.chessgame` package, so
it was not replaced or uninstalled. A disposable detached worktree at the
pinned baseline added the evaluator-only application ID suffix `.eval.m1` and
built `com.jmussel.chessgame.eval.m1`.

The evaluator package installed successfully and
`com.jmussel.chessgame.MainActivity` cold-launched successfully. Android
reported status `ok`, process ID `9588`, and the evaluator activity as the top
resumed activity.

## Cleanup

- `com.jmussel.chessgame.eval.m1` uninstalled successfully.
- The owner's `com.jmussel.chessgame` package remained installed.
- No device settings were changed.
- The evaluator-started emulator was shut down.
- The disposable worktree was removed and only the primary worktree remains.

**Result:** PASS.
