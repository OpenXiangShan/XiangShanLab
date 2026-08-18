# Cache-RequestArb：Kunminghu V2 CoupledL2 请求仲裁器源码分析

> 结论先行：Kunminghu V2 默认配置启用 CHI，因此本文分析的有效实体是每个 `coupledL2/tl2chi.Slice` 内的 `coupledL2.RequestArb`。它不是轮转仲裁器：已被阻塞条件筛掉的候选之外，固定优先级为 **已暂存的 MSHR 任务 > C > B > A**；其中“MSHR > C/B/A”只适用于已经进入 `mshr_task_s1` 寄存器的任务，新来的 MSHR 任务先走 S0，并且最多可与同拍的一个获胜 C/B/A channel task 一起准入。它向 Directory 发起 S1 读、在 S2 以 `ValidIO` 把任务交给 MainPipe，并为 Refill/Release MSHRBuffer 选择读索引。HuanCun 没有这个 `RequestArb` 类，且 V2+CHI 不实例化 HuanCun L3；不能把 HuanCun 的 `MSHRAlloc` 或 `openLLC.RequestArb` 混入本章。

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
  CFG[KunminghuV2Config<br/>EnableCHI = true] --> TOP[L2Top]
  TOP --> L2[TL2CHICoupledL2]
  L2 --> S0[tl2chi Slice bank 0]
  L2 --> SN[tl2chi Slice bank N]
  S0 --> RA0[RequestArb]
  SN --> RAN[RequestArb]
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

## 3. 模块契约：Who / Why / How / From / To

### 3.1 Slice 内实际连线

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

`tl2chi.Slice` 的接线是本节所有箭头的直接证据。特别地，V2 的 `sinkB` 来自 `RXSNP.task`，不是 TL-to-TL 分支里的 `SinkB.task`；虽然信号名保留 B，这个协议边界不能混淆。[tl2chi/Slice.scala:65](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:65) [tl2tl/Slice.scala:70](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2tl/Slice.scala:70) [RXSNP.scala:28](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:28)

### 3.1.1 S0-S2 的关键数据路径

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
  [*] --> ResetGate
  ResetGate --> ChannelEligible: resetFinish
  ChannelEligible --> MshrHeld: mshrTask.fire
  ChannelEligible --> ChannelSelected: sinkC/B/A.fire
  MshrHeld --> S2Task: s1_fire && !replReadStall
  ChannelSelected --> S2Task: s1_fire
  S2Task --> ChannelEligible: local s2_ready returns
  MshrHeld --> MshrHeld: replRead stall
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

## 12. 小结

1. Kunminghu V2 默认 CHI 的 RequestArb 位于每个 `tl2chi.Slice`，其现实 B 类输入是 CHI `RXSNP`；HuanCun 和 OpenLLC 是边界，不是本实例。
2. 仲裁的严格顺序是“已暂存 MSHR > 合格 C > 合格 B > 合格 A”；C/B/A 无 RR 保证，MSHR 内部的 RR 在上游 MSHRCtl `FastArbiter`。
3. `ready` 联合编码了 reset、Directory、MainPipe、MSHR、GrantBuffer 与 CHI TX 容量；loser 被反压而非由 RequestArb 缓存或丢弃。
4. 目录读、MCP2 间隔和 MSHRBuffer 读构成 RequestArb 的主要短路径职责；MainPipe 才负责后续命中/缺失分类、数组操作和协议输出。
5. 当前源码不足以证明精确周期数、复位时 MSHR 是否会送入、地址翻译或 MMIO 分类；这些应以相邻模块和 FST/仿真继续验证。
