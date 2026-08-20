#!/usr/bin/env python3
"""Command-line entry point for XiangShan bug replay."""

from __future__ import annotations

import argparse
import html
import subprocess
import urllib.error
import urllib.request
from html.parser import HTMLParser
from typing import Sequence


USE_SOCKS5_RPOXY = True
PROXY = "172.38.10.247:8970"

GITHUB_ISSUE_URL = "https://github.com/OpenXiangShan/XiangShan/issues/{issue_number}"
_SHA_PATTERN = r"([0-9a-f]{7,40})"
_XIANGSHAN_COMMIT_PATTERNS = (
    rf"(?:xiang\s*shan|香山)(?:\s+(?:rtl|repo(?:sitory)?))?\s+commit"
    rf"(?:\s+(?:id|hash|sha))?\s*(?:is\s*)?[:=\-]?\s*[`\"']*{_SHA_PATTERN}\b",
)
_XIANGSHAN_REFERENCE_PATTERN = (
    rf"(?:https?://github\.com)?/?OpenXiangShan/XiangShan/"
    rf"(?:blob|commit|tree)/{_SHA_PATTERN}(?:[/?#\"'<]|$)"
)


class _IssuePageTextExtractor(HTMLParser):
    """Collect visible text and JSON-LD payloads from a GitHub issue page."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self._ignored_tags: list[str] = []
        self._in_json_ld = False
        self.visible_text: list[str] = []
        self.json_ld: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if tag == "script" and attributes.get("type", "").lower() == "application/ld+json":
            self._in_json_ld = True
            return
        if tag in {"script", "style", "svg"}:
            self._ignored_tags.append(tag)

    def handle_endtag(self, tag: str) -> None:
        if tag == "script" and self._in_json_ld:
            self._in_json_ld = False
            return
        if self._ignored_tags and self._ignored_tags[-1] == tag:
            self._ignored_tags.pop()

    def handle_data(self, data: str) -> None:
        if self._in_json_ld:
            self.json_ld.append(data)
        elif not self._ignored_tags:
            self.visible_text.append(data)


def build_parser() -> argparse.ArgumentParser:
    """Build the command-line argument parser."""
    parser = argparse.ArgumentParser(description="Replay an XiangShan bug.")
    parser.add_argument("--issue", help="GitHub issue number to replay.")
    parser.add_argument("--commit_hash", help="XiangShan commit hash to use.")
    return parser


def _validate_issue_number(issue_number: int) -> int:
    if isinstance(issue_number, bool) or not isinstance(issue_number, int) or issue_number <= 0:
        raise ValueError("issue 编号必须是正整数")
    return issue_number


def get_github_issue_url(issue_number: int) -> str:
    """Return the canonical XiangShan GitHub issue URL."""
    return GITHUB_ISSUE_URL.format(issue_number=_validate_issue_number(issue_number))


def fetch_github_issue_page(issue_number: int) -> str:
    """Fetch one GitHub issue page, using SOCKS5 when configured."""
    url = get_github_issue_url(issue_number)
    if USE_SOCKS5_RPOXY:
        command = [
            "curl",
            "--fail-with-body",
            "--location",
            "--silent",
            "--show-error",
            "--connect-timeout",
            "20",
            "--max-time",
            "90",
            "--socks5-hostname",
            PROXY,
            "--user-agent",
            "xs-bug-replay/1.0",
            url,
        ]
        try:
            result = subprocess.run(command, check=True, capture_output=True, text=True)
        except (OSError, subprocess.CalledProcessError) as exc:
            detail = exc.stderr.strip() if isinstance(exc, subprocess.CalledProcessError) else str(exc)
            raise RuntimeError(f"无法通过 SOCKS5 代理获取 GitHub issue: {url}: {detail}") from exc
        return result.stdout

    request = urllib.request.Request(url, headers={"User-Agent": "xs-bug-replay/1.0"})
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            return response.read().decode("utf-8", errors="replace")
    except (urllib.error.URLError, TimeoutError) as exc:
        raise RuntimeError(f"无法获取 GitHub issue: {url}: {exc}") from exc


def _issue_page_texts(page_html: str) -> tuple[str, ...]:
    """Extract text sources in reliability order from a GitHub issue page."""
    parser = _IssuePageTextExtractor()
    parser.feed(page_html)
    parser.close()

    json_ld_text = "\n".join(parser.json_ld)
    visible_text = "\n".join(parser.visible_text)
    return (json_ld_text, visible_text, html.unescape(page_html))


def _find_commit(patterns: Sequence[str], texts: Sequence[str]) -> str | None:
    import re

    for text in texts:
        for pattern in patterns:
            match = re.search(pattern, text, flags=re.IGNORECASE)
            if match:
                return match.group(1).lower()
    return None


def extract_xiangshan_commit_hash(page_html: str) -> str | None:
    """Extract the issue's XiangShan revision without accepting unrelated SHAs."""
    if not isinstance(page_html, str):
        raise TypeError("GitHub issue 页面内容必须是字符串")

    texts = _issue_page_texts(page_html)
    explicit_commit = _find_commit(_XIANGSHAN_COMMIT_PATTERNS, texts)
    if explicit_commit:
        return explicit_commit
    return _find_commit((_XIANGSHAN_REFERENCE_PATTERN,), texts)


def parse_github_issue(issue_number: int) -> str | None:
    """Fetch an XiangShan issue page and return its referenced commit hash, if any."""
    return extract_xiangshan_commit_hash(fetch_github_issue_page(issue_number))


def prompt_for_issue() -> int:
    """Prompt until the user enters an integer issue number."""
    while True:
        issue = input("请输入想要复现的 issue 编号：").strip()
        try:
            return int(issue)
        except ValueError:
            print("输入不合法，issue 编号必须是整数，请再试一次。")


def main(argv: Sequence[str] | None = None) -> int:
    """Parse replay parameters and report missing required inputs."""
    args = build_parser().parse_args(argv)

    if not args.issue:
        issue = prompt_for_issue()
    else:
        try:
            issue = int(args.issue)
        except ValueError:
            print("输入不合法，--issue 必须是整数。")
            return 1

    commit_hash = args.commit_hash
    if not commit_hash:
        print(f"未提供 --commit_hash，正在读取 issue #{issue} 的 XiangShan commit...")
        try:
            commit_hash = parse_github_issue(issue)
        except (RuntimeError, TypeError, ValueError) as exc:
            print(f"读取 issue #{issue} 的 commit 失败：{exc}")
            return 1
        if not commit_hash:
            print(f"issue #{issue} 中没有找到 XiangShan commit hash。")
            return 1

    print(f"获取的参数：issue={issue}, commit_hash={commit_hash}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
