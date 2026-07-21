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
- For Frontend documents, use English filename stems. Use `Frontend-IFU-and-Predecode-Deep-Dive.md` and `Frontend-Overview-and-End-to-End-Signal-Analysis.md` instead of Chinese filename stems.

## Save Procedure

After producing the Markdown content, write it with:

```bash
export xiangshanlab_home=/path/to/XiangShanLab
tools/analyze-xiangshan-kunminghu/scripts/save_analysis.py --module <ModuleName> --input <markdown-file>
```

The helper creates the output directory if needed, writes `<ModuleName>.md` by default, and refuses to overwrite an existing file unless `--overwrite` is passed.

## Safety Rules

- Do not delete, rename, or overwrite existing course files unless the user explicitly requests replacement.
- When the user explicitly requests English Frontend names, rename the corresponding Chinese-stem files and update references/indexes that point to them.
- If a target file already exists and the user did not request overwrite, save to a timestamped filename or ask for confirmation when replacement is required.
- In the final response, include the saved absolute path.
- If saving fails because the destination is outside the writable workspace, request approval for the write rather than silently returning only inline Markdown.

## Tutorial Post-Processing

When the user asks for a course/tutorial document or asks to use the `Mem-MDP.md` directory style:

1. Generate and source-check the Markdown first; do not use tutorial formatting to hide missing line evidence.
2. Keep the numbered chapter structure and all required sections from the skill.
3. Run:

```bash
tools/analyze-xiangshan-kunminghu/scripts/tutorialize_frontend_docs.py \
  /path/to/中级-高性能香山处理器代码深入解析
```

4. The script removes intermediate `原章节` labels and empty generator placeholders while retaining technical content, source links, Design Doc traceability, cross-boundary analysis, diagrams, WaveDrom, scenarios, and verification tables.
5. Validate Markdown fences and WaveDrom JSON again after post-processing.
