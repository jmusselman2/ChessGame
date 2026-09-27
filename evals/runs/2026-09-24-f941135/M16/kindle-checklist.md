# M16 Kindle Checklist

## Result

**PASS**, using protected current-run emulator evidence and deterministic
transport/restart regressions without altering the connected Kindle.

## Device and runtime evidence

- [x] Ran `adb devices -l`; physical Kindle `G090MJ0574130HMR` (`KFDOWI`) was
  connected and preferred.
- [x] Preserved its existing `com.jmussel.chessgame` installation and data; no
  M16 APK was installed, launched, downgraded, cleared, or removed.
- [x] The immediately preceding fresh M14 two-client run on read-only API 36
  instances proved live realtime convergence, canonical reloads, and
  force-stop/process-restart session restoration on the identical production
  tree.
- [x] Fresh M16 tests exercised offline failed-load recovery, updates during
  in-flight reloads, lost command replies, foreground return, socket replacement,
  cold-start waiting, and capped reconnect backoff.
- [x] A raw WebSocket peer that stops answering without a close frame was
  detected with keepalive; the same peer remained stuck when keepalive was
  disabled, proving the boundary rather than simulating a clean disconnect.
- [x] App-restart tests recreated the Android view-model/dependency graph while
  retaining only the persisted session, then reloaded all game state from the
  server.

M16 defines interruption and persistence behavior rather than new layout or
orientation acceptance. Replacing the Kindle's differently signed owner app
would add device risk without exercising a boundary not already covered by the
fresh protected runtime path and deterministic transport harnesses. The M14
read-only emulator overlays were discarded after that run, and no device state
was changed by M16.
