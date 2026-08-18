<!--
# 香山昆明湖 V2：访存单元 UncacheBuffer 源码分析

> 结论先行：昆明湖 V2 的数据非缓存访问不是通过 L1 DCache 主数据通路完成。物理 UncacheBuffer 是 [Uncache.scala](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:207) 中的 UncacheImp：它把 LSQ 发来的一个 XLEN 宽度请求保存在物理条目中，按物理条目号作为 TileLink source ID 发往 data-MMIO 端口，并在 TileLink D 响应后再回送 LSQ。LoadQueueUncache 是上游的逻辑 load buffer；二者通过 mid（逻辑条目号）到 sid（物理 UncacheBuffer 条目号）的确认映射连接，不能混为同一个队列。
>
> 本文只报告静态源码可证实的行为。未生成 RTL、未运行仿真或 FST，因此 WaveDrom 仅是按 Chisel 的 valid/ready/寄存器关系绘制的时序示意，不是实测波形。

## 1. 范围、版本与证据边界

| 项目 | 本文口径 |
| --- | --- |
| 主分析对象 | 物理 UncacheBuffer：Uncache 与 UncacheImp |
| 主源码 | [Uncache.scala](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:32) |
| 上游关联 | MemBlock、LSQWrapper、LoadQueueUncache、StoreQueue、LoadUnit、StoreUnit、ROB |
| 下游边界 | Uncache 的 TileLink A/D；在 XSTile 的 data-MMIO 端口截止，不推断 L2/设备内部实现 |
| 源码目录 | /home/yanyusong/xs-memory-env/XiangShan |
| 源码基线 | 分支 kunminghu-v2，提交 e12436c7cba86b195deec24981976d78bc263661 |
| 有效配置 | KunminghuV2Config 继承 DefaultConfig；未发现它覆写 UncacheBufferSize 或 LoadUncacheBufferSize |
| 设计文档基线 | 未查阅。本地 /home/yanyusong/XiangShan-Design-Doc 不存在，因此不把设计文档描述作为实现证据 |
| 理论材料 | 课程 [14_LoadStore.md](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md:311) 用于说明 LSQ 子模块定位；行为结论均回到本次源码基线 |
| 工作树注意 | 源码树已有 difftest 修改及 src/main/resources/aia/ 未跟踪内容；本文未改动源码树 |
| 周同步检查 | 按 skill 运行 weekly_sync.py；状态文件显示距上次同步不足 7 天，因此脚本跳过同步，没有执行 fetch、pull 或任何破坏性操作 |
| 波形边界 | 本次没有可用于核对的 FST；所有周期数只描述寄存器边界和可变握手，不宣称固定总线往返延迟 |

### 1.1 Design Doc 与代码追溯矩阵

| ID | 设计命题 | Design Doc 证据 | 源码证据 | 结论 |
| --- | --- | --- | --- | --- |
| D0 | Uncache 的宏观意图 | 本地 checkout 缺失，未查阅 | 不适用 | 不从设计文档推断实现 |
| C1 | 物理 buffer 是独立 TileLink client | 不适用 | [Uncache.scala:191](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:191) | 已由源码确认 |
| C2 | data-MMIO 与 L1D→L2 是不同连接 | 不适用 | [MemBlock.scala:261](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:261)、[XSTile.scala:65](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/XSTile.scala:65)、[XSTile.scala:94](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/XSTile.scala:94) | 已由源码确认 |
| C3 | load 逻辑条目与物理条目用双 ID 对应 | 不适用 | [DCacheWrapper.scala:535](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:535)、[LoadQueueUncache.scala:466](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:466) | 已由源码确认 |

设计文档差异：无可比对的本地 Design Doc，故不存在“设计文档已验证”的条目；所有功能性表述以下述 Chisel 文件为准。

## 2. 先区分三个名字相近的层次

### 2.1 物理 UncacheBuffer、逻辑 LoadQueueUncache 与 StoreQueue 不是一个结构

| 层次 | 实例/文件 | 保存的核心状态 | 目的 | 释放条件 |
| --- | --- | --- | --- | --- |
| 物理传输层 | UncacheImp，Uncache.scala | XLEN 数据、mask、paddr/vaddr、TL 响应与 valid/inflight/waitSame/waitReturn | 合并同一 XLEN word 的 NC 请求、向 TileLink 发送、返回一个物理 sid | LSQ 接受 UncacheWordResp，即 resp.fire |
| load 语义层 | LoadQueueUncache，LoadQueueUncache.scala | 逻辑 load 的 uop、ROB 关系、异常、slaveId、s_idle/s_req/s_resp/s_wait | MMIO 到 ROB head 才发送；NC load 的数据/异常回写到 LDU | mmioOut.fire、ncOut.fire 或 redirect flush |
| store 语义层 | StoreQueue，StoreQueue.scala | 已分配 store、提交/地址/数据有效位、MMIO/NC 状态 | MMIO/NC store 在代码规定的提交条件后才送 Uncache | MMIO 完成并提交，或 NC 响应完成后的 SQ 生命周期 |

LoadQueue 在顶层显式例化 LoadQueueUncache，而不是直接把每个 LoadUnit 接至物理 UncacheImp。源码片段如下，后续的连接将其 uncache 接口接到 LSQ 边界。[LoadQueue.scala:214](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala:214)

~~~scala
val uncacheBuffer = Module(new LoadQueueUncache)
uncacheBuffer.io.uncache <> io.uncache
io.nack_rollback(0) := uncacheBuffer.io.rollback
~~~

物理模块则以 UncacheBufferSize 个 Reg 条目和独立状态向量工作，而不是用 LoadQueueUncache 的状态机作为其存储状态。[Uncache.scala:241](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:241)

~~~scala
val entries = Reg(Vec(UncacheBufferSize, new UncacheEntry))
val states = RegInit(VecInit(Seq.fill(UncacheBufferSize)(
  0.U.asTypeOf(new UncacheEntryState))))
~~~

因此，本文标题中的 UncacheBuffer 默认指物理 UncacheImp；讲到 load 的精确提交/异常语义时，明确写为 LoadQueueUncache。

### 2.2 端到端拓扑与职责

~~~mermaid
flowchart LR
  LDU[LoadUnit] --&gt;|分类后的 mmio/nc load| LQU[LoadQueueUncache]
  SQ[StoreQueue] --&gt;|提交/条件满足的 mmio/nc store| LSQ[LSQWrapper]
  LQU --&gt; LSQ
  LSQ --&gt;|UncacheWordReq: mid| MB[MemBlock pipe register]
  MB --&gt;|UncacheWordReq| UB[UncacheImp physical entries]
  UB --&gt;|TileLink A: source=sid| DMMIO[data-MMIO TL port]
  DMMIO --&gt;|TileLink D: source=sid| UB
  UB --&gt;|UncacheWordResp: id=sid| MB
  MB --&gt; LSQ
  LSQ --&gt;|is2lq route| LQU
  LQU --&gt;|mmioOut or ncOut| LDU
  LSQ --&gt;|store response| SQ
  UB --&gt;|store data forwarding| LDU
~~~

这个图中的 L1D 没有位于 Uncache 主路径上。MemBlock 分别例化 DCacheWrapper 与 Uncache；Uncache client 经 uncache_xbar、TLBuffer 到 uncache_port，而 XSTile 将该端口连接到 d_mmio_port。L1D client 另接 l1d_to_l2_buffer。[MemBlock.scala:257](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:257) [MemBlock.scala:286](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:286) [XSTile.scala:65](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/XSTile.scala:65) [XSTile.scala:99](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/XSTile.scala:99)

~~~scala
uncache_xbar := TLBuffer() := uncache.clientNode
uncache_port := TLBuffer.chainNode(2) := uncache_xbar
l2top.inner.d_mmio_port := memBlock.uncache_port
~~~

### 2.3 主模块契约：Who / Why / How / From / To

| 对象 | Who | Why | How | From | To |
| --- | --- | --- | --- | --- | --- |
| LoadUnit / StoreUnit 分类点 | 访存执行流水线产生并更新分类 | 把 cacheable、MMIO、PBMT NC、权限/非对齐异常分开 | s2 的 TLB/PMP/PBMT 条件和 kill 信号 | 译址、PMP、uop 的 nc/mmio/异常字段 | DCache 或 LSQ/LoadQueueUncache 的后续路径 |
| LoadQueueUncache entry | LoadQueue 拥有与释放 | 保留 load 的 ROB、回写和异常语义，不能把副作用语义交给纯总线 slot | FreeList 分配；s_idle/s_req/s_resp/s_wait；needFlushReg | Load S3 的 LqWriteBundle、ROB pending 信息、idResp | UncacheWordReq、ncOut/mmioOut、exception、rollback |
| LSQWrapper uncache arbiter | LSQWrapper 选择与回程分流 | 协调 load/store 共用物理 Uncache 接口 | s_idle/s_load/s_store 和 ROB age 比较；is2lq 路由 | LoadQueue 与 StoreQueue 的 req，Uncache 的 resp/idResp | MemBlock 的 uncache 请求/响应端 |
| UncacheImp entry | UncacheImp 更新、物理 sid 所有 | 将可发送总线事务与返回前占用状态分离；支持同 word 合并/顺序化 | e0 分配/合并，q0 A 发送，D 更新，r0 释放 | LSQ 的 UncacheWordReq 与 TileLink D | TileLink A、LSQ UncacheWordResp、Load forward |
| data-MMIO TileLink 端 | XSTile 连接 | 将非缓存数据访问交给外部 MMIO/uncache fabric | sid 作为 source，A/D valid-ready | Uncache clientNode | d_mmio_port；本文到此截止 |

## 3. 参数、地址粒度与接口契约

### 3.1 有效容量与 ID 宽度

| 参数/量 | 有效值 | 来源 | 影响 |
| --- | ---: | --- | --- |
| XLEN | 64 | [Configs.scala:40](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:40) | 一个物理条目保存 64-bit 数据，mask 为 8 bit |
| UncacheBufferSize | 16 | [Parameters.scala:236](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:236) | 16 个物理条目，TileLink source ID 范围 [0,16) |
| UncacheBufferIndexWidth | 4 | log2Up(16) | sid 与 UncacheWordResp.id 的宽度 |
| LoadUncacheBufferSize | 16 | [Parameters.scala:172](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:172) | LoadQueueUncache 的逻辑条目数；不是物理条目数量的定义来源 |
| LoadPipelineWidth | 3 | [Parameters.scala:214](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:214) | LoadQueueUncache 同拍最多观察 3 条 load 请求，并提供相应回写端口结构 |
| outstanding 默认值 | false | [Parameters.scala:243](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:243) | 复位后 smblockctl bit 7 为 0，除非软件改写 CSR |

KunminghuV2Config 在 DefaultConfig 上叠加 L2 配置和 CHI 开关，未在该类中覆写上表两个 Uncache size，因此此处采用 XSCoreParameters 的默认值。[Configs.scala:460](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:460) [Configs.scala:481](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:481)

### 3.2 这里的 block 不是 cache line

Uncache.scala 的 BLOCK_OFFSET 等于 log2Up(XLEN/8)。在 XLEN=64 下为 3，getBlockAddr 会丢掉地址低 3 bit；所有 addrMatch 都比较该结果。因此合并、waitSame、前递 CAM 的“同 block”是同一个 8-byte XLEN word，不是 64-byte DCache line。[Uncache.scala:32](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:32) [Uncache.scala:269](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:269)

~~~scala
def BLOCK_OFFSET = log2Up(XLEN / 8)
def getBlockAddr(x: UInt) = x >> BLOCK_OFFSET
def addrMatch(x: UncacheEntry, y: UncacheWordReq) =
  getBlockAddr(x.addr) === getBlockAddr(y.addr)
~~~

条目合并是字节级覆盖：新 mask 为 1 的字节覆盖旧 data，mask 取 OR；合并后从结果 mask 的最低置位字节重对齐 addr 和 vaddr。允许发出的 mask 必须是 1、连续且自然对齐的 1/2/4/8 byte 集合；发送时以 PopCount(mask) 形成 TileLink lgSize。若修改参数或放宽 mask 生成逻辑，必须同时验证这两个约束。[Uncache.scala:34](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:34) [Uncache.scala:88](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:88) [Uncache.scala:276](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:276) [Uncache.scala:404](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:404)

### 3.3 UncacheWordIO 的双 ID 含义

| 字段 | 产生者 | 消费者 | 含义 |
| --- | --- | --- | --- |
| UncacheWordReq.id | LoadQueueUncache 或 StoreQueue | UncacheImp 接收时读出 | 上游逻辑 owner 的 mid；对 load 是 LoadQueueUncache 的 entryIndex |
| UncacheIdResp.mid | UncacheImp | LSQWrapper 路由后由 LoadQueueUncache/StoreQueue 接收 | 原样回显上游逻辑 ID |
| UncacheIdResp.sid | UncacheImp | 上游逻辑条目 | 分配或合并后的物理 UncacheImp slot |
| UncacheWordResp.id | UncacheImp | LSQWrapper、随后上游逻辑条目 | 物理 sid；不是原 mid |
| UncacheWordResp.is2lq | UncacheImp | LSQWrapper | cmd 为 M_XRD 时为真，决定回到 load 或 store 侧 |
| UncacheWordResp.nc | UncacheImp | StoreQueue 等 | 保留 NC/MMIO 分类 |

Bundle 定义直接显示 req.id 的宽度是 uncacheIdxBits，而 idResp.mid 与 sid、resp.id 分别属于不同 ID 域。[DCacheWrapper.scala:535](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:535) [DCacheWrapper.scala:556](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:556) [DCacheWrapper.scala:563](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:563)

~~~scala
val id   = UInt(uncacheIdxBits.W)
val mid = UInt(uncacheIdxBits.W)
val sid = UInt(UncacheBufferIndexWidth.W)
val id  = UInt(UncacheBufferIndexWidth.W)
~~~

物理层在 req.fire 后下一拍给出 Valid 形式的 idResp；它不带 ready，因而消费者必须按 valid 对 mid/sid 采样。这里不是 Decoupled 响应，不应虚构一个 idResp 的 backpressure。[Uncache.scala:376](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:376)

~~~scala
io.lsq.idResp.valid := RegNext(e0_fire)
io.lsq.idResp.bits.mid := RegEnable(e0_req.id, e0_fire)
io.lsq.idResp.bits.sid := RegEnable(e0_sid, e0_fire)
~~~

一次物理合并不必是一个逻辑 mid 对一个 sid：每个已被 req.fire 接受的上游请求都会得到自己的 idResp，而若它们合并到同一物理 slot，会得到相同 sid。LoadQueueUncache 先按 mid 把 idResp 交给对应逻辑 entry，再以 slaveId==resp.id 将同一物理响应送给所有匹配 sid 的逻辑 entry；因此响应连接是按 sid 条件广播，而不是只回送一个被固定选择的 load entry。[LoadQueueUncache.scala:466](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:466) [LoadQueueUncache.scala:471](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:471)

~~~scala
when(i.U === io.uncache.idResp.bits.mid) {
  e.io.uncache.idResp <> io.uncache.idResp
}
when(e.io.slaveId.valid &&
  e.io.slaveId.bits === io.uncache.resp.bits.id) {
  e.io.uncache.resp <> io.uncache.resp
}
~~~

## 4. Request Path: Classification to Physical Entry

### 4.1 LoadUnit and StoreUnit Classify Actual Physical Attributes First

When the relevant TLB/PMP/PBMT conditions are available in LoadUnit S2, it combines PMA/MMIO, input NC, and input MMIO to derive <code>s2_actually_uncache</code>. It then suppresses the expected DCache response and asserts DCache <code>s2_kill</code>. The boolean precedence is the Scala/Chisel expression itself, so it must not be reduced to one generic MMIO bit. [LoadUnit.scala:1206](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1206>) [LoadUnit.scala:1306](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1306>) [LoadUnit.scala:1523](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1523>)

~~~scala
val s2_actually_uncache =
  !s2_in.tlbMiss && !s2_un_access_exception &&
  Pbmt.isPMA(s2_pbmt) && (s2_pmp.mmio && !s2_pmp.ld) ||
  s2_in.nc || s2_in.mmio
val s2_dcache_should_resp =
  !(s2_in.tlbMiss || s2_exception || s2_in.delayedLoadError ||
    s2_uncache || s2_prf)
io.dcache.s2_kill := s2_pmp.ld || s2_pmp.st ||
  s2_actually_uncache || s2_kill
~~~

StoreUnit has corresponding store classification and also kills DCache write intent. In some uncached vector or misaligned cases it directly forms an access or address-misaligned exception. Thus, reaching an Uncache address range does not necessarily produce a physical bus request. [StoreUnit.scala:469](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:469>) [StoreUnit.scala:494](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:494>) [StoreUnit.scala:504](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:504>)

### 4.2 LoadQueueUncache Retains Load Ordering, Exception, and Writeback Semantics

LoadQueueUncache sorts <code>LoadPipelineWidth</code> input lanes by ROB age, then on the following cycle removes redirect, exception, and replay cases before allocating FreeList entries only for MMIO or NC requests. The request slot for a FreeList allocation is offset by PopCount of earlier lanes. If insufficient logical slots exist, the load cannot enter the logical buffer and later produces rollback; physical UncacheImp does not overwrite an entry that remains valid. [LoadQueueUncache.scala:345](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:345>) [LoadQueueUncache.scala:353](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:353>) [LoadQueueUncache.scala:379](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:379>) [LoadQueueUncache.scala:561](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:561>)

Each logical UncacheEntry has four states: <code>s_idle</code>, <code>s_req</code>, <code>s_resp</code>, and <code>s_wait</code>. An MMIO load may transition from idle to request only when <code>pendingld</code> is set and its <code>robIdx</code> equals <code>pendingPtr</code>. An NC load can be ready to send immediately when <code>needFlush</code> is false. This ROB-head gate is a load-side ordering policy, not an inherent rule of physical UncacheImp. [LoadQueueUncache.scala:68](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:68>) [LoadQueueUncache.scala:122](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:122>)

~~~scala
val canSendReq = req_valid && !needFlush && Mux(
  req.nc, true.B,
  pendingld && req.uop.robIdx === pendingPtr
)
~~~

The logical load request is fixed to <code>M_XRD</code> and carries paddr, vaddr, its logical entryIndex as mid, NC, and <code>memBackTypeMM</code>. After the physical response, NC writeback uses <code>ncOut</code> while MMIO writeback uses <code>mmioOut</code>. Denied becomes <code>loadAccessFault</code>; corrupt without denied becomes <code>hardwareError</code>. [LoadQueueUncache.scala:173](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:173>) [LoadQueueUncache.scala:188](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:188>) [LoadQueueUncache.scala:205](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:205>)

### 4.3 LSQWrapper: Load/Store Arbitration and Return Routing

With <code>pendingstate=s_idle</code>, LSQWrapper selects a load or store request. If both are valid, load wins only when <code>load.robIdx < store.robIdx</code>; equality makes the expression false, so store wins. When outstanding is disabled, <code>req.fire</code> records <code>s_load</code> or <code>s_store</code> until <code>resp.fire</code> returns the wrapper to idle. With outstanding enabled, an NC request may leave the state idle. [LSQWrapper.scala:265](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:265>)

~~~scala
val selectLq = (loadQueue.io.uncache.req.valid &&
  !storeQueue.io.uncache.req.valid) || (
  loadQueue.io.uncache.req.valid &&
  storeQueue.io.uncache.req.valid &&
  loadQueue.io.uncache.req.bits.robIdx <
    storeQueue.io.uncache.req.bits.robIdx
)
~~~

Response and idResp are not routed using <code>pendingstate</code>; they use <code>is2lq</code> returned by the physical layer. This lets a physical-entry response return to the right logical subsystem based on stored command. [LSQWrapper.scala:302](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:302>) [LSQWrapper.scala:312](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:312>)

MemBlock inserts an <code>AddPipelineReg</code> on both sides of physical UncacheImp. It holds a valid bit: out.fire clears valid, in.fire sets it, isFlush clears it, and in.ready is <code>!valid || out.ready</code>. This is an elastic register that can be stalled by sink backpressure, not a mechanically fixed extra end-to-end cycle. [MemCommon.scala:99](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemCommon.scala:99>) [MemBlock.scala:1505](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1505>)

## 5. Physical UncacheImp Entry State and Lifetime

### 5.1 Entry Contents and Sendability Predicates

Each physical entry stores the request command, physical/virtual address, data, mask, NC, <code>memBackTypeMM</code>, and D-response <code>nderr</code>/<code>denied</code>/<code>corrupt</code>. A read response overwrites data; a write response does not. When forming <code>UncacheWordResp</code>, id is sid, <code>is2lq</code> is true exactly for <code>M_XRD</code>, and miss/replay/tag_error/error are fixed false. [Uncache.scala:57](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:57>) [Uncache.scala:100](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:100>) [Uncache.scala:114](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:114>)

State is not one enum; it is four bits. The diagram below is the lifetime derived from those predicates. <code>waitSame</code> blocks an entry when an earlier request for the same 8-byte word exists and can coexist with valid.

~~~mermaid
stateDiagram-v2
  [*] --&gt; Free
  Free --&gt; Ready: req.fire and allocate
  Ready --&gt; Ready: req.fire and merge
  Ready --&gt; WaitSame: earlier same-word entry sends A
  WaitSame --&gt; Ready: earlier same-word D fire
  Ready --&gt; Inflight: TileLink A fire
  Inflight --&gt; WaitReturn: TileLink D fire
  WaitReturn --&gt; Free: LSQ resp.fire
  WaitSame --&gt; Free: response path then LSQ resp.fire
~~~

| Predicate | Exact definition | Consumer | Meaning |
|---|---|---|---|
| isValid | valid | Allocation, empty, forwarding | Physical entry is occupied. |
| can2Bus | valid and not inflight/waitSame/waitReturn | q0 issue arbitration | May generate an A request. |
| canMerge | valid and not inflight | e0 merge decision | Has not issued yet, so data/mask can merge. |
| can2Lsq | valid and waitReturn | r0 return arbitration | D response has arrived and it can return upstream. |
| isFwdOld | valid and inflight or waitReturn | Store-to-load forwarding | An older store was issued or has a response awaiting pickup. |
| isFwdNew | valid, not inflight, not waitReturn, and waitSame | Store-to-load forwarding | A newer store waits for an old same-word store. |

The definitions and D-handling assertion are in [Uncache.scala:136](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:136>).

~~~scala
def can2Bus() = valid && !inflight && !waitSame && !waitReturn
def can2Lsq() = valid && waitReturn
def canMerge() = valid && !inflight
def updateUncacheResp() = {
  assert(inflight)
  inflight := false.B
  waitReturn := true.B
}
~~~

### 5.2 Reset, First Request, and Empty

<code>states</code> reset to all zeros, <code>uState</code> resets to <code>s_idle</code>, and every <code>noPending</code> bit resets true. <code>entries</code> is a non-initialized Reg(Vec(...)); correctness depends on <code>states.valid</code> being false so its payload is not read as valid. The first legal <code>req.fire</code> calls entry.set in an empty slot and sets valid; the Valid idResp announces the sid only in the following cycle. Verification must not require reset entries to have zero data/address. [Uncache.scala:241](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:241>) [Uncache.scala:366](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:366>)

<code>empty</code> is the inverse OR of all <code>states.isValid</code>. Thus <code>flush.empty</code> says all physical entries are released, not merely that no transaction is waiting for TileLink D. [Uncache.scala:495](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:495>)

## 6. e0: Allocation, Merge, Rejection, and ID Acknowledgement

### 6.1 Merge Eligibility and Conflict Semantics

For every physical entry, e0 computes reject, merge, and allocWaitSame vectors simultaneously. A primary merge requires all of the following:

1. The request vaddr and existing entry vaddr belong to the same 8-byte word.
2. Commands match.
3. Both old and new accesses are NC.
4. <code>memBackTypeMM</code> matches.
5. ORing both masks still creates a contiguous, naturally aligned legal size.
6. The entry receives no matching D response in this cycle and is not in <code>waitReturn</code>.

A secondary merge additionally requires the existing entry to satisfy <code>canMerge</code> and not be selected for q0 A issue in this cycle. If a same-word entry exists but fails the primary condition, <code>e0_rejectVec</code> rejects the request rather than opening an erroneous second same-word entry. [Uncache.scala:289](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:289>) [Uncache.scala:299](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:299>) [Uncache.scala:343](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:343>)

~~~scala
e0_rejectVec(i) := valid && isAddrMatch && !canMerge1
e0_mergeVec(i) := valid && isAddrMatch && canMerge1 && canMerge2
e0_allocWaitSameVec(i) := valid && isAddrMatch &&
  canMerge1 && !canMerge2
assert(PopCount(e0_mergeVec) <= 1.U)
~~~

### 6.2 Priority, Full State, and Ready

Empty entries and mergeable entries are selected using <code>PriorityEncoderWithFlag</code>. That helper recursively prioritizes the head of the input sequence, so a vector created through <code>in.zipWithIndex</code> favors lower valid indices; it is not round-robin. Uncache e0 has no fairness-rotation state. A system-observable starvation result under sustained traffic still requires simulation. [PriorityMuxDefault.scala:38](</home/yanyusong/xs-memory-env/XiangShan/utility/src/main/scala/utility/PriorityMuxDefault.scala:38>) [PriorityMuxDefault.scala:52](</home/yanyusong/xs-memory-env/XiangShan/utility/src/main/scala/utility/PriorityMuxDefault.scala:52>) [Uncache.scala:357](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:357>)

~~~scala
val (e0_mergeIdx, e0_canMerge) = PriorityEncoderWithFlag(e0_mergeVec)
val (e0_allocIdx, e0_canAlloc) = PriorityEncoderWithFlag(e0_invalidVec)
val e0_reject = do_uarch_drain ||
  (!e0_canMerge && !e0_invalidVec.asUInt.orR) ||
  e0_rejectVec.reduce(_ || _)
req_ready := !e0_reject
~~~

The actual acceptance event is <code>req.fire</code>, not <code>req.valid</code>. With valid asserted but ready low, physical entries, states, and idResp must remain unchanged. When no mergeable or invalid entry exists, ready becomes low and applies physical backpressure. The load side additionally has rollback for insufficient LoadQueueUncache capacity; the two kinds of full state must not be confused. [Uncache.scala:338](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:338>) [LoadQueueUncache.scala:587](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:587>)

### 6.3 Same-Word Send Order

When a q0 entry performs A.fire, every other valid, non-waitReturn entry for the same 8-byte word is marked <code>waitSame</code>. On D.fire, waitSame is cleared for same-word followers. A follower for the same word therefore cannot reach the bus in parallel; it waits for the earlier D response. This is not global serialization of unrelated addresses. [Uncache.scala:433](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:433>) [Uncache.scala:463](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:463>)

## 7. q0/r0: TileLink A/D and LSQ Return

### 7.1 q0 Issue and Command Construction

q0 chooses one <code>can2Bus</code> entry with lower sid priority. With <code>enableOutstanding</code> true, it may select any can2Bus entry; with it false, it can select only when <code>uState=s_idle</code>, forming a conservative one-at-a-time A to D to LSQ transaction mode. The uState in this mode tracks only the non-outstanding serial policy; it does not replace per-entry flags. [Uncache.scala:308](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:308>) [Uncache.scala:395](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:395>)

~~~scala
val q0_canSentVec = sizeMap(i =>
  (io.enableOutstanding || uState === s_idle) &&
  states(i).can2Bus()
)
mem_acquire.valid := q0_canSent && !io.wfi.wfiReq
mem_acquire.bits := Mux(q0_isStore, q0_store, q0_load)
~~~

Loads use edge.Get and stores use edge.Put; <code>fromSource</code> is directly q0_canSentIdx, namely sid. A.fire marks the entry inflight and sets <code>noPending[sid]</code> false. <code>mem_acquire.valid</code> is suppressed by a WFI request, so WFI pauses at the A interface rather than deleting entries. [Uncache.scala:413](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:413>) [Uncache.scala:419](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:419>) [Uncache.scala:429](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:429>)

### 7.2 TileLink D Response and One-Beat Assumption

The D channel is always ready. At D.fire it uses <code>mem_grant.bits.source</code> to recover sid, updates data/error fields, clears inflight, sets waitReturn, and restores <code>noPending[sid]</code> true. Source requires <code>refill_done</code> and directly asserts that the Uncache response is one beat. A multi-beat downstream response would trigger the assertion rather than being assembled by this implementation. [Uncache.scala:456](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:456>)

~~~scala
mem_grant.ready := true.B
when (mem_grant.fire) {
  val id = mem_grant.bits.source
  entries(id).update(mem_grant.bits)
  states(id).updateUncacheResp()
  noPending(id) := true.B
  assert(refill_done)
}
~~~

Only when this sid belongs to a store and denied or corrupt is set does UncacheImp assert <code>busError.ecc_error.valid</code>. Load denied/corrupt remains in UncacheWordResp and is mapped to exceptions by LoadQueueUncache. This output named ecc_error is not the sole error channel for all loads and stores. [Uncache.scala:477](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:477>) [LoadQueueUncache.scala:238](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:238>)

### 7.3 r0 Return and Physical-Entry Release

r0 likewise selects a can2Lsq entry with lower sid priority. <code>resp.valid</code> is high when an entry waits for return, but only <code>resp.fire</code> invokes <code>updateReturn</code> to clear valid, inflight, waitSame, and waitReturn. If the LSQ response pipe or its consumer backpressures, the entry remains waitReturn and consumes physical capacity even though its D response has arrived. [Uncache.scala:486](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:486>)

~~~scala
resp.valid := r0_canSent
resp.bits := entries(r0_canSentIdx).toUncacheWordResp(r0_canSentIdx)
when(resp.fire) {
  states(r0_canSentIdx).updateReturn()
}
~~~

## 8. Store-to-Load Forwarding and Alias Protection

UncacheImp provides <code>LoadPipelineWidth</code> lanes of LoadForwardQueryIO. Candidates include only valid store entries. f0 uses a vaddr 8-byte-word CAM for a fast mask; f1 registers paddr and verifies physical-address matching. In f1, old inflight/waitReturn stores and new waitSame stores merge byte data through <code>doMerge</code>, so a load can observe the newest byte-overwrite relationship while same-word stores are serialized. [Uncache.scala:503](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:503>) [Uncache.scala:524](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:524>) [Uncache.scala:538](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:538>)

~~~mermaid
flowchart LR
  Q[LoadForwardQueryIO valid vaddr] --&gt; F0[f0 vaddr word CAM]
  S1[valid store entries] --&gt; F0
  F0 --&gt;|old: inflight or waitReturn| O[old mask/data]
  F0 --&gt;|new: waitSame| N[new mask/data]
  O --&gt; M[byte-wise doMerge]
  N --&gt; M
  P[DTLB paddr] --&gt; F1[f1 paddr word CAM]
  F1 --&gt;|same match set| R[forwardData and forwardMask]
  F1 --&gt;|vaddr/paddr mismatch| D[set matchInvalid and request drain]
~~~

<code>f1_tagMismatch</code> means the f0 virtual-word match set differs from the f1 physical-word match set. It sets <code>forward.matchInvalid</code> and, while nonempty, sets <code>f1_needDrain</code>. <code>do_uarch_drain</code> then rejects new e0 requests until empty. This drain protects against using a wrong forwarding candidate under virtual-address aliasing; it does not directly clear the buffer. [Uncache.scala:249](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:249>) [Uncache.scala:251](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:251>) [Uncache.scala:515](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:515>) [Uncache.scala:550](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:550>)

~~~scala
f1_needDrain := f1_tagMismatchVec.asUInt.orR && !empty
when((f1_needDrain || io.flush.valid) && !empty) {
  do_uarch_drain := true.B
}
forward.matchInvalid := f1_tagMismatchVec(i)
~~~

No explicit assertion for one-hot forwarding candidates was found before this Mux1H. The e0 PopCount assertion applies to mergeVec, not forwarding-candidate uniqueness. Generated RTL or simulation should check old/new selection constraints and byte-coverage semantics for one forwarding query.

## 9. Timing, Throughput, and Backpressure

### 9.1 Effective Handshake Boundaries

| Segment | Start | End | Fixed register boundary | Variable factors |
|---|---|---|---|---|
| LSQ to UncacheImp request | LSQ <code>io.uncache.req.fire</code> | UncacheImp <code>req.fire</code> | MemBlock AddPipelineReg | Upstream arbitration, pipeline-register occupancy, e0 req_ready |
| Physical acceptance to ID acknowledgement | UncacheImp <code>req.fire</code> | <code>idResp.valid</code> | <code>RegNext(e0_fire)</code> | idResp has no ready; downstream must sample valid |
| A request | Entry can2Bus | <code>mem_acquire.fire</code> | No fixed wait-cycle promise | q0 priority, enableOutstanding, uState, WFI, A.ready |
| Bus wait | A.fire | D.fire | None | Downstream TL response time; source requires one D beat |
| D to LSQ response | D.fire | LSQ <code>resp.fire</code> | MemBlock response AddPipelineReg | r0 priority and downstream ready |
| Logical load writeback | Logical entry receives resp.fire | <code>mmioOut.fire</code> or <code>ncOut.fire</code> | AddPipelineReg | LDU writeback backpressure and redirect |

Both q0 and r0 selection use <code>PriorityEncoderWithFlag</code>, so the physical layer has no round-robin policy. A best-case issue rate must not be presented as a fairness guarantee. With default <code>outstanding=false</code>, uState permits the next q0 only after the A.fire to D.fire to resp.fire loop completes. The best steady-state request-start interval is therefore constrained by completion of those three events; source does not fix actual D latency. [Uncache.scala:308](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:308>) [Uncache.scala:395](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:395>)

### 9.2 Two Important Timing Sketches

#### Ordinary NC/MMIO Physical Transaction

~~~waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p......" },
    { "name": "lsq.req.valid", "wave": "01....." },
    { "name": "lsq.req.ready", "wave": "01....." },
    { "name": "req.fire/e0_fire", "wave": "010...." },
    { "name": "idResp.valid", "wave": "0010..." },
    { "name": "tl.a.valid", "wave": "0001..." },
    { "name": "tl.a.ready", "wave": "1111111" },
    { "name": "tl.a.fire", "wave": "00010.." },
    { "name": "state.inflight", "wave": "0001110" },
    { "name": "tl.d.valid", "wave": "0000010" },
    { "name": "tl.d.ready", "wave": "1111111" },
    { "name": "lsq.resp.valid", "wave": "0000001" }
  ],
  "head": { "text": "Illustration: A-to-D delay varies; idResp is Valid in the cycle after e0_fire" }
}
~~~

#### Serial Gate in Non-Outstanding Mode

~~~waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p........" },
    { "name": "enableOutstanding", "wave": "0........" },
    { "name": "entry0 can2Bus", "wave": "01......." },
    { "name": "entry1 can2Bus", "wave": "001......." },
    { "name": "uState idle", "wave": "1......11" },
    { "name": "A(entry0).fire", "wave": "010......" },
    { "name": "D(entry0).fire", "wave": "00010...." },
    { "name": "LSQ resp(entry0).fire", "wave": "0000010.." },
    { "name": "A(entry1).fire", "wave": "000000010" }
  ],
  "head": { "text": "Illustration: entry1 waits for uState to return idle even when ready" }
}
~~~

The first diagram does not state that D arrives in any fixed cycle. The second depicts causal gating, not an actual two-cycle D latency.

## 10. Exceptions, WFI, Flush, Commit Visibility, and Difftest

### 10.1 Layers of Error Propagation

| Source | Physical UncacheImp | Logical/architectural continuation |
|---|---|---|
| TileLink D.denied | Records <code>resp_denied</code> and returns <code>UncacheWordResp.denied</code> | LoadQueueUncache sets loadAccessFault; StoreQueue MMIO sets storeAccessFault. |
| TileLink D.corrupt | Records <code>resp_corrupt</code> | Without denied, load sets hardwareError; store follows the corresponding behavior. |
| Store denied/corrupt | Also sets <code>busError.ecc_error</code> | This output covers only the store condition. |
| Address/translation/PMP and related faults | Upstream LoadUnit/StoreUnit already classify and may raise an exception and kill DCache | They are not D-bus errors and must not be reclassified after entering Uncache. |

LoadQueueUncache's exception output is valid at writeback and connects to the LoadQueue exceptionBuffer. Thus denied/corrupt architectural exceptions do not remain only in physical-buffer registers. [LoadQueueUncache.scala:238](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:238>) [LoadQueue.scala:284](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala:284>)

### 10.2 WFI and Flush

<code>noPending</code> becomes false for a sid on A.fire and true on D.fire. <code>wfiSafe</code> requires all noPending bits true and <code>wfiReq</code> true, then passes through <code>GatedValidRegNext</code>. It proves only that no physical bus D response is in flight; it does not prove every waitReturn or waitSame entry has been released. MemBlock's final <code>wfiSafe</code> also ANDs DCache, LSQ, and PTW wfiSafe, so Uncache wfiSafe alone cannot prove the core may enter WFI. [Uncache.scala:433](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:433>) [Uncache.scala:481](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:481>) [MemBlock.scala:678](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:678>)

<code>flush.valid</code> reaches Uncache through the SBuffer flush chain, and <code>empty</code> joins SBuffer empty to form <code>sbIsEmpty</code>. On flush, physical Uncache prevents new intake through <code>do_uarch_drain</code> and waits for empty; source does not iterate over entries/states and clear them directly on <code>flush.valid</code>. For already-issued side-effecting MMIO, correct behavior is drain, not speculative discard. [MemBlock.scala:1768](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1768>) [MemBlock.scala:1778](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1778>) [Uncache.scala:247](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:247>)

LoadQueueUncache handles redirect more finely: idle/request can flush immediately; in <code>s_resp</code>, <code>needFlushReg</code> delays flush until a response arrives; <code>s_wait</code> returns idle on needFlush or writeback. It does not silently reuse a logical entry before a load response already in the physical layer returns. [LoadQueueUncache.scala:78](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:78>) [LoadQueueUncache.scala:128](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:128>)

### 10.3 MMIO Ordering and Commit

ROB records notifications from LoadQueue/StoreQueue in <code>robEntries(...).mmio</code>; it then derives <code>pendingMMIOld</code>, <code>pendingld</code>, <code>pendingst</code>, and <code>pendingPtr</code> from the commit head. An ordinary MMIO load in LoadQueueUncache is gated by <code>pendingld/pendingPtr</code>. StoreQueue's MMIO state reaches <code>s_req</code> only after <code>pendingst</code>, current deqPtr, allocated, data-valid, address-valid, and no-exception conditions. This confirms that MMIO execution is not the freely speculative path used by normal cacheable loads. [Rob.scala:556](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:556>) [Rob.scala:838](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:838>) [LoadQueueUncache.scala:122](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:122>) [StoreQueue.scala:845](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:845>)

NC stores follow another path: they require committed, allocated, unfinished, address/data-valid, non-vector, no exception, and non-MMIO conditions. After slave acknowledgement in idResp, an enabled outstanding mode may return them to <code>nc_idle</code>; otherwise they wait for response. This acknowledgement is essential because source comments say an NC store first needs assurance that Uncache has accepted it so store-data forwarding can operate. [StoreQueue.scala:917](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:917>) [StoreQueue.scala:929](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:929>) [StoreQueue.scala:939](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:939>)

### 10.4 Outstanding Control and Difftest

CSR <code>smblockctl</code> bit 7 is <code>uncache_write_outstanding_enable</code>. It drives both UncacheImp <code>enableOutstanding</code> and LSQWrapper <code>uncacheOutstanding</code>; reset comes from EnableUncacheWriteOutstanding, whose default parameter is false. A software change to this CSR simultaneously changes physical q0 gating, LSQ pendingstate, and NC-store behavior, so validation must cover both settings. [CSR.scala:538](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/CSR.scala:538>) [CSR.scala:554](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/CSR.scala:554>) [MemBlock.scala:1398](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1398>)

When <code>env.EnableDifftest</code> is enabled, UncacheImp emits <code>DiffUncacheMMStoreEvent</code> only for a store A.fire with <code>memBackTypeMM</code> true. This is an observation anchor for physical MMIO-store issue. The file contains no equivalent generic uncached-load Difftest event, so it cannot establish all architectural load-retirement comparison behavior. [Uncache.scala:445](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:445>)

## 11. Cross-Boundary Code Analysis

| Boundary | First fragment/stage | Second fragment/stage | Independent checks | Merge/order state | Fault and recovery |
|---|---|---|---|---|---|
| Virtual page | Load/Store Unit has vaddr plus TLB/PMP/PBMT results | Physical classification creates a paddr request | TLB miss, PMP, PBMT/PMA/MMIO, and existing exceptions | UncacheEntry keeps vaddr for forwarding and paddr for TileLink | Redirect records needFlush in LoadQueueUncache; forwarding vaddr/paddr-set mismatch drains. |
| 8-byte word | Low-address byte mask | Later/merged mask for same word | Same word, command, NC, memBackTypeMM, legal mask | <code>doMerge</code> overwrites bytes; waitSame waits for earlier D before follower A | Illegal/nonmergeable same word drives e0 ready low; it is not automatic packet splitting. |
| Cache line | No DCache tag/data/miss/refill main path | No second cache-line transaction | Not applicable: getBlockAddr uses an 8-byte word | No cache-line assembler or MSHR | DCache line-cross behavior must not be projected onto this module. |
| MMIO/NC | LoadUnit/StoreUnit classify MMIO/NC | LoadQueueUncache/StoreQueue creates request | Load ROB-head gate; store commit/address/data checks | mid to sid mapping; sid is TL source; LSQ uses is2lq on return | denied/corrupt become exceptions; flush drains; full logical buffer rolls back. |
| Misaligned and uncache | LoadMisalignBuffer receives the split access MMIO/NC flag | It does not send multiple fragments to physical Uncache and reassemble them | globalUncache is captured | Direct exception semantics, no uncache-fragment assembly | Address-misaligned exception path; not multiple freely split MMIO operations. |

The last row has direct evidence: when either split load is marked uncache, LoadMisalignBuffer enters <code>s_wb</code>; comments delegate it to software <code>loadAddrMisaligned</code>, and writeback deasserts rfWen. Although the file retains cross-page exception-address overwrite registers, <code>overwriteExpBuf.valid</code> is hardwired false, so retained logic must not be described as a currently active cross-page exception overwrite function. [LoadMisalignBuffer.scala:183](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:183>) [LoadMisalignBuffer.scala:213](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:213>) [LoadMisalignBuffer.scala:561](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:561>) [LoadMisalignBuffer.scala:641](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:641>)

For combined scenarios, check in this order: first per-fragment virtual address plus translation/permissions, then PMA/PBMT/MMIO/NC classification, then whether Uncache entry is allowed. For a split uncache load, current LoadMisalignBuffer behavior is an exception, not multiple-fragment convergence in physical UncacheImp.

## 12. Verification Points Requiring Special Attention

| Verification ID | Risk/invariant | Directed stimulus | Expected observation | Required checker/coverage |
|---|---|---|---|---|
| UB_RESET_FIRST | states reset to zero while entries do not; first req must not read stale payload | Legal first M_XRD, then first M_XWR after reset | Only the req.fire sid becomes valid; idResp valid arrives next cycle; invalid entries do not affect q0/r0 | FSM and occupancy checkers; e0_alloc_simple coverage |
| UB_HOLD_REQ | req.valid with e0_reject must not be accepted | Fill all 16 physical entries, or make a same-word nonmergeable request; hold req.valid | req.ready=0, e0_fire=0, entries/states/idResp unchanged | Handshake and storage-conflict checkers |
| UB_MERGE_MASK | Merge cannot lose bytes or create illegal lgSize | Same-word, same-cmd, same-NC, same-memBackTypeMM partial-byte stores; cover 1/2/4/8-byte masks | New mask overwrites data, masks OR, address aligns to low set byte, A lgSize matches PopCount | Data scoreboard, assertions, e0_merge coverage |
| UB_SAMEWORD_ORDER | A follower for one word must not A.fire before the earlier D.fire | Issue a store, then create a mergeable/following same-word entry while it is inflight | Follower waitSame=1; earlier D.fire clears it; only then follower A.fire | FSM checker, ordering scoreboard, progress checker |
| UB_A_D_SID | TL source and D source identify exactly one sid | Multiple different words, outstanding=1, D responses returned out of order | A.fromSource=sid; D.source updates same entry; one-beat assertion holds; every sid enters waitReturn once | ID scoreboard, TL protocol checker, C_SAME_ENTRY_RW |
| UB_RESP_BACKPRESSURE | D-to-LSQ backpressure cannot release early | Hold r0 downstream ready low while multiple D responses return | waitReturn entry stays valid; only resp.fire frees it; no duplicate resp | Handshake/occupancy checkers, PB_BACKPRESSURE_AMPLIFICATION |
| UB_FWD_ALIAS_DRAIN | A virtual-to-physical CAM mismatch must prohibit wrong forwarding and drain | f0 virtual same-word but f1 physical mismatch, with nonempty buffer | matchInvalid=1; f1_needDrain=1; new e0 rejected until empty | Alias, flush/drain, and P_DEADLOCK_ALL_STALL checkers |
| UB_FLUSH_INFLIGHT | Flush drains rather than clears in-flight side effects | Assert flush.valid after A.fire but before D, while upstream holds req.valid | do_uarch_drain blocks new acceptance; in-flight D reaches r0; drain ends after empty | Flush/replay and progress checkers |
| UB_WFI | wfiSafe means noPending all true, not necessarily buffer empty | Create a waitReturn entry after D, then assert wfiReq | Observe difference between noPending and states.valid; system wfiSafe still depends on DCache/LSQ/PTW | WFI property, cross-module scoreboard |
| LQU_MMIO_HEAD | MMIO load cannot issue before ROB head | Two MMIO loads, younger ready first; vary pendingPtr | Only robIdx==pendingPtr logical entry enters s_req; NC may differ | ROB-order and arbiter checkers |
| LQU_FULL_ROLLBACK | Full logical load buffer must recover the oldest unchecked load | Fill LoadUncacheBufferSize and submit multiple MMIO/NC lanes | reqNeedCheck selects oldest by ROB age; requests already flushed by redirect do not emit rollback | Rollback checker, age scoreboard, PB_RECOVERY_THROUGHPUT |
| UB_OUTSTANDING_MODE | CSR bit 7 changes serialization and return timing | Cover smblockctl bit7=0/1, NC stores, multiple different words | 0 enforces uState serial loop; 1 permits several A in flight and LSQWrapper may remain idle for NC | Configuration coverage, throughput checker, ID scoreboard |

Implementation anchors are [Uncache.scala:338](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:338>), [Uncache.scala:395](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:395>), [Uncache.scala:456](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:456>), [Uncache.scala:503](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:503>), [LoadQueueUncache.scala:552](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:552>), and [CSR.scala:568](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/CSR.scala:568>).

## 13. Two Complete Operation Paths

### 13.1 Ordinary NC Load with a Successful Physical Response

1. LoadUnit completes relevant address-attribute checks in S2, derives <code>s2_uncache</code>, and suppresses expected DCache response/activity.
2. LoadQueueUncache receives the MMIO/NC load in S2 and allocates a logical entry when no exception/replay/redirect applies. An NC request does not wait for <code>pendingPtr</code>.
3. LSQWrapper arbitrates it with StoreQueue and sends the request into the MemBlock request elastic register.
4. On <code>req.fire</code>, UncacheImp e0 allocates or merges a physical sid; a Valid idResp in the following cycle lets the logical entry record <code>slaveId=sid</code>.
5. q0 selects the can2Bus entry. A.fire marks it inflight; other entries for the same 8-byte word become waitSame.
6. D.fire returns data/error with <code>source=sid</code>, moves the entry to waitReturn, and clears waitSame on same-word followers.
7. Only r0 <code>resp.fire</code> releases physical sid. LSQWrapper uses is2lq to send it to LoadQueueUncache, which enters s_wait and writes data/possible exception through ncOut.
8. <code>ncOut.fire</code> releases the logical entry. Physical sid and logical mid need not be released in the same cycle.

### 13.2 Same-Word Store-Forwarding Alias Protection and Drain

1. An NC store has been accepted by physical UncacheImp and is a valid forwarding candidate, possibly inflight or waitReturn.
2. LoadForwardQueryIO uses a virtual-address word CAM in f0 to obtain a fast candidate mask.
3. f1 receives DTLB paddr and rechecks with a physical-address CAM. If its candidate set differs from f0, matchInvalid is asserted.
4. <code>f1_needDrain</code> raises <code>do_uarch_drain</code> while nonempty; e0 rejects new requests while existing entries drain through A/D/r0.
5. After empty, do_uarch_drain deasserts and new requests may resume. This protects forwarding correctness, but source does not specify its maximum duration because it depends on bus D timing and r0 consumer ready.

## 14. Summary and Open Verification Questions

Confirmed active behavior:

- UncacheImp is an independent 16-entry TileLink client through <code>d_mmio_port</code>, not the L1D main pipeline.
- The physical layer uses sid both as TileLink source and <code>UncacheWordResp.id</code>; the logical load layer uses mid for uop/ROB/exception state, and idResp establishes mid-to-sid.
- Merge and same-address ordering use an 8-byte XLEN word. Mask merge has explicit contiguous-aligned constraints, and same-word followers wait for the earlier D through waitSame.
- Outstanding defaults false and is controlled at runtime by smblockctl bit 7; the bit affects physical q0, LSQWrapper, and NC-store behavior together.
- Flush drains instead of clearing in-flight transactions; noPending for WFI does not equal physical buffer empty.
- Load denied/corrupt becomes loadAccessFault/hardwareError in LoadQueueUncache; corresponding store errors also trigger physical busError output.

Questions still requiring elaboration, generated RTL, or waveforms:

1. Source has no dedicated assertion for Mux1H forwarding-candidate uniqueness. Cover multiple same-word store candidates.
2. Fixed lower-sid priority is confirmed by the helper, but the system-level starvation boundary under sustained input/response backpressure needs dynamic proof.
3. Exact minimum/maximum cycles from AddPipelineReg and cross-layer ready combinations require waveform measurement; static source proves only register boundaries and stall conditions.
4. The actual timing of downstream TileLink denied/corrupt, source IDs, and D-response out-of-order range lies outside this module and needs SoC integration simulation.

## 4. 请求从分类到物理条目的路径

### 4.1 LoadUnit 与 StoreUnit 先做真实物理属性分类

LoadUnit s2 在 TLB/PMP/PBMT 相关条件可用时，结合 PMA/MMIO、输入 nc 和输入 mmio 判断 s2_actually_uncache；之后抑制 DCache 期望响应并置 DCache s2_kill。注意源码表达式的布尔优先级由 Scala/Chisel 表达式本身决定，本文不将它简化为单一“MMIO 位”。[LoadUnit.scala:1206](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1206) [LoadUnit.scala:1306](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1306) [LoadUnit.scala:1523](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1523)

~~~scala
val s2_actually_uncache =
  !s2_in.tlbMiss && !s2_un_access_exception &&
  Pbmt.isPMA(s2_pbmt) && (s2_pmp.mmio && !s2_pmp.ld) ||
  s2_in.nc || s2_in.mmio
val s2_dcache_should_resp =
  !(s2_in.tlbMiss || s2_exception || s2_in.delayedLoadError ||
    s2_uncache || s2_prf)
io.dcache.s2_kill := s2_pmp.ld || s2_pmp.st ||
  s2_actually_uncache || s2_kill
~~~

StoreUnit 对 store 有对应分类并同样 kill DCache 写意图。对 vector 或不对齐的某些 uncached 场景，它直接形成访问异常或地址未对齐异常；这说明“已经判到 Uncache 空间”不等于必然能产生一个物理总线请求。[StoreUnit.scala:469](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:469) [StoreUnit.scala:494](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:494) [StoreUnit.scala:504](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:504)

### 4.2 LoadQueueUncache：保留 load 的顺序、异常与回写语义

LoadQueueUncache 先按 ROB age 排序 LoadPipelineWidth 路输入，在下一拍筛除 redirect、异常、replay，并只为 mmio 或 nc 分配 FreeList 条目。free list 的请求槽由前面 lane 的 PopCount 偏移决定；若没有足够空闲条目，该 load 不能进入逻辑 buffer，随后会形成 rollback，而不是让物理 UncacheImp 覆盖一个仍有效的条目。[LoadQueueUncache.scala:345](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:345) [LoadQueueUncache.scala:353](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:353) [LoadQueueUncache.scala:379](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:379) [LoadQueueUncache.scala:561](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:561)

每个逻辑 UncacheEntry 有四态：s_idle、s_req、s_resp、s_wait。MMIO load 只有 pendingld 且自己的 robIdx 等于 pendingPtr 时才能 s_idle→s_req；NC load 在 needFlush 为假时可立即具备 canSendReq。这里的 ROB head 门是 load 侧的排序策略，不是物理 UncacheImp 的固有规则。[LoadQueueUncache.scala:68](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:68) [LoadQueueUncache.scala:122](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:122)

~~~scala
val canSendReq = req_valid && !needFlush && Mux(
  req.nc, true.B,
  pendingld && req.uop.robIdx === pendingPtr
)
~~~

逻辑 load 请求固定为 M_XRD，携带 paddr、vaddr、该逻辑 entryIndex 作为 mid、nc 与 memBackTypeMM。收到物理响应后，NC 回写走 ncOut；MMIO 回写走 mmioOut。denied 变为 loadAccessFault，corrupt 且非 denied 变为 hardwareError。[LoadQueueUncache.scala:173](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:173) [LoadQueueUncache.scala:188](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:188) [LoadQueueUncache.scala:205](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:205)

### 4.3 LSQWrapper：load/store 仲裁与回程路由

LSQWrapper 在 pendingstate=s_idle 时选择 load 或 store 请求。若二者都 valid，只有 load.robIdx 小于 store.robIdx 时 load 胜出；相等时表达式为假，store 获胜。非 outstanding 情况下，req.fire 后 pendingstate 记录 s_load 或 s_store，直到 resp.fire 才回 idle；outstanding 且 NC 的请求可以使状态保持 idle。[LSQWrapper.scala:265](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:265)

~~~scala
val selectLq = (loadQueue.io.uncache.req.valid &&
  !storeQueue.io.uncache.req.valid) || (
  loadQueue.io.uncache.req.valid &&
  storeQueue.io.uncache.req.valid &&
  loadQueue.io.uncache.req.bits.robIdx <
    storeQueue.io.uncache.req.bits.robIdx
)
~~~

响应和 idResp 不依据 pendingstate，而依据物理层回传的 is2lq 分流。因此物理条目响应到达后，可根据保存的 cmd 回到正确逻辑子系统。[LSQWrapper.scala:302](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:302) [LSQWrapper.scala:312](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:312)

MemBlock 在 LSQWrapper 与物理 UncacheImp 两端各插入一个 AddPipelineReg。这个结构有一个 valid 寄存器：out.fire 清 valid、in.fire 置 valid、isFlush 清 valid，in.ready 为 !valid 或 out.ready。因此它是可被 sink backpressure 的一项弹性寄存器，不应把它机械称作“固定额外一拍端到端延迟”。[MemCommon.scala:99](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemCommon.scala:99) [MemBlock.scala:1505](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1505)

## 5. 物理 UncacheImp 的条目状态与生命周期

### 5.1 条目内容与可发送谓词

每个物理条目存 req 的 cmd、物理/虚拟地址、data、mask、nc、memBackTypeMM，以及 D 响应的 nderr/denied/corrupt。读响应覆盖 data；写响应不覆盖 data。构造 UncacheWordResp 时，id 取 sid，is2lq 由 cmd 是否 M_XRD 决定，miss/replay/tag_error/error 固定为假。[Uncache.scala:57](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:57) [Uncache.scala:100](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:100) [Uncache.scala:114](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:114)

状态不是一个 enum，而是四个 bit。下图是使用这些谓词得到的生命周期；waitSame 是同一 8-byte word 已有先行请求时的阻塞标记，可与 valid 组合存在。

~~~mermaid
stateDiagram-v2
  [*] --&gt; Free
  Free --&gt; Ready: req.fire and allocate
  Ready --&gt; Ready: req.fire and merge
  Ready --&gt; WaitSame: earlier same-word entry sends A
  WaitSame --&gt; Ready: earlier same-word D fire
  Ready --&gt; Inflight: TileLink A fire
  Inflight --&gt; WaitReturn: TileLink D fire
  WaitReturn --&gt; Free: LSQ resp.fire
  WaitSame --&gt; Free: response path then LSQ resp.fire
~~~

| 谓词 | 精确定义 | 谁使用 | 意义 |
| --- | --- | --- | --- |
| isValid | valid | 分配、empty、前递 | 物理条目已占用 |
| can2Bus | valid 且非 inflight/waitSame/waitReturn | q0 发送仲裁 | 能生成 A 请求 |
| canMerge | valid 且非 inflight | e0 合并判定 | 尚未发出，可以合并 data/mask |
| can2Lsq | valid 且 waitReturn | r0 返回仲裁 | D 响应已接收，可返回上游 |
| isFwdOld | valid 且 inflight 或 waitReturn | store→load 前递 | 已经发出或响应待取的旧 store |
| isFwdNew | valid 且非 inflight/非 waitReturn/且 waitSame | store→load 前递 | 等待旧同 word store 的新 store |

以上定义和 D 处理的断言在 [Uncache.scala:136](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:136)。

~~~scala
def can2Bus() = valid && !inflight && !waitSame && !waitReturn
def can2Lsq() = valid && waitReturn
def canMerge() = valid && !inflight
def updateUncacheResp() = {
  assert(inflight)
  inflight := false.B
  waitReturn := true.B
}
~~~

### 5.2 复位、首请求与空态

states 以 RegInit 的全零状态复位，uState 复位为 s_idle，noPending 的每位复位为 true。entries 本身是 Reg(Vec(...))，没有用 RegInit 清零；正确性依赖 states.valid 为假时不读取其有效内容。首个合法 req.fire 会在空 slot 调用 entry.set 并置 valid，随后下一拍才以 Valid idResp 宣告 sid。验证不能要求 reset 后 entries 的 data/addr 为零。[Uncache.scala:241](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:241) [Uncache.scala:366](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:366)

empty 仅由所有 states.isValid 的 OR 反相得到。flush.empty 因而说明物理条目都已释放，而不是仅说明没有正在等待 TileLink D 的 transaction。[Uncache.scala:495](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:495)

## 6. e0：分配、合并、拒绝与 ID 回执

### 6.1 合并资格与冲突语义

对于每个物理条目，e0 同时计算 reject、merge、allocWaitSame 三个向量。primary merge 要求：

1. 请求 vaddr 与旧条目 vaddr 位于同一 8-byte word；
2. cmd 相同；
3. 新旧均为 nc；
4. memBackTypeMM 相同；
5. 两个 mask OR 后仍是连续自然对齐的合法大小；
6. 该条目本拍没有收到对应 D 响应，且旧条目不在 waitReturn。

secondary merge 还要求旧条目 canMerge，且本拍不是 q0 正在把它送至 A。若同 word 条目存在但不满足 primary，e0_rejectVec 使请求被拒绝，不会错误另开一个同 word 条目。[Uncache.scala:289](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:289) [Uncache.scala:299](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:299) [Uncache.scala:343](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:343)

~~~scala
e0_rejectVec(i) := valid && isAddrMatch && !canMerge1
e0_mergeVec(i) := valid && isAddrMatch && canMerge1 && canMerge2
e0_allocWaitSameVec(i) := valid && isAddrMatch &&
  canMerge1 && !canMerge2
assert(PopCount(e0_mergeVec) <= 1.U)
~~~

### 6.2 优先级、满态与 ready

空条目和可合并条目均用 PriorityEncoderWithFlag 选择。该 helper 递归优先选择输入 Seq 的 head，所以由 in.zipWithIndex 生成的向量中索引小的有效项优先；它不是 round-robin。实际 Uncache e0 没有公平轮转状态，因此持续向低编号可用项偏置是源码事实；是否会在系统级形成可观察饥饿还需要带持续流量的仿真证明。[PriorityMuxDefault.scala:38](/home/yanyusong/xs-memory-env/XiangShan/utility/src/main/scala/utility/PriorityMuxDefault.scala:38) [PriorityMuxDefault.scala:52](/home/yanyusong/xs-memory-env/XiangShan/utility/src/main/scala/utility/PriorityMuxDefault.scala:52) [Uncache.scala:357](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:357)

~~~scala
val (e0_mergeIdx, e0_canMerge) = PriorityEncoderWithFlag(e0_mergeVec)
val (e0_allocIdx, e0_canAlloc) = PriorityEncoderWithFlag(e0_invalidVec)
val e0_reject = do_uarch_drain ||
  (!e0_canMerge && !e0_invalidVec.asUInt.orR) ||
  e0_rejectVec.reduce(_ || _)
req_ready := !e0_reject
~~~

req 的真正接受事件是 req.fire，不是 req.valid。仅 valid 而 req_ready 为低时，物理条目、states 和 idResp 都不得变化。若无可合并项且无 invalid 条目，ready 低形成 backpressure；对 load 来说，上游 LoadQueueUncache 的容量不足则另有 rollback 路径，两种“满”不能混淆。[Uncache.scala:338](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:338) [LoadQueueUncache.scala:587](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:587)

### 6.3 同一 word 的发送顺序

当一个 q0 条目 A.fire 时，所有其他有效、未 waitReturn、且同一 8-byte word 的条目被标记 waitSame。D.fire 时，对同 word 的 waitSame 条目清位。这样，同 word 的后继条目不能并行到总线，而必须等待先行条目的 D 响应；它不是一般性全局顺序化。[Uncache.scala:433](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:433) [Uncache.scala:463](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:463)

## 7. q0/r0：TileLink A/D 与 LSQ 返回

### 7.1 q0 发送与命令构造

q0 从 can2Bus 条目中以低 sid 优先选择一个。enableOutstanding 为真时，可选择任意 can2Bus；为假时，只有 uState=s_idle 时能选择，构成一次仅允许一个 A→D→LSQ 完整事务的保守模式。注意该模式的 uState 只追踪非 outstanding 串行策略，不替代每个物理条目的 flags。[Uncache.scala:308](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:308) [Uncache.scala:395](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:395)

~~~scala
val q0_canSentVec = sizeMap(i =>
  (io.enableOutstanding || uState === s_idle) &&
  states(i).can2Bus()
)
mem_acquire.valid := q0_canSent && !io.wfi.wfiReq
mem_acquire.bits := Mux(q0_isStore, q0_store, q0_load)
~~~

对 load 使用 edge.Get，对 store 使用 edge.Put；fromSource 直接为 q0_canSentIdx，即 sid。A.fire 后置该状态 inflight，并把 noPending[sid] 置 false。mem_acquire.valid 会被 WFI 请求压低，故 WFI 的停顿发生在 A 接口，而不是通过删除条目实现。[Uncache.scala:413](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:413) [Uncache.scala:419](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:419) [Uncache.scala:429](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:429)

### 7.2 TileLink D 响应与一拍响应假设

D 通道始终 ready。D.fire 时以 mem_grant.bits.source 取回 sid，更新条目的 data/error 字段，把 inflight 清零、waitReturn 置位，并把 noPending[sid] 恢复为 true。代码要求 refill_done 为真，直接断言 Uncache 响应是一 beat；若下游协议产生多 beat，当前实现会触发断言而不是自行组装。[Uncache.scala:456](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:456)

~~~scala
mem_grant.ready := true.B
when (mem_grant.fire) {
  val id = mem_grant.bits.source
  entries(id).update(mem_grant.bits)
  states(id).updateUncacheResp()
  noPending(id) := true.B
  assert(refill_done)
}
~~~

仅当当前 sid 是 store 且 denied 或 corrupt，UncacheImp 才把 busError.ecc_error.valid 拉高。load 的 denied/corrupt 留在 UncacheWordResp，后由 LoadQueueUncache 映射为异常；不能把这个名为 ecc_error 的输出当作所有 load/store bus 错误的唯一通道。[Uncache.scala:477](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:477) [LoadQueueUncache.scala:238](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:238)

### 7.3 r0 返回与物理条目释放

r0 从 can2Lsq 条目中同样按低 sid 选择；resp.valid 在有 waitReturn 时为真，只有 resp.fire 才调用 updateReturn 清除 valid、inflight、waitSame、waitReturn。若 LSQ side 的 response pipe 或其消费者回压，条目保持 waitReturn 并占用物理容量，D 响应已到达也不会释放。[Uncache.scala:486](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:486)

~~~scala
resp.valid := r0_canSent
resp.bits := entries(r0_canSentIdx).toUncacheWordResp(r0_canSentIdx)
when(resp.fire) {
  states(r0_canSentIdx).updateReturn()
}
~~~

## 8. Store-to-load 前递与别名保护

UncacheImp 自己提供 LoadPipelineWidth 路 LoadForwardQueryIO。候选只包含有效的 store 条目；f0 用 vaddr 的 8-byte word 比较快速得到 mask，f1 寄存物理地址再验证 paddr 匹配。f1 把旧的 inflight/waitReturn store 与新的 waitSame store 的字节数据按 doMerge 合并；其目的在于同 word store 被顺序化时，load 可看见最新字节覆盖关系。[Uncache.scala:503](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:503) [Uncache.scala:524](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:524) [Uncache.scala:538](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:538)

~~~mermaid
flowchart LR
  Q[LoadForwardQueryIO valid vaddr] --&gt; F0[f0 vaddr word CAM]
  S1[valid store entries] --&gt; F0
  F0 --&gt;|old: inflight or waitReturn| O[old mask/data]
  F0 --&gt;|new: waitSame| N[new mask/data]
  O --&gt; M[byte-wise doMerge]
  N --&gt; M
  P[DTLB paddr] --&gt; F1[f1 paddr word CAM]
  F1 --&gt;|same match set| R[forwardData and forwardMask]
  F1 --&gt;|vaddr/paddr mismatch| D[set matchInvalid and request drain]
~~~

f1_tagMismatch 代表“f0 的虚拟 word 匹配集合”与“f1 的物理 word 匹配集合”不同。它使 forward.matchInvalid 有效，并与 nonempty 共同置 f1_needDrain；do_uarch_drain 随后使 e0 拒绝新请求，直到 empty。该 drain 是避免错误虚拟地址别名下使用错误前递候选的保护，而不是一次直接清空 buffer 的操作。[Uncache.scala:249](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:249) [Uncache.scala:251](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:251) [Uncache.scala:515](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:515) [Uncache.scala:550](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:550)

~~~scala
f1_needDrain := f1_tagMismatchVec.asUInt.orR && !empty
when((f1_needDrain || io.flush.valid) && !empty) {
  do_uarch_drain := true.B
}
forward.matchInvalid := f1_tagMismatchVec(i)
~~~

源码未在这个 Mux1H 前给出一个针对前递候选 one-hot 的显式 assert。e0 对 mergeVec 有 PopCount 断言，但它不是前递候选唯一性的证明。应在生成 RTL/仿真中检查同一 forward query 下 old/new 各自候选的选择约束与覆盖语义。

## 9. 时序、吞吐与 backpressure

### 9.1 有效握手边界

| 段 | 起点 | 结束 | 固定寄存器边界 | 可变因素 |
| --- | --- | --- | --- | --- |
| LSQ 到 UncacheImp 请求 | LSQ io.uncache.req.fire | UncacheImp req.fire | MemBlock 的 AddPipelineReg | 上游仲裁、pipeline register 占用、e0 req_ready |
| 物理接收至 ID 回执 | UncacheImp req.fire | idResp.valid | RegNext(e0_fire) | idResp 没有 ready；下游必须采样 valid |
| A 请求 | 条目 can2Bus | mem_acquire.fire | 无固定等待拍承诺 | q0 优先级、enableOutstanding、uState、WFI、A.ready |
| 总线等待 | A.fire | D.fire | 无 | 下游 TL 响应时间；代码要求一 beat D |
| D 到 LSQ response | D.fire | LSQ resp.fire | MemBlock response AddPipelineReg | r0 优先级、下游 ready |
| 逻辑 load 回写 | 逻辑 entry 收到 resp.fire | mmioOut.fire 或 ncOut.fire | AddPipelineReg | LDU 回写端 backpressure、redirect |

由于 q0/r0 选项均为 PriorityEncoderWithFlag，物理层没有 round-robin，故最佳情况下的每拍发出率不能替代公平性保证。默认 outstanding=false 时，uState 强制 A.fire→D.fire→resp.fire 的闭环后才允许下一 q0，故最佳稳态请求启动间隔至少受这三个事件的完成序列约束；总线 D 的实际延迟不在源码中固定。[Uncache.scala:308](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:308) [Uncache.scala:395](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:395)

### 9.2 两个关键时序示意

#### 正常 NC/MMIO 物理事务

~~~waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p......" },
    { "name": "lsq.req.valid", "wave": "01....." },
    { "name": "lsq.req.ready", "wave": "01....." },
    { "name": "req.fire/e0_fire", "wave": "010...." },
    { "name": "idResp.valid", "wave": "0010..." },
    { "name": "tl.a.valid", "wave": "0001..." },
    { "name": "tl.a.ready", "wave": "1111111" },
    { "name": "tl.a.fire", "wave": "00010.." },
    { "name": "state.inflight", "wave": "0001110" },
    { "name": "tl.d.valid", "wave": "0000010" },
    { "name": "tl.d.ready", "wave": "1111111" },
    { "name": "lsq.resp.valid", "wave": "0000001" }
  ],
  "head": { "text": "示意：A/D 之间的延迟可变；idResp 是 e0_fire 的下一拍 Valid" }
}
~~~

#### non-outstanding 模式下的串行门控

~~~waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p........" },
    { "name": "enableOutstanding", "wave": "0........" },
    { "name": "entry0 can2Bus", "wave": "01......." },
    { "name": "entry1 can2Bus", "wave": "001......." },
    { "name": "uState idle", "wave": "1......11" },
    { "name": "A(entry0).fire", "wave": "010......" },
    { "name": "D(entry0).fire", "wave": "00010...." },
    { "name": "LSQ resp(entry0).fire", "wave": "0000010.." },
    { "name": "A(entry1).fire", "wave": "000000010" }
  ],
  "head": { "text": "示意：entry1 即使 ready，也需等 uState 回到 idle 后才可 A.fire" }
}
~~~

第一图没有标出“D 必在某一固定拍到达”；第二图说明的是门控因果关系，不表示实际 D 延迟为两拍。

## 10. 异常、WFI、flush、提交可见性与 Difftest

### 10.1 错误的传播层次

| 来源 | 物理 UncacheImp | 逻辑/架构侧后续 |
| --- | --- | --- |
| TileLink D.denied | 记录 resp_denied，回传 UncacheWordResp.denied | LoadQueueUncache 置 loadAccessFault；StoreQueue MMIO 置 storeAccessFault |
| TileLink D.corrupt | 记录 resp_corrupt | 非 denied 时 load 置 hardwareError；store 同理 |
| store 的 denied/corrupt | 同时置 busError.ecc_error | 该输出只覆盖 store 这个条件 |
| 地址/翻译/PMP 等 | 上游 LoadUnit/StoreUnit 已分类、可能异常并 kill DCache | 不等同于总线 D 错误，也不应送入 Uncache 之后再重判 |

LoadQueueUncache 的异常输出在 writeback 时有效，接到 LoadQueue 的 exceptionBuffer。这样 denied/corrupt 的架构异常不会仅停留在物理 buffer 寄存器中。[LoadQueueUncache.scala:238](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:238) [LoadQueue.scala:284](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala:284)

### 10.2 WFI 与 flush

noPending 只在 A.fire 时对应 sid 置 false、D.fire 置 true；wfiSafe 要求 noPending 全真且 wfiReq 为真，并经过 GatedValidRegNext。它证明的是“没有物理总线在途 D 等待”，不证明所有 waitReturn 或 waitSame 条目都已从 buffer 释放。MemBlock 的最终 wfiSafe 还要 AND DCache、LSQ、PTW 的 wfiSafe，因此不能单独把 Uncache wfiSafe 当作整个核心可 WFI 的结论。[Uncache.scala:433](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:433) [Uncache.scala:481](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:481) [MemBlock.scala:678](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:678)

flush.valid 从 SBuffer flush 链路送入 Uncache，empty 则与 SBuffer empty 一起构成 sbIsEmpty。物理 Uncache 接收到 flush 时只通过 do_uarch_drain 阻止新接收并等待 empty；源码并没有在 flush.valid 时直接遍历清除 entries/states。对已经发出的有副作用 MMIO，正确语义是 drain，而不是投机丢弃。[MemBlock.scala:1768](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1768) [MemBlock.scala:1778](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1778) [Uncache.scala:247](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:247)

LoadQueueUncache 的 redirect 处理更细：idle/request 可直接 flush；若已在 s_resp，则 needFlushReg 把 flush 延迟到响应到达；s_wait 则在 needFlush 或 writeback 时回 idle。它不会在一个已在物理层的 load 响应回来前悄悄复用逻辑条目。[LoadQueueUncache.scala:78](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:78) [LoadQueueUncache.scala:128](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:128)

### 10.3 MMIO 排序与提交

ROB 从 LoadQueue/StoreQueue 的通知写入 robEntries(...).mmio；随后 pendingMMIOld、pendingld、pendingst、pendingPtr 从提交头产生。LoadQueueUncache 的普通 MMIO load 通过 pendingld/pendingPtr 门控；StoreQueue 的 MMIO 状态在 pendingst、当前 deqPtr、allocated、datavalid、addrvalid、无异常等条件后才进入 s_req。由此能确认 MMIO 的执行不是普通 cacheable load 的自由投机路径。[Rob.scala:556](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:556) [Rob.scala:838](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:838) [LoadQueueUncache.scala:122](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:122) [StoreQueue.scala:845](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:845)

NC store 的路径不同：它要求已 committed、已分配、未完成、地址/数据全部有效、非 vector、无异常且非 MMIO；在收到 idResp 的 slave ack 后，如果 outstanding 开启则可以回 nc_idle，否则等待响应。该 ack 很关键，因为源码注释说明 NC store 需要先确保 Uncache buffer 已接收，以便 store-data forwarding 可用。[StoreQueue.scala:917](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:917) [StoreQueue.scala:929](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:929) [StoreQueue.scala:939](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:939)

### 10.4 outstanding 控制与 Difftest

CSR smblockctl bit 7 是 uncache_write_outstanding_enable。它驱动 MemBlock 的 UncacheImp enableOutstanding 与 LSQWrapper 的 uncacheOutstanding；复位值来自 EnableUncacheWriteOutstanding，默认参数是 false。软件改变该 CSR 后，物理 q0 门控、LSQ pendingstate 和 NC store 行为会同时改变，验证必须覆盖两种设置。[CSR.scala:538](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/CSR.scala:538) [CSR.scala:554](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/CSR.scala:554) [MemBlock.scala:1398](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1398)

UncacheImp 在 env.EnableDifftest 时只为 A.fire 的 store 且 memBackTypeMM 为真发送 DiffUncacheMMStoreEvent。这里是物理存储写事件的检查锚点；此文件没有同等的 generic uncache load difftest event，不能从这一段推断 load 的全部架构提交比较机制。[Uncache.scala:445](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:445)

## 11. 跨边界代码解析

| 边界 | 第一片段/阶段 | 第二片段/阶段 | 独立检查 | 合并或排序状态 | 故障与恢复 |
| --- | --- | --- | --- | --- | --- |
| 虚拟页 | Load/Store Unit 先有 vaddr、TLB/PMP/PBMT 结果 | 物理分类后生成 paddr 请求 | TLB miss、PMP、PBMT/PMA/MMIO 与已有异常 | UncacheEntry 同时保存 vaddr 用于前递、paddr 用于 TL | redirect 在 LoadQueueUncache 记录 needFlush；前递 vaddr/paddr 集合不一致则 drain |
| 8-byte word | 低地址 byte mask | 同 word 的后继/合并 mask | 同 word、cmd、nc、memBackTypeMM、合法 mask | doMerge 覆盖字节；waitSame 保证先行 D 后发送后继 | 非法或不可合并同 word 使 e0 ready 低；不是自动拆包 |
| cache line | 未走 DCache tag/data/miss/refill 主路径 | 无第二 cache line 事务 | N/A：Uncache 内的 getBlockAddr 为 8-byte word | 没有 cache line assembler 或 MSHR | 不应把 DCache line crossing 行为投射到本模块 |
| MMIO/NC | LoadUnit/StoreUnit 判定 mmio/nc | LoadQueueUncache 或 StoreQueue 构造 req | load 的 ROB head 门；store 提交/地址/数据条件 | mid→sid 映射，sid 作 TL source；LSQ 以 is2lq 回程路由 | denied/corrupt 转异常；flush/drain；逻辑 buffer 满产生 rollback |
| 非对齐且 uncache | LoadMisalignBuffer 收到 split 子访问的 mmio/nc 标志 | 它不把多个 fragment 送物理 Uncache 并拼接 | globalUncache 捕获 | 直接写回异常语义，不做 uncache fragment assembly | 地址未对齐异常路径，非“多个 MMIO 可随意拆分” |

最后一行有直接代码依据：LoadMisalignBuffer 在任一 split load 被标为 uncache 时进入 s_wb，注释说明交给软件 loadAddrMisaligned；writeback 也令 rfWen 为假。文件虽保留跨页异常地址覆写寄存器，但 overwriteExpBuf.valid 被硬连为 false，因而不能把该保留逻辑当作当前启用的跨页异常覆写功能。[LoadMisalignBuffer.scala:183](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:183) [LoadMisalignBuffer.scala:213](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:213) [LoadMisalignBuffer.scala:561](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:561) [LoadMisalignBuffer.scala:641](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:641)

组合场景应按这个顺序核对：先 per-fragment 虚拟地址与翻译/权限，再确定 PMA/PBMT/MMIO/NC，随后决定是否允许进入 Uncache；若是 split uncache load，当前 LoadMisalignBuffer 的结论是异常，而非进入物理 UncacheImp 的多片段汇合。

## 12. 验证特别注意

| Verification ID | 风险/不变量 | 定向激励 | 预期观察 | 必需 checker/coverage |
| --- | --- | --- | --- | --- |
| UB_RESET_FIRST | states 全零但 entries 未初始化；首 req 不得读到旧 payload | reset 后发首个合法 M_XRD，再发首个 M_XWR | 仅 req.fire 的 sid 置 valid；idResp.valid 为下一拍；未 valid 条目不影响 q0/r0 | FSM checker、occupancy checker；覆盖 e0_alloc_simple |
| UB_HOLD_REQ | req.valid 而 e0_reject 时不得误接收 | 填满物理 16 条，或制造同 word 不可合并项；保持 req.valid | req.ready=0，e0_fire=0，entries/states/idResp 不变 | handshake checker、storage conflict checker |
| UB_MERGE_MASK | 合并不得丢字节或生成非法 lgSize | 同 word、同 cmd、同 nc、同 memBackTypeMM 的部分字节 store；覆盖 1/2/4/8 byte mask | data 按 newMask 覆盖；mask OR；addr 对齐最低置位；A 的 lgSize=PopCount 对应值 | data scoreboard、assertion coverage；覆盖 e0_merge |
| UB_SAMEWORD_ORDER | 同 word 后继不得 A.fire 早于先行 D.fire | 先发一条 store，再在其 inflight 时放入相同 word 可合并/后继条目 | 后继 waitSame=1；先行 D.fire 后清 waitSame；再允许后继 A.fire | FSM checker、ordering scoreboard、forward-progress checker |
| UB_A_D_SID | TL source 与 D source 必须精确指向同 sid | 多个不同 word 并发，outstanding=1，令 D 乱序返回 | A.fromSource=sid；D.source 更新相同 entry；单 beat断言满足；每 sid 一次 waitReturn | ID scoreboard、TL protocol checker、C_SAME_ENTRY_RW |
| UB_RESP_BACKPRESSURE | D 到 LSQ 的回压不得提前释放 | 让 r0 的下游 ready 低并让多个 D 返回 | waitReturn 条目保持 valid；只有 resp.fire 清条目；无重复 resp | handshake checker、occupancy checker、PB_BACKPRESSURE_AMPLIFICATION |
| UB_FWD_ALIAS_DRAIN | vaddr 快速 CAM 与 paddr CAM 不一致必须禁止错误前递并 drain | 构造 f0 虚拟同 word、f1 物理不匹配的 store/load 查询，buffer 非空 | matchInvalid=1；f1_needDrain=1；do_uarch_drain 后新 e0 请求被拒绝直至 empty | alias checker、flush/drain checker、P_DEADLOCK_ALL_STALL |
| UB_FLUSH_INFLIGHT | flush 不是清表；在途副作用不得丢失 | A.fire 后、D 前置 flush.valid；并让上游继续 req.valid | do_uarch_drain 阻止新接收；在途 D 后正常进入 r0；empty 之后 drain 清除 | flush/replay checker、forward-progress checker |
| UB_WFI | wfiSafe 只说明 noPending 全真，不能等价 buffer empty | 制造 waitReturn 条目且 D 已到，然后拉 wfiReq | 记录 noPending 和 states.valid 的差异；系统 wfiSafe 还需 DCache/LSQ/PTW 条件 | WFI property、cross-module scoreboard |
| LQU_MMIO_HEAD | MMIO load 不能在未到 ROB head 时出物理 req | 两个 MMIO load，年轻条目先就绪；改变 pendingPtr | 仅 robIdx==pendingPtr 的逻辑条目进 s_req；NC 同场景可不同 | ROB-order checker、arbiter checker |
| LQU_FULL_ROLLBACK | 逻辑 load buffer 满的恢复必须选择最老无效入队 load | 填满 LoadUncacheBufferSize，连续投递多 lane mmio/nc load | reqNeedCheck 产生；按 ROB age 选最老 redirect，且被已有 redirect flush 的请求不输出 rollback | rollback checker、age scoreboard、PB_RECOVERY_THROUGHPUT |
| UB_OUTSTANDING_MODE | CSR bit 7 改变串行化与响应路由时机 | 覆盖 smblockctl bit7=0/1、NC store、多个不同 word | 0 时 uState 闭环串行；1 时可多 A 在途，LSQWrapper 对 NC 可保持 idle | configuration coverage、throughput checker、ID scoreboard |

上述每项的实现锚点分别位于 [Uncache.scala:338](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:338)、[Uncache.scala:395](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:395)、[Uncache.scala:456](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:456)、[Uncache.scala:503](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:503)、[LoadQueueUncache.scala:552](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:552) 与 [CSR.scala:568](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/CSR.scala:568)。

## 13. 两条完整操作路径

### 13.1 普通 NC load，物理响应成功

1. LoadUnit 在 s2 完成相关地址属性检查，得到 s2_uncache，抑制 DCache 响应期待与 DCache s2 通路。
2. LoadQueueUncache 在 s2 接收该 mmio/nc load；若无异常/replay/redirect，分配逻辑 entry。NC 请求无需等待 pendingPtr。
3. LSQWrapper 与 StoreQueue 请求仲裁后，将 req 送入 MemBlock 请求弹性寄存器。
4. UncacheImp 的 e0 在 req.fire 时分配或合并物理 sid；下一拍 Valid idResp 让逻辑 entry 记住 slaveId=sid。
5. q0 选中 can2Bus 条目，A.fire 后它成为 inflight；若同一 8-byte word 的其他条目存在，将其 waitSame。
6. D.fire 以 source=sid 回写 data/error，entry 变 waitReturn，并清同 word 后继的 waitSame。
7. r0 的 resp.fire 才释放物理 sid。LSQWrapper 用 is2lq 将响应交给 LoadQueueUncache，后者进入 s_wait，向 ncOut 回写 data 与可能的异常。
8. LoadQueueUncache 的 ncOut.fire 后释放其逻辑 entry。物理 sid 和逻辑 mid 的释放事件可能不在同一周期。

### 13.2 同 word store 前递别名保护与 drain

1. 一个 NC store 已被物理 UncacheImp 接受，因此可成为有效前递候选；它可能正在 inflight 或 waitReturn。
2. LoadForwardQueryIO 在 f0 用虚拟地址 word CAM 快速取得候选 mask。
3. f1 得到 DTLB paddr 后用物理地址 CAM 复核；若候选集合和 f0 不同，matchInvalid 拉高。
4. f1_needDrain 使 do_uarch_drain 在非空时拉高，e0 请求接收被拒绝，已有条目继续按 A/D/r0 排空。
5. empty 后 do_uarch_drain 清低；这时才重新允许新 req。这个过程保护前递正确性，但源码没有给出该 drain 的最大周期数，因为它受总线 D 与 r0 消费者 ready 影响。

## 14. 总结与待验证问题

已确认的有效实现行为：

- UncacheImp 是独立的 16-entry TileLink client，路径经 d_mmio_port，而非 L1D main pipe。
- 物理层用 sid 同时作为 TileLink source 与 UncacheWordResp.id；load 逻辑层用 mid 保存更高层的 uop/ROB/异常状态，并由 idResp 建立 mid→sid。
- 合并和同址顺序化的地址粒度是 8-byte XLEN word。mask 合并有明确的连续对齐约束；同 word 后继用 waitSame 等待先行 D。
- 默认 outstanding=false，运行时由 smblockctl bit 7 控制；该位会同时影响物理 q0、LSQWrapper 和 NC store 行为。
- flush 是 drain，不是清空在途事务；WFI 的 noPending 条件不等价于物理 buffer empty。
- load 的 denied/corrupt 在 LoadQueueUncache 映射为 loadAccessFault/hardwareError；store 的相应错误还会触发物理 busError 输出。

仍需用 elaboration、生成 RTL 或波形验证的问题：

1. 前递 Mux1H 的候选唯一性在源级未看到专门 assert；应构造多条同 word store 的候选覆盖。
2. 低 sid 固定优先级已由 helper 证实，但在持续输入/response backpressure 下的系统级饥饿边界需要动态证明。
3. AddPipelineReg 与跨层 ready 组合形成的精确最小/最大周期数需要波形测量；静态源码只证明寄存器边界和阻塞条件。
4. TileLink 下游对 denied/corrupt 与 source ID 的实际产生时机、D 响应乱序范围属于本模块边界外，需要在 SoC 集成仿真中核对。
<!-- END ORIGINAL CHINESE -->

# XiangShan Kunminghu V2: UncacheBuffer Source-Code Analysis

> **Conclusion first:** data-side uncached accesses in Kunminghu V2 do not use the L1 DCache main data path. The physical UncacheBuffer is <code>UncacheImp</code> in [Uncache.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:207). It keeps one XLEN-wide request from the LSQ in a physical entry, sends it to the data-MMIO port using its physical-entry number as the TileLink source ID, and returns it to the LSQ after the TileLink D response. <code>LoadQueueUncache</code> is an upstream logical load buffer. The two connect through an acknowledgement mapping from <code>mid</code> (logical entry ID) to <code>sid</code> (physical UncacheBuffer entry ID); they must not be conflated as one queue.
>
> This document reports only behavior supported by static source inspection. No RTL was generated and no simulation or FST was run. WaveDrom diagrams therefore depict valid/ready/register relationships inferred from Chisel, not measured waveforms.

## 1. Scope, Version, and Evidence Boundary

| Item | Scope used here |
|---|---|
| Primary object | Physical UncacheBuffer: <code>Uncache</code> and <code>UncacheImp</code> |
| Main source | [Uncache.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:32>) |
| Upstream context | MemBlock, LSQWrapper, LoadQueueUncache, StoreQueue, LoadUnit, StoreUnit, and ROB |
| Downstream boundary | Uncache TileLink A/D, ending at the XSTile data-MMIO port. The L2/device-internal implementation is not inferred. |
| Source tree | <code>/home/yanyusong/xs-memory-env/XiangShan</code> |
| Source baseline | Branch <code>kunminghu-v2</code>, commit <code>e12436c7cba86b195deec24981976d78bc263661</code> |
| Active configuration | KunminghuV2Config inherits DefaultConfig; no override of UncacheBufferSize or LoadUncacheBufferSize was found. |
| Design-document baseline | Not consulted. <code>/home/yanyusong/XiangShan-Design-Doc</code> does not exist locally, so design-document descriptions are not implementation evidence. |
| Theory material | Course [14_LoadStore.md](</home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md:311>) explains LSQ submodule placement; behavioral conclusions return to this source baseline. |
| Worktree note | The source tree already had changes under <code>difftest</code> and untracked <code>src/main/resources/aia/</code> content. This analysis did not alter it. |
| Weekly synchronization check | The skill's weekly synchronization found less than seven days since the previous run, skipped synchronization, and performed no fetch, pull, or destructive action. |
| Waveform boundary | No FST was available for comparison. Cycle counts describe register boundaries and variable handshakes only, never fixed bus round-trip latency. |

### 1.1 Design-Document and Code Traceability

| ID | Design claim | Design-document evidence | Source evidence | Conclusion |
|---|---|---|---|---|
| D0 | Overall Uncache intent | Local checkout missing; not consulted | Not applicable | Implementation is not inferred from design documentation. |
| C1 | The physical buffer is an independent TileLink client | Not applicable | [Uncache.scala:191](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:191>) | Code confirmed |
| C2 | Data-MMIO and L1D-to-L2 are different connections | Not applicable | [MemBlock.scala:261](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:261>), [XSTile.scala:65](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/XSTile.scala:65>), [XSTile.scala:94](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/XSTile.scala:94>) | Code confirmed |
| C3 | Logical load entries and physical entries use two IDs | Not applicable | [DCacheWrapper.scala:535](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:535>), [LoadQueueUncache.scala:466](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:466>) | Code confirmed |

No local Design Doc was available for comparison, so none of the claims below is described as design-document verified. Functional statements are grounded in the named Chisel sources.

## 2. Distinguish Three Similarly Named Layers First

### 2.1 Physical UncacheBuffer, Logical LoadQueueUncache, and StoreQueue Are Different Structures

| Layer | Instance/file | Primary retained state | Purpose | Release condition |
|---|---|---|---|---|
| Physical transport layer | <code>UncacheImp</code>, Uncache.scala | XLEN data, mask, paddr/vaddr, TL response, valid/inflight/waitSame/waitReturn | Merge same-XLEN-word NC requests, issue TileLink, and return one physical sid | LSQ accepts <code>UncacheWordResp</code>, namely <code>resp.fire</code> |
| Load-semantics layer | <code>LoadQueueUncache</code>, LoadQueueUncache.scala | Logical load uop, ROB relation, exceptions, slaveId, s_idle/s_req/s_resp/s_wait | Send MMIO only at the ROB head; return NC-load data/exceptions to LDU | <code>mmioOut.fire</code>, <code>ncOut.fire</code>, or redirect flush |
| Store-semantics layer | <code>StoreQueue</code>, StoreQueue.scala | Allocated stores, committed/address/data-valid state, MMIO/NC state | Send an MMIO/NC store only after the code-specified commit conditions | MMIO completes and commits, or the SQ lifetime ends after an NC response |

At the top level, <code>LoadQueue</code> explicitly instantiates <code>LoadQueueUncache</code>; it does not connect every LoadUnit directly to physical UncacheImp. The subsequent connection attaches the uncache interface to the LSQ boundary. [LoadQueue.scala:214](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala:214>)

~~~scala
val uncacheBuffer = Module(new LoadQueueUncache)
uncacheBuffer.io.uncache <> io.uncache
io.nack_rollback(0) := uncacheBuffer.io.rollback
~~~

The physical module instead uses <code>UncacheBufferSize</code> Reg entries and an independent state vector, not the LoadQueueUncache state machine as storage state. [Uncache.scala:241](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:241>)

~~~scala
val entries = Reg(Vec(UncacheBufferSize, new UncacheEntry))
val states = RegInit(VecInit(Seq.fill(UncacheBufferSize)(
  0.U.asTypeOf(new UncacheEntryState))))
~~~

Accordingly, UncacheBuffer in this document means physical <code>UncacheImp</code> unless a discussion explicitly says <code>LoadQueueUncache</code> for load precise-retirement or exception semantics.

### 2.2 End-to-End Topology and Responsibilities

~~~mermaid
flowchart LR
  LDU[LoadUnit] -->|classified mmio/nc load| LQU[LoadQueueUncache]
  SQ[StoreQueue] -->|committed/eligible mmio/nc store| LSQ[LSQWrapper]
  LQU --> LSQ
  LSQ -->|UncacheWordReq: mid| MB[MemBlock pipeline register]
  MB -->|UncacheWordReq| UB[UncacheImp physical entries]
  UB -->|TileLink A: source=sid| DMMIO[data-MMIO TL port]
  DMMIO -->|TileLink D: source=sid| UB
  UB -->|UncacheWordResp: id=sid| MB
  MB --> LSQ
  LSQ -->|is2lq route| LQU
  LQU -->|mmioOut or ncOut| LDU
  LSQ -->|store response| SQ
  UB -->|store-data forwarding| LDU
~~~

L1D is deliberately absent from this Uncache main path. MemBlock instantiates DCacheWrapper and Uncache separately; the Uncache client goes through <code>uncache_xbar</code> and TLBuffer to <code>uncache_port</code>, and XSTile connects that port to <code>d_mmio_port</code>. The L1D client instead connects to <code>l1d_to_l2_buffer</code>. [MemBlock.scala:257](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:257>) [MemBlock.scala:286](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:286>) [XSTile.scala:65](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/XSTile.scala:65>) [XSTile.scala:99](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/XSTile.scala:99>)

~~~scala
uncache_xbar := TLBuffer() := uncache.clientNode
uncache_port := TLBuffer.chainNode(2) := uncache_xbar
l2top.inner.d_mmio_port := memBlock.uncache_port
~~~

### 2.3 Main-Module Contract: Who, Why, How, From, and To

| Object | Who | Why | How | From | To |
|---|---|---|---|---|---|
| LoadUnit / StoreUnit classification point | Memory-execution pipelines produce and update classification | Separates cacheable, MMIO, PBMT NC, permission, and misalignment cases | S2 TLB/PMP/PBMT conditions and kill signals | Translated address, PMP, and uop NC/MMIO/exception fields | DCache or the subsequent LSQ/LoadQueueUncache path |
| LoadQueueUncache entry | Owned and released by LoadQueue | Retains a load's ROB, writeback, and exception semantics; a pure bus slot cannot do this | FreeList allocation; s_idle/s_req/s_resp/s_wait; needFlushReg | S3 LqWriteBundle, ROB-pending information, and idResp | UncacheWordReq, ncOut/mmioOut, exception, rollback |
| LSQWrapper uncache arbiter | LSQWrapper selects and routes returns | Coordinates load/store sharing of the physical Uncache interface | s_idle/s_load/s_store, ROB-age comparison, is2lq route | LoadQueue/StoreQueue requests and Uncache resp/idResp | MemBlock uncache request/response endpoints |
| UncacheImp entry | Updated and physically owned by UncacheImp | Separates a sendable bus transaction from occupancy before return; supports same-word merge/order | e0 allocation/merge, q0 A issue, D update, r0 release | LSQ UncacheWordReq and TileLink D | TileLink A, LSQ UncacheWordResp, and Load forwarding |
| Data-MMIO TileLink endpoint | Connected by XSTile | Hands non-cacheable data access to external MMIO/uncache fabric | sid as source with A/D valid-ready | Uncache clientNode | d_mmio_port; analysis ends here |

## 3. Parameters, Address Granularity, and Interface Contract

### 3.1 Effective Capacity and ID Width

| Parameter/quantity | Effective value | Source | Effect |
|---|---:|---|---|
| XLEN | 64 | [Configs.scala:40](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:40>) | One physical entry retains 64-bit data with an 8-bit mask. |
| UncacheBufferSize | 16 | [Parameters.scala:236](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:236>) | Sixteen physical entries; TileLink source-ID range is [0,16). |
| UncacheBufferIndexWidth | 4 | <code>log2Up(16)</code> | Width of sid and <code>UncacheWordResp.id</code>. |
| LoadUncacheBufferSize | 16 | [Parameters.scala:172](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:172>) | Number of logical LoadQueueUncache entries; it is not the source definition of physical-entry count. |
| LoadPipelineWidth | 3 | [Parameters.scala:214](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:214>) | LoadQueueUncache observes up to three load requests in one cycle and provides the associated writeback-port structure. |
| Default outstanding setting | false | [Parameters.scala:243](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:243>) | Reset value of smblockctl bit 7 is 0 unless software changes the CSR. |

KunminghuV2Config overlays L2 settings and a CHI switch on DefaultConfig but does not override the two Uncache sizes above, so XSCoreParameters defaults apply. [Configs.scala:460](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:460>) [Configs.scala:481](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:481>)

### 3.2 Block Here Does Not Mean a Cache Line

In Uncache.scala, <code>BLOCK_OFFSET</code> is <code>log2Up(XLEN / 8)</code>. With XLEN=64 it is 3, so <code>getBlockAddr</code> removes the low three address bits; every <code>addrMatch</code> compares that result. Therefore, the same block in merging, <code>waitSame</code>, and the forwarding CAM is one 8-byte XLEN word, not a 64-byte DCache line. [Uncache.scala:32](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:32>) [Uncache.scala:269](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:269>)

~~~scala
def BLOCK_OFFSET = log2Up(XLEN / 8)
def getBlockAddr(x: UInt) = x >> BLOCK_OFFSET
def addrMatch(x: UncacheEntry, y: UncacheWordReq) =
  getBlockAddr(x.addr) === getBlockAddr(y.addr)
~~~

Entry merging is bytewise overwrite: every byte selected by the new mask replaces old data, masks are ORed, and the merged address/vaddr are realigned to the least-significant set byte of the result mask. A sendable mask must be a nonzero, contiguous, naturally aligned 1/2/4/8-byte region; issue uses <code>PopCount(mask)</code> to form TileLink <code>lgSize</code>. Any parameter or mask-generation change must revalidate both constraints. [Uncache.scala:34](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:34>) [Uncache.scala:88](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:88>) [Uncache.scala:276](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:276>) [Uncache.scala:404](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:404>)

### 3.3 The Two ID Domains of UncacheWordIO

| Field | Producer | Consumer | Meaning |
|---|---|---|---|
| UncacheWordReq.id | LoadQueueUncache or StoreQueue | Read when UncacheImp accepts it | Upstream logical-owner <code>mid</code>; for a load it is the LoadQueueUncache entryIndex. |
| UncacheIdResp.mid | UncacheImp | Received by LoadQueueUncache/StoreQueue after LSQWrapper routing | The upstream logical ID echoed unchanged. |
| UncacheIdResp.sid | UncacheImp | Upstream logical entry | Allocated or merged physical UncacheImp slot. |
| UncacheWordResp.id | UncacheImp | LSQWrapper and then upstream logical entry | Physical <code>sid</code>, not original mid. |
| UncacheWordResp.is2lq | UncacheImp | LSQWrapper | True for <code>M_XRD</code>, choosing return to the load or store side. |
| UncacheWordResp.nc | UncacheImp | StoreQueue and related consumers | Preserves NC/MMIO classification. |

Bundle definitions directly show that req.id has <code>uncacheIdxBits</code> width, whereas idResp.mid, sid, and resp.id belong to distinct ID domains. [DCacheWrapper.scala:535](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:535>) [DCacheWrapper.scala:556](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:556>) [DCacheWrapper.scala:563](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:563>)

~~~scala
val id   = UInt(uncacheIdxBits.W)
val mid = UInt(uncacheIdxBits.W)
val sid = UInt(UncacheBufferIndexWidth.W)
val id  = UInt(UncacheBufferIndexWidth.W)
~~~

The physical layer produces a Valid-form <code>idResp</code> in the cycle after <code>req.fire</code>. It has no ready, so a consumer must sample mid/sid when valid; an idResp backpressure channel must not be invented. [Uncache.scala:376](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:376>)

~~~scala
io.lsq.idResp.valid := RegNext(e0_fire)
io.lsq.idResp.bits.mid := RegEnable(e0_req.id, e0_fire)
io.lsq.idResp.bits.sid := RegEnable(e0_sid, e0_fire)
~~~

One physical merge need not map one logical mid to one sid. Every upstream request accepted by <code>req.fire</code> receives its own idResp; requests merged into one physical slot receive the same sid. LoadQueueUncache first sends idResp to the logical entry selected by mid, then delivers one physical response to all logical entries where <code>slaveId==resp.id</code>. Response wiring is conditional sid broadcast, not return to one permanently selected load entry. [LoadQueueUncache.scala:466](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:466>) [LoadQueueUncache.scala:471](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:471>)

~~~scala
when(i.U === io.uncache.idResp.bits.mid) {
  e.io.uncache.idResp <> io.uncache.idResp
}
when(e.io.slaveId.valid &&
  e.io.slaveId.bits === io.uncache.resp.bits.id) {
  e.io.uncache.resp <> io.uncache.resp
}
~~~
