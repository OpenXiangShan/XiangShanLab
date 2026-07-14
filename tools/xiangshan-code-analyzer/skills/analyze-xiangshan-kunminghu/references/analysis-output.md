# Analysis Output Saving

Use this file whenever the skill generates a module code-analysis Markdown document.

## Default Destination

Save generated module code-analysis Markdown files under:

`xiangshan-course/docs/课程体系4：实现篇-香山高性能处理器微架构优化/中级-高性能香山处理器代码深入解析/` relative to `xiangshanlab_home`

This directory is for code-deep-dive analysis output. The older directory `中级-基于代码进行分析/` is not the default destination for newly generated module analysis unless the user explicitly requests it.

## File Naming

- Use a concise module name as the file stem, for example `RegCache.md`, `IssueQueue.md`, `LoadPipe.md`, or `Tage.md`.
- If the request targets a path with multiple similarly named modules, prefix enough context to avoid ambiguity, for example `DCache-LoadPipe.md` or `XSCache-CoupledL2-MSHR.md`.
- Preserve the user's requested filename when they explicitly provide one.

## Save Procedure

After producing the Markdown content, write it with:

```bash
export xiangshanlab_home=/path/to/XiangShanLab
skills/analyze-xiangshan-kunminghu/scripts/save_analysis.py --module <ModuleName> --input <markdown-file>
```

The helper creates the output directory if needed, writes `<ModuleName>.md` by default, and refuses to overwrite an existing file unless `--overwrite` is passed.

## Safety Rules

- Do not delete, rename, or overwrite existing course files unless the user explicitly requests replacement.
- If a target file already exists and the user did not request overwrite, save to a timestamped filename or ask for confirmation when replacement is required.
- In the final response, include the saved absolute path.
- If saving fails because the destination is outside the writable workspace, request approval for the write rather than silently returning only inline Markdown.
