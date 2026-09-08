#!/usr/bin/env python3
"""Fetch riscv-boom bug issues/PRs into ProcBugSpace's historical bug layout."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import unquote, urlencode, urlparse
from urllib.request import Request, urlopen


API_ROOT = "https://api.github.com"
DEFAULT_REPO = "riscv-boom/riscv-boom"
DEFAULT_BUG_LABELS = ("bug", "performance bug")
COMMIT_LOG_FILENAME = "commit-log.md"
HTTP_404_LOG_FILENAME = "http-404.log"
FETCH_ERROR_LOG_FILENAME = "fetch-errors.log"
LANGUAGE_EXTENSIONS = {
    "asm": ".S",
    "assembly": ".S",
    "c": ".c",
    "c++": ".cpp",
    "cpp": ".cpp",
    "h": ".h",
    "python": ".py",
    "py": ".py",
    "riscv": ".S",
    "riscv64": ".S",
    "scala": ".scala",
    "shell": ".sh",
    "sh": ".sh",
    "systemverilog": ".sv",
    "sv": ".sv",
    "verilog": ".v",
    "v": ".v",
}
FENCE_RE = re.compile(r"(?ms)^\s*```([^\n`]*)\n(.*?)^\s*```\s*$")
URL_RE = re.compile(r"https?://[^\s<>()]+")
GITHUB_PULL_URL_RE = re.compile(r"https?://github\.com/[^/\s]+/[^/\s]+/pull/(\d+)")
PULL_REQUEST_REF_RE = re.compile(r"(?i)\b(?:pr|pull request|pull)\s*#(\d+)\b")
RTL_COMMIT_RE = re.compile(
    r"(?im)^\s*[-*]?\s*(?:BOOM|riscv-boom|RTL|difftest|checkout|test(?:ing)?(?:\s+RTL)?)"
    r"\s+commit(?:\s+id)?\s*:\s*`?([0-9a-fA-F]{7,40})`?"
)
BRANCH_HEADER_RE = re.compile(r"(?i)^#{1,6}\s*branch\s*$")
ARCHIVE_SUFFIXES = (".zip", ".tar", ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".tar.xz", ".txz", ".7z", ".rar")
ARCHIVE_CONTENT_TYPES = (
    "application/zip",
    "application/x-tar",
    "application/gzip",
    "application/x-gzip",
    "application/x-7z-compressed",
    "application/vnd.rar",
)


@dataclass
class CodeBlock:
    language: str
    source: str
    index: int


@dataclass
class RequestLimiter:
    max_requests: int | None
    min_delay: float
    request_count: int = 0
    last_request_at: float | None = None

    def wait_for_slot(self) -> None:
        if self.max_requests is not None and self.request_count >= self.max_requests:
            raise RuntimeError(f"request limit reached ({self.max_requests})")
        if self.last_request_at is not None and self.min_delay > 0:
            elapsed = time.monotonic() - self.last_request_at
            remaining = self.min_delay - elapsed
            if remaining > 0:
                time.sleep(remaining)
        self.request_count += 1
        self.last_request_at = time.monotonic()


def default_output_dir() -> Path:
    script = Path(__file__).resolve()
    # .../2026/XiangShanLab/tools/xiangshan-bugs-analyzer/fetch-xiangshan-bugs/scripts
    year_dir = script.parents[5]
    return year_dir / "ProcBugSpace" / "database" / "historical_bugs" / "boom"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default=DEFAULT_REPO, help="GitHub repository owner/name")
    parser.add_argument("--output", type=Path, default=default_output_dir(), help="Output directory")
    parser.add_argument("--state", choices=("open", "closed", "all"), default="all")
    parser.add_argument(
        "--label",
        action="append",
        default=[],
        help="Bug label to fetch; repeatable. Defaults to BOOM's known bug labels.",
    )
    parser.add_argument("--issue", action="append", default=[], help="Fetch only these issue/PR numbers; accepts N or START-END")
    parser.add_argument("--number", action="append", default=[], help="Alias of --issue")
    parser.add_argument("--include-comments", action="store_true", help="Fetch comments and extract fenced code from them")
    parser.add_argument("--no-download-attachments", action="store_true", help="Do not download archive attachments")
    parser.add_argument("--no-pr-diffs", action="store_true", help="Do not write commit-log.md/commit-log.diff for PRs")
    parser.add_argument("--force-refresh", action="store_true", help="Rewrite issue directories even if metadata already exists")
    parser.add_argument("--token", default=os.getenv("GITHUB_TOKEN") or os.getenv("GH_TOKEN"), help="GitHub token")
    parser.add_argument("--max-requests", type=int, default=None, help="Stop after this many GitHub API requests")
    parser.add_argument("--min-delay", type=float, default=0.2, help="Minimum delay between GitHub API requests")
    return parser.parse_args()


def github_headers(token: str | None) -> dict[str, str]:
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "boom-bug-fetcher",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def fetch_text(url: str, token: str | None = None, accept: str = "text/plain", timeout: int = 60) -> str:
    headers = {"Accept": accept, "User-Agent": "boom-bug-fetcher"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = Request(url, headers=headers)
    with urlopen(request, timeout=timeout) as response:
        charset = response.headers.get_content_charset() or "utf-8"
        return response.read().decode(charset, errors="replace")


def github_get_page(url: str, token: str | None, limiter: RequestLimiter) -> tuple[object, str | None]:
    for attempt in range(4):
        limiter.wait_for_slot()
        request = Request(url, headers=github_headers(token))
        try:
            with urlopen(request, timeout=60) as response:
                link_header = response.headers.get("Link", "")
                next_match = re.search(r'<([^>]+)>;\s*rel="next"', link_header)
                return json.load(response), next_match.group(1) if next_match else None
        except HTTPError as error:
            message = error.read().decode("utf-8", errors="replace")
            if error.code == 403 and "rate limit" in message.lower():
                raise RuntimeError("GitHub API rate limit exceeded; set GITHUB_TOKEN or resume later") from error
            if attempt == 3 or error.code not in {429, 500, 502, 503, 504}:
                raise RuntimeError(f"GitHub API request failed ({error.code}) for {url}: {message[:300]}") from error
        except (TimeoutError, URLError) as error:
            if attempt == 3:
                raise RuntimeError(f"Cannot connect to GitHub for {url}: {error}") from error
        time.sleep(2**attempt)
    raise RuntimeError(f"Cannot fetch {url}")


def github_get(url: str, token: str | None, limiter: RequestLimiter) -> object:
    payload, _ = github_get_page(url, token, limiter)
    return payload


def fetch_issue_items_by_label(repo: str, label: str, state: str, token: str | None, limiter: RequestLimiter) -> Iterable[dict]:
    query = urlencode({"state": state, "per_page": 100, "sort": "created", "direction": "desc", "labels": label})
    next_url = f"{API_ROOT}/repos/{repo}/issues?{query}"
    while next_url:
        payload, next_url = github_get_page(next_url, token, limiter)
        if not isinstance(payload, list):
            raise RuntimeError(f"unexpected issue list payload for label {label!r}")
        for item in payload:
            if isinstance(item, dict):
                yield item


def fetch_issue(repo: str, number: int, token: str | None, limiter: RequestLimiter, output: Path) -> dict | None:
    url = f"{API_ROOT}/repos/{repo}/issues/{number}"
    try:
        payload = github_get(url, token, limiter)
    except RuntimeError as error:
        append_fetch_error_log(output, "issue/PR", number, str(error))
        return None
    return payload if isinstance(payload, dict) else None


def is_pull_request_item(item: dict) -> bool:
    return "pull_request" in item


def parse_issue_number_specs(specs: Iterable[str]) -> list[int]:
    numbers: list[int] = []
    seen = set()
    for raw_spec in specs:
        spec = str(raw_spec).strip()
        if not spec:
            continue
        match = re.fullmatch(r"(\d+)(?:\s*-\s*(\d+))?", spec)
        if not match:
            raise ValueError(f"invalid issue/PR number spec {spec!r}; use N or START-END")
        start = int(match.group(1))
        end = int(match.group(2)) if match.group(2) else start
        step = 1 if end >= start else -1
        for number in range(start, end + step, step):
            if number <= 0:
                raise ValueError(f"invalid issue/PR number {number}; numbers must be positive")
            if number not in seen:
                seen.add(number)
                numbers.append(number)
    return numbers


def safe_name(value: str) -> str:
    value = re.sub(r"[^A-Za-z0-9._-]+", "-", value).strip("-.")
    return value or "issue"


def extract_code_blocks(markdown: str) -> list[CodeBlock]:
    blocks: list[CodeBlock] = []
    for index, match in enumerate(FENCE_RE.finditer(markdown or ""), start=1):
        language = match.group(1).strip().split()[0].lower() if match.group(1).strip() else "text"
        source = match.group(2).replace("\r\n", "\n").rstrip() + "\n"
        if source.strip():
            blocks.append(CodeBlock(language, source, index))
    return blocks


def source_filename(block: CodeBlock) -> str:
    extension = LANGUAGE_EXTENSIONS.get(block.language, ".txt")
    return f"program-{block.index:02d}{extension}"


def archive_urls(markdown: str) -> list[str]:
    urls = []
    seen = set()
    for raw_url in URL_RE.findall(markdown or ""):
        url = raw_url.rstrip(".,;:!?\"'")
        parsed = urlparse(url)
        host = (parsed.hostname or "").lower()
        path = unquote(parsed.path).lower()
        is_github_attachment = host.endswith("github.com") and "/user-attachments/files/" in path
        is_archive = path.endswith(ARCHIVE_SUFFIXES)
        if (is_archive or is_github_attachment) and url not in seen:
            seen.add(url)
            urls.append(url)
    return urls


def append_http_404_log(log_path: Path, issue_number: int, url: str, message: str) -> None:
    timestamp = datetime.now(timezone.utc).isoformat()
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("a", encoding="utf-8") as log_file:
        log_file.write(f"{timestamp}\tissue #{issue_number}\t{url}\t{message}\n")


def append_fetch_error_log(output: Path, item_kind: str, item_number: int, message: str) -> None:
    timestamp = datetime.now(timezone.utc).isoformat()
    log_path = output / FETCH_ERROR_LOG_FILENAME
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("a", encoding="utf-8") as log_file:
        log_file.write(f"{timestamp}\t{item_kind} #{item_number}\t{message}\n")


def download_archive(
    url: str,
    destination: Path,
    token: str | None,
    issue_number: int,
    http_404_log: Path,
) -> str | None:
    headers = {"User-Agent": "boom-bug-fetcher", "Accept": "application/octet-stream"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    try:
        request = Request(url, headers=headers)
        with urlopen(request, timeout=90) as response:
            content_type = response.headers.get_content_type().lower()
            content_disposition = response.headers.get("Content-Disposition", "")
            filename_match = re.search(r"filename\*?=(?:UTF-8''|\")?([^;\"]+)", content_disposition, re.IGNORECASE)
            filename = unquote(filename_match.group(1).strip().strip('"')) if filename_match else Path(unquote(urlparse(url).path)).name
            filename = safe_name(filename)
            if not filename or filename == "issue":
                filename = "reproducer.zip"
            if not filename.lower().endswith(ARCHIVE_SUFFIXES) and content_type in ARCHIVE_CONTENT_TYPES:
                filename += ".tar.gz" if content_type in {"application/gzip", "application/x-gzip"} else ".zip"
            output = destination / filename
            if output.exists():
                return output.name
            output.write_bytes(response.read())
            return output.name
    except HTTPError as error:
        if error.code == 404:
            append_http_404_log(http_404_log, issue_number, url, str(error))
        print(f"warning: cannot download attachment for issue #{issue_number}: {url}: {error}", file=sys.stderr)
    except (TimeoutError, URLError) as error:
        print(f"warning: cannot download attachment for issue #{issue_number}: {url}: {error}", file=sys.stderr)
    return None


def fetch_comments(issue: dict, token: str | None, limiter: RequestLimiter) -> str:
    comments_url = issue.get("comments_url")
    if not issue.get("comments") or not isinstance(comments_url, str):
        return ""
    comments = []
    next_url: str | None = f"{comments_url}?per_page=100"
    while next_url:
        payload, next_url = github_get_page(next_url, token, limiter)
        if not isinstance(payload, list):
            break
        for comment in payload:
            if isinstance(comment, dict):
                user = (comment.get("user") or {}).get("login", "unknown")
                comments.append(f"<!-- comment by {user} -->\n{comment.get('body') or ''}")
    return "\n\n".join(comments)


def extract_branch(markdown: str) -> str:
    lines = (markdown or "").splitlines()
    for index, line in enumerate(lines):
        if not BRANCH_HEADER_RE.match(line.strip()):
            continue
        for next_line in lines[index + 1 :]:
            value = next_line.strip()
            if not value:
                continue
            if value.startswith("#"):
                return ""
            return value.lstrip("-* ").strip("`")
    return ""


def extract_rtl_commit(markdown: str) -> str:
    match = RTL_COMMIT_RE.search(markdown or "")
    return match.group(1).lower() if match else ""


def diff_summary(diff_text: str) -> dict:
    files: list[str] = []
    seen = set()
    additions = 0
    deletions = 0
    for line in (diff_text or "").splitlines():
        if line.startswith("diff --git "):
            match = re.match(r"diff --git a/(.+?) b/(.+)$", line)
            if match:
                name = match.group(2)
                if name not in seen:
                    seen.add(name)
                    files.append(name)
            continue
        if line.startswith("+++") or line.startswith("---") or line.startswith("@@"):
            continue
        if line.startswith("+"):
            additions += 1
        elif line.startswith("-"):
            deletions += 1
    return {"files": files, "additions": additions, "deletions": deletions}


def pull_diff_url(repo: str, pull_number: int, issue: dict | None = None) -> str:
    if issue:
        pull_request = issue.get("pull_request") or {}
        diff_url = pull_request.get("diff_url")
        if isinstance(diff_url, str) and diff_url:
            return diff_url
    return f"https://github.com/{repo}/pull/{pull_number}.diff"


def write_commit_log(output: Path, repo: str, issue: dict, pr_number: int, diff_text: str, rtl_commit: str = "") -> Path:
    issue_number = int(issue["number"])
    issue_dir = output / f"issue-{issue_number}"
    issue_dir.mkdir(parents=True, exist_ok=True)
    summary = diff_summary(diff_text)
    pr_url = f"https://github.com/{repo}/pull/{pr_number}"
    diff_path = issue_dir / "commit-log.diff"
    diff_path.write_text((diff_text.rstrip() + "\n") if diff_text else "", encoding="utf-8")
    lines = [
        "# Commit Log",
        f"- Issue: #{issue_number}",
        f"- Issue URL: {issue.get('html_url', '')}",
        f"- Issue state: {issue.get('state', '')}",
        f"- Tested RTL commit: {rtl_commit or '-'}",
        f"- Related PR: #{pr_number}",
        f"- PR URL: {pr_url}",
        f"- Changed files: {len(summary['files'])}",
        f"- Additions: {summary['additions']}",
        f"- Deletions: {summary['deletions']}",
        "",
    ]
    if summary["files"]:
        lines.append("## Files")
        lines.extend(f"- `{name}`" for name in summary["files"])
        lines.append("")
    lines.extend(["## Diff", "```diff", diff_text.rstrip(), "```", ""])
    commit_log_path = issue_dir / COMMIT_LOG_FILENAME
    commit_log_path.write_text("\n".join(lines), encoding="utf-8")
    return commit_log_path


def related_pull_request_numbers(text: str) -> list[int]:
    numbers: list[int] = []
    seen = set()
    for pattern in (GITHUB_PULL_URL_RE, PULL_REQUEST_REF_RE):
        for match in pattern.findall(text or ""):
            number = int(match)
            if number not in seen:
                seen.add(number)
                numbers.append(number)
    return numbers


def completed_record(output: Path, issue_number: int) -> dict | None:
    metadata_path = output / f"issue-{issue_number}" / "metadata.json"
    description_path = output / f"issue-{issue_number}" / "description.md"
    if not metadata_path.exists() or not description_path.exists():
        return None
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return None
    if isinstance(metadata, dict) and int(metadata.get("number", -1)) == issue_number:
        return metadata
    return None


def write_issue(output: Path, issue: dict, comments: str, args: argparse.Namespace) -> dict:
    issue_id = f"issue-{issue['number']}"
    issue_dir = output / issue_id
    source_dir = issue_dir / "source"
    source_dir.mkdir(parents=True, exist_ok=True)

    body = issue.get("body") or ""
    description = body.strip() or "(issue body is empty)"
    (issue_dir / "description.md").write_text(description + "\n", encoding="utf-8")

    all_markdown = body + ("\n\n" + comments if comments else "")
    archive_files = []
    if not args.no_download_attachments:
        for url in archive_urls(all_markdown):
            filename = download_archive(url, source_dir, args.token, int(issue["number"]), output / HTTP_404_LOG_FILENAME)
            if filename:
                archive_files.append(str(Path(issue_id) / "source" / filename))

    source_files = []
    for block in extract_code_blocks(all_markdown):
        filename = source_filename(block)
        (source_dir / filename).write_text(block.source, encoding="utf-8")
        source_files.append(str(Path(issue_id) / "source" / filename))

    metadata = {
        "number": issue["number"],
        "title": issue.get("title", ""),
        "url": issue.get("html_url", ""),
        "state": issue.get("state", ""),
        "labels": [label.get("name", "") for label in issue.get("labels", [])],
        "author": (issue.get("user") or {}).get("login", ""),
        "created_at": issue.get("created_at", ""),
        "updated_at": issue.get("updated_at", ""),
        "submitted_at": issue.get("created_at", "")[:10],
        "source_files": archive_files + source_files,
        "archive_files": archive_files,
        "branch": extract_branch(description),
        "rtl_commit": extract_rtl_commit(description),
        "has_comments": bool(issue.get("comments")),
        "is_pull_request": is_pull_request_item(issue),
        "has_commit_log": False,
        "commit_log_files": [],
    }
    (issue_dir / "metadata.json").write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return metadata


def update_commit_log_metadata(output: Path, issue_number: int, commit_log_path: Path) -> dict | None:
    metadata_path = output / f"issue-{issue_number}" / "metadata.json"
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return None
    metadata["has_commit_log"] = True
    metadata["commit_log_files"] = [str(commit_log_path.relative_to(output))]
    metadata_path.write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return metadata


def maybe_write_commit_log(output: Path, repo: str, issue: dict, args: argparse.Namespace) -> Path | None:
    if args.no_pr_diffs or str(issue.get("state", "")).lower() != "closed":
        return None
    issue_number = int(issue["number"])
    if is_pull_request_item(issue):
        candidate_prs = [issue_number]
    else:
        candidate_prs = related_pull_request_numbers("\n".join([issue.get("title") or "", issue.get("body") or ""]))
    for pr_number in candidate_prs:
        try:
            diff_text = fetch_text(
                pull_diff_url(repo, pr_number, issue if pr_number == issue_number else None),
                args.token,
                "application/vnd.github.v3.diff, text/plain;q=0.9, */*;q=0.1",
            )
        except HTTPError as error:
            if error.code == 404:
                continue
            append_fetch_error_log(output, "PR diff", pr_number, str(error))
            continue
        except (TimeoutError, URLError) as error:
            append_fetch_error_log(output, "PR diff", pr_number, str(error))
            continue
        if diff_text.strip():
            return write_commit_log(output, repo, issue, pr_number, diff_text, extract_rtl_commit(issue.get("body") or ""))
    return None


def markdown_escape(value: object) -> str:
    return str(value if value is not None else "").replace("|", "\\|").replace("\n", " ") or "-"


def write_summary(output: Path, repo: str, labels: list[str], records: list[dict], api_requests: int) -> None:
    generated = datetime.now(timezone.utc).isoformat()
    issues = [record for record in records if not record.get("is_pull_request")]
    pull_requests = [record for record in records if record.get("is_pull_request")]
    open_count = sum(1 for record in records if record.get("state") == "open")
    closed_count = sum(1 for record in records if record.get("state") == "closed")
    commit_log_count = sum(1 for record in records if record.get("has_commit_log"))
    lines = [
        "# BOOM Bug Summary",
        "",
        f"- Repository: `{repo}`",
        f"- Labels: `{', '.join(labels)}`",
        f"- Generated at: `{generated}`",
        f"- Bug items: **{len(records)}** (issues: **{len(issues)}**, PRs: **{len(pull_requests)}**)",
        f"- State counts: open **{open_count}**, closed **{closed_count}**",
        f"- Commit logs: **{commit_log_count}**",
        f"- GitHub API requests: **{api_requests}**",
        "",
        "## Items",
        "",
        "| Number | Type | State | Title | Author | Submitted at | Source files | Commit log |",
        "|---:|---|---|---|---|---|---:|---|",
    ]
    for record in sorted(records, key=lambda item: int(item.get("number", 0)), reverse=True):
        item_type = "PR" if record.get("is_pull_request") else "Issue"
        source_count = len(record.get("source_files") or [])
        has_commit_log = "yes" if record.get("has_commit_log") else "no"
        lines.append(
            f"| [#{record['number']}]({record.get('url', '')}) | {item_type} | {record.get('state', '-')} | "
            f"{markdown_escape(record.get('title', ''))} | {markdown_escape(record.get('author', '-'))} | "
            f"{record.get('submitted_at', '-')} | {source_count} | {has_commit_log} |"
        )
    (output / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (output / "summary.json").write_text(
        json.dumps(
            {
                "repository": repo,
                "labels": labels,
                "generated_at": generated,
                "api_requests": api_requests,
                "item_count": len(records),
                "issue_count": len(issues),
                "pull_request_count": len(pull_requests),
                "open_count": open_count,
                "closed_count": closed_count,
                "commit_log_count": commit_log_count,
                "items": records,
            },
            indent=2,
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )


def collect_items(args: argparse.Namespace, limiter: RequestLimiter) -> list[dict]:
    requested_numbers = parse_issue_number_specs([*args.issue, *args.number])
    if requested_numbers:
        items = []
        for number in requested_numbers:
            item = fetch_issue(args.repo, number, args.token, limiter, args.output)
            if item is not None:
                items.append(item)
        return sorted(items, key=lambda item: int(item.get("number", 0)), reverse=True)

    labels = args.label or list(DEFAULT_BUG_LABELS)
    merged: dict[int, dict] = {}
    for label in labels:
        for item in fetch_issue_items_by_label(args.repo, label, args.state, args.token, limiter):
            merged[int(item["number"])] = item
    return [merged[number] for number in sorted(merged, reverse=True)]


def main() -> int:
    args = parse_args()
    if args.max_requests is not None and args.max_requests <= 0:
        print("error: --max-requests must be greater than 0", file=sys.stderr)
        return 2
    if args.min_delay < 0:
        print("error: --min-delay must be non-negative", file=sys.stderr)
        return 2
    labels = args.label or list(DEFAULT_BUG_LABELS)
    args.output.mkdir(parents=True, exist_ok=True)
    limiter = RequestLimiter(args.max_requests, args.min_delay)
    try:
        items = collect_items(args, limiter)
        records = []
        for position, issue in enumerate(items, start=1):
            number = int(issue["number"])
            existing = completed_record(args.output, number)
            if existing is not None and not args.force_refresh:
                record = existing
                print(f"[{position}/{len(items)}] issue #{number}: reused existing local content")
            else:
                comments = fetch_comments(issue, args.token, limiter) if args.include_comments else ""
                record = write_issue(args.output, issue, comments, args)
                print(f"[{position}/{len(items)}] issue #{number}: {len(record['source_files'])} source file(s)")
            commit_log_path = maybe_write_commit_log(args.output, args.repo, issue, args)
            if commit_log_path is not None:
                updated = update_commit_log_metadata(args.output, number, commit_log_path)
                if updated is not None:
                    record = updated
            records.append(record)
        write_summary(args.output, args.repo, labels, records, limiter.request_count)
        print(
            f"Saved {len(records)} BOOM bug item(s) to {args.output}; "
            f"commit logs: {sum(1 for record in records if record.get('has_commit_log'))}; "
            f"GitHub API requests: {limiter.request_count}"
        )
        return 0
    except (RuntimeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
