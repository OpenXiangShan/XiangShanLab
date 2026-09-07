#!/usr/bin/env python3
"""Generate a Beijing-time weekly task report from GitHub Issues."""

import argparse
import json
import os
import re
import sys
from collections import defaultdict
from datetime import date, datetime, time, timedelta
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen
from zoneinfo import ZoneInfo


BEIJING = ZoneInfo("Asia/Shanghai")
BOT_LOGIN = "github-actions[bot]"
ASSIGNMENT_MARKER = re.compile(r"<!--\s*task-assignment:(pending|resolved) claimant=([A-Za-z0-9-]+)\s*-->")
REVIEW_MARKER = re.compile(r"<!--\s*task-review:(pending|resolved) executor=([A-Za-z0-9-]+)\s*-->")
COMMIT_URL = re.compile(r"(?P<url>https?://github\.com/[^\s/]+/[^\s/]+/commit/(?P<sha>[0-9a-fA-F]{7,40}))(?=[/?#\s).,;:]|$)")
LABELED_SHA = re.compile(r"(?:\bcommit\s*(?:sha|hash)?|\bsha|\b提交(?:的)?\s*(?:commit|SHA|哈希)?)\s*[:：#=]?\s*`?([0-9a-fA-F]{7,40})`?(?![0-9a-fA-F])", re.IGNORECASE)
DDL = re.compile(r"^(\d{4})-(\d{2})-(\d{2}) ((?:[01]\d|2[0-3]):[0-5]\d|24:00)$")


class GitHubClient:
    def __init__(self, token):
        self.token = token

    def _request(self, url):
        headers = {"Accept": "application/vnd.github+json", "User-Agent": "weekly-status-report"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        request = Request(url, headers=headers)
        try:
            with urlopen(request, timeout=30) as response:
                return json.loads(response.read().decode("utf-8")), response.headers.get("Link", "")
        except HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise RuntimeError("GitHub API request failed (HTTP %s): %s" % (error.code, detail)) from error
        except URLError as error:
            raise RuntimeError("GitHub API request failed: %s" % error.reason) from error

    def get_paginated(self, path, params=None):
        params = dict(params or {})
        params.update({"per_page": 100, "page": 1})
        url = "https://api.github.com" + path + "?" + urlencode(params)
        values = []
        while url:
            page, link = self._request(url)
            if not isinstance(page, list):
                raise RuntimeError("GitHub API returned a non-list response for " + path)
            values.extend(page)
            url = next_link(link)
        return values


def next_link(link_header):
    for link in link_header.split(","):
        match = re.match(r"\s*<([^>]+)>;\s*rel=\"next\"", link)
        if match:
            return match.group(1)
    return None


def parse_github_time(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(BEIJING)


def week_range(week_start):
    start = datetime.combine(week_start, time.min, BEIJING)
    return start, start + timedelta(days=7)


def in_week(value, start, end):
    return start <= parse_github_time(value) < end


def issue_section(body, label):
    match = re.search(r"(?:^|\n)### " + re.escape(label) + r"\r?\n([\s\S]*?)(?=\r?\n### |$)", body or "")
    value = match.group(1).strip() if match else ""
    return "" if value == "_No response_" else value


def display_directory(directory):
    name = directory.strip().strip("/").split("/")[-1] if directory.strip() else "未分类"
    name = re.sub(r"^\d+-", "", name)
    name = re.sub(r"^xiangshan-", "", name, flags=re.IGNORECASE)
    return name or "未分类"


def docs_directories(repo_root=None):
    """Return display name and repository-relative path for local docs children."""
    root = Path(repo_root or Path(__file__).resolve().parent.parent)
    docs_roots = [root / "xiangshan-course/docs"]
    directories = []
    for docs_root in docs_roots:
        if not docs_root.is_dir():
            continue
        for child in docs_root.iterdir():
            if child.is_dir():
                directories.append((display_directory(child.name), child.relative_to(root).as_posix()))
    return directories


def resolve_directory(value, directories):
    raw = value.strip().strip("/")
    for name, path in directories:
        if raw == path or raw.endswith("/" + path):
            return name, path
    leaf = raw.split("/")[-1] if raw else ""
    matches = [(name, path) for name, path in directories if leaf in (Path(path).name, name)]
    return matches[0] if len(matches) == 1 else ("未分类", None)


def parse_ddl(value):
    match = DDL.match(value)
    if not match:
        return None
    year, month, day, clock = match.groups()
    try:
        day_value = date(int(year), int(month), int(day))
    except ValueError:
        return None
    if clock == "24:00":
        return datetime.combine(day_value + timedelta(days=1), time.min, BEIJING)
    hour, minute = map(int, clock.split(":"))
    return datetime.combine(day_value, time(hour, minute), BEIJING)


def marker(comment, pattern):
    if comment.get("user", {}).get("login", "").lower() != BOT_LOGIN:
        return None
    return pattern.search(comment.get("body") or "")


def sort_comments(comments):
    return sorted(comments, key=lambda item: (item.get("created_at", ""), item.get("id", 0)))


def extract_commit_refs(texts, default_repo=None):
    """Extract explicit commit references without guessing arbitrary short hex text."""
    commits = []
    seen = set()
    for text in texts:
        for match in COMMIT_URL.finditer(text or ""):
            sha = match.group("sha").lower()
            url = match.group("url")
            if url not in seen:
                seen.add(url)
                commits.append((sha, url))
        for match in LABELED_SHA.finditer(text or ""):
            sha = match.group(1).lower()
            url = "https://github.com/%s/commit/%s" % (default_repo, sha) if default_repo else None
            key = url or sha
            if key not in seen:
                seen.add(key)
                commits.append((sha, url))
    return commits


def extract_commits(texts):
    commits = []
    seen = set()
    for sha, _ in extract_commit_refs(texts):
        if sha not in seen:
            seen.add(sha)
            commits.append(sha)
    return commits


def analyze_comments(comments, start, end):
    """Return weekly events and the final bot-derived review state."""
    events = []
    review_state = None
    executor = None
    reviewers = set()
    for comment in sort_comments(comments):
        created_at = comment.get("created_at")
        assignment = marker(comment, ASSIGNMENT_MARKER)
        review = marker(comment, REVIEW_MARKER)
        if assignment and assignment.group(1) == "resolved" and created_at and in_week(created_at, start, end):
            events.append(("认领确认", assignment.group(2)))
        if review:
            review_state, executor = review.group(1), review.group(2)
            if review_state == "pending":
                reviewers = set()
                if created_at and in_week(created_at, start, end):
                    events.append(("提交验收", executor))
            continue

        user = comment.get("user") or {}
        login = user.get("login")
        is_lgtm = (comment.get("body") or "").strip().upper() == "LGTM"
        if review_state == "pending" and is_lgtm and login and user.get("type") != "Bot" and login.lower() != executor.lower():
            key = login.lower()
            if key not in reviewers:
                reviewers.add(key)
                if created_at and in_week(created_at, start, end):
                    events.append(("有效LGTM", login))
    return events, review_state, executor


def is_task(issue):
    return not issue.get("pull_request") and bool(re.match(r"^\[TASK\]\s+\S", issue.get("title") or ""))


def current_status(issue, review_state, overdue=False):
    if issue.get("state") == "open":
        if review_state == "pending":
            status = "待验收"
        elif review_state == "resolved":
            status = "进行中（已重新打开）"
        elif not issue.get("assignees"):
            status = "待认领"
        else:
            status = "进行中"
        return status + "（逾期）" if overdue else status
    return "已交付（已关闭）"


def markdown_cell(value):
    return str(value).replace("|", "\\|").replace("\n", " ").strip() or "-"


def task_row(task):
    issue = task["issue"]
    assignees = ", ".join("@" + user.get("login", "") for user in issue.get("assignees", []) if user.get("login")) or "未认领"
    commits = task["commits"]
    commit_text = "、".join("[%s](%s)" % (sha, url) if url else sha for sha, url in commits) if commits else "未记录"
    return "| %s | @%s | %s | [#%s](%s) | %s | %s | %s |" % (
        markdown_cell(re.sub(r"^\[TASK\]\s*", "", issue.get("title", ""))),
        markdown_cell((issue.get("user") or {}).get("login", "未知")),
        markdown_cell(assignees), issue.get("number"), issue.get("html_url", ""),
        markdown_cell(commit_text), markdown_cell(task["status"]), markdown_cell(task["ddl"] or "未填写"))


TABLE_HEADER = "| Task Description | Creator | Assignee | Issue Number | Commit Number | Weekly Status | DDL |\n| --- | --- | --- | --- | --- | --- | --- |"


def render_task_groups(tasks):
    if not tasks:
        return "无。"
    groups = defaultdict(list)
    for task in tasks:
        groups[(task["directory"], task["directory_path"])].append(task)
    sections = []
    for directory, path in sorted(groups, key=lambda item: item[0].lower()):
        group = groups[(directory, path)]
        rows = "\n".join(task_row(task) for task in sorted(group, key=lambda item: item["issue"].get("number", 0)))
        heading = "[%s](%s)" % (directory, task_directory_url(path, group[0]["repo"])) if path else directory
        sections.append("### %s\n\n%s\n%s" % (heading, TABLE_HEADER, rows))
    return "\n\n".join(sections)


def task_directory_url(path, repo):
    return "https://github.com/%s/tree/HEAD/%s" % (repo, quote(path))


def has_week_activity(issue, comments, start, end):
    timestamps = [issue.get(field) for field in ("created_at", "updated_at", "closed_at")]
    timestamps.extend(comment.get("created_at") for comment in comments)
    return any(value and in_week(value, start, end) for value in timestamps)


def build_report(issues, comments_by_issue, start, end, repo="owner/repo", directories=None, now=None):
    now = now or datetime.now(BEIJING)
    directories = docs_directories() if directories is None else directories
    contributions = defaultdict(lambda: defaultdict(int))
    directory_counts = {entry: defaultdict(int) for entry in directories}
    directory_contributors = defaultdict(set)
    delivered = []
    details = []
    for issue in issues:
        if not is_task(issue):
            continue
        comments = comments_by_issue.get(issue.get("number"), [])
        events, review_state, _ = analyze_comments(comments, start, end)
        if issue.get("created_at") and in_week(issue["created_at"], start, end):
            creator = (issue.get("user") or {}).get("login")
            if creator:
                events.append(("创建", creator))
        for event, login in events:
            contributions[login][event] += 1
        ddl = issue_section(issue.get("body") or "", "截止时间（DDL）")
        deadline = parse_ddl(ddl)
        overdue = bool(issue.get("state") == "open" and deadline and deadline < now)
        status = current_status(issue, review_state, overdue)
        directory, directory_path = resolve_directory(issue_section(issue.get("body") or "", "所属目录"), directories)
        key = (directory, directory_path)
        counts = directory_counts.setdefault(key, defaultdict(int))
        counts["任务总数"] += 1
        if issue.get("created_at") and in_week(issue["created_at"], start, end):
            counts["本周新增"] += 1
        closed = issue.get("state") == "closed"
        weekly_delivery = bool(closed and issue.get("closed_at") and in_week(issue["closed_at"], start, end))
        counts["累计交付" if closed else "尚未关闭"] += 1
        if weekly_delivery:
            counts["本周交付"] += 1
            # Attribute delivery to assignees, never to the creator or closing bot.
            for login in {user["login"] for user in issue.get("assignees", []) if user.get("login")}:
                contributions[login]["本周交付"] += 1
                directory_contributors[key].add(login)
            if not issue.get("assignees"):
                counts["执行人未记录"] += 1
        task = {
            "issue": issue,
            "directory": directory,
            "directory_path": directory_path,
            "ddl": ddl,
            "status": status,
            "commits": extract_commit_refs([issue.get("body") or ""] + [comment.get("body") or "" for comment in comments], repo),
            "repo": repo,
        }
        if weekly_delivery:
            delivered.append(task)
        elif has_week_activity(issue, comments, start, end) or issue.get("state") == "open":
            details.append(task)
        if issue.get("state") == "open" and issue.get("assignees"):
            for assignee in issue["assignees"]:
                login = assignee.get("login")
                if not login:
                    continue
                if review_state == "pending":
                    contributions[login]["当前待验收"] += 1
                else:
                    contributions[login]["当前进行中"] += 1
                if overdue:
                    contributions[login]["当前逾期"] += 1

    directory_rows = []
    for key in sorted(directory_counts, key=lambda item: item[0].lower()):
        name, path = key
        counts = directory_counts[key]
        heading = "[%s](%s)" % (name, task_directory_url(path, repo)) if path else name
        people = ", ".join("@" + login for login in sorted(directory_contributors[key], key=str.lower))
        if counts["执行人未记录"]:
            people = (people + "; " if people else "") + "执行人未记录：%d 项" % counts["执行人未记录"]
        directory_rows.append("| %s | %d | %d | %d | %d | %d | %s |" % (
            heading, counts["任务总数"], counts["本周新增"], counts["本周交付"],
            counts["累计交付"], counts["尚未关闭"], people or "无"))

    contribution_rows = []
    for login in sorted(contributions, key=str.lower):
        counts = contributions[login]
        contribution_rows.append("| @%s | %d | %d | %d | %d | %d | %d | %d | %d |" % (
            login, counts["创建"], counts["认领确认"], counts["提交验收"], counts["本周交付"], counts["有效LGTM"],
            counts["当前进行中"], counts["当前待验收"], counts["当前逾期"]))
    contribution_table = "\n".join(contribution_rows) or "| - | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |"
    template = """# Weekly Status: {date}

统计区间：北京时间 {start}（含）至 {end}（不含）。

## 目录贡献汇总

交付只看任务 Issue 是否关闭；本周交付按 `closed_at` 落在统计区间内计算，不再额外检查 LGTM 或验收评论。每个任务只归属一个目录，目录交付数按 Issue 计数。

| 所属目录 | 任务总数 | 本周新增 | 本周交付 | 累计交付（当前） | 尚未关闭（当前） | 本周交付贡献者 |
| --- | --- | --- | --- | --- | --- | --- |
{directory_summary}

## 本周交付明细

按目录列出谁交付了哪些任务；贡献归属 Assignee，不归属发布者或执行关闭操作的机器人。未记录执行人的交付仍计入目录，但不猜测个人贡献。

{delivered}

## 用户交付与参与

| Contributor | 创建 | 认领确认 | 提交验收 | 本周交付 | 有效 LGTM | 当前进行中 | 当前待验收 | 当前逾期 |
{contribution_separator}\n{contributions}

创建、认领、提交验收、LGTM 仅表示参与活动，不算已交付贡献。多人共同负责时每人计一次，目录内该 Issue 仍只计一次。

## 其他任务明细

列出本周有任意评论、`updated_at`、`closed_at` 或创建活动，或当前仍为 open 的其余 `[TASK]` Issue；本周交付已单独列出。状态、累计交付、尚未关闭和执行人为生成时的当前快照，补跑历史周报不会还原当时状态。已重新打开的 Issue 不计交付。

{details}
"""
    return template.format(date=start.date().isoformat(), start=start.strftime("%Y-%m-%d %H:%M"),
            end=end.strftime("%Y-%m-%d %H:%M"), contributions=contribution_table,
            directory_summary="\n".join(directory_rows), delivered=render_task_groups(delivered),
           details=render_task_groups(details),
           contribution_separator="| --- | --- | --- | --- | --- | --- | --- | --- | --- |")


def default_week_start(now=None):
    today = (now or datetime.now(BEIJING)).date()
    return today - timedelta(days=today.weekday() + 7)


def parse_week_start(value):
    try:
        result = date.fromisoformat(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError("--week-start must be YYYY-MM-DD") from error
    if result.weekday() != 0:
        raise argparse.ArgumentTypeError("--week-start must be a Monday")
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--week-start", type=parse_week_start, help="Monday in Beijing time")
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY"), help="owner/repo; defaults to GITHUB_REPOSITORY")
    parser.add_argument("--output-dir", default="weekly-status")
    args = parser.parse_args(argv)
    if not args.repo or args.repo.count("/") != 1:
        parser.error("--repo owner/repo or GITHUB_REPOSITORY is required")
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if not token:
        parser.error("GH_TOKEN or GITHUB_TOKEN is required")
    week_start = args.week_start or default_week_start()
    start, end = week_range(week_start)
    client = GitHubClient(token)
    issues = client.get_paginated("/repos/%s/issues" % args.repo, {"state": "all"})
    comments_by_issue = {}
    for issue in issues:
        if is_task(issue):
            comments_by_issue[issue["number"]] = client.get_paginated(
                "/repos/%s/issues/%s/comments" % (args.repo, issue["number"]))
    report = build_report(issues, comments_by_issue, start, end, repo=args.repo)
    output = Path(args.output_dir) / (week_start.isoformat() + ".md")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(report, encoding="utf-8")
    print(output)


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as error:
        print("error: " + str(error), file=sys.stderr)
        sys.exit(1)
