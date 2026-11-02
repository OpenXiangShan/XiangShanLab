# 第 4 章 版本基线索引

本文件是本章的版本索引，规则见[课程根目录 README](../../README.md#版本基线)。**本章不整体绑定某一代**：昆明湖 V2 与 V3 目前都是活跃分支，V2 侧文档按 V2 结论使用，V3 侧文档按 V3 结论使用，两者不得互相套用。

## 1. 实际使用的基线

| 基线 | 分支 | commit | 采集时间 | 覆盖文档 |
| --- | --- | --- | --- | --- |
| 昆明湖 V2 | `kunminghu-v2` | `52262f303fc06daf84cdab7011d59b7df65ce7e8` | 2026-07-10 | 前端 15 篇 |
| 昆明湖 V2 | `kunminghu-v2` | `e12436c7cba86b195deec24981976d78bc263661` | 2026-08-14 | 缓存 17 篇、访存 15 篇 |
| 昆明湖 V3 | `kunminghu-v3` | `055d8ad9e56b0b618f2d549a97f3a028986b4849` | 2026-07-14 | MDP / SSIT / LFST / foldPC / PageTableCache |
| 昆明湖 V3 | `kunminghu-v3` | 未固定（引用分支 HEAD） | — | `Decode-Fusion.md` |
| 昆明湖 V3 | `kunminghu-v3` | 未固定（波形 `2026-07-21-10-21-43.fst`） | 2026-07-21 | `mdp-wave-analysis.md` |
| Design Doc V3 | `XiangShan-Design-Doc` `kunminghu-v3` | `f8e258dc2d9c02c0616764856e1d18feedb91b81` | 2026-05-07 | 前端 13 篇的“设计意图”引用 |

> 两个仓库的**默认分支都是 `kunminghu-v3`**，但 `kunminghu-v2` 分支至今仍在更新（截至 2026-10-29，两分支 tip 同日刷新）。所以“默认分支 = V3”不能作为“文档应基于 V3”的依据，只能作为“引用设计文档时容易拿到 V3”的解释。
>
> Design Doc 另有 `kunminghu-v2` 分支（`58d9e2ad`，2026-07-12）。前端文档若要引用同代设计文档，应改用该分支。

## 2. 逐类登记

### 2.1 前端（V2）

| 文档 | 基线 | 证据类型 | 状态 |
| --- | --- | --- | --- |
| `frontend/Frontend-*.md`（15 篇） | V2 `52262f3` | 源码 + 源码追溯矩阵 | V2 基线；第 18 节引用的 Design Doc 是 V3 快照，已在文首注明 |

### 2.2 缓存（V2）

| 文档 | 基线 | 证据类型 | 状态 |
| --- | --- | --- | --- |
| `cache/Cache-*.md`（17 篇） | V2 `e12436c7`（CoupledL2 `fb546983`） | 源码 | V2 基线 |

### 2.3 访存（V2 / V3 混编）

| 文档 | 基线 | 证据类型 | 状态 |
| --- | --- | --- | --- |
| `memory/LoadStore-{DCache,ExceptionBuffer,LoadPipe,LoadQueue,LoadQueueRAR,LoadQueueRAW,LoadQueueReplay,MemBlockPrefetcher,MissalignBuffer,MMU,StoreBuffer,StorePipe,StoreQueue,TLB,UncacheBuffer}.md`（15 篇） | V2 `e12436c7` | 源码 | V2 基线 |
| `memory/LoadStore-MDP.md`、`memory/Memory_{MDP,SSIT,LFST}.md` | V3 `055d8ad9` | 源码 | V3 基线 |
| `memory/mdp-ref.md` | V3 `055d8ad9` | 源码 | V3 基线；第 4.6 节验证矩阵仍是 V2 行号，只作检查项清单使用 |
| `mdp-wave-analysis.md` | V3（波形 2026-07-21） | 源码 + 波形 | V3 基线，commit 未固定 |

### 2.4 后端

| 文档 | 基线 | 证据类型 | 状态 |
| --- | --- | --- | --- |
| `backend/{Decode,Decode-vector,Dispatch,RegCache}.md` | V2 | 源码 | V2 基线 |
| `backend/Decode-Fusion.md` | V3（分支 HEAD） | 源码 | V3 基线，commit 未固定 |
| `backend/{FloatAdder,Rename,Move-elimination}.md` | **未声明** | 源码 | 待补基线 |

### 2.5 foldPC / PageTableCache（V3）

| 文档 | 基线 | 证据类型 | 状态 |
| --- | --- | --- | --- |
| `{foldPC-gen,mdp-foldPC-gen,page-table-cache}.md` | V3 `055d8ad9` | 源码 | V3 基线 |

### 2.6 设计文档章节

| 文档 | 基线 | 证据类型 | 状态 |
| --- | --- | --- | --- |
| `2-xiangshan-design-document/1_Classic_Stage_Cuttings_...md` | 源码链接：昆明湖 V2；流水级描述：雁栖湖 / 南湖代 | 设计说明 + 源码导航 | 链接已从 `kunminghu-v3` 改到 `kunminghu-v2`（`bpu/BPU.scala`→`BPU.scala`、`dispatch/Dispatch.scala`→`NewDispatch.scala`、`rob/ROB.scala`→`Rob.scala`，均已核对存在于 V2）；流水级描述保留原代表述 |
| `2-xiangshan-design-document/{2_The_states_...,4_Register_renaming}.md` | 南湖（理论正文） | 设计说明 | 已加版本提示 |
| `2-xiangshan-design-document/14_LoadStore.md` | V2 | 源码 | V2 基线 |
| `2-xiangshan-design-document/15_MDP.md` | V3 | 设计说明 | V3 基线 |
| `2-xiangshan-design-document/kunminghu-instruction-latency-throughput.md` | V2 `52262f3` | 源码参数 | V2 基线 |
| `2-xiangshan-design-document/{5,6,7,8,9,10,11,12,13}_*.md`（9 篇） | **未声明** | 设计说明 | 待补基线 |

### 2.7 超标量基础理论

| 文档 | 基线 | 证据类型 | 状态 |
| --- | --- | --- | --- |
| `1-superscalar-basic-knowledge/{5_Single_Issue_vs_Multi_Issue,7_Tomosulo_vs_ScoreBoard}.md` | 南湖（历史参数） | 理论 + 历史参数 | 已修正“4 发射”为 6 发射，并加版本提示 |
| `1-superscalar-basic-knowledge/{2,3,4,6}_*.md` | 不绑定代 | 纯理论 | 无版本断言 |

## 3. 已知缺口与代价

1. **行号漂移**：`Decode-Fusion.md`、`mdp-wave-analysis.md` 只声明分支不声明 commit，行号随分支更新漂移。补 commit 只是 `git log -1`，不需要编译。
2. **待补基线的 12 篇**（后端 3 + 设计文档 9）：需要逐篇读源码判断属于哪一代，不需要编译。
3. **若要整体迁到 V3**：本章结论以源码证据为主，唯一含周期级证据的 `mdp-wave-analysis.md` 已是 V3，因此**不需要重建 emu、不需要重采波形**，只需按 V3 源码重新核对模块边界、参数与行号。
