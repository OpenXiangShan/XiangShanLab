# XiangShan Issue And PR Skill

Use this repository as an offline issue, pull request, bug, and fix knowledge
base for `OpenXiangShan/XiangShan`, especially the `kunminghu-v2` and `kunminghu-v3` branches.

## Local Data

- `issues.jsonl`: raw non-PR issue records from GitHub.
- `pulls.jsonl`: raw pull request records from GitHub.
- `issue-index.md`: compact issue table for quick lookup.
- `pr-index.md`: compact pull request table for quick lookup.
- `biweekly-bug-notes.json`: bug/fix notes parsed from XiangShan Biweekly posts.
- `bug-summarys/*.md`: module-oriented summaries.

Prefer local files before network access. Use `rg` for text search and inspect the
raw JSONL entries when index rows do not contain enough evidence.

## Issue And PR Lookup

When answering bug-history questions:

1. Search `issue-index.md` and `pr-index.md` first for quick candidates.
2. Cross-check referenced PRs in `pulls.jsonl` and issues in `issues.jsonl`.
3. Search `biweekly-bug-notes.json` when a bug/fix may have been summarized in a
   XiangShan Biweekly post.
4. Cite the GitHub PR/issue URLs when available.
5. State whether the conclusion comes directly from GitHub metadata, from the
   Biweekly notes, or from cross-checking both.

## GitHub URL Parsing

Recognize XiangShan GitHub issue and PR URLs:

```text
https://github.com/OpenXiangShan/XiangShan/issues/N
https://github.com/OpenXiangShan/XiangShan/pull/N
https://github.com/OpenXiangShan/XiangShan/pulls/N
```

Use these regular expressions:

```regex
^https://github\.com/OpenXiangShan/XiangShan/issues/(?P<number>\d+)/?$
^https://github\.com/OpenXiangShan/XiangShan/pulls?/(?P<number>\d+)/?$
```

Parsed fields:

- `type`: `issue` for `/issues/N`, `pull` for `/pull/N` or `/pulls/N`.
- `repo`: `OpenXiangShan/XiangShan`.
- `number`: integer issue or PR number.
- `html_url`: the normalized GitHub URL.

For shorthand references:

- `#N` may refer to either an issue or PR. Resolve it by checking
  `pulls.jsonl` first when the surrounding text says PR, pull request, merge,
  branch, base, draft, or fix commit; otherwise check `issues.jsonl` first.
- `PR #N`, `pull #N`, and `pull request #N` mean `pull`.
- `issue #N` means `issue`.

## Issue Parsing

Issue records live in `issues.jsonl` and should be parsed as non-PR GitHub
issues.

Important fields:

- `number`: issue number.
- `title`: issue title.
- `state`: `open` or `closed`.
- `labels`: GitHub labels.
- `created_at`, `updated_at`, `closed_at`: lifecycle timestamps.
- `body`: issue description and reproducer details.
- `heuristic_causes`: generated cause buckets.
- `html_url`: canonical GitHub issue URL.

When extracting issue evidence, prefer concrete symptoms, failing commands,
assertion names, exception names, difftest mismatches, and maintainer comments
over broad labels.

## PR Parsing

PR records live in `pulls.jsonl` and should be parsed as GitHub pull requests.

Important fields:

- `number`: PR number.
- `title`: PR title.
- `state`: `open` or `closed`.
- `draft`: whether the PR is draft.
- `base`: target branch; treat `kunminghu-v2` and `kunminghu-v3` as the primary Kunminghu branches for this dataset.
- `head`: source branch.
- `labels`: GitHub labels.
- `created_at`, `updated_at`, `closed_at`, `merged_at`: lifecycle timestamps.
- `body`: PR description, validation notes, and linked issue references.
- `heuristic_causes`: generated cause buckets.
- `html_url`: canonical GitHub PR URL.

When extracting PR evidence:

1. Treat `merged_at != null` as stronger evidence that the change landed.
2. Treat `closed_at != null` and `merged_at == null` as closed without merge
   unless local evidence says otherwise.
3. Use `base` to distinguish `kunminghu-v2`, `kunminghu-v3`, and unrelated branch PRs.
4. Prefer PR title/body and linked issue references over heuristic cause buckets
   when summarizing the actual fix.
5. Preserve validation commands from the PR body when they help assess fix
   confidence.

## Module Summaries

Use `bug-summarys/*.md` for module-level context, then verify concrete claims
against the raw JSONL records before presenting them as facts.

Common module buckets:

- `frontend`
- `backend`
- `mem`
- `cache`
- `chiselAIA`
- `chiselIOPMP`
- `difftest`
- `tool`
- `top`
- `uncategorized`
