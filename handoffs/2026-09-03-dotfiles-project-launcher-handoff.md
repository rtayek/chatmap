# Handoff: dotfiles / project-launcher session, 2026-09-03

**Scope:** dotfiles repo housekeeping, project-home.html launcher pattern,
terminal-process-identification implementation, and standing up a new
project (five-rules) using all of it end to end.

## Current state (verified against a fresh clone)

- `dotfiles` master tip: `5540b40` "added lf at end of file."
- Only `master` and `origin/feature/terminal-process-identification` exist
  as branches. That branch is fully merged into master (0 unique commits
  remaining) - safe to delete, just hasn't been deleted yet.
- All 5 test suites in `tests/validate-*.sh` pass except two known,
  pre-existing, unrelated failures:
  - `validate-git-config.sh`: stale assertion checking the old
    `attributesfile` Windows-specific path. Ray said this is not worth
    fixing - the setting correctly moved to `gitconfig-common` already;
    the test just wasn't updated to match. Leave as-is unless it becomes
    annoying.
  - `validate-publish-utilities.sh`: fails only because the expected jar
    path doesn't exist in a sandbox clone. Confirm it passes on Ray's
    actual machine; not a real bug.

## What landed this session

1. **Terminal title + PID work** (from the ChatMap-originated handoff,
   generalized into dotfiles): `bash/bashrc-common`'s
   `ray_update_terminal_title` now reads `PROJECT_TERMINAL_NAME` (falls
   back to folder name) and prints `<project> | Bash <PID> | <PWD>`.
   `bin/project-processes.sh` and `tests/validate-project-processes.sh`
   were added alongside it. All tests for this pass, including the safety
   checks (refuses to kill the shared `WindowsTerminal.exe`, no
   broad-process-name termination).

2. **`bin/launch-webterms.sh` moved from the separate `bin` repo into
   `dotfiles/bin`.** It starts local web-based terminals per project on
   fixed ports: 1031 dotfiles, 1032 dotmdfiles, 1033 chatmap, 1034
   five-rules. Uses absolute `$HOME/bin/webterm.sh`, so it doesn't matter
   which repo it physically lives in. Confirmed removed from the old `bin`
   repo location (Ray did this manually).

3. **`templates/project-home.html.macro`** - a reusable template for each
   project's own `project-home.html` launcher page (plain list of links:
   Bash webterm, Editor, ChatGPT, Claude, Gemini, GitHub). Several actual
   `project-home.html` files already exist per-project, filled in from
   this macro.

4. **`five-rules` stood up end to end** as the first project run through
   the full new setup: `.envrc` (direnv, `useProjectHistory`,
   `PROJECT_TERMINAL_NAME=five-rules`), webterm on port 1034,
   `project-home.html`, Chrome tab group. Fully working - webterm
   confirmed live, direnv confirmed exporting the right variables.

5. **Discovered Windows Terminal profiles are NOT per-project.** The six
   color profiles (Red/Green/Blue/Cyan/Magenta/Yellow, each with WSL24 and
   Ubuntu variants) have no `startingDirectory` and no per-profile env
   vars - they're generic colored shells. The terminal title is fully
   dynamic via `.envrc` -> `PROJECT_TERMINAL_NAME`, so **no new Windows
   Terminal profile is needed per project**. This corrects an earlier
   assumption in this session that a new profile per project was a
   required manual step - it is not.

## Not yet committed anywhere - needs a home

**`new-project.sh`** was drafted in this chat (not yet placed in any repo)
to orchestrate new-project setup end to end:

1. calls `dotmdfiles/bin/setup-project.sh <dir>` for LLM context files
   (CLAUDE.md, AGENTS.md, persona.md, human.md)
2. auto-picks the next free port and appends a `restart_webterm` line to
   `dotfiles/bin/launch-webterms.sh` (with a fix for a real bug: the file
   had no trailing newline, so a naive `>>` append glued the new line onto
   the old one - the script now guards against that)
3. generates `project-home.html` from `dotfiles/templates/project-home.html.macro`
4. generates or extends `.envrc` with `PROJECT_TERMINAL_NAME`

Agreed destination: the general `bin` repo, alongside where the old
`relaunch_all.sh` used to live (that script and `launch-bash-boxes.sh` are
confirmed dead code from the retired multi-monitor workflow - Ray now
works from browser tab groups on one monitor).

**Action needed next session:** get `new-project.sh` into the `bin` repo
and committed/pushed. It currently only exists as a generated file in this
chat.

## Open items / things to watch

- **Naming convention**: Ray asked mid-session to avoid underscores in
  favor of hyphens (filenames, shell function names) and camelCase (shell
  variables, since hyphens aren't legal there). He then reverted this
  after confirming shell variables can't use hyphens at all. Net effect:
  **underscores are back to the default everywhere**, including
  variables. Don't apply the hyphen/camelCase convention unless Ray raises
  it again explicitly.
- `new-project.sh`'s own internal variable names (`DOTFILES`, `DOTMDFILES`,
  `HOME_HTML`, etc.) still use the standard shell all-caps-with-underscore
  convention for constants - this was left as an open question to Ray,
  never resolved. Fine to leave as-is given the naming-convention reversal
  above.
- `.llm/working-context.md` and some `.llm/handoffs/*.md` files may still
  be stale relative to actual repo state (this was true earlier in the
  session for the shell-startup-restructure handoff, which said "not yet
  implemented" after it had, in fact, been merged). Worth a periodic
  freshness check rather than trusting these files at face value.
- Stale git ref cleanup pattern: when `git branch -a` shows a branch that
  doesn't exist on GitHub, the fix is `git fetch --all --prune`. On
  Windows this can hit a locked-directory error if another process (e.g.
  a frozen terminal) still holds a handle on `.git/`; retry after closing
  other terminals/editors touching the repo.
- `bugs.txt` and `todo.txt` in dotfiles have small pre-existing open items
  unrelated to this session (gitignore migration, a `~/Downloads` tab
  expansion bug) - not touched this session.

## Recommended next steps, in order

1. Commit `new-project.sh` into the `bin` repo.
2. Delete the now-merged `origin/feature/terminal-process-identification`
   branch (housekeeping only, no risk).
3. Decide whether to keep or delete `launch-bash-boxes.sh` and
   `relaunch_all.sh` in `bin` (confirmed dead code; no urgency).
4. If Ray starts a project after `five-rules`, that's the real test of
   `new-project.sh` end to end - watch for anything the script assumed
   incorrectly (e.g. the `eclipse-workspace` path prefix, which is not
   used by `dotfiles` itself but is used by `dotmdfiles`, `chatmap`, and
   `five-rules`).
