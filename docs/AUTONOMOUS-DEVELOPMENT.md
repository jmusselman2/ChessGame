# Autonomous Development Protocol

Use this protocol when instructed to run continuous autonomous development of
the MVP (for example: "start the autopilot", "continue autonomously", "work the
backlog").

## Default Autonomy Scope

Work continuously through the backlog. The normal autonomous task loop is
**exactly** these steps, in order:

1. Select the highest-priority unblocked `TODO` (see **Task Selection**).
2. Mark it `IN PROGRESS` in `docs/BACKLOG.md`.
3. Implement it.
4. Add or update tests.
5. Run targeted verification (the narrowest relevant tests / module check).
6. Run `./gradlew build`.
7. Fix failures until local verification passes. Iterate here; do not stop for a
   normal failure (see **Failure Handling and Escalation**).
8. Mark the task `DONE` and add its completion note in `docs/BACKLOG.md`
   (date + how it was locally verified). Update any other affected documentation
   (`DECISIONS.md`, `DEVELOPMENT.md`; `PRODUCT.md`/`ARCHITECTURE.md` only when a
   higher-precedence doc is stale).
9. Review `git status` and `git diff`, and run `git diff --check` — it must
   report nothing (exit 0). Fix any trailing-whitespace or conflict-marker
   errors it flags before committing.
10. Commit the verified task on `claude-autopilot` (see **Branch and Commit
    Workflow**).
11. Push `claude-autopilot` to `origin`.
12. Wait for the required GitHub Actions run for **that pushed commit** (see
    **Remote CI Gate**).
13. If CI fails: diagnose the failure, make the necessary fix, rerun local
    verification (`./gradlew build`), commit, push again, and wait for CI again.
    Use the same **Failure Handling and Escalation** ladder.
14. Only after the required CI checks are green for the pushed commit may the
    workflow select and begin the next backlog task.
15. Continue automatically across milestone boundaries.

### `DONE` vs. advancing to the next task

These are two distinct gates:

- **`DONE`** means the task's implementation is finished and its required
  **local** verification passed (acceptance criteria met, targeted tests pass,
  `./gradlew build` passes). A task may be marked `DONE` at step 8.
- **Advancing** to the next task is gated on a **green required remote CI run**
  for the pushed `claude-autopilot` commit (steps 11–14). The workflow may not
  select or begin the next task until that run has passed.

So a task can be `DONE` while the branch is still blocked from progressing
because CI has not yet gone green. That is expected. `DONE` = task-level
implementation gate; green remote CI = branch-level progression gate.

**Exception — tasks whose acceptance criteria require GitHub Actions itself to
run successfully (e.g. `M1.7`):** do not mark the task `DONE` until the required
CI run has actually succeeded. For these tasks the CI run is part of the local
acceptance criteria, not only the progression gate.

**Milestone completion is not a stopping condition.** When the last task in a
milestone is `DONE` and its CI is green, continue directly into the next
milestone. Do not pause for acknowledgement at milestone boundaries.

Stop only for a genuine blocker in the **Stop Conditions** list, or when the
backlog has no remaining unblocked `TODO` task.

## Task Selection

`docs/BACKLOG.md` is the authority. Its **Task Selection Order** section defines
the deterministic rule:

- consider only `Status: TODO` tasks,
- exclude tasks whose `Depends on` tasks are not all `DONE`,
- exclude `BLOCKED` tasks and tasks that would trigger a Stop Condition,
- of the rest, take the one that appears first in the document (lowest milestone
  number, then lowest task number).

If two tasks are genuinely independent and equally ranked, do the lower-numbered
one first.

## Work Loop (detailed)

1. Read root `CLAUDE.md`.
2. Read `docs/DECISIONS.md`.
3. Read `docs/BACKLOG.md` and apply the Task Selection rule.
4. Read the relevant parts of `docs/PRODUCT.md` and `docs/ARCHITECTURE.md` for
   the selected task.
5. Set the task to `IN PROGRESS` in `docs/BACKLOG.md`.
6. Inspect existing implementation and tests before editing.
7. Implement the selected task with the smallest change that satisfies its
   acceptance criteria.
8. Add or update tests. Tests that encode a documented requirement are
   authoritative — never weaken them (see **Test Integrity Rule**).
9. Run the narrowest relevant tests (see `docs/DEVELOPMENT.md`).
10. Fix failures caused by the change. Iterate here, not by stopping.
11. Run affected-module build/check.
12. Run the single aggregate command `./gradlew build` (see **Verification**).
13. Inspect `git status` and `git diff`, and run `git diff --check` (must be
    clean — fix any flagged whitespace/conflict-marker errors).
14. Review specifically for:
    - requirement violations,
    - unnecessary abstractions,
    - duplicated game rules,
    - Android/client-authoritative behavior,
    - persistence concerns leaking into `game-core`,
    - missing concurrency protection,
    - silent product-behavior changes.
15. Update `docs/BACKLOG.md` (set the task `DONE` — unless its acceptance
    criteria require a successful CI run, see the exception in **Default
    Autonomy Scope** — and add a short `Completed:` note with the date and how
    it was locally verified).
16. Record meaningful architecture decisions in `docs/DECISIONS.md`.
17. Update `docs/DEVELOPMENT.md` if any command, version, or environment detail
    changed.
18. Commit the verified work on `claude-autopilot`.
19. Push `claude-autopilot` to `origin`.
20. Identify and watch the required GitHub Actions run for the commit just
    pushed (see **Remote CI Gate**). Confirm the run's head SHA matches the
    pushed commit.
21. If CI fails, diagnose and fix it (same escalation ladder), then re-run local
    verification, commit, push, and watch CI again. Repeat until the required
    checks are green.
22. Only once the required CI checks are green for that commit, continue to the
    next unblocked task.

## Verification

Authoritative commands live in `docs/DEVELOPMENT.md`.

- **Narrow, while iterating:** the specific module test task, e.g.
  `./gradlew :game-core:test`.
- **Single aggregate gate, before marking a task `DONE`:** `./gradlew build`.
  This one command runs ktlintCheck, all module unit tests, Android lint, the
  Android APKs, and the server distribution. It is also exactly what CI runs.

A task is marked `DONE` when its **local** gate is satisfied:

- required behavior is implemented,
- relevant tests pass,
- `./gradlew build` passes,
- known regressions caused by the work are fixed,
- documentation is updated as needed.

Never mark a task `DONE` because code was merely written.

`DONE` does **not** authorize starting the next task. Advancing the branch is
separately gated on a green required remote CI run for the pushed commit (see
**Remote CI Gate**). Do not treat pushed work as verified while a required CI
check for it is failing, and do not silently skip remote CI verification.

For a task whose acceptance criteria require GitHub Actions itself to succeed
(e.g. `M1.7`), the successful CI run is part of the `DONE` gate — do not mark it
`DONE` until that run is green.

## Remote CI Gate

After pushing `claude-autopilot`, the workflow must wait for the required
GitHub Actions run for the commit just pushed and confirm it is green before
selecting the next backlog task.

### Monitoring the run

- Prefer the GitHub CLI (`gh`) to identify and watch the run. Typical sequence:
  - `git rev-parse HEAD` — the commit that was pushed.
  - `gh run list --branch claude-autopilot --limit 10` — find the run whose
    head SHA equals that commit.
  - `gh run watch <run-id> --exit-status` — block until it finishes; non-zero
    exit means the run failed.
  - `gh run view <run-id>` / `gh run view <run-id> --log-failed` — inspect
    failures.
- **Verify the run corresponds to the commit just pushed.** Match the run's head
  SHA to `git rev-parse HEAD`. Do not accept the latest unrelated run, a run for
  an earlier commit, or a run still in progress as satisfying the gate.
- If the expected run has not been created yet, wait briefly and re-list before
  concluding anything.

### When `gh` is not usable

If the GitHub CLI is unavailable, unauthenticated, or cannot access the
repository (`gh auth status` fails, repo not found, API errors), treat it as a
**missing external prerequisite** and stop per **Stop Conditions**. Report that
remote CI verification could not be performed and what needs to be provided
(`gh` installed and authenticated with repo access). Do not silently skip the
remote CI gate and do not advance to the next task.

### When CI fails

A failing GitHub Actions run is normally an implementation or configuration
problem to diagnose and fix — not an immediate reason to stop. Apply the same
**Failure Handling and Escalation** ladder: read the failing logs, form a
specific hypothesis, fix, re-run `./gradlew build` locally, commit, push, and
watch CI again. Only escalate to a stop if repeated genuinely-different fixes
fail the same way (which may indicate the workflow or documented architecture is
wrong).

## Failure Handling and Escalation

A normal compile error, test failure, lint failure, dependency-resolution
error, failing GitHub Actions run, or implementation bug is **not** a reason to
stop. Diagnose and fix it.

Escalation ladder for a failing verification step (local or remote CI):

1. **Attempt 1–2:** read the actual error output, form a specific hypothesis,
   apply a targeted fix, re-run the failing command.
2. **Attempt 3:** widen the investigation — re-read the relevant product /
   architecture sections, check recent `git diff`, check whether a test encodes
   a requirement you are misreading, try a different implementation approach.
3. **Attempt 4+:** if the same verification step still fails for the same
   underlying reason after genuinely different fix attempts, treat it as a
   possible signal that the documented architecture or an authoritative
   requirement is wrong or contradictory. Stop and report with: the task, the
   failing command, the full error, each distinct fix tried and why it did not
   work, and the specific documentation you believe is implicated.

Do not report a failure back to the requester until the escalation ladder has
been worked through. "It failed" without a diagnosis is not an acceptable stop.

If a failure is clearly caused by external infrastructure being unavailable
(no database, no Supabase project, no network), that is a Stop Condition, not a
bug to fix — report it as a missing prerequisite.

## Stop Conditions

Stop autonomous work and report when — and only when — one of these is true:

- **Contradictory requirements:** two authoritative documents genuinely
  conflict and resolving it changes product or architecture behavior.
- **Missing prerequisite:** required credentials, external services, or
  infrastructure (PostgreSQL, Supabase project, hosting) are unavailable.
- **Remote CI not verifiable:** the GitHub CLI is unavailable, unauthenticated,
  or cannot access the repository, so the **Remote CI Gate** cannot be checked.
  Do not silently skip remote CI verification — stop and report what is needed.
- **Difficult-to-reverse architecture change:** the task needs a significant
  structural change that would be costly to undo (new module boundary, changed
  persistence model, transport redesign, introducing KMP).
- **New recurring cost:** the task would introduce a meaningful ongoing cost
  (paid hosting, paid third-party service, paid CI capacity).
- **Production or destructive operation:** deployment to production, production
  secret changes, destructive migrations against non-disposable data, deleting
  user data.
- **Security-boundary change:** altering authentication, authorization, token
  verification, server-authority rules, or what the client is trusted to do.
- **Architecture-level verification failure:** repeated verification failure
  (per the escalation ladder) that indicates the documented architecture itself
  is wrong rather than the implementation.
- **Backlog exhausted:** no unblocked `TODO` task remains.

A stop report must include what was completed and verified so far, the exact
blocker, and the options or decision needed to proceed.

When none of the above applies, keep going without asking — including for minor
unspecified implementation choices, which should be resolved with the
recommended conventional option per `CLAUDE.md`.

## Branch and Commit Workflow

Autonomous work happens on the `claude-autopilot` branch.

- Create it from an up-to-date `main` if it does not exist:
  `git switch -c claude-autopilot` (or `git switch claude-autopilot` if it
  already exists).
- Make one focused commit per completed, verified task. Commit messages state
  the backlog task id and what changed.
- Local commits after verified tasks are expected and require no approval.
- After each such commit, push `claude-autopilot` to `origin` and then satisfy
  the **Remote CI Gate** before starting the next task.
- **Do not** force-push.
- **Do not** commit or push directly to `main` (protected).
- **Do not** rewrite published history.
- Pushing `claude-autopilot` to `origin` is expected so CI can run; opening or
  merging a pull request into `main` is a human step unless explicitly
  authorized.
- Production and beta deployment remain prohibited without explicit approval.

If `main` has advanced, update `claude-autopilot` by merging or rebasing local
(unpublished) commits only; never rebase commits that have been pushed and
shared.

## Test Integrity Rule

Never weaken, delete, skip, or rewrite a correct test merely to make an
implementation pass.

If a test encodes a documented requirement:

- fix the implementation.

If the test itself appears inconsistent with a higher-precedence requirement:

1. identify the conflict,
2. stop if the resolution changes product/architecture behavior,
3. otherwise correct the test and document why in the commit and, if
   meaningful, in `docs/DECISIONS.md`.

Do not mark failing tests as ignored without explicit written justification.

## Scope Discipline

- Do not opportunistically add deferred features (`docs/PRODUCT.md` "Deferred
  Features", `docs/MVP.md` "Explicitly Not Required").
- Do not create deck-building abstractions during the chess MVP unless a
  current concrete task requires them.
- Do not perform broad refactors unrelated to the active task unless required
  to complete it safely.
- One backlog task (or a tightly related group) at a time.

## Git Discipline

Always inspect `git status` and `git diff` before considering work complete, and
run `git diff --check` — it must report nothing. Fix every trailing-whitespace
or leftover-conflict-marker error it reports on the lines the change touches
before committing.

Do not perform without explicit authorization:

- `git reset --hard`,
- deleting untracked user work,
- force push,
- pushing to `main` or any protected branch,
- rewriting published history.

Prefer additive, reversible recovery approaches.

## No Manual Backup Copies of Tracked Files

Git history is the recovery mechanism for anything already tracked.

- Autonomous tooling must **not** create `.bak`, `.backup`, `.orig`, `.old`,
  `~`, `copy of …`, or similarly-named manual backup copies of tracked files.
  To experiment safely, use a branch, `git stash`, or `git worktree` — not a
  duplicate file.
- If a tool creates a temporary backup file as part of an in-place operation
  (for example some `sed -i` / `perl -i` variants), it may delete **that
  specific file it just created** once the operation has succeeded and
  verification passes.
- Never delete an untracked file the workflow did not create. Unknown or
  user-created untracked files (including any pre-existing `*.bak`) are left
  alone and, if relevant, mentioned in the summary — deleting untracked user
  work still requires explicit authorization.
- `.gitignore` already excludes common editor/tool backup patterns; do not rely
  on that as a licence to create them.

## Production / Destructive Operations

Never autonomously:

- deploy to production or beta,
- modify production or beta secrets,
- wipe production or shared databases,
- run destructive migrations against non-disposable environments,
- delete user data.

A development/test environment must be disposable and separate from
production/beta data.
