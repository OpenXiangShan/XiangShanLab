<!-- # 香山昆明湖 V2 LoadStore-MissalignBuffer 源码分析 -->
# XiangShan Kunminghu V2 LoadStore-MissalignBuffer Source-Code Analysis

<!-- > 用户请求使用 MissalignBuffer；当前源码中的类、文件和信号拼作 MisalignBuffer。本文保留源码拼写，以免检索时漏掉 LoadMisalignBuffer 和 StoreMisalignBuffer。 -->
> The requested name is MissalignBuffer, but the current source spells the classes, files, and signals as MisalignBuffer. This document retains the source spelling so searches do not miss LoadMisalignBuffer and StoreMisalignBuffer.

<!-- ## 1. 范围、版本与证据 -->
## 1. Scope, Version, and Evidence

<!-- | 项目 | 本次基线 |
| --- | --- |
| 分支与源码路径 | kunminghu-v2，/home/yanyusong/xs-memory-env/XiangShan |
| 有效源码提交 | e12436c7cba86b195deec24981976d78bc263661 |
| 课程仓库提交 | 680010a3cf7cc72900345600b99709bc337a52bf |
| 独立 Design Doc 基线 | 未查阅；本机不存在 /home/yanyusong/XiangShan-Design-Doc，课程内的 design-document 目录也没有 LSU/Misalign 文档。 |
| 周同步 | 已按 skill 执行 weekly_sync.py；输出为 skip: last sync 2.85 days ago < 7 days，因此没有 fetch 或 pull。 |
| 分析对象 | LoadMisalignBuffer 和 StoreMisalignBuffer。MemBlock 将它们都作为单例实例化，故 LoadStore 文件名应覆盖两条路径。 |
| 覆盖范围 | 标量/向量 load/store、16B 分片、Store 的 4KB 跨页闭环、TLB/PMP/DCache、replay、redirect、异常、Difftest、虚拟页/缓存行/MMIO 边界。 | -->
| Item | Baseline for this analysis |
| --- | --- |
| Branch and source path | `kunminghu-v2`, `/home/yanyusong/xs-memory-env/XiangShan` |
| Effective source commit | `e12436c7cba86b195deec24981976d78bc263661` |
| Course repository commit | `680010a3cf7cc72900345600b99709bc337a52bf` |
| Independent Design Doc baseline | Not consulted; `/home/yanyusong/XiangShan-Design-Doc` is absent on this machine, and the course's `design-document` directory has no LSU/Misalign document. |
| Weekly synchronization | `weekly_sync.py` was run according to the skill; it reported `skip: last sync 2.85 days ago < 7 days`, so no fetch or pull occurred. |
| Analysis target | LoadMisalignBuffer and StoreMisalignBuffer. MemBlock instantiates both as singletons, so the LoadStore file name covers both paths. |
| Coverage | Scalar/vector load/store, 16B splitting, the Store 4KB-crossing closure, TLB/PMP/DCache, replay, redirect, exceptions, Difftest, and virtual-page/cache-line/MMIO boundaries. |

<!-- 本次只读检查源码。源工作树原本已有与本分析无关的 difftest 修改和 aia/ 未跟踪内容；课程仓库也已有未跟踪文档。它们均未被修改。 -->
This analysis inspected source code read-only. The source worktree already contained unrelated difftest changes and untracked `aia/` content; the course repository also already contained untracked documents. None were modified.

<!-- ### 1.1 关键证据索引 -->
### 1.1 Key Evidence Index

<!-- | 主题 | 当前提交中的有效源码 | 核心代码 | 证明内容 |
| --- | --- | --- | --- |
| 顶层实例化与专用 port | [MemBlock.scala:55](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:55)、[MemBlock.scala:435](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:435) | MisalignWBPort = 1 | 两个 buffer 均是 MemBlock 内单例；Load fragment 固定复用 LDU port 1。 |
| Load 单槽与固定仲裁 | [LoadMisalignBuffer.scala:143](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:143) | loadMisalignFull := req_valid | Load 不是 FIFO；只有一个占用位，多个 LDU 同拍候选时按低 port 编号优先。 |
| Store 单槽与 oldest 仲裁 | [StoreMisalignBuffer.scala:71](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:71)、[StoreMisalignBuffer.scala:146](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:146) | selectOldest | Store 也只有一个请求槽，但同拍候选按 ROB 年龄和 uop 序号择老。 |
| Load 分片与合并 | [LoadMisalignBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:292)、[LoadMisalignBuffer.scala:510](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:510)、[LoadMisalignBuffer.scala:540](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:540) | maxSplitNum = 2 | 只生成两个对齐子 load，顺序发射、收集，再按原始类型拼接。 |
| Store 跨页闭环 | [StoreMisalignBuffer.scala:223](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:223)、[StoreQueue.scala:1187](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1187) | s_block / doDeq | 跨 4KB 页的 store 在写回后仍保留 buffer，直到 StoreQueue 的 DataBuffer 入队确认。 |
| 异常覆盖未启用 | [LoadMisalignBuffer.scala:625](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:625)、[StoreMisalignBuffer.scala:660](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:660) | overwriteExpBuf.valid := false.B | 两模块都计算候选 payload，但当前提交把 valid 强制为 false。 | -->
| Topic | Effective source in this commit | Key code | What it establishes |
| --- | --- | --- | --- |
| Top-level instantiation and dedicated port | [MemBlock.scala:55](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:55), [MemBlock.scala:435](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:435) | `MisalignWBPort = 1` | Both buffers are singletons in MemBlock; load fragments always reuse LDU port 1. |
| Load single slot and fixed arbitration | [LoadMisalignBuffer.scala:143](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:143) | `loadMisalignFull := req_valid` | Load is not a FIFO: it has one occupancy bit, and same-cycle LDU candidates use lower port-number priority. |
| Store single slot and oldest arbitration | [StoreMisalignBuffer.scala:71](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:71), [StoreMisalignBuffer.scala:146](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:146) | `selectOldest` | Store also has only one request slot, but chooses same-cycle candidates by ROB age and uop sequence number. |
| Load splitting and merging | [LoadMisalignBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:292), [LoadMisalignBuffer.scala:510](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:510), [LoadMisalignBuffer.scala:540](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:540) | `maxSplitNum = 2` | It creates only two aligned child loads, issues and collects them in order, then merges them by original type. |
| Store cross-page closure | [StoreMisalignBuffer.scala:223](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:223), [StoreQueue.scala:1187](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1187) | `s_block / doDeq` | A store that crosses a 4KB page keeps the buffer after writeback until StoreQueue confirms DataBuffer enqueue. |
| Exception override disabled | [LoadMisalignBuffer.scala:625](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:625), [StoreMisalignBuffer.scala:660](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:660) | `overwriteExpBuf.valid := false.B` | Both modules compute candidate payloads, but the current commit forces `valid` low. |

<!-- ### 1.2 理论、课程意图和有效代码 -->
### 1.2 Theory, Course Intent, and Effective Code

<!-- 课程资料 [14_LoadStore.md:3](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md:3) 声明它参考的是旧检出 3fdbebedf6d505dedfdd66f8d8154c82136963a6，而不是本次 e12436c7。其 [14_LoadStore.md:222](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md:222) 和 [14_LoadStore.md:1235](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md:1235) 仅用作术语和学习路径，不能作为本提交的行为证据。 -->
Course material [14_LoadStore.md:3](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md:3) states that it references the older checkout `3fdbebedf6d505dedfdd66f8d8154c82136963a6`, not this analysis's `e12436c7`. Its [14_LoadStore.md:222](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md:222) and [14_LoadStore.md:1235](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md:1235) serve only as terminology and a learning path, not behavioral evidence for this commit.

<!-- | 层次 | 结论 | 证据边界 |
| --- | --- | --- |
| 理论 | 跨对齐边界的一次访存可拆成两个更小、对齐的访问，待它们的结果确定后再向原指令交付数据或异常。 | 课程第 14 章的概念背景。 |
| 设计意图 | 用独立的小缓冲承接少见且控制复杂的请求，复用常规 LoadUnit/StoreUnit、TLB 和 DCache，避免扩张通常的高速路径。 | 课程结构和 MemBlock 实例化支持该意图；没有同提交独立 Design Doc。 |
| 有效代码 | 每类 buffer 均只有一个请求槽和至多两个 fragment；Load 复用 LDU1，Store 复用 STA0；二者仲裁、状态、异常和完成规则不同。 | 下文 Scala/Chisel 链路是唯一行为依据。 | -->
| Level | Conclusion | Evidence boundary |
| --- | --- | --- |
| Theory | A memory access crossing an alignment boundary can be divided into two smaller aligned accesses, whose results are resolved before data or an exception is delivered to the original instruction. | Conceptual background from course Chapter 14. |
| Design intent | A separate small buffer absorbs rare, control-intensive requests, reusing ordinary LoadUnit/StoreUnit, TLB, and DCache paths without expanding the usual fast path. | Course structure and MemBlock instantiation support this intent; no independent Design Doc at the same commit exists. |
| Effective code | Each buffer class has one request slot and at most two fragments; Load reuses LDU1, Store reuses STA0, and their arbitration, state, exceptions, and completion rules differ. | The Scala/Chisel paths below are the only behavioral evidence. |

<!-- 课程资料的一个覆盖缺口是：旧文档的 Store 说明错误地指向非对齐 Load 小节，[14_LoadStore.md:3473](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md:3473)。所以不能套用旧 Load 图解释 Store；后文单独追踪其 cross-4KB 和 s_block。 -->
One coverage gap in the course material is that its older Store discussion incorrectly points to the misaligned Load section, [14_LoadStore.md:3473](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md:3473). The older Load diagram therefore cannot explain Store; the following analysis tracks its cross-4KB behavior and `s_block` separately.

<!-- ## 2. 模块契约：Who / Why / How / From / To -->
## 2. Module Contract: Who / Why / How / From / To

<!-- | 模块 | Who | Why | How | From | To |
| --- | --- | --- | --- | --- | --- |
| LoadMisalignBuffer | MemBlock 所有；LDU0..2 生产 enq，LDU1 消费 fragment 并回传响应。 | 将跨 16B 的标量/向量 load 从常规发射路径移出，串行管理两个子访问、replay 和结果合并。 | 一个寄存器请求槽、两个 fragment/response 槽、curPtr、unSentLoads 和六态 FSM。 | LoadUnit.io.misalign_enq 的 LqWriteBundle。 | LDU1 的 misalign_ldin；最终标量写回或向量 MergeBuffer；redirect 时取消。 |
| StoreMisalignBuffer | MemBlock 所有；STA0..1 生产 enq，STA0 执行 fragment；StoreQueue 参与跨页释放。 | 为跨 16B store 复用常规 StoreUnit，并为跨 4KB 页的高半页物理地址和 DataBuffer 写入建立闭环。 | 一个请求槽、两个 fragment/response 槽、择老仲裁、六态 FSM，其中 s_block 等待 SQ doDeq。 | StoreUnit.io.misalign_enq。 | STA0 的 misalign_stin，标量/向量 store 回写接口，以及 StoreMaBufToSqControlIO。 |
| MemBlock | 集成者与端口仲裁者。 | 将 MAB 连接到三个 LDU、两个 STA、ROB/LSQ、TLB/DCache 和公共写回端口。 | 固定 MisalignWBPort = 1；普通 LDU/STA 输出在共享回写端口上优先于 MAB。 | LDU/STA、LSQ/ROB 和 redirect。 | backend writeback、StoreQueue、向量 MergeBuffer、LSQ 状态。 | -->
| Module | Who | Why | How | From | To |
| --- | --- | --- | --- | --- | --- |
| LoadMisalignBuffer | Owned by MemBlock; LDU0..2 produce enqueue requests, and LDU1 consumes fragments and returns responses. | Removes scalar/vector loads that cross 16B from the ordinary issue path and serially manages two child accesses, replay, and result merging. | One registered request slot, two fragment/response slots, `curPtr`, `unSentLoads`, and a six-state FSM. | `LqWriteBundle` from `LoadUnit.io.misalign_enq`. | LDU1 `misalign_ldin`; final scalar writeback or vector MergeBuffer; cancellation on redirect. |
| StoreMisalignBuffer | Owned by MemBlock; STA0..1 produce enqueue requests, STA0 executes fragments, and StoreQueue participates in cross-page release. | Reuses the ordinary StoreUnit for stores crossing 16B, and closes the loop for high-page physical addresses and DataBuffer writes across a 4KB page. | One request slot, two fragment/response slots, oldest-first arbitration, and a six-state FSM where `s_block` waits for SQ `doDeq`. | `StoreUnit.io.misalign_enq`. | STA0 `misalign_stin`, scalar/vector store writeback interfaces, and StoreMaBufToSqControlIO. |
| MemBlock | Integrator and port arbiter. | Connects MAB to three LDUs, two STAs, ROB/LSQ, TLB/DCache, and common writeback ports. | Fixed `MisalignWBPort = 1`; ordinary LDU/STA outputs have priority over MAB on shared writeback ports. | LDU/STA, LSQ/ROB, and redirect. | Backend writeback, StoreQueue, vector MergeBuffer, and LSQ state. |

~~~mermaid
flowchart LR
  R[Redirect and ROB] --> LMAB[LoadMisalignBuffer]
  R --> SMAB[StoreMisalignBuffer]
  LDU0[LDU0] -->|enq| LMAB
  LDU1[LDU1] -->|enq| LMAB
  LDU2[LDU2] -->|enq| LMAB
  LMAB -->|splitLoadReq Decoupled| LDU1
  LDU1 -->|splitLoadResp Valid| LMAB
  LMAB -->|writeBack or vecWriteBack| WB[WB or Vector Merge]
  STA0[STA0] -->|enq| SMAB
  STA1[STA1] -->|enq| SMAB
  SMAB -->|splitStoreReq Decoupled| STA0
  STA0 -->|splitStoreResp Valid| SMAB
  SMAB <-->|sqControl| SQ[StoreQueue and DataBuffer]
~~~

<!-- 图中的 LDU1 与 STA0 不是说它们独占所有正常访存，而是 MAB fragment 的唯一专用复用通道。fragment 进入这些单元后仍按普通路径做 DTLB、PMP/PMA、DCache、转发、miss/replay 和异常判定。 -->
LDU1 and STA0 in the diagram do not exclusively own all ordinary memory accesses. They are the only dedicated reuse channels for MAB fragments; after entering those units, a fragment still follows ordinary DTLB, PMP/PMA, DCache, forwarding, miss/replay, and exception processing.

<!-- ## 3. 参数、端口与单槽资源 -->
## 3. Parameters, Ports, and Single-Slot Resources

<!-- ### 3.1 参数与容量 -->
### 3.1 Parameters and Capacity

<!-- | 项目 | 定义位置 | 当前值或来源 | 对行为的影响 |
| --- | --- | --- | --- |
| Load 入队端口数 | [Parameters.scala:214](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:214)、[LoadMisalignBuffer.scala:39](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:39) | LoadPipelineWidth = 3 | 三个 LDU 都能向一个 LoadMAB 提交候选。 |
| Store 入队端口数 | [Parameters.scala:215](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:215)、[StoreMisalignBuffer.scala:41](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:41) | StorePipelineWidth = 2 | 两个 STA 都能向一个 StoreMAB 提交候选。 |
| 最大子请求数 | [LoadMisalignBuffer.scala:43](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:43)、[StoreMisalignBuffer.scala:45](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:45) | maxSplitNum = 2 | 两模块均只维护两份 fragment 与 response。 |
| Load 硬件失对齐开关 | [CSR.scala:566](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/CSR.scala:566) | smblockctl bit 9，hd_misalign_ld_enable | LDU 只在该 CSR 允许且满足其它安全条件时把请求送入 LoadMAB。 |
| Store 硬件失对齐开关 | [CSR.scala:567](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/CSR.scala:567) | smblockctl bit 8，hd_misalign_st_enable | STA 的 MAB 路径受该开关门控。 |
| MAB 存储深度 | [LoadMisalignBuffer.scala:143](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:143)、[StoreMisalignBuffer.scala:130](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:130) | 一个 req_valid 和一个 req 寄存器 | 容量为 1 的单槽缓冲，不是有 head/tail 的队列；req_valid=1 时没有第二条独立 MAB 指令可进入。 | -->
| Item | Definition | Current value or source | Behavioral effect |
| --- | --- | --- | --- |
| Load enqueue-port count | [Parameters.scala:214](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:214), [LoadMisalignBuffer.scala:39](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:39) | `LoadPipelineWidth = 3` | All three LDUs can submit candidates to one LoadMAB. |
| Store enqueue-port count | [Parameters.scala:215](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:215), [StoreMisalignBuffer.scala:41](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:41) | `StorePipelineWidth = 2` | Both STAs can submit candidates to one StoreMAB. |
| Maximum child-request count | [LoadMisalignBuffer.scala:43](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:43), [StoreMisalignBuffer.scala:45](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:45) | `maxSplitNum = 2` | Both modules maintain only two fragments and responses. |
| Load hardware-misalignment enable | [CSR.scala:566](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/CSR.scala:566) | `smblockctl` bit 9, `hd_misalign_ld_enable` | LDU sends a request to LoadMAB only when this CSR permits it and other safety conditions hold. |
| Store hardware-misalignment enable | [CSR.scala:567](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/CSR.scala:567) | `smblockctl` bit 8, `hd_misalign_st_enable` | The STA MAB path is gated by this switch. |
| MAB storage depth | [LoadMisalignBuffer.scala:143](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:143), [StoreMisalignBuffer.scala:130](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:130) | One `req_valid` and one `req` register | A one-entry buffer, not a head/tail queue; while `req_valid=1`, no second independent MAB instruction can enter. |

~~~scala
// LoadMisalignBuffer.scala
val req_valid = RegInit(false.B)
val req = Reg(new LqWriteBundle)
io.loadMisalignFull := req_valid
val canEnqValid = !req_valid && !select_req_bit.uop.robIdx.needFlush(io.redirect) && select_req_valid
~~~

<!-- 该代码同时给出 allocation、full 和 redirect 前筛除：接收成功时置 req_valid，完成或 redirect 时清除；模块没有索引、环绕位、free-list 或多 entry 搜索。因此任何“并行处理多个失对齐 load/store”的表述都不符合当前实现。 -->
This code simultaneously defines allocation, full, and pre-redirect filtering: successful acceptance sets `req_valid`, while completion or redirect clears it. The module has no index, wrap bit, free list, or multi-entry search. Therefore, any statement that it concurrently processes multiple misaligned loads/stores is inconsistent with the current implementation.

<!-- ### 3.2 公共入队契约与握手 -->
### 3.2 Common Enqueue Contract and Handshake

<!-- [Bundles.scala:374](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/Bundles.scala:374) 定义的公共契约为： -->
The common contract defined by [Bundles.scala:374](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/Bundles.scala:374) is:

~~~scala
class MisalignBufferEnqIO extends XSBundle {
  val req = DecoupledIO(new LqWriteBundle)
  val revoke = Output(Bool())
}
~~~

<!-- | 边 | payload | 控制与阻塞 | 消费者和效果 |
| --- | --- | --- | --- |
| enq(i).req | LqWriteBundle，含 uop、vaddr、fullva、数据/掩码及访存属性。 | 只有空槽且仲裁选中时 ready；valid and ready 才接收。Load 是低编号优先，Store 是 oldest 优先。 | 将原请求锁存到唯一 req。 |
| splitLoadReq / splitStoreReq | 当前 curPtr 指向的对齐 fragment，含恢复后的类型、地址、mask、向量标志和 isFinalSplit。 | Decoupled；仅 fire 才从 s_req 前进到 s_resp。 | LDU1 / STA0 的 S0 输入。 |
| splitLoadResp / splitStoreResp | fragment 的 data、exception、uncache/MMIO、replay 与地址属性。 | Valid；buffer 在 s_resp 且 ROB index 相等时采样；没有显式 child-id 比较。 | 写 response 槽，决定重发、合并或异常。 |
| writeBack / vecWriteBack | 原始 uop 和合并后的 data 或 exception。 | Decoupled；下游端口竞争时 MAB ready 可低，槽持续占用。 | MemBlock 的标量写回或向量 merge 路径。 |
| redirect | backend redirect。 | req.uop.robIdx.needFlush(redirect) 为真时取消当前槽，优先于正常状态推进。 | 清空状态，阻止错误路径写回。 | -->
| Edge | Payload | Control and blocking | Consumer and effect |
| --- | --- | --- | --- |
| `enq(i).req` | `LqWriteBundle`, containing the uop, `vaddr`, `fullva`, data/mask, and memory-access attributes. | `ready` only when the slot is empty and arbitration selects it; acceptance requires `valid && ready`. Load prioritizes lower port numbers, whereas Store prioritizes the oldest request. | Latches the original request into the sole `req`. |
| `splitLoadReq` / `splitStoreReq` | The aligned fragment selected by `curPtr`, including recovered type, address, mask, vector flag, and `isFinalSplit`. | `Decoupled`; only fire advances from `s_req` to `s_resp`. | S0 input of LDU1 / STA0. |
| `splitLoadResp` / `splitStoreResp` | Fragment data, exception, uncache/MMIO, replay, and address attributes. | `Valid`; the buffer samples it in `s_resp` when the ROB index matches, with no explicit child-ID comparison. | Writes a response slot and determines retry, merging, or exception handling. |
| `writeBack` / `vecWriteBack` | The original uop and merged data or exception. | `Decoupled`; MAB `ready` can be low during downstream-port contention, so the slot remains occupied. | MemBlock scalar-writeback or vector-merge path. |
| `redirect` | Backend redirect. | Cancels the current slot when `req.uop.robIdx.needFlush(redirect)` is true, ahead of ordinary state advance. | Clears state and prevents wrong-path writeback. |

<!-- splitResp 是 Valid 而非 Decoupled，因而它没有 response ready 可以施加背压；正确性依赖专用 LDU/STA 仅在 buffer 处于 s_resp、且 ROB 身份相符时产生可采样结果。源码检查的是 robIdx，并未找到 fragment 序号比较；这是后文验证重点。 -->
`splitResp` is `Valid`, not `Decoupled`, so it has no response `ready` to apply backpressure. Correctness relies on the dedicated LDU/STA producing a sampleable result only while the buffer is in `s_resp` and the ROB identity matches. The source checks `robIdx`, but no fragment-sequence comparison was found; this is a verification focus later in the document.

<!-- ## 4. MemBlock 集成与端口仲裁 -->
## 4. MemBlock Integration and Port Arbitration

<!-- ### 4.1 连接事实 -->
### 4.1 Connection Facts

<!-- // MemBlock.scala，省略非 MAB 端口 -->
~~~scala
// MemBlock.scala; non-MAB ports omitted.
loadMisalignBuffer.io.enq(i) <> loadUnits(i).io.misalign_enq
if (i == MisalignWBPort) {
  loadUnits(i).io.misalign_ldin  <> loadMisalignBuffer.io.splitLoadReq
  loadUnits(i).io.misalign_ldout <> loadMisalignBuffer.io.splitLoadResp
}
~~~

<!-- 上述连接位于 [MemBlock.scala:1019](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1019)。所有 LDU 可入队，但只有 MisalignWBPort = 1 的 LDU 可执行与回传 load fragment；其余 LDU 的 misalign_ldin.valid 被置为 false。Store 侧也让两个 STA 入队，而只将 splitStoreReq/Resp 接到 STA0，[MemBlock.scala:1281](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1281)。 -->
These connections are at [MemBlock.scala:1019](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1019). Every LDU can enqueue, but only the LDU at `MisalignWBPort = 1` executes and returns load fragments; `misalign_ldin.valid` is set to false for the others. The Store side likewise allows both STAs to enqueue, but connects `splitStoreReq/Resp` only to STA0, [MemBlock.scala:1281](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1281).

<!-- MAB 虽接收完整 RobLsqIO，[MemBlock.scala:1183](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1183)，但 LoadMAB 内部对 io.rob 的唯一可见用途是把反向 MMIO 字段置 DontCare，[LoadMisalignBuffer.scala:138](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:138)。因此“只有足够老的 load 才可入 MAB”不能归因于 buffer 内部 ROB 仲裁；实际门控发生在 LoadUnit S3 的 s3_misalign_can_go，[LoadUnit.scala:1565](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1565)。 -->
Although MAB receives the full RobLsqIO, [MemBlock.scala:1183](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1183), the only visible use of `io.rob` inside LoadMAB is setting a reverse MMIO field to `DontCare`, [LoadMisalignBuffer.scala:138](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:138). Therefore, the claim that only sufficiently old loads may enter MAB cannot be attributed to ROB arbitration inside the buffer; the actual gate is `s3_misalign_can_go` in LoadUnit S3, [LoadUnit.scala:1565](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1565).

<!-- ### 4.2 写回优先级不是 MAB 优先 -->
### 4.2 Writeback Priority Is Not MAB Priority

~~~scala
val misalignWritebackOverride = Mux(
  loadUnits(MisalignWBPort).io.ldout.valid,
  loadUnits(MisalignWBPort).io.ldout.bits,
  loadMisalignBuffer.io.writeBack.bits)
loadMisalignBuffer.io.writeBack.ready :=
  ldaExeWbReqs(MisalignWBPort).ready && !loadUnits(MisalignWBPort).io.ldout.valid
~~~

<!-- [MemBlock.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:529) 表明普通 LDU1 的 ldout.valid 同拍出现时，它覆盖 MAB 标量写回，且 MAB ready 被压低。Store 的 stOut(0) 也仅在其它常规标量/向量 store 输出都无效时才接收 MAB，[MemBlock.scala:1388](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1388)。因此 MAB 完成不等于同拍离开 buffer；必须等到共享写回端口真正 fire。 -->
[MemBlock.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:529) shows that a same-cycle ordinary LDU1 `ldout.valid` overrides MAB scalar writeback and forces MAB `ready` low. Store `stOut(0)` likewise accepts MAB only when all other ordinary scalar/vector store outputs are invalid, [MemBlock.scala:1388](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1388). MAB completion therefore does not mean it leaves the buffer in that cycle; it must wait for actual fire on the shared writeback port.

<!-- loadMisalignFull 从 LoadMAB 接到 LSQ、LoadQueue 和 LoadQueueReplay，[MemBlock.scala:1195](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1195)、[LoadQueue.scala:288](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala:288)。但本提交中搜索 LoadQueueReplay.scala，该输入只在 IO 声明处出现；C_MF replay 的解除条件只读取 robDeqPtr，[LoadQueueReplay.scala:366](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:366)。所以不能宣称 loadMisalignFull 当前直接阻塞或释放 replay；满槽后的请求会 nack/replay，后续是否成功取决于重新执行时的槽状态和 ROB 年龄。 -->
`loadMisalignFull` connects from LoadMAB to LSQ, LoadQueue, and LoadQueueReplay, [MemBlock.scala:1195](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1195), [LoadQueue.scala:288](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala:288). However, in this commit, searching LoadQueueReplay.scala shows that the input appears only in the IO declaration; the C_MF replay-release condition reads only `robDeqPtr`, [LoadQueueReplay.scala:366](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:366). Thus it must not be claimed that `loadMisalignFull` currently directly blocks or releases replay: a full-slot request nacks/replays, and later success depends on slot state and ROB age when it re-executes.

<!-- ## 5. LoadMisalignBuffer：状态、控制与数据 -->
## 5. LoadMisalignBuffer: State, Control, and Data

<!-- ### 5.1 入队仲裁和生命周期 -->
### 5.1 Enqueue Arbitration and Lifecycle

<!-- LoadMAB 的 select_req_bit 使用 ParallelPriorityMux(io.enq.map(_.req.valid), ...)，[LoadMisalignBuffer.scala:148](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:148)。enq(0) 优先于 enq(1)，再优先于 enq(2)；这是静态 port 优先级，不是按 robIdx 选最老。只有空槽、候选非 redirect-killed 且选中 valid 时才锁存。 -->
LoadMAB's `select_req_bit` uses `ParallelPriorityMux(io.enq.map(_.req.valid), ...)`, [LoadMisalignBuffer.scala:148](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:148). `enq(0)` has priority over `enq(1)`, which has priority over `enq(2)`; this is static port priority, not oldest-by-`robIdx`. The request is latched only when the slot is empty, the candidate is not redirect-killed, and the selected request is valid.

<!-- LoadUnit S3 把“足够老”和 buffer ready 合在入队条件中： -->
LoadUnit S3 combines “old enough” and buffer-ready conditions in the enqueue condition:

~~~scala
val toMisalignBufferValid =
  s3_can_enter_lsq_valid && s3_mis_align && !s3_frm_mabuf
io.misalign_enq.req.valid :=
  toMisalignBufferValid && s3_misalign_can_go
s3_lrq_rep_info.misalign_nack :=
  toMisalignBufferValid && !(io.misalign_enq.req.ready && s3_misalign_can_go)
~~~

<!-- 见 [LoadUnit.scala:1582](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1582)。这里的 nack 会成为 C_MF replay 原因；MAB 内部不保存第二个候选。 -->
See [LoadUnit.scala:1582](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1582). This `nack` becomes a C_MF replay cause; MAB does not retain a second candidate internally.

~~~mermaid
stateDiagram-v2
  [*] --> s_idle
  s_idle --> s_split: req_valid
  s_split --> s_req: build two fragments
  s_req --> s_resp: splitLoadReq.fire
  s_resp --> s_req: replay or unsent fragment
  s_resp --> s_comb_wakeup_rep: both fragments complete
  s_resp --> s_wb: exception or uncache
  s_comb_wakeup_rep --> s_wb: scalar wakeup fire or vector direct
  s_wb --> s_idle: writeBack.fire or vecWriteBack.fire
  s_split --> s_idle: redirect flush
  s_req --> s_idle: redirect flush
  s_resp --> s_idle: redirect flush
  s_comb_wakeup_rep --> s_idle: redirect flush
  s_wb --> s_idle: redirect flush
~~~

<!-- | 状态 | Who / 状态含义 | 入口 | 保持或退出 | 主要输出与恢复 |
| --- | --- | --- | --- | --- |
| s_idle | buffer 空闲的控制状态。 | reset 或上次写回完成。 | 已锁存 req_valid 后进入 s_split。 | 不发 fragment。 |
| s_split | 根据原类型和低地址位建立两个对齐 splitLoadReqs。 | req_valid。 | 构造完无条件进 s_req。 | 设置 unSentLoads = 2b11、curPtr = 0。 |
| s_req | 向 LDU1 发当前 fragment。 | s_split 或 replay。 | 仅 splitLoadReq.fire 后进入 s_resp。 | splitLoadReq.valid；ready=0 时保持 fragment 和指针。 |
| s_resp | 接收当前 fragment 的 LDU 结果。 | 一个 fragment 发射完成。 | replay/未完成片段回 s_req；两个正常完成进合并；异常或 uncache 直接进写回。 | 写 splitLoadResps(curPtr)、异常向量、未发送位。 |
| s_comb_wakeup_rep | 标量 normal load 的最终唤醒往返。 | 两片正常完成。 | 标量等 misalignNeedWakeUp 请求 fire；向量直接进 s_wb。 | 不是第三个数据 fragment。 |
| s_wb | 对原 uop 发标量或向量写回。 | 全局异常/uncache，或合并及唤醒完成。 | 对应 Decoupled 写回 fire。 | 清 req_valid、指针、response、全局状态；端口竞争则停留。 | -->
| State | Meaning / owner | Entry | Hold or exit | Primary output and recovery |
| --- | --- | --- | --- | --- |
| `s_idle` | Control state with an idle buffer. | Reset or completion of the prior writeback. | Enters `s_split` after `req_valid` is latched. | Issues no fragment. |
| `s_split` | Builds two aligned `splitLoadReqs` from the original type and low address bits. | `req_valid`. | Unconditionally enters `s_req` after construction. | Sets `unSentLoads = 2b11`, `curPtr = 0`. |
| `s_req` | Sends the current fragment to LDU1. | `s_split` or replay. | Enters `s_resp` only after `splitLoadReq.fire`. | `splitLoadReq.valid`; holds fragment and pointer when `ready=0`. |
| `s_resp` | Receives the LDU result for the current fragment. | One fragment has been issued. | Returns to `s_req` for replay/unfinished fragments; enters merge after two normal completions; enters writeback directly on exception or uncache. | Writes `splitLoadResps(curPtr)`, exception vector, and unsent bit. |
| `s_comb_wakeup_rep` | Final wakeup round trip for a scalar normal load. | Both fragments complete normally. | A scalar waits for `misalignNeedWakeUp` request fire; a vector enters `s_wb` directly. | Not a third data fragment. |
| `s_wb` | Issues scalar or vector writeback for the original uop. | Global exception/uncache, or completed merge and wakeup. | Corresponding `Decoupled` writeback fire. | Clears `req_valid`, pointer, responses, and global state; stays on port contention. |

<!-- 状态与更新由 [LoadMisalignBuffer.scala:165](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:165) 到 [LoadMisalignBuffer.scala:290](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:290) 给出。redirect 清理在同文件 [LoadMisalignBuffer.scala:610](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:610)，应视为各状态的高优先级恢复路径。 -->
State and update logic is given from [LoadMisalignBuffer.scala:165](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:165) through [LoadMisalignBuffer.scala:290](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:290). Redirect cleanup is at [LoadMisalignBuffer.scala:610](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:610) and should be regarded as a high-priority recovery path from every state.

<!-- ### 5.2 哪些 load 会进入本模块 -->
### 5.2 Which Loads Enter This Module

<!-- 它不是通用的“任意自然失对齐”单元。LDU S0 分别计算访问是否跨 16B 以及是否在自然边界对齐；同一 16B 内的自然失对齐由 misalignWith16Byte 标记，[LoadUnit.scala:711](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:711)。进入 buffer 的条件在 S2/S3 由 hd_misalign_ld_enable、失对齐类别、非异常/非 uncache、非 trigger 等条件共同门控，[LoadUnit.scala:1238](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1238)、[LoadUnit.scala:1582](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1582)。buffer 自己在 split 逻辑中以 cross16BytesBoundary 为前提，[LoadMisalignBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:292)。 -->
This is not a general-purpose unit for every naturally misaligned access. LDU S0 separately determines whether an access crosses 16B and whether it is naturally aligned; natural misalignment within the same 16B is marked by `misalignWith16Byte`, [LoadUnit.scala:711](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:711). Admission to the buffer is jointly gated in S2/S3 by `hd_misalign_ld_enable`, misalignment class, non-exception/non-uncache, non-trigger, and other conditions, [LoadUnit.scala:1238](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1238), [LoadUnit.scala:1582](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1582). The buffer itself assumes `cross16BytesBoundary` in its split logic, [LoadMisalignBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:292).

<!-- 可确认的结论是：本模块处理可被硬件分成两个、跨 16B 边界的 load fragment，而非所有架构意义上的非自然对齐访问。同一 16B 内的情况和被前级判为异常/特殊内存类型的情况，不能由本模块的存在推断也会进入该 buffer。 -->
The confirmed conclusion is that this module handles load fragments crossing a 16B boundary that hardware can divide into two, not every architecturally naturally misaligned access. Its existence does not imply that a same-16B case or a case classified as exception/special memory type upstream also enters this buffer.

<!-- ### 5.3 分片算法、索引和数据合并 -->
### 5.3 Split Algorithm, Indexing, and Data Merging

<!-- LoadMAB 先按原访问类型计算末字节地址，再比较 vaddr 低 5 位的 bit 4： -->
LoadMAB first calculates the address of the final byte from the original access type, then compares bit 4 of the low five bits of `vaddr`:

~~~scala
val highAddress = LookupTree(alignedType, Seq(
  LSUOpType.lb -> 0.U, LSUOpType.lh -> 1.U,
  LSUOpType.lw -> 3.U, LSUOpType.ld -> 7.U)) + req.vaddr(4, 0)
val cross16BytesBoundary = req_valid && highAddress(4) =/= req.vaddr(4)
~~~

<!-- 见 [LoadMisalignBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:292)。这里的边界是 16B，而不是 64B DCache line 或 4KB virtual page；后两者在后文单独讨论。 -->
See [LoadMisalignBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:292). This boundary is 16B, not a 64B DCache line or a 4KB virtual page; the latter two are discussed separately later.

<!-- | 原访问 | 低地址位条件 | 低 fragment | 高 fragment | 分片依据 |
| --- | --- | --- | --- | --- |
| LH | bit 0 为 1 | LB at A | LB at A + 1 | 两个字节分别对齐。 |
| LW | A mod 4 = 1 | LW at A - 1 | LB at A + 3 | 低片保留 3 字节，高片 1 字节。 |
| LW | A mod 4 = 2 | LH at A | LH at A + 2 | 2B 加 2B。 |
| LW | A mod 4 = 3 | LB at A | LW at A + 1 | 1B 加 3B。 |
| LD | A mod 8 = 1/2/3 | LD at A - 1/-2/-3 | LB/LH/LW at A + 7/+6/+5 | 低片分别取 7/6/5 个有效字节。 |
| LD | A mod 8 = 4 | LW at A | LW at A + 4 | 4B 加 4B。 |
| LD | A mod 8 = 5/6/7 | LW/LH/LB at A - 1/-2/A | LD at A + 3/+2/+1 | 低片分别取 3/2/1 个有效字节。 | -->
| Original access | Low-address condition | Low fragment | High fragment | Split rationale |
| --- | --- | --- | --- | --- |
| LH | bit 0 is 1 | LB at A | LB at A + 1 | The two bytes are separately aligned. |
| LW | A mod 4 = 1 | LW at A - 1 | LB at A + 3 | The low fragment retains 3 bytes and the high fragment 1 byte. |
| LW | A mod 4 = 2 | LH at A | LH at A + 2 | 2B plus 2B. |
| LW | A mod 4 = 3 | LB at A | LW at A + 1 | 1B plus 3B. |
| LD | A mod 8 = 1/2/3 | LD at A - 1/-2/-3 | LB/LH/LW at A + 7/+6/+5 | The low fragment uses 7/6/5 valid bytes, respectively. |
| LD | A mod 8 = 4 | LW at A | LW at A + 4 | 4B plus 4B. |
| LD | A mod 8 = 5/6/7 | LW/LH/LB at A - 1/-2/A | LD at A + 3/+2/+1 | The low fragment uses 3/2/1 valid bytes, respectively. |

<!-- 完整 case 在 [LoadMisalignBuffer.scala:314](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:314) 到 [LoadMisalignBuffer.scala:508](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:508)。表中 A 是原始虚拟地址；代码为每个 child 写新的 vaddr、掩码、访问大小和 uop 访存类型，同时保存原始 fullva。不能把名字为 getMask、new128Load 的局部计算直接描述为有效状态机功能：本次只以最终被 splitLoadReq 使用的请求字段为证据。 -->
The complete cases are at [LoadMisalignBuffer.scala:314](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:314) through [LoadMisalignBuffer.scala:508](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:508). In the table, A is the original virtual address; the code writes a new `vaddr`, mask, access size, and uop memory-access type for each child while retaining the original `fullva`. Local calculations named `getMask` or `new128Load` must not themselves be described as active state-machine functions; this analysis relies only on request fields ultimately used by `splitLoadReq`.

<!-- fragment 使用一个 1-bit curPtr 和 2-bit unSentLoads。s_req 只把 splitLoadReqs(curPtr) 放到 Decoupled 输出；每次 response 后，正常无 replay 的当前 bit 才清除并移动 curPtr。任何 fragment 的 rep_info.need_rep 都回到 s_req 重发，而不会丢弃另一个片段。[LoadMisalignBuffer.scala:192](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:192)、[LoadMisalignBuffer.scala:522](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:522)。 -->
Fragments use a 1-bit `curPtr` and 2-bit `unSentLoads`. `s_req` places only `splitLoadReqs(curPtr)` on the `Decoupled` output; after each response, only a normal non-replayed current bit is cleared and `curPtr` advances. `rep_info.need_rep` for either fragment returns to `s_req` to resend it without discarding the other fragment. [LoadMisalignBuffer.scala:192](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:192), [LoadMisalignBuffer.scala:522](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:522).

<!-- 两片正常响应保存在 splitLoadResps(0/1)。buffer 用 getShiftAndTruncateData、rdataHelper 或 rdataVecHelper 根据原类型、data_select 和原始地址完成位移、截断、拼接以及标量符号/零扩展，[LoadMisalignBuffer.scala:540](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:540)。数据合并发生在 buffer 内，TLB/DCache 不需要知道这是原指令的哪一半。 -->
The two normal responses are retained in `splitLoadResps(0/1)`. The buffer uses `getShiftAndTruncateData`, `rdataHelper`, or `rdataVecHelper` to shift, truncate, concatenate, and apply scalar sign/zero extension according to the original type, `data_select`, and original address, [LoadMisalignBuffer.scala:540](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:540). Data merging occurs inside the buffer; TLB/DCache need not know which half belongs to the original instruction.

<!-- ### 5.4 Load fragment 复用 LDU1 的流水线 -->
### 5.4 Load-Fragment Pipeline Reusing LDU1

<!-- | 位置 | 输入、工作和寄存器 | 关键握手或限制 | 下游效果 |
| --- | --- | --- | --- |
| LoadMAB s_req | splitLoadReq 是 Decoupled，携带 curPtr fragment。 | 只有 splitLoadReq.fire 才到 s_resp。 | 进入 LDU1 的 misalign_ldin。 |
| LDU S0 source arbitration | misalign_ldin 是 source 0，在常规 issue/replay/prefetch 等 source 前。 | s0 source ready 需要没有更高优先级 source；MAB source 自身为最高优先级。 | 已被 buffer 选中的 child 可抢占 LDU1 常规 S0 输入。 |
| LDU S0/S1 | child 的 vaddr 和 fullva 进入 DTLB；常规对齐、TLB/PMP、DCache request 逻辑复用。 | LoadUnit 以 frm_mabuf 标记该来源；交给 DCache 前仍受 ready、kill、redirect 影响。 | 每个 child 独立经历译址、权限与 cache/forwarding。 |
| LDU S2/S3 | child 取得 data、exception、uncache 或 replay 原因。 | MAB child 的 fwd_fail、mem_amb、nuke、RAR/RAW nack 可引起 rollback。 | Valid-only 的 misalign_ldout 回到 buffer。 |
| LoadMAB s_resp/s_wb | 保存 response、决定重发或合并，并在允许时向公共 WB 发原 uop。 | 正常标量还有一个 wakeup 往返；公共端口可能反压。 | 写回后才释放唯一槽。 | -->
| Location | Inputs, operation, and registers | Key handshake or constraint | Downstream effect |
| --- | --- | --- | --- |
| LoadMAB `s_req` | `splitLoadReq` is `Decoupled` and carries the `curPtr` fragment. | Reaches `s_resp` only on `splitLoadReq.fire`. | Enters LDU1 `misalign_ldin`. |
| LDU S0 source arbitration | `misalign_ldin` is source 0, ahead of ordinary issue/replay/prefetch and other sources. | S0 source `ready` requires no higher-priority source; the MAB source itself is highest priority. | A child selected by the buffer can preempt LDU1's ordinary S0 input. |
| LDU S0/S1 | Child `vaddr` and `fullva` enter DTLB; ordinary alignment, TLB/PMP, and DCache request logic is reused. | LoadUnit marks this source with `frm_mabuf`; before reaching DCache it remains subject to `ready`, kill, and redirect. | Each child independently undergoes translation, permission checks, and cache/forwarding. |
| LDU S2/S3 | The child obtains data, exception, uncache, or replay cause. | An MAB child can roll back because of `fwd_fail`, `mem_amb`, `nuke`, or RAR/RAW nack. | Valid-only `misalign_ldout` returns to the buffer. |
| LoadMAB `s_resp/s_wb` | Retains the response, chooses retransmission or merging, and issues the original uop to shared WB when permitted. | A normal scalar needs an additional wakeup round trip; the common port can backpressure. | The only slot is released only after writeback. |

<!-- 证据：MAB source 在 [LoadUnit.scala:290](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:290) 到 [LoadUnit.scala:335](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:335) 具有最高优先级；来源标记在 [LoadUnit.scala:431](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:431)，地址与译址路径在 [LoadUnit.scala:692](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:692)，结果回传在 [LoadUnit.scala:1865](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1865)。 -->
Evidence: the MAB source has highest priority from [LoadUnit.scala:290](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:290) through [LoadUnit.scala:335](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:335); its source marking is at [LoadUnit.scala:431](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:431), address and translation paths are at [LoadUnit.scala:692](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:692), and result return is at [LoadUnit.scala:1865](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1865).

<!-- 当两个标量 child 都正常完成时，LoadMAB 会在 s_comb_wakeup_rep 发一个带 misalignNeedWakeUp 的请求；LDU 识别它后不做普通 DCache 访问，而是经三拍寄存延迟生成一个 Valid-only 回应，[LoadMisalignBuffer.scala:243](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:243)、[LoadUnit.scala:1182](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1182)、[LoadUnit.scala:1865](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1865)。它是最终写回时序的完成令牌，不是第三个读请求。向量路径不发送这个 wakeup，而是合并后走 vecWriteBack。 -->
After two scalar children complete normally, LoadMAB sends a request with `misalignNeedWakeUp` in `s_comb_wakeup_rep`. LDU recognizes it without performing an ordinary DCache access and instead produces a Valid-only response after a three-cycle registered delay, [LoadMisalignBuffer.scala:243](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:243), [LoadUnit.scala:1182](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1182), [LoadUnit.scala:1865](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1865). It is a completion token for final writeback timing, not a third read request. The vector path sends no such wakeup and proceeds through `vecWriteBack` after merging.

~~~waveform-draw
{
  "signal": [
    {"name": "clk", "wave": "p.........."},
    {"name": "enq.req.valid", "wave": "010........"},
    {"name": "enq.req.ready", "wave": "010........"},
    {"name": "req_valid", "wave": "0.1........"},
    {"name": "splitLoadReq.valid", "wave": "0...101...."},
    {"name": "splitLoadReq.ready", "wave": "1..........."},
    {"name": "splitLoadResp.valid", "wave": "0....101..."},
    {"name": "misalignNeedWakeUp", "wave": "0.......10.."},
    {"name": "writeBack.valid", "wave": "0.........1."},
    {"name": "writeBack.ready", "wave": "1..........."},
    {"name": "redirect.valid", "wave": "0..........."}
  ],
  "config": {"hscale": 1}
}
~~~

<!-- 这是正常标量路径的符号波形，连续的两个 splitLoadReq/Resp 高电平表示两个 fragment 的发送和回收；点号代表可变的 hit/miss/replay 间隔，不表示固定周期。每个实际推进点都要求相应的 Decoupled fire，response 则是 Valid-only。 -->
This is a symbolic waveform for the normal scalar path. The two consecutive high periods of `splitLoadReq/Resp` denote issue and return of the two fragments; dots denote variable hit/miss/replay intervals rather than fixed cycles. Every actual advance point requires the associated `Decoupled` fire, while responses are Valid-only.

<!-- ### 5.5 Load 当前代码中的可见风险 -->
### 5.5 Visible Risks in Current Load Code

<!-- 在正常标量路径中，s_comb_wakeup_rep 后的 writeBack.valid 依赖一次性 fake wakeup response 的同拍条件，[LoadMisalignBuffer.scala:561](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:561)。而 LDU 的 fake response 是 RegNextN 后的 Valid 脉冲，没有 response ready；MemBlock 又在同拍普通 LDU1 ldout.valid 为 1 时压低 MAB writeBack.ready 并选择普通输出，[MemBlock.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:529)。 -->
On the normal scalar path, `writeBack.valid` after `s_comb_wakeup_rep` depends on the same-cycle condition of a one-shot fake-wakeup response, [LoadMisalignBuffer.scala:561](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:561). The LDU fake response is a `RegNextN`-delayed Valid pulse with no response `ready`; MemBlock also lowers MAB `writeBack.ready` and selects the ordinary output when normal LDU1 `ldout.valid` is 1 in the same cycle, [MemBlock.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:529).

<!-- 这形成一个**条件风险**：若 fake response 脉冲与普通 LDU1 输出重叠，当前源码表面上可能既没有 MAB 写回 fire，也没有重发机制。源码中未找到保证二者互斥的 assert。它不是已由动态测试证明的缺陷，应使用波形或形式属性验证该交叠是否不可达。向量路径不同：vecWriteBack.valid 已显式受 loadVecOutValid 抑制，[LoadMisalignBuffer.scala:581](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:581)，普通向量输出占用时 MAB 会保持在 s_wb；仍应验证它随后能正确交付而不丢数据。 -->
This creates a **conditional risk**: if a fake-response pulse overlaps ordinary LDU1 output, the visible source may provide neither MAB writeback fire nor a retry mechanism. No assertion guaranteeing mutual exclusion was found. This is not a defect proven by dynamic testing; waveforms or formal properties should determine whether the overlap is unreachable. The vector path differs: `vecWriteBack.valid` is explicitly suppressed by `loadVecOutValid`, [LoadMisalignBuffer.scala:581](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:581), so MAB remains in `s_wb` while ordinary vector output occupies the port; it still needs verification that delivery eventually succeeds without data loss.

<!-- ## 6. StoreMisalignBuffer：分片、跨页与 StoreQueue 闭环 -->
## 6. StoreMisalignBuffer: Splitting, Cross-Page Handling, and the StoreQueue Closure

<!-- ### 6.1 与 Load 的核心差异 -->
### 6.1 Core Differences from Load

<!-- | 项目 | LoadMisalignBuffer | StoreMisalignBuffer |
| --- | --- | --- |
| 同拍入队仲裁 | 固定低编号 port 优先。 | selectOldest，按 ROB 年龄再按 uop 序号选最老。 |
| 特有状态 | s_comb_wakeup_rep，给标量合并结果制造写回令牌。 | s_block，等待 StoreQueue 侧 DataBuffer 处理跨页 store。 |
| 跨页识别 | 该模块自身只判定 bit 4 的 16B 跨界。 | 额外计算 bit 12 的 cross4KBPageBoundary。 |
| 正常完成 | 两个 child data 在 buffer 合并。 | 不在 buffer 合并 store data；StoreQueue 使用 mask/data 的高低分片和 MAB 返回的高页 paddr。 |
| 输出竞争 | 普通 LDU1 标量/向量输出优先。 | 其它普通标量与向量 StoreUnit 输出优先。 | -->
| Item | LoadMisalignBuffer | StoreMisalignBuffer |
| --- | --- | --- |
| Same-cycle enqueue arbitration | Fixed lower port-number priority. | `selectOldest`, choosing oldest first by ROB age and then uop sequence number. |
| Unique state | `s_comb_wakeup_rep`, which creates a writeback token for the scalar merged result. | `s_block`, which waits for StoreQueue DataBuffer handling of a cross-page store. |
| Cross-page detection | The module itself detects only a 16B crossing at bit 4. | Additionally calculates `cross4KBPageBoundary` at bit 12. |
| Normal completion | The buffer merges two child-data responses. | It does not merge store data in the buffer; StoreQueue uses low/high mask/data fragments and the high-page `paddr` returned by MAB. |
| Output contention | Ordinary LDU1 scalar/vector output has priority. | Other ordinary scalar and vector StoreUnit outputs have priority. |

<!-- Store 的入队 bundle 虽名为 LqWriteBundle，但它只是 MisalignBufferEnqIO 的共享 payload 类型，[Bundles.scala:374](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/Bundles.scala:374)。不能据此把 StoreMAB 入队误写成 LoadQueue 分配。 -->
Although the Store enqueue bundle is named `LqWriteBundle`, it is merely the shared payload type of MisalignBufferEnqIO, [Bundles.scala:374](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/Bundles.scala:374). That does not make StoreMAB enqueue a LoadQueue allocation.

~~~scala
// StoreMisalignBuffer.scala
val (reqSelValid, reqSel) = selectOldest(io.enq.map(_.req))
val canEnq = !req_valid && !reqRedirect && reqSelValid
when (canEnq) {
  req := reqSel.bits
  req_valid := true.B
}
~~~

<!-- 该路径见 [StoreMisalignBuffer.scala:146](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:146)。selectOldest 的比较递归在 [StoreMisalignBuffer.scala:71](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:71)；它和 Load 的 PriorityMux 完全不同。 -->
This path is at [StoreMisalignBuffer.scala:146](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:146). The recursive `selectOldest` comparison is at [StoreMisalignBuffer.scala:71](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:71); it differs completely from Load's `PriorityMux`.

<!-- ### 6.2 Store FSM 与状态释放 -->
### 6.2 Store FSM and State Release

~~~mermaid
stateDiagram-v2
  [*] --> s_idle
  s_idle --> s_split: selected request and robMatch
  s_split --> s_req: build two fragments
  s_req --> s_resp: splitStoreReq.fire
  s_resp --> s_req: replay or unsent fragment
  s_resp --> s_wb: two fragments done or global error
  s_wb --> s_idle: non-cross-page writeBack fire
  s_wb --> s_block: cross4KB writeBack fire
  s_block --> s_idle: sqControl.doDeq
  s_split --> s_idle: redirect or revoke
  s_req --> s_idle: redirect or revoke
  s_resp --> s_idle: redirect or revoke
  s_wb --> s_idle: redirect or revoke
  s_block --> s_idle: redirect or revoke
~~~

<!-- | 状态 | 进入条件 | 工作和保持条件 | 退出/释放 |
| --- | --- | --- | --- |
| s_idle | reset 或已完成释放。 | 等待择老 request，跨页路径还受 robMatch/pendingPtr 条件约束。 | 满足后进入 s_split。 |
| s_split | 已锁存 request。 | 按访问类型与低地址位生成两个对齐 store child。 | 无条件进入 s_req。 |
| s_req | 有未发送 child。 | 把 splitStoreReqs(curPtr) 送给 STA0，等待 fire。 | fire 后进入 s_resp。 |
| s_resp | 一个 child 已被 STA0 接收。 | 接收 replay、异常/uncache 或正常结果；未成功的 fragment 保持为待发送。 | replay/未发送回 s_req；全完成或全局错误进 s_wb。 |
| s_wb | 两 child 已处理或发生全局异常。 | 发标量/向量 store 输出；被公共端口挡住时保持。 | 非跨页在 writeBack fire 后释放；跨 4KB 页转 s_block。 |
| s_block | 跨 4KB 页 store 的写回已经交付。 | 不等 ROB commit；等待 StoreQueue 确认 dataBuffer.io.enq(0).fire。 | sqControl.toStoreMisalignBuffer.doDeq 后才清 req_valid。 | -->
| State | Entry condition | Operation and hold condition | Exit/release |
| --- | --- | --- | --- |
| `s_idle` | Reset or completed release. | Waits for the oldest request; the cross-page path also observes `robMatch/pendingPtr` conditions. | Enters `s_split` when conditions hold. |
| `s_split` | A request is latched. | Generates two aligned store children from access type and low address bits. | Unconditionally enters `s_req`. |
| `s_req` | An unsent child exists. | Sends `splitStoreReqs(curPtr)` to STA0 and waits for fire. | Enters `s_resp` after fire. |
| `s_resp` | STA0 has accepted one child. | Receives replay, exception/uncache, or normal result; unsuccessful fragments remain pending. | Replay/unsent returns to `s_req`; full completion or global error enters `s_wb`. |
| `s_wb` | Both children have been handled or a global exception occurred. | Issues scalar/vector store output and holds when blocked by the common port. | A non-cross-page request releases after `writeBack` fire; a 4KB-crossing request moves to `s_block`. |
| `s_block` | Writeback for a store crossing a 4KB page has been delivered. | Does not wait for ROB commit; waits for StoreQueue confirmation of `dataBuffer.io.enq(0).fire`. | Clears `req_valid` only after `sqControl.toStoreMisalignBuffer.doDeq`. |

<!-- 状态和转移位于 [StoreMisalignBuffer.scala:136](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:136) 到 [StoreMisalignBuffer.scala:326](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:326)。redirect 或 enq.revoke 会以高优先级清槽，[StoreMisalignBuffer.scala:641](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:641)。 -->
State and transition logic is at [StoreMisalignBuffer.scala:136](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:136) through [StoreMisalignBuffer.scala:326](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:326). Redirect or `enq.revoke` clears the slot at high priority, [StoreMisalignBuffer.scala:641](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:641).

<!-- ### 6.3 Store 分片与 4KB 页控制 -->
### 6.3 Store Splitting and 4KB-Page Control

<!-- Store 同样比较低 5 位 bit 4 判断是否跨 16B，并额外比较 bit 12 判断 cross4KBPageBoundary，[StoreMisalignBuffer.scala:329](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:329)。其 LH/LW/SW 的子访问形状与 Load 对应类型相近；SD 的高偏移 case 则不能照抄 Load。例如 A mod 8 为 5/6/7 时，Store 代码分别使用 SD at A-5/A-6/A-7 加 SD at A+3/A+2/A+1，而 Load 对应的是较小低片加 LD 高片。[StoreMisalignBuffer.scala:360](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:360) 到 [StoreMisalignBuffer.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:529)。 -->
Store also compares bit 4 of the low five bits to determine whether it crosses 16B, and additionally compares bit 12 to determine `cross4KBPageBoundary`, [StoreMisalignBuffer.scala:329](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:329). The child-access shapes for LH/LW/SW resemble their Load counterparts, but high-offset SD cases must not be copied from Load. For example, for A mod 8 equal to 5/6/7, Store code uses SD at A-5/A-6/A-7 plus SD at A+3/A+2/A+1, whereas Load uses a smaller low fragment plus a high LD. [StoreMisalignBuffer.scala:360](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:360) through [StoreMisalignBuffer.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:529).

<!-- fragment 接口在 [StoreMisalignBuffer.scala:532](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:532)：当前 child 的 vaddr、掩码、uop 和向量标志经 splitStoreReq 进入 STA0。STA0 仅在 hd_misalign_st_enable、跨 16B 类型、非特殊/异常类别等前提下把原 store 交给 MAB，[StoreUnit.scala:430](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:430)。每个 fragment 再复用 STA 的 TLB/PMP/DCache/store 执行路径，回传 splitStoreResp；buffer 在 [StoreMisalignBuffer.scala:542](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:542) 保存结果或重发。 -->
The fragment interface is at [StoreMisalignBuffer.scala:532](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:532): the current child's `vaddr`, mask, uop, and vector flag enter STA0 through `splitStoreReq`. STA0 sends the original store to MAB only under conditions including `hd_misalign_st_enable`, a 16B-crossing class, and non-special/non-exception classification, [StoreUnit.scala:430](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:430). Each fragment then reuses STA's TLB/PMP/DCache/store-execution path and returns `splitStoreResp`; the buffer retains the result or retries at [StoreMisalignBuffer.scala:542](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:542).

<!-- 跨 4KB 页的特殊性不是把 store “提交到 ROB”。buffer 向 StoreQueue 提供 crossPageWithHit、crossPageCanDeq、高页 paddr 和 withSamePtr，[StoreMisalignBuffer.scala:223](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:223)、[Bundles.scala:277](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/Bundles.scala:277)。StoreQueue 在两个 DataBuffer 槽可用时拆原始 store 的 mask/data，使用 MAB 提供的高页物理地址；它把 doDeq 定义为跨页条件、可出队条件和 dataBuffer.io.enq(0).fire 的合取，[StoreQueue.scala:1189](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1189)。这就是 s_block 的唯一正常释放确认。 -->
The special cross-4KB-page behavior does not “commit the store to the ROB.” The buffer provides StoreQueue with `crossPageWithHit`, `crossPageCanDeq`, high-page `paddr`, and `withSamePtr`, [StoreMisalignBuffer.scala:223](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:223), [Bundles.scala:277](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/Bundles.scala:277). When two DataBuffer slots are available, StoreQueue splits the original store mask/data using the high-page physical address from MAB; it defines `doDeq` as the conjunction of the cross-page condition, dequeuable condition, and `dataBuffer.io.enq(0).fire`, [StoreQueue.scala:1189](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1189). This is the only normal release confirmation for `s_block`.

~~~waveform-draw
{
  "signal": [
    {"name": "clk", "wave": "p............."},
    {"name": "enq.req.valid", "wave": "010..........."},
    {"name": "req_valid", "wave": "0.1..........."},
    {"name": "splitStoreReq.valid", "wave": "0...101......."},
    {"name": "splitStoreReq.ready", "wave": "1............."},
    {"name": "splitStoreResp.valid", "wave": "0....101......"},
    {"name": "cross4KBPageBoundary", "wave": "0.1..........."},
    {"name": "writeBack.valid", "wave": "0.........1...."},
    {"name": "s_block", "wave": "0..........1..."},
    {"name": "sqControl.doDeq", "wave": "0...........1.."},
    {"name": "req_valid after release", "wave": "0............1"}
  ],
  "config": {"hscale": 1}
}
~~~

<!-- 这是跨 4KB 页 Store 的符号波形：writeBack 之后仍不释放，直到 StoreQueue 侧 doDeq。真实时长由 fragment 的 TLB/DCache/replay 和 DataBuffer 的可用性决定；图不表达固定周期。 -->
This is a symbolic waveform for a store crossing a 4KB page: it is not released after `writeBack`, but only after `doDeq` from StoreQueue. Actual duration depends on fragment TLB/DCache/replay behavior and DataBuffer availability; the diagram does not express a fixed cycle count.

<!-- ### 6.4 Store 输出、向量接口和公共端口 -->
### 6.4 Store Outputs, Vector Interface, and Common Ports

<!-- StoreMAB 的标量 writeBack 和 vecWriteBack 分别在 [StoreMisalignBuffer.scala:598](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:598) 与 [StoreMisalignBuffer.scala:612](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:612) 生成。MemBlock 对 stOut(0) 的覆盖条件先检查其它标量 StoreUnit 输出、STA0 标量输出和两个 vector store 输出，[MemBlock.scala:1388](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1388)。故 StoreMAB 也必须等待公共出口可用，writeBack 发出不是 ROB commit，也不是已经进入 Sbuffer。 -->
StoreMAB generates scalar `writeBack` and `vecWriteBack` at [StoreMisalignBuffer.scala:598](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:598) and [StoreMisalignBuffer.scala:612](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:612), respectively. MemBlock's override condition for `stOut(0)` first checks other scalar StoreUnit outputs, the STA0 scalar output, and two vector-store outputs, [MemBlock.scala:1388](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1388). StoreMAB therefore must also wait for the common exit to be available; issuing `writeBack` is neither ROB commit nor confirmation that it has entered Sbuffer.

<!-- StoreMAB 的 toVecSplit.empty 由 req_valid 反相而来，并被 MemBlock 接到 vector store split 路径，[StoreMisalignBuffer.scala:188](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:188)、[MemBlock.scala:1613](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1613)。它只表达此单槽是否空闲；不能从该信号推导 StoreMAB 已完成全部架构可见写入。 -->
StoreMAB's `toVecSplit.empty` is the inverse of `req_valid` and is connected by MemBlock to the vector-store split path, [StoreMisalignBuffer.scala:188](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:188), [MemBlock.scala:1613](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1613). It expresses only whether this single slot is free; it cannot establish that StoreMAB has completed all architecturally visible writes.

<!-- ## 7. 端到端数据流、时序和吞吐 -->
## 7. End-to-End Data Flow, Timing, and Throughput

<!-- ### 7.1 有效流水线位置 -->
### 7.1 Effective Pipeline Positions

~~~mermaid
flowchart LR
  I[Original LSU request] --> G[LoadUnit S2/S3 or StoreUnit S1 eligibility]
  G -->|enq fire| B[one-slot MisalignBuffer]
  B -->|fragment 0 Decoupled| X[dedicated LDU1 or STA0 S0]
  X --> T[DTLB and PMP/PMA]
  T --> C[DCache, forwarding, miss or replay]
  C -->|Valid response| B
  B -->|fragment 1 Decoupled| X
  B -->|load: merge and wakeup| O[shared WB or vector merge]
  B -->|store: sq control and store output| Q[StoreQueue, DataBuffer and later store path]
  R[redirect or rollback] --> G
  R --> B
~~~

<!-- MAB 是分片控制器与数据重组器，不拥有 DCache array、TLB state、MSHR、cache set/bank/way 或独立的外部 AXI/TL 接口。相关译址和 cache 资源由复用的 LDU/STA 申请：Load fragment 的 vaddr/fullva 选择在 [LoadUnit.scala:692](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:692)，MemBlock 把 LDU 接到 TLB/PMP/DCache 的集成关系在 [MemBlock.scala:686](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:686) 与 [MemBlock.scala:880](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:880)。 -->
MAB is a fragment controller and data reassembler. It owns no DCache array, TLB state, MSHR, cache set/bank/way, or dedicated external AXI/TL interface. Translation and cache resources are requested by the reused LDU/STA: `vaddr/fullva` selection for a load fragment is at [LoadUnit.scala:692](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:692), while MemBlock's integration of LDU with TLB/PMP/DCache is at [MemBlock.scala:686](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:686) and [MemBlock.scala:880](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:880).

<!-- Load fragment 回到 LDU 时，MAB source 在 S0 的优先级高于普通 issue、LSQ replay、prefetch、vector 等候选，[LoadUnit.scala:290](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:290)。反过来，MAB 最终写回又低于普通 LDU1 输出。这是合理的资源复用取舍：前端注入优先保证子访问推进，末端输出仍保护常规端口协议。 -->
When a load fragment returns to LDU, the MAB source has higher S0 priority than ordinary issue, LSQ replay, prefetch, vector, and other candidates, [LoadUnit.scala:290](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:290). Conversely, MAB final writeback is below ordinary LDU1 output. This is a deliberate resource-reuse tradeoff: front-end injection prioritizes child-access progress, while the final output preserves ordinary-port protocol.

<!-- ### 7.2 定义清楚的时序边界 -->
### 7.2 Well-Defined Timing Boundaries

<!-- | 路径 | 起点 | 终点 | 固定部分 | 可变贡献项 | 吞吐瓶颈 |
| --- | --- | --- | --- | --- | --- |
| 标量 LoadMAB 正常路径 | enq.req.fire 锁存原 request。 | writeBack.fire。 | 两个 fragment 严格串行；正常合并后还有 LDU fake wakeup 的三拍寄存延迟。 | LDU1 S0 仲裁、TLB/PMP、DCache hit/miss、forwarding、replay、公共 WB 反压。 | 一个 req_valid 槽；任一时刻只有一条原始 MAB load。 |
| 向量 LoadMAB 正常路径 | enq.req.fire。 | vecWriteBack.fire。 | 两个 child 串行；没有 scalar fake wakeup。 | 同上，加上普通 vector 输出优先级。 | 单槽和 LDU1。 |
| StoreMAB 非跨页 | enq.req.fire。 | 标量或向量 Store 输出 fire 并清槽。 | 两个 child 串行。 | STA0、TLB/PMP/DCache/replay、公共 store 输出反压。 | 单槽和 STA0。 |
| StoreMAB 跨 4KB 页 | enq.req.fire。 | sqControl.doDeq 后清槽。 | 两个 child 串行；writeBack 后还有 s_block。 | 上述条件加 StoreQueue/DataBuffer 槽可用性。 | 单槽、STA0 和 DataBuffer 0 fire。 | -->
| Path | Start | End | Fixed portion | Variable contributors | Throughput bottleneck |
| --- | --- | --- | --- | --- | --- |
| Normal scalar LoadMAB path | `enq.req.fire` latches the original request. | `writeBack.fire`. | Two fragments are strictly serial; normal merging is followed by a three-cycle registered LDU fake-wakeup delay. | LDU1 S0 arbitration, TLB/PMP, DCache hit/miss, forwarding, replay, and common-WB backpressure. | One `req_valid` slot; only one original MAB load at a time. |
| Normal vector LoadMAB path | `enq.req.fire`. | `vecWriteBack.fire`. | Two children are serial; no scalar fake wakeup. | The above, plus ordinary vector-output priority. | Single slot and LDU1. |
| Non-cross-page StoreMAB | `enq.req.fire`. | Scalar or vector Store output fire clears the slot. | Two children are serial. | STA0, TLB/PMP/DCache/replay, and common-store-output backpressure. | Single slot and STA0. |
| StoreMAB crossing a 4KB page | `enq.req.fire`. | Slot is cleared after `sqControl.doDeq`. | Two children are serial; `s_block` follows `writeBack`. | The above plus StoreQueue/DataBuffer-slot availability. | Single slot, STA0, and DataBuffer 0 fire. |

<!-- 没有任何源码常量给出“从原 request 到写回固定 N 拍”的结论。特别是 DCache miss、TLB miss、replay 和公共端口争用都使端到端延迟可变。可明确的是事务完成 writeBack 的那个周期 req_valid 尚未被组合地变成可接收下一事务；清槽在时钟边沿后生效，所以新的原始 MAB request 至少在下一周期才可能接收。低 port 优先的 Load 仲裁还可能使持续的 port 0 流量使更高编号 port 长期失败；当前源码没有 round-robin/fairness 状态。 -->
No source constant establishes a fixed N-cycle latency from original request to writeback. In particular, DCache misses, TLB misses, replay, and common-port contention make end-to-end latency variable. It is clear that in the cycle completing `writeBack`, `req_valid` has not yet combinationally become ready to accept the next transaction; the slot clears after the clock edge, so a new original MAB request can be accepted no earlier than the next cycle. Lower-port-priority Load arbitration can also cause a continuing port-0 stream to make higher-numbered ports fail for a long time; the current source has no round-robin/fairness state.

<!-- ### 7.3 正常与恢复动态操作 -->
### 7.3 Normal and Recovery Dynamic Operations

<!-- **正常标量 load。** LDU S2/S3 判定原 request 为硬件可处理的跨 16B load，并通过年龄门控后与空槽握手。LoadMAB 在 s_split 构造两个 child，s_req 先向 LDU1 发第 0 片，s_resp 收到其正常结果后切到第 1 片。第二片正常后 buffer 合并字节，发 fake wakeup，得到完成令牌后以原 uop 通过共享 WB 写回。最后一次 writeBack.fire 才清 req_valid。 -->
**Normal scalar load.** LDU S2/S3 classifies the original request as a hardware-handled load crossing 16B and, after age gating, handshakes it into an empty slot. LoadMAB builds two children in `s_split`; `s_req` sends child 0 to LDU1 first, and `s_resp` moves to child 1 after receiving its normal result. After normal completion of child 1, the buffer merges bytes, sends a fake wakeup, and writes the original uop through shared WB after receiving the completion token. Only the final `writeBack.fire` clears `req_valid`.

<!-- **被阻塞或恢复的 load。** 若槽满或指令尚不满足 S3 的年龄条件，LDU 产生 misalign_nack，形成 C_MF replay；它不在 MAB 排队。若 fragment 返回 need_rep，buffer 保留当前 curPtr 和相应未发送位，回到 s_req 重发。若 redirect 命中原 uop，buffer 立即失效，并且后续 response 不应形成错误路径写回。若任一 fragment 的 exception/uncache 成立，buffer 停止剩余片的正常流程，携带异常信息进入 s_wb。 -->
**Blocked or recovering load.** If the slot is full or the instruction does not yet meet the S3 age condition, LDU produces `misalign_nack`, creating C_MF replay; it is not queued in MAB. If a fragment returns `need_rep`, the buffer retains the current `curPtr` and corresponding unsent bit, returning to `s_req` to resend it. If redirect hits the original uop, the buffer invalidates immediately, and subsequent responses must not cause wrong-path writeback. If any fragment has an exception/uncache condition, the buffer stops the normal flow for remaining fragments and enters `s_wb` carrying exception information.

<!-- **跨页 store。** StoreMAB 择老锁存原 store，依次让 STA0 处理两个 fragment。两个 fragment 完成后，非跨页请求在输出 fire 后释放；跨 4KB 页请求保留在 s_block，向 StoreQueue 提供高页 paddr 与控制信息，直到 DataBuffer 入队确认 doDeq。这条等待是 store data 侧的完成握手，而非“已 ROB commit”的同义词。 -->
**Cross-page store.** StoreMAB selects the oldest original store and lets STA0 process its two fragments in order. After both fragments complete, a non-cross-page request releases after output fire; a request crossing 4KB remains in `s_block`, providing StoreQueue with the high-page `paddr` and control information until DataBuffer enqueue confirms `doDeq`. This wait is a store-data-side completion handshake, not a synonym for “already ROB committed.”

<!--
## 8. 异常、特权、redirect 与架构可见性

### 8.1 CSR 和前级分类

| 事件或属性 | 生产者 | MAB 中的处理 | 架构可见性 |
| --- | --- | --- | --- |
| hd_misalign_ld_enable | CSR smblockctl bit 9。 | 决定 LDU 是否允许把适用 load 送 MAB。 | CSR 控制的执行策略；不是 buffer 内的状态。 |
| hd_misalign_st_enable | CSR smblockctl bit 8。 | 决定 STA 是否允许把适用 store 送 MAB。 | 同上。 |
| TLB/PMP/PMA/page/access 结果 | LDU/STA fragment 流水线。 | response 的 exception/global metadata 被 MAB 收集；异常路径停止正常合并/后续 fragment。 | 通过正常 LSU/ROB 异常路径处理；MAB 本身不提交 trap。 |
| PBMT NC 或 MMIO | LDU/STA 分类。 | MAB 将其视为全局异常/uncache 型终止；具体 Load/StoreUnit 中有不同 exception 映射。 | 不应仅凭 MAB 名字推断会形成外部 uncache 事务。 |
| redirect | backend。 | 当前 req 的 robIdx 需要 flush 时清槽和控制状态。 | 防止错误路径 request/result 继续写回。 |

LoadUnit S2 对 PBMT NC、MMIO 和异常有专门分类；其中 MAB 或 NC 相关条件会构造 loadAddrMisaligned，而真实 MMIO 会形成 access-fault 候选，[LoadUnit.scala:1340](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1340)。LoadMAB 在任一 response 带 mmio 或 nc 时会停止另一片、把该全局情况带到写回，并在该路径关闭 rfWen，[LoadMisalignBuffer.scala:213](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:213)、[LoadMisalignBuffer.scala:522](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:522)。StoreUnit 有对应的 storeAddrMisaligned/storeAccessFault 分类，[StoreUnit.scala:462](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:462)。

这两个层次的具体 exception bit 组合不应被过度简化为“任意 MMIO 一定等于某一个 trap”。可靠结论是：MAB fragment 不能把这种特殊属性当正常 data 完成，当前代码将其终止并交由既有 LSU 异常链路处理。LoadQueueUncache 对已有异常请求有排除条件，[LoadQueueUncache.scala:338](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:338)，因此分片 NC/MMIO 情况不应在没有额外验证时描述为普通的外部 uncache 请求。

### 8.2 未启用的异常覆盖接口

MemBlock 把两类 MAB 的 overwriteExpBuf 接到异常地址选择优先级中，[MemBlock.scala:1871](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1871)、[MemBlock.scala:1904](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1904)。但两个 MAB 都明确执行：
-->
## 8. Exceptions, Privilege, Redirect, and Architectural Visibility

### 8.1. CSR and Upstream Classification

| Event or Attribute | Producer | Handling in MAB | Architectural Visibility |
| --- | --- | --- | --- |
| `hd_misalign_ld_enable` | CSR `smblockctl` bit 9. | Determines whether the LDU may send an applicable load to MAB. | A CSR-controlled execution policy, not state inside the buffer. |
| `hd_misalign_st_enable` | CSR `smblockctl` bit 8. | Determines whether the STA may send an applicable store to MAB. | Same as above. |
| TLB/PMP/PMA/page/access result | LDU/STA fragment pipeline. | MAB collects the response exception/global metadata; the exception path stops normal merging/later fragments. | Handled through the normal LSU/ROB exception path; MAB does not itself commit a trap. |
| PBMT NC or MMIO | LDU/STA classification. | MAB treats it as global exception/uncache-style termination; the LoadUnit and StoreUnit use different exception mappings. | The MAB name alone must not be used to infer an external uncache transaction. |
| redirect | Backend. | Clears the slot and control state when the current request `robIdx` needs flush. | Prevents wrong-path requests/results from continuing to write back. |

LoadUnit S2 has dedicated classification for PBMT NC, MMIO, and exceptions. MAB- or NC-related conditions produce `loadAddrMisaligned`, while actual MMIO produces an access-fault candidate; see [LoadUnit.scala:1340](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1340). When either LoadMAB response carries mmio or nc, LoadMAB stops the other fragment, carries that global condition to writeback, and disables `rfWen` on that path; see [LoadMisalignBuffer.scala:213](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:213) and [LoadMisalignBuffer.scala:522](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:522). StoreUnit has corresponding `storeAddrMisaligned`/`storeAccessFault` classification; see [StoreUnit.scala:462](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:462).

The concrete combinations of exception bits at these two levels must not be oversimplified as any MMIO always being one particular trap. The reliable conclusion is that an MAB fragment cannot treat such a special attribute as a normal data completion; the current code terminates it and passes it to the established LSU exception chain. `LoadQueueUncache` excludes requests that already have an exception; see [LoadQueueUncache.scala:338](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:338). A split NC/MMIO case therefore must not be described as an ordinary external uncache request without further verification.

### 8.2. Disabled Exception-Override Interface

MemBlock connects both MAB types' `overwriteExpBuf` into exception-address selection priority; see [MemBlock.scala:1871](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1871) and [MemBlock.scala:1904](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1904). However, both MABs explicitly execute:

~~~scala
io.overwriteExpBuf.valid := false.B
~~~

<!--
Load 在 [LoadMisalignBuffer.scala:641](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:641)，Store 在 [StoreMisalignBuffer.scala:669](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:669)。所以正确描述是“候选 payload 和 MemBlock 选择线路存在，但当前提交的有效 MAB override 路径静态不可达”，而不是“跨页 MAB 会覆盖异常地址”。Load 的 flushLdExpBuff 也未找到模块外的行为性消费者。

### 8.3 Difftest、调试与外部协议边界

在两个 MisalignBuffer.scala 中未搜索到 Difftest 产生器。它们保存的是微体系结构中间状态；直接可见的 Difftest event 在更后的 commit/store 数据路径产生。例如 Load 的 DiffLoadEvent 由 ROB commit 侧输出，[Rob.scala:1584](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:1584)；StoreQueue 在 DataBuffer 入队、ncReq/mmioReq 条件下准备 DiffStore 输入，[StoreQueue.scala:1408](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1408)。因此 MAB 只会经正常 LSU、SQ/Sbuffer 或 ROB 的后续路径间接影响 Difftest，不能称它“直接发 Difftest”。

两个 MAB 也没有直接的 AIA、IOPMP、AXI、TileLink master/slave 接口。本任务在它们、LoadUnit 和 StoreUnit 的相关接口中未见这些通道；它们的外部存储系统影响只能经 DCache/uncache/LSQ 下游间接体现。debug/trigger 等 eligibility 条件属于 LDU/STA 前级分类，不能伪造为 MAB 内部特权状态。
-->
For Load, this is in [LoadMisalignBuffer.scala:641](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:641); for Store, it is in [StoreMisalignBuffer.scala:669](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:669). The correct description is that candidate payloads and the MemBlock-selection wires exist, but the effective MAB override path in this commit is statically unreachable, not that a cross-page MAB overrides the exception address. No behavioral consumer of the Load `flushLdExpBuff` was found outside the module.

### 8.3. Difftest, Debug, and External-Protocol Boundary

No Difftest producer was found in either MisalignBuffer.scala. They retain intermediate microarchitectural state; directly visible Difftest events are produced later on the commit/store-data path. For example, the load `DiffLoadEvent` is emitted on the ROB-commit side; see [Rob.scala:1584](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:1584). StoreQueue prepares DiffStore input when the DataBuffer enqueues and `ncReq/mmioReq` conditions hold; see [StoreQueue.scala:1408](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1408). MAB can therefore affect Difftest only indirectly through subsequent normal LSU, SQ/Sbuffer, or ROB paths; it must not be described as directly emitting Difftest.

Neither MAB has a direct AIA, IOPMP, AXI, or TileLink master/slave interface. This analysis found none of those channels in their relevant interfaces or those of LoadUnit and StoreUnit. Their external-memory-system effects can appear only indirectly through DCache/uncache/LSQ downstream paths. Eligibility conditions such as debug/trigger are upstream LDU/STA classification and must not be represented as privileged state inside MAB.

<!--
## 9. 跨边界代码解析

| 边界 | 已证实的代码路径 | 分片、状态与恢复 | 不能过度推断的部分 | 验证焦点 |
| --- | --- | --- | --- | --- |
| 16B 对齐窗口 | Load/Store MAB 通过低 5 位 bit 4 和访问末字节比较决定跨界。 | 两个 child 严格串行；分别等待 response，正常后合并或输出。 | 它不是任意宽度的通用 split engine。 | LH/LW/LD/SD 各类低地址偏移，检查 child 地址、mask、数据重构。 |
| 虚拟 4KB 页 | LoadMAB 本身不计算 page bit 12；child 重新进 TLB。TLB 对 cross-page vaddr/fullva 有专门选择，[TLB.scala:397](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:397)。StoreMAB 显式算 bit 12，并经 SQ 交付高页 paddr。 | load 让两个 child 各自译址；store 在 s_block 等高页 dataBuffer 写入确认。 | 不能因 LoadMAB 不显式判页就断言它无法处理跨页；也不能把 overwriteExpBuf 当有效覆盖。 | LD at address ending ffb/fff，令高页 TLB/PMP fault，确认 fault 地址与停止剩余请求。 |
| 64B DCache line | DCache blockBytes 为 64B，[DCacheWrapper.scala:53](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:53)。MAB 只按 16B 切，child 经普通 LoadUnit/StoreUnit 进 DCache。 | 例如原地址靠近 line offset 3f 时，两个 child 可能落到不同 cache line，并串行请求。 | MAB 不看 set/way/MSHR，源码不能证明两个 child 的 miss 是否共享 MSHR 或合并 refill。 | line offset 0f、1f、2f、3f 下 hit/miss 组合，观察两 child 的 cache 请求与回收顺序。 |
| MMIO/uncache | LDU/STA 分类后，MAB 在 response 看到 mmio/nc 即走全局终止。 | 停止正常合并/余片，并按异常路径输出。 | 不可把它写成普通 UncacheEntry/AXI transaction；需沿 LoadQueueUncache/Sbuffer 再验证。 | 将低或高 child 映射到 NC/MMIO，检查没有错误 normal WB/normal store 数据路径。 |
| redirect 与 replay | MAB req.robIdx.needFlush 清槽；fragment need_rep 回 s_req。 | redirect 取消所有尚未完成片，replay 重发当前片。 | 不能用 valid-only response 假定后到 response 会被握手阻塞。 | fragment 0 后 redirect；fragment 1 replay；response 与 redirect 相邻周期。 |

跨 16B 并不必然跨 64B cache line，也不必然跨 4KB page；反之，跨页或跨 line 的具体地址组合应由 child 地址、TLB 和 DCache 实际观察决定。这个区分是理解 MAB 的关键：它只负责 16B 元素边界的拆分和控制，不是 page splitter 或 cache-line splitter。
-->
## 9. Cross-Boundary Code Analysis

| Boundary | Established Code Path | Fragmentation, State, and Recovery | What Must Not Be Over-Inferred | Verification Focus |
| --- | --- | --- | --- | --- |
| 16B alignment window | Load/Store MAB use bit 4 of the low five bits and the last accessed byte to determine crossing. | The two children are strictly serial, each waits for its response, and normal completion merges or outputs them. | It is not a general split engine for arbitrary widths. | Test low-address offsets for LH/LW/LD/SD; check child addresses, masks, and data reconstruction. |
| Virtual 4KB page | LoadMAB itself does not calculate page bit 12; children enter TLB again. TLB has a dedicated cross-page `vaddr/fullva` selection; see [TLB.scala:397](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:397). StoreMAB explicitly calculates bit 12 and delivers the high-page paddr through SQ. | Load translates its two children independently; Store waits in `s_block` for high-page DataBuffer-write confirmation. | The lack of an explicit page test in LoadMAB does not prove it cannot handle a cross-page case; neither can `overwriteExpBuf` be treated as an effective override. | Use LD at an address ending in ffb/fff, inject a high-page TLB/PMP fault, and confirm the fault address and remaining-request stop. |
| 64B DCache line | DCache `blockBytes` is 64B; see [DCacheWrapper.scala:53](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:53). MAB splits only on 16B, and children enter DCache through the ordinary LoadUnit/StoreUnit. | For example, when the original address is near line offset 3f, the two children can fall in distinct cache lines and issue serially. | MAB does not inspect set/way/MSHR, so source cannot prove whether the two child misses share an MSHR or merge a refill. | Combine hit/miss at line offsets 0f, 1f, 2f, and 3f; observe child cache requests and retirement order. |
| MMIO/uncache | After LDU/STA classification, a response with mmio/nc sends MAB to global termination. | Stops ordinary merging/remaining children and outputs through the exception path. | It must not be represented as an ordinary UncacheEntry/AXI transaction; follow LoadQueueUncache/Sbuffer for further verification. | Map the lower or upper child to NC/MMIO and check that no erroneous normal WB/normal-store-data path occurs. |
| redirect and replay | `MAB req.robIdx.needFlush` clears the slot; fragment `need_rep` returns to `s_req`. | Redirect cancels all unfinished children; replay resends the current child. | A Valid-only response must not be assumed to be handshake-blocked on late arrival. | Redirect after fragment 0; replay fragment 1; place response and redirect in adjacent cycles. |

Crossing 16B does not necessarily cross a 64B cache line or a 4KB page. Conversely, the concrete address combination for a cross-page or cross-line case must be established by observing the child addresses, TLB, and DCache. This distinction is central to understanding MAB: it controls splitting at a 16B element boundary, not page splitting or cache-line splitting.

<!--
## 10. 场景矩阵

| 场景 | 触发条件 | 资源和仲裁 | 状态更新 | 重试、flush 或恢复 | 最终消费者 | 证据 |
| --- | --- | --- | --- | --- | --- | --- |
| 两个 Load 同拍候选 | LDU0、LDU1 或 LDU2 同拍 enq.valid，且槽空。 | Load 固定最低 port 号胜出；没有 fairness 指针。 | 胜者 req 被锁存并置 req_valid。 | 败者本拍没有 ready，应由上游处理。 | 获胜 load 的 s_split。 | [LoadMisalignBuffer.scala:148](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:148) |
| Load 单槽满 | req_valid=1 时新 load 到达。 | 所有 enq.ready 不可接受。 | 当前事务保持。 | LDU S3 形成 misalign_nack/C_MF replay；当前 LoadQueueReplay 未行为性消费 loadMisalignFull。 | LDU replay 路径。 | [LoadUnit.scala:1614](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1614)、[LoadQueueReplay.scala:366](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:366) |
| Load 两片正常返回 | 两个 child 都无 exception/uncache/replay。 | LDU1 source 0 逐片执行。 | response 写入两个槽，随后数据合并。 | 标量再经历 fake wakeup；向量直接 s_wb。 | 共享标量 WB 或 vector merge。 | [LoadMisalignBuffer.scala:540](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:540) |
| Load child replay | 当前 splitLoadResp 标记 need_rep。 | 不占新的 MAB 槽。 | 当前未发送位不清，curPtr 保持。 | 回 s_req 重发同一 child。 | LDU1。 | [LoadMisalignBuffer.scala:213](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:213) |
| Load child exception/NC/MMIO | 当前 response 表示 exception 或全局特殊属性。 | 停止正常片段序列。 | 保存 globalException/globalUncache，清未发片。 | 进入 s_wb；不继续正常 data merge。 | 常规 LSU/ROB exception 链。 | [LoadMisalignBuffer.scala:216](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:216) |
| Load redirect | req 的 robIdx 被 redirect flush。 | redirect 高优先级。 | 清 req_valid、状态、指针和完成标志。 | 错路径 response 不得写回。 | backend recovery。 | [LoadMisalignBuffer.scala:610](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:610) |
| Load 写回与普通 LDU1 冲突 | MAB WB 条件和 LDU1 ldout.valid 同拍。 | MemBlock 选普通 LDU1，MAB ready 低。 | MAB 应保留到可写回；标量 fake response 的有效保持是待验证条件。 | 检查是否存在不可达性约束。 | backend writeback。 | [MemBlock.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:529) |
| 两个 Store 同拍候选 | STA0、STA1 同拍 enq.valid，且槽空。 | Store selectOldest 选 ROB/uop 最老。 | 最老 request 锁存。 | 未选者不进入 MAB。 | s_split。 | [StoreMisalignBuffer.scala:71](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:71) |
| Store child replay | STA0 回传 need_rep。 | 一个 MAB 槽和当前 curPtr 保持。 | 未发送位不清。 | 回 s_req 重发当前 child。 | STA0。 | [StoreMisalignBuffer.scala:233](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:233) |
| Store 跨 4KB 页 | highAddress 跨 bit 12，两个 child 已处理。 | StoreQueue/DataBuffer 参与。 | writeBack 后进入 s_block。 | 仅 sqControl.doDeq 释放。 | StoreQueue/DataBuffer 后续路径。 | [StoreMisalignBuffer.scala:233](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:233)、[StoreQueue.scala:1189](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1189) |
-->
## 10. Scenario Matrix

| Scenario | Trigger Condition | Resources and Arbitration | State Update | Retry, Flush, or Recovery | Final Consumer | Evidence |
| --- | --- | --- | --- | --- | --- |
| Two same-cycle Load candidates | LDU0, LDU1, or LDU2 have `enq.valid` in the same cycle and the slot is empty. | Load uses fixed lowest-port-number priority; it has no fairness pointer. | The winner request is latched and sets `req_valid`. | The loser has no `ready` that cycle and must be handled upstream. | `s_split` for the winning load. | [LoadMisalignBuffer.scala:148](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:148) |
| Full Load single slot | A new load arrives while `req_valid=1`. | All `enq.ready` signals cannot accept it. | The current transaction remains. | LDU S3 creates `misalign_nack`/C_MF replay; the current LoadQueueReplay has no behavioral consumption of `loadMisalignFull`. | LDU replay path. | [LoadUnit.scala:1614](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1614), [LoadQueueReplay.scala:366](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:366) |
| Normal return of two Load children | Both children have no exception/uncache/replay. | LDU1 source 0 executes them one at a time. | Responses write both slots, followed by data merge. | Scalar load also passes through fake wakeup; vector load goes directly to `s_wb`. | Shared scalar WB or vector merge. | [LoadMisalignBuffer.scala:540](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:540) |
| Load-child replay | Current `splitLoadResp` marks `need_rep`. | Does not occupy a new MAB slot. | Current unsent bit is not cleared and `curPtr` holds. | Return to `s_req` to resend the same child. | LDU1. | [LoadMisalignBuffer.scala:213](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:213) |
| Load-child exception/NC/MMIO | Current response reports an exception or global special attribute. | Stops the normal fragment sequence. | Retains `globalException/globalUncache` and clears unsent children. | Enters `s_wb`; normal data merge does not continue. | Normal LSU/ROB exception chain. | [LoadMisalignBuffer.scala:216](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:216) |
| Load redirect | The request `robIdx` is redirect-flushed. | Redirect has high priority. | Clears `req_valid`, state, pointers, and completion flags. | A wrong-path response must not write back. | Backend recovery. | [LoadMisalignBuffer.scala:610](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:610) |
| Load writeback conflicts with ordinary LDU1 | MAB WB condition and LDU1 `ldout.valid` occur in the same cycle. | MemBlock selects ordinary LDU1 and lowers MAB ready. | MAB should remain until writeback is possible; validity retention for the scalar fake response is a verification condition. | Check whether an unreachability constraint exists. | Backend writeback. | [MemBlock.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:529) |
| Two same-cycle Store candidates | STA0 and STA1 have `enq.valid` in the same cycle and the slot is empty. | Store `selectOldest` chooses the oldest ROB/uop. | The oldest request is latched. | The unselected request does not enter MAB. | `s_split`. | [StoreMisalignBuffer.scala:71](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:71) |
| Store-child replay | STA0 returns `need_rep`. | One MAB slot and current `curPtr` are retained. | Unsent bit is not cleared. | Return to `s_req` to resend the current child. | STA0. | [StoreMisalignBuffer.scala:233](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:233) |
| Store crosses a 4KB page | `highAddress` crosses bit 12 after both children are processed. | StoreQueue/DataBuffer participate. | Enters `s_block` after `writeBack`. | Only `sqControl.doDeq` releases it. | Subsequent StoreQueue/DataBuffer path. | [StoreMisalignBuffer.scala:233](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:233), [StoreQueue.scala:1189](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1189) |

<!--
## 11. 验证特别注意

以下项目是针对本模块结构的验证清单。每一项都应使用展开后的 RTL、仿真波形或形式属性闭环，而不是只检查 Scala 文本。

| ID | 不变量 | 激励 | 预期观察 | checker 或覆盖 | 源码依据 |
| --- | --- | --- | --- | --- | --- |
| MAB-V01 | LoadMAB 任何时刻最多有一个原始 request。 | 连续多拍向三个 enq 口同时施压。 | req_valid 高期间所有新 request 不被接受；完成 fire 后下拍才可再入。 | assert accepted_count minus release_count is in 0..1；cover 满槽再释放。 | [LoadMisalignBuffer.scala:143](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:143) |
| MAB-V02 | Load 同拍仲裁始终低 port 优先，Store 始终选 oldest。 | 分别构造三路 load 与两路 store 同拍 valid，改变 robIdx/uopIdx。 | Load 选最小 port；Store 选年龄最老而非 port 最小。 | functional cover 所有 winner；assert selected request payload。 | [LoadMisalignBuffer.scala:148](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:148)、[StoreMisalignBuffer.scala:71](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:71) |
| MAB-V03 | 两个 child 严格串行，当前 child 未 response 前不能发下一 child。 | 对 fragment 0 的 TLB/DCache response 注入延迟或 replay。 | splitReq 的 child 1 只在 child 0 正常完成后出现；replay 重发同一 curPtr。 | scoreboarding child index、ROB ID、vaddr/mask；cover 0/1/replay。 | [LoadMisalignBuffer.scala:192](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:192)、[StoreMisalignBuffer.scala:233](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:233) |
| MAB-V04 | load 合并字节等于原地址范围的存储器字节序。 | LH/LW/LD 对所有跨 16B 低地址偏移，低/高 fragment 返回不同可识别 data。 | rdata 与按地址抽取的期望字节序、符号/零扩展一致。 | byte-level reference model；cross 每个 case 分支。 | [LoadMisalignBuffer.scala:314](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:314)、[LoadMisalignBuffer.scala:540](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:540) |
| MAB-V05 | redirect 后旧 ROB request 不产生写回。 | fragment 0 后、fragment 1 前、s_wb 前分别注入 redirect。 | req_valid 清零；无旧 robIdx 的 writeBack、vecWriteBack 或 sq doDeq。 | SVA/Chisel assert：flush 后直到新 enq 不得输出旧 uop；三处 cover。 | [LoadMisalignBuffer.scala:610](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:610)、[StoreMisalignBuffer.scala:641](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:641) |
| MAB-V06 | fragment exception/NC/MMIO 终止正常片段/数据路径。 | 低片或高片分别注入 page/access/PBMT-NC/MMIO 类 response。 | 未完成片不继续 normal merge；rfWen 与异常输出符合 LDU/STA 规则。 | cross 低片/高片、load/store、exception/uncache 覆盖。 | [LoadMisalignBuffer.scala:213](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:213)、[StoreMisalignBuffer.scala:542](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:542) |
| MAB-V07 | 标量 fake wakeup 不会因与普通 LDU1 输出冲突而永久停留。 | 人为制造 fake response 与 LDU1 ldout.valid、WB ready 的各种同拍组合。 | 要么证明组合不可达，要么确认 buffer 可重新获得有效 WB fire。 | liveness assertion：进入 s_wb 后最终退出；波形观察 valid/ready。 | [LoadMisalignBuffer.scala:561](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:561)、[MemBlock.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:529) |
| MAB-V08 | vector MAB 在普通 vector output 占用时不丢数据。 | 同拍强制普通 LDU vector output 和 MAB vecWriteBack 候选。 | MAB vecWriteBack.valid 因 loadVecOutValid 被抑制并保持 s_wb，直到可发。 | assert vec MAB fire 必有被选中 payload；cover 冲突后成功。 | [LoadMisalignBuffer.scala:581](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:581)、[MemBlock.scala:1629](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1629) |
| MAB-V09 | Store 跨 4KB 页写回后必须等 StoreQueue data buffer 确认。 | 用跨页 SD/SW，使 DataBuffer 空间先不可用再可用。 | s_block 保持，直到 doDeq；高页 paddr/mask/data 与两个 DataBuffer 写入对应。 | assert s_block implies req_valid；cover backpressure then doDeq。 | [StoreMisalignBuffer.scala:233](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:233)、[StoreQueue.scala:1189](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1189) |
| MAB-V10 | 16B、64B line 与 4KB page 三类边界互不混淆。 | 在 offset 0f、1f、2f、3f、ffb、fff 组合上运行 hit/miss/fault。 | MAB 只按 16B 分片；TLB/DCache/SQ 对 line/page 产生各自可观测请求/异常。 | address class coverage 加 cache/TLB event scoreboarding。 | [LoadMisalignBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:292)、[TLB.scala:397](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:397) |
-->
## 11. Verification Considerations

The following is a verification checklist for this module's structure. Every item should be closed with elaborated RTL, simulation waveforms, or formal properties, rather than checking Scala text alone.

| ID | Invariant | Stimulus | Expected Observation | Checker or Coverage | Source Basis |
| --- | --- | --- | --- | --- | --- |
| MAB-V01 | LoadMAB has at most one original request at any time. | Apply simultaneous pressure to all three enqueue ports over consecutive cycles. | While `req_valid` is high, no new request is accepted; reentry is possible only in the cycle after completion fire. | Assert `accepted_count minus release_count is in 0..1`; cover full-slot then release. | [LoadMisalignBuffer.scala:143](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:143) |
| MAB-V02 | Load same-cycle arbitration always favors the lower port; Store always chooses the oldest. | Build three simultaneous valid loads and two simultaneous valid stores, varying `robIdx/uopIdx`. | Load selects the smallest port; Store selects the oldest age rather than the smallest port. | Functional cover for every winner; assert selected-request payload. | [LoadMisalignBuffer.scala:148](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:148), [StoreMisalignBuffer.scala:71](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:71) |
| MAB-V03 | Two children are strictly serial: the next child cannot issue until the current child responds. | Inject TLB/DCache-response delay or replay for fragment 0. | Child 1 appears on `splitReq` only after child 0 completes normally; replay resends the same `curPtr`. | Scoreboard child index, ROB ID, `vaddr/mask`; cover 0/1/replay. | [LoadMisalignBuffer.scala:192](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:192), [StoreMisalignBuffer.scala:233](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:233) |
| MAB-V04 | Merged load bytes equal memory byte order over the original address range. | For LH/LW/LD at every 16B-crossing low-address offset, return distinct recognizable data for low/high fragments. | `rdata` matches the expected byte order extracted by address, including sign/zero extension. | Byte-level reference model; cross every case branch. | [LoadMisalignBuffer.scala:314](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:314), [LoadMisalignBuffer.scala:540](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:540) |
| MAB-V05 | An old ROB request produces no writeback after redirect. | Inject redirect after fragment 0, before fragment 1, and before `s_wb`. | Clear `req_valid`; no old-`robIdx` `writeBack`, `vecWriteBack`, or SQ `doDeq`. | SVA/Chisel assertion: after flush and until new enqueue, no old uop may be output; cover all three points. | [LoadMisalignBuffer.scala:610](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:610), [StoreMisalignBuffer.scala:641](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:641) |
| MAB-V06 | Fragment exception/NC/MMIO terminates the normal fragment/data path. | Inject page/access/PBMT-NC/MMIO-class responses separately for low and high children. | An unfinished child does not continue normal merge; `rfWen` and exception output follow LDU/STA rules. | Cross low/high child, load/store, exception/uncache coverage. | [LoadMisalignBuffer.scala:213](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:213), [StoreMisalignBuffer.scala:542](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:542) |
| MAB-V07 | Scalar fake wakeup does not remain permanently stalled because it conflicts with ordinary LDU1 output. | Create all same-cycle combinations of fake response, LDU1 `ldout.valid`, and WB ready. | Either prove the combination unreachable or confirm that the buffer can regain an effective WB fire. | Liveness assertion: eventually exit after entering `s_wb`; observe valid/ready in waveforms. | [LoadMisalignBuffer.scala:561](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:561), [MemBlock.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:529) |
| MAB-V08 | Vector MAB does not lose data while ordinary vector output occupies the port. | Force ordinary LDU vector output and an MAB `vecWriteBack` candidate in the same cycle. | MAB `vecWriteBack.valid` is suppressed by `loadVecOutValid` and it remains in `s_wb` until it can issue. | Assert every vector-MAB fire has the selected payload; cover conflict followed by success. | [LoadMisalignBuffer.scala:581](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:581), [MemBlock.scala:1629](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1629) |
| MAB-V09 | After cross-4KB-page Store writeback, Store must wait for StoreQueue DataBuffer confirmation. | Use cross-page SD/SW and make DataBuffer space unavailable, then available. | `s_block` holds until `doDeq`; high-page `paddr/mask/data` correspond to two DataBuffer writes. | Assert `s_block implies req_valid`; cover backpressure then `doDeq`. | [StoreMisalignBuffer.scala:233](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:233), [StoreQueue.scala:1189](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1189) |
| MAB-V10 | The three boundary types, 16B, 64B line, and 4KB page, are not confused. | Run hit/miss/fault combinations at offsets 0f, 1f, 2f, 3f, ffb, and fff. | MAB splits only on 16B; TLB/DCache/SQ produce their respective observable requests/exceptions for line/page. | Address-class coverage plus cache/TLB-event scoreboarding. | [LoadMisalignBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:292), [TLB.scala:397](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:397) |

<!--
## 12. 当前代码的边界和未确认项

| 观察 | 已确认事实 | 不能得出的结论 | 下一步证据 |
| --- | --- | --- | --- |
| response 身份 | s_resp 的状态推进看 splitResp.valid，数据写入再比 robIdx；没有 child-id 比较。 | 不能仅凭接口就证明错误 response 绝不出现。 | LDU1 专用端口波形或 assertion：valid response 的 robIdx 必匹配活动 MAB。 |
| scalar wakeup | fake response 是 Valid 脉冲，标量 WB 与 LDU1 输出共享端口。 | 不能仅凭静态结构断言已发生死锁。 | 覆盖 MAB-V07 或形式 liveness。 |
| vector 输出 | vecWriteBack.valid 显式受 loadVecOutValid 抑制。 | 不能把 scalar 的条件风险直接复制到 vector 路径。 | MAB-V08 的仲裁保持检查。 |
| exception override | 两边 payload/优先级连线存在，但 valid 硬置 false。 | 不能称当前 MAB 覆盖 exception address。 | 若后续版本打开 valid，再重新追踪 MemBlock 异常 mux。 |
| 具名局部信号 | getMask、new128Load/new128Store、needFlushPipe、unWriteStores 等在本文件中有未消费或未形成可观察输出的定义。 | 不能按名称把它们叙述成有效硬件功能。 | elaborated RTL cone 或后续提交差异。 |
| cache 资源 | child 经普通 LDU/STA/DCache 路径。 | 不能从 MAB 文件证明 child miss 的 MSHR 分配、coalescing 或 refill 关系。 | DCache 波形、MSHR 事件、cache miss 测试。 |
-->
## 12. Current-Code Boundaries and Open Questions

| Observation | Confirmed Fact | Conclusion That Cannot Be Drawn | Next Evidence |
| --- | --- | --- | --- |
| Response identity | `s_resp` state advancement uses `splitResp.valid`, then data write compares `robIdx`; no child-ID comparison exists. | The interface alone cannot prove that an incorrect response can never occur. | Dedicated LDU1-port waveform or assertion: a valid response `robIdx` must match the active MAB. |
| Scalar wakeup | Fake response is a Valid pulse, and scalar WB shares a port with LDU1 output. | Static structure alone cannot establish that a deadlock has occurred. | Cover MAB-V07 or use formal liveness. |
| Vector output | `vecWriteBack.valid` is explicitly inhibited by `loadVecOutValid`. | The scalar conditional risk cannot simply be copied onto the vector path. | MAB-V08 arbitration-retention check. |
| Exception override | Payload/priority wires exist on both sides, but valid is hardwired false. | The current MAB cannot be said to override the exception address. | If a later version enables valid, trace the MemBlock exception mux again. |
| Named local signals | `getMask`, `new128Load/new128Store`, `needFlushPipe`, `unWriteStores`, and others are defined in this file without consumption or observable output. | Names alone cannot describe them as effective hardware functions. | Elaborated-RTL cone or later-commit delta. |
| Cache resources | Children use ordinary LDU/STA/DCache paths. | The MAB files cannot prove MSHR allocation, coalescing, or refill relationships for child misses. | DCache waveform, MSHR events, and cache-miss tests. |

<!--
## 13. 总结

在 kunminghu-v2 的 e12436c7 提交中，LoadMisalignBuffer 与 StoreMisalignBuffer 是 MemBlock 内各一个、容量为 1 的特殊访存控制器。它们都把一条跨 16B 的原请求构造成至多两个对齐 child，并且严格串行地借用普通 LSU 管线；但 Load 以固定 port 优先、合并数据并使用标量 fake wakeup，而 Store 以 oldest 仲裁、显式处理 4KB 跨页、并在 s_block 等待 StoreQueue 的 DataBuffer 确认。

最重要的资源结论是单槽和共享专用执行端口：Load 复用 LDU1、Store 复用 STA0，最终写回仍受普通端口优先级约束。最重要的边界结论是 16B 分片不等于 64B cache line 或 4KB page 分片；页、TLB、cache miss、MSHR 和外部 uncache 的行为须沿子请求的下游链路验证。最重要的异常结论是两个 MAB 的 exception override payload 当前未启用，不能写成有效异常地址覆盖。

后续动态分析应首先覆盖：双 child 的地址/数据 scoreboarding、fragment replay、redirect、跨页高片 fault、Store s_block/DataBuffer 背压，以及标量 fake wakeup 与普通 LDU1 输出的潜在同拍竞争。这样才能把本次源码级结论推进到可观测的 RTL/FST 证据。
-->
## 13. Summary

At `kunminghu-v2` commit `e12436c7`, LoadMisalignBuffer and StoreMisalignBuffer are each a one-entry special memory-access controller inside MemBlock. Both transform one original request crossing 16B into at most two aligned children and borrow the ordinary LSU pipeline strictly serially. Load uses fixed port priority, merges data, and uses scalar fake wakeup; Store uses oldest arbitration, explicitly handles 4KB-page crossing, and waits in `s_block` for StoreQueue DataBuffer confirmation.

The most important resource conclusion is the single slot and shared dedicated execution ports: Load reuses LDU1 and Store reuses STA0, while final writeback remains constrained by ordinary-port priority. The key boundary conclusion is that 16B splitting is not 64B cache-line or 4KB-page splitting; page, TLB, cache-miss, MSHR, and external-uncache behavior must be verified along the downstream child-request path. The key exception conclusion is that exception-override payloads for both MABs are currently disabled and must not be described as effective exception-address override.

Follow-on dynamic analysis should first cover address/data scoreboarding for both children, fragment replay, redirect, a high-child fault across a page, Store `s_block`/DataBuffer backpressure, and the possible same-cycle contention between scalar fake wakeup and ordinary LDU1 output. That is necessary to advance these source-level conclusions to observable RTL/FST evidence.
