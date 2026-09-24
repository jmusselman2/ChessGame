# Android 5.1/API 22 Compatibility — Independent Evaluation

Evaluated `main`: `83de79ceed51577b9b8cffa38ca1b937e029537a`

Evaluated change: `8538db33b35b3c9833b3e2b5c75998ace082740f` in its current
integrated form.

## Verdict

**PASS.** The app builds, packages, installs, cold-launches, authenticates,
renders, navigates, opens a local game, survives activity recreation, and
presents a usable server-failure state on a physical Kindle Fire KFDOWI running
Android 5.1.1/API 22. No compatibility defect was found and no evaluator
regression or production change was needed.

## What changed

- Android `minSdk` was lowered from 31 to 22.
- The Compose BOM moved from `2026.02.01` to `2025.11.01`, and DataStore from
  `1.2.1` to `1.1.7`, selecting dependency versions whose Android metadata
  permits API 22.
- Adaptive launcher XML moved from unqualified `mipmap-anydpi` to
  `mipmap-anydpi-v26`. API 22 therefore resolves the retained mdpi–xxxhdpi WebP
  launcher resources instead of attempting to inflate an adaptive icon it does
  not support.

## Static compatibility review

The packaged debug manifest declares minSdk 22 and targetSdk 37. Both debug and
release dependency metadata checks passed. The APK contains v26 adaptive icons
and density-specific v4 raster fallbacks.

The only direct newer-platform branch in Android production sources is dynamic
Material color. It is guarded by `SDK_INT >= VERSION_CODES.S`; API 22 takes the
ordinary light/dark color schemes. Android lint reported no errors and no
`NewApi` issue. The remaining warnings are dependency/version, unused resource
or attribute, and style diagnostics, not API-level violations.

The app has no unguarded Java/platform API use that requires a newer runtime.
Ktor/OkHttp, Compose, activity, lifecycle, and DataStore were exercised in the
physical-device startup and local-game path rather than inferred solely from
compilation.

## Physical-device isolation

Runtime checks used a temporary evaluator application id,
`com.jmussel.chessgame.codexeval`. This kept the user's installed
`com.jmussel.chessgame` package and its data untouched. The clone was removed
after testing, the Kindle's original stay-awake setting was restored to `0`,
and the repository application id was restored before this report was written.

The first instrumentation attempt began behind Fire OS's lock screen. Its first
four no-hierarchy failures are setup failures, not product evidence. API 22 does
not support `wm dismiss-keyguard`; the compatible Menu-key unlock was used and
the clean full rerun passed 9/9.

## Newer-target regression boundary

The same integrated sources passed all 484 Android host-side tests and the
corresponding build/lint/packaging checks. A Pixel 7 on Android 16 passed the
same nine rendered layout tests plus the live startup/local-game recreation
test in the preceding current-M17 checkpoint. Lowering the minimum did not
regress the current target.
