# Handoff: normalize shell conditional style in rtayek/dotfiles

## Goal

Make conditionals use the TERSE one-line form wherever it is safe,
to minimize vertical space (owner is low-vision, large font, wants
fewer lines on screen):

    [ -d "${HOME}/bin" ] && pathPrepend "${HOME}/bin"
    [ -f "$f" ] || return 0

Convert full block forms down to one-liners:

    if [ -d "${HOME}/bin" ]; then      # <- convert these ...
      pathPrepend "${HOME}/bin"
    fi
    # ... to:
    [ -d "${HOME}/bin" ] && pathPrepend "${HOME}/bin"

## When to KEEP the block form (do not force one-liners)

- Body has 2+ statements.
- if/else or elif chains (e.g. the aliases/functions fallback logic in
  bashrc-common: home stub first, else repo copy - keep as if/elif).
- Any `A && B || C` that would be created: NEVER produce this pattern;
  it is not if/else (C runs when B fails too). Keep those as blocks.
- The line would exceed ~70 characters. Short lines matter at font
  size 20; a one-liner that wraps is worse than a block.

## Scope

Repo: https://github.com/rtayek/dotfiles (branch: master).
Files to normalize:
- bash/bashrc and all bash/bashrc-* files
- bash/bash_profile, bash/bash_functions*, bash/bash_aliases
- profile, sh/shrc
- direnv/envrc (already mostly terse - use it as the style reference)
- deploy.sh, get-windws-settings.sh, put-windows-settings.sh
- bin/*.sh
- real/* stubs (already one-liners - leave as-is, they are the target
  style)

Do NOT touch: publish-utilities/, settings.json, minttyrc files,
tests/ except to update expected strings if a normalized file's exact
content is asserted (see Testing).

## Rules

1. Semantics must be identical. `set -e` interaction warning: in
   scripts with `set -e` (tests/), a trailing `... && cmd` as the last
   line of a function changes exit status behavior - leave those alone.
2. `case` statements stay as they are.
3. One task in-scope beyond style: ADD the missing PATH entry in
   bash/bashrc-common, after the existing ~/bin line, in the terse
   style:

       [ -d "${HOME}/dotfiles/bin" ] && pathPrepend "${HOME}/dotfiles/bin"

   (If the ~/bin block above it is still block-form, convert it too so
   the two lines match.)
4. While in bash/bash_profile, fix shellcheck SC2164: bare `cd`
   becomes `cd || return`.
5. A file whose conditionals are ALL already terse needs no changes.

## Testing (required before commit)

1. bash tests/validate-shell-startup.sh - all 13 checks must pass.
   If the test greps exact contents of a file you changed, update the
   expected strings; the test must still verify the same intent.
2. shellcheck:
   - shellcheck -s sh deploy.sh *.sh bin/*.sh
   - shellcheck -s bash bash/bashrc* bash/bash_profile \
       bash/bash_functions* direnv/envrc
   No new warnings beyond pre-existing SC1091 info notices. Watch for
   SC2015 (A && B || C) - its appearance means a conversion broke
   rule "never produce && ... ||"; fix by reverting to a block.
3. Smoke test both platforms via env overrides, as the test suite
   does: RAY_DOTFILES_UNAME_S=MINGW64, and =Linux with
   RAY_DOTFILES_OS_ID=ubuntu, sourcing bash/bashrc in clean bash -i.

## After changes

- If any file in real/ changed (unlikely - they are already terse),
  remind Ray to run sh deploy.sh on both Windows and Ubuntu.
- Single commit, message like:
  "normalize conditionals to terse one-line style; add dotfiles/bin to PATH"

## Owner preferences

- Ray dictates via speech-to-text; expect artifacts ("period",
  homophones like "corn shell" = Bourne shell). Confirm garbled
  instructions before acting.
- POSIX sh for scripts. Low-vision: fewer lines vertically is the
  point of this task, but lines must stay short horizontally (~70
  chars) - both matter.
- Leave alone: openclaw files, publish-utilities/, minttyrc
  duplicates (known separate issue), get-windws-settings.sh filename
  typo (known, fix separately if asked).
