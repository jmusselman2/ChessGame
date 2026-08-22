# Autonomous Development Protocol

Use this protocol when instructed to continue implementing the MVP autonomously.

## Default Autonomy Scope

By default, work through all unblocked tasks in the **current milestone only**.

After the milestone satisfies its exit/verification criteria:

1. stop,
2. summarize what changed,
3. report verification results,
4. identify the next milestone.

Do not automatically continue across milestone boundaries unless explicitly instructed to do so.

This checkpoint can be loosened later after the workflow has proven reliable.

## Work Loop

1. Read root `CLAUDE.md`.
2. Read `docs/DECISIONS.md`.
3. Read `docs/BACKLOG.md`.
4. Identify the highest-priority unblocked task in the current milestone.
5. Read any relevant product/architecture documentation.
6. Inspect existing implementation and tests before editing.
7. Implement the selected task.
8. Add or update tests.
9. Run the narrowest relevant tests.
10. Fix failures caused by the change.
11. Run affected-module build/check.
12. Inspect `git status` and `git diff`.
13. Review specifically for:
    - requirement violations,
    - unnecessary abstractions,
    - duplicated game rules,
    - Android/client-authoritative behavior,
    - persistence concerns leaking into `game-core`,
    - missing concurrency protection,
    - silent product behavior changes.
14. Update `docs/BACKLOG.md`.
15. Record meaningful architecture decisions in `docs/DECISIONS.md`.
16. Continue to the next unblocked task in the same milestone when safe.

## Continue Without Asking When

A choice is:

- implementation-local,
- easily reversible,
- consistent with existing architecture,
- not security-sensitive,
- not production/destructive,
- not a product behavior change.

For minor unspecified choices, select the recommended conventional solution and proceed.

## Stop and Ask When

Stop when:

- authoritative documents genuinely contradict each other,
- required credentials/infrastructure are unavailable,
- a choice materially changes user-facing product behavior,
- a significant difficult-to-reverse architecture change is required,
- meaningful recurring cost would be introduced,
- production/destructive action is required,
- a security boundary would need to change,
- tests reveal that the documented architecture itself is flawed rather than merely an implementation bug.

## Test Integrity Rule

Never weaken, delete, skip, or rewrite a correct test merely to make an implementation pass.

If a test encodes a documented requirement:

- fix the implementation.

If the test itself appears inconsistent with higher-precedence requirements:

1. identify the conflict,
2. stop if the resolution changes product/architecture behavior,
3. otherwise correct the test and document why.

Do not mark failing tests as ignored without explicit justification.

## Verification Rules

Never mark a backlog task `DONE` merely because code was written.

A task is `DONE` only when:

- required behavior is implemented,
- relevant tests pass,
- affected module builds/checks,
- known regressions caused by the work are fixed,
- documentation is updated as needed.

## Scope Discipline

Do not opportunistically add deferred features.

Do not create future deck-building abstractions while implementing chess unless a current concrete requirement needs them.

Do not perform broad refactors unrelated to the active task unless required to complete it safely.

## Git Discipline

Always inspect:

```text
git status
git diff
```

before considering work complete.

Do not perform without explicit authorization:

- `git reset --hard`,
- deleting untracked user work,
- force push,
- pushing to a protected branch,
- rewriting published history.

Prefer additive/safe recovery approaches.

## Production / Destructive Operations

Never autonomously:

- deploy to production,
- modify production secrets,
- wipe production or shared databases,
- run destructive migrations against non-disposable environments,
- delete user data.

A development/test environment should be disposable and separate from production/beta data where practical.
