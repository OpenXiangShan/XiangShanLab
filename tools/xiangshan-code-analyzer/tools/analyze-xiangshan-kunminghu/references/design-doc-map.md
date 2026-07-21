# XiangShan Design Doc Map

Use this file to locate intent-level documentation in `OpenXiangShan/XiangShan-Design-Doc.git`. Prefer `docs/zh` when it is more complete; use `docs/en` when present and sufficient.

## Repository

- GitHub: `https://github.com/OpenXiangShan/XiangShan-Design-Doc.git`
- MkDocs configs: `mkdocs-zh.yml`, `mkdocs-en.yml`
- Chinese docs root: `docs/zh`
- English docs root: `docs/en`

## Backend

- Overview: `docs/zh/backend/index.md`
- Decode/Rename/Dispatch/ROB: `docs/zh/backend/CtrlBlock/*.md`
- DataPath, writeback, bypass, reg-cache figure: `docs/zh/backend/DataPath/*.md`, especially `DataPath.md`, `BypassNetwork.md`, `WbDataPath.md`, and figures under `DataPath/figure/`
- Scheduler/IssueQueue/Entries: `docs/zh/backend/Schedule_And_Issue/*.md`
- ExuBlock/ExuUnit: `docs/zh/backend/ExuBlock/*.md`
- Function units: `docs/zh/backend/FunctionUnit/*.md`
- CSR/HPM/Debug: `docs/zh/backend/CSR.md`, `HPM.md`, `DebugModule/DM.md`

## Frontend

- Branch prediction: `docs/zh/frontend/BPU/*.md`
- FTQ: `docs/zh/frontend/FTQ/index.md`
- IFU and predecode: `docs/zh/frontend/IFU/*.md`
- ICache: `docs/zh/frontend/ICache/*.md`
- PC/pruned address: `docs/zh/frontend/Pruned_Address/index.md`

## Memory, DCache, MMU

- LSU overview and units: `docs/zh/memblock/LSU/*.md`
- LSQ: `docs/zh/memblock/LSU/LSQ/*.md`
- Vector LSU: `docs/zh/memblock/LSU/VLSU/*.md`
- DCache: `docs/zh/memblock/DCache/*.md`
- MMU/TLB/PTW: `docs/zh/memblock/MMU/**/*.md`
- L2 cache subsystem: `docs/zh/cache/l2cache/**/*.md`

## How to Use Docs

1. Search by module name and nearby design terms: `rg -n "ModuleName|signal|algorithm|FSM|状态|控制|数据通路" docs/zh docs/en`.
2. Extract design claims: purpose, pipeline stage, algorithm, FSM, timing diagram, and interface assumptions.
3. Cross-check every claim against active source code.
4. When a doc names a figure, use it to orient the explanation, but derive final behavior from Scala/Chisel.
5. If no doc page exists for the exact module, use the nearest parent module doc and state that no exact design-doc page was found.


## XSCache

For XSCache-specific source analysis, use `references/xscache.md` and the `OpenXiangShan/XSCache.git` repository. Design Doc cache/l2cache pages may describe CoupledL2 concepts, but effective XSCache behavior must be verified in XSCache source.
