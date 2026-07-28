---
name: get-xiangshan-bug
description: Collect XiangShan GitHub issues and pull requests into a local bug library for triage, branch-aware analysis, and cause summaries.
---

# Get XiangShan Bug

## Overview

Build a local bug knowledge base for `OpenXiangShan/XiangShan`. The default output directory is `xiangshan-bug-lib` in the current working directory.

The collector writes raw JSONL plus Markdown indexes. Use the generated files as triage context, then narrow down to the concrete bug or PR the user asked about.

## Workflow

1. Choose the PR filter branch. If the user names one, use it. Otherwise use the repository default branch.
2. Run the collector.

```bash
python3 get-xiangshan-bug/scripts/fetch_xiangshan_bugs.py --branch <branch-name>
```

Use `--all-pr-branches` when you need PRs from every base branch:

```bash
python3 get-xiangshan-bug/scripts/fetch_xiangshan_bugs.py --all-pr-branches
```

Use `--include-comments` only when comment history matters.
3. Read `xiangshan-bug-lib/README.md`, `overview-2026-07-28.md`, `issue-index.md`, `pr-index.md`, and `bug-cause-summary.md`.
4. Analyze using concrete evidence from titles, labels, bodies, branch names, commit SHAs, and merge/close relationships.
5. If the user wants a fix, use the library for triage first, then inspect and edit the XiangShan source tree separately.

## Output Contract

- `xiangshan-bug-lib/issues.jsonl`: non-PR GitHub issues.
- `xiangshan-bug-lib/pulls.jsonl`: pull requests, optionally filtered by base branch.
- `xiangshan-bug-lib/comments.jsonl`: issue and PR comments when `--include-comments` is used.
- `xiangshan-bug-lib/overview-2026-07-28.md`: canonical entry point and consolidated summary.
- `xiangshan-bug-lib/issue-index.md`: quick issue lookup table.
- `xiangshan-bug-lib/pr-index.md`: quick PR lookup table.
- `xiangshan-bug-lib/bug-cause-summary.md`: heuristic cause buckets and examples.
- `xiangshan-bug-lib/README.md`: collection metadata and next steps.

## Authentication

The script can run without authentication, but unauthenticated GitHub API quota is low. If available, set one of these environment variables before running:

```bash
export GITHUB_TOKEN=<token>
```

or:

```bash
export GH_TOKEN=<token>
```

Never print tokens in final answers or write them into `xiangshan-bug-lib`.

## Cause Analysis Rules

Use `heuristic_causes` as triage hints, not as final truth.

- Treat labels such as `bug`, `fix`, `regression`, `CI`, `frontend`, `backend`, `cache`, `memory`, `CSR`, `interrupt`, and `difftest` as evidence.
- Separate symptom from root cause.
- Prefer merged PRs that reference issues over open issues when the evidence is stronger.
- Distinguish `kunminghu-v2` and `kunminghu-v3` explicitly.
- Preserve commit SHA values when the source record provides them; do not infer missing SHAs.
- Link sources as `https://github.com/OpenXiangShan/XiangShan/issues/<number>` or `/pull/<number>`.
- If evidence is incomplete, say the result is inferred and name the missing confirmation.

## Script Reference

```bash
python3 get-xiangshan-bug/scripts/fetch_xiangshan_bugs.py   --repo OpenXiangShan/XiangShan   --branch master   --out xiangshan-bug-lib   --state all   --include-comments
```

Important flags:

- `--branch <name>`: collect PRs whose base branch is `<name>`.
- `--all-pr-branches`: collect PRs from all base branches.
- `--state open|closed|all`: GitHub item state, default `all`.
- `--since YYYY-MM-DD`: only fetch items updated after this date.
- `--limit N`: stop after N issues and N PRs for a quick sample.
- `--include-comments`: fetch comments for each issue and PR.
