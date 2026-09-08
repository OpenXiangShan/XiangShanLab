"""Report regression tests run only by GitHub Actions."""

import os
import sys
import unittest
from datetime import date, datetime
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import mock

sys.path.insert(0, str(Path(__file__).parent))
import generate_report as report


START, END = report.week_range(date(2026, 8, 31))


def issue(number=1, state="open", created_at="2026-09-01T00:00:00Z", body="", assignees=None,
          title="[TASK] Test", **fields):
    result = {
        "number": number, "state": state, "created_at": created_at, "body": body,
        "assignees": assignees or [], "title": title,
        "html_url": "https://example.test/%s" % number, "user": {"login": "creator"},
    }
    result.update(fields)
    return result


def comment(body, login="github-actions[bot]", created_at="2026-09-01T00:00:00Z", user_type="Bot", id=1):
    return {"body": body, "created_at": created_at, "id": id,
            "user": {"login": login, "type": user_type}}


class ReportTest(unittest.TestCase):
    def test_week_boundary_is_beijing_half_open(self):
        self.assertTrue(report.in_week("2026-08-30T16:00:00Z", START, END))
        self.assertFalse(report.in_week("2026-09-06T16:00:00Z", START, END))

    def test_default_week_is_previous_complete_week(self):
        now = datetime(2026, 9, 7, 9, tzinfo=report.BEIJING)
        self.assertEqual(report.default_week_start(now), date(2026, 8, 31))

    def test_ddl_boundary_2400_and_invalid_values(self):
        self.assertEqual(report.parse_ddl("2026-09-01 24:00"), datetime(2026, 9, 2, 0, tzinfo=report.BEIJING))

        def reasons(number, ddl):
            body = "### 截止时间（DDL）\n" + ddl if ddl is not None else ""
            return report.week_reasons(issue(number=number, created_at="2026-08-01T00:00:00Z", body=body), START, END)

        self.assertEqual(reasons(1, "2026-08-31 00:00"), ["本周到期"])
        self.assertEqual(reasons(2, "2026-09-06 23:59"), ["本周到期"])
        self.assertEqual(reasons(3, "2026-09-06 24:00"), [])
        self.assertEqual(reasons(4, "2026-09-01 24:00"), ["本周到期"])
        self.assertEqual(reasons(5, None), [])
        self.assertEqual(reasons(6, "2026-09-31 12:00"), [])

    def test_week_reasons_are_the_deduplicated_union_of_all_three_conditions(self):
        body = "### 所属目录\nAI\n### 截止时间（DDL）\n2026-09-03 12:00"
        task = issue(7, state="closed", body=body, closed_at="2026-09-04T00:00:00Z")
        self.assertEqual(report.week_reasons(task, START, END), ["本周新增", "本周交付", "本周到期"])

        text = report.build_report([task, task], {}, START, END,
                                   directories=[("AI", "xiangshan-course/docs/8-xiangshan-AI")])
        self.assertIn("| 1 | 1 | 1 | 1 | 0 | 执行人未记录：1 项 |", text)
        self.assertEqual(text.count("[#7](https://example.test/7)"), 1)

    def test_updated_or_commented_tasks_outside_the_three_conditions_are_excluded(self):
        old = "2026-08-01T00:00:00Z"
        updated = issue(created_at=old, updated_at="2026-09-02T00:00:00Z")
        commented = issue(number=2, created_at=old)
        text = report.build_report([updated, commented], {
            1: [], 2: [comment("ordinary comment", "alice", "2026-09-03T00:00:00Z", "User")],
        }, START, END)
        self.assertNotIn("[#1](https://example.test/1)", text)
        self.assertNotIn("[#2](https://example.test/2)", text)
        self.assertNotIn("@alice", text)

    def test_only_github_actions_markers_are_counted(self):
        comments = [comment("<!-- task-assignment:resolved claimant=alice -->", login="other-bot", id=1)]
        events, _, _, lgtm_count = report.analyze_comments(comments, START, END)
        self.assertEqual(events, [])
        self.assertEqual(lgtm_count, 0)

    def test_lgtm_is_deduplicated_and_excludes_executor_and_bots(self):
        comments = [
            comment("<!-- task-review:pending executor=alice -->", id=1),
            comment("LGTM", "bob", user_type="User", id=2),
            comment("lgtm", "bob", user_type="User", id=3),
            comment("LGTM", "alice", user_type="User", id=4),
            comment("LGTM", "robot", user_type="Bot", id=5),
        ]
        events, _, _, lgtm_count = report.analyze_comments(comments, START, END)
        self.assertEqual(events, [("提交验收", "alice"), ("有效LGTM", "bob")])
        self.assertEqual(lgtm_count, 1)

    def test_effective_lgtm_progress_from_zero_through_three(self):
        for expected in range(4):
            with self.subTest(expected=expected):
                comments = [comment("<!-- task-review:pending executor=alice -->", id=1)]
                comments.extend(comment("LGTM", "reviewer%s" % index, user_type="User", id=index + 2)
                                for index in range(expected))
                _, _, _, lgtm_count = report.analyze_comments(comments, START, END)
                self.assertEqual(lgtm_count, expected)
                self.assertEqual(report.current_status(issue(), lgtm_count),
                                 "未交付（未关闭）；LGTM %d/3" % expected)

    def test_cross_week_votes_show_total_progress_but_only_weekly_votes_contribute(self):
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
        events, state, executor, lgtm_count = report.analyze_comments(comments, START, END)
        self.assertEqual(events, [("认领确认", "alice"), ("有效LGTM", "carol"), ("有效LGTM", "dave")])
        self.assertEqual((state, executor, lgtm_count), ("resolved", "alice", 3))

    def test_status_is_not_inferred_from_assignment_or_review(self):
        task = issue()
        comments = [
            comment("<!-- task-assignment:resolved claimant=alice -->", id=1),
            comment("<!-- task-review:pending executor=alice -->", id=2),
            comment("LGTM", "bob", user_type="User", id=3),
            comment("LGTM", "carol", user_type="User", id=4),
            comment("LGTM", "dave", user_type="User", id=5),
        ]
        text = report.build_report([task], {1: comments}, START, END)
        self.assertIn("未交付（未关闭）；LGTM 3/3", text)
        self.assertEqual(report.current_status(issue(state="closed"), 0), "已交付（已关闭）；LGTM 0/3")

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

    def test_directory_display_and_mapping(self):
        self.assertEqual(report.display_directory("docs/3-xiangshan-frontend"), "frontend")
        body = "### 所属目录\n1-xiangshan-development-environment"
        text = report.build_report([issue(body=body)], {1: []}, START, END, repo="org/repo",
                                   directories=[("development-environment", "xiangshan-course/docs/1-xiangshan-development-environment")])
        self.assertIn("[development-environment](https://github.com/org/repo/tree/HEAD/xiangshan-course/docs/1-xiangshan-development-environment)", text)

    def test_all_directories_are_listed_without_tasks(self):
        directories = report.docs_directories()
        text = report.build_report([], {}, START, END, directories=directories)
        for name, path in directories:
            self.assertIn("| [%s](%s) | 0 | 0 | 0 | 0 | 0 | 无 |" % (
                name, report.task_directory_url(path, "owner/repo")), text)

    def test_directory_delivery_uses_closed_at_and_assignees(self):
        tasks = [
            issue(state="closed", body="### 所属目录\nAI", closed_at="2026-09-01T00:00:00Z",
                  assignees=[{"login": "alice"}, {"login": "bob"}]),
            issue(number=2, body="### 所属目录\nAI", assignees=[{"login": "carol"}]),
        ]
        text = report.build_report(tasks, {2: [comment("<!-- task-review:resolved executor=carol -->")]},
                                   START, END, directories=[("AI", "xiangshan-course/docs/8-xiangshan-AI")])
        self.assertIn("| 2 | 2 | 1 | 0 | 0 | @alice, @bob |", text)
        self.assertIn("| @alice | 0 | 0 | 0 | 1 | 0 | 0 | 0 |", text)
        self.assertIn("| @bob | 0 | 0 | 0 | 1 | 0 | 0 | 0 |", text)
        self.assertIn("| @carol | 0 | 0 | 0 | 0 | 0 | 1 | 0 |", text)

    def test_delivery_week_uses_closed_at_boundaries(self):
        tasks = [issue(number=index, state="closed", created_at="2026-08-01T00:00:00Z",
                       closed_at=closed_at, body="### 所属目录\nAI", assignees=[{"login": "alice"}])
                 for index, closed_at in enumerate([
                     "2026-08-30T15:59:59Z", "2026-08-30T16:00:00Z",
                     "2026-09-06T15:59:59Z", "2026-09-06T16:00:00Z"], 1)]
        text = report.build_report(tasks, {}, START, END,
                                   directories=[("AI", "xiangshan-course/docs/8-xiangshan-AI")])
        self.assertIn("| 2 | 0 | 2 | 0 | 0 | @alice |", text)
        self.assertIn("| @alice | 0 | 0 | 0 | 2 | 0 | 0 | 0 |", text)

    def test_current_unclosed_and_overdue_columns(self):
        overdue_body = "### 截止时间（DDL）\n2026-09-01 24:00"
        active = issue(body=overdue_body, assignees=[{"login": "alice"}, {"login": "bob"}])
        pending = issue(number=2, assignees=[{"login": "bob"}])
        text = report.build_report([active, pending], {1: [], 2: []}, START, END,
                                   now=datetime(2026, 9, 7, tzinfo=report.BEIJING))
        self.assertIn("未交付（未关闭）；LGTM 0/3；逾期", text)
        self.assertIn("| @alice | 0 | 0 | 0 | 0 | 0 | 1 | 1 |", text)
        self.assertIn("| @bob | 0 | 0 | 0 | 0 | 0 | 2 | 1 |", text)

    def test_contributor_table_has_eight_columns(self):
        text = report.build_report([], {}, START, END)
        lines = text.splitlines()
        header = lines[lines.index("## 用户交付与参与") + 2]
        separator = lines[lines.index("## 用户交付与参与") + 3]
        self.assertEqual(len(header.strip("|").split("|")), 8)
        self.assertEqual(len(header.strip("|").split("|")), len(separator.strip("|").split("|")))

    def test_pull_requests_and_non_tasks_do_not_count(self):
        tasks = [issue(state="closed", pull_request={"url": "https://example.test/pr"}),
                 issue(number=2, state="closed", title="Not a task")]
        text = report.build_report(tasks, {}, START, END,
                                   directories=[("AI", "xiangshan-course/docs/8-xiangshan-AI")])
        self.assertIn("| 0 | 0 | 0 | 0 | 0 | 无 |", text)
        self.assertNotIn("[#1]", text)
        self.assertNotIn("[#2]", text)

    def test_main_fetches_comments_only_for_selected_tasks_with_comments(self):
        class Client:
            calls = []

            def __init__(self, token):
                pass

            def get_paginated(self, path, params=None):
                self.calls.append((path, params))
                if path.endswith("/issues"):
                    return [
                        issue(1, comments=2),
                        issue(2, created_at="2026-08-01T00:00:00Z", updated_at="2026-09-02T00:00:00Z", comments=4),
                        issue(3, comments=0),
                    ]
                if path.endswith("/issues/1/comments"):
                    return []
                raise AssertionError("unexpected comment request: " + path)

        with TemporaryDirectory() as temporary, mock.patch.dict(os.environ, {"GH_TOKEN": "token"}, clear=False), \
                mock.patch.object(report, "GitHubClient", Client), \
                mock.patch.object(report, "docs_directories", return_value=[]):
            report.main(["--repo", "org/repo", "--week-start", "2026-08-31", "--output-dir", temporary])

        self.assertEqual(Client.calls, [
            ("/repos/org/repo/issues", {"state": "all"}),
            ("/repos/org/repo/issues/1/comments", None),
        ])

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

    def test_pagination_follows_next_link(self):
        class Client(report.GitHubClient):
            def __init__(self):
                pass

            def _request(self, url):
                if "page=1" in url:
                    return [{"id": 1}], '<https://api.github.com/test?page=2>; rel="next"'
                return [{"id": 2}], ""

        self.assertEqual([item["id"] for item in Client().get_paginated("/test")], [1, 2])


if __name__ == "__main__":
    unittest.main()
