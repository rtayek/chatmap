# ChatMap GitHub branch trimming recommendation

## Recommendation

Delete these two remote branches:

- `fix/chatgpt-json-import-identity` at `fbf2218`
- `fix/chatgpt-json-import-identity-v2` at `9531ff1`

The live GitHub remote currently has only those two branches plus `master`.

Neither feature commit is an ancestor of `master`, and `git cherry` reports
both as distinct patches. However, their useful behavior is already present on
current `master`:

- ChatGPT `conversation_id` and fallback `id` are preserved.
- Active conversation branches are selected correctly.
- Repeated imports are covered for identity and idempotency.
- Pull request 4 merged the current-architecture regression coverage that was
  extracted from these obsolete branches.

The branches are therefore semantically superseded even though Git cannot
classify them as mechanically merged.

## Safe removal commands

These commands were not run because deleting remote branches is a push:

```sh
git push origin --delete \
  fix/chatgpt-json-import-identity \
  fix/chatgpt-json-import-identity-v2

git fetch --prune origin
git ls-remote --heads origin
```

The final command should then list only `refs/heads/master`.

There is also one local branch named `fix/chatgpt-json-import-identity`. Git's
safe `-d` check will reject it because the commit is not ancestry-merged. After
the remote deletion, remove that local branch explicitly if desired:

```sh
git branch -D fix/chatgpt-json-import-identity
```

If the exact obsolete patches must remain reachable, create archive tags before
deleting instead. That is unnecessary for the current code because the useful
behavior and tests are already on `master`.

## Separate local cleanup

The checkout has 27 local branches, including 21 whose upstream is already
gone. Those branches were not audited here and should not be bulk-deleted
without a separate ancestry and semantic review.

No branches were deleted and nothing was pushed.
