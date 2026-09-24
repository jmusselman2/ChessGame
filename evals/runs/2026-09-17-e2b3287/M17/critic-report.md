# M17 — Independent Evaluation: Critic Report

Production baseline: `38be421dfd64c269687c893300f11e661bfa9c90`

## Verdict

**PASS WITH CARRIED FINDINGS.** M17 provides a reproducible, safely signed beta
artifact path; announces newly created games to both players; identifies the
server and client builds; and retains a completed real-user physical-device
play-through. No new M17 defect was found and no production code was changed.
M10-01, M12-01, and M14-01 through M14-03 remain unresolved.

## Requirement evaluation

- **M17.1 — small beta distribution:** release signing is opt-in and consumes
  only external build inputs. A named keystore with incomplete credentials or
  a missing file stops the build, while the ordinary CI build remains unsigned.
  Version name/code, deployed HTTPS endpoint, and the no-cleartext release
  policy reach the packaged APK. Keystore patterns and properties are ignored,
  and no APK, bundle, keystore, or keystore-properties file is tracked.
- The opponent now receives `game-updated` when `POST /series` actually creates
  the first game, while idempotent reopens do not emit a false creation event.
  This closes the defect found during the two-emulator pre-distribution run and
  the retained broadcast tests pass.
- The authoritative M17.1 completion record says one other real person
  installed `0.1.2-beta` (versionCode 3) on their own physical Android device,
  completed onboarding unaided, and played an online game to completion with
  no crash, synchronization failure, installation problem, or developer
  intervention. It names server build `81fbab9`; commit `b032172`, which records
  that evidence, is an ancestor of the evaluated production baseline. This
  external play-through was audited as historical evidence rather than
  re-enacted by the evaluator.
- **M17.2 — build identity:** Render's supplied commit is shortened to seven
  characters in `/health`; missing or blank input preserves the original body.
  The dashboard formats and displays `Build <versionName>`. Both present and
  absent cases have retained tests. A current live probe returned HTTP 200 over
  verified TLS and named build `38be421`.

## Distribution safety

The verifier generated a throwaway PKCS12 key, produced and cryptographically
verified the signed test APK, checked its certificate subject and manifest
version, found the deployed HTTPS URL in its bytecode, inspected the packaged
network-security configuration, proved both incomplete signing configurations
fail, deleted the temporary key, and restored the unsigned release artifact.
It did not access the owner's permanent beta key or publish an APK.

## Carried findings and impact

M10-01 can still tear an unlocked game refresh; M12-01 can still serialize
realtime fan-out behind a stalled connection; and M14-01 through M14-03 can
leave Android views stale under specific response orderings. Those defects
remain relevant risks for beta users, but none is newly caused by M17 and none
invalidates the recorded successful physical-device acceptance session. Their
regressions remain intact and expected-red.

## Scope conclusion

M17.1 and M17.2 were reconciled against build configuration, signing and
distribution documentation, the executable APK verifier, server broadcast and
health behavior, dashboard build-label behavior, commit history, the live
deployment, and the physical-user completion record. No new evaluator
regression was warranted.
