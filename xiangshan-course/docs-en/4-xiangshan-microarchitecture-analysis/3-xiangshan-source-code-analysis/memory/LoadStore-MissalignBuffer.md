# 香山昆明湖 V2 LoadStore-MissalignBuffer 源码分析

> 用户请求使用 MissalignBuffer；当前源码中的类、文件和信号拼作 MisalignBuffer。本文保留源码拼写，以免检索时漏掉 LoadMisalignBuffer 和 StoreMisalignBuffer。

## 1. 范围、版本与证据

| 项目 | 本次基线 |
| --- | --- |
| 分支与源码路径 | kunminghu-v2，/home/yanyusong/xs-memory-env/XiangShan |
| 有效源码提交 | e12436c7cba86b195deec24981976d78bc263661 |
| 课程仓库提交 | 680010a3cf7cc72900345600b99709bc337a52bf |
| 独立 Design Doc 基线 | 未查阅；本机不存在 /home/yanyusong/XiangShan-Design-Doc，课程内的 design-document 目录也没有 LSU/Misalign 文档。 |
| 周同步 | 已按 skill 执行 weekly_sync.py；输出为 skip: last sync 2.85 days ago < 7 days，因此没有 fetch 或 pull。 |
| 分析对象 | LoadMisalignBuffer 和 StoreMisalignBuffer。MemBlock 将它们都作为单例实例化，故 LoadStore 文件名应覆盖两条路径。 |
| 覆盖范围 | 标量/向量 load/store、16B 分片、Store 的 4KB 跨页闭环、TLB/PMP/DCache、replay、redirect、异常、Difftest、虚拟页/缓存行/MMIO 边界。 |

本次只读检查源码。源工作树原本已有与本分析无关的 difftest 修改和 aia/ 未跟踪内容；课程仓库也已有未跟踪文档。它们均未被修改。

### 1.1 关键证据索引

| 主题 | 当前提交中的有效源码 | 核心代码 | 证明内容 |
| --- | --- | --- | --- |
| 顶层实例化与专用 port | [MemBlock.scala:55](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:55)、[MemBlock.scala:435](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:435) | MisalignWBPort = 1 | 两个 buffer 均是 MemBlock 内单例；Load fragment 固定复用 LDU port 1。 |
| Load 单槽与固定仲裁 | [LoadMisalignBuffer.scala:143](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:143) | loadMisalignFull := req_valid | Load 不是 FIFO；只有一个占用位，多个 LDU 同拍候选时按低 port 编号优先。 |
| Store 单槽与 oldest 仲裁 | [StoreMisalignBuffer.scala:71](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:71)、[StoreMisalignBuffer.scala:146](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:146) | selectOldest | Store 也只有一个请求槽，但同拍候选按 ROB 年龄和 uop 序号择老。 |
| Load 分片与合并 | [LoadMisalignBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:292)、[LoadMisalignBuffer.scala:510](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:510)、[LoadMisalignBuffer.scala:540](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:540) | maxSplitNum = 2 | 只生成两个对齐子 load，顺序发射、收集，再按原始类型拼接。 |
| Store 跨页闭环 | [StoreMisalignBuffer.scala:223](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:223)、[StoreQueue.scala:1187](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1187) | s_block / doDeq | 跨 4KB 页的 store 在写回后仍保留 buffer，直到 StoreQueue 的 DataBuffer 入队确认。 |
| 异常覆盖未启用 | [LoadMisalignBuffer.scala:625](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:625)、[StoreMisalignBuffer.scala:660](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:660) | overwriteExpBuf.valid := false.B | 两模块都计算候选 payload，但当前提交把 valid 强制为 false。 |

### 1.2 理论、课程意图和有效代码

课程资料 [14_LoadStore.md:3](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md:3) 声明它参考的是旧检出 3fdbebedf6d505dedfdd66f8d8154c82136963a6，而不是本次 e12436c7。其 [14_LoadStore.md:222](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md:222) 和 [14_LoadStore.md:1235](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md:1235) 仅用作术语和学习路径，不能作为本提交的行为证据。

| 层次 | 结论 | 证据边界 |
| --- | --- | --- |
| 理论 | 跨对齐边界的一次访存可拆成两个更小、对齐的访问，待它们的结果确定后再向原指令交付数据或异常。 | 课程第 14 章的概念背景。 |
| 设计意图 | 用独立的小缓冲承接少见且控制复杂的请求，复用常规 LoadUnit/StoreUnit、TLB 和 DCache，避免扩张通常的高速路径。 | 课程结构和 MemBlock 实例化支持该意图；没有同提交独立 Design Doc。 |
| 有效代码 | 每类 buffer 均只有一个请求槽和至多两个 fragment；Load 复用 LDU1，Store 复用 STA0；二者仲裁、状态、异常和完成规则不同。 | 下文 Scala/Chisel 链路是唯一行为依据。 |

课程资料的一个覆盖缺口是：旧文档的 Store 说明错误地指向非对齐 Load 小节，[14_LoadStore.md:3473](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md:3473)。所以不能套用旧 Load 图解释 Store；后文单独追踪其 cross-4KB 和 s_block。

## 2. 模块契约：Who / Why / How / From / To

| 模块 | Who | Why | How | From | To |
| --- | --- | --- | --- | --- | --- |
| LoadMisalignBuffer | MemBlock 所有；LDU0..2 生产 enq，LDU1 消费 fragment 并回传响应。 | 将跨 16B 的标量/向量 load 从常规发射路径移出，串行管理两个子访问、replay 和结果合并。 | 一个寄存器请求槽、两个 fragment/response 槽、curPtr、unSentLoads 和六态 FSM。 | LoadUnit.io.misalign_enq 的 LqWriteBundle。 | LDU1 的 misalign_ldin；最终标量写回或向量 MergeBuffer；redirect 时取消。 |
| StoreMisalignBuffer | MemBlock 所有；STA0..1 生产 enq，STA0 执行 fragment；StoreQueue 参与跨页释放。 | 为跨 16B store 复用常规 StoreUnit，并为跨 4KB 页的高半页物理地址和 DataBuffer 写入建立闭环。 | 一个请求槽、两个 fragment/response 槽、择老仲裁、六态 FSM，其中 s_block 等待 SQ doDeq。 | StoreUnit.io.misalign_enq。 | STA0 的 misalign_stin，标量/向量 store 回写接口，以及 StoreMaBufToSqControlIO。 |
| MemBlock | 集成者与端口仲裁者。 | 将 MAB 连接到三个 LDU、两个 STA、ROB/LSQ、TLB/DCache 和公共写回端口。 | 固定 MisalignWBPort = 1；普通 LDU/STA 输出在共享回写端口上优先于 MAB。 | LDU/STA、LSQ/ROB 和 redirect。 | backend writeback、StoreQueue、向量 MergeBuffer、LSQ 状态。 |

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

图中的 LDU1 与 STA0 不是说它们独占所有正常访存，而是 MAB fragment 的唯一专用复用通道。fragment 进入这些单元后仍按普通路径做 DTLB、PMP/PMA、DCache、转发、miss/replay 和异常判定。

## 3. 参数、端口与单槽资源

### 3.1 参数与容量

| 项目 | 定义位置 | 当前值或来源 | 对行为的影响 |
| --- | --- | --- | --- |
| Load 入队端口数 | [Parameters.scala:214](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:214)、[LoadMisalignBuffer.scala:39](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:39) | LoadPipelineWidth = 3 | 三个 LDU 都能向一个 LoadMAB 提交候选。 |
| Store 入队端口数 | [Parameters.scala:215](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:215)、[StoreMisalignBuffer.scala:41](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:41) | StorePipelineWidth = 2 | 两个 STA 都能向一个 StoreMAB 提交候选。 |
| 最大子请求数 | [LoadMisalignBuffer.scala:43](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:43)、[StoreMisalignBuffer.scala:45](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:45) | maxSplitNum = 2 | 两模块均只维护两份 fragment 与 response。 |
| Load 硬件失对齐开关 | [CSR.scala:566](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/CSR.scala:566) | smblockctl bit 9，hd_misalign_ld_enable | LDU 只在该 CSR 允许且满足其它安全条件时把请求送入 LoadMAB。 |
| Store 硬件失对齐开关 | [CSR.scala:567](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/CSR.scala:567) | smblockctl bit 8，hd_misalign_st_enable | STA 的 MAB 路径受该开关门控。 |
| MAB 存储深度 | [LoadMisalignBuffer.scala:143](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:143)、[StoreMisalignBuffer.scala:130](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:130) | 一个 req_valid 和一个 req 寄存器 | 容量为 1 的单槽缓冲，不是有 head/tail 的队列；req_valid=1 时没有第二条独立 MAB 指令可进入。 |

~~~scala
// LoadMisalignBuffer.scala
val req_valid = RegInit(false.B)
val req = Reg(new LqWriteBundle)
io.loadMisalignFull := req_valid
val canEnqValid = !req_valid && !select_req_bit.uop.robIdx.needFlush(io.redirect) && select_req_valid
~~~

该代码同时给出 allocation、full 和 redirect 前筛除：接收成功时置 req_valid，完成或 redirect 时清除；模块没有索引、环绕位、free-list 或多 entry 搜索。因此任何“并行处理多个失对齐 load/store”的表述都不符合当前实现。

### 3.2 公共入队契约与握手

[Bundles.scala:374](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/Bundles.scala:374) 定义的公共契约为：

~~~scala
class MisalignBufferEnqIO extends XSBundle {
  val req = DecoupledIO(new LqWriteBundle)
  val revoke = Output(Bool())
}
~~~

| 边 | payload | 控制与阻塞 | 消费者和效果 |
| --- | --- | --- | --- |
| enq(i).req | LqWriteBundle，含 uop、vaddr、fullva、数据/掩码及访存属性。 | 只有空槽且仲裁选中时 ready；valid and ready 才接收。Load 是低编号优先，Store 是 oldest 优先。 | 将原请求锁存到唯一 req。 |
| splitLoadReq / splitStoreReq | 当前 curPtr 指向的对齐 fragment，含恢复后的类型、地址、mask、向量标志和 isFinalSplit。 | Decoupled；仅 fire 才从 s_req 前进到 s_resp。 | LDU1 / STA0 的 S0 输入。 |
| splitLoadResp / splitStoreResp | fragment 的 data、exception、uncache/MMIO、replay 与地址属性。 | Valid；buffer 在 s_resp 且 ROB index 相等时采样；没有显式 child-id 比较。 | 写 response 槽，决定重发、合并或异常。 |
| writeBack / vecWriteBack | 原始 uop 和合并后的 data 或 exception。 | Decoupled；下游端口竞争时 MAB ready 可低，槽持续占用。 | MemBlock 的标量写回或向量 merge 路径。 |
| redirect | backend redirect。 | req.uop.robIdx.needFlush(redirect) 为真时取消当前槽，优先于正常状态推进。 | 清空状态，阻止错误路径写回。 |

splitResp 是 Valid 而非 Decoupled，因而它没有 response ready 可以施加背压；正确性依赖专用 LDU/STA 仅在 buffer 处于 s_resp、且 ROB 身份相符时产生可采样结果。源码检查的是 robIdx，并未找到 fragment 序号比较；这是后文验证重点。

## 4. MemBlock 集成与端口仲裁

### 4.1 连接事实

~~~scala
// MemBlock.scala，省略非 MAB 端口
loadMisalignBuffer.io.enq(i) <> loadUnits(i).io.misalign_enq
if (i == MisalignWBPort) {
  loadUnits(i).io.misalign_ldin  <> loadMisalignBuffer.io.splitLoadReq
  loadUnits(i).io.misalign_ldout <> loadMisalignBuffer.io.splitLoadResp
}
~~~

上述连接位于 [MemBlock.scala:1019](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1019)。所有 LDU 可入队，但只有 MisalignWBPort = 1 的 LDU 可执行与回传 load fragment；其余 LDU 的 misalign_ldin.valid 被置为 false。Store 侧也让两个 STA 入队，而只将 splitStoreReq/Resp 接到 STA0，[MemBlock.scala:1281](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1281)。

MAB 虽接收完整 RobLsqIO，[MemBlock.scala:1183](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1183)，但 LoadMAB 内部对 io.rob 的唯一可见用途是把反向 MMIO 字段置 DontCare，[LoadMisalignBuffer.scala:138](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:138)。因此“只有足够老的 load 才可入 MAB”不能归因于 buffer 内部 ROB 仲裁；实际门控发生在 LoadUnit S3 的 s3_misalign_can_go，[LoadUnit.scala:1565](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1565)。

### 4.2 写回优先级不是 MAB 优先

~~~scala
val misalignWritebackOverride = Mux(
  loadUnits(MisalignWBPort).io.ldout.valid,
  loadUnits(MisalignWBPort).io.ldout.bits,
  loadMisalignBuffer.io.writeBack.bits)
loadMisalignBuffer.io.writeBack.ready :=
  ldaExeWbReqs(MisalignWBPort).ready && !loadUnits(MisalignWBPort).io.ldout.valid
~~~

[MemBlock.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:529) 表明普通 LDU1 的 ldout.valid 同拍出现时，它覆盖 MAB 标量写回，且 MAB ready 被压低。Store 的 stOut(0) 也仅在其它常规标量/向量 store 输出都无效时才接收 MAB，[MemBlock.scala:1388](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1388)。因此 MAB 完成不等于同拍离开 buffer；必须等到共享写回端口真正 fire。

loadMisalignFull 从 LoadMAB 接到 LSQ、LoadQueue 和 LoadQueueReplay，[MemBlock.scala:1195](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1195)、[LoadQueue.scala:288](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala:288)。但本提交中搜索 LoadQueueReplay.scala，该输入只在 IO 声明处出现；C_MF replay 的解除条件只读取 robDeqPtr，[LoadQueueReplay.scala:366](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:366)。所以不能宣称 loadMisalignFull 当前直接阻塞或释放 replay；满槽后的请求会 nack/replay，后续是否成功取决于重新执行时的槽状态和 ROB 年龄。

## 5. LoadMisalignBuffer：状态、控制与数据

### 5.1 入队仲裁和生命周期

LoadMAB 的 select_req_bit 使用 ParallelPriorityMux(io.enq.map(_.req.valid), ...)，[LoadMisalignBuffer.scala:148](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:148)。enq(0) 优先于 enq(1)，再优先于 enq(2)；这是静态 port 优先级，不是按 robIdx 选最老。只有空槽、候选非 redirect-killed 且选中 valid 时才锁存。

LoadUnit S3 把“足够老”和 buffer ready 合在入队条件中：

~~~scala
val toMisalignBufferValid =
  s3_can_enter_lsq_valid && s3_mis_align && !s3_frm_mabuf
io.misalign_enq.req.valid :=
  toMisalignBufferValid && s3_misalign_can_go
s3_lrq_rep_info.misalign_nack :=
  toMisalignBufferValid && !(io.misalign_enq.req.ready && s3_misalign_can_go)
~~~

见 [LoadUnit.scala:1582](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1582)。这里的 nack 会成为 C_MF replay 原因；MAB 内部不保存第二个候选。

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

| 状态 | Who / 状态含义 | 入口 | 保持或退出 | 主要输出与恢复 |
| --- | --- | --- | --- | --- |
| s_idle | buffer 空闲的控制状态。 | reset 或上次写回完成。 | 已锁存 req_valid 后进入 s_split。 | 不发 fragment。 |
| s_split | 根据原类型和低地址位建立两个对齐 splitLoadReqs。 | req_valid。 | 构造完无条件进 s_req。 | 设置 unSentLoads = 2b11、curPtr = 0。 |
| s_req | 向 LDU1 发当前 fragment。 | s_split 或 replay。 | 仅 splitLoadReq.fire 后进入 s_resp。 | splitLoadReq.valid；ready=0 时保持 fragment 和指针。 |
| s_resp | 接收当前 fragment 的 LDU 结果。 | 一个 fragment 发射完成。 | replay/未完成片段回 s_req；两个正常完成进合并；异常或 uncache 直接进写回。 | 写 splitLoadResps(curPtr)、异常向量、未发送位。 |
| s_comb_wakeup_rep | 标量 normal load 的最终唤醒往返。 | 两片正常完成。 | 标量等 misalignNeedWakeUp 请求 fire；向量直接进 s_wb。 | 不是第三个数据 fragment。 |
| s_wb | 对原 uop 发标量或向量写回。 | 全局异常/uncache，或合并及唤醒完成。 | 对应 Decoupled 写回 fire。 | 清 req_valid、指针、response、全局状态；端口竞争则停留。 |

状态与更新由 [LoadMisalignBuffer.scala:165](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:165) 到 [LoadMisalignBuffer.scala:290](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:290) 给出。redirect 清理在同文件 [LoadMisalignBuffer.scala:610](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:610)，应视为各状态的高优先级恢复路径。

### 5.2 哪些 load 会进入本模块

它不是通用的“任意自然失对齐”单元。LDU S0 分别计算访问是否跨 16B 以及是否在自然边界对齐；同一 16B 内的自然失对齐由 misalignWith16Byte 标记，[LoadUnit.scala:711](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:711)。进入 buffer 的条件在 S2/S3 由 hd_misalign_ld_enable、失对齐类别、非异常/非 uncache、非 trigger 等条件共同门控，[LoadUnit.scala:1238](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1238)、[LoadUnit.scala:1582](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1582)。buffer 自己在 split 逻辑中以 cross16BytesBoundary 为前提，[LoadMisalignBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:292)。

可确认的结论是：本模块处理可被硬件分成两个、跨 16B 边界的 load fragment，而非所有架构意义上的非自然对齐访问。同一 16B 内的情况和被前级判为异常/特殊内存类型的情况，不能由本模块的存在推断也会进入该 buffer。

### 5.3 分片算法、索引和数据合并

LoadMAB 先按原访问类型计算末字节地址，再比较 vaddr 低 5 位的 bit 4：

~~~scala
val highAddress = LookupTree(alignedType, Seq(
  LSUOpType.lb -> 0.U, LSUOpType.lh -> 1.U,
  LSUOpType.lw -> 3.U, LSUOpType.ld -> 7.U)) + req.vaddr(4, 0)
val cross16BytesBoundary = req_valid && highAddress(4) =/= req.vaddr(4)
~~~

见 [LoadMisalignBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:292)。这里的边界是 16B，而不是 64B DCache line 或 4KB virtual page；后两者在后文单独讨论。

| 原访问 | 低地址位条件 | 低 fragment | 高 fragment | 分片依据 |
| --- | --- | --- | --- | --- |
| LH | bit 0 为 1 | LB at A | LB at A + 1 | 两个字节分别对齐。 |
| LW | A mod 4 = 1 | LW at A - 1 | LB at A + 3 | 低片保留 3 字节，高片 1 字节。 |
| LW | A mod 4 = 2 | LH at A | LH at A + 2 | 2B 加 2B。 |
| LW | A mod 4 = 3 | LB at A | LW at A + 1 | 1B 加 3B。 |
| LD | A mod 8 = 1/2/3 | LD at A - 1/-2/-3 | LB/LH/LW at A + 7/+6/+5 | 低片分别取 7/6/5 个有效字节。 |
| LD | A mod 8 = 4 | LW at A | LW at A + 4 | 4B 加 4B。 |
| LD | A mod 8 = 5/6/7 | LW/LH/LB at A - 1/-2/A | LD at A + 3/+2/+1 | 低片分别取 3/2/1 个有效字节。 |

完整 case 在 [LoadMisalignBuffer.scala:314](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:314) 到 [LoadMisalignBuffer.scala:508](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:508)。表中 A 是原始虚拟地址；代码为每个 child 写新的 vaddr、掩码、访问大小和 uop 访存类型，同时保存原始 fullva。不能把名字为 getMask、new128Load 的局部计算直接描述为有效状态机功能：本次只以最终被 splitLoadReq 使用的请求字段为证据。

fragment 使用一个 1-bit curPtr 和 2-bit unSentLoads。s_req 只把 splitLoadReqs(curPtr) 放到 Decoupled 输出；每次 response 后，正常无 replay 的当前 bit 才清除并移动 curPtr。任何 fragment 的 rep_info.need_rep 都回到 s_req 重发，而不会丢弃另一个片段。[LoadMisalignBuffer.scala:192](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:192)、[LoadMisalignBuffer.scala:522](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:522)。

两片正常响应保存在 splitLoadResps(0/1)。buffer 用 getShiftAndTruncateData、rdataHelper 或 rdataVecHelper 根据原类型、data_select 和原始地址完成位移、截断、拼接以及标量符号/零扩展，[LoadMisalignBuffer.scala:540](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:540)。数据合并发生在 buffer 内，TLB/DCache 不需要知道这是原指令的哪一半。

### 5.4 Load fragment 复用 LDU1 的流水线

| 位置 | 输入、工作和寄存器 | 关键握手或限制 | 下游效果 |
| --- | --- | --- | --- |
| LoadMAB s_req | splitLoadReq 是 Decoupled，携带 curPtr fragment。 | 只有 splitLoadReq.fire 才到 s_resp。 | 进入 LDU1 的 misalign_ldin。 |
| LDU S0 source arbitration | misalign_ldin 是 source 0，在常规 issue/replay/prefetch 等 source 前。 | s0 source ready 需要没有更高优先级 source；MAB source 自身为最高优先级。 | 已被 buffer 选中的 child 可抢占 LDU1 常规 S0 输入。 |
| LDU S0/S1 | child 的 vaddr 和 fullva 进入 DTLB；常规对齐、TLB/PMP、DCache request 逻辑复用。 | LoadUnit 以 frm_mabuf 标记该来源；交给 DCache 前仍受 ready、kill、redirect 影响。 | 每个 child 独立经历译址、权限与 cache/forwarding。 |
| LDU S2/S3 | child 取得 data、exception、uncache 或 replay 原因。 | MAB child 的 fwd_fail、mem_amb、nuke、RAR/RAW nack 可引起 rollback。 | Valid-only 的 misalign_ldout 回到 buffer。 |
| LoadMAB s_resp/s_wb | 保存 response、决定重发或合并，并在允许时向公共 WB 发原 uop。 | 正常标量还有一个 wakeup 往返；公共端口可能反压。 | 写回后才释放唯一槽。 |

证据：MAB source 在 [LoadUnit.scala:290](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:290) 到 [LoadUnit.scala:335](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:335) 具有最高优先级；来源标记在 [LoadUnit.scala:431](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:431)，地址与译址路径在 [LoadUnit.scala:692](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:692)，结果回传在 [LoadUnit.scala:1865](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1865)。

当两个标量 child 都正常完成时，LoadMAB 会在 s_comb_wakeup_rep 发一个带 misalignNeedWakeUp 的请求；LDU 识别它后不做普通 DCache 访问，而是经三拍寄存延迟生成一个 Valid-only 回应，[LoadMisalignBuffer.scala:243](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:243)、[LoadUnit.scala:1182](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1182)、[LoadUnit.scala:1865](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1865)。它是最终写回时序的完成令牌，不是第三个读请求。向量路径不发送这个 wakeup，而是合并后走 vecWriteBack。

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

这是正常标量路径的符号波形，连续的两个 splitLoadReq/Resp 高电平表示两个 fragment 的发送和回收；点号代表可变的 hit/miss/replay 间隔，不表示固定周期。每个实际推进点都要求相应的 Decoupled fire，response 则是 Valid-only。

### 5.5 Load 当前代码中的可见风险

在正常标量路径中，s_comb_wakeup_rep 后的 writeBack.valid 依赖一次性 fake wakeup response 的同拍条件，[LoadMisalignBuffer.scala:561](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:561)。而 LDU 的 fake response 是 RegNextN 后的 Valid 脉冲，没有 response ready；MemBlock 又在同拍普通 LDU1 ldout.valid 为 1 时压低 MAB writeBack.ready 并选择普通输出，[MemBlock.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:529)。

这形成一个**条件风险**：若 fake response 脉冲与普通 LDU1 输出重叠，当前源码表面上可能既没有 MAB 写回 fire，也没有重发机制。源码中未找到保证二者互斥的 assert。它不是已由动态测试证明的缺陷，应使用波形或形式属性验证该交叠是否不可达。向量路径不同：vecWriteBack.valid 已显式受 loadVecOutValid 抑制，[LoadMisalignBuffer.scala:581](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:581)，普通向量输出占用时 MAB 会保持在 s_wb；仍应验证它随后能正确交付而不丢数据。

## 6. StoreMisalignBuffer：分片、跨页与 StoreQueue 闭环

### 6.1 与 Load 的核心差异

| 项目 | LoadMisalignBuffer | StoreMisalignBuffer |
| --- | --- | --- |
| 同拍入队仲裁 | 固定低编号 port 优先。 | selectOldest，按 ROB 年龄再按 uop 序号选最老。 |
| 特有状态 | s_comb_wakeup_rep，给标量合并结果制造写回令牌。 | s_block，等待 StoreQueue 侧 DataBuffer 处理跨页 store。 |
| 跨页识别 | 该模块自身只判定 bit 4 的 16B 跨界。 | 额外计算 bit 12 的 cross4KBPageBoundary。 |
| 正常完成 | 两个 child data 在 buffer 合并。 | 不在 buffer 合并 store data；StoreQueue 使用 mask/data 的高低分片和 MAB 返回的高页 paddr。 |
| 输出竞争 | 普通 LDU1 标量/向量输出优先。 | 其它普通标量与向量 StoreUnit 输出优先。 |

Store 的入队 bundle 虽名为 LqWriteBundle，但它只是 MisalignBufferEnqIO 的共享 payload 类型，[Bundles.scala:374](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/Bundles.scala:374)。不能据此把 StoreMAB 入队误写成 LoadQueue 分配。

~~~scala
// StoreMisalignBuffer.scala
val (reqSelValid, reqSel) = selectOldest(io.enq.map(_.req))
val canEnq = !req_valid && !reqRedirect && reqSelValid
when (canEnq) {
  req := reqSel.bits
  req_valid := true.B
}
~~~

该路径见 [StoreMisalignBuffer.scala:146](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:146)。selectOldest 的比较递归在 [StoreMisalignBuffer.scala:71](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:71)；它和 Load 的 PriorityMux 完全不同。

### 6.2 Store FSM 与状态释放

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

| 状态 | 进入条件 | 工作和保持条件 | 退出/释放 |
| --- | --- | --- | --- |
| s_idle | reset 或已完成释放。 | 等待择老 request，跨页路径还受 robMatch/pendingPtr 条件约束。 | 满足后进入 s_split。 |
| s_split | 已锁存 request。 | 按访问类型与低地址位生成两个对齐 store child。 | 无条件进入 s_req。 |
| s_req | 有未发送 child。 | 把 splitStoreReqs(curPtr) 送给 STA0，等待 fire。 | fire 后进入 s_resp。 |
| s_resp | 一个 child 已被 STA0 接收。 | 接收 replay、异常/uncache 或正常结果；未成功的 fragment 保持为待发送。 | replay/未发送回 s_req；全完成或全局错误进 s_wb。 |
| s_wb | 两 child 已处理或发生全局异常。 | 发标量/向量 store 输出；被公共端口挡住时保持。 | 非跨页在 writeBack fire 后释放；跨 4KB 页转 s_block。 |
| s_block | 跨 4KB 页 store 的写回已经交付。 | 不等 ROB commit；等待 StoreQueue 确认 dataBuffer.io.enq(0).fire。 | sqControl.toStoreMisalignBuffer.doDeq 后才清 req_valid。 |

状态和转移位于 [StoreMisalignBuffer.scala:136](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:136) 到 [StoreMisalignBuffer.scala:326](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:326)。redirect 或 enq.revoke 会以高优先级清槽，[StoreMisalignBuffer.scala:641](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:641)。

### 6.3 Store 分片与 4KB 页控制

Store 同样比较低 5 位 bit 4 判断是否跨 16B，并额外比较 bit 12 判断 cross4KBPageBoundary，[StoreMisalignBuffer.scala:329](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:329)。其 LH/LW/SW 的子访问形状与 Load 对应类型相近；SD 的高偏移 case 则不能照抄 Load。例如 A mod 8 为 5/6/7 时，Store 代码分别使用 SD at A-5/A-6/A-7 加 SD at A+3/A+2/A+1，而 Load 对应的是较小低片加 LD 高片。[StoreMisalignBuffer.scala:360](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:360) 到 [StoreMisalignBuffer.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:529)。

fragment 接口在 [StoreMisalignBuffer.scala:532](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:532)：当前 child 的 vaddr、掩码、uop 和向量标志经 splitStoreReq 进入 STA0。STA0 仅在 hd_misalign_st_enable、跨 16B 类型、非特殊/异常类别等前提下把原 store 交给 MAB，[StoreUnit.scala:430](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:430)。每个 fragment 再复用 STA 的 TLB/PMP/DCache/store 执行路径，回传 splitStoreResp；buffer 在 [StoreMisalignBuffer.scala:542](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:542) 保存结果或重发。

跨 4KB 页的特殊性不是把 store “提交到 ROB”。buffer 向 StoreQueue 提供 crossPageWithHit、crossPageCanDeq、高页 paddr 和 withSamePtr，[StoreMisalignBuffer.scala:223](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:223)、[Bundles.scala:277](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/Bundles.scala:277)。StoreQueue 在两个 DataBuffer 槽可用时拆原始 store 的 mask/data，使用 MAB 提供的高页物理地址；它把 doDeq 定义为跨页条件、可出队条件和 dataBuffer.io.enq(0).fire 的合取，[StoreQueue.scala:1189](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1189)。这就是 s_block 的唯一正常释放确认。

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

这是跨 4KB 页 Store 的符号波形：writeBack 之后仍不释放，直到 StoreQueue 侧 doDeq。真实时长由 fragment 的 TLB/DCache/replay 和 DataBuffer 的可用性决定；图不表达固定周期。

### 6.4 Store 输出、向量接口和公共端口

StoreMAB 的标量 writeBack 和 vecWriteBack 分别在 [StoreMisalignBuffer.scala:598](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:598) 与 [StoreMisalignBuffer.scala:612](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:612) 生成。MemBlock 对 stOut(0) 的覆盖条件先检查其它标量 StoreUnit 输出、STA0 标量输出和两个 vector store 输出，[MemBlock.scala:1388](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1388)。故 StoreMAB 也必须等待公共出口可用，writeBack 发出不是 ROB commit，也不是已经进入 Sbuffer。

StoreMAB 的 toVecSplit.empty 由 req_valid 反相而来，并被 MemBlock 接到 vector store split 路径，[StoreMisalignBuffer.scala:188](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:188)、[MemBlock.scala:1613](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1613)。它只表达此单槽是否空闲；不能从该信号推导 StoreMAB 已完成全部架构可见写入。

## 7. 端到端数据流、时序和吞吐

### 7.1 有效流水线位置

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

MAB 是分片控制器与数据重组器，不拥有 DCache array、TLB state、MSHR、cache set/bank/way 或独立的外部 AXI/TL 接口。相关译址和 cache 资源由复用的 LDU/STA 申请：Load fragment 的 vaddr/fullva 选择在 [LoadUnit.scala:692](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:692)，MemBlock 把 LDU 接到 TLB/PMP/DCache 的集成关系在 [MemBlock.scala:686](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:686) 与 [MemBlock.scala:880](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:880)。

Load fragment 回到 LDU 时，MAB source 在 S0 的优先级高于普通 issue、LSQ replay、prefetch、vector 等候选，[LoadUnit.scala:290](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:290)。反过来，MAB 最终写回又低于普通 LDU1 输出。这是合理的资源复用取舍：前端注入优先保证子访问推进，末端输出仍保护常规端口协议。

### 7.2 定义清楚的时序边界

| 路径 | 起点 | 终点 | 固定部分 | 可变贡献项 | 吞吐瓶颈 |
| --- | --- | --- | --- | --- | --- |
| 标量 LoadMAB 正常路径 | enq.req.fire 锁存原 request。 | writeBack.fire。 | 两个 fragment 严格串行；正常合并后还有 LDU fake wakeup 的三拍寄存延迟。 | LDU1 S0 仲裁、TLB/PMP、DCache hit/miss、forwarding、replay、公共 WB 反压。 | 一个 req_valid 槽；任一时刻只有一条原始 MAB load。 |
| 向量 LoadMAB 正常路径 | enq.req.fire。 | vecWriteBack.fire。 | 两个 child 串行；没有 scalar fake wakeup。 | 同上，加上普通 vector 输出优先级。 | 单槽和 LDU1。 |
| StoreMAB 非跨页 | enq.req.fire。 | 标量或向量 Store 输出 fire 并清槽。 | 两个 child 串行。 | STA0、TLB/PMP/DCache/replay、公共 store 输出反压。 | 单槽和 STA0。 |
| StoreMAB 跨 4KB 页 | enq.req.fire。 | sqControl.doDeq 后清槽。 | 两个 child 串行；writeBack 后还有 s_block。 | 上述条件加 StoreQueue/DataBuffer 槽可用性。 | 单槽、STA0 和 DataBuffer 0 fire。 |

没有任何源码常量给出“从原 request 到写回固定 N 拍”的结论。特别是 DCache miss、TLB miss、replay 和公共端口争用都使端到端延迟可变。可明确的是事务完成 writeBack 的那个周期 req_valid 尚未被组合地变成可接收下一事务；清槽在时钟边沿后生效，所以新的原始 MAB request 至少在下一周期才可能接收。低 port 优先的 Load 仲裁还可能使持续的 port 0 流量使更高编号 port 长期失败；当前源码没有 round-robin/fairness 状态。

### 7.3 正常与恢复动态操作

**正常标量 load。** LDU S2/S3 判定原 request 为硬件可处理的跨 16B load，并通过年龄门控后与空槽握手。LoadMAB 在 s_split 构造两个 child，s_req 先向 LDU1 发第 0 片，s_resp 收到其正常结果后切到第 1 片。第二片正常后 buffer 合并字节，发 fake wakeup，得到完成令牌后以原 uop 通过共享 WB 写回。最后一次 writeBack.fire 才清 req_valid。

**被阻塞或恢复的 load。** 若槽满或指令尚不满足 S3 的年龄条件，LDU 产生 misalign_nack，形成 C_MF replay；它不在 MAB 排队。若 fragment 返回 need_rep，buffer 保留当前 curPtr 和相应未发送位，回到 s_req 重发。若 redirect 命中原 uop，buffer 立即失效，并且后续 response 不应形成错误路径写回。若任一 fragment 的 exception/uncache 成立，buffer 停止剩余片的正常流程，携带异常信息进入 s_wb。

**跨页 store。** StoreMAB 择老锁存原 store，依次让 STA0 处理两个 fragment。两个 fragment 完成后，非跨页请求在输出 fire 后释放；跨 4KB 页请求保留在 s_block，向 StoreQueue 提供高页 paddr 与控制信息，直到 DataBuffer 入队确认 doDeq。这条等待是 store data 侧的完成握手，而非“已 ROB commit”的同义词。

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

~~~scala
io.overwriteExpBuf.valid := false.B
~~~

Load 在 [LoadMisalignBuffer.scala:641](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:641)，Store 在 [StoreMisalignBuffer.scala:669](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:669)。所以正确描述是“候选 payload 和 MemBlock 选择线路存在，但当前提交的有效 MAB override 路径静态不可达”，而不是“跨页 MAB 会覆盖异常地址”。Load 的 flushLdExpBuff 也未找到模块外的行为性消费者。

### 8.3 Difftest、调试与外部协议边界

在两个 MisalignBuffer.scala 中未搜索到 Difftest 产生器。它们保存的是微体系结构中间状态；直接可见的 Difftest event 在更后的 commit/store 数据路径产生。例如 Load 的 DiffLoadEvent 由 ROB commit 侧输出，[Rob.scala:1584](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:1584)；StoreQueue 在 DataBuffer 入队、ncReq/mmioReq 条件下准备 DiffStore 输入，[StoreQueue.scala:1408](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1408)。因此 MAB 只会经正常 LSU、SQ/Sbuffer 或 ROB 的后续路径间接影响 Difftest，不能称它“直接发 Difftest”。

两个 MAB 也没有直接的 AIA、IOPMP、AXI、TileLink master/slave 接口。本任务在它们、LoadUnit 和 StoreUnit 的相关接口中未见这些通道；它们的外部存储系统影响只能经 DCache/uncache/LSQ 下游间接体现。debug/trigger 等 eligibility 条件属于 LDU/STA 前级分类，不能伪造为 MAB 内部特权状态。

## 9. 跨边界代码解析

| 边界 | 已证实的代码路径 | 分片、状态与恢复 | 不能过度推断的部分 | 验证焦点 |
| --- | --- | --- | --- | --- |
| 16B 对齐窗口 | Load/Store MAB 通过低 5 位 bit 4 和访问末字节比较决定跨界。 | 两个 child 严格串行；分别等待 response，正常后合并或输出。 | 它不是任意宽度的通用 split engine。 | LH/LW/LD/SD 各类低地址偏移，检查 child 地址、mask、数据重构。 |
| 虚拟 4KB 页 | LoadMAB 本身不计算 page bit 12；child 重新进 TLB。TLB 对 cross-page vaddr/fullva 有专门选择，[TLB.scala:397](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:397)。StoreMAB 显式算 bit 12，并经 SQ 交付高页 paddr。 | load 让两个 child 各自译址；store 在 s_block 等高页 dataBuffer 写入确认。 | 不能因 LoadMAB 不显式判页就断言它无法处理跨页；也不能把 overwriteExpBuf 当有效覆盖。 | LD at address ending ffb/fff，令高页 TLB/PMP fault，确认 fault 地址与停止剩余请求。 |
| 64B DCache line | DCache blockBytes 为 64B，[DCacheWrapper.scala:53](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:53)。MAB 只按 16B 切，child 经普通 LoadUnit/StoreUnit 进 DCache。 | 例如原地址靠近 line offset 3f 时，两个 child 可能落到不同 cache line，并串行请求。 | MAB 不看 set/way/MSHR，源码不能证明两个 child 的 miss 是否共享 MSHR 或合并 refill。 | line offset 0f、1f、2f、3f 下 hit/miss 组合，观察两 child 的 cache 请求与回收顺序。 |
| MMIO/uncache | LDU/STA 分类后，MAB 在 response 看到 mmio/nc 即走全局终止。 | 停止正常合并/余片，并按异常路径输出。 | 不可把它写成普通 UncacheEntry/AXI transaction；需沿 LoadQueueUncache/Sbuffer 再验证。 | 将低或高 child 映射到 NC/MMIO，检查没有错误 normal WB/normal store 数据路径。 |
| redirect 与 replay | MAB req.robIdx.needFlush 清槽；fragment need_rep 回 s_req。 | redirect 取消所有尚未完成片，replay 重发当前片。 | 不能用 valid-only response 假定后到 response 会被握手阻塞。 | fragment 0 后 redirect；fragment 1 replay；response 与 redirect 相邻周期。 |

跨 16B 并不必然跨 64B cache line，也不必然跨 4KB page；反之，跨页或跨 line 的具体地址组合应由 child 地址、TLB 和 DCache 实际观察决定。这个区分是理解 MAB 的关键：它只负责 16B 元素边界的拆分和控制，不是 page splitter 或 cache-line splitter。

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

## 12. 当前代码的边界和未确认项

| 观察 | 已确认事实 | 不能得出的结论 | 下一步证据 |
| --- | --- | --- | --- |
| response 身份 | s_resp 的状态推进看 splitResp.valid，数据写入再比 robIdx；没有 child-id 比较。 | 不能仅凭接口就证明错误 response 绝不出现。 | LDU1 专用端口波形或 assertion：valid response 的 robIdx 必匹配活动 MAB。 |
| scalar wakeup | fake response 是 Valid 脉冲，标量 WB 与 LDU1 输出共享端口。 | 不能仅凭静态结构断言已发生死锁。 | 覆盖 MAB-V07 或形式 liveness。 |
| vector 输出 | vecWriteBack.valid 显式受 loadVecOutValid 抑制。 | 不能把 scalar 的条件风险直接复制到 vector 路径。 | MAB-V08 的仲裁保持检查。 |
| exception override | 两边 payload/优先级连线存在，但 valid 硬置 false。 | 不能称当前 MAB 覆盖 exception address。 | 若后续版本打开 valid，再重新追踪 MemBlock 异常 mux。 |
| 具名局部信号 | getMask、new128Load/new128Store、needFlushPipe、unWriteStores 等在本文件中有未消费或未形成可观察输出的定义。 | 不能按名称把它们叙述成有效硬件功能。 | elaborated RTL cone 或后续提交差异。 |
| cache 资源 | child 经普通 LDU/STA/DCache 路径。 | 不能从 MAB 文件证明 child miss 的 MSHR 分配、coalescing 或 refill 关系。 | DCache 波形、MSHR 事件、cache miss 测试。 |

## 13. 总结

在 kunminghu-v2 的 e12436c7 提交中，LoadMisalignBuffer 与 StoreMisalignBuffer 是 MemBlock 内各一个、容量为 1 的特殊访存控制器。它们都把一条跨 16B 的原请求构造成至多两个对齐 child，并且严格串行地借用普通 LSU 管线；但 Load 以固定 port 优先、合并数据并使用标量 fake wakeup，而 Store 以 oldest 仲裁、显式处理 4KB 跨页、并在 s_block 等待 StoreQueue 的 DataBuffer 确认。

最重要的资源结论是单槽和共享专用执行端口：Load 复用 LDU1、Store 复用 STA0，最终写回仍受普通端口优先级约束。最重要的边界结论是 16B 分片不等于 64B cache line 或 4KB page 分片；页、TLB、cache miss、MSHR 和外部 uncache 的行为须沿子请求的下游链路验证。最重要的异常结论是两个 MAB 的 exception override payload 当前未启用，不能写成有效异常地址覆盖。

后续动态分析应首先覆盖：双 child 的地址/数据 scoreboarding、fragment replay、redirect、跨页高片 fault、Store s_block/DataBuffer 背压，以及标量 fake wakeup 与普通 LDU1 输出的潜在同拍竞争。这样才能把本次源码级结论推进到可观测的 RTL/FST 证据。
