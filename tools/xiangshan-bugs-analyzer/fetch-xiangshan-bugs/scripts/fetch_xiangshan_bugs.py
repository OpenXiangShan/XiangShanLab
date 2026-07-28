#!/usr/bin/env python3
"""Collect XiangShan GitHub issues and PRs into xiangshan-bug-lib."""

from __future__ import annotations

import argparse
import datetime as dt
import http.client
import socket
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable


API_ROOT = "https://api.github.com"

CAUSE_RULES: list[tuple[str, tuple[str, ...]]] = [
    ("difftest/reference-model mismatch", ("difftest", "diff test", "nemu", "ref", "golden")),
    ("CSR/privilege behavior", ("csr", "mstatus", "satp", "pmp", "privilege", "exception", "interrupt")),
    ("cache/memory subsystem", ("cache", "l1", "l2", "memory", "mmu", "tlb", "load", "store", "uncache")),
    ("pipeline/control hazard", ("pipeline", "flush", "redirect", "branch", "rename", "rob", "replay", "stall")),
    ("backend/execution unit", ("backend", "alu", "fpu", "mul", "div", "scheduler", "issue queue")),
    ("frontend/fetch/decode", ("frontend", "fetch", "decode", "predictor", "btb", "ras", "tage")),
    ("build/CI/toolchain", ("ci", "build", "compile", "sbt", "mill", "verilator", "gcc", "toolchain")),
    ("test/flaky/regression", ("test", "regression", "flaky", "fail", "assert", "timeout")),
    ("documentation/configuration", ("doc", "readme", "config", "parameter", "script")),
    ("bug fix/general", ("bug", "fix", "wrong", "error", "crash", "panic", "deadlock")),
]

SHA_RE = re.compile(r"\b[0-9a-f]{7,40}\b", re.IGNORECASE)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default="OpenXiangShan/XiangShan", help="owner/repo")
    parser.add_argument("--branch", help="PR base branch to collect; defaults to repo default branch")
    parser.add_argument("--all-pr-branches", action="store_true", help="collect PRs for all base branches")
    parser.add_argument("--out", default="xiangshan-bug-lib", help="output directory")
    parser.add_argument("--state", choices=("open", "closed", "all"), default="all")
    parser.add_argument("--since", help="only fetch items updated since YYYY-MM-DD")
    parser.add_argument("--limit", type=int, help="maximum issues and maximum PRs to collect")
    parser.add_argument("--include-comments", action="store_true", help="fetch issue/PR comments")
    return parser.parse_args()


class GitHubClient:
    def __init__(self) -> None:
        self.token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")

    def get(self, path_or_url: str, params: dict[str, Any] | None = None) -> tuple[Any, dict[str, str]]:
        if path_or_url.startswith("https://"):
            url = path_or_url
        else:
            url = f"{API_ROOT}{path_or_url}"
        if params:
            clean_params = {key: value for key, value in params.items() if value is not None}
            url = f"{url}?{urllib.parse.urlencode(clean_params)}"

        headers = {
            "Accept": "application/vnd.github+json",
            "User-Agent": "codex-get-xiangshan-bug",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"

        request = urllib.request.Request(url, headers=headers)
        for attempt in range(4):
            try:
                with urllib.request.urlopen(request, timeout=60) as response:
                    body = response.read().decode("utf-8")
                    return json.loads(body), dict(response.headers.items())
            except urllib.error.HTTPError as exc:
                if exc.code in (403, 429, 500, 502, 503, 504) and attempt < 3:
                    wait_for_rate_limit(exc.headers, attempt)
                    continue
                detail = exc.read().decode("utf-8", errors="replace")
                raise RuntimeError(f"GitHub API error {exc.code} for {url}: {detail}") from exc
            except (urllib.error.URLError, TimeoutError, http.client.RemoteDisconnected, http.client.IncompleteRead, ConnectionResetError, socket.timeout) as exc:
                if attempt < 3:
                    time.sleep(2**attempt)
                    continue
                raise RuntimeError(f"Failed to reach GitHub API for {url}: {exc}") from exc
        raise RuntimeError(f"Failed to fetch {url}")

    def paginate(self, path: str, params: dict[str, Any], limit: int | None = None) -> Iterable[dict[str, Any]]:
        next_url: str | None = None
        count = 0
        while True:
            if next_url:
                data, headers = self.get(next_url)
            else:
                data, headers = self.get(path, params)
            for item in data:
                yield item
                count += 1
                if limit and count >= limit:
                    return
            next_url = parse_next_link(headers.get("Link", ""))
            if not next_url:
                return


def wait_for_rate_limit(headers: Any, attempt: int) -> None:
    reset = headers.get("X-RateLimit-Reset") if headers else None
    if reset and str(reset).isdigit():
        delay = max(1, min(120, int(reset) - int(time.time()) + 1))
    else:
        delay = 2**attempt
    time.sleep(delay)


def parse_next_link(link_header: str) -> str | None:
    for part in link_header.split(","):
        match = re.match(r'\s*<([^>]+)>;\s*rel="([^"]+)"', part)
        if match and match.group(2) == "next":
            return match.group(1)
    return None


def compact_issue(item: dict[str, Any]) -> dict[str, Any]:
    labels = [label["name"] for label in item.get("labels", [])]
    text = "\n".join([item.get("title") or "", item.get("body") or "", " ".join(labels)])
    return {
        "number": item["number"],
        "type": "issue",
        "state": item["state"],
        "title": item.get("title") or "",
        "user": (item.get("user") or {}).get("login"),
        "labels": labels,
        "created_at": item.get("created_at"),
        "updated_at": item.get("updated_at"),
        "closed_at": item.get("closed_at"),
        "html_url": item.get("html_url"),
        "body": item.get("body") or "",
        "branch": extract_branch(text, item.get("base") or "", item.get("head") or ""),
        "commit": extract_commit(text),
        "heuristic_causes": classify_causes(text),
    }


def compact_pull(item: dict[str, Any]) -> dict[str, Any]:
    labels = [label["name"] for label in item.get("labels", [])]
    head = item.get("head") or {}
    text = "\n".join([item.get("title") or "", item.get("body") or "", " ".join(labels)])
    return {
        "number": item["number"],
        "type": "pull",
        "state": item["state"],
        "title": item.get("title") or "",
        "user": (item.get("user") or {}).get("login"),
        "labels": labels,
        "created_at": item.get("created_at"),
        "updated_at": item.get("updated_at"),
        "closed_at": item.get("closed_at"),
        "merged_at": item.get("merged_at"),
        "draft": item.get("draft"),
        "base": ((item.get("base") or {}).get("ref")),
        "head": head.get("ref"),
        "head_sha": head.get("sha"),
        "merge_commit_sha": item.get("merge_commit_sha") or "",
        "html_url": item.get("html_url"),
        "body": item.get("body") or "",
        "branch": extract_branch(text, ((item.get("base") or {}).get("ref")) or "", head.get("ref") or ""),
        "commit": extract_commit(text, head.get("sha") or "", item.get("merge_commit_sha") or ""),
        "heuristic_causes": classify_causes(text),
    }


def classify_causes(text: str) -> list[str]:
    normalized = text.lower()
    causes = []
    for cause, needles in CAUSE_RULES:
        if any(needle in normalized for needle in needles):
            causes.append(cause)
    return causes or ["uncategorized"]



def extract_branch(text: str, base: str = "", head: str = "") -> str:
    normalized = text.lower()
    if re.search(r"\bkunminghu[-_ ]?v3\b|\bkunminghuv3\b", normalized):
        return "kunminghu-v3"
    if re.search(r"\bkunminghu[-_ ]?v2\b|\bkunminghuv2\b", normalized):
        return "kunminghu-v2"
    return base or head or ""


def extract_commit(*texts: str) -> str:
    patterns = (
        r"\b(?:xiangshan|nemu)?\s*commit(?:\s+id|\s+sha)?\s*[:=\-]?\s*`?([0-9a-f]{7,40})`?\b",
        r"\bcommit(?:\s+id|\s+sha)?\s*[:=\-]?\s*`?([0-9a-f]{7,40})`?\b",
        r"\bsha\s*[:=\-]?\s*`?([0-9a-f]{7,40})`?\b",
    )
    for text in texts:
        text = text or ""
        for pattern in patterns:
            match = re.search(pattern, text, flags=re.IGNORECASE)
            if match:
                return match.group(1)
    return ""

def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8") as file:
        for row in rows:
            file.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")


def markdown_escape(text: str) -> str:
    return text.replace("|", "\\|").replace("\n", " ")


def write_index(path: Path, title: str, rows: list[dict[str, Any]], is_pr: bool) -> None:
    lines = [f"# {title}", ""]
    lines.append("| Number | State | Branch | Commit | Updated | Labels | Heuristic causes | Title |")
    lines.append("| --- | --- | --- | --- | --- | --- | --- | --- |")
    for row in sorted(rows, key=lambda item: item.get("updated_at") or "", reverse=True):
        number = f"[#{row['number']}]({row['html_url']})"
        labels = ", ".join(row.get("labels") or [])
        causes = ", ".join(row.get("heuristic_causes") or [])
        title_cell = markdown_escape(row.get("title") or "")
        lines.append(
            f"| {number} | {row.get('state')} | {markdown_escape(row.get('branch') or '')} | "
            f"{markdown_escape(row.get('commit') or '')} | {row.get('updated_at') or ''} | "
            f"{markdown_escape(labels)} | {markdown_escape(causes)} | {title_cell} |"
        )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_cause_summary(path: Path, issues: list[dict[str, Any]], pulls: list[dict[str, Any]]) -> None:
    buckets: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in issues + pulls:
        for cause in row.get("heuristic_causes") or ["uncategorized"]:
            buckets[cause].append(row)

    counts = Counter({cause: len(rows) for cause, rows in buckets.items()})
    lines = ["# Bug Cause Summary", "", "Heuristic categories generated from titles, labels, and bodies.", ""]
    lines.append("| Cause | Count | Recent examples |")
    lines.append("| --- | ---: | --- |")
    for cause, count in counts.most_common():
        examples = sorted(buckets[cause], key=lambda item: item.get("updated_at") or "", reverse=True)[:5]
        links = ", ".join(f"[#{item['number']}]({item['html_url']})" for item in examples)
        lines.append(f"| {markdown_escape(cause)} | {count} | {links} |")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_readme(path: Path, args: argparse.Namespace, default_branch: str, issue_count: int, pr_count: int) -> None:
    branch_scope = "all PR base branches" if args.all_pr_branches else f"PR base branch `{args.branch or default_branch}`"
    lines = [
        "# XiangShan Bug Library",
        "",
        f"Generated at `{dt.datetime.now(dt.timezone.utc).isoformat()}`.",
        "",
        f"- Repository: `{args.repo}`",
        f"- Issue state: `{args.state}`",
        f"- PR scope: {branch_scope}",
        f"- Since: `{args.since or 'not set'}`",
        f"- Issues collected: `{issue_count}`",
        f"- PRs collected: `{pr_count}`",
        f"- Comments included: `{bool(args.include_comments)}`",
        "",
        "## Files",
        "",
        "- `issues.jsonl`: raw non-PR issue records.",
        "- `pulls.jsonl`: raw pull request records.",
        "- `comments.jsonl`: comments, only when comment collection is enabled.",
        "- `issue-index.md`: issue triage table with branch/commit columns.",
        "- `pr-index.md`: PR triage table with branch/commit columns.",
        "- `bug-cause-summary.md`: heuristic cause buckets and examples.",
        "",
        "## Next Analysis Step",
        "",
        "Read the indexes and raw JSONL, then convert heuristic causes into evidence-backed root-cause notes for the target bug or module.",
    ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def fetch_comments(client: GitHubClient, repo: str, rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    comments: list[dict[str, Any]] = []
    for row in rows:
        path = f"/repos/{repo}/issues/{row['number']}/comments"
        for comment in client.paginate(path, {"per_page": 100}):
            comments.append(
                {
                    "number": row["number"],
                    "type": row["type"],
                    "comment_id": comment.get("id"),
                    "user": (comment.get("user") or {}).get("login"),
                    "created_at": comment.get("created_at"),
                    "updated_at": comment.get("updated_at"),
                    "html_url": comment.get("html_url"),
                    "body": comment.get("body") or "",
                }
            )
    return comments


def main() -> int:
    args = parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    client = GitHubClient()
    repo_info, _ = client.get(f"/repos/{args.repo}")
    default_branch = repo_info.get("default_branch") or "master"
    branch = args.branch or default_branch

    common_params = {"state": args.state, "per_page": 100, "sort": "updated", "direction": "desc"}
    if args.since:
        common_params["since"] = f"{args.since}T00:00:00Z"

    issue_rows: list[dict[str, Any]] = []
    for item in client.paginate(f"/repos/{args.repo}/issues", common_params, args.limit):
        if "pull_request" in item:
            continue
        issue_rows.append(compact_issue(item))

    pull_params = dict(common_params)
    if not args.all_pr_branches:
        pull_params["base"] = branch
    pull_rows = [compact_pull(item) for item in client.paginate(f"/repos/{args.repo}/pulls", pull_params, args.limit)]

    write_jsonl(out / "issues.jsonl", issue_rows)
    write_jsonl(out / "pulls.jsonl", pull_rows)
    write_index(out / "issue-index.md", "XiangShan Issues", issue_rows, is_pr=False)
    write_index(out / "pr-index.md", "XiangShan Pull Requests", pull_rows, is_pr=True)
    write_cause_summary(out / "bug-cause-summary.md", issue_rows, pull_rows)
    write_readme(out / "README.md", args, default_branch, len(issue_rows), len(pull_rows))

    if args.include_comments:
        comments = fetch_comments(client, args.repo, issue_rows + pull_rows)
        write_jsonl(out / "comments.jsonl", comments)

    print(f"Wrote {len(issue_rows)} issues and {len(pull_rows)} PRs to {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
