# 15. XSCache：昆明湖 V3 缓存实现与原理

本文逐题回答《缓存理解要点_G6FAsVr79K66FT33/index.md》。涉及实现的结论均以本工作区的源码为准：XiangShan 主仓库 `022cee074`（分支 `kunminghu-v3`），CoupledL2 子仓 `82079f40`，HuanCun 子仓 `90aaf59`。行号按当前工作区 `nl -ba` 输出；代码片段保留了决定行为的 RTL，省略与问题无关的字段连接。

## 15.1 概念部分

### 15.1.1 什么是组相连、全相连、直接相连

一条地址先由 `tag/set/offset` 拆分。直接相连（direct mapped）每个 Set 只有一个 Way，地址只能落到该位置；组相连（N-way set associative）每个 Set 有 N 个 Way，在同一个 Set 内并行比较 N 个 Tag；全相连（fully associative）只有一个逻辑 Set，任意 CacheLine 都可放置，比较范围是整组条目。相连度越高，冲突缺失越少，但 Tag 比较器、选择器和功耗越大。

### 15.1.2 Way、Set、Offset

- `Offset` 是 CacheBlock 内的字节（进一步可拆成 beat/byte mask）索引，决定访问哪一个 beat 及其字节。
- `Set` 是组索引，决定访问哪一个组；同一 Set 的所有 Way 同时查 Tag/Meta。
- `Way` 是该 Set 中的槽位编号，命中比较后选出，或者由替换器选出牺牲 Way。

CPL2 的目录请求把这些字段作为独立输入，说明了硬件中的对应关系：

```scala
// coupledL2/src/main/scala/coupledL2/Directory.scala:69-81
class DirRead(implicit p: Parameters) extends L2Bundle {
  val tag         = UInt(tagBits.W)
  val set         = UInt(setBits.W)
  val wayMask     = UInt(cacheParams.ways.W)
  val replacerInfo = new ReplacerInfo()
  val refill      = Bool()
  val mshrId       = UInt(mshrBits.W)
  val cmoAll       = Bool()
  val cmoWay       = UInt(wayBits.W)
}
```

### 15.1.3 为什么按 CacheBlock/Cacheline 管理，L1/L2/L3 能否使用不同大小

Cacheline 是 Tag、权限、脏位和一致性协议共同管理的最小粒度。按块传输可以把一次 Tag/Probe/权限开销摊到多个字节，并且让替换、预取和写回都具有明确边界。TileLink 的 Acquire/Grant/Release 也以 block 为语义，物理传输再分成若干 beat。

不同层级可以有不同 `blockBytes`，但相邻总线端点必须能表达双方的传输粒度；若粒度不同，就必须在边界做拆分/合并、维护多个 beat 的掩码和一致性状态，不能直接把一个层级的 line 当成另一个层级的 line。HuanCun 的参数明确把块大小和 beat 数分开：

```scala
// huancun/src/main/scala/huancun/HuanCun.scala:46-60
val blockBytes = cacheParams.blockBytes
val beatBytes = cacheParams.channelBytes.d.get
val beatSize = blockBytes / beatBytes
val wayBits = log2Ceil(cacheParams.ways)
val setBits = log2Ceil(cacheParams.sets)
val offsetBits = log2Ceil(blockBytes)
val beatBits = offsetBits - log2Ceil(beatBytes)
```

当前 CPL2 配置还明确要求一个块恰好两个 beat：

```scala
// coupledL2/src/main/scala/coupledL2/RequestArb.scala:265-267
require(beatSize == 2)
```

因此答案是“可以不同，但必须在接口和控制逻辑中显式适配”；不能只改一个参数。

### 15.1.4 Directory 与 Snoop 两种一致性策略

Directory 在共享位置保存“哪些客户端持有该块、谁拥有可写权限”等元数据，发生写入或权限升级时只 Probe 相关客户端；通信量近似与 sharer 数量相关，适合较大系统，但目录存储和维护复杂。Snoop 则把请求广播给所有可能的缓存，由每个缓存自行查 Tag 并响应，控制简单、低核数延迟低，但广播带宽和功耗随客户端数增加。

昆明湖目录项直接保存客户端位图，属于目录式实现：

```scala
// coupledL2/src/main/scala/coupledL2/Directory.scala:29-39
class MetaEntry(implicit p: Parameters) extends L2Bundle {
  val dirty = Bool()
  val state = UInt(stateBits.W)
  val clients = UInt(clientBits.W)
  val alias = aliasBitsOpt.map(width => UInt(width.W))
  val prefetch = if (hasPrefetchBit) Some(Bool()) else None
  val prefetchSrc = if (hasPrefetchSrc) Some(UInt(PfSource.pfSourceBits.W)) else None
  val accessed = Bool()
  val tagErr = Bool()
  val dataErr = Bool()
}
```

### 15.1.5 “非阻塞”缓存是否一直非阻塞

非阻塞指缓存有 MSHR、请求队列和数据缓冲，某个 miss 等待下级响应时，独立请求仍可进入并行处理；它不是“任何情况下都不阻塞”。MSHR 数量、同一 Set/Tag 的顺序约束、Tag/Data SRAM 端口、Probe/Release 优先级以及 Grant/Refill/ReleaseBuffer 容量都会形成反压。

CPL2 的入口就把这些资源状态折算成 Block 信号：

```scala
// coupledL2/src/main/scala/coupledL2/RequestArb.scala:102-109
io.mshrTask.ready := !io.fromGrantBuffer.blockMSHRReqEntrance &&
  !s1_needs_replRead && !(mshr_task_s1.valid && !s2_ready) &&
  (if (io.fromSourceC.isDefined) !io.fromSourceC.get.blockMSHRReqEntrance else true.B) &&
  (if (io.fromTXDAT.isDefined) !io.fromTXDAT.get.blockMSHRReqEntrance else true.B) &&
  (if (io.fromTXRSP.isDefined) !io.fromTXRSP.get.blockMSHRReqEntrance else true.B) &&
  (if (io.fromTXREQ.isDefined) !io.fromTXREQ.get.blockMSHRReqEntrance else true.B)
```

HuanCun 的 RequestBuffer 也按“同 Set 的有效 MSHR 且尚未释放”建立冲突掩码。因此，非阻塞是“尽量重叠”，不是无条件接收。

### 15.1.6 Inclusive、Non-inclusive、Exclusive 及优势

- **Inclusive**：上层存在的 line 必须在下层也存在。下层目录可过滤 Probe，查找简单；代价是下层驱逐时必须回 invalidation/Probe 上层，并且重复存储占容量。
- **Non-inclusive**：下层不保证包含所有上层 line。容量利用率和替换自由度更好，但目录需要记录上层客户端，即使自身未保存数据时也要能发 Probe；实现路径更多。
- **Exclusive**：同一 line 只存在于一个层级。总有效容量最大，但层间迁移、命中判断和一致性转移更复杂，访问延迟也可能增加。

HuanCun 通过参数选择两条实现路径：

```scala
// huancun/src/main/scala/huancun/Slice.scala:93-102
val ms = Seq.fill(mshrsAll) {
  if (cacheParams.inclusive)
    Module(new inclusive.MSHR())
  else Module(new noninclusive.MSHR())
}
require(mshrsAll == mshrs + 2)
val ms_abc = ms.init.init
val ms_bc = ms.init.last
val ms_c = ms.last
```

非 inclusive 目录还把自身目录和客户端目录分开，正是为了在“数据不在 L3”时仍追踪上层持有者（`huancun/src/main/scala/huancun/noninclusive/Directory.scala:26-65`）。

### 15.1.7 预取算法与替换算法的作用

预取器预测未来会访问的 block，在真正 miss 前发 Hint/读请求，用带宽和容量换取更低的 miss 延迟；预测错误会污染 Cache、消耗下级带宽。替换器在需要新 line 时选择牺牲 Way，目标是保留更可能再次访问的 line；策略（PLRU、RRIP 等）只影响选择，不改变一致性协议。

HuanCun 默认参数把两者明确分开：

```scala
// huancun/src/main/scala/huancun/HCCacheParameters.scala:83-104
ways: Int = 4,
sets: Int = 128,
blockBytes: Int = 64,
replacement: String = "plru",
mshrs: Int = 14,
channelBytes: TLChannelBeatBytes = TLChannelBeatBytes(32),
prefetch: Option[PrefetchParameters] = None,
inclusive: Boolean = true,
```

目录在 refill 时先优先选 invalid way，再调用 replacer（`coupledL2/src/main/scala/coupledL2/Directory.scala:322-330`），而 BestOffsetPrefetch 用历史 offset 的评分表学习预取距离（`huancun/src/main/scala/huancun/prefetch/BestOffsetPrefetch.scala:145-228`）。

## 15.2 TileLink 总线部分

### 15.2.1 A/B/C/D/E 通道的方向、优先级和信息

方向由“客户端/缓存”与“管理者/下级缓存或内存”定义：A、C、E 向下（client -> manager），B、D 向上（manager -> client）。通道承载的信息如下。

| 通道 | 方向 | 典型消息 | 作用 |
|---|---|---|---|
| A | client -> manager | Get、Put、AcquireBlock/Perm、Hint | 读写、权限获取、预取 |
| B | manager -> client | Probe | 要求客户端降级/失效/回写 |
| C | client -> manager | ProbeAck、ProbeAckData、Release/ReleaseData | 对 Probe 应答或主动释放 |
| D | manager -> client | AccessAck(_Data)、Grant(_Data)、ReleaseAck、HintAck | A/C 的响应或权限/数据返回 |
| E | client -> manager | GrantAck | 确认已接收 Grant，释放 sink 资源 |

TileLink 1.8.1 规定跨通道的全局优先级从低到高为 **A < B < C < D < E**；目的就是让跨网络的资源等待保持无环。这里的顺序与某个模块内部怎样选择多个输入不是同一个问题：昆明湖的局部仲裁还要结合 MSHR、同 Set 冲突和数据阵列端口决定。规范来源：[TileLink Specification v1.8.1](https://starfivetech.com/uploads/tilelink_spec_1.8.1.pdf)，第 2.2 节和第 5 节。

例如 CPL2 的 ReqArb 只在 **内侧入站** C/B/A 三类 task 间选择，明确 C 优先于 B、B 优先于 A；它是对上述全局顺序的局部实现，不是重新定义 TL 的通道优先级：

```scala
// coupledL2/src/main/scala/coupledL2/RequestArb.scala:157-180
val sinkValids = VecInit(Seq(
  io.sinkC.valid && !block_C,
  io.sinkB.valid && !block_B,
  io.sinkA.valid && !block_A
)).asUInt
io.sinkA.ready := sink_ready_basic && !block_A &&
  !sinkValids(1) && !sinkValids(0)
io.sinkB.ready := sink_ready_basic && !block_B && !sinkValids(0)
chnl_task_s1.bits := ParallelPriorityMux(
  sinkValids, Seq(C_task, B_task, A_task))
```

HuanCun 的 Slice 也把 C、B、A/ABC MSHR 按依赖关系仲裁（`huancun/src/main/scala/huancun/Slice.scala:464-510`）；DataStorage 则有独立的端口优先级，见 15.4.5。

### 15.2.2 各类 Opcode 的语义

以下按 TileLink 1.8.1 的语义归类，并对应昆明湖代码中实际使用的消息。

| 类别 | Opcode | 语义 |
|---|---|---|
| A 读写 | `Get` | 读取指定字节，返回 `AccessAckData` |
| A 写 | `PutFullData` / `PutPartialData` | 全掩码/部分掩码写入，返回 `AccessAck` |
| A 原子 | `ArithmeticData` / `LogicalData` | 原子运算，返回旧值 |
| A 提示 | `Hint`（规范中称 Intent） | 提示管理者预取或执行非必需操作，通常回 `HintAck` |
| A 权限 | `AcquireBlock` | 获取 block 数据和权限（N->B/T） |
| A 权限 | `AcquirePerm` | 只升级权限，不必返回整块数据 |
| B | `Probe` | 管理者要求客户端把 T/B 降级到 B/N，必要时回数据 |
| C | `ProbeAck` / `ProbeAckData` | 对 Probe 的无数据/带数据应答 |
| C | `Release` / `ReleaseData` | 客户端主动放弃权限，带/不带数据 |
| D | `AccessAck` / `AccessAckData` | 完成 Put/Get |
| D | `Grant` / `GrantData` | 完成 Acquire，授予权限，后续需要 E |
| D | `ReleaseAck` | 接收 Release 后允许客户端释放事务状态 |
| D | `HintAck` | 完成 Hint |
| E | `GrantAck` | 客户端确认 Grant 已消费，管理者可回收 sink |

例如 CPL2 MSHR 对 A 请求选择 `AcquirePerm` 或 `AcquireBlock`，并按是否需要 T 选择 `BtoT/NtoT/NtoB`：

```scala
// coupledL2/src/main/scala/coupledL2/tl2tl/MSHR.scala:126-142
oa.opcode := Mux(req_acquirePerm && dirResult.hit,
  req.opcode, AcquireBlock)
oa.param := Mux(req_needT,
  Mux(dirResult.hit, BtoT, NtoT), NtoB)
```

### 15.2.3 缓存块状态及状态转移

TileLink 权限状态可抽象为 N（Nothing，无权限）、B（Branch，可读共享）、T（Trunk，唯一可写）。昆明湖目录用四态编码：`INVALID`、`BRANCH`、`TRUNK`、`TIP`；其中 TIP 表示本级持有 trunk、下游存在 branches。定义见 `coupledL2/src/main/scala/coupledL2/Consts.scala:26-75` 和 `huancun/src/main/scala/huancun/MetaData.scala:26-66`。

典型转移如下：

1. N -> B/T：客户端对缺失 line 发 `AcquireBlock`，下级返回 `Grant(Data)`；若请求共享读为 B，需要 T 为写则为 T。
2. B -> T：客户端发 `AcquirePerm`（参数 `BtoT`）。管理者 Probe 其他 B 客户端，收到全部 `ProbeAck` 后回 Grant。
3. T -> B/N：管理者发 `Probe(toB/toN)`；客户端回 `ProbeAck(_Data)` 后更新目录。
4. 客户端主动放弃：发 `Release/ReleaseData`，管理者回 `ReleaseAck`，目录降为 INVALID 或保留为 B/T。
5. T + 下游 B：本级记为 TIP；当下游共享者全部被 Probe 清除后可回到 TRUNK。

CPL2 的 Probe 参数正是按请求和目录状态生成：

```scala
// coupledL2/src/main/scala/coupledL2/tl2tl/MSHR.scala:148-164
ob.opcode := Probe
ob.param := Mux(!state.s_pprobe, req.param,
  Mux(req_get && dirResult.hit && meta.state === TRUNK, toB, toN))
```

### 15.2.4 为什么请求会“打断”：Probe 打断 Acquire、Release 打断 Probe

同一 line 的事务共享 MSHR、目录项、数据端口和 TL sink/source。若 Acquire 已经占用入口而 Probe/Release 不能及时进入，双方可能各自等待对方释放权限或缓冲区，因此实现必须允许高依赖请求插入流水线（而不是简单 FIFO）。

- **Probe 打断 Acquire**：Acquire miss 选中一个仍被 L1 持有的 victim 时，先 Probe L1 收回权限/脏数据，才能完成替换和 Grant。
- **Release 打断 Probe**：Probe 的应答可能带回 victim 数据；若客户端同时主动 Release，ReleaseData 应优先写入 ReleaseBuffer，避免 Probe 等待数据、又阻塞释放路径。

CPL2 MSHR 的合法顺序直接编码了这些依赖：Grant 只有在回收 Probe 完成后才调度，Release 还要等替换数据返回：

```scala
// coupledL2/src/main/scala/coupledL2/tl2tl/MSHR.scala:115-123
io.tasks.source_a.valid := !state.s_acquire
val mp_release_valid := !state.s_release && state.w_rprobeacklast &&
  state.w_grantlast && state.w_replResp
val mp_grant_valid := !state.s_refill && state.w_grantlast &&
  state.w_rprobeacklast
```

HuanCun inclusive MSHR 的部分序也明确写出 `s_release > s_acquire`、`s_pprobe > s_acquire`，并据此生成 task valid（`huancun/src/main/scala/huancun/inclusive/MSHR.scala:326-355`）。

### 15.2.5 优先级错误导致死锁的例子

以“低优先级 A 占用了必须转发高优先级 C 的唯一队列”为例：

1. M0 的 A/Acquire 到达 L2；为了给它权限，L2 必须先对持有者 M1 发 B/Probe。
2. M1 已接到 Probe，必须发 C/ProbeAckData；但错误仲裁让持续到来的 A 请求占住唯一缓冲/路由资源，C 不能前进。
3. L2 在收到 C 前不能发 D/Grant 给 M0；M0 的 A 又在等待 D，于是 A -> B -> C -> D 的依赖环无法解除。

这正是规范要求 A < B < C < D < E 的原因：高优先级消息不能被低优先级消息永久占住其所需资源。实际实现还必须遵守同一 block 的序列化约束：Grant 等待必要 ProbeAck，Grant 发出后等待 GrantAck 前不应再发同 block Probe；Release 发出后等待 ReleaseAck 前不应继续该 block 的 ProbeAck/Acquire/Release。CPL2 的 `ParallelPriorityMux(C_task,B_task,A_task)`、GrantBuffer 的 inflight Grant 记录和 HuanCun 的部分序共同实现这些局部约束。

### 15.2.6 为什么 Grant 需要 GrantAck、Release 需要 ReleaseAck

`Grant` 可能带多个 beat，并且携带管理者分配的 `sink`。管理者在收到客户端 E 通道 `GrantAck` 前不能回收 sink 或假设客户端已经完成权限接收，否则新的事务可能复用 sink、与旧数据混淆。CPL2 的 GrantBuffer 记录 inflight grant，E fire 才清除：

```scala
// coupledL2/src/main/scala/coupledL2/GrantBuffer.scala:265-290
when (io.d_task.fire && (dtaskOpcode === Grant ||
  dtaskOpcode === GrantData || io.d_task.bits.task.mergeA)) {
  val entry = inflightGrant(inflight_insertIdx)
  entry.valid := true.B
  entry.bits.set := io.d_task.bits.task.set
  entry.bits.tag := io.d_task.bits.task.tag
}
when(io.e.fire) {
  inflightGrant(io.e.bits.sink).valid := false.B
}
```

`Release` 则是客户端把权限/脏数据交回管理者；D 通道 `ReleaseAck` 是管理者确认已经接收并完成必要目录/数据处理的事务完成点。CPL2 MSHR 把它作为 `w_releaseack`，只有与其它 schedule/wait 条件同时满足才 free：

```scala
// coupledL2/src/main/scala/coupledL2/tl2tl/MSHR.scala:526-531
val no_wait = state.w_rprobeacklast && state.w_pprobeacklast &&
  state.w_grantlast && state.w_releaseack && state.w_replResp
val will_free = no_schedule && no_wait
```

## 15.3 CPL2 部分

### 15.3.1 缓存总体框架

CPL2 的一个 Slice 由请求入口、主流水、目录/数据阵列、MSHR 控制、四个 TL sink/source 控制器以及多个事务缓冲组成。`Slice.scala` 的实例化和连接是最直接的框图代码：

```scala
// coupledL2/src/main/scala/coupledL2/tl2tl/Slice.scala:40-53
val reqArb     = Module(new RequestArb())
val a_reqBuf   = Module(new RequestBuffer)
val mainPipe   = Module(new MainPipe())
val mshrCtl    = Module(new MSHRCtl())
val directory  = Module(new Directory())
val dataStorage = Module(new DataStorage())
val refillUnit = Module(new RefillUnit())
val sinkA = Module(new SinkA)
val sinkB = Module(new SinkB)
val sinkC = Module(new SinkC)
val sourceC = Module(new SourceC)
val grantBuf = Module(new GrantBuffer)
val refillBuf = Module(new MSHRBuffer(wPorts = 2))
val releaseBuf = Module(new MSHRBuffer(wPorts = 3))
```

TL 连接也在同一文件（`Slice.scala:127-172`）：内侧 A/B/C/D/E 由 `sinkA/sinkB/sinkC/grantBuf` 消费或产生，外侧 A/B/C/D/E 分别由 `AcquireUnit/SourceB/SourceC/RefillUnit` 驱动。CPL2 顶层按 bank/hash 把请求分发到 Slice，Slice 内每个 MSHR 以 source id 关联一个 block。

### 15.3.2 请求处理流程

#### 15.3.2.1 收到 L1 Acquire 且命中

`SinkA` 把内侧 A 转成 task（解析 tag/set/offset/opcode/alias）。MainPipe 查目录后，若命中且权限足够，可直接产生 D 响应；若是命中但需要权限升级（例如 B -> T），则分配 MSHR，MSHR 发 `AcquirePerm`，必要时 Probe 其他客户端，最后由 GrantBuffer 返回 Grant。

```scala
// coupledL2/src/main/scala/coupledL2/tl2tl/MainPipe.scala:170-203
val acquire_on_hit_s3 = meta_s3.state === BRANCH && req_needT_s3 &&
  !req_prefetch_s3
val need_acquire_s3_a = req_s3.fromA &&
  Mux(dirResult_s3.hit, acquire_on_hit_s3, acquire_on_miss_s3)
val need_mshr_s3 = need_acquire_s3_a || need_probe_s3_a || cache_alias
```

若 `need_mshr_s3` 为假，MainPipe 直接选择 `AccessAck/Grant`（`MainPipe.scala:252-283`）；若为真，`mshr_alloc_s3` 锁存请求和目录结果（`MainPipe.scala:200-249`）。

#### 15.3.2.2 收到 L1 Acquire 且缺失，需要从 L3 获取

目录 miss 时 `need_acquire_s3_a` 为真，MSHR 的 source-A task 把请求编码为 `AcquireBlock`，参数为 `NtoB` 或 `NtoT`。`AcquireUnit` 将其送到外侧 A；外侧 D 的 Grant/GrantData 由 `RefillUnit` 接收，写入 `refillBuf`，再由 MSHR 生成内侧 Grant。

```scala
// coupledL2/src/main/scala/coupledL2/tl2tl/MSHR.scala:126-145
oa.opcode := Mux(req_acquirePerm && dirResult.hit,
  req.opcode, AcquireBlock)
oa.param := Mux(req_needT,
  Mux(dirResult.hit, BtoT, NtoT), NtoB)
oa.source := io.id
```

`RefillUnit` 对外侧 D 的 first/last beat 计数，并把一个 block 的两个 beat 合成写入 MSHRBuffer（`coupledL2/src/main/scala/coupledL2/tl2tl/RefillUnit.scala:34-79`）。

#### 15.3.2.3 缺失且需要替换

Directory 在 refill 请求上先计算同 Set 中仍被 MSHR 占用的 way，再优先选择 invalid way，若没有才调用 replacer；无可用 way 时返回 `retry`，MSHR 留在等待状态并重试。选中有效 victim 后，若有 L1 client，MSHR 先发 replacement Probe；脏/被访问数据通过 DataStorage 读出并由 SourceC 发 `ReleaseData`，收到 `ReleaseAck` 后才允许覆盖目录和写入新 Grant。

```scala
// coupledL2/src/main/scala/coupledL2/Directory.scala:248-330
val occWayMask_s2 = VecInit(io.msInfo.map(s =>
  Mux(s.valid && s.bits.set === req_s2.set &&
    (s.bits.blockRefill || s.bits.dirHit), UIntToOH(s.bits.way, ways), 0.U)
)).reduceTree(_ | _)
val freeWayMask_s3 = RegEnable(~occWayMask_s2, refillReqValid_s2)
val refillRetry = !freeWayMask_s3.orR
// invalid way first, otherwise replacer-selected way
val chosenWay = Mux(inv, invalidWay, replaceWay)
```

MSHR 的 `mp_release_valid` 同时等待 `w_replResp`、Grant 和 replacement Probe，避免新 line 覆盖仍在使用的 victim（`MSHR.scala:115-123,166-215`）。

#### 15.3.2.4 收到 L3 Probe 且需要去 Probe L1

外侧 B 进入 `SinkB`。它先检查是否有同 set/tag 的 MSHR、是否正在 refill/release；无冲突才生成 Probe task。MSHR 将 client 位图转换成 source-B Probe，等待所有 `ProbeAck(_Data)`，Probe 返回的数据进入 `ReleaseBuffer`，随后 MainPipe 更新目录、必要时向 L3 回 `ProbeAckData`。

```scala
// coupledL2/src/main/scala/coupledL2/tl2tl/SinkB.scala:62-76
val addrConflict = VecInit(io.msInfo.map(s =>
  s.valid && s.bits.set === task.set && s.bits.reqTag === task.tag &&
  !s.bits.willFree && s.bits.w_grantfirst
)).asUInt.orR
val replaceConflictMask = VecInit(io.msInfo.map(s =>
  s.valid && s.bits.set === task.set && s.bits.metaTag === task.tag &&
  (s.bits.blockRefill || !s.bits.w_releaseack)
)).asUInt
val replaceConflict = replaceConflictMask.orR
io.task.valid := io.b.valid && !addrConflict && !replaceConflict
```

### 15.3.3 ReqArb / MainPipe 流水级

ReqArb 是三段小流水：

1. **S0**：对 MSHR task、SinkA/B/C 做 ready/优先级门控，检查 GrantBuffer、SourceB/C 和 MainPipe 的入口阻塞。
2. **S1**：锁存 task，发起目录读；处理 `block_A/B/C` 和 replacement DataStorage read 冲突。
3. **S2**：把 S1 选出的 task 送进 MainPipe/数据路径，Hint 的特殊 MCP stall 在此登记。

```scala
// coupledL2/src/main/scala/coupledL2/RequestArb.scala:190-208
val s1_AHint_fire = io.sinkA.fire && io.sinkA.bits.opcode === Hint
val ds_mcp2_stall = RegNext(s1_fire && !s1_AHint_fire)
s2_ready := !ds_mcp2_stall
task_s2.valid := s1_fire
io.taskToPipe_s2 := task_s2
```

MainPipe 随后是目录/权限决策和数据阵列流水：S2 锁存 Arb task，S3 得到目录结果并计算 hit/miss、MSHR/Probe/数据需求，S4/S5 完成 DataStorage 返回、元数据写回和 TL C/D 输出。源码中的阶段注释和寄存器如下：

```scala
// coupledL2/src/main/scala/coupledL2/tl2tl/MainPipe.scala:125-135,463-475,511-528
/* ======== Stage 2 ======== */
val task_s2 = io.taskFromArb_s2
/* ======== Stage 3 ======== */
val task_s3 = RegInit(0.U.asTypeOf(Valid(new TaskBundle())))
task_s3.valid := task_s2.valid
when(task_s2.valid) { task_s3.bits := task_s2.bits }
/* ======== Stage 4 ======== */
val task_s4 = RegInit(0.U.asTypeOf(Valid(new TaskBundle())))
task_s4.valid := task_s3.valid && !req_drop_s3
/* ======== Stage 5 ======== */
val task_s5 = RegInit(0.U.asTypeOf(Valid(new TaskBundle())))
task_s5.valid := task_s4.valid && !req_drop_s4
```

### 15.3.4 Directory 目录项包含哪些内容

CPL2 `MetaEntry` 包含：脏位 `dirty`、权限状态 `state`、客户端位图 `clients`、可选别名位 `alias`、预取标志/来源 `prefetch/prefetchSrc`、访问位 `accessed`、Tag/Data ECC 错误 `tagErr/dataErr`（`coupledL2/src/main/scala/coupledL2/Directory.scala:29-39`）。`DirResult` 还返回命中的 tag/set/way、完整 meta、错误和 replacer 信息（`Directory.scala:83-100`）。

这些字段分别用于：权限和 Probe 决策（state/clients）、虚拟别名判定（alias）、替换/预取反馈（accessed/prefetch）、可靠性处理（tagErr/dataErr）。

### 15.3.5 状态机：s_、w_、alloc_state 与 MSHR 子请求

CPL2 约定 `s_` 是“是否已经调度”的状态，`w_` 是“是否已收到应答”的状态；MainPipe 的 `alloc_state` 用**低有效**初始化这些状态：注释明确写着 “s_ and w_ are false-as-valid”，所以 `false` 表示该动作仍待执行/该应答仍待等待，置 `true` 表示完成。

```scala
// coupledL2/src/main/scala/coupledL2/tl2tl/MainPipe.scala:634-667
// ! Caution: s_ and w_ are false-as-valid
when (req_s3.fromA) {
  alloc_state.s_refill := false.B
  alloc_state.w_replResp := dirResult_s3.hit
  when (need_acquire_s3_a) {
    alloc_state.s_acquire := false.B
    alloc_state.w_grantfirst := false.B
    alloc_state.w_grantlast := false.B
  }
  when (cache_alias || need_probe_s3_a) {
    alloc_state.s_rprobe := false.B
    alloc_state.w_rprobeackfirst := false.B
    alloc_state.w_rprobeacklast := false.B
  }
}
```

MSHR 根据这些 flag 生成各通道子请求，并用 `ParallelPriorityMux` 选主流水任务：

```scala
// coupledL2/src/main/scala/coupledL2/tl2tl/MSHR.scala:115-123,410-415
io.tasks.source_a.valid := !state.s_acquire
io.tasks.source_b.valid := !state.s_pprobe || !state.s_rprobe
io.tasks.mainpipe.valid := mp_release_valid || mp_probeack_valid || mp_grant_valid
io.tasks.mainpipe.bits := ParallelPriorityMux(Seq(
  mp_grant_valid -> mp_grant,
  mp_release_valid -> mp_release,
  mp_probeack_valid -> mp_probeack))
```

收到 A/B/C/D/E 响应时分别置对应 `w_*`；当 `no_schedule && no_wait` 成立才释放 MSHR（`MSHR.scala:526-531`）。

### 15.3.6 入口阻塞：BlockA/BlockB/BlockC 与同 Set 并发

ReqArb 把 MSHRCtl、MainPipe、GrantBuffer 三类阻塞条件按通道 OR 合并：

```scala
// coupledL2/src/main/scala/coupledL2/RequestArb.scala:113-180
val block_A = io.fromMSHRCtl.blockA_s1 || io.fromMainPipe.blockA_s1 ||
  io.fromGrantBuffer.blockSinkReqEntrance.blockA_s1
val block_B = io.fromMSHRCtl.blockB_s1 || io.fromMainPipe.blockB_s1 ||
  io.fromGrantBuffer.blockSinkReqEntrance.blockB_s1 ||
  (if (io.fromSourceC.isDefined) io.fromSourceC.get.blockSinkBReqEntrance else false.B) ||
  (if (io.fromTXDAT.isDefined) io.fromTXDAT.get.blockSinkBReqEntrance else false.B) ||
  (if (io.fromTXRSP.isDefined) io.fromTXRSP.get.blockSinkBReqEntrance else false.B)
val block_C = io.fromMSHRCtl.blockC_s1 || io.fromMainPipe.blockC_s1 ||
  io.fromGrantBuffer.blockSinkReqEntrance.blockC_s1
```

基本规则是 C 优先于 B，B 优先于 A；某通道仅在自身 block 为假且更高优先级通道无 valid 时 ready。MSHRCtl 在 MSHR 数量接近上限时先堵 A，再堵 B：

```scala
// coupledL2/src/main/scala/coupledL2/MSHRCtl.scala:122-147
io.toReqArb.blockC_s1 := false.B
io.toReqArb.blockB_s1 := mshrFull
io.toReqArb.blockA_s1 := a_mshrFull
io.toReqArb.blockG_s1 := false.B
```

同 Set 并发不是“同 Set 永远互斥”，而是允许不冲突的阶段重叠：RequestBuffer 统计同 Set 的 MSHR 和 MainPipe S2/S3 请求；只有占满 ways、会读写同一 way、或存在尚未释放的地址/依赖时才停住新请求。

```scala
// coupledL2/src/main/scala/coupledL2/RequestBuffer.scala:160-174,197-213
def noFreeWayForSet(set: UInt): Bool = {
val sameSet_s2 = task_s2.valid && task_s2.bits.fromA &&
  !task_s2.bits.mshrTask && task_s2.bits.set === set
val sameSet_s3 = RegNext(task_s2.valid && task_s2.bits.fromA &&
  !task_s2.bits.mshrTask) && RegEnable(task_s2.bits.set, task_s2.valid) === set
val sameSetCnt = PopCount(VecInit(io.mshrInfo.map(
  s => s.valid && s.bits.set === set && s.bits.fromA) :+ sameSet_s2 :+ sameSet_s3).asUInt)
val noFreeWay = sameSetCnt >= cacheParams.ways.U
noFreeWay
}
val canFlow = flow.B && !full && !conflict(in) && !chosenQValid &&
  !Cat(io.mainPipeBlock).orR && !noFreeWay(in)
entry.rdy := !conflict(in) && !mpBlock && !s1Block && !noFreeWay(in)
```

因此新请求进入主流水的条件是：有空闲/可合并的 RequestBuffer 项、没有同地址冲突、同 Set 尚有可用 way、MSHR/Grant/数据端口未反压；以下任一条件成立就阻塞或转为等待项。

### 15.3.7 GrantBuffer 的作用

GrantBuffer 做三件事：向 L1 发送 D 通道 Grant/GrantData，接收 E 通道 GrantAck，并根据在途 Grant、队列和 MSHR 数量给 ReqArb 反压；此外还把 HintAck/预取响应排队送回预取器。

```scala
// coupledL2/src/main/scala/coupledL2/GrantBuffer.scala:53-83
// 1. Send Grant/GrantData/ReleaseAck/AccessAckData from D;
//    receive GrantAck through E.
// 2. Send response to Prefetcher; 3. block MainPipe entrance on capacity.
val d_task = Flipped(DecoupledIO(new TaskWithData()))
val d = DecoupledIO(new TLBundleD(edgeIn.bundle))
val e = Flipped(DecoupledIO(new TLBundleE(edgeIn.bundle)))
val grantStatus = Output(Vec(grantBufInflightSize, new GrantStatus))
```

当前配置要求 `beatSize == 2`。第一 beat 可直送 D，第二 beat 暂存在两 beat queue，下一周期再发，直到 last：

```scala
// coupledL2/src/main/scala/coupledL2/GrantBuffer.scala:178-217
require(beatSize == 2)
val grantBufValid = RegInit(false.B)
when(deqValid && io.d.ready && !grantBufValid && deqTask.opcode(0)) {
  grantBufValid := true.B
  grantBuf.task := deqTask
  grantBuf.data := Mux(deqTask.isKeyword.getOrElse(false.B),
    deqData(0), deqData(1))
  grantBuf.grantid := deqId
}
io.d.valid := grantBufValid || deqValid
```

收到 E 后清除对应 sink 的 inflight 记录（`GrantBuffer.scala:265-290`）；容量不足时输出 `blockSinkReqEntrance.blockA/B/C` 和 `blockMSHRReqEntrance`，由 ReqArb 形成入口反压，而不是让 D 通道无条件丢数据。

### 15.3.8 RefillBuffer 与 ReleaseBuffer

两者都是按 MSHR id、按 beat 存放数据的 `MSHRBuffer`：

```scala
// coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:39-66
// Each entry is indexed by MSHR id; selected beat lanes are overwritten.
val buffer = Reg(Vec(mshrsAll, Vec(beatSize, UInt((beatBytes * 8).W))))
buffer.zipWithIndex.foreach {
  case (block, i) =>
  val wens = VecInit(io.w.map(w => w.valid && w.bits.id === i.U)).asUInt
  val w_data = PriorityMux(wens, io.w.map(_.bits.data))
  val w_beatSel = PriorityMux(wens, io.w.map(_.bits.beatMask))
  when(wens.orR) {
    block.zip(w_beatSel.asBools).zipWithIndex.foreach { case ((beat, sel), i) =>
      when (sel) { beat := w_data.data((i+1) * beatBytes * 8 - 1, i * beatBytes * 8) }
    }
  }
}
val rdata = buffer(io.r.bits.id).asUInt
io.resp.data.data := RegEnable(rdata, 0.U.asTypeOf(rdata), io.r.valid)
```

- **RefillBuffer**：`RefillUnit` 接收外侧 D 的 Grant/GrantData/ReleaseAck；其中有数据的 GrantData 在 first/last beat 期间写入 `refillBufWrite`，MainPipe/MSHR 之后读出，降低外侧响应到内侧 Grant 的时延（`coupledL2/src/main/scala/coupledL2/tl2tl/RefillUnit.scala:45-79`）。
- **ReleaseBuffer**：替换/ProbeAckData/嵌套 ReleaseData 的写回暂存区。它允许 Release/Probe 数据先脱离主流水排队，等 SourceC 或 MainPipe 可用时再发，避免低优先级 DataStorage 读阻塞协议应答。

Slice 的写端口连接还显式给出优先级：nested ReleaseData/ProbeAckData > MainPipe DataStorage 读结果（`coupledL2/src/main/scala/coupledL2/tl2tl/Slice.scala:114-125`）。

### 15.3.9 高优先级请求嵌套低优先级请求

嵌套的核心是“每个 MSHR 持有独立 s/w 状态，入口只对冲突 Set/Tag 堵塞”，而不是抢占并复制整个流水。MSHRCtl 用 `FastArbiter` 把各 MSHR 的 source A/B 和 MainPipe task 汇聚；ReqArb 对 C/B/A 做优先级选择；ReleaseBuffer/RefillBuffer 把数据路径从控制路径解耦。

MSHR 主流水任务优先级是 Grant > Release > ProbeAck；但 Grant 的 valid 又依赖 `w_rprobeacklast`，所以 ProbeAck 会先完成必要前置，再让 Grant 嵌套进入：

```scala
// coupledL2/src/main/scala/coupledL2/tl2tl/MSHR.scala:118-123,410-415
val mp_release_valid = !state.s_release && state.w_rprobeacklast &&
  state.w_grantlast && state.w_replResp
val mp_grant_valid = !state.s_refill && state.w_grantlast &&
  state.w_rprobeacklast
io.tasks.mainpipe.bits := ParallelPriorityMux(Seq(
  mp_grant_valid -> mp_grant,
  mp_release_valid -> mp_release,
  mp_probeack_valid -> mp_probeack))
```

如果嵌套请求命中同一 MSHR 的 set/tag，`nestedwb_match` 直接更新脏位并把数据写入 ReleaseBuffer（`MSHR.scala:582-597`）；不冲突的 MSHR 可并行推进。

### 15.3.10 Cache alias 问题及解决

别名是不同虚拟地址映射到同一物理 block，导致同一物理数据在不同虚拟 Tag/alias 下出现两份，读写可能不一致。CPL2 的目录在命中时比较请求 alias 与已有 client alias；发现不同时把它当作需要 MSHR/Probe 的特殊事务，先收回旧客户端权限，再用新 alias 完成 Grant。Get/Prefetch 不应改写已有 alias 位，真正获得权限的 Acquire 才更新：

```scala
// coupledL2/src/main/scala/coupledL2/tl2tl/MainPipe.scala:170-196,348-355
val cache_alias = req_acquire_s3 && dirResult_s3.hit &&
  meta_s3.clients(0) && meta_s3.alias.getOrElse(0.U) =/=
  req_s3.alias.getOrElse(0.U)
val need_mshr_s3_a = need_acquire_s3_a || need_probe_s3_a || cache_alias
// Get and Prefetch should not change alias bit
val metaW_s3_a_alias = Mux(req_get_s3 || req_prefetch_s3,
  meta_s3.alias.getOrElse(0.U), req_s3.alias.getOrElse(0.U))
```

MSHR 还把 alias task 标记在状态中，并在生成 Grant 时决定是否使用 ProbeData（`coupledL2/src/main/scala/coupledL2/tl2tl/MSHR.scala:284-408`）。这样既避免双份可写副本，也不因普通 Get/预取误覆盖 alias 元数据。

### 15.3.11 预取请求的处理

预取器产生请求后，SinkA 把它编码为 `Hint`，带 `PREFETCH_READ/WRITE` 参数、`fromL2pft` 和 source；MainPipe 将其分配到 MSHR，但预取命中不重复分配（代码有断言），缺失时按普通 Acquire 获取数据。GrantBuffer 把预取的 HintAck/结果送回预取器，MainPipe 的 `prefetchTrain` 在真实访问 miss 或命中预取 line 时更新训练。

```scala
// coupledL2/src/main/scala/coupledL2/SinkA.scala:94-130
task.opcode := Hint
task.param := Mux(req.needT, PREFETCH_WRITE, PREFETCH_READ)
task.fromL2pft.foreach(_ := req.needAck)
task.reqSource := req.source
```

```scala
// coupledL2/src/main/scala/coupledL2/tl2tl/MainPipe.scala:446-460
train.valid := task_s3.valid &&
  (((req_acquire_s3 || req_get_s3) && req_s3.needHint.getOrElse(false.B) &&
    (!dirResult_s3.hit || meta_s3.prefetch.get)) || req_s3.mergeA)
train.bits.hit := Mux(req_s3.mergeA, true.B, dirResult_s3.hit)
```

RequestBuffer 会合并同地址的预取/普通 A 请求，GrantBuffer 的独立响应队列再把预取结果交给 `Prefetcher`（`coupledL2/src/main/scala/coupledL2/GrantBuffer.scala:234-260`）。

## 15.4 HuanCun 部分

以下默认讨论 HuanCun 的 **inclusive** 实现；当前源码同时保留 non-inclusive 实现，遇到会改变结论的地方单独说明。

### 15.4.1 缓存总体工作流程

HuanCun Slice 的数据流可以按五步理解：

1. 内侧 A 由 `SinkA` 接收。若是 Put，先把数据 beat 收入 PutBuffer；请求元数据进入 RequestBuffer/MSHRAlloc。
2. MSHR 向 Directory 发读请求并选择 way；随后按状态机生成 SourceA（向下 Acquire/Put）、SourceB（Probe）、SourceC（Release/ProbeAck）、SourceD（向上响应）和 SourceE（GrantAck）。
3. 外侧 D 由 `SinkD` 接收。GrantData 可旁路到 RefillBuffer，也可写 DataStorage；Probe/Release 的 C 由 `SinkC` 接收并唤醒相应 MSHR。
4. DataStorage 为 SourceC/SinkC/SinkD/SourceD 提供多端口抽象，Slice 用 arbiter 把各 MSHR 的同类 task 汇聚到通道控制器。
5. 事务完成后 MSHR 写回 Directory/Tag，等待 GrantAck、ReleaseAck 等尾部应答，最后释放 entry。

Slice 的实例和通道连接如下：

```scala
// huancun/src/main/scala/huancun/Slice.scala:31-91,93-153
val sinkA = Module(new SinkA)
val sourceB = Module(new SourceB)
val sinkC = Module(if (cacheParams.inclusive) new inclusive.SinkC else new noninclusive.SinkC)
val sourceD = Module(new SourceD)
val sourceA = Module(new SourceA)
val sinkB = Module(new SinkB(edgeOut))
val sourceC = Module(new SourceC(edgeOut))
val sinkD = Module(new SinkD(edgeOut))
val sourceE = Module(new SourceE(edgeOut))
val refillBuffer = Module(new RefillBuffer)
val inBuf = cacheParams.innerBuf
sinkA.io.a <> inBuf.a(io.in.a)
io.in.b <> inBuf.b(sourceB.io.b)
sinkC.io.c <> inBuf.c(io.in.c)
io.in.d <> inBuf.d(sourceD.io.d)
sinkE.io.e <> inBuf.e(sourceE.io.e)
io.out.a <> outBuf.a(sourceA.io.a)
sinkB.io.b <> outBuf.b(io.out.b)
io.out.c <> outBuf.c(out_c)
sinkD.io.d <> outBuf.d(io.out.d)
io.out.e <> outBuf.e(sourceE.io.e)
```

顶层 `HuanCun.scala:362-405` 按 bank 实例化多个 Slice，并在返回路径恢复 bank 地址；参数中的 `blockBytes`、`beatBytes`、sets、ways 和 MSHR 数量由 `HCCacheParameters.scala:33-47,83-124` 统一提供。

### 15.4.2 MSHR 中常见请求的控制逻辑

inclusive MSHR 把一次请求拆成“需要发送的动作”和“等待的响应”。常见请求的控制可以概括为：

- **AcquireBlock/AcquirePerm**：若 miss 或需要权限，发送 SourceA；收到 Grant/GrantData 后置 `w_grantfirst/w_grantlast/w_grant`，再执行内侧 D 响应和 meta 写回。
- **Get/Put**：命中时可直接生成 AccessAck(_Data)；Put miss 需要先保存 PutBuffer 数据，并可能向下转为 Acquire/Put。
- **Probe（来自外侧 B）**：先由 SourceB 向内侧 client 发送 Probe，等待所有 `ProbeAck(_Data)`；随后 SourceC 向外侧返回 ProbeAck，脏数据同时写 DataStorage/ReleaseBuffer。
- **Release（来自内侧 C）**：更新目录、必要时写入数据，并由 SourceD 向内侧返回 ReleaseAck；HuanCun 自己向外侧发送 Release 时，则等待外侧 SinkD 的 ReleaseAck。
- **Hint/预取**：标记 `isPrefetch`，可在下级获取 line；预取响应不阻塞普通 CPU MSHR 的完成。

关键的调度 valid 与响应 flag：

```scala
// huancun/src/main/scala/huancun/inclusive/MSHR.scala:349-361,523-561
io.tasks.source_a.valid := (!s_acquire || !s_transferput) && s_release && s_pprobe
io.tasks.source_b.valid := !s_rprobe || !s_pprobe
io.tasks.source_c.valid := !s_release && w_rprobeackfirst ||
  !s_probeack && w_pprobeackfirst
io.tasks.source_d.valid := !s_execute && w_grant && w_pprobeack
io.tasks.source_e.valid := !s_grantack && w_grantfirst

when (io.resps.sink_d.valid) {
  when (io.resps.sink_d.bits.opcode === Grant ||
    io.resps.sink_d.bits.opcode === GrantData) {
    w_grantfirst := true.B
    w_grantlast := io.resps.sink_d.bits.last
    w_grant := req.off === 0.U || io.resps.sink_d.bits.last
  }
  when (io.resps.sink_d.bits.opcode === ReleaseAck) { w_releaseack := true.B }
}
when (io.resps.sink_e.valid) { w_grantack := true.B }
```

MSHR 只有在所有 schedule 和 wait 条件满足时清除 `req_valid`；因此“常见请求控制”本质上是一个带依赖的微操作调度器，而不是单一的 opcode `switch`。

### 15.4.3 MSHR、各通道控制器的连接与仲裁

每个 MSHR 的 `tasks.source_a/source_b/source_c/source_d/source_e` 分别连到对应控制器，响应按 source/sink/set 匹配回各 MSHR。Slice 对 `mshrsAll` 个 MSHR 分成三类：普通 ABC MSHR、BC MSHR、C MSHR。C MSHR 和 BC MSHR 是嵌套写回/Probe 的高优先级保留槽，使用 `block_abc/block_bc` 暂停低优先级 ABC MSHR。

```scala
// huancun/src/main/scala/huancun/Slice.scala:167-203,392-418
val block_bc = c_mshr.io.status.valid
val block_abc = block_bc || bc_mshr.io.status.valid
abc_mshr.zipWithIndex.foreach {
  case (mshr, i) =>
  val bc_disable = bc_mask_latch(i) && select_bc
  val c_disable = c_mask_latch(i) && select_c
  mshr.io.enable := !(bc_disable || c_disable)
}
def block_b_c[T <: Data](sink: DecoupledIO[T], sources: Seq[DecoupledIO[T]]): Unit = {
  val c_src = sources.last
  val b_src = sources.init.last
  val abc_src = sources.init.init
  val arbiter = Module(new FastArbiter[T](chiselTypeOf(sink.bits), sources.size))
  arbiter.io.in.init.init.zip(abc_src).foreach(x => x._1 <> x._2)
  block_decoupled(arbiter.io.in.init.last, b_src, select_c)
  arbiter.io.in.last <> c_src
  sink <> arbiter.io.out
}
```

通道仲裁器 `arbTasks` 对 C/BC/ABC 有显式优先级：strict 模式下 C valid 最高，其次 BC，最后 ABC；非 strict 模式用一拍 latch 保持同样的优先级并减少组合路径（`Slice.scala:464-510`）。响应通过 source/sink id 送回：

```scala
// Slice.scala:532-545
mshr.io.resps.sink_c.valid := sinkC.io.resp.valid &&
  sinkC.io.resp.bits.set === mshr.io.status.bits.set
mshr.io.resps.sink_d.valid := sinkD.io.resp.valid &&
  sinkD.io.resp.bits.source === i.U
mshr.io.resps.sink_e.valid := sinkE.io.resp.valid &&
  sinkE.io.resp.bits.sink === i.U
```

### 15.4.4 Directory 选 way 的逻辑

inclusive Directory 的顺序是：先在当前 Set 比较 Tag；refill 时优先找 INVALID way；没有 invalid way 才使用 replacement（例如 PLRU）给出的 way。目录初始化状态为 INVALID，命中条件为 `state =/= INVALID`：

```scala
// huancun/src/main/scala/huancun/inclusive/Directory.scala:50-72
def invalid_way_sel(metaVec: Seq[DirectoryEntry], repl: UInt) = {
  val invalid_vec = metaVec.map(_.state === MetaData.INVALID)
  val has_invalid_way = Cat(invalid_vec).orR
  val way = ParallelPriorityMux(
    invalid_vec.zipWithIndex.map(x => x._1 -> x._2.U(wayBits.W)))
  (has_invalid_way, way)
}
// dir_hit_fn = x => x.state =/= MetaData.INVALID
```

non-inclusive 版本同时选择 self directory 和 client directory 的 way。self 目录没有 invalid way 时优先找 TRUNK，因为 non-inclusive 不变量是“self 为 TRUNK 时 client 必有 TIP”，替换 TRUNK 不会丢失仍由上层持有的唯一数据：

```scala
// huancun/src/main/scala/huancun/noninclusive/Directory.scala:199-213
// 1. invalid way; 2. if none, choose a TRUNK
val trunk_vec = metaVec.map(_.state === MetaData.TRUNK)
val trunk_way = ParallelPriorityMux(trunk_vec.zipWithIndex.map(
  x => x._1 -> x._2.U(wayBits.W)))
Mux(has_invalid_way, invalid_way,
  Mux(repl_way_is_trunk, repl, trunk_way))
```

### 15.4.5 DataStorage 的端口、读写优先级及实现

DataStorage 的五个逻辑端口含义如下：

- `sourceC_raddr`：SourceC 回写/ReleaseData 要读出的数据；
- `sinkC_waddr/wdata`：收到 ProbeAckData/ReleaseData 后写回；
- `sinkD_waddr/wdata`：外侧 GrantData 写入 cache 或旁路前的落盘；
- `sourceD_raddr`：向内侧 D 返回 Grant/AccessAckData 时读数据；
- `sourceD_waddr/wdata`：Put/合并写入数据阵列。

物理 SRAM 是 banked single-port。一个 bank 同周期只能接受一个请求，所以优先级按协议紧迫度和数据丢失风险定义：最高是 SourceC 读（不能延迟 Probe/Release 数据），其次是 SinkC 写，再是 SinkD 写、SourceD 写，最低是 SourceD 读。代码用请求列表顺序定义全局优先级，并对每个 bank 做 `PriorityMux`：

```scala
// huancun/src/main/scala/huancun/DataStorage.scala:134-153,173-177
val sourceC_req = req(wen = false, io.sourceC_raddr, io.sourceC_rdata)
val sourceD_rreq = req(wen = false, io.sourceD_raddr, io.sourceD_rdata)
val sourceD_wreq = req(wen = true, io.sourceD_waddr, io.sourceD_wdata)
val sinkD_wreq = req(wen = true, io.sinkD_waddr, io.sinkD_wdata)
val sinkC_req = req(wen = true, io.sinkC_waddr, io.sinkC_wdata)
val reqs = Seq(sourceC_req, sinkC_req, sinkD_wreq,
  sourceD_wreq, sourceD_rreq)
reqs.foldLeft(0.U(nrBanks.W)) { case (sum, req) =>
  req.bankSum := sum
  req.bankSel | sum
}
val selectedReq = PriorityMux(reqs.map(_.bankSel(i)), reqs)
```

`bankSum`/`accessVec` 先屏蔽同一 stack 的冲突，再由 `PriorityMux` 选最先请求；因此“优先级”不是注释上的抽象排序，而是每个 bank 的可综合选择逻辑。写请求还经过 `RegNext`，配合 SRAM latency 和 ECC 返回（`DataStorage.scala:178-206`）。

### 15.4.6 高优先级请求打断低优先级请求

HuanCun 不依赖全局抢占，而是保留高优先级 MSHR 槽并在每条 task 通道上屏蔽低优先级输入。C MSHR 有效时 `block_bc`，BC MSHR 有效时 `block_abc`；同一个周期 C 的目录写回会阻止 B 写回，保证 nested writeback 原子性。这样 ProbeAck/ReleaseData 可以插入低优先级 Acquire 的等待窗口，而不破坏已发出的 beat。

```scala
// huancun/src/main/scala/huancun/Slice.scala:182-203,425-429
val block_bc = c_mshr.io.status.valid
val block_abc = block_bc || bc_mshr.io.status.valid
// don't allow b write back when c is valid
block_b_c(Pipeline.pipeTo(directory.io.dirWReq),
  add_ctrl(ms.map(_.io.tasks.dir_write), ctrl.map(_.io.s_dir_w)))
```

inclusive MSHR 内部的偏序还要求 `Release`、`PProbe` 先于 `Acquire`，所以低优先级 Acquire 会在 `s_release/s_pprobe` 未置位时等待，而高优先级 task 直接 fire（`inclusive/MSHR.scala:326-355`）。

### 15.4.7 RequestBuffer 的位置与作用

RequestBuffer 位于 SinkA/预取请求与 `MSHRAlloc` 之间（`Slice.scala:126-153`），当前实例为 4 entries。它做三类事情：短暂吸收 A 请求、按同 Set 建立依赖顺序、合并重复预取并在 MSHR 未释放时等待。

```scala
// huancun/src/main/scala/huancun/RequestBuffer.scala:16-25,47-75
val wait_table = Reg(Vec(entries, UInt(mshrs.W)))
// same-set entries are sent to MSHR in order
val buffer_dep_mask = Reg(Vec(entries, Vec(entries, Bool())))
val conflict_mask = (0 until mshrs) map { i =>
  val s = io.mshr_status(i)
  s.valid && set_conflict(s.bits.set, in_set) && !s.bits.will_free
}
val dup = io.in.valid && io.in.bits.isPrefetch.getOrElse(false.B) &&
  Cat(dup_mask).orR
rdys(insert_idx) := !conflict && !Cat(req_deps).orR
```

当 MSHR `will_free` 或前序 buffer entry 发出后，`wait_table/dep_mask` 清位，条目才重新 ready；这既保持同 Set 的写后读顺序，又允许不同 Set 并行。

### 15.4.8 RefillBuffer 的位置与作用

RefillBuffer 位于 `SinkD`（外侧 D）与 `SourceD`/内侧 Grant 之间：Slice 中 `refillBuffer.io.w <> sinkD.io.bypass_write`、`refillBuffer.io.r <> sourceD.io.bypass_read`（`Slice.scala:80-82`）。它保存按 MSHR/buffer id 编号的每个 beat，使外侧 GrantData 到达后可以直接旁路给内侧 D，不必先等待 SRAM 写入再读出。

```scala
// huancun/src/main/scala/huancun/RefillBuffer.scala:24-69
/** RefillBuffer is used to reduce outer grant -> inner grant latency.
  * refill data can be bypassed to inner cache without go through SRAM
  */
val buffer = Mem(bufBlocks, Vec(beatSize, new DSData()))
val valids = RegInit(VecInit(Seq.fill(bufBlocks) {
  VecInit(Seq.fill(beatSize) { false.B })
}))
when (r.valid && r.ready && rlast) { valids(r.id).foreach(_ := false.B) }
```

写入 first beat 时分配空闲 id，last beat 读出后整项失效；若无空闲 entry，`w.ready` 反压 SinkD。

### 15.4.9 SinkA Put 请求的数据流向：两条流能否合并

SinkA 对 Put 数据只在第一个 beat 分配 MSHR，所有 beat 先写入 PutBuffer；每个 beat 由 `beatVals` 标记，分别提供给 SourceA 和 SourceD 两个消费者：

```scala
// huancun/src/main/scala/huancun/SinkA.scala:48-70,118-128
val putBuffer = Reg(Vec(bufBlocks, Vec(beats, new PutBufferBeatEntry())))
when (a.fire && hasData) {
  putBuffer(insertIdx)(count).data := a.bits.data
  putBuffer(insertIdx)(count).mask := a.bits.mask
  beatVals(insertIdx)(count) := true.B
}
io.d_pb_pop.ready := beatVals(io.d_pb_pop.bits.bufIdx)(io.d_pb_pop.bits.count)
io.a_pb_pop.ready := beatVals(io.a_pb_pop.bits.bufIdx)(io.a_pb_pop.bits.count)
```

**流 1（外侧 A）**：若 Put 需要向下转发，SourceA 从 `a_pb_pop` 取 beat，组成外侧 `PutFull/PutPartialData`；S1 通过 `TLArbiter.lowest(edgeIn, io.a, a_put, a_acquire)` 发出（`SourceA.scala:68-114`）。

**流 2（内侧 D/数据阵列）**：SourceD 对 Put miss 生成 `AccessAck(_Data)`，从 `d_pb_pop` 取相同 beat，并在 S4 按 mask 与外侧返回数据合并后写 DataStorage（`SourceD.scala:65-71,128-169,243-275`）。

两条流不能简单合并成一个消费者：它们的目标通道、握手时机、beat 计数和数据语义不同；一个可能在外侧 A 被 backpressure，另一个仍需要向内侧 D 确认。可以共享 PutBuffer 存储和仲裁元数据，但必须保留两个独立 pop/ready 状态，否则会丢 beat 或让一个协议通道互相等待。

### 15.4.10 HuanCun 的 Cache alias 及解决

alias 的根因仍是虚拟索引/别名地址让同一物理 line 可能对应多个客户端 Tag。HuanCun non-inclusive 目录为每个 client entry 保存 `state` 和可选 `alias`，同时保存 self directory 的状态、clientStates 和 way；收到 alias 请求时，MSHR 比较 client directory 的 tag/alias，发 Probe 收回旧副本，再更新 clientDir/selfDir，而不是直接新增一份可写 line。

```scala
// huancun/src/main/scala/huancun/noninclusive/Directory.scala:26-36
class SelfDirEntry(implicit p: Parameters) extends HuanCunBundle {
  val dirty = Bool()
  val state = UInt(stateBits.W)
  val clientStates = Vec(clientBits, UInt(stateBits.W))
  val prefetch = if (hasPrefetchBit) Some(Bool()) else None
}
class ClientDirEntry(implicit p: Parameters) extends HuanCunBundle {
  val state = UInt(stateBits.W)
  val alias = aliasBitsOpt.map(bits => UInt(bits.W))
}
```

non-inclusive MSHR 直接比较请求 alias 和 client directory alias，并强制 alias 请求走 `preferCache`、Probe-to-N 路径：

```scala
// huancun/src/main/scala/huancun/noninclusive/MSHR.scala:114-132,719-731,1065-1104
val req_client_meta = clients_meta(iam)
val client_hit_flag = Hold(req_client_meta.hit, l2Only = true)
val cache_alias = !req.isPrefetch.getOrElse(false.B) && client_hit_flag && req_acquire &&
  req_client_meta.alias.getOrElse(0.U) =/= req.alias.getOrElse(0.U)
// Cache alias will always preferCache to avoid trifle
val preferCache = (req.preferCache && !bypassPut_all) || cache_alias

ob.alias.foreach(a => a.zip(clients_meta.map(_.alias.get)).foreach {
  case (sink, source) => sink := source
})
ob.param := Mux(
  req.fromB,
  req.param,
  Mux(
    req.opcode === Hint,
    Mux(req.param === PREFETCH_READ, toB, toN),
    Mux(req.fromCmoHelper,
      Mux(req.param === 1.U, toB, toN),
      Mux(req_needT || cache_alias, toN, toB)
    )
  )
)
```

实际文件在 `ob.param` 外还有 Hint/CMO 分支；上面摘出的是 alias 相关条件。其效果是先以旧 alias 发送 Probe、回收旧客户端副本，再更新目录，避免同一物理 line 留下两份可写副本。

### 15.4.11 预取请求的处理

Slice 把预取器输出转成普通 MSHRRequest，但 opcode 是 `Hint`，参数依据 `needT` 选择 `PREFETCH_READ/WRITE`，并打上 `isPrefetch=true`、`preferCache=true`；该请求与 CPU A 请求一起进入 RequestBuffer，因此仍遵守同 Set 依赖和重复过滤。

```scala
// huancun/src/main/scala/huancun/Slice.scala:622-649
mshrReq.bits.opcode := TLMessages.Hint
mshrReq.bits.param := Mux(pftReq.bits.needT,
  TLHints.PREFETCH_WRITE, TLHints.PREFETCH_READ)
mshrReq.bits.isPrefetch.foreach(_ := true.B)
mshrReq.bits.isBop.foreach(_ := pftReq.bits.isBOP)
mshrReq.bits.preferCache := true.B
```

预取器本身在 `huancun/src/main/scala/huancun/prefetch/Prefetcher.scala:84-142` 连接 BOP/receiver；BestOffset 表用 RecentRequestTable 和 OffsetScoreTable 学习 offset（`huancun/src/main/scala/huancun/prefetch/BestOffsetPrefetch.scala:96-228`）。Slice 只把 ABC MSHR 的 `prefetch_train`/`prefetch_resp` 仲裁给预取器，BC/C 保留槽不参与训练响应（`huancun/src/main/scala/huancun/Slice.scala:513-529`）。inclusive MSHR 对 Hint miss 触发下级获取，预取命中不要求普通 CPU 的 HintAck 数据路径（`huancun/src/main/scala/huancun/inclusive/MSHR.scala:270-320`），从而把预取延迟隐藏在正常访问之前。

## 15.5 代码追踪小结

一条典型的昆明湖 V3 miss 可以沿以下路径复核：

```text
L1 A -> CPL2 SinkA -> RequestBuffer/ReqArb -> MainPipe/Directory
     -> MSHR -> AcquireUnit -> outer A
outer D -> RefillUnit/RefillBuffer -> MSHR -> MainPipe -> GrantBuffer -> L1 D
victim/probe: outer B -> SinkB -> MSHR -> SourceB/SourceC -> outer C
```

读代码时应把“协议通道握手”“MSHR 的 s_/w_ 状态”和“目录/数据阵列端口”三层分开；任何一层的 ready/block 都可能让表面上的非阻塞请求暂时停顿。上文所有实现结论均以列出的 V3 源码为证据，章节 14 中针对旧版本的描述不能替代这些代码。
