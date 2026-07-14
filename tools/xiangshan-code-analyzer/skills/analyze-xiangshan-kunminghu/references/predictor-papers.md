# Predictor Paper Search and Algorithm Principles

Use this file whenever analyzing frontend predictors or predictor-like structures, including `Bim`, `FTB`, `FauFTB`, `Tage`, `ITTAGE`, `SC`, `RAS`, `newRAS`, history predictors, target predictors, indirect predictors, statistical correctors, and predictor integration in `BPU`.

## Paper Search Requirement

Use the `paper-search-agent-mcp` tool before explaining predictor algorithm principles. Prefer the MCP namespace `mcp__paper_search_agent`:

- Start with `search_papers` for broad discovery.
- Use predictor-specific queries, for example:
  - `TAGE branch predictor Seznec Michaud`
  - `ITTAGE indirect branch predictor`
  - `statistical corrector branch predictor`
  - `branch target buffer FTB branch prediction`
  - `return address stack branch prediction`
  - `bimodal branch predictor two-bit counter`
- If a DOI is available and deeper context is needed, use `resolve_and_plan`, then `fetch_fulltext`, then `get_paper_sections` for `abstract`, `introduction`, `algorithm`, `design`, `evaluation`, or `conclusion` sections when available.
- If full text is unavailable, use title/abstract/metadata and state the source limitation.

## Source Discipline

- Cite the searched paper title and stable identifier when available: DOI, venue/year, or URL returned by the MCP result.
- Separate paper algorithm from XiangShan implementation. The paper explains the principle; the source code proves the implemented behavior.
- If multiple papers describe related predictors, choose the most directly relevant paper and mention important differences.
- Do not claim XiangShan exactly implements a paper unless code structure, parameters, and update rules support that claim.

## Predictor Principle Explanation

For each predictor, include:

| Predictor | Paper/source | Core principle | Main state | XiangShan source lines | XiangShan core code | Lookup algorithm | Update/training algorithm | Recovery behavior | XiangShan code mapping | Differences/uncertainty |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

Explain in prose:
- Why the predictor exists: what branch behavior or frontend bottleneck it targets.
- The paper algorithm at a conceptual level: indexing, tags, counters/confidence, history folding, target storage, stack behavior, chooser/corrector behavior, allocation, useful bits, aging, or replacement as relevant.
- The XiangShan implementation: concrete tables, registers, bundles, parameters, lookup stages, update ports, commit/redirect training path, snapshots, and recovery signals, with exact Chisel source line numbers and short core code snippets from the analyzed commit.
- A scenario example: for example cold miss, aliasing, long-history branch, indirect target change, return prediction, misprediction redirect, useful-bit replacement, or statistical correction overriding a base prediction.

## Common Predictor Mapping Hints

- `Bim`: explain bimodal/two-bit saturating counter principle, index aliasing, read/update timing, and how it contributes to ensemble prediction.
- `Tage`: explain tagged geometric history principle, folded history/index/tag generation, longest matching provider, alternate prediction, useful bits, allocation on misprediction, and counter update.
- `ITTAGE`: explain indirect target prediction with tagged history, target storage, provider selection, allocation/update, and redirect training.
- `SC`: explain statistical correction as a second-stage correction over a base predictor, confidence/counter summation if present, threshold/update behavior, and when it overrides or confirms base prediction.
- `FTB`/`FauFTB`: explain target-block/branch-target storage, tag/set lookup, branch slot metadata, fall-through/target selection, update on resolved branches, and replacement.
- `RAS`/`newRAS`: explain call push, return pop, speculative update, snapshot/restore, overflow/underflow, and redirect recovery.
- `BPU`: explain predictor ensemble integration, priority/composition, fetch-stage timing, FTQ metadata, backend update path, and redirect recovery.
