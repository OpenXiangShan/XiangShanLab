#!/usr/bin/env python3
"""Fetch XiangShan bug issues, PRs, reproducer programs, and closed-PR commit logs from GitHub."""

from __future__ import annotations

import argparse
import json
import os
import random
import re
import shutil
import ssl
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from html import unescape
from html.parser import HTMLParser
from pathlib import Path
from typing import Iterable
from urllib.error import HTTPError, URLError
from http.client import RemoteDisconnected
from urllib.parse import unquote, urlencode, urljoin, urlparse
from urllib.request import Request, urlopen


API_ROOT = "https://api.github.com"
BIWEEKLY_EN_CATEGORY_URL = "https://docs.xiangshan.cc/zh-cn/latest/blog/category/biweekly-en/"
STATE_FILENAME = ".fetch-state.json"
HTTP_404_LOG_FILENAME = "http-404.log"
FETCH_ERROR_LOG_FILENAME = "fetch-errors.log"
FIRST_FETCH_ERROR_FILENAME = "first-fetch-error.json"
COMMIT_LOG_FILENAME = "commit-log.md"
LANGUAGE_EXTENSIONS = {
    "asm": ".S",
    "assembly": ".S",
    "c": ".c",
    "c++": ".cpp",
    "cpp": ".cpp",
    "h": ".h",
    "haskell": ".hs",
    "python": ".py",
    "py": ".py",
    "riscv": ".S",
    "riscv64": ".S",
    "rust": ".rs",
    "scala": ".scala",
    "shell": ".sh",
    "sh": ".sh",
    "verilog": ".v",
    "v": ".v",
    "vhdl": ".vhd",
}
FENCE_RE = re.compile(r"(?ms)^\s*```([^\n`]*)\n(.*?)^\s*```\s*$")
URL_RE = re.compile(r"https?://[^\s<>()]+")
HREF_RE = re.compile(r"href=[\"']([^\"']+)[\"']", re.IGNORECASE)
BIWEEKLY_EN_ARTICLE_RE = re.compile(r"/blog/\d{4}/\d{2}/\d{2}/biweekly-\d+-en/?\Z")
GITHUB_ISSUE_URL_RE = re.compile(r"https?://github\.com/OpenXiangShan/XiangShan/(?:issues|pull)/(\d+)")
GITHUB_PULL_URL_RE = re.compile(r"https?://github\.com/OpenXiangShan/XiangShan/pull/(\d+)")
PULL_REQUEST_REF_RE = re.compile(r"(?i)\b(?:pr|pull request|pull)\s*#(\d+)\b")
RTL_COMMIT_RE = re.compile(
    r"(?im)^\s*[-*]?\s*(?:XiangShan|RTL|difftest|checkout|test(?:ing)?(?:\s+RTL)?)\s+commit(?:\s+id)?\s*:\s*`?([0-9a-fA-F]{7,40})`?"
)
BRANCH_HEADER_RE = re.compile(r"(?i)^#{1,6}\s*branch\s*$")
BUG_CONTEXT_KEYWORDS = (
    "bug",
    "fix",
    "fixed",
    "fixes",
    "failure",
    "failed",
    "incorrect",
    "issue",
    "mismatch",
    "wrong",
)
NON_BUG_SECTION_TITLES = {
    "rtl features",
    "ppa optimizations",
    "code quality",
    "code refactoring",
    "debugging tools",
    "performance evaluation",
    "related links",
}
ARCHIVE_SUFFIXES = (".zip", ".tar", ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".tar.xz", ".txz", ".7z", ".rar")
ARCHIVE_CONTENT_TYPES = ("application/zip", "application/x-tar", "application/gzip", "application/x-gzip", "application/x-7z-compressed", "application/vnd.rar")
BOSC_IPV6_ENV = "XIANGSHAN_BUG_FETCHER_BOSC_IPV6"
BUG_CATEGORY_ORDER = ("frontend", "backend", "mem/cache", "uncategorized")
BUG_CATEGORY_LABELS = {
    "frontend": "Frontend",
    "backend": "Backend",
    "mem/cache": "Mem/Cache",
    "uncategorized": "Uncategorized",
}
RETRYABLE_NETWORK_ERROR_PATTERNS = (
    "connection refused",
    "cannot connect to github",
    "cannot fetch https://github.com",
    "github api response timed out",
    "github api connection closed",
    "github api ssl connection failed",
    "ssl",
    "unexpected_eof_while_reading",
    "eof occurred in violation of protocol",
    "urlopen error",
    "timed out",
    "temporary failure",
    "connection reset",
)
NETWORK_ERRORS = (RemoteDisconnected, TimeoutError, URLError, ssl.SSLError)


class GithubNotFoundError(RuntimeError):
    """Raised when a requested GitHub issue or PR number does not exist."""

    def __init__(self, repo: str, number: int, message: str) -> None:
        super().__init__(f"{repo} issue/PR #{number} not found: {message}")
        self.repo = repo
        self.number = number


@dataclass
class CodeBlock:
    language: str
    source: str
    index: int


@dataclass
class HtmlBlock:
    text: str
    hrefs: list[str]
    tag: str


@dataclass
class RequestLimiter:
    max_requests: int | None
    min_delay: float
    max_delay: float
    request_count: int = 0
    last_request_at: float | None = None

    def wait_for_slot(self) -> None:
        if self.max_requests is not None and self.request_count >= self.max_requests:
            raise RuntimeError(f"Request limit reached ({self.max_requests}); stop before hitting GitHub limits")
        if self.last_request_at is not None and self.max_delay > 0:
            delay = random.uniform(self.min_delay, self.max_delay)
            elapsed = time.monotonic() - self.last_request_at
            remaining = delay - elapsed
            if remaining > 0:
                time.sleep(remaining)
        self.request_count += 1
        self.last_request_at = time.monotonic()


def fetch_text(url: str, token: str | None, limiter: RequestLimiter, accept: str, timeout: int = 30) -> str:
    headers = {"User-Agent": "xiangshan-bug-fetcher", "Accept": accept}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    for attempt in range(4):
        limiter.wait_for_slot()
        request = Request(url, headers=headers)
        try:
            with urlopen(request, timeout=timeout) as response:
                charset = response.headers.get_content_charset() or "utf-8"
                return response.read().decode(charset, errors="replace")
        except (HTTPError, *NETWORK_ERRORS) as error:
            if attempt == 3:
                raise RuntimeError(f"Cannot fetch {url}: {error}") from error
            time.sleep(2 ** attempt)


def github_get_page(url: str, token: str | None, limiter: RequestLimiter) -> tuple[object, str | None]:
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "xiangshan-bug-fetcher",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    for attempt in range(4):
        limiter.wait_for_slot()
        request = Request(url, headers=headers)
        try:
            with urlopen(request, timeout=30) as response:
                link_header = response.headers.get("Link", "")
                next_match = re.search(r'<([^>]+)>;\s*rel="next"', link_header)
                return json.load(response), next_match.group(1) if next_match else None
        except HTTPError as error:
            message = error.read().decode("utf-8", errors="replace")
            if error.code == 403 and "rate limit" in message.lower():
                raise RuntimeError("GitHub API rate limit exceeded; set GITHUB_TOKEN") from error
            raise RuntimeError(f"GitHub API request failed ({error.code}): {message[:300]}") from error
        except NETWORK_ERRORS as error:
            if attempt == 3:
                if isinstance(error, TimeoutError):
                    raise RuntimeError("GitHub API response timed out after 4 attempts") from error
                if isinstance(error, RemoteDisconnected):
                    raise RuntimeError("GitHub API connection closed without a response") from error
                if isinstance(error, ssl.SSLError):
                    raise RuntimeError(f"GitHub API SSL connection failed after 4 attempts: {error}") from error
                reason = getattr(error, "reason", error)
                raise RuntimeError(f"Cannot connect to GitHub: {reason}") from error
            time.sleep(2 ** attempt)


def github_get(url: str, token: str | None, limiter: RequestLimiter) -> object:
    payload, _ = github_get_page(url, token, limiter)
    return payload


def github_get_issue(repo: str, number: int, token: str | None, limiter: RequestLimiter) -> dict:
    try:
        payload = github_get(f"{API_ROOT}/repos/{repo}/issues/{number}", token, limiter)
    except RuntimeError as error:
        message = str(error)
        if "GitHub API request failed (404)" in message:
            raise GithubNotFoundError(repo, number, message) from error
        raise
    if not isinstance(payload, dict):
        raise RuntimeError(f"unexpected issue payload for #{number}")
    return payload


def fetch_html(url: str, limiter: RequestLimiter) -> str:
    return fetch_text(url, None, limiter, "text/html")


def html_links(html: str, base_url: str) -> list[str]:
    links = []
    seen = set()
    for href in HREF_RE.findall(html or ""):
        url = urljoin(base_url, unescape(href)).split("#", 1)[0]
        if url and url not in seen:
            seen.add(url)
            links.append(url)
    return links


class HtmlBlockParser(HTMLParser):
    block_tags = {"p", "li", "h1", "h2", "h3", "h4", "h5", "h6"}

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.blocks: list[HtmlBlock] = []
        self.current_tag: str | None = None
        self.text_parts: list[str] = []
        self.hrefs: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in self.block_tags:
            self._flush()
            self.current_tag = tag
        if tag == "a":
            for name, value in attrs:
                if name.lower() == "href" and value:
                    self.hrefs.append(value)

    def handle_endtag(self, tag: str) -> None:
        if tag == self.current_tag:
            self._flush()

    def handle_data(self, data: str) -> None:
        if data:
            self.text_parts.append(data)

    def _flush(self) -> None:
        text = " ".join("".join(self.text_parts).split())
        if text or self.hrefs:
            self.blocks.append(HtmlBlock(text=text, hrefs=self.hrefs, tag=self.current_tag or "text"))
        self.current_tag = None
        self.text_parts = []
        self.hrefs = []

    def close(self) -> None:
        super().close()
        self._flush()


def html_blocks(html: str) -> list[HtmlBlock]:
    parser = HtmlBlockParser()
    parser.feed(html or "")
    parser.close()
    return parser.blocks


def normalize_title(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def github_issue_numbers_from_links(hrefs: Iterable[str], base_url: str) -> set[int]:
    numbers = set()
    for href in hrefs:
        url = urljoin(base_url, unescape(href))
        match = GITHUB_ISSUE_URL_RE.search(url)
        if match:
            numbers.add(int(match.group(1)))
    return numbers


def extract_pull_request_numbers(text: str) -> list[int]:
    numbers: list[int] = []
    seen = set()
    for pattern in (GITHUB_PULL_URL_RE, PULL_REQUEST_REF_RE):
        for match in pattern.findall(text or ""):
            number = int(match)
            if number in seen:
                continue
            seen.add(number)
            numbers.append(number)
    return numbers


def biweekly_bug_category_from_heading(text: str) -> str | None:
    normalized = normalize_title(text)
    words = set(normalized.split())
    if not normalized:
        return None
    if "frontend" in words or "front end" in normalized:
        return "frontend"
    if "backend" in words or "back end" in normalized:
        return "backend"
    if (
        "memory" in words
        or "mem" in words
        or "cache" in words
        or "icache" in words
        or "dcache" in words
        or "l2" in words
        or "l3" in words
        or "lsu" in words
    ):
        return "mem/cache"
    return None


def add_biweekly_bug_reference(references: dict[int, set[str]], numbers: Iterable[int], category: str | None) -> None:
    bug_category = category or "uncategorized"
    for number in numbers:
        references.setdefault(number, set()).add(bug_category)


def extract_biweekly_bug_issue_references(html: str, base_url: str) -> dict[int, set[str]]:
    references: dict[int, set[str]] = {}
    current_category: str | None = None
    in_bug_context = False
    for block in html_blocks(html):
        normalized = normalize_title(block.text)
        words = set(normalized.split())
        has_bug_keyword = bool(words & set(BUG_CONTEXT_KEYWORDS))
        heading_category = biweekly_bug_category_from_heading(block.text) if block.tag.startswith("h") else None
        if heading_category:
            current_category = heading_category
        if normalized in {"bug fixes", "development stories"}:
            in_bug_context = True
        elif block.tag.startswith("h") and normalized not in {"recent developments"} and not heading_category:
            in_bug_context = False
        elif normalized in NON_BUG_SECTION_TITLES:
            in_bug_context = False
        if block.hrefs and (in_bug_context or has_bug_keyword):
            add_biweekly_bug_reference(
                references,
                github_issue_numbers_from_links(block.hrefs, base_url),
                current_category,
            )
    return references


def merge_biweekly_bug_references(target: dict[int, set[str]], source: dict[int, set[str]]) -> None:
    for number, categories in source.items():
        target.setdefault(number, set()).update(categories)


def fetch_biweekly_bug_issue_references(category_url: str, limiter: RequestLimiter) -> dict[int, set[str]]:
    category_pages = [category_url]
    seen_category_pages = set()
    article_urls = set()
    references: dict[int, set[str]] = {}
    while category_pages:
        page_url = category_pages.pop()
        if page_url in seen_category_pages:
            continue
        seen_category_pages.add(page_url)
        html = fetch_html(page_url, limiter)
        for link in html_links(html, page_url):
            parsed = urlparse(link)
            if BIWEEKLY_EN_ARTICLE_RE.search(parsed.path):
                article_urls.add(link)
            elif "/blog/category/biweekly-en/" in parsed.path and link not in seen_category_pages:
                category_pages.append(link)

    for article_url in sorted(article_urls):
        article_html = fetch_html(article_url, limiter)
        merge_biweekly_bug_references(references, extract_biweekly_bug_issue_references(article_html, article_url))
    return references


def fetch_biweekly_bug_issue_numbers(category_url: str, limiter: RequestLimiter) -> set[int]:
    return set(fetch_biweekly_bug_issue_references(category_url, limiter))


def fetch_issue_items(repo: str, state: str, token: str | None, limiter: RequestLimiter) -> Iterable[dict]:
    query = urlencode({"state": state, "per_page": 100, "sort": "created", "direction": "desc"})
    next_url = f"{API_ROOT}/repos/{repo}/issues?{query}"
    while next_url:
        payload, next_url = github_get_page(next_url, token, limiter)
        if not isinstance(payload, list) or not payload:
            return
        for issue in payload:
            yield issue


def is_pull_request_item(item: dict) -> bool:
    return "pull_request" in item


def is_bug_issue(issue: dict, requested_labels: set[str]) -> bool:
    labels = {str(label.get("name", "")).strip().lower() for label in issue.get("labels", [])}
    if requested_labels:
        return bool(labels & requested_labels)
    return any(label == "bug" or "bug" in label for label in labels)


def fetch_pull_request(issue: dict, token: str | None, limiter: RequestLimiter) -> dict:
    pull_request = issue.get("pull_request", {})
    url = pull_request.get("url")
    if not isinstance(url, str) or not url:
        raise RuntimeError(f"missing pull request API URL for #{issue.get('number')}")
    payload = github_get(url, token, limiter)
    if not isinstance(payload, dict):
        raise RuntimeError(f"unexpected pull request payload for #{issue.get('number')}")
    return payload


def fetch_pull_request_commits(commits_url: str, token: str | None, limiter: RequestLimiter) -> list[dict]:
    commits: list[dict] = []
    separator = "&" if "?" in commits_url else "?"
    next_url = f"{commits_url}{separator}per_page=100"
    while next_url:
        payload, next_url = github_get_page(next_url, token, limiter)
        if not isinstance(payload, list):
            return commits
        commits.extend(payload)
    return commits


def fetch_pull_request_diff(pull_request: dict, token: str | None, limiter: RequestLimiter) -> str:
    diff_url = pull_request.get("diff_url")
    if not isinstance(diff_url, str) or not diff_url:
        html_url = pull_request.get("html_url")
        if not isinstance(html_url, str) or not html_url:
            raise RuntimeError("missing pull request diff URL")
        diff_url = f"{html_url}.diff"
    return fetch_text(diff_url, token, limiter, "application/vnd.github.v3.diff, text/plain;q=0.9, */*;q=0.1", timeout=60)


def commit_record(commit: dict) -> dict:
    commit_data = commit.get("commit", {}) if isinstance(commit.get("commit"), dict) else {}
    author_data = commit_data.get("author", {}) if isinstance(commit_data.get("author"), dict) else {}
    committer_data = commit_data.get("committer", {}) if isinstance(commit_data.get("committer"), dict) else {}
    author = commit.get("author", {}) if isinstance(commit.get("author"), dict) else {}
    committer = commit.get("committer", {}) if isinstance(commit.get("committer"), dict) else {}
    message = str(commit_data.get("message", ""))
    return {
        "sha": commit.get("sha", ""),
        "url": commit.get("html_url", ""),
        "message": message.splitlines()[0] if message else "",
        "author": author.get("login") or author_data.get("name", ""),
        "committer": committer.get("login") or committer_data.get("name", ""),
        "authored_at": author_data.get("date", ""),
        "committed_at": committer_data.get("date", ""),
    }


def extract_rtl_commit(markdown: str) -> str:
    match = RTL_COMMIT_RE.search(markdown or "")
    return match.group(1).lower() if match else ""


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


def write_commit_log(output: Path, issue: dict, pull_request: dict, diff_text: str, rtl_commit: str = "") -> Path:
    issue_number = int(issue["number"])
    issue_dir = output / f"issue-{issue_number}"
    issue_dir.mkdir(parents=True, exist_ok=True)
    summary = diff_summary(diff_text)
    pr_number = pull_request.get("number", issue_number)
    pr_url = pull_request.get("html_url") or issue.get("html_url", "")
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
    lines.extend([
        "## Diff",
        "```diff",
        diff_text.rstrip(),
        "```",
        "",
    ])
    commit_log_path = issue_dir / COMMIT_LOG_FILENAME
    commit_log_path.write_text("\n".join(lines), encoding="utf-8")
    return commit_log_path


def update_issue_commit_log_metadata(output: Path, issue_number: int, commit_log_path: Path) -> None:
    metadata_path = output / f"issue-{issue_number}" / "metadata.json"
    if not metadata_path.exists():
        return
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return
    if not isinstance(metadata, dict):
        return
    try:
        rel_commit_log = str(commit_log_path.relative_to(output))
    except ValueError:
        return
    metadata["has_commit_log"] = True
    metadata["commit_log_files"] = [rel_commit_log]
    metadata_path.write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def update_issue_rtl_commit_metadata(output: Path, issue_number: int, rtl_commit: str) -> None:
    metadata_path = output / f"issue-{issue_number}" / "metadata.json"
    if not metadata_path.exists():
        return
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return
    if not isinstance(metadata, dict):
        return
    metadata["rtl_commit"] = rtl_commit
    metadata_path.write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def update_issue_summary_metadata(output: Path, issue_number: int, **fields: object) -> None:
    metadata_path = output / f"issue-{issue_number}" / "metadata.json"
    if not metadata_path.exists():
        return
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return
    if not isinstance(metadata, dict):
        return
    changed = False
    for key, value in fields.items():
        if value is not None and metadata.get(key) != value:
            metadata[key] = value
            changed = True
    if changed:
        metadata_path.write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def fetch_related_pull_request_for_issue(repo: str, issue: dict, token: str | None, limiter: RequestLimiter, comments_text: str | None = None) -> dict | None:
    if is_pull_request_item(issue):
        return None
    candidate_numbers: list[int] = []
    seen_numbers = set()
    for text in (str(issue.get("title", "")), str(issue.get("body", "")), comments_text or ""):
        for number in extract_pull_request_numbers(text):
            if number in seen_numbers:
                continue
            seen_numbers.add(number)
            candidate_numbers.append(number)
    if not candidate_numbers and issue.get("comments") and issue.get("comments_url"):
        comments = github_get(issue["comments_url"], token, limiter)
        if isinstance(comments, list):
            for comment in comments:
                body = str(comment.get("body") or "")
                for number in extract_pull_request_numbers(body):
                    if number in seen_numbers:
                        continue
                    seen_numbers.add(number)
                    candidate_numbers.append(number)
    for number in candidate_numbers:
        try:
            candidate = github_get_issue(repo, number, token, limiter)
        except RuntimeError:
            continue
        if is_pull_request_item(candidate):
            return candidate
    return None


def generate_commit_log_for_closed_item(output: Path, repo: str, issue: dict, token: str | None, limiter: RequestLimiter, processed_commit_logs: set[int], comments_text: str | None = None) -> Path | None:
    number = int(issue["number"])
    item_kind = "pr" if is_pull_request_item(issue) else "issue"
    if number in processed_commit_logs or str(issue.get("state", "")).lower() != "closed":
        return None
    existing_commit_log_path = completed_commit_log_path(output, number)
    if existing_commit_log_path is not None:
        processed_commit_logs.add(number)
        return existing_commit_log_path
    try:
        rtl_commit = extract_rtl_commit(str(issue.get("body", "")))
        pr_issue = issue if is_pull_request_item(issue) else fetch_related_pull_request_for_issue(repo, issue, token, limiter, comments_text)
        if pr_issue is None:
            print(f"warning: cannot find related PR for closed issue #{number}", file=sys.stderr)
            processed_commit_logs.add(number)
            return None
        pull_request = fetch_pull_request(pr_issue, token, limiter)
        diff_text = fetch_pull_request_diff(pull_request, token, limiter)
        commit_log_path = write_commit_log(output, issue, pull_request, diff_text, rtl_commit)
        processed_commit_logs.add(number)
        return commit_log_path
    except RuntimeError as error:
        message = str(error)
        print(f"warning: cannot write commit log for #{number}: {message}", file=sys.stderr)
        if is_retryable_network_error(message):
            record_first_fetch_error(output, item_kind, number, message)
        processed_commit_logs.add(number)
        return None


def extract_code_blocks(markdown: str) -> list[CodeBlock]:
    blocks: list[CodeBlock] = []
    for index, match in enumerate(FENCE_RE.finditer(markdown or ""), start=1):
        language = match.group(1).strip().split()[0].lower() if match.group(1).strip() else "text"
        source = match.group(2).replace("\r\n", "\n").rstrip() + "\n"
        if source.strip():
            blocks.append(CodeBlock(language, source, index))
    return blocks


def safe_name(value: str) -> str:
    value = re.sub(r"[^A-Za-z0-9._-]+", "-", value).strip("-.")
    return value or "issue"


def archive_urls(markdown: str) -> list[str]:
    """Return likely archive attachments from issue Markdown."""
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
    line = f"{timestamp}\tissue #{issue_number}\t{url}\t{message}\n"
    try:
        log_path.parent.mkdir(parents=True, exist_ok=True)
        with log_path.open("a", encoding="utf-8") as log_file:
            log_file.write(line)
    except OSError as error:
        print(f"warning: cannot write HTTP 404 log {log_path}: {error}", file=sys.stderr)


def is_retryable_network_error(message: str) -> bool:
    lowered = message.lower()
    return any(pattern in lowered for pattern in RETRYABLE_NETWORK_ERROR_PATTERNS)


def append_fetch_error_log(log_path: Path, item_kind: str, item_number: int, message: str) -> None:
    timestamp = datetime.now(timezone.utc).isoformat()
    line = f"{timestamp}\t{item_kind} #{item_number}\t{message}\n"
    try:
        log_path.parent.mkdir(parents=True, exist_ok=True)
        with log_path.open("a", encoding="utf-8") as log_file:
            log_file.write(line)
    except OSError as error:
        print(f"warning: cannot write fetch error log {log_path}: {error}", file=sys.stderr)


def record_first_fetch_error(output: Path, item_kind: str, item_number: int, message: str) -> None:
    first_error_path = output / FIRST_FETCH_ERROR_FILENAME
    append_fetch_error_log(output / FETCH_ERROR_LOG_FILENAME, item_kind, item_number, message)
    if first_error_path.exists():
        return
    payload = {
        "recorded_at": datetime.now(timezone.utc).isoformat(),
        "kind": item_kind,
        "number": item_number,
        "message": message,
    }
    try:
        first_error_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    except OSError as error:
        print(f"warning: cannot write first fetch error {first_error_path}: {error}", file=sys.stderr)


def completed_commit_log_path(output: Path, issue_number: int) -> Path | None:
    issue_dir = output / f"issue-{issue_number}"
    commit_log_path = issue_dir / COMMIT_LOG_FILENAME
    diff_path = issue_dir / "commit-log.diff"
    if commit_log_path.exists() and diff_path.exists():
        try:
            if commit_log_path.stat().st_size > 0 and diff_path.stat().st_size > 0:
                return commit_log_path
        except OSError:
            return None
    return None


def download_archive(
    url: str,
    destination: Path,
    token: str | None,
    limiter: RequestLimiter,
    issue_number: int,
    http_404_log: Path,
) -> str | None:
    headers = {"User-Agent": "xiangshan-bug-fetcher", "Accept": "application/octet-stream"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = Request(url, headers=headers)
    try:
        limiter.wait_for_slot()
        with urlopen(request, timeout=60) as response:
            content_type = response.headers.get_content_type().lower()
            content_disposition = response.headers.get("Content-Disposition", "")
            filename_match = re.search(r"filename\*?=(?:UTF-8''|\")?([^;\"]+)", content_disposition, re.IGNORECASE)
            filename = unquote(filename_match.group(1).strip().strip('"')) if filename_match else Path(unquote(urlparse(url).path)).name
            filename = safe_name(filename)
            if not filename or filename == "issue":
                filename = "reproducer.zip"
            if not filename.lower().endswith(ARCHIVE_SUFFIXES) and content_type in ARCHIVE_CONTENT_TYPES:
                extension = ".tar.gz" if content_type in ("application/gzip", "application/x-gzip") else ".zip"
                filename += extension
            output = destination / filename
            if output.exists():
                return output.name
            output.write_bytes(response.read())
            return output.name
    except HTTPError as error:
        if error.code == 404:
            append_http_404_log(http_404_log, issue_number, url, str(error))
        print(f"warning: cannot download attachment {url}: {error}", file=sys.stderr)
        return None
    except NETWORK_ERRORS as error:
        print(f"warning: cannot download attachment {url}: {error}", file=sys.stderr)
        return None


def source_filename(block: CodeBlock) -> str:
    extension = LANGUAGE_EXTENSIONS.get(block.language, ".txt")
    return f"program-{block.index:02d}{extension}"


def fetch_comments(issue: dict, token: str | None, limiter: RequestLimiter) -> str:
    if not issue.get("comments"):
        return ""
    comments = github_get(issue["comments_url"], token, limiter)
    if not isinstance(comments, list):
        return ""
    return "\n\n".join(
        f"<!-- comment by {comment.get('user', {}).get('login', 'unknown')} -->\n{comment.get('body') or ''}"
        for comment in comments
    )


def write_issue(output: Path, issue: dict, body: str, comments: str, token: str | None, limiter: RequestLimiter) -> dict:
    issue_id = f"issue-{issue['number']}"
    issue_dir = output / issue_id
    source_dir = issue_dir / "source"
    source_dir.mkdir(parents=True, exist_ok=True)
    description = body.strip() or "(issue body is empty)"
    (issue_dir / "description.md").write_text(description + "\n", encoding="utf-8")
    rtl_commit = extract_rtl_commit(description)
    branch = extract_branch(description)

    all_markdown = body + ("\n\n" + comments if comments else "")
    archive_files = []
    for url in archive_urls(all_markdown):
        filename = download_archive(url, source_dir, token, limiter, int(issue["number"]), output / HTTP_404_LOG_FILENAME)
        if filename:
            archive_files.append(str(Path(issue_id) / "source" / filename))

    blocks = extract_code_blocks(all_markdown)
    source_files = []
    for block in blocks:
        filename = source_filename(block)
        (source_dir / filename).write_text(block.source, encoding="utf-8")
        source_files.append(str(Path(issue_id) / "source" / filename))

    metadata = {
        "number": issue["number"],
        "title": issue.get("title", ""),
        "url": issue.get("html_url", ""),
        "state": issue.get("state", ""),
        "labels": [label.get("name", "") for label in issue.get("labels", [])],
        "author": issue.get("user", {}).get("login", ""),
        "created_at": issue.get("created_at", ""),
        "updated_at": issue.get("updated_at", ""),
        "submitted_at": issue.get("created_at", "")[:10],
        "source_files": archive_files + source_files,
        "archive_files": archive_files,
        "branch": branch,
        "rtl_commit": rtl_commit,
        "has_comments": bool(comments),
        "is_pull_request": is_pull_request_item(issue),
        "has_commit_log": False,
        "commit_log_files": [],
    }
    (issue_dir / "metadata.json").write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return metadata


def load_completed_issue_record(output: Path, issue_number: int) -> dict | None:
    issue_id = f"issue-{issue_number}"
    issue_dir = output / issue_id
    metadata_path = issue_dir / "metadata.json"
    description_path = issue_dir / "description.md"
    if not metadata_path.exists() or not description_path.exists():
        return None
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as error:
        print(f"warning: cannot read existing metadata {metadata_path}: {error}; refetching issue #{issue_number}", file=sys.stderr)
        return None
    if not isinstance(metadata, dict):
        return None
    try:
        metadata_number = int(metadata.get("number", -1))
    except (TypeError, ValueError):
        return None
    if metadata_number != issue_number:
        return None
    source_files = metadata.get("source_files", [])
    if not isinstance(source_files, list):
        return None
    for source_file in source_files:
        if not isinstance(source_file, str):
            return None
        source_path = Path(source_file)
        if not source_path.is_absolute():
            source_path = output / source_path
        if not source_path.exists():
            return None
    return metadata


def count_by_state(records: list[dict]) -> dict[str, int]:
    counts = {"open": 0, "closed": 0}
    for record in records:
        state = str(record.get("state", "")).lower()
        if state in counts:
            counts[state] += 1
    return counts


def pull_request_record(issue: dict, pull_request: dict, commits: list[dict]) -> dict:
    commit_records = [commit_record(commit) for commit in commits]
    return {
        "number": issue["number"],
        "title": issue.get("title", ""),
        "url": issue.get("html_url", ""),
        "state": issue.get("state", ""),
        "labels": [label.get("name", "") for label in issue.get("labels", [])],
        "author": issue.get("user", {}).get("login", ""),
        "created_at": issue.get("created_at", ""),
        "updated_at": issue.get("updated_at", ""),
        "submitted_at": issue.get("created_at", "")[:10],
        "merged_at": pull_request.get("merged_at", ""),
        "base": pull_request.get("base", {}).get("ref", ""),
        "head": pull_request.get("head", {}).get("ref", ""),
        "commits": commit_records,
        "commit_count": len(commit_records),
        "branch": extract_branch(str(issue.get("body", ""))),
        "rtl_commit": extract_rtl_commit(str(issue.get("body", ""))),
    }


def fix_bug_commit_records(pull_requests: list[dict]) -> list[dict]:
    records = []
    seen = set()
    for pull_request in pull_requests:
        for commit in pull_request.get("commits", []):
            sha = commit.get("sha", "")
            if not sha or sha in seen:
                continue
            seen.add(sha)
            record = dict(commit)
            record["pull_request_number"] = pull_request.get("number")
            record["pull_request_url"] = pull_request.get("url", "")
            records.append(record)
    return records


def markdown_escape(value: object) -> str:
    return str(value if value is not None else "").replace("|", "\\|").replace("\n", " ") or "-"


def record_categories(record: dict) -> list[str]:
    categories = record.get("bug_categories", [])
    if not isinstance(categories, list):
        return ["uncategorized"]
    clean = [str(category) for category in categories if str(category) in BUG_CATEGORY_ORDER]
    return clean or ["uncategorized"]


def ordered_categories(categories: Iterable[str]) -> list[str]:
    category_set = set(categories)
    ordered = [category for category in BUG_CATEGORY_ORDER if category in category_set]
    ordered.extend(sorted(category_set - set(BUG_CATEGORY_ORDER)))
    return ordered or ["uncategorized"]


def apply_biweekly_bug_categories(record: dict, biweekly_bug_references: dict[int, set[str]]) -> dict:
    number = int(record.get("number", 0))
    updated = dict(record)
    if number in biweekly_bug_references:
        updated["bug_categories"] = ordered_categories(biweekly_bug_references[number])
        updated["bug_category_source"] = "biweekly-en"
    else:
        updated["bug_categories"] = record_categories(updated)
        updated.setdefault("bug_category_source", "unclassified")
    return updated


def count_records_by_author(records: list[dict]) -> list[dict]:
    authors: dict[str, dict] = {}
    for record in records:
        author = str(record.get("author") or "unknown")
        entry = authors.setdefault(
            author,
            {
                "author": author,
                "total": 0,
                "open": 0,
                "closed": 0,
                "categories": {category: 0 for category in BUG_CATEGORY_ORDER},
                "items": [],
            },
        )
        entry["total"] += 1
        state = str(record.get("state", "")).lower()
        if state in {"open", "closed"}:
            entry[state] += 1
        for category in record_categories(record):
            entry["categories"].setdefault(category, 0)
            entry["categories"][category] += 1
        entry["items"].append(int(record.get("number", 0)))
    return sorted(authors.values(), key=lambda item: (-item["total"], item["author"]))


def write_author_summary(output: Path, filename: str, title: str, records: list[dict], item_label: str) -> list[dict]:
    generated = datetime.now(timezone.utc).isoformat()
    summary = count_records_by_author(records)
    lines = [
        f"# {title}",
        "",
        f"- Generated at: `{generated}`",
        f"- {item_label}s: **{len(records)}**",
        f"- Authors: **{len(summary)}**",
        "",
        f"| Author | Total | Open | Closed | Frontend | Backend | Mem/Cache | Uncategorized | {item_label}s |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---|",
    ]
    for entry in summary:
        items = ", ".join(f"#{number}" for number in sorted(entry["items"], reverse=True) if number)
        categories = entry["categories"]
        lines.append(
            f"| {markdown_escape(entry['author'])} | {entry['total']} | {entry['open']} | {entry['closed']} | "
            f"{categories.get('frontend', 0)} | {categories.get('backend', 0)} | {categories.get('mem/cache', 0)} | "
            f"{categories.get('uncategorized', 0)} | {items or '-'} |"
        )
    summary_path = output / filename
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return summary


def bug_category_summary(issues: list[dict], pull_requests: list[dict]) -> dict[str, dict]:
    summary = {
        category: {
            "category": category,
            "issue_count": 0,
            "open_issues": 0,
            "closed_issues": 0,
            "pull_request_count": 0,
            "open_pull_requests": 0,
            "closed_pull_requests": 0,
        }
        for category in BUG_CATEGORY_ORDER
    }
    for record in issues:
        for category in record_categories(record):
            entry = summary.setdefault(category, {"category": category})
            entry["issue_count"] = entry.get("issue_count", 0) + 1
            state = str(record.get("state", "")).lower()
            if state == "open":
                entry["open_issues"] = entry.get("open_issues", 0) + 1
            elif state == "closed":
                entry["closed_issues"] = entry.get("closed_issues", 0) + 1
    for record in pull_requests:
        for category in record_categories(record):
            entry = summary.setdefault(category, {"category": category})
            entry["pull_request_count"] = entry.get("pull_request_count", 0) + 1
            state = str(record.get("state", "")).lower()
            if state == "open":
                entry["open_pull_requests"] = entry.get("open_pull_requests", 0) + 1
            elif state == "closed":
                entry["closed_pull_requests"] = entry.get("closed_pull_requests", 0) + 1
    return summary


def issue_table_row(record: dict) -> str:
    title = markdown_escape(record.get("title", ""))
    author = markdown_escape(record.get("author") or "-")
    submitted_at = markdown_escape(record.get("submitted_at") or str(record.get("created_at", ""))[:10] or "-")
    branch = markdown_escape(record.get("branch") or "-")
    rtl_commit = markdown_escape(record.get("rtl_commit") or "-")
    source_files = record.get("source_files", [])
    source_count = len(source_files) if isinstance(source_files, list) else 0
    return f"| [#{record['number']}]({record.get('url', '')}) | {title} | {author} | {submitted_at} | {branch} | `{rtl_commit}` | {source_count} |"


def pull_request_table_row(record: dict) -> str:
    title = markdown_escape(record.get("title", ""))
    author = markdown_escape(record.get("author") or "-")
    merged_at = markdown_escape(record.get("merged_at") or "-")
    return f"| [#{record['number']}]({record.get('url', '')}) | {title} | {author} | {record.get('state', '-')} | {record.get('commit_count', 0)} | {merged_at} |"


def write_bugs_summary(output: Path, filename: str, issues: list[dict], pull_requests: list[dict], repo: str, state: str) -> dict[str, dict]:
    generated = datetime.now(timezone.utc).isoformat()
    category_summary = bug_category_summary(issues, pull_requests)
    issue_counts = count_by_state(issues)
    pull_request_counts = count_by_state(pull_requests)
    lines = [
        "# Bugs Summary",
        "",
        f"- Repository: `{repo}`",
        f"- Issue state filter: `{state}`",
        f"- Generated at: `{generated}`",
        f"- Bug issues: **{len(issues)}** (open: **{issue_counts['open']}**, closed: **{issue_counts['closed']}**)",
        f"- Bug PRs: **{len(pull_requests)}** (open: **{pull_request_counts['open']}**, closed: **{pull_request_counts['closed']}**)",
        "",
        "## Category Counts",
        "",
        "| Category | Issues | Open issues | Closed issues | PRs | Open PRs | Closed PRs |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for category in BUG_CATEGORY_ORDER:
        entry = category_summary[category]
        lines.append(
            f"| {BUG_CATEGORY_LABELS.get(category, category)} | {entry.get('issue_count', 0)} | "
            f"{entry.get('open_issues', 0)} | {entry.get('closed_issues', 0)} | "
            f"{entry.get('pull_request_count', 0)} | {entry.get('open_pull_requests', 0)} | {entry.get('closed_pull_requests', 0)} |"
        )
    for category in BUG_CATEGORY_ORDER:
        label = BUG_CATEGORY_LABELS.get(category, category)
        category_issues = [record for record in issues if category in record_categories(record)]
        category_pull_requests = [record for record in pull_requests if category in record_categories(record)]
        lines.extend([
            "",
            f"## {label} Bugs",
            "",
            "### Issues",
            "",
            "| Issue | Title | Author | Submitted at | Branch | RTL commit | Reproducer files |",
            "|---:|---|---|---|---|---|---:|",
        ])
        if category_issues:
            lines.extend(issue_table_row(record) for record in category_issues)
        else:
            lines.append("| - | - | - | - | - | - | 0 |")
        lines.extend([
            "",
            "### Pull Requests",
            "",
            "| PR | Title | Author | State | Commits | Merged at |",
            "|---:|---|---|---|---:|---|",
        ])
        if category_pull_requests:
            lines.extend(pull_request_table_row(record) for record in category_pull_requests)
        else:
            lines.append("| - | - | - | - | 0 | - |")
    summary_path = output / filename
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    summary_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return category_summary


def write_summary(
    output: Path,
    issues: list[dict],
    pull_requests: list[dict],
    repo: str,
    state: str,
    *,
    write_bug_summary_markdown: bool = True,
    write_author_summary_markdown: bool = True,
    bugs_summary_file: str = "bugs-summary.md",
    issue_author_summary_file: str = "issue-author-summary.md",
    pr_author_summary_file: str = "pr-author-summary.md",
) -> None:
    generated = datetime.now(timezone.utc).isoformat()
    issue_counts = count_by_state(issues)
    pull_request_counts = count_by_state(pull_requests)
    fix_bug_commits = fix_bug_commit_records(pull_requests)
    issue_author_summary = count_records_by_author(issues)
    pr_author_summary = count_records_by_author(pull_requests)
    category_summary = bug_category_summary(issues, pull_requests)
    summary_files = []
    if write_author_summary_markdown:
        issue_author_summary = write_author_summary(output, issue_author_summary_file, "Issue Author Summary", issues, "Issue")
        pr_author_summary = write_author_summary(output, pr_author_summary_file, "PR Author Summary", pull_requests, "PR")
        summary_files.extend([
            ("Issue Author Summary", issue_author_summary_file),
            ("PR Author Summary", pr_author_summary_file),
        ])
    if write_bug_summary_markdown:
        category_summary = write_bugs_summary(output, bugs_summary_file, issues, pull_requests, repo, state)
        summary_files.append(("Bugs Summary", bugs_summary_file))
    lines = [
        "# XiangShan Bug Summary",
        "",
        f"- Repository: `{repo}`",
        f"- Issue state filter: `{state}`",
        f"- Generated at: `{generated}`",
        f"- Bug issues: **{len(issues)}** (open: **{issue_counts['open']}**, closed: **{issue_counts['closed']}**)",
        f"- Bug PRs: **{len(pull_requests)}** (open: **{pull_request_counts['open']}**, closed: **{pull_request_counts['closed']}**)",
        f"- Fix-bug commits from bug PRs: **{len(fix_bug_commits)}**",
        "",
        "## Summary Files",
        "",
    ]
    if summary_files:
        lines.extend(f"- [{title}]({filename})" for title, filename in summary_files)
    else:
        lines.append("- Markdown summary generation disabled by command-line options")
    (output / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (output / "summary.json").write_text(
        json.dumps(
            {
                "repository": repo,
                "generated_at": generated,
                "issue_counts": issue_counts,
                "pull_request_counts": pull_request_counts,
                "fix_bug_commit_count": len(fix_bug_commits),
                "bug_category_order": list(BUG_CATEGORY_ORDER),
                "bug_category_summary": category_summary,
                "issue_author_summary": issue_author_summary,
                "pr_author_summary": pr_author_summary,
                "issues": issues,
                "pull_requests": pull_requests,
                "fix_bug_commits": fix_bug_commits,
            },
            indent=2,
            ensure_ascii=False,
        ) + "\n",
        encoding="utf-8",
    )


def state_path(output: Path) -> Path:
    return output / STATE_FILENAME


def load_fetch_state(output: Path, repo: str) -> dict:
    path = state_path(output)
    if not path.exists():
        records = load_existing_records(output, repo)
        numbers = sorted(int(record["number"]) for record in records if "number" in record)
        if not numbers:
            return {}
        return {
            "repository": repo,
            "last_issue_number": max(numbers),
            "downloaded_issue_numbers": numbers,
        }
    try:
        state = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as error:
        print(f"warning: cannot read fetch state {path}: {error}; starting without state", file=sys.stderr)
        return {}
    if state.get("repository") != repo:
        print(f"warning: ignoring fetch state for repository {state.get('repository')!r}", file=sys.stderr)
        return {}
    return state


def save_fetch_state(
    output: Path,
    repo: str,
    records: list[dict],
    previous_state: dict,
    pull_requests_collected: bool,
    next_issue_number: int | None = None,
) -> None:
    seen_numbers = {int(number) for number in previous_state.get("downloaded_issue_numbers", [])}
    seen_numbers.update(int(record["number"]) for record in records)
    last_issue_number = max(seen_numbers) if seen_numbers else previous_state.get("last_issue_number")
    state = {
        "repository": repo,
        "updated_at": datetime.now(timezone.utc).isoformat(),
        "last_issue_number": last_issue_number,
        "downloaded_issue_numbers": sorted(seen_numbers),
        "pull_requests_collected": pull_requests_collected,
    }
    if next_issue_number is not None:
        state["next_issue_number"] = next_issue_number
    state_path(output).write_text(
        json.dumps(state, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def load_existing_summary(output: Path, repo: str) -> dict:
    path = output / "summary.json"
    if not path.exists():
        return {}
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as error:
        print(f"warning: cannot read existing summary {path}: {error}; rewriting from new records only", file=sys.stderr)
        return {}
    if not isinstance(payload, dict) or payload.get("repository") != repo:
        return {}
    return payload


def load_existing_records(output: Path, repo: str) -> list[dict]:
    records = load_existing_summary(output, repo).get("issues", [])
    return records if isinstance(records, list) else []


def load_existing_pull_requests(output: Path, repo: str) -> list[dict]:
    records = load_existing_summary(output, repo).get("pull_requests", [])
    return records if isinstance(records, list) else []


def merge_records(existing: list[dict], new_records: list[dict]) -> list[dict]:
    merged = {int(record["number"]): record for record in existing if "number" in record}
    for record in new_records:
        merged[int(record["number"])] = record
    return [merged[number] for number in sorted(merged, reverse=True)]


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
            if number in seen:
                continue
            seen.add(number)
            numbers.append(number)
    return numbers


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Checkpoint behavior:\n"
            "  Incremental progress is saved in --output/.fetch-state.json. "
            "During a run, the file is updated after each completed issue/PR with "
            "next_issue_number so an interrupted run can resume from the saved "
            "checkpoint. After a successful full run, next_issue_number is cleared "
            "and last_issue_number remains as the completed incremental boundary. "
            "Use --force-refresh to ignore saved incremental/checkpoint state."
        ),
    )
    parser.add_argument("--repo", default="OpenXiangShan/XiangShan", help="GitHub repository owner/name")
    parser.add_argument("--output", default="xiangshan-bugs-src", type=Path, help="Output directory")
    parser.add_argument("--state", choices=("open", "closed", "all"), default="all")
    parser.add_argument("--label", action="append", default=[], help="Exact label to match; repeatable")
    parser.add_argument("--issue", action="append", default=[], help="Fetch only these issue/PR numbers; accepts N or START-END; repeatable")
    parser.add_argument("--number", action="append", default=[], help="Alias of --issue for explicit issue/PR numbers or ranges")
    parser.add_argument("--include-comments", action="store_true", help="Also extract fenced code from issue comments")
    parser.add_argument(
        "--no-biweekly-en",
        action="store_true",
        help="Do not force-fetch bug issues mentioned by XiangShan biweekly-en blog posts",
    )
    parser.add_argument(
        "--biweekly-en-url",
        default=BIWEEKLY_EN_CATEGORY_URL,
        help="XiangShan biweekly-en category URL used to discover bug issue links",
    )
    parser.add_argument(
        "--force-refresh",
        action="store_true",
        help="Ignore saved incremental/checkpoint state and scan all matching issues again",
    )
    parser.add_argument(
        "--no-bug-summary",
        action="store_true",
        help="Do not write the bug-category Markdown summary file",
    )
    parser.add_argument(
        "--no-author-summary",
        action="store_true",
        help="Do not write the issue/PR author Markdown summary files",
    )
    parser.add_argument(
        "--bugs-summary-file",
        default="bugs-summary.md",
        help="Path under --output for the bug-category Markdown summary",
    )
    parser.add_argument(
        "--issue-author-summary-file",
        default="issue-author-summary.md",
        help="Path under --output for the issue author Markdown summary",
    )
    parser.add_argument(
        "--pr-author-summary-file",
        default="pr-author-summary.md",
        help="Path under --output for the PR author Markdown summary",
    )
    parser.add_argument("--token", default=os.getenv("GITHUB_TOKEN"), help="GitHub token (or GITHUB_TOKEN)")
    parser.add_argument(
        "--max-requests",
        type=int,
        default=None,
        help="Stop after this many GitHub HTTP requests, including retries and downloads",
    )
    parser.add_argument(
        "--min-delay",
        type=float,
        default=1.0,
        help="Minimum random delay in seconds between GitHub HTTP requests",
    )
    parser.add_argument(
        "--max-delay",
        type=float,
        default=5.0,
        help="Maximum random delay in seconds between GitHub HTTP requests",
    )
    parser.add_argument(
        "--no-bosc-ipv6",
        action="store_true",
        help="Do not restart through the bosc-ipv6 proxy wrapper",
    )
    return parser.parse_args()


def restart_with_bosc_ipv6(args: argparse.Namespace) -> None:
    """Restart this process through the locally configured GitHub proxy."""
    if args.no_bosc_ipv6 or os.getenv(BOSC_IPV6_ENV) == "1":
        return
    proxy = shutil.which("bosc-ipv6")
    if not proxy:
        return
    os.environ[BOSC_IPV6_ENV] = "1"
    os.execvpe(proxy, [proxy, sys.executable, os.path.abspath(__file__), *sys.argv[1:]], os.environ)


def main() -> int:
    args = parse_args()
    restart_with_bosc_ipv6(args)
    if args.max_requests is not None and args.max_requests <= 0:
        print("error: --max-requests must be greater than 0", file=sys.stderr)
        return 2
    if args.min_delay < 0 or args.max_delay < 0:
        print("error: --min-delay and --max-delay must be non-negative", file=sys.stderr)
        return 2
    if args.min_delay > args.max_delay:
        print("error: --min-delay cannot be greater than --max-delay", file=sys.stderr)
        return 2
    limiter = RequestLimiter(args.max_requests, args.min_delay, args.max_delay)
    requested_labels = {label.lower() for label in args.label}
    try:
        requested_numbers = parse_issue_number_specs([*args.issue, *args.number])
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    args.output.mkdir(parents=True, exist_ok=True)
    biweekly_bug_references: dict[int, set[str]] = {}
    biweekly_issue_numbers: set[int] = set()
    if not args.no_biweekly_en and not requested_numbers:
        biweekly_bug_references = fetch_biweekly_bug_issue_references(args.biweekly_en_url, limiter)
        biweekly_issue_numbers = set(biweekly_bug_references)
        if biweekly_issue_numbers:
            print(f"Found {len(biweekly_issue_numbers)} biweekly-en bug issue/PR reference(s)")
    fetch_state = load_fetch_state(args.output, args.repo)
    resume_in_progress = bool(fetch_state.get("next_issue_number")) and not args.force_refresh and not requested_numbers
    resume_issue_number = None
    if resume_in_progress:
        resume_issue_number = fetch_state.get("next_issue_number")
    elif not args.force_refresh and not requested_numbers:
        resume_issue_number = fetch_state.get("last_issue_number")
    if resume_issue_number is not None:
        try:
            resume_issue_number = int(resume_issue_number)
        except (TypeError, ValueError):
            print(f"warning: invalid saved resume issue number {resume_issue_number!r}; scanning all issues", file=sys.stderr)
            resume_issue_number = None
            resume_in_progress = False
    refresh_pull_requests = args.force_refresh or not bool(fetch_state.get("pull_requests_collected"))
    try:
        selected_issues = []
        selected_pull_requests = []
        selected_issue_numbers = set()
        selected_pull_request_numbers = set()
        if requested_numbers:
            for number in requested_numbers:
                try:
                    item = github_get_issue(args.repo, number, args.token, limiter)
                except GithubNotFoundError as error:
                    append_fetch_error_log(args.output / FETCH_ERROR_LOG_FILENAME, "issue/PR", number, str(error))
                    print(f"warning: skipping missing issue/PR #{number}: {error}", file=sys.stderr)
                    continue
                selected_issues.append(item)
                selected_issue_numbers.add(number)
                if is_pull_request_item(item):
                    selected_pull_requests.append(item)
                    selected_pull_request_numbers.add(number)
        else:
            for item in fetch_issue_items(args.repo, args.state, args.token, limiter):
                number = int(item.get("number", 0))
                is_pull_request = is_pull_request_item(item)
                is_forced_biweekly = number in biweekly_issue_numbers
                if resume_in_progress and resume_issue_number is not None:
                    if number > resume_issue_number:
                        continue
                    if not is_forced_biweekly and not is_bug_issue(item, requested_labels):
                        continue
                    if is_pull_request:
                        if number not in selected_pull_request_numbers:
                            selected_pull_requests.append(item)
                            selected_pull_request_numbers.add(number)
                    elif number not in selected_issue_numbers:
                        selected_issues.append(item)
                        selected_issue_numbers.add(number)
                    continue
                if (
                    not refresh_pull_requests
                    and not is_pull_request
                    and resume_issue_number is not None
                    and number <= resume_issue_number
                    and not is_forced_biweekly
                ):
                    break
                if not is_forced_biweekly and not is_bug_issue(item, requested_labels):
                    continue
                if is_pull_request:
                    if is_forced_biweekly and number not in selected_issue_numbers:
                        selected_issues.append(item)
                        selected_issue_numbers.add(number)
                    if (is_forced_biweekly or refresh_pull_requests or resume_issue_number is None or number > resume_issue_number) and number not in selected_pull_request_numbers:
                        selected_pull_requests.append(item)
                        selected_pull_request_numbers.add(number)
                elif (is_forced_biweekly or resume_issue_number is None or number > resume_issue_number) and number not in selected_issue_numbers:
                    selected_issues.append(item)
                    selected_issue_numbers.add(number)

            missing_biweekly_numbers = sorted(biweekly_issue_numbers - selected_issue_numbers - selected_pull_request_numbers, reverse=True)
            for number in missing_biweekly_numbers:
                item = github_get_issue(args.repo, number, args.token, limiter)
                if is_pull_request_item(item):
                    if number not in selected_issue_numbers:
                        selected_issues.append(item)
                        selected_issue_numbers.add(number)
                    if number not in selected_pull_request_numbers:
                        selected_pull_requests.append(item)
                        selected_pull_request_numbers.add(number)
                elif number not in selected_issue_numbers:
                    selected_issues.append(item)
                    selected_issue_numbers.add(number)

        issue_records = []
        refreshed_records = []
        generated_commit_logs: set[int] = set()
        pull_requests_collected = bool(fetch_state.get("pull_requests_collected")) or refresh_pull_requests or bool(biweekly_issue_numbers)
        for position, issue in enumerate(selected_issues, start=1):
            issue_number = int(issue["number"])
            existing_record = load_completed_issue_record(args.output, issue_number)
            if existing_record is not None and not args.force_refresh:
                record = apply_biweekly_bug_categories(existing_record, biweekly_bug_references)
                update_issue_summary_metadata(
                    args.output,
                    issue_number,
                    bug_categories=record.get("bug_categories", []),
                    bug_category_source=record.get("bug_category_source", "unclassified"),
                )
                comments = ""
                if completed_commit_log_path(args.output, issue_number) is None and args.include_comments:
                    comments = fetch_comments(issue, args.token, limiter)
                commit_log_path = generate_commit_log_for_closed_item(args.output, args.repo, issue, args.token, limiter, generated_commit_logs, comments if comments else None)
                if commit_log_path is not None:
                    update_issue_commit_log_metadata(args.output, issue_number, commit_log_path)
                    record = load_completed_issue_record(args.output, issue_number) or record
                    record = apply_biweekly_bug_categories(record, biweekly_bug_references)
                refreshed_records.append(record)
                save_fetch_state(
                    args.output,
                    args.repo,
                    [*refreshed_records, *issue_records],
                    fetch_state,
                    pull_requests_collected,
                    next_issue_number=issue_number - 1,
                )
                print(f"[{position}/{len(selected_issues)}] issue #{issue_number}: reused existing local content")
                continue
            comments = fetch_comments(issue, args.token, limiter) if args.include_comments else ""
            record = write_issue(args.output, issue, issue.get("body") or "", comments, args.token, limiter)
            record = apply_biweekly_bug_categories(record, biweekly_bug_references)
            update_issue_summary_metadata(
                args.output,
                issue_number,
                submitted_at=record.get("submitted_at", ""),
                branch=record.get("branch", ""),
                rtl_commit=record.get("rtl_commit", ""),
                bug_categories=record.get("bug_categories", []),
                bug_category_source=record.get("bug_category_source", "unclassified"),
            )
            print(f"[{position}/{len(selected_issues)}] issue #{issue_number}: {len(record['source_files'])} source file(s)")
            commit_log_path = generate_commit_log_for_closed_item(args.output, args.repo, issue, args.token, limiter, generated_commit_logs, comments if comments else None)
            if commit_log_path is not None:
                update_issue_commit_log_metadata(args.output, issue_number, commit_log_path)
                record = load_completed_issue_record(args.output, issue_number) or record
                record = apply_biweekly_bug_categories(record, biweekly_bug_references)
            issue_records.append(record)
            save_fetch_state(
                args.output,
                args.repo,
                [*refreshed_records, *issue_records],
                fetch_state,
                pull_requests_collected,
                next_issue_number=issue_number - 1,
            )

        pull_request_records = []
        for position, issue in enumerate(selected_pull_requests, start=1):
            pull_request = fetch_pull_request(issue, args.token, limiter)
            commits_url = pull_request.get("commits_url", "")
            commits = fetch_pull_request_commits(commits_url, args.token, limiter) if commits_url else []
            record = pull_request_record(issue, pull_request, commits)
            record = apply_biweekly_bug_categories(record, biweekly_bug_references)
            pull_request_records.append(record)
            save_fetch_state(
                args.output,
                args.repo,
                [*refreshed_records, *issue_records, *pull_request_records],
                fetch_state,
                pull_requests_collected,
                next_issue_number=int(issue["number"]),
            )
            print(f"[{position}/{len(selected_pull_requests)}] PR #{issue['number']}: {record['commit_count']} commit(s)")
            commit_log_path = generate_commit_log_for_closed_item(args.output, args.repo, issue, args.token, limiter, generated_commit_logs)
            if commit_log_path is not None:
                update_issue_commit_log_metadata(args.output, int(issue['number']), commit_log_path)

        completed_records = refreshed_records + issue_records
        all_issues = [
            apply_biweekly_bug_categories(record, biweekly_bug_references)
            for record in merge_records(load_existing_records(args.output, args.repo), completed_records)
        ]
        all_pull_requests = [
            apply_biweekly_bug_categories(record, biweekly_bug_references)
            for record in merge_records(load_existing_pull_requests(args.output, args.repo), pull_request_records)
        ]
        write_summary(
            args.output,
            all_issues,
            all_pull_requests,
            args.repo,
            args.state,
            write_bug_summary_markdown=not args.no_bug_summary,
            write_author_summary_markdown=not args.no_author_summary,
            bugs_summary_file=args.bugs_summary_file,
            issue_author_summary_file=args.issue_author_summary_file,
            pr_author_summary_file=args.pr_author_summary_file,
        )
        save_fetch_state(
            args.output,
            args.repo,
            [*refreshed_records, *issue_records, *pull_request_records],
            fetch_state,
            pull_requests_collected,
        )
        skipped_note = f"; resumed from #{resume_issue_number}" if resume_in_progress and resume_issue_number is not None else ""
        existing_note = f", refreshed {len(refreshed_records)} existing issue(s)" if refreshed_records else ""
        print(
            f"Saved {len(issue_records)} new/updated bug issue(s), {len(pull_request_records)} bug PR(s), and "
            f"{len(fix_bug_commit_records(all_pull_requests))} unique fix-bug commit(s) to {args.output}{existing_note}{skipped_note}; "
            f"biweekly-en references: {len(biweekly_issue_numbers)}; "
            f"HTTP 404 log: {args.output / HTTP_404_LOG_FILENAME}; GitHub requests: {limiter.request_count}"
        )
        return 0
    except RuntimeError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
