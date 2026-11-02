# 第 5 章 版本基线索引

本文件是本章的版本索引，规则见[课程根目录 README](../../README.md#版本基线)。

## 1. 本章当前状态

本章**尚未迁移到昆明湖 V3**。本轮只做基线标注：说清每篇文档的结论属于哪一代、证据来自哪次运行，避免读者把 V2 的周期号当成 V3 的事实。内容迁移见第 5 节。

## 2. 实际使用的运行环境

| 环境 | 分支 / commit | 说明 |
| --- | --- | --- |
| `/nfs/home/wanghao/emuByYuan/stable-kmh-v2` | `kunminghu-v2 @ abd0f867a`（2026-05-20） | 本章事实上的公共 emu，构建于 2026-06-22；全文检索到 **407 处**引用 |
| `/nfs/home/yanyusong/cache-conflict-env` | `kunminghu-v2 @ 485333f4d` | Cache bank conflict 场景，目录内含 2 个 FST（184 MB / 164 MB） |
| `/nfs/home/yanyusong/cbo-env` | 昆明湖 V2 | CBO Zero 场景 |
| `~/cbo-kmhv2` | 昆明湖 V2 | CBO / Prefetch / Fence 场景；**该目录已不存在** |
| `~/mdp-kmhv3` | `kunminghu-v3 @ 055d8ad9` | Mem-MDP 场景；**该目录已不存在** |

> 现状是“谁的环境能跑出波形就用谁”，本章没有独立的公共环境。34 篇文档引用了其他同学的 `/nfs/home/...` 绝对路径，其中 3 个目录已失效（`wanghao/xs-env`、`~/cbo-kmhv2`、`~/mdp-kmhv3`）。

## 3. 三级分层

| 层级 | 判定 | 篇数 | 处理方式 |
| --- | --- | --- | --- |
| T1 周期级 | 含密集周期号（`C\d+` / “第 N 拍”） | **10** | 迁 V3 时必须重跑、重采波形、重算周期号 |
| T2 有证据但无密集周期号 | 引用了波形、程序运行结果或少量周期号 | **35** | 迁 V3 时按需重采或复跑确认 |
| T3 纯源码推理 | 只引用源码与行号 | **50** | 只需按 V3 源码复核，不跑仿真 |

### 3.1 T1 周期级（10 篇，全部为 V2）

| 文档 | 基线 | 证据 |
| --- | --- | --- |
| `scenarios-analysis/conflict/cache-conflict/experiment.md` | V2 | 程序 `cache_conflict.S` + `main.c`；FST `2026-08-27-11-19-12.fst`；命令与输入 SHA-256 齐全 |
| `scenarios-analysis/cbo-zero/cbo-zero-scenario.md` | V2 | 程序 `cbo-zero-demo.c`；FST `2026-08-25-13-36-09.fst`（仓库内，46 MB） |
| `scenarios-analysis/cbo-inval/WAVEFORM_ANALYSIS.md` | V2 | 程序 `cbo-inval-scenario.c`；5 个 FST（`2026-07-31-*`，含 `waveform.zip`） |
| `scenarios-analysis/exception/trap-execution-process.md` | V2 | 程序 `trap.S`；68 个周期号，本章最重的一篇 |
| `instructions-lifecycle/cbo/cbo-clean.md` | V2 | FST `2026-07-30-12-11-26.fst` |
| `instructions-lifecycle/cbo/cbo-inval.md` | V2 | 程序 `cbo_inval.c`；FST `2026-07-30-12-01-06.fst` |
| `instructions-lifecycle/cbo/cbo-zero.md` | V2 | 程序 `cbo_zero.c`；FST `2026-07-30-11-31-16.fst` |
| `instructions-lifecycle/prefetch/prefetch_i.md` | V2 | 程序 `prefetch_i.c`；FST `2026-07-30-16-25-24.fst` |
| `instructions-lifecycle/prefetch/prefetch_r.md` | V2 | 程序 `prefetch_r.c`；FST `2026-07-30-16-39-21.fst` |
| `instructions-lifecycle/prefetch/prefetch_w.md` | V2 | 程序 `prefetch_w.c`；FST `2026-07-30-17-23-02.fst` |

### 3.2 T2（35 篇：19 篇 V2、1 篇 V3、15 篇未声明）

- **V2（19 篇）**：`instructions-lifecycle/` 下的 CBO、Fence、Prefetch、AMO、标量/浮点指令生命周期，以及 `scenarios-analysis/` 下的 prefetch、redirect、exception 源码摘录、cache-conflict 原理篇。判定依据是文中引用了 `stable-kmh-v2` / `emuByYuan` / `kunminghu-v2` / `KunminghuV2Config`。
- **V3（1 篇）**：`scenarios-analysis/memory-dependency-predictor/Mem-MDP.md`，基线 `kunminghu-v3 @ 055d8ad9`。
- **未声明（15 篇）**：多为只讲机制、未固定运行环境的篇目（`scalar-load`、`scalar-add`、`jal`、`branch`、`scalar-store`、`backend-mechanisms/*`、`branch-predictors/*` 等）。这些文档**未标注基线，不能当作 V3 事实**。

### 3.3 T3（50 篇）

场景描述、需求说明、索引类文档，未做版本绑定；若正文引用具体信号或行号，引用时须补基线。

## 4. 工具与仓库内资产

| 项 | 位置 | 状态 |
| --- | --- | --- |
| wavekit | `/nfs/home/sunyuhang/tools/wavekit`（v0.8.2，`.venv` 已装） | 可用；已实测 `FstReader` 能打开仓库内 `cbo-zero/2026-08-25-13-36-09.fst` |
| 波形（仓库内） | `scenarios-analysis/cbo-zero/2026-08-25-13-36-09.fst`、`prefetch/prefetch-load-priority/wave.fst`、`cbo-inval/waveform.zip`、`attachments/8Y4Nert_doKhtzEK/add_inst.zip`（VCD） | 均为 V2 证据 |
| 测试程序源码 | 12 个（3 个在 zip 内）：`cbo-zero`、`cbo-inval`、`cache-conflict`、`prefetch-load-priority`、`unaligned-mmio-addr`、`learn-redirect`、`branch`、`branch-predictors/stage{1,2}`、`float-point-round-mode` | 均为 V2 期程序 |

## 5. 迁移到 V3 的代价（不在本轮范围）

1. **必须先带波形重建 emu**。现有 `~/kmh/XiangShan` 的 V3 emu（`7599a0976`）功能正常（`flash_recursion_test` 跑到 GOOD TRAP，8641 拍，exit 0），但 `nm` 查不到 `traceInit` / `traceChg`、没有 `VerilatedFstC` / `VerilatedVcdC`，`--dump-wave-full` 直接 SIGABRT。需要 `make emu EMU_TRACE=fst` 重建（建议用独立 worktree，别覆盖现有 emu）。
2. **T1 十篇**全部要重跑、重采波形、重算周期号，不能只改版本号。
3. **T2 三十五篇**按需重采或复跑确认。
4. **T3 五十篇**只做 V3 源码复核，不跑仿真。

## 6. 本轮改动边界

- 只新增本索引，并在 T1 / T2 共 45 篇文首补基线声明；**未改动任何结论、周期号或运行命令**。
- 第 5 章的内容迁移、波形重采、程序重编均未执行。
