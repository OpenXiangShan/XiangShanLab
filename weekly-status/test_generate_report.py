import unittest
from datetime import date, datetime
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import mock
import os
import sys

sys.path.insert(0, str(Path(__file__).parent))
import generate_report as report


START, END = report.week_range(date(2026, 8, 31))


def issue(number=1, state="open", created_at="2026-09-01T00:00:00Z", body="", assignees=None, title="[TASK] Test", **fields):
    result = {"number": number, "state": state, "created_at": created_at, "body": body,
            "assignees": assignees or [], "title": title, "html_url": "https://example.test/%s" % number,
            "user": {"login": "creator"}}
    result.update(fields)
    return result


def comment(body, login="github-actions[bot]", created_at="2026-09-01T00:00:00Z", user_type="Bot", id=1):
    return {"body": body, "created_at": created_at, "id": id, "user": {"login": login, "type": user_type}}


class ReportTest(unittest.TestCase):
    def test_week_boundary_is_beijing_half_open(self):
        self.assertTrue(report.in_week("2026-08-30T16:00:00Z", START, END))
        self.assertFalse(report.in_week("2026-09-06T16:00:00Z", START, END))

    def test_default_week_is_previous_complete_week(self):
        self.assertEqual(report.default_week_start(datetime(2026, 9, 7, 9, tzinfo=report.BEIJING)), date(2026, 8, 31))

    def test_only_github_actions_markers_are_counted(self):
        comments = [comment("<!-- task-assignment:resolved claimant=alice -->", login="other-bot", id=1)]
        events, _, _ = report.analyze_comments(comments, START, END)
        self.assertEqual(events, [])

    def test_lgtm_is_deduplicated_and_excludes_executor_and_bots(self):
        comments = [
            comment("<!-- task-review:pending executor=alice -->", id=1),
            comment("LGTM", "bob", user_type="User", id=2),
            comment("lgtm", "bob", user_type="User", id=3),
            comment("LGTM", "alice", user_type="User", id=4),
            comment("LGTM", "robot", user_type="Bot", id=5),
        ]
        events, _, _ = report.analyze_comments(comments, START, END)
        self.assertEqual(events, [("提交验收", "alice"), ("有效LGTM", "bob")])

    def test_reopened_resolved_issue_is_not_completed(self):
        task = issue()
        text = report.build_report([task], {1: [comment("<!-- task-review:resolved executor=alice -->")]}, START, END)
        self.assertIn("进行中（已重新打开）", text)
        self.assertNotIn("| 未记录 | 验收通过 |", text)

    def test_cross_week_votes_and_successful_review(self):
        comments = [
            comment("<!-- task-assignment:resolved claimant=alice -->", id=1),
            comment("<!-- task-review:pending executor=alice -->", created_at="2026-08-29T00:00:00Z", id=2),
            comment("LGTM", "bob", "2026-08-29T01:00:00Z", "User", id=3),
            comment("LGTM", "BOB", user_type="User", id=4),
            comment("LGTM", "carol", user_type="User", id=5),
            comment("LGTM", "dave", user_type="User", id=6),
            comment("<!-- task-review:resolved executor=alice -->", id=7),
            comment("LGTM", "eve", user_type="User", id=8),
        ]
        events, state, executor = report.analyze_comments(comments, START, END)
        self.assertEqual(events, [("认领确认", "alice"), ("有效LGTM", "carol"),
                                  ("有效LGTM", "dave"), ("验收通过", "alice")])
        self.assertEqual((state, executor), ("resolved", "alice"))
        self.assertEqual(report.current_status(issue(state="closed"), state), "验收通过")

    def test_commit_urls_and_labeled_shas_are_deduplicated(self):
        sha = "abcdef1234567"
        commits = report.extract_commits([
            "https://github.com/a/b/commit/" + sha + " random deadbee",
            "SHA: `" + sha.upper() + "` and commit: 1234567",
        ])
        self.assertEqual(commits, [sha, "1234567"])

    def test_commit_links_preserve_source_repo_and_labeled_sha_uses_current_repo(self):
        sha = "abcdef1234567"
        refs = report.extract_commit_refs([
            "https://github.com/other/project/commit/" + sha,
            "SHA: 1234567",
        ], "current/repo")
        self.assertEqual(refs, [
            (sha, "https://github.com/other/project/commit/" + sha),
            ("1234567", "https://github.com/current/repo/commit/1234567"),
        ])

    def test_long_hex_is_not_truncated_to_a_sha(self):
        self.assertEqual(report.extract_commits(["SHA: " + "a" * 41]), [])

    def test_missing_fields_and_multiple_assignees(self):
        task = issue(assignees=[{"login": "alice"}, {"login": "bob"}])
        text = report.build_report([task], {1: []}, START, END)
        self.assertIn("@alice, @bob", text)
        self.assertIn("未分类", text)
        self.assertIn("未填写", text)
        self.assertIn("未记录", text)

    def test_ddl_2400_is_next_midnight(self):
        self.assertEqual(report.parse_ddl("2026-09-01 24:00"), datetime(2026, 9, 2, 0, tzinfo=report.BEIJING))

    def test_directory_display_removes_prefixes(self):
        self.assertEqual(report.display_directory("docs/3-xiangshan-frontend"), "frontend")

    def test_directory_group_links_to_mapped_docs_path(self):
        body = "### 所属目录\n1-xiangshan-development-environment"
        text = report.build_report([issue(body=body)], {1: []}, START, END, repo="org/repo",
                                   directories=[("development-environment", "xiangshan-course/docs/1-xiangshan-development-environment")])
        self.assertIn("[development-environment](https://github.com/org/repo/tree/HEAD/xiangshan-course/docs/1-xiangshan-development-environment)", text)

    def test_docs_mapping_reads_real_first_level_docs_directory(self):
        self.assertIn(("development-environment", "xiangshan-course/docs/1-xiangshan-development-environment"),
                      report.docs_directories())

    def test_any_comment_or_issue_update_in_week_includes_closed_detail(self):
        old = "2026-08-01T00:00:00Z"
        updated = issue(state="closed", created_at=old, updated_at="2026-09-02T00:00:00Z")
        commented = issue(number=2, state="closed", created_at=old)
        text = report.build_report([updated, commented], {
            1: [], 2: [comment("ordinary comment", "alice", "2026-09-03T00:00:00Z", "User")],
        }, START, END)
        self.assertIn("[#1](https://example.test/1)", text)
        self.assertIn("[#2](https://example.test/2)", text)

    def test_current_assignee_stock_columns_and_overdue_status(self):
        overdue_body = "### 截止时间（DDL）\n2026-09-01 24:00"
        active = issue(body=overdue_body, assignees=[{"login": "alice"}, {"login": "bob"}])
        pending = issue(number=2, assignees=[{"login": "bob"}])
        unassigned = issue(number=3)
        text = report.build_report([active, pending, unassigned], {
            1: [], 2: [comment("<!-- task-review:pending executor=bob -->")], 3: [],
        }, START, END, now=datetime(2026, 9, 7, tzinfo=report.BEIJING))
        self.assertIn("待认领", text)
        self.assertIn("进行中（逾期）", text)
        self.assertIn("| @alice | 0 | 0 | 0 | 0 | 0 | 1 | 0 | 1 |", text)
        self.assertIn("| @bob | 0 | 0 | 1 | 0 | 0 | 1 | 1 | 1 |", text)
        self.assertNotIn("| @unassigned |", text)

    def test_contribution_header_and_separator_have_equal_columns(self):
        text = report.build_report([], {}, START, END)
        lines = text.splitlines()
        header = lines[lines.index("## 本周贡献") + 2]
        separator = lines[lines.index("## 本周贡献") + 3]
        self.assertEqual(len(header.strip("|").split("|")), 9)
        self.assertEqual(len(header.strip("|").split("|")), len(separator.strip("|").split("|")))
        self.assertNotIn("## 存量：", text)

    def test_pagination_follows_next_link(self):
        class Client(report.GitHubClient):
            def __init__(self):
                pass
            def _request(self, url):
                if "page=1" in url:
                    return [{"id": 1}], '<https://api.github.com/test?page=2>; rel="next"'
                return [{"id": 2}], ""
        self.assertEqual([item["id"] for item in Client().get_paginated("/test")], [1, 2])

    def test_closed_without_resolved_is_not_approved(self):
        self.assertEqual(report.current_status(issue(state="closed"), "pending"), "已关闭（未验收）")

    def test_api_failure_does_not_create_report(self):
        class FailingClient:
            def __init__(self, token):
                pass
            def get_paginated(self, path, params=None):
                raise RuntimeError("network failure")
        with TemporaryDirectory() as temporary, mock.patch.dict(os.environ, {"GH_TOKEN": "token"}, clear=False), \
                mock.patch.object(report, "GitHubClient", FailingClient):
            output = Path(temporary) / "output"
            with self.assertRaises(RuntimeError):
                report.main(["--repo", "org/repo", "--week-start", "2026-08-31", "--output-dir", str(output)])
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
