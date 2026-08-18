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
  LDU[LoadUnit] -->|分类后的 mmio/nc load| LQU[LoadQueueUncache]
  SQ[StoreQueue] -->|提交/条件满足的 mmio/nc store| LSQ[LSQWrapper]
  LQU --> LSQ
  LSQ -->|UncacheWordReq: mid| MB[MemBlock pipe register]
  MB -->|UncacheWordReq| UB[UncacheImp physical entries]
  UB -->|TileLink A: source=sid| DMMIO[data-MMIO TL port]
  DMMIO -->|TileLink D: source=sid| UB
  UB -->|UncacheWordResp: id=sid| MB
  MB --> LSQ
  LSQ -->|is2lq route| LQU
  LQU -->|mmioOut or ncOut| LDU
  LSQ -->|store response| SQ
  UB -->|store data forwarding| LDU
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
  [*] --> Free
  Free --> Ready: req.fire and allocate
  Ready --> Ready: req.fire and merge
  Ready --> WaitSame: earlier same-word entry sends A
  WaitSame --> Ready: earlier same-word D fire
  Ready --> Inflight: TileLink A fire
  Inflight --> WaitReturn: TileLink D fire
  WaitReturn --> Free: LSQ resp.fire
  WaitSame --> Free: response path then LSQ resp.fire
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
  Q[LoadForwardQueryIO valid vaddr] --> F0[f0 vaddr word CAM]
  S1[valid store entries] --> F0
  F0 -->|old: inflight or waitReturn| O[old mask/data]
  F0 -->|new: waitSame| N[new mask/data]
  O --> M[byte-wise doMerge]
  N --> M
  P[DTLB paddr] --> F1[f1 paddr word CAM]
  F1 -->|same match set| R[forwardData and forwardMask]
  F1 -->|vaddr/paddr mismatch| D[set matchInvalid and request drain]
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
