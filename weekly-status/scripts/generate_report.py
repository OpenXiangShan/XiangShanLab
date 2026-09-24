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
ASSIGNMENT_MARKER = re.compile(r"<!--\s*task-assignment:(pending|resolved|failed) claimant=([A-Za-z0-9-]+)\s*-->")
REVIEW_MARKER = re.compile(r"<!--\s*task-review:(pending|resolved) executor=([A-Za-z0-9-]+)\s*-->")
DDL = re.compile(r"^(\d{4})-(\d{2})-(\d{2}) ((?:[01]\d|2[0-3]):[0-5]\d|24:00)$")
DIRECTORY_ALIASES = {
    "xiangshan-AI": ("AI/basic-algorithm", "AI"),
    "xiangshang-verification": ("verification/uvm", "verification", "11-xiangshang-verification", "13-xiangshang-verification"),
    "xiangshan-aglie-tools": ("aglie-tools", "agile-tools"),
    "xiangshan-ddr": ("ddr", "DDR"),
}
TEMPLATE_FIELDS = ("所属目录", "任务背景与目标", "交付清单", "任务难度", "预估工时", "质押积分", "截止时间（DDL）")


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
    root = Path(repo_root or Path(__file__).resolve().parents[2])
    docs_roots = [root / "xiangshan-course/docs"]
    directories = []
    for docs_root in docs_roots:
        if not docs_root.is_dir():
            continue
        for child in docs_root.iterdir():
            if child.is_dir():
                path = child.relative_to(root).as_posix()
                topic = re.sub(r"^\d+-", "", child.name)
                label = next((values[0] for key, values in DIRECTORY_ALIASES.items() if key == topic),
                             display_directory(child.name))
                directories.append((label, path))
    return directories


def resolve_directory(value, directories):
    raw = value.strip().strip("/")
    for name, path in directories:
        topic = re.sub(r"^\d+-", "", Path(path).name)
        aliases = {name, path, Path(path).name, topic}
        aliases.update(DIRECTORY_ALIASES.get(topic, ()))
        if raw in aliases:
            return name, path
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


def analyze_comments(comments, start, end):
    """Return weekly events, review state, executor and valid LGTM count."""
    events = []
    review_state = None
    executor = None
    reviewers = set()
    for comment in sort_comments(comments):
        created_at = comment.get("created_at")
        assignment = marker(comment, ASSIGNMENT_MARKER)
        review = marker(comment, REVIEW_MARKER)
        if assignment and assignment.group(1) == "resolved" and created_at and in_week(created_at, start, end):
            # Older failure comments also used resolved; preserve their actual outcome.
            message = (comment.get("body") or "")[assignment.end():].strip()
            if not message.startswith(("认领未完成：", "无法将 @")):
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
    return events, review_state, executor, len(reviewers)


def is_task(issue):
    return not issue.get("pull_request") and bool(re.match(r"^\[TASK\]\s+\S", issue.get("title") or ""))


def template_quality(issue):
    """Return missing task template fields without excluding legacy tasks."""
    body = issue.get("body") or ""
    return [field for field in TEMPLATE_FIELDS if not issue_section(body, field)]


def current_status(issue, lgtm_count, risk=None):
    status = "已交付（已关闭）" if issue.get("state") == "closed" else "未交付（未关闭）"
    status += "；LGTM %d/3" % lgtm_count
    return status + ("；" + risk if risk else "")


def markdown_cell(value):
    return str(value).replace("|", "\\|").replace("\n", " ").strip() or "-"


def task_row(task):
    issue = task["issue"]
    assignees = ", ".join("@" + user.get("login", "") for user in issue.get("assignees", []) if user.get("login")) or "未认领"
    return "| %s | @%s | %s | [#%s](%s) | %s | %s |" % (
        markdown_cell(re.sub(r"^\[TASK\]\s*", "", issue.get("title", ""))),
        markdown_cell((issue.get("user") or {}).get("login", "未知")),
        markdown_cell(assignees), issue.get("number"), issue.get("html_url", ""),
        markdown_cell(task["status"]), markdown_cell(task["ddl"] or "未填写"))


TABLE_HEADER = "| 任务 | 创建者 | 执行人 | Issue | 状态 | DDL |\n| --- | --- | --- | --- | --- | --- |"


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


def week_reasons(issue, start, end):
    if not is_task(issue):
        return []
    reasons = []
    if issue.get("created_at") and in_week(issue["created_at"], start, end):
        reasons.append("本周新增")
    if issue.get("state") == "closed" and issue.get("closed_at") and in_week(issue["closed_at"], start, end):
        reasons.append("本周交付")
    deadline = parse_ddl(issue_section(issue.get("body") or "", "截止时间（DDL）"))
    if deadline and start <= deadline < end:
        reasons.append("本周到期")
    return reasons


def build_report(issues, comments_by_issue, start, end, repo="owner/repo", directories=None, now=None):
    now = now or datetime.now(BEIJING)
    directories = docs_directories() if directories is None else directories
    contributions = defaultdict(lambda: defaultdict(int))
    total = defaultdict(int)
    people = defaultdict(lambda: defaultdict(int))
    seen = set()
    for issue in issues:
        reasons = week_reasons(issue, start, end)
        if not reasons or issue["number"] in seen:
            continue
        seen.add(issue["number"])
        comments = comments_by_issue.get(issue.get("number"), [])
        events, _, _, lgtm_count = analyze_comments(comments, start, end)
        if issue.get("created_at") and in_week(issue["created_at"], start, end):
            creator = (issue.get("user") or {}).get("login")
            if creator:
                events.append(("创建", creator))
        ddl = issue_section(issue.get("body") or "", "截止时间（DDL）")
        deadline = parse_ddl(ddl)
        due_open = "本周到期" in reasons and issue.get("state") == "open"
        historical_overdue = bool(issue.get("state") == "open" and deadline and deadline < now and
                                  "本周到期" not in reasons)
        risk = "本周到期未完成" if due_open else "历史逾期" if historical_overdue else None
        status = current_status(issue, lgtm_count, risk)
        total["任务数"] += 1
        for reason in reasons:
            total[reason] += 1
        if due_open:
            total["本周到期未完成"] += 1
        if historical_overdue:
            total["历史逾期"] += 1
        weekly_delivery = "本周交付" in reasons
        assignees = {user["login"] for user in issue.get("assignees", []) if user.get("login")}
        target_people = assignees or {"未认领"}
        for login in target_people:
            if weekly_delivery:
                people[login]["本周交付"] += 1
            if issue.get("state") == "open":
                people[login]["当前未关闭"] += 1
            if due_open:
                people[login]["本周到期未完成"] += 1
            if historical_overdue:
                people[login]["历史逾期"] += 1
                people[login]["未认领风险"] += int(login == "未认领")
    person_sections = []
    for login in sorted(people, key=str.lower):
        counts = people[login]
        label = "未认领" if login == "未认领" else "@" + login
        person_sections.append("### %s\n\n| 本周交付 | 当前未关闭 | 本周到期未完成 | 历史逾期 | 未认领风险 |\n| --- | --- | --- | --- | --- |\n| %d | %d | %d | %d | %d |" % (
            label, counts["本周交付"], counts["当前未关闭"], counts["本周到期未完成"],
            counts["历史逾期"], counts["未认领风险"]))
    total_row = "| 总计 | %d | %d | %d | %d | %d | %d |" % (total["任务数"], total["本周新增"], total["本周交付"], total["本周到期"], total["本周到期未完成"], total["历史逾期"])
    return "| 总计 | 任务数 | 本周新增 | 本周交付 | 本周到期 | 本周到期未完成 | 历史逾期 |\n| --- | --- | --- | --- | --- | --- | --- |\n" + total_row + "\n\n" + "\n\n".join(person_sections)


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
    # DDL is a body field, so updated-since filtering would miss old tasks due this week.
    issues = list({issue["number"]: issue for issue in issues if week_reasons(issue, start, end)}.values())
    comments_by_issue = {}
    for issue in issues:
        if issue.get("comments") != 0:
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
