#!/usr/bin/env python3
"""Command-line entry point for XiangShan bug replay."""

from __future__ import annotations

import argparse
import html
import os
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from html.parser import HTMLParser
from pathlib import Path
from typing import Sequence
import zipfile


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
_ATTACHMENT_URL_PATTERN = re.compile(
    r"https?://github\.com/[^\s\"'<>\\)]+",
    flags=re.IGNORECASE,
)


class _IssuePageTextExtractor(HTMLParser):
    """Collect visible text and JSON-LD payloads from a GitHub issue page."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self._ignored_tags: list[str] = []
        self._in_json_ld = False
        self.visible_text: list[str] = []
        self.json_ld: list[str] = []
        self.published_times: list[str] = []
        self.relative_times: list[str] = []
        self.links: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        if tag == "a" and attributes.get("href"):
            self.links.append(attributes["href"] or "")
        if tag == "meta":
            descriptor = (attributes.get("property") or attributes.get("name") or "").lower()
            if descriptor in {"article:published_time", "og:article:published_time"}:
                content = attributes.get("content")
                if content:
                    self.published_times.append(content)
        elif tag == "relative-time":
            datetime_value = attributes.get("datetime")
            if datetime_value:
                self.relative_times.append(datetime_value)
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


def _format_issue_created_at(timestamp: str) -> str | None:
    """Format an ISO timestamp as YYYY-MM-DD HH-MM-SS."""
    normalized = html.unescape(timestamp).strip()
    if normalized.endswith(("Z", "z")):
        normalized = normalized[:-1] + "+00:00"
    try:
        parsed = datetime.fromisoformat(normalized)
    except (TypeError, ValueError):
        return None
    return parsed.strftime("%Y-%m-%d %H-%M-%S")


def extract_issue_created_at(page_html: str) -> str | None:
    """Extract the issue creation time from GitHub page metadata."""
    if not isinstance(page_html, str):
        raise TypeError("GitHub issue 页面内容必须是字符串")

    parser = _IssuePageTextExtractor()
    parser.feed(page_html)
    parser.close()

    json_ld = "\n".join(parser.json_ld)
    for field in ("datePublished", "dateCreated"):
        pattern = rf'"{field}"\s*:\s*"([^"]+)"'
        for match in re.finditer(pattern, json_ld, flags=re.IGNORECASE):
            formatted = _format_issue_created_at(match.group(1))
            if formatted:
                return formatted
    for timestamp in parser.published_times + parser.relative_times:
        formatted = _format_issue_created_at(timestamp)
        if formatted:
            return formatted
    return None


def extract_issue_attachment_urls(page_html: str) -> list[str]:
    """Extract GitHub attachment URLs referenced by an issue page."""
    if not isinstance(page_html, str):
        raise TypeError("GitHub issue 页面内容必须是字符串")

    parser = _IssuePageTextExtractor()
    parser.feed(page_html)
    parser.close()

    candidates = parser.links + _ATTACHMENT_URL_PATTERN.findall(html.unescape(page_html).replace("\\/", "/"))
    attachment_urls: list[str] = []
    seen: set[str] = set()
    for candidate in candidates:
        candidate = html.unescape(candidate).replace("\\/", "/")
        candidate = re.split(r"\\[nrt]", candidate, maxsplit=1)[0]
        candidate = candidate.rstrip(".,;:)]}\\")
        if candidate.startswith("/"):
            candidate = urllib.parse.urljoin("https://github.com", candidate)
        parsed = urllib.parse.urlsplit(candidate)
        host = parsed.netloc.lower().split(":", 1)[0]
        path = parsed.path.lower()
        is_attachment = host == "github.com" and (
            path.startswith("/user-attachments/files/")
            or path.startswith("/user-attachments/assets/")
            or path.startswith("/openxiangshan/xiangshan/files/")
        )
        if is_attachment and candidate not in seen:
            seen.add(candidate)
            attachment_urls.append(candidate)
    return attachment_urls


def parse_github_issue_resources(issue_number: int) -> tuple[str | None, str | None, list[str]]:
    """Fetch one issue page and return commit, creation time, and attachments."""
    page_html = fetch_github_issue_page(issue_number)
    return (
        extract_xiangshan_commit_hash(page_html),
        extract_issue_created_at(page_html),
        extract_issue_attachment_urls(page_html),
    )


def _attachment_filename(url: str) -> str:
    path = urllib.parse.unquote(urllib.parse.urlsplit(url).path)
    filename = Path(path).name
    return filename if filename not in {"", ".", ".."} else "attachment"


def _unique_path(directory: Path, filename: str) -> Path:
    candidate = directory / filename
    if not candidate.exists():
        return candidate
    stem = candidate.stem
    suffix = candidate.suffix
    index = 1
    while True:
        candidate = directory / f"{stem}-{index}{suffix}"
        if not candidate.exists():
            return candidate
        index += 1


def download_issue_attachment(url: str, directory: Path) -> Path | None:
    """Download one issue attachment through the configured proxy."""
    directory.mkdir(parents=True, exist_ok=True)
    destination = _unique_path(directory, _attachment_filename(url))
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
    ]
    if USE_SOCKS5_RPOXY:
        command.extend(("--socks5-hostname", PROXY))
    command.extend(("--output", str(destination), url))
    try:
        subprocess.run(command, check=True)
    except (OSError, subprocess.CalledProcessError) as exc:
        destination.unlink(missing_ok=True)
        detail = exc.stderr.strip() if isinstance(exc, subprocess.CalledProcessError) and exc.stderr else str(exc)
        print(f"附件下载失败：{url}: {detail}")
        return None
    return destination


def extract_zip_safely(zip_path: Path, directory: Path) -> bool:
    """Extract a ZIP while rejecting members that escape the target directory."""
    target_root = directory.resolve()
    try:
        with zipfile.ZipFile(zip_path) as archive:
            for member in archive.infolist():
                target = (directory / member.filename).resolve()
                if target != target_root and target_root not in target.parents:
                    raise RuntimeError(f"ZIP 成员路径越界：{member.filename}")
            archive.extractall(directory)
    except (OSError, RuntimeError, zipfile.BadZipFile) as exc:
        print(f"ZIP 解压失败：{zip_path}: {exc}")
        return False
    return True


def download_and_extract_attachments(urls: Sequence[str], directory: Path) -> list[Path]:
    """Download issue attachments and extract downloaded ZIP files."""
    downloaded: list[Path] = []
    for url in urls:
        path = download_issue_attachment(url, directory)
        if path is None:
            continue
        downloaded.append(path)
        if path.suffix.lower() == ".zip" or zipfile.is_zipfile(path):
            extract_zip_safely(path, directory)
    return downloaded


def find_latest_commit_before(
    repository: Path, issue_created_at: str | None
) -> str | None:
    """Return the newest repository commit strictly before an issue timestamp."""
    if issue_created_at is None:
        return None

    try:
        issue_datetime = datetime.strptime(issue_created_at, "%Y-%m-%d %H-%M-%S")
    except (TypeError, ValueError):
        return None

    # GitHub exposes issue timestamps in UTC; keep that timezone explicit when
    # asking Git so the host's local timezone cannot shift the cutoff.
    git_before = issue_datetime.strftime("%Y-%m-%dT%H:%M:%SZ")
    result = subprocess.run(
        [
            "git",
            "log",
            "--all",
            "HEAD",
            "--before",
            git_before,
            "--date-order",
            "--format=%H",
            "--max-count=1",
        ],
        cwd=repository,
        check=True,
        capture_output=True,
        text=True,
    )
    for line in result.stdout.splitlines():
        commit_hash = line.strip()
        if re.fullmatch(r"[0-9a-f]{7,40}", commit_hash, flags=re.IGNORECASE):
            return commit_hash.lower()
    return None


def parse_github_issue_details(issue_number: int) -> tuple[str | None, str | None]:
    """Fetch one issue page and return its XiangShan commit and creation time."""
    page_html = fetch_github_issue_page(issue_number)
    return extract_xiangshan_commit_hash(page_html), extract_issue_created_at(page_html)


def parse_github_issue(issue_number: int) -> str | None:
    """Fetch an XiangShan issue page and return its referenced commit hash, if any."""
    return parse_github_issue_details(issue_number)[0]


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

    commit_hash: str | None = args.commit_hash or None
    if commit_hash is None:
        print(f"未提供 --commit_hash，正在读取 issue #{issue} 的 commit 和提出时间...")
    else:
        print(f"正在读取 issue #{issue} 的提出时间...")

    fetched_commit_hash: str | None = None
    issue_created_at: str | None = None
    attachment_urls: list[str] = []
    issue_lookup_failed = False
    try:
        fetched_commit_hash, issue_created_at, attachment_urls = parse_github_issue_resources(issue)
    except Exception as exc:
        issue_lookup_failed = True
        print(f"读取 issue #{issue} 页面信息失败，自动 commit 和提出时间记为 None：{exc}")

    if commit_hash is None:
        commit_hash = fetched_commit_hash
        if commit_hash is None and not issue_lookup_failed:
            print(f"issue #{issue} 中没有找到 XiangShan commit hash，已记录为 None。")

    print(
        f"获取的参数：issue={issue}, commit_hash={commit_hash}, "
        f"issue_created_at={issue_created_at}"
    )
    replay_directory = Path(f"xs-bug-replay-{issue}")
    replay_directory.mkdir(exist_ok=True)
    print(f"工作目录：{replay_directory}")
    if attachment_urls:
        print(f"发现 {len(attachment_urls)} 个 issue 附件，开始下载到 {replay_directory}...")
        downloaded_attachments = download_and_extract_attachments(attachment_urls, replay_directory)
        print(f"附件处理完成，成功下载 {len(downloaded_attachments)} 个文件")
    else:
        print(f"issue #{issue} 页面没有找到附件")
    os.chdir(replay_directory)
    print("开始克隆 xs-env 仓库……")
    subprocess.run(
        ["git", "clone", "git@github.com:OpenXiangShan/xs-env.git"],
        check=True,
    )
    print("克隆 xs-env 仓库完成")
    xs_env_directory = Path.cwd() / "xs-env"
    os.chdir(xs_env_directory)
    print("已进入 xs-env 目录，开始执行 source setup.sh")
    subprocess.run(["bash", "-lc", "source setup.sh"], check=True)
    print("source setup.sh 执行完成")

    xiangshan_directory = xs_env_directory / "XiangShan"
    if not xiangshan_directory.is_dir():
        print(f"未找到 XiangShan 目录：{xiangshan_directory}，跳过 checkout")
        return 0

    os.chdir(xiangshan_directory)
    print(f"已进入 XiangShan 目录：{xiangshan_directory}")

    checkout_hash = commit_hash
    if checkout_hash is None:
        try:
            checkout_hash = find_latest_commit_before(xiangshan_directory, issue_created_at)
        except (OSError, subprocess.CalledProcessError) as exc:
            detail = (
                exc.stderr.strip()
                if isinstance(exc, subprocess.CalledProcessError) and exc.stderr
                else str(exc)
            )
            print(f"查找 issue 创建时间之前的 XiangShan commit 失败，跳过 checkout：{detail}")
        if checkout_hash is None:
            if issue_created_at is None:
                print("issue_created_at 为 None，无法选择历史 commit，跳过 checkout")
            else:
                print(f"没有找到早于 issue_created_at={issue_created_at} 的 XiangShan commit，跳过 checkout")
        else:
            commit_hash = checkout_hash
            print(f"根据 issue_created_at={issue_created_at} 选择 XiangShan commit：{checkout_hash}")

    if checkout_hash is not None:
        subprocess.run(["git", "checkout", checkout_hash], check=True)
        print(f"XiangShan checkout 完成：{checkout_hash}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
