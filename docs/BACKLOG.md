# Chess MVP — Implementation Backlog

## Status Values

Use exactly one:

```text
TODO
IN PROGRESS
BLOCKED
DONE
```

Do not use Markdown checkboxes as a second status system.

Work in milestone order unless a dependency explicitly permits otherwise.

Each task must satisfy its acceptance criteria and verification requirements before being marked `DONE`.

## How the Backlog Is Organised

This file holds the backlog's rules, its index, and every milestone still being
worked, in full. Finished milestones are archived under `docs/backlog/`, grouped by
phase, one file per group, named by its range with two-digit milestone numbers so
the files sort in order (`M01-M05.md`, `M21.md`). Task `M19.9` is in
`docs/backlog/M18-M20.md` (`D093`). Archived milestones keep their own
`# M<n> — Title` headings. This file and the archives share `BACKLOG`'s rank in
`CLAUDE.md`'s precedence list.

- **Completing a task:** mark it `DONE` and add its completion note here.
- **Adding a milestone:** add it to this file, after the last active one, and its
  row to the index as `Active`.
- **Finishing a milestone:** in the commit that marks its last task `DONE`, move it
  unchanged into a new archive file in `docs/backlog/`, named by its range
  (`M22.md`, or `M22-M24.md` when several finished milestones go together), and
  point its index row there as `Done`. Archive files are never added to or renamed,
  so links into them keep working. Fix relative links in the moved text: the
  archive sits one directory deeper.
- The backlog holds only scheduled work. Work that is not scheduled lives in
  `docs/FUTURE.md` (`D072`).

## Milestone Index

| Milestone | Title                                                       | Where                                       | State  |
| --------- | ----------------------------------------------------------- | ------------------------------------------- | ------ |
| `M1`      | Repository and Build Bootstrap                              | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M2`      | Chess Domain Model                                          | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M3`      | Chess Legal Move Engine                                     | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M4`      | Undo Semantics                                              | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M5`      | Local Android Chess                                         | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M6`      | Server + PostgreSQL Foundation                              | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M7`      | Identity and Username                                       | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M8`      | Friends                                                     | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M9`      | Game Series                                                 | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M10`     | Authoritative Game Command API                              | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M11`     | Authoritative Undo                                          | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M12`     | Realtime                                                    | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M13`     | Game End and Automatic Rematch                              | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M14`     | Android Multiplayer Client, Dashboard, and History          | [`backlog/M14-M17.md`](backlog/M14-M17.md)  | Done   |
| `M15`     | Beta Deployment Environment                                 | [`backlog/M14-M17.md`](backlog/M14-M17.md)  | Done   |
| `M16`     | Hardening                                                   | [`backlog/M14-M17.md`](backlog/M14-M17.md)  | Done   |
| `M17`     | Friend Beta                                                 | [`backlog/M14-M17.md`](backlog/M14-M17.md)  | Done   |
| `M18`     | Post-Chess Architecture Review                              | [`backlog/M18-M20.md`](backlog/M18-M20.md)  | Done   |
| `M19`     | Deck-Builder Platform Generalization                        | [`backlog/M18-M20.md`](backlog/M18-M20.md)  | Done   |
| `M20`     | Multi-Game Rules Architecture                               | [`backlog/M18-M20.md`](backlog/M18-M20.md)  | Done   |
| `M21`     | AI Opponent, Local Game Persistence, and Game-Screen Polish | [`backlog/M21.md`](backlog/M21.md)          | Done   |
| `M22`     | Play and Social Improvements                                | [`backlog/M22.md`](backlog/M22.md)          | Done   |

The archives, by phase: `M01-M05.md` local chess; `M06-M13.md` the server and online
play; `M14-M17.md` the multiplayer client through the friend beta; `M18-M20.md` the
platform review and generalization; `M21.md` the AI opponent and local games;
`M22.md` play and social improvements.

## Task Selection Order (Autonomous Mode)

When running the continuous autonomous workflow (`docs/AUTONOMOUS-DEVELOPMENT.md`),
select the next task deterministically:

1. Consider only the milestones in this file (the index marks them `Active`),
   and in them only tasks with `Status: TODO`. Archived milestones are finished.
2. Exclude any task whose `Depends on` tasks are not all `DONE`.
3. Exclude any task marked `BLOCKED` and any task that would require a
   documented stop condition.
4. Of the remainder, pick the one with the lowest milestone number, then the
   lowest task number.
5. Mark it `IN PROGRESS` before editing code.
6. Mark it `DONE` only after its required **local** verification passes
   (implementation + acceptance criteria + `./gradlew build`).

### `DONE` is not the same as being allowed to start the next task

- `DONE` = the task's implementation and local acceptance criteria passed.
- The autonomous workflow may **not** select or begin the next task until the
  pushed `claude-autopilot` commit has passed its **required GitHub Actions
  run**. A green remote CI run is the branch-level progression gate; `DONE` is
  the task-level implementation gate. A task can legitimately be `DONE` while
  the branch is still waiting on CI. A commit that changes Markdown files only
  does not wait for its run (`D094`).
- For a task whose acceptance criteria require GitHub Actions itself to run
  successfully (for example `M1.7`), do **not** mark it `DONE` until that CI run
  has actually succeeded — the successful run is part of that task's acceptance
  criteria, not only the progression gate.
- If remote CI cannot be verified (GitHub CLI unavailable, unauthenticated, or
  no repo access), stop per the Stop Conditions in
  `docs/AUTONOMOUS-DEVELOPMENT.md`. Do not silently skip remote CI.
- Claude pushes `claude-autopilot` only. The owner brings `main` and
  `codex-autopilot` up to date by hand (`D094`).

Milestone boundaries are **not** stopping points. After a task is `DONE` and its
pushed commit's required CI is green, immediately select the next one,
continuing into the next milestone.

A single milestone-level task with no sub-tasks (for example `M2.1` inside a
milestone that only has `M2.1`, `M2.2`) is selected the same way.
