# M10 Critic Report

## Fresh assessment

**Fresh verdict: PASS**

This section was completed before consulting any retained M10 evaluation
report.

### Scope and current authority

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M10:
  `48f981aa2e4ea5ae6c9bf8d7fe5050b2b27c8e96`
- Requirements: `docs/BACKLOG.md` M10.1 through M10.4, interpreted through
  current D004, D019-D021, D057, PRODUCT, and ARCHITECTURE
- Production implementation: `GameCommandService`, `GameRepository`, game
  routes and response mapping, `GameView`, `game-core`, schema, callers, and
  retained tests
- Verification: source/schema/caller audit, focused command/API/concurrency
  suites, complete build, device discovery, and one added evaluator probe

### Requirement assessment

| Requirement | Fresh result | Evidence summary |
| --- | --- | --- |
| M10.1 `MakeMove` | PASS | Authenticated routes accept intent only. The service uses the locked canonical game, checks participant/version/game/turn, delegates legality and application to `game-core`, performs a guarded transactional save, increments the version, and returns/audits canonical state. |
| M10.2 stale version handling | PASS | Both pre-check and guarded-write races return machine-readable stale responses with a canonical `GameView`. `GameRepository.load` verifies the row/history pair by re-reading its monotonic version, retries a torn attempt, and has a bounded locked fallback. |
| M10.3 `ClaimDraw` | PASS | Only the participant to move can claim; `game-core` derives current claimability from repetition and the halfmove clock; accepted claims finish and audit the game; invalid, stale, foreign, wrong-turn, and finished-game commands write nothing. Capabilities are exposed only to the entitled viewer. |
| M10.4 two-client turn-taking | PASS | Independent authenticated clients see opposite sides and one canonical version, alternate moves, receive structured refusals for wrong-turn/stale/terminal commands, reach checkmate, and exclude non-participants. |

The fresh audit examined an additional terminal-capability edge. A live game was
played through the authoritative service until threefold repetition was
claimable, then ended by the other player's resignation without changing its
board or history. The resulting `GameView` is terminal and advertises no draw
claim. Although `GameView.of` delegates capability calculation to
`ChessRules.availableDrawClaims`, the rule-level predicates explicitly require
a live state. The retained evaluator probe now pins that composition.

No fresh M10 production defect was found.

## Historical comparison

The retained `2026-09-17-e2b3287` evaluation found `M10-01`: an unlocked
two-statement display read could combine an old `games` row with newer `moves`
history under PostgreSQL READ COMMITTED. That report's deterministic regression
paused the history query while a real save committed and observed a hybrid that
matched no canonical version.

D057 is present in the current implementation and closes that exact path.
`GameRepository.load` now reads the row and history, re-reads the monotonic
version, discards an attempt if the version changed, and after three such races
falls back to `loadForUpdate`. The retained
`aRefreshCannotMixAnOldGameRowWithNewMoveHistory` regression passes unmodified.
Mutating commands continue to use the locked path, so the read repair did not
weaken command serialization.

The earlier report selected 53 core command/API tests plus the one red
adversarial test. The fresh selection expands that coverage to 63 passing tests:
the current retained suites, the repaired historical regression, eight
`GameViewTest` cases, and the new terminal-capability probe. No historical
finding is reopened and no new finding is introduced.

**Final M10 verdict: PASS. Historical `M10-01` remains closed.**
