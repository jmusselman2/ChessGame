# Chess MVP — Claude Project Instructions

## Project Purpose

This repository builds a multiplayer Android chess game as the first implementation of a reusable turn-based game platform.

Chess is the first ruleset, not the permanent product goal. The long-term goal is a custom turn-based deck-building game.

Build chess concretely. Do not prematurely design a universal board-game framework.

## Required Reading

Before making architectural or product-behavior changes, read:

- `docs/DECISIONS.md`
- `docs/PRODUCT.md`
- `docs/ARCHITECTURE.md`
- `docs/MVP.md`
- `docs/BACKLOG.md`
- `docs/DEVELOPMENT.md`
- `docs/AUTONOMOUS-DEVELOPMENT.md`

## Document Precedence

If documents appear to conflict, use this precedence order:

1. `docs/DECISIONS.md` — latest accepted or superseding decisions
2. `docs/PRODUCT.md` — intended user-facing behavior
3. `docs/ARCHITECTURE.md` — technical boundaries and system structure
4. `docs/MVP.md` — MVP scope and definition of done
5. `docs/BACKLOG.md` — implementation ordering and task acceptance criteria
6. `docs/DEVELOPMENT.md` — verified developer commands and environment instructions
7. `docs/AUTONOMOUS-DEVELOPMENT.md` — working procedure

If a lower-precedence document conflicts with a higher-precedence document, do not silently reconcile them. Follow the higher-precedence document and update the stale lower-precedence document when appropriate.

## Core Architecture Rules

- `game-core` is a pure Kotlin/JVM module shared by Android and the Ktor server.
- `game-core` must remain independent of Android, Jetpack Compose, Ktor, PostgreSQL, Supabase database APIs, HTTP, WebSockets, and UI state.
- Do not migrate `game-core` to Kotlin Multiplatform unless a concrete non-JVM client requirement exists.
- The Ktor server is authoritative for multiplayer game state.
- Android may pre-validate moves using the shared game core, but may not directly modify canonical game state.
- Android must not directly read or write canonical game tables through Supabase database APIs.
- Canonical game operations go through Ktor.
- Commands represent requested actions.
- The server validates and applies commands using `game-core`.
- Every accepted game-state mutation increments the game version.
- Persist current canonical state directly, plus active move history and append-only audit events.
- Do not implement full event sourcing.
- Do not introduce microservices for the MVP.
- Do not add speculative deck-building abstractions during the chess MVP.
- Keep user, friendship, game-series, persistence, and transport concerns outside the chess rules engine.

## Product Rules That Must Not Be Changed Silently

- Usernames are globally unique, case-insensitively.
- Authentication is invisible/anonymous for the MVP.
- Friends are added by username.
- Friendships are mutual immediately.
- `lastSeenAt` is tracked internally.
- One current active game series per friend pair for MVP.
- Initial colors are random.
- Automatic rematches are the default.
- Rematch colors alternate.
- Removing a friend does not terminate the current game; it disables the next automatic rematch for that series, and the series closes when the current game ends.
- A normal move may be undone by its player while it remains the latest unanswered move.
- Once the opponent moves, the prior move is locked.
- If the opponent undoes their move, the previous player's move becomes undoable again.
- A game-ending move is immediately final and cannot be undone.
- Resignation is immediately final after confirmation.
- The next game is created automatically after a normally completed game when the series remains active.
- Threefold repetition and the fifty-move rule are claimable draws.
- Fivefold repetition and the seventy-five-move rule are automatic draws.
- Draw offers by agreement are outside MVP.

## Development Rules

- Work on one backlog item or tightly related group at a time.
- Before editing, inspect relevant code and tests.
- Add or update tests for behavioral changes.
- Run the narrowest relevant tests first, then the affected-module verification.
- Do not claim completion while tests/builds fail.
- Fix failures caused by your changes before proceeding.
- Never weaken, delete, or rewrite a correct requirement-backed test merely to make an implementation pass.
- Do not silently change product requirements.
- Record meaningful architectural decisions in `docs/DECISIONS.md`.
- Update `docs/BACKLOG.md` when work is completed.
- Prefer immutable domain state where practical.
- Keep rule logic out of Compose.
- Keep persistence DTOs out of the pure game domain.
- Prefer standard Kotlin/Android/Ktor conventions over custom frameworks.
- Avoid unnecessary interfaces, wrapper layers, and one-use abstractions.

## Continuous Autonomous Development

When asked to run autonomously (for example "start the autopilot", "work the
backlog", "continue autonomously"), follow `docs/AUTONOMOUS-DEVELOPMENT.md`. The
normal task loop is exactly:

1. Select the highest-priority unblocked `TODO` (Task Selection Order in
   `docs/BACKLOG.md`).
2. Mark it `IN PROGRESS`.
3. Implement it.
4. Add/update tests.
5. Run targeted verification.
6. Run `./gradlew build`.
7. Fix failures until local verification passes.
8. Mark the task `DONE` and add its completion note.
9. Review `git status` and `git diff`.
10. Commit the verified task on `claude-autopilot`.
11. Push `claude-autopilot` to `origin`.
12. Wait for the required GitHub Actions run for that pushed commit.
13. If CI fails, diagnose and fix it, rerun local verification, commit, push
    again, and wait for CI again (same escalation ladder).
14. Only after the required CI checks are green may the workflow select and
    begin the next backlog task.
15. Continue automatically across milestone boundaries.

Key clarifications:

- **`DONE` vs. advancing are different gates.** A task may be marked `DONE` once
  its required **local** verification succeeds (implementation + acceptance
  criteria + `./gradlew build`). The workflow may **not** advance to the next
  task until the pushed `claude-autopilot` commit has passed required remote CI.
  `DONE` = task-level implementation gate; green remote CI = branch-level
  progression gate.
- **Tasks whose acceptance criteria require GitHub Actions itself to run
  successfully (e.g. `M1.7`)** are not `DONE` until that CI run has actually
  succeeded.
- **Remote CI monitoring:** prefer the GitHub CLI (`gh`) to find and watch the
  run for the pushed commit; verify the run's head SHA matches the commit just
  pushed rather than trusting the latest unrelated run. If `gh` is unavailable,
  unauthenticated, or cannot reach the repo, treat it as a missing external
  prerequisite and stop per the Stop Conditions — do not silently skip remote
  CI verification.
- **Milestone completion is not a stopping condition.** Continue across
  milestone boundaries without pausing.
- A normal compile error, test failure, lint failure, failing GitHub Actions
  run, or implementation bug is **not** a reason to stop — diagnose and fix it.
  Work the escalation ladder in `docs/AUTONOMOUS-DEVELOPMENT.md` (targeted
  fixes, then wider investigation, then — only after genuinely different
  attempts fail the same way — stop and report with a full diagnosis).
- Stop and ask only for a genuine blocker: contradictory authoritative
  requirements, unavailable required credentials/infrastructure (including `gh`
  unavailable for the Remote CI Gate), a significant difficult-to-reverse
  architecture change, a meaningful new recurring cost, a production/destructive
  operation, a security-boundary change, a repeated verification failure
  implicating the documented architecture itself, or an empty unblocked backlog.
- Preserved guardrails: no direct pushes to `main`, no force-push, no rewriting
  published history; PR/merge into `main` stays human-controlled; production and
  beta deployment remain prohibited without explicit authorization.

## Verification Commands

Use `docs/DEVELOPMENT.md` as the authoritative source for build, test, formatting, lint, server-run, and environment commands.

The single aggregate verification command is `./gradlew build` (Windows: `.\gradlew.bat build`). It runs `ktlintCheck`, every module's unit tests, Android lint, the Android APKs, and the server distribution, and it is exactly what CI runs.

Before completing affected work:

- run the narrowest relevant tests,
- run the affected-module build or check,
- run `ktlintCheck` for Kotlin changes,
- run `./gradlew build` when the change can affect multiple modules or Android lint, and always before marking a backlog task `DONE`,
- inspect `git status` and `git diff`,
- do not proceed while required verification is failing,
- do not treat pushed work as verified while required CI checks are failing.

In the autonomous workflow, local `./gradlew build` success lets a task be
marked `DONE`, but a green required GitHub Actions run for the pushed
`claude-autopilot` commit is a separate gate that must pass before the next task
is started (see **Continuous Autonomous Development** and
`docs/AUTONOMOUS-DEVELOPMENT.md`).

## Decision Rule for Unspecified Details

When an implementation detail is unspecified:

1. Prefer the simplest solution consistent with the documented architecture.
2. Prefer maintainability and testability over cleverness.
3. Prefer established Kotlin/Android/Ktor conventions.
4. Do not add infrastructure for hypothetical future needs.
5. If a choice is easy to reverse, make the recommended choice and record it if meaningful.
6. Ask the user only when the decision materially changes product behavior, security, meaningful cost, production data, or creates difficult-to-reverse architecture.

## Completion Standard

A task is complete only when:

1. implementation is finished,
2. relevant tests pass,
3. affected modules build/check successfully,
4. no known regression caused by the change remains,
5. documentation/backlog is updated when required,
6. required CI checks pass for pushed work.

## Git / Safety

Routine autonomous work may include:

- editing files,
- running Gradle builds/tests/checks,
- inspecting `git status`, `git diff`, and history,
- creating the `claude-autopilot` branch and making local commits on it after
  verified tasks,
- pushing `claude-autopilot` to `origin` so CI can run.

### `claude-autopilot` branch workflow

- Autonomous work happens on `claude-autopilot`, branched from an up-to-date
  `main`.
- One focused commit per completed, verified backlog task; the message names the
  task id.
- `main` is protected: do not commit or push to it directly. Merging
  `claude-autopilot` into `main` (via pull request) is a human step unless
  explicitly authorized.
- Keep `claude-autopilot` current with `main` by merging or rebasing only
  local, unpublished commits.

### Pre-commit hygiene

- Run `git diff --check` before every commit; it must report nothing. Fix any
  trailing-whitespace or leftover-conflict-marker errors it flags on touched
  lines first. This check is part of the autonomous pre-commit review (see
  `docs/AUTONOMOUS-DEVELOPMENT.md` and the `docs/DEVELOPMENT.md` verification
  policy).
- Do not create `.bak`, `.backup`, `.orig`, `.old`, `~`, or similarly-named
  manual backup copies of tracked files. Git history is the recovery mechanism;
  use a branch, `git stash`, or `git worktree` instead.
- A temporary backup file the tooling itself creates during an in-place edit may
  be removed by that same tooling once the operation succeeds. Never delete
  unknown or user-created untracked files (deleting untracked user work still
  requires explicit authorization).

Do not perform any of the following without explicit authorization:

- destructive production actions,
- production or beta deployment,
- credential changes,
- destructive database operations,
- `git reset --hard`,
- deleting untracked user work,
- force pushing,
- pushing to `main` or any other protected branch,
- rewriting published history.
