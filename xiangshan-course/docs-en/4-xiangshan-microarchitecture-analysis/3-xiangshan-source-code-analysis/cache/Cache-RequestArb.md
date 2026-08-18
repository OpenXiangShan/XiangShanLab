<!--
# Cache-RequestArb：Kunminghu V2 CoupledL2 请求仲裁器源码分析

> 结论先行：Kunminghu V2 默认配置启用 CHI，因此本文分析的有效实体是每个 `coupledL2/tl2chi.Slice` 内的 `coupledL2.RequestArb`。它不是轮转仲裁器：已被阻塞条件筛掉的候选之外，固定优先级为 **已暂存的 MSHR 任务 > C > B > A**；其中“MSHR > C/B/A”只适用于已经进入 `mshr_task_s1` 寄存器的任务，新来的 MSHR 任务先走 S0，并且最多可与同拍的一个获胜 C/B/A channel task 一起准入。它向 Directory 发起 S1 读、在 S2 以 `ValidIO` 把任务交给 MainPipe，并为 Refill/Release MSHRBuffer 选择读索引。HuanCun 没有这个 `RequestArb` 类，且 V2+CHI 不实例化 HuanCun L3；不能把 HuanCun 的 `MSHRAlloc` 或 `openLLC.RequestArb` 混入本章。
-->

# Cache-RequestArb: Source Analysis of the Kunminghu V2 CoupledL2 Request Arbiter

> **Conclusion first:** Kunminghu V2's default configuration enables CHI, so the effective entity analyzed here is `coupledL2.RequestArb` inside every `coupledL2/tl2chi.Slice`. It is not a round-robin arbiter: after candidates excluded by blocking conditions are removed, its fixed priority is **held MSHR task > C > B > A**. "MSHR > C/B/A" applies only to a task that has already entered the `mshr_task_s1` register. A newly arriving MSHR task first traverses S0 and can be admitted alongside one winning C/B/A channel task in the same cycle. The module issues S1 reads to Directory, hands tasks to MainPipe at S2 using `ValidIO`, and chooses read indices for Refill/Release MSHRBuffers. HuanCun has no such `RequestArb` class, and V2+CHI does not instantiate HuanCun L3; do not mix in HuanCun's `MSHRAlloc` or `openLLC.RequestArb`.

<!--
## 1. 范围、版本与有效实现

### 1.1 分析基线

| 项目 | 本文采用的基线 | 处理方式 |
| --- | --- | --- |
| XiangShan | URL: `https://github.com/OpenXiangShan/XiangShan.git`；`/home/yanyusong/xs-memory-env/XiangShan`，`kunminghu-v2@e12436c7cba86b195deec24981976d78bc263661` | 用户指定的本地 checkout。工作树原有 `difftest` 修改和 `src/main/resources/aia/` 未跟踪内容；本文没有修改它们，也不以它们为证据。 |
| coupledL2 | `fb5469838c8902b6cb33992c0a30ee3d446e4453` | 本文的直接 RTL/Chisel 证据：RequestArb、Slice、Directory、MSHRCtl、MainPipe、各 buffer 和 CHI TX 模块。 |
| huancun | `65ef077373ecf398b4cecdea06b65ef9b8d79044` | 只用于确认边界和对照其不同的请求分配结构。 |
| XiangShan Design Doc | URL: `https://github.com/OpenXiangShan/XiangShan-Design-Doc.git`；`/home/yanyusong/XiangShan-Design-Doc`，`kunminghu-v2@58d9e2ad11f044cb6f8887d9687d9e110696d1aa` | 仅作为设计意图索引；分支名相同但提交独立，行为结论均回到当前 Chisel 源码。 |
| XiangShanLab | `680010a3cf7cc72900345600b99709bc337a52bf` | 用于课程概念链接和本文保存位置。 |
| skill 同步检查 | `weekly_sync.py` 返回 `skip: last sync 0.24 days ago < 7 days` | 已按当前 skill 执行检查；没有执行 reset、clean、pull 或覆盖任何工作树。 |

### 1.2 为什么有效实现是 `tl2chi/RequestArb`

`KunminghuV2Config` 把 1 MiB、4 bank 的 L2 配置与 `WithCHI` 组合；`WithCHI` 令 `EnableCHI=true`。`L2Top` 按该参数选择 `TL2CHICoupledL2`，而 `CoupledL2Base` 为每一个 bank 选择 `tl2chi.Slice`。该 Slice 明确例化 `RequestArb`，所以本文不以文件名猜测实现，而是沿配置和例化链得到它。[Configs.scala:477](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:477) [L2Top.scala:111](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/L2Top.scala:111) [CoupledL2.scala:419](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:419) [tl2chi/Slice.scala:58](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:58)

~~~scala
// Configs.scala:477-485；L2Top.scala:125-131（节选）
class WithCHI extends Config((_, _, _) => {
  case EnableCHI => true
})
class KunminghuV2Config(n: Int = 1) extends Config(
  L2CacheConfig("1MB", inclusive = true, banks = 4, tp = false)
    ++ new DefaultConfig(n) ++ new WithCHI
)
if (enableCHI) Some(LazyModule(new TL2CHICoupledL2()(new Config(config))))
else Some(LazyModule(new TL2TLCoupledL2()(new Config(config))))
~~~

~~~mermaid
flowchart LR
  CFG[KunminghuV2Config<br/>EnableCHI = true] -- > TOP[L2Top]
  TOP -- > L2[TL2CHICoupledL2]
  L2 -- > S0[tl2chi Slice bank 0]
  L2 -- > SN[tl2chi Slice bank N]
  S0 -- > RA0[RequestArb]
  SN -- > RAN[RequestArb]
~~~

### 1.3 HuanCun、OpenLLC 与 L1 DCache 的边界

`L3CacheParamsOpt` 只在 `!EnableCHI` 时保留；CHI 情形走 `OpenLLCParamsOpt`，顶层也据此选择组件。对 `huancun/src/main/scala` 的同名类搜索没有发现 `class RequestArb`。HuanCun 的 Slice 使用 `RequestBuffer -> MSHRAlloc`，它的 C/B/A 分配优先级是另一个模块的独立实现，不能替代本文的 RequestArb。`openLLC.RequestArb` 同样属于不同 package 和 LLC 层级。[Configs.scala:346](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:346) [Top.scala:111](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Top.scala:111) [huancun/Slice.scala:127](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/Slice.scala:127) [huancun/MSHRAlloc.scala:113](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/MSHRAlloc.scala:113)

| 名称 | 所在位置 | 是否为 V2+CHI 的本文目标 | 原因 |
| --- | --- | --- | --- |
| L2 CHI RequestArb | `coupledL2/tl2chi/Slice.scala` | 是 | V2 实际例化的每 bank Slice 请求入口。 |
| L2 TL-to-TL RequestArb | `coupledL2/tl2tl/Slice.scala` | 否 | 仅 `EnableCHI=false` 时有效，接口来源与 CHI 版本不同。 |
| HuanCun MSHRAlloc | `huancun/MSHRAlloc.scala` | 否 | 没有同名 `RequestArb`；并且 V2+CHI 不启用 HuanCun L3。 |
| OpenLLC RequestArb | `openLLC` package | 否 | LLC 层的独立模块，不在 CoupledL2 Slice 内。 |
| L1 DCache 相关 arbiter | `xiangshan/cache/dcache` | 否 | L1D 的协议、队列和流水边界均不同。 |
-->

## 1. Scope, Version, and Effective Implementation

### 1.1 Analysis Baseline

| Item | Baseline Used Here | Handling |
| --- | --- | --- |
| XiangShan | URL: `https://github.com/OpenXiangShan/XiangShan.git`; `/home/yanyusong/xs-memory-env/XiangShan`, `kunminghu-v2@e12436c7cba86b195deec24981976d78bc263661` | The user-specified local checkout. Its worktree already contained `difftest` changes and untracked `src/main/resources/aia/` content; neither was modified nor used as evidence. |
| coupledL2 | `fb5469838c8902b6cb33992c0a30ee3d446e4453` | Direct RTL/Chisel evidence for this document: RequestArb, Slice, Directory, MSHRCtl, MainPipe, buffers, and CHI TX modules. |
| huancun | `65ef077373ecf398b4cecdea06b65ef9b8d79044` | Used only to confirm boundaries and compare its different request-allocation structure. |
| XiangShan Design Doc | URL: `https://github.com/OpenXiangShan/XiangShan-Design-Doc.git`; `/home/yanyusong/XiangShan-Design-Doc`, `kunminghu-v2@58d9e2ad11f044cb6f8887d9687d9e110696d1aa` | An index to design intent only. The branch name is the same but its commit is independent; behavior conclusions always return to current Chisel source. |
| XiangShanLab | `680010a3cf7cc72900345600b99709bc337a52bf` | Used for course-concept links and this document's saved location. |
| Skill synchronization check | `weekly_sync.py` returned `skip: last sync 0.24 days ago < 7 days` | Checked through the current skill. No reset, clean, pull, or worktree overwrite was performed. |

### 1.2 Why `tl2chi/RequestArb` Is the Effective Implementation

`KunminghuV2Config` combines the 1 MiB, four-bank L2 configuration with `WithCHI`, and `WithCHI` sets `EnableCHI=true`. `L2Top` selects `TL2CHICoupledL2` from that parameter, while `CoupledL2Base` selects `tl2chi.Slice` for every bank. That Slice explicitly instantiates `RequestArb`, so this document follows the configuration and instantiation chain instead of inferring an implementation from a filename. [Configs.scala:477](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:477) [L2Top.scala:111](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/L2Top.scala:111) [CoupledL2.scala:419](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:419) [tl2chi/Slice.scala:58](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:58)

~~~scala
// Configs.scala:477-485; L2Top.scala:125-131 (excerpt)
class WithCHI extends Config((_, _, _) => {
  case EnableCHI => true
})
class KunminghuV2Config(n: Int = 1) extends Config(
  L2CacheConfig("1MB", inclusive = true, banks = 4, tp = false)
    ++ new DefaultConfig(n) ++ new WithCHI
)
if (enableCHI) Some(LazyModule(new TL2CHICoupledL2()(new Config(config))))
else Some(LazyModule(new TL2TLCoupledL2()(new Config(config))))
~~~

~~~mermaid
flowchart LR
  CFG[KunminghuV2Config<br/>EnableCHI = true] --> TOP[L2Top]
  TOP --> L2[TL2CHICoupledL2]
  L2 --> S0[tl2chi Slice bank 0]
  L2 --> SN[tl2chi Slice bank N]
  S0 --> RA0[RequestArb]
  SN --> RAN[RequestArb]
~~~

### 1.3 Boundaries of HuanCun, OpenLLC, and L1 DCache

`L3CacheParamsOpt` is retained only when `!EnableCHI`; the CHI case uses `OpenLLCParamsOpt`, and the top level selects components accordingly. A same-name-class search in `huancun/src/main/scala` found no `class RequestArb`. HuanCun Slice uses `RequestBuffer -> MSHRAlloc`, whose C/B/A allocation priority is a separate implementation and cannot substitute for this RequestArb. `openLLC.RequestArb` is likewise in a different package and LLC hierarchy. [Configs.scala:346](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:346) [Top.scala:111](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Top.scala:111) [huancun/Slice.scala:127](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/Slice.scala:127) [huancun/MSHRAlloc.scala:113](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/MSHRAlloc.scala:113)

| Name | Location | Target of This V2+CHI Document? | Reason |
| --- | --- | --- | --- |
| L2 CHI RequestArb | `coupledL2/tl2chi/Slice.scala` | Yes | The request ingress of each bank Slice actually instantiated by V2. |
| L2 TL-to-TL RequestArb | `coupledL2/tl2tl/Slice.scala` | No | Active only when `EnableCHI=false`; its interface sources differ from the CHI version. |
| HuanCun MSHRAlloc | `huancun/MSHRAlloc.scala` | No | There is no same-named `RequestArb`, and V2+CHI does not enable HuanCun L3. |
| OpenLLC RequestArb | `openLLC` package | No | An independent LLC-level module outside a CoupledL2 Slice. |
| L1 DCache-related arbiters | `xiangshan/cache/dcache` | No | L1D has different protocol, queue, and pipeline boundaries. |

<!--
## 2. 理论、Design Doc 与有效代码

### 2.1 Theory-to-Code Mapping

课程中“结构冲突”和“流水寄存器”的概念有助于理解该模块为何反压、为何把任务分 S1/S2 保存；但这里仲裁的是 L2 一致性事务 `TaskBundle`，不是带 ROB、redirect 或异常提交语义的 CPU 指令。[结构冒险课程](/home/yanyusong/XiangShanLab/xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/1-superscalar-basic-knowledge/3_Structural_Hazards_vs_Data_Hazards_vs_Control_Hazards.md) [Common.scala:57](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Common.scala:57)

| 课程概念 | 在 RequestArb 周边的有效实体 | 源码中的具体含义 | 不能外推的结论 |
| --- | --- | --- | --- |
| 结构冲突 | Directory 读端口、DataStorage MCP2、MSHR、GrantBuffer、CHI TX 队列 | 资源或预测容量不允许时，通过 `ready` 或 `block*` 阻止本次准入。 | 不能只看 `valid` 就判定请求被接收。 |
| 优先级仲裁 | `ParallelPriorityMux(C,B,A)` | C/B/A 同时合格时 C 获胜，B/A 保持 Decoupled 请求。 | 本体不是公平的 RR 仲裁器。 |
| 级间寄存器 | `mshr_task_s1`、`task_s2` | MSHR S0 先入寄存器；S1 成功后，S2 以 `ValidIO` 送 MainPipe。 | 这不是 CPU 指令的提交、flush 或 redirect 管线。 |
| 非阻塞缓存 | MSHRCtl、MSHRBuffer、回流 `mshrTask` | 长 miss/替换活动脱离短主路径，随后以任务形式回送。 | 不能把 MSHR 的内部状态当作 RequestArb 的显式 FSM。 |

### 2.2 Design Doc 到源码的追踪矩阵

下表只将官方文档中的意图映射到本地当前源码，不复制文档描述。状态为“部分验证”时，表示实现中存在相应机制，但时序细节或接口含义与文档文字不同，故以源码为准。

| ID | Design Doc 位置 | Design claim | Source file:lines | Code relationship | Status | Discrepancy / risk |
| --- | --- | --- | --- | --- | --- |
| D1 | [ReqArb_MainPipe.md:3](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/ReqArb_MainPipe.md:3)，开篇五级流水说明 | ReqArb 形成 S1/S2，MainPipe 接续 S3-S5 | [RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [RequestArb.scala:199](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:199) [MainPipe.scala:142](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:142) | S1 的 `task_s1` 经 `s1_fire` 写入 `task_s2`；Slice 将该 ValidIO 同时接到 MainPipe，后者再注册后续 stage。 | Verified | 文档的阶段名称与源码寄存器相符；本文不把它外推成固定周期服务时间。 |
| D2 | [ReqArb_MainPipe.md:5](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/ReqArb_MainPipe.md:5)，`## S0 流水级` | S0 对 MSHR 任务施加下游资源反压 | [RequestArb.scala:114](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:114) [RequestArb.scala:120](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:120) [tl2chi/Slice.scala:99](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:99) | Grant/TX/replacement-read/S2 条件组合成 `mshrTask.ready`；`valid && ready` 形成 `s0_fire`，再写 S1 MSHR 寄存器。 | Verified | 文档列举的 TL SourceC 在 CHI 配置下由 TXDAT/TXRSP/TXREQ 分支替代。 |
| D3 | [ReqArb_MainPipe.md:17](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/ReqArb_MainPipe.md:17)，`## S1 流水级` | MSHR、C、B、A 按优先级握手，随后读 Directory | [RequestArb.scala:95](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:95) [RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [RequestArb.scala:165](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:165) [RequestArb.scala:176](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:176) | C/B/A 先进入 `sinkValids -> ParallelPriorityMux`，已寄存 MSHR 再经 `Mux` 覆盖 channel；Directory read 只对 channel task 或 `s1_needs_replRead` 的 MSHR 有效，普通 MSHR 可 S1 fire 而不读 Directory。 | Partially verified | 新 MSHR 必经 S0，不能同拍抢占 channel；V2 的 B 是 CHI snoop；文档将“MSHR 握手后读 Directory”概括得比代码更强。 |
| D4 | [ReqArb_MainPipe.md:35](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/ReqArb_MainPipe.md:35)，`## S2 流水级` | MCP2 对相邻请求施加入口间隔 | [RequestArb.scala:199](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:199) [DataStorage.scala:119](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/DataStorage.scala:119) | `s1_fire && !s1_AHint_fire` 经 `RegNext` 变为 `ds_mcp2_stall`，反相产生下一拍 `s2_ready`，后者回到 channel 和 MSHR 的准入式。 | Partially verified | 文档概括为背靠背请求阻塞；源码明确给 A Hint 例外，且其他 block 可再延长间隔。 |
| D5 | [ReqArb_MainPipe.md:41](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/ReqArb_MainPipe.md:41)，S2 buffer read 段落 | S2 决定 Refill/ReleaseBuffer 读并将任务送 MainPipe | [RequestArb.scala:219](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:219) [tl2chi/Slice.scala:146](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:146) [tl2chi/Slice.scala:166](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:166) [tl2chi/Slice.scala:121](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:121) | `task_s2` 的 MSHR/replace/probe/snoop 字段决定两个 read `valid/id`；Slice 接读口并把响应寄存一拍送 MainPipe S3。 | Verified | 只采用实际 read-valid mux；未使用的局部 `*NeedData` 变量不计入实现。 |

### 2.3 关键映射的逐信号说明

- D1：`RequestArb.task_s1` 是 S1 的组合选择结果，`s1_fire` 是唯一写 `task_s2.valid/bits` 的接受事件；`Slice` 将 `taskToPipe_s2` 接给 `MainPipe.taskFromArb_s2`，因此这里的 producer、阶段寄存器和 consumer 均可在源码中追到。 [RequestArb.scala:165](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:165) [RequestArb.scala:206](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:206) [tl2chi/Slice.scala:113](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:113)
- D2：`MSHRCtl.mshrTask` 是 producer；RequestArb 将多组 block 归并为 `ready`，`s0_fire` 写入 `mshr_task_s1.bits`，而 `valid := old_valid && !s1_fire || s0_fire` 规定保持、清除和同边沿覆盖。 [RequestArb.scala:114](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:114) [RequestArb.scala:125](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:125)
- D3：C/B/A 的 producer 分别连到 `sinkC/sinkB/sinkA`；未被 `block_*` 屏蔽的 valid 进入 priority mux，ready 反馈只给获胜者。只有 channel task，或 `s1_needs_replRead` 为真的 MSHR，才使 `task_s1` 驱动有效的 Directory `set/tag/wayMask` 读；普通 MSHR 可以仅推进 S1/S2。 [RequestArb.scala:132](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:132) [RequestArb.scala:155](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:155) [RequestArb.scala:176](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:176)
- D4：非 A-Hint 任务在 S1 已被接受后，才在下一个寄存器边界生成 `ds_mcp2_stall`；它不是 MainPipe ready，也没有反向 ValidIO 握手。 [RequestArb.scala:200](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:200) [RequestArb.scala:204](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:204)
- D5：S2 的 buffer read 由 RequestArb 发出，buffer response 由 Slice 的 `RegNext` 成为 MainPipe S3 输入；没有把 MSHRBuffer 的 Valid-only read 误当成 Decoupled 响应。 [RequestArb.scala:264](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:264) [tl2chi/Slice.scala:121](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:121)

### 2.4 不能从文档或注释外推的结论

1. 没有运行本提交生成的 RTL 或 FST，故本文不报告“命中固定 N 拍”或端到端周期数；下文的时序只描述寄存器和握手关系。
2. `ds_mcp2_stall` 由未显式 `RegInit` 的 `RegNext` 生成，源码没有证明它在 reset 后必为 0；复位第一拍的精确表现应在仿真中检查。
3. `io.msInfo` 的有效引用位于整段注释掉的 `noFreeWay` 代码；`s1_to_s2_valid`、`snoopNeedData`、`releaseNeedData`、`dctNeedData`、`cmoNeedData` 只定义而不驱动最终有效判定。本文不将它们描述为已生效的硬件控制。[RequestArb.scala:166](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:166) [RequestArb.scala:245](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:245)

### 2.5 Design Doc 差异清单

| 分类 | 条目 | 结论 |
| --- | --- | --- |
| Partially verified | D3 | 文档把 MSHR、C、B、A 和 Directory read 放在同一概述中；实际普通 MSHR 可 S1 fire 而 `dirRead_s1.valid=0`，只有 replacement-read MSHR 需要目录读。 |
| Partially verified | D4 | 文档概述背靠背请求阻塞；当前代码对 A Hint 保留例外，并让额外 block 延长间隔。 |
| Not found | 无 | 本文使用的五个 Design Doc claim 均在当前代码找到对应机制；未把注释掉代码作为替代实现。 |
| Version mismatch | 无已证实项 | Design Doc 与源码均在 `kunminghu-v2` 分支但 commit 独立；矩阵已将配置/协议差异标为风险，不把分支名相同当作版本等价。 |
| Design-only | 无作为行为依据的项 | 没有把 Design Doc 图或术语直接当作有效 RTL；所有 load-bearing 结论均有 Chisel 链接。 |
-->

## 2. Theory, Design Doc, and Effective Code

### 2.1 Theory-to-Code Mapping

The course concepts of structural hazards and pipeline registers help explain why this module applies backpressure and retains tasks across S1/S2. However, it arbitrates L2 coherence-transaction `TaskBundle`s, not CPU instructions with ROB, redirect, or exception-commit semantics. [Structural-hazard course](/home/yanyusong/XiangShanLab/xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/1-superscalar-basic-knowledge/3_Structural_Hazards_vs_Data_Hazards_vs_Control_Hazards.md) [Common.scala:57](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Common.scala:57)

| Course Concept | Effective Entity around RequestArb | Concrete Meaning in Source | Conclusion That Must Not Be Extrapolated |
| --- | --- | --- | --- |
| Structural conflict | Directory read port, DataStorage MCP2, MSHR, GrantBuffer, CHI TX queues | If resource availability or predicted capacity disallows admission, `ready` or `block*` prevents the current admission. | A request cannot be considered accepted from `valid` alone. |
| Priority arbitration | `ParallelPriorityMux(C,B,A)` | When C/B/A are all eligible, C wins and B/A retain their Decoupled requests. | This module is not a fair round-robin arbiter. |
| Inter-stage registers | `mshr_task_s1`, `task_s2` | An MSHR task enters a register through S0; after S1 succeeds, S2 sends it to MainPipe using `ValidIO`. | This is not a CPU-instruction commit, flush, or redirect pipeline. |
| Non-blocking cache | MSHRCtl, MSHRBuffer, returned `mshrTask` | Long misses/replacements are decoupled from the short main path and later sent back as tasks. | MSHR internal state must not be described as an explicit RequestArb FSM. |

### 2.2 Design-Document-to-Source Traceability Matrix

The following table maps only design intent from the official documents to the current local source; it does not reproduce document prose. "Partially verified" means an implementation mechanism exists, but timing details or interface meaning differ from the document wording, so source wins.

| ID | Design Doc Location | Design Claim | Source File:Lines | Code Relationship | Status | Discrepancy / Risk |
| --- | --- | --- | --- | --- | --- | --- |
| D1 | [ReqArb_MainPipe.md:3](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/ReqArb_MainPipe.md:3), opening five-stage-pipeline description | ReqArb forms S1/S2 and MainPipe continues S3-S5 | [RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [RequestArb.scala:199](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:199) [MainPipe.scala:142](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:142) | `task_s1` is written into `task_s2` on `s1_fire`. Slice connects that ValidIO to MainPipe, which then registers later stages. | Verified | Document stage names agree with source registers; this document does not turn that into a fixed service-cycle claim. |
| D2 | [ReqArb_MainPipe.md:5](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/ReqArb_MainPipe.md:5), `## S0 pipeline stage` | S0 applies downstream-resource backpressure to MSHR tasks | [RequestArb.scala:114](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:114) [RequestArb.scala:120](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:120) [tl2chi/Slice.scala:99](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:99) | Grant/TX/replacement-read/S2 conditions combine into `mshrTask.ready`; `valid && ready` forms `s0_fire`, which writes the S1 MSHR register. | Verified | The document's TL SourceC is replaced in CHI configuration by TXDAT/TXRSP/TXREQ branches. |
| D3 | [ReqArb_MainPipe.md:17](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/ReqArb_MainPipe.md:17), `## S1 pipeline stage` | MSHR, C, B, A handshake by priority and then read Directory | [RequestArb.scala:95](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:95) [RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [RequestArb.scala:165](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:165) [RequestArb.scala:176](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:176) | C/B/A first enter `sinkValids -> ParallelPriorityMux`, then a held MSHR overrides the channel task through `Mux`. Directory read is valid only for a channel task or an MSHR with `s1_needs_replRead`; an ordinary MSHR can fire at S1 without Directory read. | Partially verified | A new MSHR must traverse S0 and cannot preempt a channel in the same cycle. V2 B is a CHI snoop. The document makes "MSHR handshakes then reads Directory" stronger than the code. |
| D4 | [ReqArb_MainPipe.md:35](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/ReqArb_MainPipe.md:35), `## S2 pipeline stage` | MCP2 imposes an ingress interval between adjacent requests | [RequestArb.scala:199](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:199) [DataStorage.scala:119](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/DataStorage.scala:119) | `s1_fire && !s1_AHint_fire` becomes `ds_mcp2_stall` through `RegNext`; its inverse supplies next-cycle `s2_ready`, which returns to channel and MSHR admission expressions. | Partially verified | The document summarizes back-to-back blocking. Source explicitly exempts A Hint, and other blocks can extend the interval. |
| D5 | [ReqArb_MainPipe.md:41](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/ReqArb_MainPipe.md:41), S2 buffer-read paragraph | S2 chooses Refill/ReleaseBuffer reads and sends the task to MainPipe | [RequestArb.scala:219](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:219) [tl2chi/Slice.scala:146](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:146) [tl2chi/Slice.scala:166](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:166) [tl2chi/Slice.scala:121](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:121) | MSHR/replace/probe/snoop fields in `task_s2` select two read `valid/id` signals. Slice registers the responses for one cycle before MainPipe S3. | Verified | Only actual read-valid muxes are used; unused local `*NeedData` variables do not count as implementation. |

### 2.3 Signal-Level Explanation of Key Mappings

- D1: `RequestArb.task_s1` is the combinational S1 selection. `s1_fire` is the only acceptance event that writes `task_s2.valid/bits`; Slice connects `taskToPipe_s2` to `MainPipe.taskFromArb_s2`. Producer, stage register, and consumer are all traceable in source. [RequestArb.scala:165](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:165) [RequestArb.scala:206](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:206) [tl2chi/Slice.scala:113](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:113)
- D2: `MSHRCtl.mshrTask` is the producer. RequestArb combines multiple blocks into `ready`; `s0_fire` writes `mshr_task_s1.bits`, while `valid := old_valid && !s1_fire || s0_fire` defines retention, clearing, and same-edge replacement. [RequestArb.scala:114](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:114) [RequestArb.scala:125](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:125)
- D3: The C/B/A producers connect to `sinkC/sinkB/sinkA`. Valids not masked by `block_*` enter the priority mux; ready feeds back only to the winner. Only a channel task, or an MSHR for which `s1_needs_replRead` is true, drives a valid Directory read of `set/tag/wayMask`; an ordinary MSHR can advance only through S1/S2. [RequestArb.scala:132](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:132) [RequestArb.scala:155](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:155) [RequestArb.scala:176](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:176)
- D4: A non-A-Hint task generates `ds_mcp2_stall` only after it has been accepted at S1, at the following register boundary. It is not MainPipe ready and has no reverse ValidIO handshake. [RequestArb.scala:200](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:200) [RequestArb.scala:204](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:204)
- D5: RequestArb issues S2 buffer reads; Slice's `RegNext` makes buffer responses MainPipe S3 inputs. An MSHRBuffer Valid-only read must not be mistaken for a Decoupled response. [RequestArb.scala:264](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:264) [tl2chi/Slice.scala:121](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:121)

### 2.4 Conclusions That Cannot Be Inferred from Documents or Comments

1. RTL generated by this commit and an FST were not run, so the document does not report a fixed hit latency or end-to-end cycle count. Timing below describes only registers and handshake relationships.
2. `ds_mcp2_stall` comes from a `RegNext` without explicit `RegInit`; source does not establish it as zero immediately after reset. Simulate exact first-cycle reset behavior.
3. The effective reference to `io.msInfo` is in an entirely commented-out `noFreeWay` block. `s1_to_s2_valid`, `snoopNeedData`, `releaseNeedData`, `dctNeedData`, and `cmoNeedData` are only defined and do not drive final effective predicates. This document does not call them active hardware control. [RequestArb.scala:166](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:166) [RequestArb.scala:245](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:245)

### 2.5 Design-Document Difference List

| Category | Item | Conclusion |
| --- | --- | --- |
| Partially verified | D3 | The document puts MSHR, C, B, A, and Directory read into one overview. Actual ordinary MSHRs can fire at S1 while `dirRead_s1.valid=0`; only replacement-read MSHRs need Directory read. |
| Partially verified | D4 | The document summarizes back-to-back-request blocking. Current code retains an A-Hint exception and lets additional blocks extend the interval. |
| Not found | None | Each of the five Design Doc claims used here has a corresponding mechanism in current code; commented-out code was not used as an alternative implementation. |
| Version mismatch | No proven item | Design Doc and source use the `kunminghu-v2` branch but independent commits. The matrix labels configuration/protocol differences as risks instead of treating identical branch names as version equivalence. |
| Design-only | No item used as behavior evidence | No Design Doc diagram or term is treated directly as effective RTL. Every load-bearing conclusion has a Chisel link. |

<!--
## 3. 模块契约：Who / Why / How / From / To

### 3.1 Slice 内实际连线

~~~mermaid
flowchart LR
  A[SinkA<br/>TL-A] -- > RB[RequestBuffer]
  RB -- >|sinkA: Decoupled TaskBundle| RA[RequestArb]
  C[SinkC<br/>Release/ReleaseData] -- >|sinkC: Decoupled TaskBundle| RA
  SNP[CHI RXSNP<br/>2-entry Queue] -- >|sinkB: Decoupled TaskBundle| RA
  MC[MSHRCtl<br/>FastArbiter] -- >|mshrTask: Decoupled| RA
  RA -- >|dirRead_s1: Decoupled| DIR[Directory]
  RA -- >|taskToPipe_s2: Valid| MP[MainPipe]
  RA -- >|refillBufRead_s2| RF[Refill MSHRBuffer]
  RA -- >|releaseBufRead_s2| RL[Release MSHRBuffer]
  MP -- >|block/status| RA
  MC -- >|block/status| RA
  GB[GrantBuffer] -- >|block/status| RA
  TX[TXREQ / TXDAT / TXRSP] -- >|predicted capacity block| RA
  RA -- >|s1Entrance| RB
~~~

`tl2chi.Slice` 的接线是本节所有箭头的直接证据。特别地，V2 的 `sinkB` 来自 `RXSNP.task`，不是 TL-to-TL 分支里的 `SinkB.task`；虽然信号名保留 B，这个协议边界不能混淆。[tl2chi/Slice.scala:65](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:65) [tl2tl/Slice.scala:70](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2tl/Slice.scala:70) [RXSNP.scala:28](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:28)

### 3.1.1 S0-S2 的关键数据路径

~~~mermaid
flowchart LR
  MT[mshrTask valid/bits] -- >|valid && ready: s0_fire| MR[mshr_task_s1 Reg]
  C[sinkC valid/bits] -- > SV[sinkValids]
  B[sinkB valid/bits] -- > SV
  A[sinkA valid/bits] -- > SV
  SV -- > PM[ParallelPriorityMux C B A]
  PM -- > CT[chnl_task_s1]
  MR -- > TM[task_s1 Mux]
  CT -- > TM
  TM -- > GATE[s1_cango and s2_ready]
  GATE -- >|s1_fire| T2[task_s2 Reg]
  TM -- >|channel or repl MSHR: tag set wayMask| DR[dirRead_s1]
  T2 -- >|Valid taskToPipe_s2| MP[MainPipe]
  T2 -- >|valid/id| RFB[refillBufRead_s2]
  T2 -- >|valid/id| RLB[releaseBufRead_s2]
  BLK[Directory ready and block inputs] -- > SV
  BLK -- > DR
  MCP[ds_mcp2_stall] -- >|inverts to s2_ready| GATE
~~~

图中的 `sinkValids` 已经包含 `!block_A/B/C`，`TM` 左侧的 MSHR 输入只有在 `mshr_task_s1.valid` 时覆盖 channel 输入；`dirRead_s1` 的实际 valid 还区分普通 channel 和 replacement-read MSHR。这是信号级数据路径图，不把所有输入都错误地画成同一个 `fire`。[RequestArb.scala:124](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:124) [RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [RequestArb.scala:176](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:176)

| 接口组 | Who / From | Why | How | To |
| --- | --- | --- | --- | --- |
| `sinkA` | `RequestBuffer.out`，上游源于 TL-A | 承接普通 L1 请求或 Hint | `DecoupledIO[TaskBundle]`；仲裁失败靠 `ready=0` 保留请求 | S1 channel 候选、Directory、MainPipe。 |
| `sinkC` | `SinkC.task` | 接收 Release/ReleaseData；ProbeAck 不是该入口，而是 MSHR 响应路径 | `DecoupledIO`；同拍优先级最高 | S1 channel 候选；其 fire 可反馈 `s1Entrance`。 |
| `sinkB` | CHI `RXSNP.task` | 把下行 coherent snoop 转为本地 task | `DecoupledIO`；TXDAT/TXRSP 可反压它 | S1 channel 候选；其 fire 可反馈 `s1Entrance`。 |
| `mshrTask` | `MSHRCtl` 汇合多个 MSHR mainpipe task | 重放/推进长 miss、替换或一致性事务 | `DecoupledIO`，先入 S0/S1 暂存槽 | S1 绝对优先于 channel，必要时读 Directory。 |
| `dirRead_s1` | RequestArb 到 Directory | 用 `tag/set/wayMask` 获得命中、way 和替换信息 | `DecoupledIO`；read ready 影响入口准入 | Directory tag/meta SRAM。 |
| `taskToPipe_s2` | RequestArb 到 MainPipe | 将已准入任务交给主流水 | `ValidIO`，没有 MainPipe 的反向 ready | MainPipe S2/S3 流水。 |
| `refillBufRead_s2` / `releaseBufRead_s2` | RequestArb 到两个 MSHRBuffer | 为随后 MainPipe 阶段取回长路径保留的数据 | `ValidIO[MSHRBufRead]`，用 MSHR id 索引 | Slice 将读响应延迟一拍送 MainPipe S3。 |
| `from*` 状态 | MainPipe、MSHRCtl、GrantBuffer、TX 队列 | 把容量和同组冲突前推到入口 | `BlockInfo`、状态向量，不是统一 ready | 构造 `block_A/B/C` 与 MSHR S0 ready。 |

`TaskBundle` 不仅携带 channel，还携带本 bank 的 `tag/set/off`、`mshrTask/mshrId`、`replTask`、`metaWen`、`dsWen`、`useProbeData` 等字段；RequestArb 根据这些字段选路径，并不从 CPU ROB 或执行单元读取状态。[Common.scala:57](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Common.scala:57)

### 3.2 握手边界

只有 `sinkA/B/C`、`mshrTask` 和 `dirRead_s1` 是 Decoupled 接口，故可严格定义 `fire = valid && ready`。`taskInfo_s1`、`taskToPipe_s2`、MSHRBuffer 读请求均为 `ValidIO`；它们没有本接口的 `ready`，不能写成“MainPipe ready 后才接收”。[RequestArb.scala:32](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:32) [MainPipe.scala:40](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:40)

| 名称 | `valid` 的来源/含义 | `ready` 的来源/含义 | 可以断言的 `fire` |
| --- | --- | --- | --- |
| `sinkA/B/C` | 各请求缓冲/协议接收端有待处理 task | RequestArb 的基础准入、block 和优先级共同决定 | 是；落选者不得被当作已接收。 |
| `mshrTask` | 上游 MSHR `FastArbiter` 选出的任务 | GrantBuffer、replace-read、S2 保护、CHI TX 预测容量决定 | 是；fire 后 payload 写入 `mshr_task_s1`。 |
| `dirRead_s1` | channel 选中或 MSHR replacement-read 需要目录 | Directory 无写/替换占用时才 ready | 是；Directory 真正读 SRAM 的条件是其 read fire。 |
| `taskInfo_s1` | `s1_fire` | 无 | 否，只有提示有效。 |
| `taskToPipe_s2` | S2 寄存器有效 | 无 | 否，时序关系由 S1 fire 和本地寄存器决定。 |
| buffer read | 分类后需要的 MSHR 数据 | 无 | 否，必须与关联 MSHR 状态联合解释。 |
-->

## 3. Module Contract: Who / Why / How / From / To

### 3.1 Actual Wiring Inside Slice

~~~mermaid
flowchart LR
  A[SinkA<br/>TL-A] --> RB[RequestBuffer]
  RB -->|sinkA: Decoupled TaskBundle| RA[RequestArb]
  C[SinkC<br/>Release/ReleaseData] -->|sinkC: Decoupled TaskBundle| RA
  SNP[CHI RXSNP<br/>2-entry Queue] -->|sinkB: Decoupled TaskBundle| RA
  MC[MSHRCtl<br/>FastArbiter] -->|mshrTask: Decoupled| RA
  RA -->|dirRead_s1: Decoupled| DIR[Directory]
  RA -->|taskToPipe_s2: Valid| MP[MainPipe]
  RA -->|refillBufRead_s2| RF[Refill MSHRBuffer]
  RA -->|releaseBufRead_s2| RL[Release MSHRBuffer]
  MP -->|block/status| RA
  MC -->|block/status| RA
  GB[GrantBuffer] -->|block/status| RA
  TX[TXREQ / TXDAT / TXRSP] -->|predicted capacity block| RA
  RA -->|s1Entrance| RB
~~~

The `tl2chi.Slice` wiring is direct evidence for every arrow in this section. In particular, V2's `sinkB` comes from `RXSNP.task`, not `SinkB.task` in the TL-to-TL branch. The signal name retains B, but this protocol boundary must not be conflated. [tl2chi/Slice.scala:65](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:65) [tl2tl/Slice.scala:70](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2tl/Slice.scala:70) [RXSNP.scala:28](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:28)

### 3.1.1 Key S0--S2 Data Path

~~~mermaid
flowchart LR
  MT[mshrTask valid/bits] -->|valid && ready: s0_fire| MR[mshr_task_s1 Reg]
  C[sinkC valid/bits] --> SV[sinkValids]
  B[sinkB valid/bits] --> SV
  A[sinkA valid/bits] --> SV
  SV --> PM[ParallelPriorityMux C B A]
  PM --> CT[chnl_task_s1]
  MR --> TM[task_s1 Mux]
  CT --> TM
  TM --> GATE[s1_cango and s2_ready]
  GATE -->|s1_fire| T2[task_s2 Reg]
  TM -->|channel or repl MSHR: tag set wayMask| DR[dirRead_s1]
  T2 -->|Valid taskToPipe_s2| MP[MainPipe]
  T2 -->|valid/id| RFB[refillBufRead_s2]
  T2 -->|valid/id| RLB[releaseBufRead_s2]
  BLK[Directory ready and block inputs] --> SV
  BLK --> DR
  MCP[ds_mcp2_stall] -->|inverts to s2_ready| GATE
~~~

`sinkValids` in the diagram already includes `!block_A/B/C`. The MSHR input on the left of `TM` overrides a channel input only when `mshr_task_s1.valid` is true. Actual `dirRead_s1.valid` further distinguishes ordinary channels from replacement-read MSHRs. This is a signal-level data-path diagram; it does not incorrectly draw every input as sharing one `fire`. [RequestArb.scala:124](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:124) [RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [RequestArb.scala:176](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:176)

| Interface Group | Who / From | Why | How | To |
| --- | --- | --- | --- | --- |
| `sinkA` | `RequestBuffer.out`, upstream from TL-A | Accepts ordinary L1 requests or Hints | `DecoupledIO[TaskBundle]`; arbitration failure retains the request with `ready=0` | S1 channel candidate, Directory, MainPipe. |
| `sinkC` | `SinkC.task` | Accepts Release/ReleaseData. ProbeAck is not this ingress; it is an MSHR response path. | `DecoupledIO`; highest same-cycle priority | S1 channel candidate; its fire can feed back `s1Entrance`. |
| `sinkB` | CHI `RXSNP.task` | Converts a downstream coherent snoop into a local task | `DecoupledIO`; TXDAT/TXRSP can backpressure it | S1 channel candidate; its fire can feed back `s1Entrance`. |
| `mshrTask` | MSHRCtl converging multiple MSHR MainPipe tasks | Replays / advances a long miss, replacement, or coherence transaction | `DecoupledIO`, first entering the S0/S1 holding slot | Absolute S1 priority over channels; reads Directory when necessary. |
| `dirRead_s1` | RequestArb to Directory | Obtains hit, way, and replacement information using `tag/set/wayMask` | `DecoupledIO`; read ready affects admission | Directory tag/meta SRAM. |
| `taskToPipe_s2` | RequestArb to MainPipe | Hands an admitted task to the main pipeline | `ValidIO`, with no reverse MainPipe ready | MainPipe S2/S3 pipeline. |
| `refillBufRead_s2` / `releaseBufRead_s2` | RequestArb to two MSHRBuffers | Retrieves data retained for later MainPipe stages from the long path | `ValidIO[MSHRBufRead]`, indexed by MSHR id | Slice delays the read response by one cycle before MainPipe S3. |
| `from*` status | MainPipe, MSHRCtl, GrantBuffer, TX queues | Pushes capacity and same-group conflicts to ingress | `BlockInfo` and status vectors, not a unified ready | Builds `block_A/B/C` and MSHR S0 ready. |

`TaskBundle` carries more than the channel: it includes this-bank `tag/set/off`, `mshrTask/mshrId`, `replTask`, `metaWen`, `dsWen`, `useProbeData`, and other fields. RequestArb chooses paths from these fields; it does not read CPU ROB or execution-unit state. [Common.scala:57](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Common.scala:57)

### 3.2 Handshake Boundaries

Only `sinkA/B/C`, `mshrTask`, and `dirRead_s1` are Decoupled interfaces, so `fire = valid && ready` is well defined for them. `taskInfo_s1`, `taskToPipe_s2`, and MSHRBuffer read requests are all `ValidIO`; they have no local-interface `ready` and must not be described as "accepted only after MainPipe ready." [RequestArb.scala:32](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:32) [MainPipe.scala:40](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:40)

| Name | Source / Meaning of `valid` | Source / Meaning of `ready` | Valid `fire` Assertion? |
| --- | --- | --- | --- |
| `sinkA/B/C` | Each request buffer / protocol receiver has a pending task | RequestArb's basic admission, blocks, and priority combine | Yes; a loser must not be treated as accepted. |
| `mshrTask` | A task selected by the upstream MSHR `FastArbiter` | GrantBuffer, replacement read, S2 protection, and predicted CHI TX capacity | Yes; fire writes the payload into `mshr_task_s1`. |
| `dirRead_s1` | A selected channel or MSHR replacement read needs Directory | Directory is ready only with no meta/tag write or replacement-write occupancy | Yes; Directory actually reads SRAM on its read fire. |
| `taskInfo_s1` | `s1_fire` | None | No; it is only a valid hint. |
| `taskToPipe_s2` | S2 register is valid | None | No; its timing comes from S1 fire and local registers. |
| Buffer read | Classified task needs MSHR data | None | No; interpret it together with the associated MSHR state. |

<!--
## 4. 参数、地址与状态承载

### 4.1 配置推导而非仿真测量

| 项目 | 代码依据 | 本配置下的结论 | 限制 |
| --- | --- | --- | --- |
| L2 容量、bank、way | `L2CacheConfig("1MB", banks=4)`，默认 8 way | 每 bank 为 256 KiB；以 64 B line 计算为 512 set/bank | 是配置公式的静态推导，未运行 elaboration。 |
| line / D beat | L2 参数默认 block 64 B、D beat 32 B | `beatSize=2`；RequestArb 有显式 `require(beatSize == 2)` | 这是 L2 内部数据节拍，不代表任意 CPU 访存的拆分规则。 |
| MSHR 数 | L2 参数默认 `mshrs=16` | `MSHRBuffer` 以全局 MSHR id 建寄存器向量 | ID 位宽来自更大的 id 空间，不能从 16 直接臆测所有 ID 位数。 |
| 地址拆分 | `parseAddress` | `off=[5:0]`，4 bank 时 bank bits 为 `[7:6]`，单 bank `set=[16:8]`，其上为 tag | 只描述本 bank TaskBundle 的地址表示。 |

`parseAddress` 先取 line offset，再跳过 bank bits 得到 per-bank set，最后得到 tag；外发 CHI 前会再由下游路径恢复 Slice/bank 信息。这里的 `set/tag` 是缓存内部物理地址分解，不是虚拟地址翻译。[CoupledL2.scala:186](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:186) [L2Param.scala:65](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:65) [RequestArb.scala:276](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:276)

### 4.2 RequestArb 自身不是多项 FIFO

| 结构 | 容量参数 | 占用状态 | 空/满或准入条件 | 写入/释放 | 反压对象 |
| --- | --- | --- | --- | --- | --- |
| `mshr_task_s1` | 1 个 `Valid[TaskBundle]` 寄存器 | `mshr_task_s1.valid` | 已有效时 channel 基础准入关闭；S0 同时还要满足各 MSHR 条件 | `s0_fire` 写入；`s1_fire` 消耗，S0 新入可同边沿覆盖 | MSHRCtl 的 `mshrTask.ready`、所有 channel 的基础 ready。 |
| `task_s2` | 1 个 `Valid[TaskBundle]` 寄存器 | `task_s2.valid` | 不是独立 FIFO；由 `s2_ready` 和 MCP2 保护限制下一次 S1 准入 | `s1_fire` 写入，随后作为 Valid 送 MainPipe | A/B/C 与 MSHR 的 S1 准入。 |
| Refill MSHRBuffer | `mshrsAll x beatSize` 寄存器阵列 | 本 buffer 无自身 valid 向量 | 无本地 empty/full 信号；数据可用性由关联 MSHR 状态保证 | 多写端口写入，RequestArb 用 `mshrId` 读 | 无直接 ready；错误的上游状态会导致读无语义数据。 |
| Release MSHRBuffer | `mshrsAll x beatSize` 寄存器阵列 | 同上 | 同上 | 同上；snoop hit release 可换用 `snpHitReleaseIdx` | 同上。 |

MSHRBuffer 读口是 Valid-only：`r.valid` 时把 `buffer(r.id)` 采样到响应寄存器；写端口允许至多两路命中同一 entry 的选择逻辑，但源码断言 `PopCount(wens) <= 2`。因此它不是自行维护 empty/full 的队列，不能仅凭 `r.valid` 声称“该 id 的数据已经填充”。[MSHRBuffer.scala:39](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:39)

这两个阶段寄存器的生命周期需要单独读：`mshr_task_s1` 由 `RegInit` 清零；每拍更新为 `old_valid && !s1_fire || s0_fire`，因此旧项在未 fire 时保持，fire 时清除，而同时发生 `s0_fire` 时以新 payload 覆盖。`task_s2.valid` 则每拍直接等于 `s1_fire`；下一拍没有新的 fire 时 valid 清零，而旧 bits 可保留但已无语义。RequestArb 没有 `flush`、`cancel` 或 `redirect` IO，因此源码中没有另一条对这些寄存器的取消/清空路径；复位和上述有效位更新是这里可证明的恢复范围。[RequestArb.scala:89](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:89) [RequestArb.scala:125](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:125) [RequestArb.scala:206](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:206)
-->

## 4. Parameters, Address, and State Carriers

### 4.1 Configuration Derivation Rather Than Simulation Measurement

| Item | Code Basis | Conclusion Under This Configuration | Limit |
| --- | --- | --- | --- |
| L2 capacity, banks, ways | `L2CacheConfig("1MB", banks=4)`, default eight ways | Each bank is 256 KiB; with 64 B lines this derives 512 sets per bank | Static derivation from configuration; elaboration was not run. |
| Line / D beat | L2 parameters default to 64 B blocks and 32 B D beats | `beatSize=2`; RequestArb explicitly has `require(beatSize == 2)` | This is an internal L2 data beat, not the splitting rule of an arbitrary CPU access. |
| MSHR count | L2 parameters default to `mshrs=16` | `MSHRBuffer` builds its register vector from global MSHR IDs | ID width comes from a larger ID space; do not infer every ID width from 16. |
| Address split | `parseAddress` | `off=[5:0]`; with four banks, bank bits are `[7:6]`, single-bank `set=[16:8]`, and higher bits are tag | Describes only the address representation of a this-bank `TaskBundle`. |

`parseAddress` first takes the line offset, skips bank bits to obtain the per-bank set, and then produces tag. A downstream path restores Slice/bank information before emitting CHI. These `set/tag` fields are internal-cache physical-address decomposition, not virtual-address translation. [CoupledL2.scala:186](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:186) [L2Param.scala:65](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:65) [RequestArb.scala:276](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:276)

### 4.2 RequestArb Itself Is Not a Multi-Entry FIFO

| Structure | Capacity Parameter | Occupancy State | Empty/Full or Admission Condition | Write / Release | Backpressured Object |
| --- | --- | --- | --- | --- | --- |
| `mshr_task_s1` | One `Valid[TaskBundle]` register | `mshr_task_s1.valid` | When valid, basic channel admission closes; S0 must also satisfy all MSHR conditions | `s0_fire` writes it; `s1_fire` consumes it, while a new S0 input can replace it on the same edge | MSHRCtl's `mshrTask.ready` and the basic ready of every channel. |
| `task_s2` | One `Valid[TaskBundle]` register | `task_s2.valid` | Not an independent FIFO; `s2_ready` and MCP2 protection limit the next S1 admission | `s1_fire` writes it, then it is sent to MainPipe as Valid | S1 admission of A/B/C and MSHR. |
| Refill MSHRBuffer | `mshrsAll x beatSize` register array | This buffer has no valid vector of its own | No local empty/full signal; associated MSHR state guarantees data availability | Multiple write ports write it; RequestArb reads by `mshrId` | No direct ready; an incorrect upstream state can result in a read of semantically unavailable data. |
| Release MSHRBuffer | `mshrsAll x beatSize` register array | Same as above | Same as above | Same as above; a snoop-hit release can instead use `snpHitReleaseIdx` | Same as above. |

The MSHRBuffer read port is Valid-only: when `r.valid` is asserted, `buffer(r.id)` is sampled into a response register. Its write-port selection permits at most two `wens` selecting the same entry, enforced by `PopCount(wens) <= 2`. It is therefore not a queue that self-maintains empty/full state, and `r.valid` alone cannot establish that data for that ID has already been filled. [MSHRBuffer.scala:39](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:39)

The two stage registers have distinct lifetimes. `mshr_task_s1` is cleared by `RegInit`; each cycle it updates to `old_valid && !s1_fire || s0_fire`. Thus an old item remains without fire, clears on fire, and is replaced by the new payload if `s0_fire` occurs concurrently. `task_s2.valid` directly equals `s1_fire` each cycle. Without a new fire on the next cycle, valid clears while old bits can remain but have no meaning. RequestArb has no `flush`, `cancel`, or `redirect` I/O, so source contains no additional cancellation/clear path for these registers. Reset and the valid-bit updates above are the recovery scope that source proves. [RequestArb.scala:89](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:89) [RequestArb.scala:125](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:125) [RequestArb.scala:206](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:206)

<!--
## 5. S0、复位与 MSHR 回流入口

### 5.1 复位门控的精确范围

`resetIdx` 从 `sets-1` 递减，到零后将 `resetFinish` 置位。A/B/C 的 `sink_ready_basic` 包含 `resetFinish`，所以它们在该门控未完成前不会从 RequestArb 获得 ready。按本配置 `sets=512`，因此 counter 初值是 511；它只是一个**本地准入延迟计数器**，没有接到 Directory 或 DataStorage 的 set 选择端，不能据此说 RequestArb 正在逐 set 清空数组，也不能写成未经波形验证的精确释放周期。[RequestArb.scala:78](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:78) [RequestArb.scala:153](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:153)

重要例外：`mshrTask.ready` 的表达式**没有** `resetFinish`。源码仅能证明 A/B/C 被这一个局部门控抑制；是否在复位期间实际有 MSHRCtl 任务送达，需要结合上游复位时序或 FST 验证，不能概括为“所有入口都停止”。[RequestArb.scala:111](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:111)

### 5.2 S0 的 MSHR `ready`

~~~scala
// RequestArb.scala:111-130（按 CHI 分支整理，节选）
io.mshrTask.ready :=
  !io.fromGrantBuffer.blockMSHRReqEntrance &&
  !s1_needs_replRead &&
  !(mshr_task_s1.valid && !s2_ready) &&
  !io.fromTXDAT.blockMSHRReqEntrance &&
  !io.fromTXRSP.blockMSHRReqEntrance &&
  !io.fromTXREQ.blockMSHRReqEntrance
val s0_fire = io.mshrTask.valid && io.mshrTask.ready
when (s0_fire) { mshr_task_s1.bits := io.mshrTask.bits }
~~~

| S0 阻塞源 | 谁产生 | 为什么阻塞 | 何时恢复 |
| --- | --- | --- | --- |
| `GrantBuffer.blockMSHRReqEntrance` | GrantBuffer | 预计的 Grant/GrantAck 容量不足 | 条目释放或预测占用下降。 |
| `s1_needs_replRead` | 当前暂存的 MSHR task | replacement-read 要先安全访问 Directory | Directory ready 且 `blockG_s1` 清除后 S1 fire。 |
| `mshr_task_s1.valid && !s2_ready` | RequestArb 本地 | S2 MCP2 保护周期不允许覆盖/推进该暂存任务 | `s2_ready` 恢复。 |
| `TXDAT/TXRSP/TXREQ` block | CHI 发送队列 | 预计在途项已接近或达到队列限制 | TX 队列出队降低预测占用。 |

MSHRCtl 不是把任意一个 MSHR 随机送入 RequestArb：它用 `FastArbiter` 汇合多个 MSHR 的 mainpipe task。`FastArbiter` 在输出 fire 后记录候选并用 RR mask 选择后续项，所以 MSHR 之间的公平性属于该上游模块；RequestArb 的 C/B/A 固定优先级不会改变这层事实。[tl2chi/MSHRCtl.scala:180](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:180) [FastArbiter.scala:30](/home/yanyusong/xs-memory-env/XiangShan/utility/src/main/scala/utility/FastArbiter.scala:30)
-->

## 5. S0, Reset, and the MSHR Return Ingress

### 5.1 Exact Scope of Reset Gating

`resetIdx` decrements from `sets-1`, and sets `resetFinish` after reaching zero. `sink_ready_basic` for A/B/C includes `resetFinish`, so they receive no ready from RequestArb before this gate completes. Under this configuration `sets=512`, so the counter starts at 511. It is only a **local admission-delay counter**: it does not connect to Directory or DataStorage set-select ports. It cannot establish that RequestArb clears arrays set by set, nor can it be described as an exact release-cycle count without waveform evidence. [RequestArb.scala:78](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:78) [RequestArb.scala:153](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:153)

Important exception: the `mshrTask.ready` expression **does not** include `resetFinish`. Source proves only that A/B/C are suppressed by this local gate. Whether MSHRCtl actually presents a task during reset requires upstream reset-timing or FST validation; it cannot be summarized as "all ingress stops." [RequestArb.scala:111](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:111)

### 5.2 MSHR `ready` in S0

~~~scala
// RequestArb.scala:111-130 (arranged by the CHI branch, excerpt)
io.mshrTask.ready :=
  !io.fromGrantBuffer.blockMSHRReqEntrance &&
  !s1_needs_replRead &&
  !(mshr_task_s1.valid && !s2_ready) &&
  !io.fromTXDAT.blockMSHRReqEntrance &&
  !io.fromTXRSP.blockMSHRReqEntrance &&
  !io.fromTXREQ.blockMSHRReqEntrance
val s0_fire = io.mshrTask.valid && io.mshrTask.ready
when (s0_fire) { mshr_task_s1.bits := io.mshrTask.bits }
~~~

| S0 Blocking Source | Producer | Why It Blocks | When It Recovers |
| --- | --- | --- | --- |
| `GrantBuffer.blockMSHRReqEntrance` | GrantBuffer | Predicted Grant/GrantAck capacity is insufficient | An entry is released or predicted occupancy falls. |
| `s1_needs_replRead` | Current held MSHR task | A replacement read must first access Directory safely | Directory becomes ready and `blockG_s1` clears before S1 fire. |
| `mshr_task_s1.valid && !s2_ready` | RequestArb local logic | The S2 MCP2 protection cycle does not permit overwriting / advancing the held task | `s2_ready` returns. |
| TXDAT/TXRSP/TXREQ blocks | CHI transmit queues | Predicted in-flight items are at or near queue limits | TX-queue dequeue reduces predicted occupancy. |

MSHRCtl does not present an arbitrary MSHR randomly to RequestArb. It converges multiple MSHR MainPipe tasks with `FastArbiter`. `FastArbiter` records a candidate after output fire and uses an RR mask to select later items, so fairness among MSHRs belongs to that upstream module. RequestArb's fixed C/B/A priority does not change that fact. [tl2chi/MSHRCtl.scala:180](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:180) [FastArbiter.scala:30](/home/yanyusong/xs-memory-env/XiangShan/utility/src/main/scala/utility/FastArbiter.scala:30)

<!--
## 6. S1：仲裁、背压和目录读

### 6.1 选择规则与 loser 行为

~~~scala
// RequestArb.scala:145-169（节选）
val sinkValids = VecInit(Seq(
  io.sinkC.valid && !block_C,
  io.sinkB.valid && !block_B,
  io.sinkA.valid && !block_A
)).asUInt
io.sinkA.ready := sink_ready_basic && !block_A && !sinkValids(1) && !sinkValids(0)
io.sinkB.ready := sink_ready_basic && !block_B && !sinkValids(0)
io.sinkC.ready := sink_ready_basic && !block_C
chnl_task_s1.bits := ParallelPriorityMux(sinkValids, Seq(C_task, B_task, A_task))
val task_s1 = Mux(mshr_task_s1.valid, mshr_task_s1, chnl_task_s1)
val s1_fire = task_s1.valid && !mshr_replRead_stall && s2_ready
~~~

`ParallelPriorityMux` 使用给定序列的左侧优先级；因此仲裁是固定的，而非轮转。[ParallelMux.scala:88](/home/yanyusong/xs-memory-env/XiangShan/utility/src/main/scala/utility/ParallelMux.scala:88)

| 同拍可见情况 | 结果 | loser / 反馈 |
| --- | --- | --- |
| 已有 `mshr_task_s1.valid=1`，且不被 replacement-read stall | 该 MSHR task 进入 S1，覆盖所有 C/B/A 候选 | A/B/C 基础 ready 因已有 MSHR 变低，保持 valid/bits。 |
| 旧 MSHR 槽为空，C/B/A 都合格 | C fire，B/A ready=0 | B/A 按 Decoupled 合约保持，等待 C 不再合格或被 block。 |
| C 被 `block_C`，B/A 合格 | B fire | C 已被从候选集中剔除；A 仍因 B 候选而 ready=0。 |
| C、B 被各自 block，A 合格 | A fire | A 只有在更高优先级候选均不合格时进入。 |
| 新 MSHR 与 C/B/A 同拍均可接受，旧 MSHR 槽为空 | `mshrTask` S0 fire，**同拍只有一个获胜** channel task 可进入 S1 | 新 MSHR 在下拍成为暂存任务后才取得 MSHR 优先级。 |
| C 或 B 长期持续合格 | A 可能持续得不到 ready | 源码没有轮转状态，A 无 starvation bound。 |

这解释了 `ready` 的责任边界：RequestArb 不缓存 C/B/A loser，而是通过 ready 使其生产者或前级 buffer 保留请求。不能从 `sinkA.valid=1` 推导 A 已被分配或已进入 MainPipe。

### 6.2 `block_A/B/C` 的来源

| 来源 | 对哪个入口的有效影响 | 代码层面的目的 |
| --- | --- | --- |
| MSHRCtl | B 在 MSHR 全满时阻塞；A 在仅剩最后一个保留项时阻塞；C 不由该容量条件阻塞 | 给 B 留出能处理 snoop 的 MSHR 资源。 [MSHRCtl.scala:106](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:106) |
| MainPipe | C 对 S2 同 set 冲突阻塞；B 对 S2 同 set、S3-S5 同 tag/set 冲突阻塞；A 在 MainPipe 侧为 false | 避免与正在推进的目录/数据操作冲突；A 的同 set 等待交给 RequestBuffer。 [MainPipe.scala:909](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:909) |
| GrantBuffer | A 受 grant queue、inflight Grant 和可选 prefetch 响应容量限制；C 只受 `noSpaceForSinkReq` 容量限制；B 受同 set/tag 的 inflight Grant 限制；MSHR 有保留阈值 | 分别保护返回 grant/ack 存储容量与 B 类地址冲突。 [GrantBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/GrantBuffer.scala:292) |
| TXDAT / TXRSP | B 和 MSHR | 下游 CHI 数据/响应队列预计容量压力反推到入口。 [TXDAT.scala:61](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TXDAT.scala:61) [TXRSP.scala:50](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TXRSP.scala:50) |
| TXREQ | MSHR | 下游 CHI 请求队列预计容量压力。 [TXREQ.scala:50](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TXREQ.scala:50) |

基础准入还要求 `dirRead_s1.ready && resetFinish && !mshr_task_s1.valid && s2_ready`。Directory 的 read ready 又要求没有 meta/tag write 和 replacement write 占用。故“C 的优先级最高”只是在所有上述条件已经允许的候选中成立，而不是绕过 Directory 端口。[RequestArb.scala:153](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:153) [Directory.scala:322](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:322)

### 6.3 Directory request 和 `s1Entrance`

普通 channel 任务在 S1 使用 `task_s1` 的 tag/set 发 Directory read，通常开全 way mask；MSHR retry 会排除先前记录的 way。replacement-read MSHR 是例外：只有它需要触发 replacement 信息读时，Directory 读才在 MSHR 路径中生效。Directory 最终以 `read.fire` 启动 tag/meta SRAM 读，因此 RequestArb 不会在 Directory 不 ready 时偷偷消费 channel task。[RequestArb.scala:174](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:174) [Directory.scala:211](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:211)

`s1Entrance` 是 RequestArb 向 RequestBuffer 的定点反馈：B/C fire，或 `s2_ready` 下带 `metaWen` 的暂存 MSHR，会携带其 set；RequestBuffer 可据此阻止同 set 的 A 提前穿透。注意该 MSHR 条件没有并入 `!mshr_replRead_stall`，因此它是由源码表达式定义的保守占用提示，不能擅自改写为“只有实际 S1 fire 才断言”。[RequestArb.scala:191](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:191) [RequestBuffer.scala:208](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestBuffer.scala:208)
-->

## 6. S1: Arbitration, Backpressure, and Directory Read

### 6.1 Selection Rules and Loser Behavior

~~~scala
// RequestArb.scala:145-169 (excerpt)
val sinkValids = VecInit(Seq(
  io.sinkC.valid && !block_C,
  io.sinkB.valid && !block_B,
  io.sinkA.valid && !block_A
)).asUInt
io.sinkA.ready := sink_ready_basic && !block_A && !sinkValids(1) && !sinkValids(0)
io.sinkB.ready := sink_ready_basic && !block_B && !sinkValids(0)
io.sinkC.ready := sink_ready_basic && !block_C
chnl_task_s1.bits := ParallelPriorityMux(sinkValids, Seq(C_task, B_task, A_task))
val task_s1 = Mux(mshr_task_s1.valid, mshr_task_s1, chnl_task_s1)
val s1_fire = task_s1.valid && !mshr_replRead_stall && s2_ready
~~~

`ParallelPriorityMux` uses left-to-right priority of the supplied sequence, so arbitration is fixed rather than rotating. [ParallelMux.scala:88](/home/yanyusong/xs-memory-env/XiangShan/utility/src/main/scala/utility/ParallelMux.scala:88)

| Same-Cycle Visible Case | Result | Loser / Feedback |
| --- | --- | --- |
| `mshr_task_s1.valid=1` already, with no replacement-read stall | That MSHR task enters S1 and overrides all C/B/A candidates | Basic ready for A/B/C falls because a held MSHR exists; they retain valid/bits. |
| Old MSHR slot empty; C/B/A all eligible | C fires; B/A ready is 0 | B/A retain their Decoupled requests until C is no longer eligible or is blocked. |
| C is `block_C`; B/A eligible | B fires | C has been removed from candidates; A remains ready=0 because B is a candidate. |
| C and B are each blocked; A eligible | A fires | A enters only when every higher-priority candidate is ineligible. |
| A new MSHR and C/B/A can all be admitted in the same cycle, while the old MSHR slot is empty | `mshrTask` fires in S0, and **only one winning** channel task can enter S1 in that cycle | The new MSHR receives MSHR priority only after becoming held on the next cycle. |
| C or B stays eligible indefinitely | A can stay without ready indefinitely | Source has no rotation state, so A has no starvation bound. |

This explains the boundary of `ready`: RequestArb does not buffer a C/B/A loser. It relies on the producer or preceding buffer to retain the request through `ready=0`. `sinkA.valid=1` does not mean that A has been allocated or entered MainPipe.

### 6.2 Sources of `block_A/B/C`

| Source | Effective Impact on Which Ingress | Code-Level Purpose |
| --- | --- | --- |
| MSHRCtl | B blocks when MSHRs are full; A blocks when only the final reserved entry remains; C is not blocked by this capacity condition | Preserves MSHR resources capable of handling a snoop on B. [MSHRCtl.scala:106](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:106) |
| MainPipe | C blocks on same-set S2 conflict; B blocks on same-set S2 and same-tag/set S3--S5 conflicts; A is false on the MainPipe side | Avoids conflicts with ongoing directory/data operations; A's same-set waiting belongs to RequestBuffer. [MainPipe.scala:909](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:909) |
| GrantBuffer | A is limited by grant queue, in-flight Grant, and optional prefetch-response capacity; C only by `noSpaceForSinkReq`; B by an in-flight Grant at the same set/tag; MSHR has a reservation threshold | Separately protects Grant/GrantAck storage capacity and B-class address conflicts. [GrantBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/GrantBuffer.scala:292) |
| TXDAT / TXRSP | B and MSHR | Pushes predicted capacity pressure from downstream CHI data/response queues to ingress. [TXDAT.scala:61](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TXDAT.scala:61) [TXRSP.scala:50](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TXRSP.scala:50) |
| TXREQ | MSHR | Predicted capacity pressure in the downstream CHI request queue. [TXREQ.scala:50](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TXREQ.scala:50) |

Basic admission also requires `dirRead_s1.ready && resetFinish && !mshr_task_s1.valid && s2_ready`. Directory read ready itself requires that no meta/tag write or replacement write is occupying it. Thus "C has highest priority" holds only among candidates already permitted by all of the above conditions; it does not bypass the Directory port. [RequestArb.scala:153](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:153) [Directory.scala:322](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:322)

### 6.3 Directory Request and `s1Entrance`

An ordinary channel task uses `task_s1` tag/set to read Directory in S1, normally with an all-way mask. An MSHR retry excludes its previously recorded way. A replacement-read MSHR is the exception: Directory read is active on an MSHR path only if it must obtain replacement information. Directory actually starts the tag/meta SRAM read on `read.fire`, so RequestArb does not secretly consume a channel task when Directory is not ready. [RequestArb.scala:174](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:174) [Directory.scala:211](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:211)

`s1Entrance` is directed feedback from RequestArb to RequestBuffer. A B/C fire, or a held MSHR with `metaWen` while `s2_ready`, carries its set. RequestBuffer can use it to prevent a same-set A from passing through too early. The MSHR condition does not include `!mshr_replRead_stall`, so it is a conservative occupancy indication defined by the source expression; it must not be rewritten as "asserted only on actual S1 fire." [RequestArb.scala:191](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:191) [RequestBuffer.scala:208](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestBuffer.scala:208)

<!--
## 7. S2、MCP2 保护与 MSHRBuffer 读

### 7.1 阶段关系

| 阶段 | 主要寄存器/接口 | 做什么 | 可阻塞来源 |
| --- | --- | --- | --- |
| Reset | `resetIdx`、`resetFinish` | 使 A/B/C entry 暂不准入 | 复位计数未完成。 |
| S0 | `mshrTask` Decoupled | 接收并暂存 MSHR 回流任务 | GrantBuffer、TX 队列、replacement-read、S2 保护。 |
| S1 | `task_s1`、`dirRead_s1`、`taskInfo_s1` | 在已有 MSHR 与 C/B/A 间选择，触发目录读 | block、Directory ready、MCP2、replacement-read。 |
| S2 | `task_s2`、`taskToPipe_s2` | 把已 S1 fire 的 task 寄存并送 MainPipe | 没有 MainPipe ready；下一次准入受 `s2_ready` 控制。 |
| S3 及后续 | MainPipe / Directory / DataStorage | 分类、读写数组、MSHR 分配和 CHI/TL 输出 | 属于相邻 MainPipe 的职责。 |

~~~mermaid
stateDiagram-v2
  [*] -- > ResetGate
  ResetGate -- > ChannelEligible: resetFinish
  ChannelEligible -- > MshrHeld: mshrTask.fire
  ChannelEligible -- > ChannelSelected: sinkC/B/A.fire
  MshrHeld -- > S2Task: s1_fire && !replReadStall
  ChannelSelected -- > S2Task: s1_fire
  S2Task -- > ChannelEligible: local s2_ready returns
  MshrHeld -- > MshrHeld: replRead stall
~~~

上图是依据 valid 寄存器和握手抽象出的**状态关系图**，不是源码中存在的 `Enum` FSM；源码的实际状态承载为 `mshr_task_s1.valid`、`task_s2.valid` 和局部 stall 信号。

### 7.2 DataStorage MCP2 的入口保护

~~~scala
// RequestArb.scala:199-217（节选）
val s1_AHint_fire = io.sinkA.fire && io.sinkA.bits.opcode === Hint
val ds_mcp2_stall = RegNext(s1_fire && !s1_AHint_fire)
val s2_ready = !ds_mcp2_stall
task_s2.valid := s1_fire
when (s1_fire) { task_s2.bits := task_s1.bits }
io.taskToPipe_s2 := task_s2
~~~

DataStorage 的 MCP2 接口禁止连续 `en`，并要求对应 request/data 稳定；RequestArb 因而在一个非 A-Hint S1 fire 的下一拍压低 `s2_ready`。这只是入口最早相邻周期约束：Directory、block、MSHR 和输出资源仍可能令实际吞吐更低；A Hint 是源码中的明确例外。[DataStorage.scala:119](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/DataStorage.scala:119)

下图是从上述组合/寄存关系推演的教学波形，不是 FST 截图。假设 reset 已结束、C 和一个**非 Hint** A0 同拍有效、没有其它 block。C 在第一个可接收拍获胜，A0 在 C 优先和随后 MCP2 气泡期间保持 `valid` 与 `{tag,set}`；`s2_ready` 变低时，三个 channel 的 ready 和 `s1_fire` 都不能继续为高。`sinkC.fire` 与 `sinkA.fire` 是 Chisel `DecoupledIO.fire` 的等价命名。

~~~waveform-draw
{
  "signal": [
    {"name": "clk", "wave": "p......."},
    {"name": "sinkC.valid", "wave": "010....."},
    {"name": "sinkA.valid", "wave": "01..0..."},
    {"name": "sinkA.bits.{tag,set}", "wave": "x=..x...", "data": ["A0"]},
    {"name": "sinkC.ready", "wave": "110101.."},
    {"name": "sinkA.ready", "wave": "100101.."},
    {"name": "sinkC.fire", "wave": "010....."},
    {"name": "sinkA.fire", "wave": "00010..."},
    {"name": "s1_fire", "wave": "01010..."},
    {"name": "ds_mcp2_stall", "wave": "001010.."},
    {"name": "s2_ready", "wave": "110101.."},
    {"name": "taskToPipe_s2.valid", "wave": "001010.."}
  ],
  "config": {"hscale": 1}
}
~~~

### 7.3 Refill/Release 数据选择

| 读口 | `valid` 的有效条件 | 读 id | 含义 |
| --- | --- | --- | --- |
| `refillBufRead_s2` | MSHR task 且是 release/refill 数据路径，或向上返回数据且不使用 probe data | `mshrId` | 将 MSHR 先前缓存的数据提供给后续 MainPipe。 |
| `releaseBufRead_s2` | MSHR task 的 `readProbeDataDown`，或向上数据且使用 probe data；CHI 非 MSHR snoop hit 且需 release data 也会触发 | 通常 `mshrId`；snoop hit release 时 `snpHitReleaseIdx` | 选择 release/probe 侧保存的数据。 |

这里的条件来自实际 `Mux` 和 `when`，而不是上方局部 `snoopNeedData` 等未使用变量。Slice 把这两个读口的响应延迟一拍后送给 MainPipe S3；所以“读请求发出”与“MainPipe 获得数据”不是同一个周期。[RequestArb.scala:219](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:219) [tl2chi/Slice.scala:121](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:121) [tl2chi/Slice.scala:123](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:123)
-->

## 7. S2, MCP2 Protection, and MSHRBuffer Reads

### 7.1 Stage Relationships

| Stage | Main Register / Interface | Function | Possible Blocking Sources |
| --- | --- | --- | --- |
| Reset | `resetIdx`, `resetFinish` | Temporarily prevents admission at A/B/C ingress | Reset count is unfinished. |
| S0 | `mshrTask` Decoupled | Accepts and holds a returned MSHR task | GrantBuffer, TX queues, replacement read, S2 protection. |
| S1 | `task_s1`, `dirRead_s1`, `taskInfo_s1` | Selects between a held MSHR and C/B/A, starts the directory read | Blocks, Directory ready, MCP2, replacement read. |
| S2 | `task_s2`, `taskToPipe_s2` | Registers an S1-fired task and sends it to MainPipe | No MainPipe ready; the next admission is limited by `s2_ready`. |
| S3 and later | MainPipe / Directory / DataStorage | Classifies, accesses arrays, allocates MSHRs, and emits CHI/TL | Responsibilities of neighboring MainPipe logic. |

~~~mermaid
stateDiagram-v2
  [*] --> ResetGate
  ResetGate --> ChannelEligible: resetFinish
  ChannelEligible --> MshrHeld: mshrTask.fire
  ChannelEligible --> ChannelSelected: sinkC/B/A.fire
  MshrHeld --> S2Task: s1_fire && !replReadStall
  ChannelSelected --> S2Task: s1_fire
  S2Task --> ChannelEligible: local s2_ready returns
  MshrHeld --> MshrHeld: replRead stall
~~~

This is a **state-relationship diagram** abstracted from valid registers and handshakes, not an `Enum` FSM present in source. Source state is carried by `mshr_task_s1.valid`, `task_s2.valid`, and local stall signals.

### 7.2 DataStorage MCP2 Ingress Protection

~~~scala
// RequestArb.scala:199-217 (excerpt)
val s1_AHint_fire = io.sinkA.fire && io.sinkA.bits.opcode === Hint
val ds_mcp2_stall = RegNext(s1_fire && !s1_AHint_fire)
val s2_ready = !ds_mcp2_stall
task_s2.valid := s1_fire
when (s1_fire) { task_s2.bits := task_s1.bits }
io.taskToPipe_s2 := task_s2
~~~

DataStorage's MCP2 interface prohibits consecutive `en` and requires the corresponding request/data to remain stable. RequestArb therefore lowers `s2_ready` in the cycle after a non-A-Hint S1 fire. This is only the earliest adjacent-cycle ingress constraint: Directory, blocks, MSHR, and output resources can still reduce actual throughput. A Hint is an explicit source exception. [DataStorage.scala:119](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/DataStorage.scala:119)

The diagram below is a teaching waveform derived from the combinational/register relationship, not an FST screenshot. It assumes reset has completed, C and one **non-Hint** A0 are valid in the same cycle, and no other blocks exist. C wins in the first admissible cycle. A0 retains `valid` and `{tag,set}` through C priority and the following MCP2 bubble. When `s2_ready` falls, the three channel ready signals and `s1_fire` cannot remain high. `sinkC.fire` and `sinkA.fire` are equivalent names for Chisel `DecoupledIO.fire`.

~~~waveform-draw
{
  "signal": [
    {"name": "clk", "wave": "p......."},
    {"name": "sinkC.valid", "wave": "010....."},
    {"name": "sinkA.valid", "wave": "01..0..."},
    {"name": "sinkA.bits.{tag,set}", "wave": "x=..x...", "data": ["A0"]},
    {"name": "sinkC.ready", "wave": "110101.."},
    {"name": "sinkA.ready", "wave": "100101.."},
    {"name": "sinkC.fire", "wave": "010....."},
    {"name": "sinkA.fire", "wave": "00010..."},
    {"name": "s1_fire", "wave": "01010..."},
    {"name": "ds_mcp2_stall", "wave": "001010.."},
    {"name": "s2_ready", "wave": "110101.."},
    {"name": "taskToPipe_s2.valid", "wave": "001010.."}
  ],
  "config": {"hscale": 1}
}
~~~

### 7.3 Refill/Release Data Selection

| Read Port | Effective Condition for `valid` | Read ID | Meaning |
| --- | --- | --- | --- |
| `refillBufRead_s2` | An MSHR task on a release/refill data path, or data returned upward without using probe data | `mshrId` | Supplies data previously retained by the MSHR to later MainPipe processing. |
| `releaseBufRead_s2` | An MSHR task's `readProbeDataDown`, or upward data using probe data; a CHI non-MSHR snoop hit needing release data also triggers it | Usually `mshrId`; `snpHitReleaseIdx` for snoop-hit release | Selects data retained on the release/probe side. |

These conditions follow actual `Mux` and `when` expressions rather than unused local variables such as `snoopNeedData`. Slice delays both read-port responses by one cycle before sending them to MainPipe S3. Consequently, "the read request is issued" and "MainPipe obtains data" are not the same cycle. [RequestArb.scala:219](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:219) [tl2chi/Slice.scala:121](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:121) [tl2chi/Slice.scala:123](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:123)

<!--
## 8. 两条动态路径

### 8.1 普通 A/C/B 任务的短入口路径

1. SinkA 经 RequestBuffer、SinkC 经 release task、CHI snoop 经 RXSNP queue 到达对应 Decoupled 输入。
2. RequestArb 汇总 MainPipe、MSHRCtl、GrantBuffer 和 TX 预测 block；先排除不合格候选。
3. 若没有已暂存 MSHR，按 C、B、A 固定顺序置 ready；仅获胜者 fire。
4. 获胜 task 在 S1 发 Directory read，同时产生 `taskInfo_s1`；B/C 还可能产生 `s1Entrance`。
5. `s1_fire` 后，task 在 S2 成为 `taskToPipe_s2.valid`，由 MainPipe 接续处理。

### 8.2 MSHR 回流和 replacement-read 路径

1. MSHRCtl 的 `FastArbiter` 选择一个 MSHR task；只有 `mshrTask.valid && ready` 才进入 S0。
2. `s0_fire` 将其 payload 写入一项 `mshr_task_s1`，同拍仍可能有一个 channel task 获胜。
3. 下拍该暂存 MSHR 覆盖 channel 选择。若它是需要 replacement read 的特定回传任务，`s1_needs_replRead` 令它等待 Directory ready 与 `blockG_s1`。
4. S1 fire 后，RequestArb 按 `mshrTask/replTask/useProbeData/chiOpcode` 选择 Refill 或 Release buffer 读，并把 task 交给 MainPipe。
5. MainPipe 再依据 Directory/DataStorage/下游 CHI 结果推进事务；RequestArb 不维护该事务的完整生命周期。
-->

## 8. Two Dynamic Paths

### 8.1 Short Ingress Path for Ordinary A/C/B Tasks

1. SinkA arrives through RequestBuffer, SinkC through a release task, and a CHI snoop through the RXSNP queue at their respective Decoupled inputs.
2. RequestArb aggregates block predictions from MainPipe, MSHRCtl, GrantBuffer, and TX, then removes ineligible candidates.
3. If no MSHR is already held, it asserts ready in fixed C, B, A order; only the winner fires.
4. The winning task reads Directory in S1 and produces `taskInfo_s1`; B/C can also produce `s1Entrance`.
5. After `s1_fire`, the task becomes `taskToPipe_s2.valid` in S2 for continued MainPipe processing.

### 8.2 MSHR Return and Replacement-Read Path

1. MSHRCtl's `FastArbiter` selects one MSHR task. It enters S0 only on `mshrTask.valid && ready`.
2. `s0_fire` writes its payload into the one-entry `mshr_task_s1`; one channel task can still win in S1 during the same cycle.
3. On the next cycle, the held MSHR overrides channel selection. If it is a specific returned task requiring a replacement read, `s1_needs_replRead` makes it wait for Directory ready and `blockG_s1` to clear.
4. After S1 fire, RequestArb chooses a Refill or Release buffer read based on `mshrTask/replTask/useProbeData/chiOpcode` and hands the task to MainPipe.
5. MainPipe then advances the transaction using Directory/DataStorage/downstream-CHI results. RequestArb does not retain the transaction's complete lifetime.

<!--
## 9. CHI、地址、事务 ID 与跨边界代码解析

### 9.1 CHI 事务边界

RequestArb 不直接向 TXREQ/TXDAT/TXRSP 发包。它通过 `fromTX*` 观察预测容量压力，向 MainPipe 送 `TaskBundle`；MainPipe/MSHRCtl 再连接 TX 队列。CHI snoop 输入在 RXSNP 中把协议字段组装为 task，故 `sinkB` 的名字不能被解释为 V2 下的 TL B。`sourceId` 也不能当作外部 CHI TxnID：CHI top 会给 cacheable 和 MMIO 事务使用不同的 TxnID 编码空间。[RXSNP.scala:131](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:131) [tl2chi/Slice.scala:69](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:69) [TL2CHICoupledL2.scala:99](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:99)

### 9.2 虚拟页、cache line、MMIO 的边界

| Boundary | First fragment | Second fragment | Independent checks | Merge/ordering state | Failure and recovery |
| --- | --- | --- | --- | --- | --- |
| Virtual page | RequestArb 中没有页内第一片段：它收到的只是已经形成的本 bank `TaskBundle.tag/set/off`。 | 没有下一页 task、ASID/VMID、TLB 或权限字段的第二片段状态。 | 上游 TLB/访存模块必须分别完成翻译、权限和异常判定；RequestArb 只可验证到 `sink*.valid/ready/fire`。 [RequestArb.scala:32](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:32) [RequestArb.scala:132](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:132) | 无 fragment buffer、merge bit 或跨页排序状态。 | 模块没有页故障、redirect、flush/cancel 输出/输入，无法从此模块证明 fault/stall/recovery；责任在上游翻译与异常路径。 |
| Cache line | 一个已路由的 task 用 `tag/set/off` 指向一条 line；RequestArb 可对它进行 C/B/A/MSHR 准入和 Directory read。 | 没有“下一 line”保留字段或拆分器；跨 line 访问必须在上游形成第二个独立 task。 | 分别检查两个 task 的 `valid && ready`、`tag/set`、Directory 请求和 MSHR 资源；`beatSize==2` 仅是 L2 内部 line 数据 beat。 [CoupledL2.scala:186](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:186) [RequestArb.scala:174](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:174) [RequestArb.scala:276](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:276) | 无 response assembler 或跨 line ordering state；每个 task 独立进入仲裁，后续 MSHR/MainPipe 才拥有各自的长时状态。 | RequestArb 能做的恢复只有不置 ready 以反压 loser；没有本地 replay、合并、取消或跨 line 重组。 |
| MMIO / uncache | V2 CHI 的 `TaskBundle` 可携带 `memAttr.cacheable/device`，但 RequestArb 源码不读取该字段来分类或选路。 [Common.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Common.scala:145) [Message.scala:377](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Message.scala:377) | 没有由 RequestArb 维护的 MMIO response fragment、side-effect ordering 或 uncache entry 状态。 | 在 `TL2CHICoupledL2`/MMIO bridge 验证 PMA/PBMT/MMIO 分类和高位 TxnID 编码；不能把“携带 memAttr”误写成“已由 RequestArb 分类”。 [RequestArb.scala:32](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:32) [TL2CHICoupledL2.scala:99](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:99) | 无 MMIO queue、commit gate 或 response arbiter。 | 模块中没有设备异常、重发、commit wait 或 cancel 机制；这些恢复语义不能由 RequestArb 证明。 |

这三个边界都不构成 RequestArb 内部的“跨越状态机”。若把“边界 + flush/cancel”强行加到本模块输入，无法驱动出源码不存在的端口；应在拥有翻译、MMIO bridge 或上游 task 生命周期的模块测试该组合。RequestArb 的可证明职责仅是：对已经成为本 bank 一致性 task 的输入进行资源感知准入、目录读和短流水转交。
-->

## 9. CHI, Address, Transaction ID, and Cross-Boundary Code Analysis

### 9.1 CHI Transaction Boundary

RequestArb does not directly send packets to TXREQ/TXDAT/TXRSP. It observes predicted capacity pressure through `fromTX*` and sends a `TaskBundle` to MainPipe; MainPipe/MSHRCtl subsequently connect TX queues. The CHI snoop input assembles protocol fields into a task in RXSNP, so the name `sinkB` cannot be interpreted as TL B in V2. Nor can `sourceId` be treated as an external CHI TxnID: the CHI top uses different TxnID encoding spaces for cacheable and MMIO transactions. [RXSNP.scala:131](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:131) [tl2chi/Slice.scala:69](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:69) [TL2CHICoupledL2.scala:99](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:99)

### 9.2 Boundaries of Virtual Pages, Cache Lines, and MMIO

| Boundary | First Fragment | Second Fragment | Independent Checks | Merge/Ordering State | Failure and Recovery |
| --- | --- | --- | --- | --- | --- |
| Virtual page | RequestArb has no first within-page fragment. It receives only an already formed this-bank `TaskBundle.tag/set/off`. | It has no second-page-task, ASID/VMID, TLB, or permission-field state. | Upstream TLB/memory modules must independently complete translation, permission, and exception determination; RequestArb can only validate `sink*.valid/ready/fire`. [RequestArb.scala:32](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:32) [RequestArb.scala:132](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:132) | No fragment buffer, merge bit, or cross-page ordering state. | The module has no page fault, redirect, flush/cancel input/output. Fault/stall/recovery cannot be proven from it and belongs to upstream translation and exception paths. |
| Cache line | One routed task uses `tag/set/off` to address one line. RequestArb can apply C/B/A/MSHR admission and Directory read to it. | No retained "next line" field or splitter; a cross-line access must become a second independent task upstream. | Check each task's `valid && ready`, `tag/set`, Directory request, and MSHR resources independently. `beatSize==2` is only an internal L2 line-data beat. [CoupledL2.scala:186](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:186) [RequestArb.scala:174](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:174) [RequestArb.scala:276](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:276) | No response assembler or cross-line ordering state. Each task independently enters arbitration; later MSHR/MainPipe logic owns its long-lived state. | RequestArb recovery consists only of withholding ready to backpressure a loser; it has no local replay, merge, cancellation, or cross-line reconstruction. |
| MMIO / uncache | A V2 CHI `TaskBundle` can carry `memAttr.cacheable/device`, but RequestArb source does not read that field to classify or route. [Common.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Common.scala:145) [Message.scala:377](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Message.scala:377) | No MMIO response fragment, side-effect ordering, or uncache-entry state belongs to RequestArb. | Validate PMA/PBMT/MMIO classification and high TxnID encoding in `TL2CHICoupledL2` / MMIO bridge. Carrying `memAttr` must not be written as RequestArb having classified it. [RequestArb.scala:32](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:32) [TL2CHICoupledL2.scala:99](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:99) | No MMIO queue, commit gate, or response arbiter. | No device exception, retry, commit wait, or cancellation exists in the module; those recovery semantics cannot be proven by RequestArb. |

None of these boundaries forms an internal "crossing state machine" in RequestArb. Adding "boundary + flush/cancel" as if it were an input to this module cannot drive a source-nonexistent port. Test the combination in the owner of translation, MMIO bridge, or upstream task lifetime. RequestArb's provable responsibility is limited to resource-aware admission, Directory reads, and short-pipeline handoff for inputs that have already become this-bank coherence tasks.

<!--
## 10. 异常、可见性、性能与吞吐边界

### 10.1 架构可见性

RequestArb 本身没有 Difftest 端口、异常输出、TLB/页故障接口、redirect/flush/cancel 输入，也不持有架构寄存器状态。Directory/DataStorage 的错误和协议响应由 MainPipe 及上层继续处理，不能归因成 RequestArb “抛出异常”。本模块可观察的是 `XSPerfAccumulate` 的请求和 stall 计数，例如 MSHR、sink、Directory、MCP2、优先级和 TX 队列停顿分类；它们是性能观测，不是架构提交事件。[RequestArb.scala:303](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:303)

### 10.2 不给出伪精确周期数

| 关系 | 源码可证明 | 不能证明 |
| --- | --- | --- |
| channel 准入到 S2 | S1 fire 后 `task_s2` 在寄存器边界成为有效，MainPipe 接收 ValidIO | 从 L1 发出到 MainPipe 完成的固定拍数。 |
| 连续非 A-Hint | 前一个 `s1_fire` 的下一拍会压低 `s2_ready` | 所有请求恒为隔拍吞吐；其它 block 还会扩大间隔。 |
| MSHR 回流 | 新任务先 S0 fire，下一拍才能作为已暂存 MSHR 取得优先级 | MSHR 的端到端完成时间或公平性延迟上界。 |
| Directory read | 入口必须等待 `dirRead_s1.ready`，Directory 在 `read.fire` 后读阵列 | hit/miss、替换、CHI 返回的完整服务时间。 |
-->

## 10. Exception, Visibility, Performance, and Throughput Boundaries

### 10.1 Architectural Visibility

RequestArb itself has no Difftest port, exception output, TLB/page-fault interface, redirect/flush/cancel input, or architectural register state. Directory/DataStorage errors and protocol responses continue through MainPipe and higher layers; they must not be attributed to RequestArb as "raising an exception." Observable module signals include `XSPerfAccumulate` request and stall counters, such as MSHR, sink, Directory, MCP2, priority, and TX-queue stall classifications. They are performance observations, not architectural commit events. [RequestArb.scala:303](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:303)

### 10.2 No Spurious Exact Cycle Counts

| Relationship | What Source Can Prove | What It Cannot Prove |
| --- | --- | --- |
| Channel admission to S2 | After S1 fire, `task_s2` becomes valid at the register boundary and MainPipe receives ValidIO | A fixed number of cycles from L1 issue to MainPipe completion. |
| Consecutive non-A-Hint tasks | The cycle after a previous `s1_fire` lowers `s2_ready` | Every request always has every-other-cycle throughput; other blocks can increase the interval. |
| MSHR return | A new task first fires at S0 and cannot receive held-MSHR priority until the next cycle | MSHR end-to-end completion time or a fairness-delay bound. |
| Directory read | Ingress must wait for `dirRead_s1.ready`, and Directory reads its array after `read.fire` | Complete service time for hit/miss, replacement, and CHI return. |

<!--
## 11. 验证特别注意

下表是模块专属的验证清单。每项都记录有效 task 的 `tag/set/channel/mshrId`，并以 Decoupled `valid && ready` 判断真正接收，不能用单独 valid 推断结果。

| Verification ID | Risk / invariant | Directed stimulus | Expected observation | Required checker / coverage |
| --- | --- | --- | --- | --- |
| RA-RESET-ABC | A/B/C 必须在 `resetFinish=0` 时不被接收；此结论不延伸到 `mshrTask`。 [RequestArb.scala:78](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:78) [RequestArb.scala:153](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:153) | reset 后持续给 A/B/C valid，记录 `resetIdx`、`resetFinish`、三个 ready 和 mshrTask ready。 | 计数未完成前三路 ready 均低；首次合法 channel fire 只在 `resetFinish=1` 后。mshrTask 单独按其 ready 式判定。 | `Handshake checker` 覆盖 reset release；`FSM checker` 用 `resetFinish` 本地状态模型；cross `A/B/C x resetFinish`。 |
| RA-HOLD-BACKPRESSURE | `valid=1 && ready=0` 的 loser 不得被接收，且 `{tag,set,channel}` 必须保持到 fire。 [RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [RequestArb.scala:155](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:155) | A 与 C 同拍 valid，保持 A valid/bits，直到 C 消失或被 block。 | C fire 前 A.ready=0；A bits 连续稳定；A 仅在随后自身 `valid && ready` 时产生一次 fire。 | `Handshake checker` 检查无 accept/no double accept；`Arbiter checker` 检查 loser persistence；coverage `C wins -> A hold -> A fire`。 |
| RA-CBA-PRIORITY | 合格候选必须固定 `C > B > A`，并且一个周期只有一个 channel fire。 [RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [RequestArb.scala:155](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:155) | 分别施加 `C+B+A`、`block_C+B+A`、`block_C+block_B+A`，维持 Directory ready、`s2_ready` 和 reset 条件均满足。 | 三组分别只看到 C、B、A fire；B 在 C 合格时、A 在 B/C 合格时 ready=0。 | `Arbiter checker` 做 one-hot fire、优先级和 ready feedback；cross `block_C/block_B x chosen_channel`。 |
| RA-MSHR-S0-OVERLAP | MSHR 槽的保持/覆盖必须符合 `old_valid && !s1_fire || s0_fire`；新 MSHR 不得同拍抢占 channel。 [RequestArb.scala:120](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:120) [RequestArb.scala:125](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:125) | 先让 mshrTask S0 fire，再下一拍给 C/B/A；另测旧槽为空时 mshrTask 与 C/B/A 同拍有效。 | 已暂存 MSHR 在下一拍胜出；旧槽为空时 S0 接收 MSHR 且只有一个 channel task 可 S1 fire。 | `Occupancy checker` 建一项 `mshr_task_s1` valid/payload 参考模型；`Arbiter checker` cross `old_mshr_valid x s0_fire x channel_fire`。 |
| RA-DIR-WRITE-CONFLICT | Directory 写/replace 占用时不得接受 channel，也不得将 `chnl_task_s1.valid` 误当 SRAM 读。 [Directory.scala:322](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:322) [RequestArb.scala:153](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:153) | 使 `metaWReq`、`tagWReq`、`replacerWen` 分别有效，同时持续驱动每个 channel。 | `dirRead_s1.ready=0`，因此三个 channel 不 fire；解除冲突后按固定优先级恢复。 | `Storage conflict checker` 检查 read/write 互斥；`Handshake checker` 检查 fire 只在 Directory ready 后发生；cross 每种写源 x A/B/C。 |
| RA-MCP2-HINT | 非 A-Hint S1 fire 的下一拍必须令 `s2_ready=0`；A Hint 不设置该下一拍 stall，且 reset 初值未被源码指定。 [RequestArb.scala:200](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:200) [RequestArb.scala:204](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:204) | 发送连续普通 A/C/B，再单独发送连续 A Hint；记录 `s1_fire`、`s1_AHint_fire`、`ds_mcp2_stall`、`s2_ready`。 | 普通 S1 fire 后下一拍 bubble；Hint 不产生该 bubble；bubble 拍不得出现 channel 或 S1 fire。 | `Handshake checker` 与 `Performance checker` 覆盖 `s1_fire -> stall -> resume`；cross `opcode=Hint/non-Hint x channel`。 |
| RA-S1ENTRANCE-SAMESET | B/C fire 或 `s2_ready && mshr_task_s1.valid && metaWen` 应把 set 反馈给 RequestBuffer，防止同 set A 穿透。 [RequestArb.scala:191](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:191) [RequestBuffer.scala:208](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestBuffer.scala:208) | 分别驱动 B、C 和 metaWen MSHR，再给 RequestBuffer 同 set/异 set A。 | 同 set A 受 RequestBuffer 防护，异 set 不应被该 set 比较误伤；replRead stall 时仍按实际表达式检查提示。 | `Storage conflict checker` 检查 set 比较；`Context isolation checker` 覆盖 same-set vs different-set；coverage `source x sameSet x s2_ready`。 |
| RA-BUFFER-SELECT | Refill/Release 读的 valid/id 必须与 task 的 MSHR、probe、snoop 字段一致，且响应延迟一拍到 MainPipe。 [RequestArb.scala:219](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:219) [tl2chi/Slice.scala:121](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:121) | 构造 `useProbeData`、`readProbeDataDown`、CHI snoop-hit-release 和不同 `mshrId` task。 | 只对应的 read port valid，id 为 `mshrId` 或 `snpHitReleaseIdx`；MainPipe S3 response valid 比 read valid 晚一拍。 | `Storage conflict checker` 与 `Handshake checker` 检查 Valid-only 读；cross `useProbeData x readProbeDataDown x snoopHitRelease`。 |
| RA-NO-FLUSH-CANCEL | 不得虚构 flush/cancel/replay 优先级；该模块 IO 没有这些输入，寄存器只由 reset 和 S0/S1 条件更新。 [RequestArb.scala:32](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:32) [RequestArb.scala:125](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:125) | 静态接口断言/编译检查该 IO 集合；在上游拥有 cancel 的模块另行测试 cancel 与 task 生命周期。 | 本模块没有可驱动的 flush/cancel 覆盖点；已 accepted task 不会由不存在的端口在 RequestArb 内清除。 | `Flush/replay checker` 作为**范围 checker**：拒绝本模块级虚假 coverage，要求上游 owner 覆盖；`FSM checker` 只检查可见 valid 更新。 |
| RA-STARVATION-DRAIN | 固定优先级没有 A 的等待上界；系统需要证明高优先级停流后能 drain。 [RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [RequestArb.scala:155](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:155) | 持续 C 或 B，同时持久 A；随后停止高优先级且解除 block。 | 高优先级持续时 A 可保持不 fire；高优先级停流后 A 必须在资源可用时 fire。 | `Forward-progress checker` 以“高优先级停止”为假设；coverage `C/B pressure -> drain -> A fire`，不可错误声称无条件公平。 |
| RA-BOUNDARY-SCOPE | 跨页、跨 line、MMIO 的分片/异常/合并不在此模块；只允许验证已形成 task 的入口握手。 [RequestArb.scala:32](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:32) [CoupledL2.scala:186](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:186) | 从上游分别送入经拆分的跨 line task、已分类 cacheable task，并在顶层单独施加 MMIO/翻译场景。 | RequestArb 只按独立 `tag/set/off` task 仲裁；没有 fault、merge、MMIO 或 cancel 状态。 | `Context isolation checker` 和 `Handshake checker` 覆盖入口；边界 fault/recovery coverage 转交 TLB/MMIO bridge/上游访问模块。 |
-->

## 11. Verification Notes

The following checklist is module-specific. For every item, record an effective task's `tag/set/channel/mshrId` and use Decoupled `valid && ready` to determine real acceptance. A standalone valid cannot imply a result.

| Verification ID | Risk / Invariant | Directed Stimulus | Expected Observation | Required Checker / Coverage |
| --- | --- | --- | --- | --- |
| RA-RESET-ABC | A/B/C must not be accepted while `resetFinish=0`; this result does not extend to `mshrTask`. [RequestArb.scala:78](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:78) [RequestArb.scala:153](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:153) | Hold A/B/C valid after reset and record `resetIdx`, `resetFinish`, the three ready signals, and mshrTask ready. | Before count completion, all three channel ready signals are low; the first legal channel fire occurs only after `resetFinish=1`. Judge mshrTask independently from its ready expression. | `Handshake checker` covers reset release; `FSM checker` uses a local `resetFinish` state model; cross `A/B/C x resetFinish`. |
| RA-HOLD-BACKPRESSURE | A loser with `valid=1 && ready=0` must not be accepted, and `{tag,set,channel}` must stay stable until fire. [RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [RequestArb.scala:155](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:155) | Make A and C valid together; hold A valid/bits until C disappears or is blocked. | Before C fire, A.ready=0; A bits remain stable; A produces exactly one fire only on its later `valid && ready`. | `Handshake checker` checks no-accept/no-double-accept; `Arbiter checker` checks loser persistence; cover `C wins -> A hold -> A fire`. |
| RA-CBA-PRIORITY | Eligible candidates must use fixed `C > B > A`, with only one channel fire per cycle. [RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [RequestArb.scala:155](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:155) | Apply `C+B+A`, `block_C+B+A`, and `block_C+block_B+A` while Directory ready, `s2_ready`, and reset conditions hold. | The three cases see only C, B, and A fire respectively. B has ready=0 while C is eligible, and A has ready=0 while B/C is eligible. | `Arbiter checker` checks one-hot fire, priority, and ready feedback; cross `block_C/block_B x chosen_channel`. |
| RA-MSHR-S0-OVERLAP | Holding/replacing the MSHR slot must follow `old_valid && !s1_fire || s0_fire`; a new MSHR must not preempt a channel in the same cycle. [RequestArb.scala:120](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:120) [RequestArb.scala:125](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:125) | First make mshrTask fire at S0, then present C/B/A on the next cycle. Also test mshrTask and C/B/A valid together with an empty old slot. | A held MSHR wins on the following cycle; with an empty old slot, S0 accepts the MSHR while only one channel task can fire at S1. | `Occupancy checker` implements a one-entry `mshr_task_s1` valid/payload reference model; `Arbiter checker` crosses `old_mshr_valid x s0_fire x channel_fire`. |
| RA-DIR-WRITE-CONFLICT | A Directory write/replacement conflict must neither accept a channel nor misread `chnl_task_s1.valid` as an SRAM read. [Directory.scala:322](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:322) [RequestArb.scala:153](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:153) | Assert `metaWReq`, `tagWReq`, and `replacerWen` separately while continuously driving each channel. | `dirRead_s1.ready=0`, so none of the three channels fires; after conflict removal, fixed priority resumes. | `Storage conflict checker` checks read/write exclusion; `Handshake checker` checks fire only after Directory ready; cross each write source x A/B/C. |
| RA-MCP2-HINT | The cycle after a non-A-Hint S1 fire must have `s2_ready=0`; A Hint does not cause that next-cycle stall, and source does not specify reset initial value. [RequestArb.scala:200](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:200) [RequestArb.scala:204](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:204) | Send consecutive ordinary A/C/B, then consecutive A Hints, recording `s1_fire`, `s1_AHint_fire`, `ds_mcp2_stall`, and `s2_ready`. | A normal S1 fire produces a next-cycle bubble; Hint does not. No channel or S1 fire occurs in the bubble cycle. | `Handshake checker` and `Performance checker` cover `s1_fire -> stall -> resume`; cross `opcode=Hint/non-Hint x channel`. |
| RA-S1ENTRANCE-SAMESET | B/C fire, or `s2_ready && mshr_task_s1.valid && metaWen`, must feed the set back to RequestBuffer to prevent same-set A from passing through. [RequestArb.scala:191](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:191) [RequestBuffer.scala:208](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestBuffer.scala:208) | Independently drive B, C, and a metaWen MSHR, then present same-set and different-set A from RequestBuffer. | Same-set A is protected by RequestBuffer; a different set must not be falsely blocked by this comparison. During replacement-read stall, check the hint by its actual expression. | `Storage conflict checker` checks set comparison; `Context isolation checker` covers same-set versus different-set; cover `source x sameSet x s2_ready`. |
| RA-BUFFER-SELECT | Refill/Release read valid/id must match a task's MSHR, probe, and snoop fields, and the response reaches MainPipe one cycle later. [RequestArb.scala:219](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:219) [tl2chi/Slice.scala:121](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:121) | Construct tasks with `useProbeData`, `readProbeDataDown`, CHI snoop-hit-release, and different `mshrId`s. | Only the corresponding read port is valid, with `mshrId` or `snpHitReleaseIdx`; MainPipe S3 response valid is one cycle later than read valid. | `Storage conflict checker` and `Handshake checker` check Valid-only reads; cross `useProbeData x readProbeDataDown x snoopHitRelease`. |
| RA-NO-FLUSH-CANCEL | No flush/cancel/replay priority may be invented. This module's I/O lacks these inputs, and registers update only under reset and S0/S1 conditions. [RequestArb.scala:32](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:32) [RequestArb.scala:125](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:125) | Statically assert / compile-check this I/O set; test cancellation and task lifetime separately in the upstream owner. | There is no module-local flush/cancel coverpoint. An accepted task cannot be cleared inside RequestArb by a nonexistent port. | `Flush/replay checker` is a **scope checker**: reject fictional module-level coverage and require upstream-owner coverage; `FSM checker` checks only visible valid updates. |
| RA-STARVATION-DRAIN | Fixed priority gives no A waiting bound; the system needs to prove drain after high-priority traffic stops. [RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [RequestArb.scala:155](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:155) | Hold C or B continuously while A remains persistent, then stop high-priority input and remove blocks. | With continuing high priority, A can remain without fire. Once high priority stops, A must fire when resources are available. | `Forward-progress checker` assumes high-priority traffic stops; cover `C/B pressure -> drain -> A fire`. Do not claim unconditional fairness. |
| RA-BOUNDARY-SCOPE | Cross-page, cross-line, and MMIO splitting/exception/merge are not in this module; only ingress handshakes of formed tasks may be tested. [RequestArb.scala:32](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:32) [CoupledL2.scala:186](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:186) | Send upstream-split cross-line tasks and already classified cacheable tasks separately, and apply MMIO/translation scenarios at the top level. | RequestArb arbitrates only independent `tag/set/off` tasks. It has no fault, merge, MMIO, or cancel state. | `Context isolation checker` and `Handshake checker` cover ingress; move boundary fault/recovery coverage to the TLB/MMIO bridge/upstream access module. |

<!--
## 12. 小结

1. Kunminghu V2 默认 CHI 的 RequestArb 位于每个 `tl2chi.Slice`，其现实 B 类输入是 CHI `RXSNP`；HuanCun 和 OpenLLC 是边界，不是本实例。
2. 仲裁的严格顺序是“已暂存 MSHR > 合格 C > 合格 B > 合格 A”；C/B/A 无 RR 保证，MSHR 内部的 RR 在上游 MSHRCtl `FastArbiter`。
3. `ready` 联合编码了 reset、Directory、MainPipe、MSHR、GrantBuffer 与 CHI TX 容量；loser 被反压而非由 RequestArb 缓存或丢弃。
4. 目录读、MCP2 间隔和 MSHRBuffer 读构成 RequestArb 的主要短路径职责；MainPipe 才负责后续命中/缺失分类、数组操作和协议输出。
5. 当前源码不足以证明精确周期数、复位时 MSHR 是否会送入、地址翻译或 MMIO 分类；这些应以相邻模块和 FST/仿真继续验证。
-->

## 12. Summary

1. Kunminghu V2's default-CHI RequestArb lives in every `tl2chi.Slice`; its real B-class input is CHI `RXSNP`. HuanCun and OpenLLC are boundaries, not this instance.
2. The strict arbitration order is "held MSHR > eligible C > eligible B > eligible A." C/B/A have no RR guarantee; MSHR-internal RR lives in upstream MSHRCtl `FastArbiter`.
3. `ready` jointly encodes reset, Directory, MainPipe, MSHRCtl, GrantBuffer, and CHI TX capacity. Losers are backpressured rather than cached or dropped by RequestArb.
4. Directory reads, MCP2 spacing, and MSHRBuffer reads form RequestArb's main short-path responsibilities. MainPipe owns later hit/miss classification, array operations, and protocol output.
5. Current source cannot prove exact cycle counts, whether MSHR tasks arrive during reset, address translation, or MMIO classification. Validate those through neighboring modules and FST/simulation.
