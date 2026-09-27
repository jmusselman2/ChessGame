# M15 Kindle Checklist

## Result

**PASS**, with the install-sensitive beta runtime evidence supplied by protected
read-only emulator instances rather than overwriting the connected Kindle's
owner app.

## Device protection and discovery

- [x] Ran `adb devices -l`; physical Kindle `G090MJ0574130HMR` (`KFDOWI`) was
  the available and preferred device.
- [x] Rechecked the installed `com.jmussel.chessgame`: versionCode 9000,
  versionName `splash-test`.
- [x] Did not replace, downgrade, uninstall, clear, or launch that owner app.
- [x] Did not install the unsigned M15 release artifact on any device.

## Beta endpoint evidence

- [x] Rebuilt the release APK with
  `https://chessgame-hit7.onrender.com` as a build input.
- [x] Inspected the APK and confirmed the HTTPS endpoint is compiled in.
- [x] Confirmed release network security forbids cleartext and packages no
  development-only `10.0.2.2`/localhost exception.
- [x] Confirmed HTTPS maps to WSS and a public unauthenticated socket is refused
  with 401.
- [x] Reused only evidence generated fresh in the immediately preceding M14
  evaluation on the identical production tree: two read-only API 36 clients
  authenticated against this beta, loaded dashboards, played canonical moves,
  and received realtime state over WSS.
- [x] Those emulator overlays were already discarded after M14; the current
  device list contains only the protected Kindle.

The milestone's device-relevant behavior is the deployed endpoint path, not a
Kindle-specific layout. Installing the release artifact on the Kindle would
require replacing an existing differently signed package and risk its data, so
the release artifact was verified statically and the runtime path was exercised
on disposable read-only clients. No device installation or data was changed by
M15.
