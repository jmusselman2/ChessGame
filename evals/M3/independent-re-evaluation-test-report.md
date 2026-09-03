# M3 — Independent Re-evaluation: Test Report

## Added adversarial coverage

- Retained eight published non-starting perft scenarios in
  `M3ReferencePerftTest`: Kiwipete, positions 3–6, en-passant check evasion, all
  promotion choices, and open castling.
- Added a repetition-identity regression for an en-passant marker whose only
  geometric capture is illegal because the pawn is pinned.
- Added concrete checkmate positions proving that one knight per side and
  opposite-color bishops are sparse but live, preventing an overly broad
  insufficient-material rule.

## Perft/reference results

- Standard position depths 1–4: 20, 400, 8,902, 197,281 — **PASS**.
- Kiwipete depths 1–3: 48, 2,039, 97,862 — **PASS**.
- Position 3 depths 1–4: 14, 191, 2,812, 43,238 — **PASS**.
- Positions 4–6 through depth 3: 6/264/9,467; 44/1,486/62,379;
  46/2,079/89,890 — **PASS**.
- En-passant check-evasion depths 1–2: 6, 136 — **PASS**.
- All-promotions depths 1–2: 24, 496 — **PASS**.
- Open-castling depths 1–3: 26, 568, 13,744 — **PASS**.

## Verification

- Clean synchronized baseline before new tests: `:game-core:test` — **PASS**,
  380 tests, 0 failures, 0 errors, 0 skipped.
- Focused adversarial/reference run: **PASS**, 28 tests, 0 failures, 0 errors,
  0 skipped.
- Final complete `:game-core:test --rerun-tasks`: **PASS**, 391 tests,
  0 failures, 0 errors, 0 skipped.
- `:game-core:ktlintCheck`: **PASS**.
- `git diff --check`: **PASS**.

All retained prior M3 regressions pass. No deliberately failing test remains.
