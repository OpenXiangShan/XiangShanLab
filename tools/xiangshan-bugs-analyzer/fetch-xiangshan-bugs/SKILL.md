---
name: get-xiangshan-bug
description: Collect GitHub issues and pull requests from OpenXiangShan/XiangShan for bug-fix work, including branch-filtered PRs, local raw data, Markdown indexes, and cause-oriented summaries. Use when Codex needs to fetch XiangShan issue/PR history, build or refresh xiangshan-bug-lib, inspect recurring bug causes, or prepare context for fixing XiangShan bugs from GitHub reports.
---

# Get XiangShan Bug

## Overview

Use this skill to build a local bug knowledge base for `https://github.com/OpenXiangShan/XiangShan.git`. The default output directory is `xiangshan-bug-lib` in the current working directory.

The bundled script performs deterministic GitHub collection and creates raw JSONL plus Markdown summaries. Codex should then read the generated Markdown/JSONL and refine the cause analysis for the user's concrete bug-fix task.

## Workflow

1. Confirm the target branch when the user mentions one. If no branch is specified, use the repository default branch for PR filtering.
2. Run the collector:

```bash
python3 get-xiangshan-bug/scripts/fetch_xiangshan_bugs.py --branch <branch-name>
```

Use `--all-pr-branches` when the user asks for all PRs regardless of base branch:

```bash
python3 get-xiangshan-bug/scripts/fetch_xiangshan_bugs.py --all-pr-branches
```

Use `--include-comments` only when comment-level history is needed; it is slower and consumes more GitHub API quota.

3. Inspect `xiangshan-bug-lib/README.md`, `issue-index.md`, `pr-index.md`, and `bug-cause-summary.md`.
4. Produce a concise cause analysis for the user. Prefer concrete evidence from issue titles, labels, bodies, PR descriptions, changed-file hints, and closure/merge relationships. Avoid inventing causes when the source data only shows symptoms.
5. If the user asks to fix a bug, use the generated library as triage context, then inspect the XiangShan source tree and implement the code change separately.

## Output Contract

The collector writes:

- `xiangshan-bug-lib/issues.jsonl`: non-PR GitHub issues.
- `xiangshan-bug-lib/pulls.jsonl`: pull requests, optionally filtered by base branch.
- `xiangshan-bug-lib/comments.jsonl`: issue and PR comments when `--include-comments` is used.
- `xiangshan-bug-lib/issue-index.md`: issue list with labels and heuristic cause tags.
- `xiangshan-bug-lib/pr-index.md`: PR list with base/head branches, merge state, labels, and heuristic cause tags.
- `xiangshan-bug-lib/bug-cause-summary.md`: counts and examples by likely bug cause.
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

Use script-generated `heuristic_causes` as triage hints, not final truth. When writing final analysis:

- Treat labels such as `bug`, `fix`, `regression`, `CI`, `frontend`, `backend`, `cache`, `memory`, `CSR`, `interrupt`, and `difftest` as evidence.
- Distinguish symptom from root cause. For example, a test failure is a symptom unless the issue/PR identifies the broken module or invariant.
- Prefer merged PRs that reference issues as stronger evidence than open issues.
- Include source links as `https://github.com/OpenXiangShan/XiangShan/issues/<number>` or `/pull/<number>`.
- If evidence is incomplete, say that the likely cause is inferred and name the missing confirmation.

## Script Reference

Collector options:

```bash
python3 get-xiangshan-bug/scripts/fetch_xiangshan_bugs.py \
  --repo OpenXiangShan/XiangShan \
  --branch master \
  --out xiangshan-bug-lib \
  --state all \
  --include-comments
```

Important flags:

- `--branch <name>`: collect PRs whose base branch is `<name>`.
- `--all-pr-branches`: collect all PRs, ignoring base branch.
- `--state open|closed|all`: GitHub item state, default `all`.
- `--since YYYY-MM-DD`: only fetch items updated after this date.
- `--limit N`: stop after N issues and N PRs for a quick sample.
- `--include-comments`: fetch comments for each issue and PR.
