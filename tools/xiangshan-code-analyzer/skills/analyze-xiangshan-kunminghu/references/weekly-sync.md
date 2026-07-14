# Weekly Repository Sync

Use this file before analyzing XiangShan when the skill has not synchronized local documentation/course inputs for 7 days. XiangShan source code is not taken from local sync by default; obtain it directly from `https://github.com/OpenXiangShan/XiangShan.git`.

## Purpose

Keep the local documentation and course inputs fresh. XiangShan source analysis should use `https://github.com/OpenXiangShan/XiangShan.git` directly.

- XiangShan Design Doc checkout when present, normally `XiangShan-Design-Doc` relative to `xiangshanlab_home`
- XiangShanLab course repository at `xiangshanlab_home`
- Generated course-analysis content under `xiangshan-course/docs/课程体系4：实现篇-香山高性能处理器微架构优化/中级-基于代码进行分析/` relative to `xiangshanlab_home`
- Generated code deep-dive output under `xiangshan-course/docs/课程体系4：实现篇-香山高性能处理器微架构优化/中级-高性能香山处理器代码深入解析/` relative to `xiangshanlab_home`

## Required Sync Check

At the start of a module analysis, run the helper unless the user explicitly asks not to sync:

```bash
export xiangshanlab_home=/path/to/XiangShanLab
skills/analyze-xiangshan-kunminghu/scripts/weekly_sync.py
```

The helper exits without network work when the last sync is less than 7 days old. It does not define the authoritative XiangShan source revision; the analysis source revision comes from `https://github.com/OpenXiangShan/XiangShan.git`. Use `--force` only when the user explicitly requests an immediate refresh.

## Safety Rules

- Never run `git reset`, `git clean`, or destructive checkout as part of sync.
- `git fetch --all --prune` is allowed for configured repositories.
- `git pull --ff-only` is allowed only when the target repository worktree is clean.
- If a repository is dirty, fetch remote refs but skip pull and report the dirty state.
- Missing repositories are reported, not cloned automatically, unless the user explicitly requests cloning and approves network access.
- Course analysis directories are treated as generated/user-editable content. Record `git status --short` for them; do not overwrite or delete files during sync.

## Configuration

Default repositories checked by the helper:

- `xiangshanlab_home`
- `XiangShan-Design-Doc` relative to `xiangshanlab_home`

Add additional repositories with `XIANGSHAN_SYNC_REPOS`, separated by `:` on Linux, or with `--extra-repos`.

The default state file is:

`~/.cache/codex/analyze-xiangshan-kunminghu-weekly-sync.json`

## Interpreting Results

- `skip`: a sync happened less than 7 days ago.
- `fetched-only`: remote refs were updated and pull was disabled.
- `pulled-ff-only`: the clean worktree fast-forwarded successfully.
- `fetched-pull-skipped-dirty`: remote refs were updated, but local changes prevented pull.
- `missing` or `not-git`: path is unavailable as a git checkout; continue with available docs/course files and state this in the analysis scope. Do not treat local XiangShan source as authoritative unless explicitly requested by the user.
