# M2 Collection-Immutability Audit

## Scope

Audited the current integrated chess-domain collection surface on synchronized
`origin/main` baseline `c2f7af9fb34981861ac086d7e5fb3842ff135a5f`.
The audit covered constructor inputs, public collection properties, shared
constants, Kotlin/JVM collection implementations, mutable casts, and map
entries/keys/values. It included the specifically flagged `ChessGame.history`,
`Square.ALL`, and `Direction` collections, plus every related collection found
in the package inventory.

No production code was changed. Adversarial tests restore every mutated global
in `finally`, so one intentional failure cannot contaminate later tests.

## Confirmed finding 1 — `ChessGame.history` has two mutation paths

1. **Invariant:** a game and its move history are immutable values; moves and
   undo eligibility change only through a game-state transition.
2. **Scenario:** retain a mutable list passed to the constructor and append a
   record later, or cast the public history of a normally played game to a JVM
   mutable list and clear it.
3. **Evidence:** `ChessGame` stores the constructor list directly and publicly
   exposes the same property (`ChessGame.kt:23`). Normal transitions build
   history with Kotlin `plus`, whose multi-entry JVM result is mutable.
4. **Existing coverage:** move/undo tests verify functional copies but do not
   retain constructor input or mutate the published list.
5. **Sufficiency:** insufficient for either aliasing boundary.
6. **Classification:** **CONFIRMED DEFECT**.
7. **Tests:** `chessGameSnapshotsConstructorHistory` and
   `publishedChessGameHistoryCannotBeMutated`; both fail. The separate
   `derivedMoveListDoesNotExposeHistory` test passes, confirming the computed
   `moves` list is a safe fresh result rather than another escape.

## Confirmed finding 2 — `Square.ALL` is mutable shared state

1. **Invariant:** the canonical 64-square registry must remain ordered from a1
   through h8 for the lifetime of the process.
2. **Scenario:** cast `Square.ALL` to `MutableList` and replace index zero.
3. **Evidence:** the public registry is produced by `map` and stored directly
   (`Square.kt:41`). The JVM result accepts indexed replacement. Square
   factories and board iteration read this shared list.
4. **Existing coverage:** `SquareTest` checks initial contents but never attempts
   mutation.
5. **Sufficiency:** insufficient for a process-wide shared invariant.
6. **Classification:** **CONFIRMED DEFECT**.
7. **Test:** `squareRegistryCannotBeMutated`; fails.

## Confirmed finding 3 — promotion choices are mutable

1. **Invariant:** the only promotion choices remain Q/R/B/N.
2. **Scenario:** replace an entry through the mutable JVM list interface.
3. **Evidence:** `PROMOTION_CHOICES` is a public `listOf` result
   (`PieceType.kt:25`). `Move` validation and promotion generation trust it.
4. **Existing coverage:** tests verify its original values and reject pawn/king
   promotions, but do not protect the shared list from later mutation.
5. **Sufficiency:** insufficient; mutation can change legal validation globally.
6. **Classification:** **CONFIRMED DEFECT**.
7. **Test:** `promotionChoicesCannotBeMutated`; fails.

## Confirmed finding 4 — the published standard back rank is mutable

1. **Invariant:** the shared standard back-rank definition remains RNBQKBNR.
2. **Scenario:** replace a shared list entry through the JVM interface.
3. **Evidence:** `StandardPosition.BACK_RANK` is a public `listOf` result
   (`StandardPosition.kt:8-18`). It is used to construct the canonical starting
   board (`StandardPosition.kt:32`).
4. **Existing coverage:** starting-board tests prove initial construction, not
   continued integrity of the exposed shared definition.
5. **Sufficiency:** insufficient for the published constant. Mutation after
   object initialization does not rewrite the already-built `BOARD`, so this
   defect has lower immediate product impact than the other shared lists.
6. **Classification:** **CONFIRMED DEFECT**.
7. **Test:** `standardBackRankCannotBeMutated`; fails.

## Confirmed finding 5 — all shared direction lists are mutable

1. **Invariant:** rook, bishop, queen, and king direction sets remain fixed.
2. **Scenario:** replace an entry in `ORTHOGONAL`, `DIAGONAL`, or `ALL` through
   its JVM mutable-list interface.
3. **Evidence:** the first two are public `listOf` results and `ALL` is a public
   `plus` result (`Direction.kt:23-29`). Sliding-move generation returns these
   same shared lists.
4. **Existing coverage:** movement tests verify the original geometry but do
   not attempt mutation of its shared inputs.
5. **Sufficiency:** insufficient; one write can alter legal movement globally.
6. **Classification:** **CONFIRMED DEFECT** for all three lists.
7. **Tests:** `orthogonalDirectionsCannotBeMutated`,
   `diagonalDirectionsCannotBeMutated`, and `allDirectionsCannotBeMutated`; all
   fail.

## Confirmed finding 6 — knight steps are mutable shared state

1. **Invariant:** the eight knight offsets remain fixed.
2. **Scenario:** replace a step with `(0, 0)` through the JVM list interface.
3. **Evidence:** `PseudoLegalMoves.KNIGHT_STEPS` is a public `listOf` result
   (`PseudoLegalMoves.kt:65-76`), and knight generation iterates it directly
   (`PseudoLegalMoves.kt:88`).
4. **Existing coverage:** knight tests verify the initial offsets but not shared
   constant mutation.
5. **Sufficiency:** insufficient; mutation changes move generation globally.
6. **Classification:** **CONFIRMED DEFECT**.
7. **Test:** `knightStepsCannotBeMutated`; fails.

## Audited collection inventory

| Collection surface | Disposition | Evidence |
|---|---|---|
| `DrawRuleState.positionCounts` constructor input | Safe | Defensive snapshot; retained regression passes. |
| Published repetition map | Safe | Unmodifiable wrapper rejects direct writes at 0/1/many entries. |
| Repetition `entries`, `keys`, and `values` | Safe | Entry `setValue`, key removal, and value removal all throw; tests pass. |
| `Board.of` placement map | Safe | Copied into private square storage; caller-map mutation test passes. |
| `Board` query lists | Safe | Fresh results; clearing them leaves placement intact. |
| `ChessGame.history` | Defective | Constructor alias and public backing both mutate the game. |
| `ChessGame.moves` | Safe | Fresh derived list; clearing it leaves history intact. |
| `Square.ALL` | Defective | Mutable shared list. |
| `PieceType.PROMOTION_CHOICES` | Defective | Mutable shared list. |
| `StandardPosition.BACK_RANK` | Defective | Mutable shared list. |
| `Direction.ORTHOGONAL`, `DIAGONAL`, `ALL` | Defective | Three independently mutable shared lists. |
| `PseudoLegalMoves.KNIGHT_STEPS` | Defective | Mutable shared list. |
| `InsufficientMaterial.MATING_MATERIAL` | Safe from callers | Private and never returned. |
| Legal-move, attack, draw-claim, and board-query results | Safe from owner mutation | Fresh derived `List`/`Set` values; mutation cannot reach domain or shared state. |
| Kotlin enum `entries` collections | Safe | Runtime-owned immutable enum views, not caller-supplied or domain backing state. |

No other public/shared `List`, `Map`, `Set`, constructor collection, or
collection-backed domain property was found in the current chess package.

## Verification

- Focused audit selection: 25 tests run, 16 passed, 9 failed.
- Full `game-core` verification:
  `./gradlew.bat :game-core:ktlintCheck :game-core:test --rerun-tasks` —
  formatting **PASS**; 341 tests run, 332 passed, 9 failed.
- All failures are the intentionally retained regression tests for the confirmed
  defects above. Existing tests and the new safe-boundary checks pass.

M2 remains `DEFECT FOUND`; M3 was not evaluated.
