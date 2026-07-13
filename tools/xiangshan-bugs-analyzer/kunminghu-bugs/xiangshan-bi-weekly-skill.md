# XiangShan Biweekly Skill

Use this skill for XiangShan Biweekly URL parsing, historical backfill, and
bug/fix item extraction.

## Local Data

- `biweekly-bug-notes.json`: parsed bug/fix notes from XiangShan Biweekly posts.
- `pulls.jsonl`: raw pull request records for cross-checking referenced PRs.
- `issues.jsonl`: raw issue records for cross-checking referenced issues.
- `bug-summarys/*.md`: module-oriented summaries that may need updates after a
  Biweekly refresh.

Prefer local files before network access. Use `rg` for text search and inspect
the raw JSONL entries when local metadata is available.

## XiangShan Biweekly URL Parsing

Recognize XiangShan Biweekly pages from the XiangShan documentation site:

```text
https://docs.xiangshan.cc/zh-cn/latest/blog/YYYY/MM/DD/biweekly-N-en/
https://docs.xiangshan.cc/zh-cn/latest/blog/YYYY/MM/DD/biweekly-N/
https://docs.xiangshan.cc/en/latest/blog/YYYY/MM/DD/biweekly-N-en/
https://docs.xiangshan.cc/en/latest/blog/YYYY/MM/DD/biweekly-N/
```

Use this regular expression for URL parsing:

```regex
^https://docs\.xiangshan\.cc/(?P<locale>zh-cn|en)/latest/blog/(?P<year>\d{4})/(?P<month>\d{2})/(?P<day>\d{2})/biweekly-(?P<biweekly>\d+)(?:-(?P<lang>[a-z]{2}))?/?$
```

Parsed fields:

- `source_url`: the original URL, normalized with a trailing slash.
- `date`: `YYYY-MM-DD`, assembled from the path date fields.
- `biweekly`: integer value from `biweekly-N`.
- `source_title`: `[XiangShan Biweekly N] YYYYMMDD`.
- `locale`: documentation locale from the first path segment, usually `zh-cn` or
  `en`.
- `lang`: optional post language suffix, for example `en` in
  `biweekly-106-en`; when absent, leave it unset rather than guessing.

For existing records in `biweekly-bug-notes.json`, `source_url` is the canonical
link back to the post. To find all notes from one post, filter by either
`biweekly` or exact `source_url`.

## Biweekly Refresh Cadence

Refresh the XiangShan Biweekly data every two weeks, and keep historical
coverage backfilled to the earliest available 2021 Biweekly post.

On each refresh:

1. Find the latest XiangShan Biweekly post under
   `https://docs.xiangshan.cc/zh-cn/latest/blog/`.
2. Ensure all XiangShan Biweekly posts from `2021-01-01` onward are represented
   in `biweekly-bug-notes.json`; backfill any missing older posts before adding
   the newest post.
3. Parse any Biweekly URL newer than the newest `biweekly` value already present
   in `biweekly-bug-notes.json`, plus any missing URL dated on or after
   `2021-01-01`.
4. Extract bug/fix items from each new or backfilled post using the rules below.
5. Append only new records to `biweekly-bug-notes.json`; do not duplicate
   existing records with the same `source_url` and `description`.
6. Cross-check referenced PRs and issues against `pulls.jsonl` and
   `issues.jsonl` when local metadata is available.
7. Update affected module summaries in `bug-summarys/*.md` when a new Biweekly
   item changes the module-level bug history.

If the two-week refresh date arrives but no new Biweekly page is published, keep
the local data unchanged after confirming there are no missing posts dated on or
after `2021-01-01`, and note that the latest known `biweekly` number remains
current.

## Biweekly Item Extraction

When parsing a Biweekly page, keep only entries that describe fixes, bugs,
regressions, assertions, deadlocks, difftest mismatches, exception behavior, or
other correctness issues. Ignore pure features, performance improvements, and
release process notes unless the text explicitly identifies a bug fix.

For each kept item, emit one object compatible with `biweekly-bug-notes.json`:

```json
{
  "biweekly": 106,
  "date": "2026-07-06",
  "description": "Fix the FTQ trainCache flush condition (#6147)",
  "modules": ["frontend", "cache"],
  "refs": [
    {
      "number": "6147",
      "repo": "OpenXiangShan/XiangShan",
      "text": "#6147",
      "url": "https://github.com/OpenXiangShan/XiangShan/pull/6147"
    }
  ],
  "section": "Frontend",
  "source_title": "[XiangShan Biweekly 106] 20260706",
  "source_url": "https://docs.xiangshan.cc/zh-cn/latest/blog/2026/07/06/biweekly-106-en/"
}
```

Reference normalization rules:

- `#6147` means `OpenXiangShan/XiangShan` and should link to
  `https://github.com/OpenXiangShan/XiangShan/pull/6147` when the item is from a
  fix list. Use `/issues/` only when the text clearly refers to an issue.
- `XSCache #20` means `OpenXiangShan/XSCache` and should link to
  `https://github.com/OpenXiangShan/XSCache/pull/20` when the item is from a fix
  list.
- Preserve the visible reference text in `text`.

Module inference rules:

- Prefer the Biweekly section heading, then the referenced PR/issue labels, then
  keywords in the description.
- Normalize common module names to these buckets when possible:
  `frontend`, `backend`, `mem`, `cache`, `chiselAIA`, `chiselIOPMP`,
  `difftest`, `tool`, `top`, `uncategorized`.
- A single item may have multiple modules, for example frontend fixes involving
  ICache can use `["frontend", "cache"]`.

## Answering Questions

When answering Biweekly-sourced bug-history questions:

1. Search `biweekly-bug-notes.json` for the Biweekly source context.
2. Cross-check referenced PRs in `pulls.jsonl` and issues in `issues.jsonl`.
3. Cite the Biweekly `source_url` and the GitHub PR/issue URLs when available.
4. State whether the conclusion comes directly from the Biweekly item or from
   cross-checking local GitHub metadata.
