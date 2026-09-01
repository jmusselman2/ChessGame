# M2 — Chess Domain Model: Re-evaluation Report

## Scope

Re-evaluated the complete M2 milestone against synchronized `origin/main`
baseline `ab3472628e5468831d0c2ec326a90dbe7dc581d2`.

The prior constructor-aliasing and non-positive-count findings were not assumed
fixed. Their retained regression tests were rerun and pass. The M2 production
types, standard-position construction, dedicated tests, module dependencies,
original evaluation artifacts, and fix commit were independently inspected.

## Confirmed defect — the published repetition map remains mutable

1. **Requirement or invariant:** M2.1 records that all domain types are
   immutable (`docs/BACKLOG.md:304`). `DrawRuleState` itself says a caller
   cannot change an already-built state or its repetition decision outside a
   state transition.
2. **Adversarial scenario:** create a normal draw state containing two recorded
   positions, obtain its public `positionCounts`, and mutate the map through
   the JVM mutable-map interface. Replacing the current position's count with
   five makes the already-built game report automatic fivefold repetition even
   though no move or state transition occurred. Java callers can invoke this
   mutation directly; Kotlin callers can reach the same JVM interface by cast.
3. **Implementation evidence:** `DrawRuleState` assigns
   `positionCounts.toMap()` to the public property
   (`DrawRuleState.kt:40`). For a multi-entry map, the current JVM result is a
   mutable `LinkedHashMap`. `repetitionsOf` reads that same published object
   (`DrawRuleState.kt:49`), and `Repetition.isFivefold` trusts the count
   (`Repetition.kt:46`). The fix severed the constructor input alias, but did
   not make the published snapshot unmodifiable.
4. **Existing coverage:** the original
   `externalMapMutationCannotManufactureAnAutomaticDraw` regression mutates the
   caller-retained input map and passes. It never mutates the map returned by
   `state.positionCounts`, so it cannot detect this remaining path.
5. **Coverage sufficiency:** insufficient. It proves defensive copying at
   construction, not immutability of the resulting public value.
6. **Classification:** **CONFIRMED DEFECT**.
7. **Smallest automated test:** build a two-position `DrawRuleState`, attempt
   to set the published current-position count to five through
   `MutableMap`, and assert the existing game remains non-fivefold with count
   one. Added as
   `M2DomainInvariantTest.exposedPositionCountsCannotManufactureAnAutomaticDraw`.

## Other M2 results

- **Prior mutable-input alias defect:** resolved; retained regression passes.
- **Prior non-positive count defect:** resolved; retained regression passes.
- **Standard initial position:** passes the documented 32-piece placement,
  White-to-move, castling-rights, no-moves, counters, and no-result checks.
- **Domain representations:** required M2 types remain present and otherwise
  behave as covered by the dedicated value/invariant tests.
- **Platform independence:** `game-core` still applies only Kotlin/JVM and has
  no Android, Ktor, database, or serialization production dependency.

## Verification

- Baseline `./gradlew.bat :game-core:test --rerun-tasks`: **PASS**.
- M2 invariant regression class: 3 run, 2 passed, 1 failed. The failure is the
  confirmed published-map mutation defect.
- `./gradlew.bat :game-core:ktlintCheck :game-core:test --rerun-tasks`:
  formatting **PASS**; 326 tests run, 325 passed, 1 failed.

No production code was changed. M2 remains `DEFECT FOUND`, so M3 was not
evaluated.
