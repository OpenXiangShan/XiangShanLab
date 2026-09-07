# Weekly Status

`generate_report.py` uses the GitHub REST API and Python's standard library only. It produces the report for the previous complete Monday-through-Sunday week in Beijing time; the report file is named for that week's Monday.

```sh
GH_TOKEN=... python3 weekly-status/generate_report.py --repo owner/repo
GH_TOKEN=... python3 weekly-status/generate_report.py --repo owner/repo --week-start 2026-08-31
```

`--week-start` must be a Monday. `--repo` falls back to `GITHUB_REPOSITORY`, `GH_TOKEN` takes precedence over `GITHUB_TOKEN`, and `--output-dir` defaults to `weekly-status`. The final stdout line is the generated path, for example `weekly-status/2026-08-31.md`.

The report fetches every page of repository Issues (`state=all`) and every page of comments for each `[TASK]` Issue. API errors stop generation before an output directory or report file is created. Pull requests and non-`[TASK]` Issues are excluded.

## Counting Rules

- Week boundaries are `[Monday 00:00, next Monday 00:00)` in `Asia/Shanghai`.
- The contribution table counts Issue creation, and only `github-actions[bot]` comments carrying `<!-- task-assignment:resolved claimant=... -->`, `<!-- task-review:pending executor=... -->`, or `<!-- task-review:resolved executor=... -->` during the week.
- An effective `LGTM` is an exact, non-bot `LGTM` comment after a pending review marker, from someone other than the executor. Each reviewer counts once per review round, including when their earlier vote was outside the reporting week.
- Approval is identified by `task-review:resolved`, not simply by a closed Issue.
- Commit values are unique explicit GitHub commit URLs or explicitly labelled `SHA`/`commit` values. URLs retain their source repository and are rendered as clickable SHAs; labelled bare SHAs link to the selected `--repo`. Unlabelled seven-character-looking text and hex values longer than 40 characters are ignored; no extracted commits render as `未记录`.
- `所属目录` and `截止时间（DDL）` are read from `###` sections in the Issue body. The script reads the local `xiangshan-course/docs` directory's immediate child directories, and a uniquely mapped value renders as a link to its full repository path on the default branch. Unknown or ambiguous values render as `未分类`. DDL accepts `YYYY-MM-DD HH:mm`, including `24:00`.
- The detail table includes current open tasks and closed tasks with any Issue creation, `updated_at`, `closed_at`, or comment activity in the week. Its status column marks an open expired DDL as `（逾期）`.
- The contributor table includes weekly event counts plus current assigned stock: `当前进行中`, `当前待验收`, and `当前逾期`. Every current assignee is counted; unassigned tasks are not counted as in progress.

## Snapshot Limits

Task status, assignees, DDL, directories, and contributor stock are current snapshots at report generation time, not historical reconstructions. Re-running a historical week does not restore historical state, and the report deliberately has no per-run timestamp, avoiding empty commits caused only by generation time. An open Issue with an old resolved marker is rendered as `进行中（已重新打开）`, never as completed. Bot marker edits, deletions, or markers emitted by accounts other than `github-actions[bot]` are intentionally not inferred; only the currently returned comment content is used.

Run tests with:

```sh
python3 -m unittest discover -s weekly-status -p 'test_*.py'
```

## Automation

The `Weekly Status` workflow runs on Mondays at approximately 09:00 Beijing time (`0 1 * * 1` UTC). GitHub schedules can be delayed and only run from the default branch; fork repositories may require scheduled workflows to be enabled first.

Use **Actions > Weekly Status > Run workflow** to generate a report manually. Select the target branch and optionally enter a Monday as `week_start`. GitHub requires the workflow to exist on the default branch before manual dispatch is available.

Both workflows use the `python3` already installed on `ubuntu-latest`, with standard-library modules only: no Python setup step or package installation is needed. Their only external Action is `actions/checkout`.

The workflow writes an Actions summary and commits only the generated `weekly-status/YYYY-MM-DD.md` to the selected branch; it does not upload a separate artifact. Unchanged reports do not create commits. It needs `contents: write`; branch protection must permit the bot to push. If the push is denied, the summary remains available. The workflow does not bypass branch protection or retry over concurrent branch changes.

`Weekly Status Checks` runs unit tests and checks that form options match the actual course directories on relevant pushes and pull requests. When adding or renaming a directory, update the `course_directory` dropdown in `.github/ISSUE_TEMPLATE/task_request.yml` too.
