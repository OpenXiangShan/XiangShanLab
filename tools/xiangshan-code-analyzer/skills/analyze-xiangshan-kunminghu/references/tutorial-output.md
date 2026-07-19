# Tutorial Markdown Output

Use this reference when the user asks for course-ready Markdown, tutorial restructuring, or a `Mem-MDP.md`-style directory.

## Required Shape

Keep the numbered chapter layout:

1. Scope
2. Key source evidence
3. Theory-to-code mapping
4. Theory and effective implementation
5. Parameters
6. Module boundaries and interfaces
7. Why the module exists
8. Dynamic path
9. Index/address/history calculation
10. Core algorithm
11. State and storage
12. Pipeline stages
13. Control path rationale
14. Data path and cross-boundary behavior
15. Exceptions/debug/privilege
16. CSR control
17. Diagrams
18. Design Doc/source discrepancies
19. Dynamic scenarios
20. Conclusion

## Title Numbering Constraint

Every heading inside generated tutorial Markdown must use explicit numeric prefixes and remain sequential within its nesting level. Use these forms:

- `## 1. ...` for top-level chapters
- `### 1.1. ...` for second-level sections
- `#### 1.1.1. ...` for third-level sections

Do not leave unnumbered section headings in tutorial-facing output. If a section is inserted or removed, renumber the whole document so chapter order stays monotonic and parent/child relationships stay consistent.

Keep `验证特别注意` after the conclusion. For Frontend/full-chain documents, keep both `Top-Level Module Connectivity` and `Frontend/Backend Pipeline Stages` Mermaid diagrams.

## Tutorial Rules

- Explain each module with `who`, `why`, `how`, `from what`, and `to what`.
- Teach from concrete source evidence; every behavior-changing claim keeps file/line links.
- In `16. CSR control`, include the frontend branch predictor enable path: `sbpctl` CSR fields, `CustomCSRCtrlIO.bp_ctrl`, Backend `frontendCsrCtrl`, Frontend `bpu.io.ctrl`, and BPU sub-predictor `io.enable` for uBTB, aBTB, mBTB, TAGE, SC, ITTAGE, and RAS. State fixed-enabled predictors and Constantin override behavior when present.
- Keep speculative, normal, blocked, redirect, flush, replay, exception, and commit paths distinct.
- Explain queue/table/stack/FIFO/MSHR/uncache occupancy, empty/full behavior, port conflicts, and backpressure.
- Keep virtual-page, cache-line, and MMIO/uncache cases in the cross-boundary section.
- Keep Design Doc baseline, source baseline, traceability matrix, line-by-line mapping, and discrepancy status.
- Remove intermediate-generation labels and empty placeholders. Do not remove technical evidence merely because its original section title is removed.

## Post-Processor

After generating the checked Markdown set, run:

```bash
tools/analyze-xiangshan-kunminghu/scripts/tutorialize_frontend_docs.py <frontend-doc-directory>
```

The script is idempotent for the generated tutorial marker, renumbers headings to the required `1.` / `1.1.` / `1.1.1.` hierarchy, and is intended for the 13 English-stem Frontend documents. Re-run structural, fence, WaveDrom, and `git diff --check` validation after it finishes.
