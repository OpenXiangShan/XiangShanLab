# 14. Load / Store（访存）

本章以访存工作组入门指南提供的术语和问题为入口，但所有关于当前昆明湖的实现结论都只依据下面的源码检出：

~~~text
~/xiangshan-frontend/kmh-v2-env/XiangShan
branch: kunminghu-v2
HEAD:   3fdbebedf6d505dedfdd66f8d8154c82136963a6
~~~

这里实际存在的目录名是 kmh-v2-env。下文的路径均相对于 XiangShan 根目录；每段 Scala 代码都是从该源码树复制的原始 Chisel 片段。

> 证据规则：只有紧邻“代码依据”的段落才描述当前昆明湖实现。RISC-V 内存模型、旧版本经验和代码中不存在的功能不在本章伪装成当前 RTL 事实。

## 14.1 读 Chisel 的必要语法

| 写法 | 在本章中的硬件含义 |
| --- | --- |
| **Module(new X)** | elaboration 时例化一个 X 硬件模块。 |
| **Seq.fill(n)(Module(...))** | 例化 n 个并行模块，不是运行时循环。 |
| **RegInit(x)** | 生成带复位初值的寄存器。 |
| **Wire 或 WireInit** | 生成组合逻辑信号。 |
| **:=** | 单向硬件连接。 |
| **<>** | 根据 Bundle 方向批量连接接口。 |
| **when / otherwise** | 生成条件更新或组合选择，不是软件分支。 |
| **Mux(c, a, b)** | 生成组合多路选择器。 |
| **RegNext(x)** | 在下一个时钟边界后输出本拍采样的 x。 |

### 14.1.1 从 Scala 文本判断硬件时间

Chisel 源码同时包含两层程序。外层是 Scala：`Seq.fill`、`map`、`foreach`、`if`、`def` 和 `case class` 在 elaboration 时执行，用来**生成**电路；内层是 Chisel 硬件图：`Bool`、`UInt`、`Wire`、`Reg`、`when`、`Mux` 和 `DecoupledIO` 才描述芯片运行时的信号与状态。读下面每段代码时，先做这四个判断：

1. `Seq` 是 Scala 集合，`Vec` 是硬件向量。`(0 until N).foreach` 的 N 若为参数，就在生成时展开 N 份硬件；它不是每个周期执行 N 次的软件循环。
2. `val` 只是在 Scala 中给对象取名，并不天然表示寄存器。右侧出现 `RegInit`、`RegNext` 或 `RegEnable` 才有跨拍状态；出现 `Wire`、`WireInit`、`Mux`、`&&`、`||`、`&` 时，通常是同拍组合网络。
3. `when (cond) { reg := x }` 的 `cond` 是硬件 `Bool`，它为寄存器写使能或组合选择增加条件；Scala 的 `if (i == 0)` 则由 elaboration 常量决定，会只生成其中一支硬件。
4. `Decoupled` 接口中，生产者给出 `valid` 和 `bits`，消费者给出 `ready`；同拍 `valid && ready` 才是 `fire`。因此看到 `RegEnable(bits, fire)` 时，应理解为“只有一次真正握手时才锁存 payload”。

源码的书写顺序也不等于周期顺序。例如后文的 S2 先使用 `s2_fwd_mask`，再在 `for` 中给它每个 lane 赋值；Chisel 最终建立的是一张组合依赖图，而非按文本逐行在一个周期内执行。

**代码依据：utility/src/main/scala/utility/ClockGatedReg.scala:23-39, 53-81**

~~~scala
object GatedValidRegNext {
  // 3 is the default minimal width of EDA inserted clock gating cells.
  // so using `GatedValidRegNext` to signals whoes width is less than 3 may not help.

  // It is useless to clockgate only one bit, so change to RegNext here
  def apply(next: Bool, init: Bool = false.B): Bool = {
    val last = WireInit(false.B)
    last := RegNext(next, init)
    last
  }

  def apply(last: Vec[Bool]): Vec[Bool] = {
    val next = VecInit(Seq.fill(last.size)(false.B))
    next := RegEnable(last, VecInit(Seq.fill(last.size)(false.B)), last.asUInt =/= next.asUInt)
    next
  }
}

object GatedRegNext{
  // Vec can be judged and assigned one by one
  def regEnableVec[T <: Data](lastVec: Vec[T], initOptVec: Option[Vec[T]]): Vec[T] = {
    val nextVec = WireInit(0.U.asTypeOf(lastVec))
    for (i <- 0 until lastVec.length) {
      initOptVec match {
        case Some(initVec) => nextVec(i) := RegEnable(lastVec(i), initVec(i), lastVec(i).asUInt =/= nextVec(i).asUInt)
        case None => nextVec(i) := RegEnable(lastVec(i), 0.U.asTypeOf(lastVec(i)), lastVec(i).asUInt =/= nextVec(i).asUInt)
      }
    }
    nextVec
  }

  // NOTICE: The larger Data width , the longger time of =/= operations, which may lead to timing violations.
  // Callers need to consider timing requirements themselves.
  def apply[T <: Data](last: T, initOpt: Option[T] = None): T = {
    val next = WireInit(0.U.asTypeOf(last))
    last match {
      case v: Vec[_] =>
        next := regEnableVec(v.asInstanceOf[Vec[T]], initOpt.map(_.asInstanceOf[Vec[T]]))
      case _ =>
        initOpt match {
          case Some(init) => next := RegEnable(last, init, last.asUInt =/= next.asUInt)
          case None => next := RegEnable(last, 0.U.asTypeOf(last), last.asUInt =/= next.asUInt)
        }
    }
    next
  }
}
~~~

这里能看到两种不同含义。单 bit 的 `GatedValidRegNext(next)` 直接返回 `RegNext(next)`，所以它确实把 Bool 延后一拍；不要从名字猜测它一定插入了时钟门控。通用 `GatedRegNext(last)` 则先创建名为 `next` 的 Wire，再用 `RegEnable(last, init, last =/= next)` 驱动它：寄存器的 D 输入是 `last`，使能仅在输入值不同于当前保持值时为真。对 `Vec`，`for` 为每个元素生成一个这样的保持寄存器。后文的 redirect 取消计数因此应读作“对变化采样并保持的跨拍值”，而不是普通的组合延迟线。

本章还反复出现两种接口，它们不能混为一谈：

| 接口 | 硬件字段 | 本章中的读法 |
| --- | --- | --- |
| `DecoupledIO[T]` | `valid`、`ready`、`bits` | 只有 `valid && ready` 的 `fire` 才传输一项数据；SBuffer 和 `NewPipelineConnect` 都属于这一类。 |
| `Valid[T]` | `valid`、`bits` | 没有 ready，生产者不能等消费端接受；`valid` 为假时 `bits` 不应解释，例如 RAW redirect 候选。 |

这也解释了 `io.rollback.bits := DontCare` 的写法：它只是给 valid 为假时的无关字段提供一个合法的默认驱动；消费者只有在对应 `valid` 为真时才应使用 robIdx、level 和 PC 等字段。

### 14.1.2 当前默认容量和端口

**代码依据：xiangshan/Parameters.scala:167-176**

~~~scala
  VirtualLoadQueueSize: Int = 72,
  LoadQueueRARSize: Int = 72,
  LoadQueueRAWSize: Int = 32, // NOTE: make sure that LoadQueueRAWSize is power of 2.
  RollbackGroupSize: Int = 8,
  LoadQueueReplaySize: Int = 72,
  LoadUncacheBufferSize: Int = 16,
  LoadQueueNWriteBanks: Int = 8, // NOTE: make sure that LoadQueueRARSize/LoadQueueRAWSize is divided by LoadQueueNWriteBanks
  StoreQueueSize: Int = 56,
  StoreQueueNWriteBanks: Int = 8, // NOTE: make sure that StoreQueueSize is divided by StoreQueueNWriteBanks
  StoreQueueForwardWithMask: Boolean = true,
~~~

这些 `: Int =` 和 `: Boolean =` 字段是 Scala 默认参数，在 elaboration 时决定结构规模：当前 VLQ/RAR/RAW/Replay/Uncache/SQ 为 72/72/32/72/16/56。`LoadQueueNWriteBanks` 与 `StoreQueueNWriteBanks` 同样是静态银行数；注释中的整除约束在生成配置时成立，而不是运行时用硬件除法检查。它们不是周期级寄存器值，因此不能在波形中把参数本身当作动态计数器。

**代码依据：xiangshan/Parameters.scala:214-226**

~~~scala
  LoadPipelineWidth: Int = 3,
  StorePipelineWidth: Int = 2,
  VecLoadPipelineWidth: Int = 2,
  VecStorePipelineWidth: Int = 2,
  VecMemSrcInWidth: Int = 2,
  VecMemInstWbWidth: Int = 1,
  VecMemDispatchWidth: Int = 1,
  VecMemDispatchMaxNumber: Int = 16,
  VecMemUnitStrideMaxFlowNum: Int = 2,
  VecMemLSQEnqIteratorNumberSeq: Seq[Int] = Seq(16, 16, 16, 16, 16, 16),
  StoreBufferSize: Int = 16,
  StoreBufferThreshold: Int = 7,
  EnsbufferWidth: Int = 2,
~~~

默认标量结构有三条 Load 流水线、两条 Store 地址流水线、16 项 SBuffer 和两路 SBuffer 入队。`VecMemLSQEnqIteratorNumberSeq` 是 Scala 的 `Seq[Int]`，不是一个运行时 `Vec`；它把若干固定深度传给后续生成逻辑。STD 的数量不能从 `StorePipelineWidth` 推断，必须看实际调度资源和例化。

**代码依据：xiangshan/Parameters.scala:493-498**

~~~scala
      IssueBlockParams(Seq(
        ExeUnitParams("STD0", Seq(StdCfg, MoudCfg), Seq(), Seq(Seq(IntRD(5, 2), FpRD(9, 0)))),
      ), numEntries = 16, numEnq = 2, numComp = 12),
      IssueBlockParams(Seq(
        ExeUnitParams("STD1", Seq(StdCfg, MoudCfg), Seq(), Seq(Seq(IntRD(3, 2), FpRD(10, 0)))),
      ), numEntries = 16, numEnq = 2, numComp = 12),
~~~

这里外层的 `Seq(...)` 组织一个 issue block 的执行单元参数，内层 `Seq(StdCfg, MoudCfg)` 列出该单元支持的配置。`STD0` 和 `STD1` 是两次独立的 `ExeUnitParams(...)` 调用，elaboration 后形成两个 Store-data 执行资源，而不是一个端口在两个周期复用。它们与 Store 地址单元分开，说明地址和数据可独立等待、独立发射。

### 14.1.3 默认 L1D 配置

**代码依据：top/Configs.scala:258-276**

~~~scala
case class WithNKBL1D(n: Int, ways: Int = 8) extends Config((site, here, up) => {
  case XSTileKey =>
    val sets = n * 1024 / ways / 64
    up(XSTileKey).map(_.copy(
      dcacheParametersOpt = Some(DCacheParameters(
        nSets = sets,
        nWays = ways,
        tagECC = Some("secded"),
        dataECC = Some("secded"),
        replacer = Some("setplru"),
        nMissEntries = 16,
        nProbeEntries = 8,
        nReleaseEntries = 18,
        nMaxPrefetchEntry = 6,
        enableTagEcc = true,
        enableDataEcc = true
      ))
    ))
})
~~~

`WithNKBL1D` 是 Scala 配置构造器，不会生成一个名为 WithNKBL1D 的硬件模块。`case XSTileKey` 匹配 Rocket-Chip 配置查询；`up(XSTileKey)` 取上层已有配置，`map(_.copy(...))` 返回带新 `dcacheParametersOpt` 的不可变副本。sets 的公式在生成期执行，说明容量参数 n 以 KiB 计，按 ways 和 64 B line 换算为组数。

**代码依据：top/Configs.scala:460-485**

~~~scala
class DefaultConfig(n: Int = 1) extends Config(
  L3CacheConfig("16MB", inclusive = false, banks = 4, ways = 16)
    ++ L2CacheConfig("1MB", inclusive = true, banks = 4)
    ++ WithNKBL1D(64, ways = 4)
    ++ new BaseConfig(n)
)

class KunminghuV2Config(n: Int = 1) extends Config(
  L2CacheConfig("1MB", inclusive = true, banks = 4, tp = false)
    ++ new DefaultConfig(n)
    ++ new WithCHI
)
~~~

`++` 是 Scala Config 的组合运算：`KunminghuV2Config` 先叠加自己的 L2 参数，再叠加 `DefaultConfig`，最后叠加 `WithCHI`。因此 `WithNKBL1D(64, ways = 4)` 会向下游模块提供 nSets=256、nWays=4 的参数；当前默认 L1D 为 64 KiB、4 路、64 B line，即 256 组。这个结论来自配置组合，而不是误读最小配置。

**代码依据：xiangshan/cache/dcache/DCacheWrapper.scala:128-142**

~~~scala
  val DCacheSets = cacheParams.nSets
  val DCacheWayDiv = 2
  val DCacheWays = cacheParams.nWays
  val DCacheBanks = 8 // hardcoded
  val DCacheDupNum = 16
  val DCacheSRAMRowBits = cacheParams.rowBits // hardcoded
  val DCacheWordBits = 64 // hardcoded
  val DCacheWordBytes = DCacheWordBits / 8
  val MaxPrefetchEntry = cacheParams.nMaxPrefetchEntry
  val DCacheVWordBytes = VLEN / 8
  require(DCacheSRAMRowBits == 64)
  val DCacheSetBits = log2Ceil(DCacheSets)
  val DCacheSizeBits = DCacheSRAMRowBits * DCacheBanks * DCacheWays * DCacheSets
~~~

这一段中的 `DCacheSets`、`DCacheWays`、`DCacheBanks` 和 `DCacheSRAMRowBits` 都由 `val` 保存为生成期常量，用于决定数组和位宽，不是 cache 运行时输出。`DCacheSizeBits` 把行宽、bank、路和组相乘，是总数据位数的结构表达式；它也说明 8 bank 是在模块内写死的。`require` 是 elaboration 时断言，若 SRAM 行宽 `DCacheSRAMRowBits` 不为 64 bit，硬件生成失败。

## 14.2 MemBlock：访存硬件怎样装配

### 14.2.1 标量、向量和非对齐模块

**代码依据：xiangshan/mem/MemBlock.scala:420-437**

~~~scala
  val loadUnits = Seq.fill(LduCnt)(Module(new LoadUnit))
  val storeUnits = Seq.fill(StaCnt)(Module(new StoreUnit))
  val stdExeUnits = Seq.fill(StdCnt)(Module(new MemExeUnit(backendParams.memSchdParams.get.issueBlockParams.find(_.StdCnt != 0).get.exuBlockParams.head)))
  val hybridUnits = Seq.fill(HyuCnt)(Module(new HybridUnit)) // Todo: replace it with HybridUnit
  val stData = stdExeUnits.map(_.io.out)
  val exeUnits = loadUnits ++ storeUnits

  // The number of vector load/store units is decoupled with the number of load/store units
  val vlSplit = Seq.fill(VlduCnt)(Module(new VLSplitImp))
  val vsSplit = Seq.fill(VstuCnt)(Module(new VSSplitImp))
  val vlMergeBuffer = Module(new VLMergeBufferImp)
  val vsMergeBuffer = Seq.fill(VstuCnt)(Module(new VSMergeBufferImp))
  val vSegmentUnit  = Module(new VSegmentUnit)
  val vfofBuffer    = Module(new VfofBuffer)

  // misalign Buffer
  val loadMisalignBuffer = Module(new LoadMisalignBuffer)
  val storeMisalignBuffer = Module(new StoreMisalignBuffer)
~~~

`Seq.fill(LduCnt)(Module(new LoadUnit))` 先在 Scala 层创建一个长度为 LduCnt 的集合；集合的每一项执行一次 `Module(new LoadUnit)`，因而得到彼此独立的 LoadUnit 实例。`++` 只是把两个 Scala 集合拼接为 `exeUnits`，不表示两类执行单元在硬件中串联。`stData = stdExeUnits.map(_.io.out)` 也只是收集各 STD 模块的输出端口，后续连线才决定每个端口的去向。向量 split/merge 由独立参数控制，且两个 misalign buffer 是单例 `Module`，所以向量端口数不等于标量端口数。

**代码依据：xiangshan/mem/MemBlock.scala:615-625**

~~~scala
  // TODO: fast load wakeup
  val lsq     = Module(new LsqWrapper)
  val sbuffer = Module(new Sbuffer)
  // if you wants to stress test dcache store, use FakeSbuffer
  // val sbuffer = Module(new FakeSbuffer) // out of date now
  io.mem_to_ooo.stIssuePtr := lsq.io.issuePtrExt

  dcache.io.hartId := io.hartId
  lsq.io.hartId := io.hartId
  sbuffer.io.hartId := io.hartId
  atomicsUnit.io.hartId := io.hartId
~~~

`Module(new LsqWrapper)` 和 `Module(new Sbuffer)` 是实际子模块例化；`val lsq`、`val sbuffer` 则分别持有这些模块的 IO 句柄。`io.mem_to_ooo.stIssuePtr := lsq.io.issuePtrExt` 不是一次软件赋值，而是在父模块中生成从 LSQ 输出到后端端口的一条单向网线。后面的四个 `hartId :=` 同理把同一个顶层输入扇出到不同子模块；文本靠后的连接不会晚一个周期发生。

### 14.2.2 Load 的数据和检查接口

**代码依据：xiangshan/mem/MemBlock.scala:921-949**

~~~scala
    // forward
    loadUnits(i).io.lsq.forward <> lsq.io.forward(i)
    loadUnits(i).io.sbuffer <> sbuffer.io.forward(i)
    loadUnits(i).io.ubuffer <> uncache.io.forward(i)
    loadUnits(i).io.tl_d_channel := dcache.io.lsu.forward_D(i)
    loadUnits(i).io.forward_mshr <> dcache.io.lsu.forward_mshr(i)
    // ld-ld violation check
    loadUnits(i).io.lsq.ldld_nuke_query <> lsq.io.ldu.ldld_nuke_query(i)
    loadUnits(i).io.lsq.stld_nuke_query <> lsq.io.ldu.stld_nuke_query(i)
    // loadqueue old ptr
    loadUnits(i).io.lsq.lqDeqPtr := lsq.io.lqDeqPtr
    loadUnits(i).io.csrCtrl       <> csrCtrl
    // dtlb
    loadUnits(i).io.tlb <> dtlb_reqs.take(LduCnt)(i)
~~~

外层针对每个 `i` 的循环在 elaboration 时复制这组端口连接。`<>` 会按照 Bundle 字段的 `Input`/`Output` 方向同时连好 `valid`、`ready`、`bits` 等字段，它不是一个仲裁器，也不会自动插入寄存器；例如 `loadUnits(i).io.sbuffer <> sbuffer.io.forward(i)` 是该 LoadUnit 与 SBuffer 前递端口的直接接口连接。`take(LduCnt)(i)` 是 Scala 的集合裁剪和索引，确保只把前 LduCnt 个 DTLB 请求端口接给标量 LoadUnit。`:=` 在 `tl_d_channel`、`lqDeqPtr` 等位置只规定一个驱动方向，因此这段代码把 Load 的 SQ、SBuffer、Uncache、DCache、RAR/RAW 和 DTLB 接口静态装配在一起。

**代码依据：xiangshan/mem/lsqueue/LSQWrapper.scala:181-201**

~~~scala
  // store queue wiring
  storeQueue.io.brqRedirect <> io.brqRedirect
  storeQueue.io.vecFeedback   <> io.stvecFeedback
  storeQueue.io.storeAddrIn <> io.sta.storeAddrIn // from store_s1
  storeQueue.io.storeAddrInRe <> io.sta.storeAddrInRe // from store_s2
  storeQueue.io.storeDataIn <> io.std.storeDataIn // from store_s0
  storeQueue.io.storeMaskIn <> io.sta.storeMaskIn // from store_s0
  storeQueue.io.sbuffer     <> io.sbuffer
  storeQueue.io.mmioStout   <> io.mmioStout
  storeQueue.io.cboZeroStout <> io.cboZeroStout
  storeQueue.io.vecmmioStout <> io.vecmmioStout
  storeQueue.io.rob         <> io.rob
~~~

这段 `<>` 连接把 StoreQueue 的输入按职责拆开：`storeAddrIn`、`storeAddrInRe` 来自 Sta 的不同阶段，`storeDataIn` 来自 Std，`storeMaskIn` 又来自 Sta。它们是四组独立的 Bundle 接口，而不是把地址和数据打包成一次必须同时到达的写入。源码注释标出 `store_s1`、`store_s2`、`store_s0`，所以后续 StoreQueue 使用 `addrvalid` 与 `datavalid` 分别记录到达情况；地址和数据可跨周期独立到达。

## 14.3 LSQ：位置分配、子队列和恢复

### 14.3.1 LoadQueue 的真实组成

**代码依据：xiangshan/mem/lsqueue/LoadQueue.scala:214-258**

~~~scala
  val loadQueueRAR = Module(new LoadQueueRAR)  //  read-after-read violation
  val loadQueueRAW = Module(new LoadQueueRAW)  //  read-after-write violation
  val loadQueueReplay = Module(new LoadQueueReplay)  //  enqueue if need replay
  val virtualLoadQueue = Module(new VirtualLoadQueue)  //  control state
  val exceptionBuffer = Module(new LqExceptionBuffer) // exception buffer
  val uncacheBuffer = Module(new LoadQueueUncache) // uncache

  loadQueueRAR.io.redirect  <> io.redirect
  loadQueueRAR.io.release   <> io.release
  loadQueueRAR.io.ldWbPtr   <> virtualLoadQueue.io.ldWbPtr

  loadQueueRAW.io.redirect         <> io.redirect
  loadQueueRAW.io.storeIn          <> io.sta.storeAddrIn
  loadQueueRAW.io.stAddrReadySqPtr <> io.sq.stAddrReadySqPtr
  loadQueueRAW.io.stIssuePtr       <> io.sq.stIssuePtr

  virtualLoadQueue.io.redirect      <> io.redirect
  virtualLoadQueue.io.enq           <> io.enq
  virtualLoadQueue.io.ldin          <> io.ldu.ldin // from load_s3
~~~

这不是一个普通 FIFO 的单一读写接口，而是多个 `Module` 的组合。`virtualLoadQueue.io.enq <> io.enq` 将分配请求交给 VLQ，`virtualLoadQueue.io.ldin <> io.ldu.ldin` 接入 Load S3 的生命周期结果；`loadQueueRAW.io.storeIn <> io.sta.storeAddrIn` 把 Store 地址事件送往 RAW 检查；RAR 则接收 `release`。`<>` 的存在还表示这些子模块可以各自保留 `ready` 回压，而父模块只负责把端口边界拼接起来。

### 14.3.2 lqIdx 和 sqIdx 如何同时分配

**代码依据：xiangshan/mem/lsqueue/LSQWrapper.scala:155-184**

~~~scala
  io.enq.canAccept := loadQueue.io.enq.canAccept && storeQueue.io.enq.canAccept
  io.lqCanAccept := loadQueue.io.enq.canAccept
  io.sqCanAccept := storeQueue.io.enq.canAccept
  loadQueue.io.enq.sqCanAccept := storeQueue.io.enq.canAccept
  storeQueue.io.enq.lqCanAccept := loadQueue.io.enq.canAccept

  for (i <- io.enq.req.indices) {
    loadQueue.io.enq.needAlloc(i)      := io.enq.needAlloc(i)(0)
    loadQueue.io.enq.req(i).valid      := io.enq.needAlloc(i)(0) && io.enq.req(i).valid
    loadQueue.io.enq.req(i).bits       := io.enq.req(i).bits
    loadQueue.io.enq.req(i).bits.sqIdx := storeQueue.io.enq.resp(i)

    storeQueue.io.enq.needAlloc(i)      := io.enq.needAlloc(i)(1)
    storeQueue.io.enq.req(i).valid      := io.enq.needAlloc(i)(1) && io.enq.req(i).valid
    storeQueue.io.enq.req(i).bits       := io.enq.req(i).bits
    storeQueue.io.enq.req(i).bits.lqIdx := loadQueue.io.enq.resp(i)

    io.enq.resp(i).lqIdx := loadQueue.io.enq.resp(i)
    io.enq.resp(i).sqIdx := storeQueue.io.enq.resp(i)
  }
~~~

`io.enq.canAccept` 的 `&&` 是一颗组合 AND 门：任一队列满时，总入队在同拍就不可接受。循环中的 `needAlloc(i)(0)` 与 `(1)` 是同一派发请求中 Load/SQ 分配位的硬件位选择；每个 i 都有自己的一组赋值。对 Load 请求，`storeQueue.io.enq.resp(i)` 被写入 `bits.sqIdx`；对 Store 请求，`loadQueue.io.enq.resp(i)` 被写入 `bits.lqIdx`。因此同一派发槽的 uop 在发出前已经携带另一队列预分配的索引，而最后两行再把这对结果返回总接口。

**代码依据：xiangshan/mem/lsqueue/LSQWrapper.scala:378-413**

~~~scala
  val t1_redirect = RegNext(io.redirect.valid)
  val t2_redirect = RegNext(t1_redirect)
  val t2_update = t2_redirect && !VecInit(io.enq.needAlloc.map(_.orR)).asUInt.orR
  val t3_update = RegNext(t2_update)
  val t3_lqCancelCnt = GatedRegNext(io.lqCancelCnt)
  val t3_sqCancelCnt = GatedRegNext(io.sqCancelCnt)
  when (t3_update) {
    lqPtr := lqPtr - t3_lqCancelCnt
    lqCounter := lqCounter + io.lcommit + t3_lqCancelCnt
    sqPtr := sqPtr - t3_sqCancelCnt
    sqCounter := sqCounter + io.scommit + t3_sqCancelCnt
  }.elsewhen (!io.redirect.valid && io.enq.canAccept) {
    lqPtr := lqPtr + lqAllocNumber
    lqCounter := lqCounter + io.lcommit - lqAllocNumber
    sqPtr := sqPtr + sqAllocNumber
    sqCounter := sqCounter + io.scommit - sqAllocNumber
  }.otherwise {
    lqCounter := lqCounter + io.lcommit
    sqCounter := sqCounter + io.scommit
  }

  io.enq.canAccept := RegNext(ldCanAccept && sqCanAccept && !t2_update)
~~~

`t1_redirect`、`t2_redirect`、`t3_update` 是连续的 `RegNext` 级，因此 redirect 的观察和实际指针修正被明确延后多个时钟边界；这不是 Scala 变量在同一个周期被反复改写。`GatedRegNext(io.lqCancelCnt)`、`GatedRegNext(io.sqCancelCnt)` 提供两个带保持行为的跨拍操作数，本片段直接证明它们在 `t3_update` 分支中与 commit 数量一起参与运算；取消计数的产生端及其完整对齐关系需继续追踪上游逻辑。`when / elsewhen / otherwise` 生成优先级选择：恢复分支优先，正常分配分支只在没有 redirect 且可接收时采用，其余情况下只处理 commit。右侧的 `lqPtr`、`lqCounter` 是更新前的寄存器值，新的结果在时钟边界写回。最后 `io.enq.canAccept := RegNext(ldCanAccept && sqCanAccept && !t2_update)` 直接表明接收许可也延后一拍，且该许可计算包含 `!t2_update` 条件。

## 14.4 Store：状态、前递、提交和 SBuffer

### 14.4.1 StoreQueue 保存哪些状态

**代码依据：xiangshan/mem/lsqueue/StoreQueue.scala:254-272**

~~~scala
  val allocated = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // sq entry has been allocated
  val completed = RegInit(VecInit(List.fill(StoreQueueSize)(false.B)))
  val addrvalid = RegInit(VecInit(List.fill(StoreQueueSize)(false.B)))
  val datavalid = RegInit(VecInit(List.fill(StoreQueueSize)(false.B)))
  val allvalid  = VecInit((0 until StoreQueueSize).map(i => addrvalid(i) && datavalid(i)))
  val committed = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // inst has been committed by rob
  val unaligned = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // unaligned store
  val cross16Byte = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // unaligned cross 16Byte boundary
  val pending = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // mmio pending: inst is an mmio inst, it will not be executed until it reachs the end of rob
  val nc = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // nc: inst is a nc inst
  val mmio = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // mmio: inst is an mmio inst
  val memBackTypeMM = RegInit(VecInit(List.fill(StoreQueueSize)(false.B)))
  val isVec = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // vector store instruction
  val hasException = RegInit(VecInit(List.fill(StoreQueueSize)(false.B))) // store has exception, should deq but not write sbuffer
~~~

`List.fill(StoreQueueSize)(false.B)` 先构造 Scala 列表；`VecInit` 把它变成硬件 Bool 向量；最外层 `RegInit` 才使每个 entry 都具有复位为 0 的寄存器状态。因此 `allocated(i)`、`addrvalid(i)`、`datavalid(i)`、`committed(i)` 等是独立保存的队列位。与之对照，`allvalid` 只有 `VecInit(addrvalid(i) && datavalid(i))`，没有 `RegInit`：它是由当前两组寄存器即时计算出的组合向量。这样地址和数据无论先后到达，只要两位最终都为 1，`allvalid` 就在该拍为 1。

### 14.4.2 SQ 前递何时拒绝旧 DCache 数据

**代码依据：xiangshan/mem/lsqueue/StoreQueue.scala:660-732**

~~~scala
    val addrValidVec = WireInit(VecInit((0 until StoreQueueSize).map(j => addrvalid(j) && allocated(j))))
    val dataValidVec = WireInit(VecInit((0 until StoreQueueSize).map(j => datavalid(j))))
    val allValidVec  = WireInit(VecInit((0 until StoreQueueSize).map(j => addrvalid(j) && datavalid(j) && allocated(j))))

    val forwardMask1 = Mux(differentFlag, ~deqMask, deqMask ^ forwardMask)
    val forwardMask2 = Mux(differentFlag, forwardMask, 0.U(StoreQueueSize.W))
    val canForward1 = forwardMask1 & allValidVec.asUInt
    val canForward2 = forwardMask2 & allValidVec.asUInt

    // do real fwd query (cam lookup in load_s1)
    dataModule.io.needForward(i)(0) := canForward1 & vaddrModule.io.forwardMmask(i).asUInt
    dataModule.io.needForward(i)(1) := canForward2 & vaddrModule.io.forwardMmask(i).asUInt

    vaddrModule.io.forwardMdata(i) := io.forward(i).vaddr
    vaddrModule.io.forwardDataMask(i) := io.forward(i).mask
    paddrModule.io.forwardMdata(i) := io.forward(i).paddr
    paddrModule.io.forwardDataMask(i) := io.forward(i).mask

    io.forward(i).forwardMaskFast := dataModule.io.forwardMaskFast(i)
    io.forward(i).forwardMask := dataModule.io.forwardMask(i)
    io.forward(i).forwardData := dataModule.io.forwardData(i)

    val dataInvalidMask1 = ((addrValidVec.asUInt & ~dataValidVec.asUInt & vaddrModule.io.forwardMmask(i).asUInt) | unaligned.asUInt & allocated.asUInt) & forwardMask1.asUInt
    val dataInvalidMask2 = ((addrValidVec.asUInt & ~dataValidVec.asUInt & vaddrModule.io.forwardMmask(i).asUInt) | unaligned.asUInt & allocated.asUInt) & forwardMask2.asUInt
    val dataInvalidMask = dataInvalidMask1 | dataInvalidMask2
    io.forward(i).dataInvalidFast := dataInvalidMask.orR
~~~

`WireInit(VecInit(...))` 在这里形成组合位图：每一位对应一个 SQ entry，并不保存历史值。`forwardMask1/2` 先按环形队列边界选择待查范围，`canForward1/2` 再把范围与 `allValidVec` 按位相与；因此某一位能参与数据前递必须同时有地址、数据和 allocated。`dataInvalidMask1/2` 则故意改用 `addrValidVec & ~dataValidVec`，表达“更老地址已知、但数据尚未到”的危险情形。末尾 `orR` 是对整个位图的归约 OR，只要任一 entry 危险就给出一个 Bool。该前递接口据此向 LoadUnit 标识数据尚未就绪，后续的 s2_fwd_fail 与 Replay 处理见 14.5.3 和 14.6。

### 14.4.3 committed 与 SBuffer 的边界

**代码依据：xiangshan/mem/lsqueue/StoreQueue.scala:1133-1162**

~~~scala
  for (i <- 0 until CommitWidth) {
    // don't mark misalign store as committed
    val ptr = cmtPtrExt(i).value
    val isCommit = WireInit(false.B)
    when (
      allocated(ptr) &&
      isNotAfter(uop(ptr).robIdx, GatedRegNext(io.rob.pendingPtr)) &&
      !needCancel(ptr) &&
      (!waitStoreS2(ptr) || isVec(ptr))) {
      if (i == 0){
        // TODO: fixme for vector mmio
        when ((mmioState === s_idle) || (mmioState === s_wait && scommit > 0.U)){
          when ((isVec(ptr) && vecMbCommit(ptr)) || !isVec(ptr)) {
            isCommit := true.B
            committed(ptr) := true.B
            commitVec(0) := true.B
          }
        }
      } else {
        when ((isVec(ptr) && vecMbCommit(ptr)) || !isVec(ptr)) {
          isCommit := commitVec(i - 1) || committed(ptr)
          committed(ptr) := commitVec(i - 1) || committed(ptr)
          commitVec(i) := commitVec(i - 1)
        }
      }
    }
    when(isCommit && nc(ptr) && hasException(ptr)) {
      completed(ptr) := true.B
    }
  }
~~~

这个 `for (i <- 0 until CommitWidth)` 在生成期展开为每个 ROB 提交槽的一段硬件。`if (i == 0)` 是 Scala 常量判断，所以 i=0 与 i>0 会生成不同逻辑，而非运行时对 i 比较。每一段先将 `isCommit` 组合默认置 0，随后在 allocated、ROB 年龄、未取消、Store S2 就绪等条件同时满足时把 `committed(ptr)` 写 1；第二个 `when` 又处理带异常的 NC Store 完成位。此处只更新 SQ 的 `committed`/`completed` 寄存器，并未产生 DCache 请求。`isNotAfter` 用 ROB pendingPtr 形成按序边界，`needCancel` 排除被冲刷的 uop；因此 `committed` 不能解释为“已经写入 DCache”。

**代码依据：xiangshan/mem/lsqueue/StoreQueue.scala:1206-1224**

~~~scala
    when(firstWithMisalign && firstWithCross16Byte) {
      dataBuffer.io.enq(i).valid := misalignToDataBufferValid
      assert_flag := dataBuffer.io.enq(1).valid
    }.otherwise {
      dataBuffer.io.enq(i).valid := (
        allocated(ptr) && committed(ptr)
          && ((!isVec(ptr) && (allvalid(ptr) || hasException(ptr))) || vecMbCommit(ptr))
          && !mmioStall && !ncStall
          && (!unaligned(ptr) || !cross16Byte(ptr) && (allvalid(ptr) || hasException(ptr)))
        )
    }
~~~

这段 `when(...).otherwise(...)` 是两个互斥的硬件条件分支。普通分支把许多 Bool 用 `&&` 合成 `dataBuffer.io.enq(i).valid`：entry 必须已分配且提交，标量 Store 必须 `allvalid` 或带异常，向量 Store 则走 `vecMbCommit` 条件；`!mmioStall && !ncStall` 直接阻止 NC/MMIO 占用这条普通入 SBuffer 通路。`valid` 的产生仍只是请求声明，真正入队还要由 dataBuffer 的 `ready` 与它握手。

**代码依据：xiangshan/mem/lsqueue/StoreQueue.scala:1327-1346**

~~~scala
  for (i <- 0 until EnsbufferWidth) {
    io.sbuffer(i).valid := dataBuffer.io.deq(i).valid
    dataBuffer.io.deq(i).ready := io.sbuffer(i).ready
    io.sbuffer(i).bits.fromDataBufferEntry(dataBuffer.io.deq(i).bits, MemoryOpConstants.M_XWR)
    // io.sbuffer(i).fire is RegNexted, as sbuffer data write takes 2 cycles.
    // Before data write finish, sbuffer is unable to provide store to load
    // forward data. As an workaround, deqPtrExt and allocated flag update
    // is delayed so that load can get the right data from store queue.
    // ---
    // Only sqNeedDeq can move the ptr.
    // ---
    // however, `completed` is register, when it turn true, the data has already been written to sbuffer
    // Besides, we should not have cbozero completed. (wline is currently only for cbozero)
    val ptr = dataBuffer.io.deq(i).bits.sqPtr.value
    when (io.sbuffer(i).fire && io.sbuffer(i).bits.sqNeedDeq && !io.sbuffer(i).bits.wline) {
      completed(ptr) := true.B
    }
    XSDebug(RegNext(io.sbuffer(i).fire && io.sbuffer(i).bits.sqNeedDeq), "sbuffer "+i+" fire: ptr %d\n", ptr)
  }
~~~

这里 `io.sbuffer(i)` 和 `dataBuffer.io.deq(i)` 是一对 Decoupled 接口：前两行把 producer 的 `valid` 与 consumer 的 `ready` 对接，故 `io.sbuffer(i).fire` 等价于同拍两者均为真。`fromDataBufferEntry(...)` 是 Scala 辅助方法，在 elaboration 时把 DataBuffer entry 的字段连入 SBuffer 请求 `bits`；它不表示一次软件拷贝。`when (fire && sqNeedDeq && !wline)` 只有在 SBuffer 实际接受且该请求需要 SQ 出队时才写 `completed(ptr)`。源注释说明该数据写入存在跨拍完成窗口，因此 SQ 指针更新不能只按 `valid` 推断。

**代码依据：xiangshan/cache/dcache/DCacheWrapper.scala:401-423**

~~~scala
class DCacheWordReqWithVaddr(implicit p: Parameters) extends DCacheWordReq {
  val addr = UInt(PAddrBits.W)
  val wline = Bool()
}

class DCacheWordReqWithVaddrAndPfFlag(implicit p: Parameters) extends DCacheWordReqWithVaddr {
  val prefetch = Bool()
  val vecValid = Bool()
  val sqNeedDeq = Bool()

  def fromDataBufferEntry(src: DataBufferEntry, cmd: UInt) = {
    this := DontCare
    this := DontCare
    this.cmd := cmd
    this.addr := src.addr
    this.vaddr := src.vaddr
    this.data := src.data
    this.mask := src.mask
    this.wline := src.wline && src.vecValid
    this.prefetch := src.prefetch
    this.vecValid := src.vecValid
    this.sqNeedDeq := src.sqNeedDeq
  }
~~~

`DCacheWordReqWithVaddrAndPfFlag` 在继承的基础请求字段上增加 `prefetch`、`vecValid`、`sqNeedDeq`。方法中的 `this` 是当前 Chisel Bundle，两个 `DontCare` 为未列出字段提供默认驱动，后续 `:=` 再逐项覆盖命令、地址、数据和 mask。`wline := src.wline && src.vecValid` 不是简单复制：只有 vector-valid 的 line write 才保留 wline。故前一段的 `sqNeedDeq` 和 `wline` 条件可以追溯到 DataBuffer entry 的明确字段映射。

**代码依据：xiangshan/mem/sbuffer/Sbuffer.scala:425-469**

~~~scala
  def wordReqToBufLine( // allocate a new line in sbuffer
    req: DCacheWordReq,
    reqptag: UInt,
    reqvtag: UInt,
    insertIdx: UInt,
    insertVec: UInt,
    wordOffset: UInt
  ): Unit = {
    assert(UIntToOH(insertIdx) === insertVec)
    val sameBlockInflightMask = genSameBlockInflightMask(reqptag)
    (0 until StoreBufferSize).map(entryIdx => {
      when(insertVec(entryIdx)){
        stateVec(entryIdx).state_valid := true.B
        stateVec(entryIdx).w_sameblock_inflight := sameBlockInflightMask.orR // set w_sameblock_inflight when a line is first allocated
        when(sameBlockInflightMask.orR){
          waitInflightMask(entryIdx) := sameBlockInflightMask
        }
        cohCount(entryIdx) := 0.U
        // missqReplayCount(insertIdx) := 0.U
        ptag(entryIdx) := reqptag
        vtag(entryIdx) := reqvtag // update vtag if a new sbuffer line is allocated
      }
    })
  }

  def mergeWordReq( // merge write req into an existing line
    req: DCacheWordReq,
    reqptag: UInt,
    reqvtag: UInt,
    mergeIdx: UInt,
    mergeVec: UInt,
    wordOffset: UInt
  ): Unit = {
    assert(UIntToOH(mergeIdx) === mergeVec)
    (0 until StoreBufferSize).map(entryIdx => {
      when(mergeVec(entryIdx)) {
        cohCount(entryIdx) := 0.U
        // missqReplayCount(entryIdx) := 0.U
        // check if vtag is the same, if not, trigger sbuffer flush
        when(reqvtag =/= vtag(entryIdx)) {
          merge_need_uarch_drain := true.B
        }
      }
    })
  }
~~~

这两个 `def` 是 Scala 辅助函数：调用它们时会在当前 Chisel 条件作用域中发射硬件连接，并不会在芯片运行时进行函数调用。`wordReqToBufLine` 中的 `(0 until StoreBufferSize).map` 展开为每个 SBuffer entry 的 `when(insertVec(entryIdx))`；只有被 one-hot `insertVec` 指中的 entry 被写 `state_valid`、ptag、vtag 等字段。`sameBlockInflightMask` 是组合匹配结果，若有同块在飞请求就写入 `waitInflightMask`。`mergeWordReq` 相似地用 `mergeVec` 展开选择，并在 vtag 不同的实际合并情形下置 `merge_need_uarch_drain`。两个 `assert(UIntToOH(idx) === vec)` 把索引和 one-hot 向量的一致性作为硬件断言。

**代码依据：xiangshan/mem/sbuffer/Sbuffer.scala:471-496**

~~~scala
  for(((in, vwordOffset), i) <- io.in.zip(Seq(firstWord, secondWord)).zipWithIndex){
    writeReq(i).valid := in.fire && in.bits.vecValid
    writeReq(i).bits.vwordOffset := vwordOffset
    writeReq(i).bits.mask := in.bits.mask
    writeReq(i).bits.data := in.bits.data
    writeReq(i).bits.wline := in.bits.wline
    val debug_insertIdx = if(i == 0) firstInsertIdx else secondInsertIdx
    val insertVec = if(i == 0) firstInsertVec else secondInsertVec
    assert(!((PopCount(insertVec) > 1.U) && in.fire && in.bits.vecValid))
    val insertIdx = OHToUInt(insertVec)
    val accessValid = in.fire && in.bits.vecValid
    accessIdx(i).valid := RegNext(accessValid)
    accessIdx(i).bits := RegEnable(Mux(canMerge(i), mergeIdx(i), insertIdx), accessValid)

    XSDebug(accessValid && canMerge(i), p"merge req $i to line [${mergeIdx(i)}]\n")
    XSDebug(accessValid && !canMerge(i), p"insert req $i to line[$insertIdx]\n")
    when(accessValid){
      when(canMerge(i)){
        writeReq(i).bits.wvec := mergeVec(i)
        mergeWordReq(in.bits, inptags(i), invtags(i), mergeIdx(i), mergeVec(i), vwordOffset)
      }.otherwise({
        writeReq(i).bits.wvec := insertVec
        wordReqToBufLine(in.bits, inptags(i), invtags(i), insertIdx, insertVec, vwordOffset)
        assert(debug_insertIdx === insertIdx)
      })
    }
~~~

`for (((in, vwordOffset), i) <- ...)` 是 Scala 的 zip/索引展开，因此两路 SBuffer 入队端口各生成一套逻辑。`accessValid` 是 `in.fire && in.bits.vecValid` 的组合握手条件；`RegNext(accessValid)` 和 `RegEnable(Mux(...), accessValid)` 分别把该次访问的有效位与所选索引带到下一拍。`Mux(canMerge(i), mergeIdx(i), insertIdx)` 只选择索引，实际写向由下面的硬件 `when(canMerge(i))` 决定。虽然 `mergeWordReq`/`wordReqToBufLine` 是 Scala 函数，它们位于 `when` 块中所发出的连接会带上相应条件。因此 SBuffer 的“同 line 合并”来自实际的 `canMerge` 硬件分支，而非只由函数名推断。

更细地看，`assert(!((PopCount(insertVec) > 1.U) && in.fire && in.bits.vecValid))` 约束一个实际入队周期不会选择多个新 entry；`OHToUInt(insertVec)` 才安全地把 one-hot 写指针转成二进制 `insertIdx`。`RegEnable(..., accessValid)` 在 accessValid 为假时保持旧 `accessIdx.bits`，因此该 bits 本身不能当作新请求；消费者必须同时看下一拍的 `accessIdx.valid`，它由 `RegNext(accessValid)` 标记这份索引是否属于上一拍真实握手。

## 14.5 Load：多入口、前递和恢复

### 14.5.1 S0 仲裁的全部来源

**代码依据：xiangshan/mem/pipeline/LoadUnit.scala:290-335**

~~~scala
  // load flow select/gen
  // src 0: misalignBuffer load (io.misalign_ldin)
  // src 1: super load replayed by LSQ (cache miss replay) (io.replay)
  // src 2: fast load replay (io.fast_rep_in)
  // src 3: mmio (io.lsq.uncache)
  // src 4: nc (io.lsq.nc_ldin)
  // src 5: load replayed by LSQ (io.replay)
  // src 6: hardware prefetch from prefetchor (high confidence) (io.prefetch)
  // src 7: vec read from RS (io.vecldin)
  // src 8: int read / software prefetch first issue from RS (io.in)
  // src 9: load try pointchaising when no issued or replayed load (io.fastpath)
  // src10: hardware prefetch from prefetchor (high confidence) (io.prefetch)
  // priority: high to low
  val s0_rep_stall           = io.ldin.valid && isAfter(io.replay.bits.uop.lqIdx, io.ldin.bits.uop.lqIdx) ||
                               io.vecldin.valid && isAfter(io.replay.bits.uop.lqIdx, io.vecldin.bits.uop.lqIdx)
  private val SRC_NUM = 11
  private val Seq(
    mab_idx, super_rep_idx, fast_rep_idx, lsq_rep_idx, high_pf_idx,
    vec_iss_idx, int_iss_idx, mmio_idx, nc_idx, l2l_fwd_idx, low_pf_idx
  ) = (0 until SRC_NUM).toSeq
  val s0_src_valid_vec = WireInit(VecInit(Seq(
    io.misalign_ldin.valid,
    io.replay.valid && io.replay.bits.forward_tlDchannel,
    io.fast_rep_in.valid,
    io.replay.valid && !io.replay.bits.forward_tlDchannel && !s0_rep_stall,
    io.prefetch_req.valid && io.prefetch_req.bits.confidence > 0.U,
    io.vecldin.valid,
    io.ldin.valid, // int flow first issue or software prefetch
    io.lsq.uncache.valid,
    io.lsq.nc_ldin.valid,
    io.l2l_fwd_in.valid,
    io.prefetch_req.valid && io.prefetch_req.bits.confidence === 0.U,
  )))
  val s0_src_ready_vec = Wire(Vec(SRC_NUM, Bool()))
  s0_src_ready_vec(0) := true.B
  for(i <- 1 until SRC_NUM){
    s0_src_ready_vec(i) := !s0_src_valid_vec.take(i).reduce(_ || _)
  }
  // load flow source select (OH)
  val s0_src_select_vec = WireInit(VecInit((0 until SRC_NUM).map{i => s0_src_valid_vec(i) && s0_src_ready_vec(i)}))
~~~

`private val Seq(...) = (0 until SRC_NUM).toSeq` 是 Scala 模式解构：这些 `*_idx` 都是生成期整数，不是硬件寄存器。`s0_src_valid_vec` 才是 11 位硬件 `Vec[Bool]`，其中每一项对一个候选源的当前有效条件编码。`s0_src_ready_vec(0) := true.B` 无条件允许最高优先级；对 i>0，`take(i).reduce(_ || _)` 生成所有更高优先级 valid 的 OR，再取反得到“前面都没有请求”。最后 `s0_src_select_vec(i)` 将本路 valid 与其 ready 相与，因此有候选时只会选最先出现的有效源。这里 `for`、`take`、`reduce` 都在 elaboration 时构造固定优先级网络；首次发射、队列 Replay、Fast Replay、NC、MMIO、向量和预取共享 S0，但不是依次由软件检查。

代码上方的旧 `src N` 注释不能替代实际硬件数组顺序；应以命名索引解构和 `s0_src_valid_vec` 的 `Seq(...)` 为准：

| 索引 | 当前 `s0_src_valid_vec` 条目 | 读法 |
| --- | --- | --- |
| 0 | `io.misalign_ldin.valid` | 非对齐缓冲返回的 Load。 |
| 1 / 2 | `io.replay` 的 forward-TL-D 路径 / `io.fast_rep_in` | 高优先级 replay 输入。 |
| 3 | `io.replay` 的非 forward-TL-D 路径 | 普通 LSQ replay。 |
| 4 | `prefetch_req.confidence > 0.U` | 高置信预取。 |
| 5 / 6 | `io.vecldin.valid` / `io.ldin.valid` | 向量发射 / 标量首次发射或软件预取。 |
| 7 / 8 | `io.lsq.uncache.valid` / `io.lsq.nc_ldin.valid` | MMIO/uncache 与 NC 输入。 |
| 9 / 10 | `io.l2l_fwd_in.valid` / 低置信预取 | L2L forward 与低置信预取。 |

### 14.5.2 S1 同时发起三种前递查询

**代码依据：xiangshan/mem/pipeline/LoadUnit.scala:962-986**

~~~scala
  io.sbuffer.valid := s1_valid && !(s1_exception || s1_tlb_miss || s1_kill || s1_dly_err || s1_prf)
  io.sbuffer.vaddr := s1_vaddr
  io.sbuffer.paddr := s1_paddr_dup_lsu
  io.sbuffer.uop   := s1_in.uop
  io.sbuffer.sqIdx := s1_in.uop.sqIdx
  io.sbuffer.mask  := s1_in.mask

  io.ubuffer.valid := s1_valid && s1_nc_with_data && !(s1_exception || s1_tlb_miss || s1_kill || s1_dly_err || s1_prf)
  io.ubuffer.vaddr := s1_vaddr
  io.ubuffer.paddr := s1_paddr_dup_lsu
  io.ubuffer.uop   := s1_in.uop
  io.ubuffer.sqIdx := s1_in.uop.sqIdx
  io.ubuffer.mask  := s1_in.mask

  io.lsq.forward.valid     := s1_valid && !(s1_exception || s1_tlb_miss || s1_kill || s1_dly_err || s1_prf)
  io.lsq.forward.vaddr     := s1_vaddr
  io.lsq.forward.paddr     := s1_paddr_dup_lsu
  io.lsq.forward.uop       := s1_in.uop
  io.lsq.forward.sqIdx     := s1_in.uop.sqIdx
  io.lsq.forward.sqIdxMask := 0.U
  io.lsq.forward.mask      := s1_in.mask
~~~

三组 `:=` 在同一 S1 并行生成 SQ、SBuffer 和 UBuffer 查询；它们的文本顺序不表示查询先后。SQ/SBuffer 两路 `valid` 都要求 `s1_valid`，并同时排除异常、TLB miss、kill、延迟错误和预取；这使一个已经不能正常完成的 Load 不会继续向前递端口发请求。三组接口都携带 vaddr、paddr、uop、sqIdx 和 byte mask，让被查询方能按 Store 年龄和访问字节判断。UBuffer 的 `valid` 额外与 `s1_nc_with_data` 相与，故它不是所有 Load 的通用第三路前递查询。

**代码依据：xiangshan/mem/pipeline/LoadUnit.scala:1014-1023**

~~~scala
  s1_out.paddr             := s1_paddr_dup_lsu
  s1_out.gpaddr            := s1_gpaddr_dup_lsu
  s1_out.isForVSnonLeafPTE := io.tlb.resp.bits.isForVSnonLeafPTE
  s1_out.tlbMiss           := s1_tlb_miss
  s1_out.ptwBack           := io.tlb.resp.bits.ptwBack
  s1_out.rep_info.debug    := s1_in.uop.debugInfo
  s1_out.rep_info.nuke     := s1_nuke && !s1_sw_prf
  s1_out.delayedLoadError  := s1_dly_err
  s1_out.nc := (s1_nc || Pbmt.isNC(s1_pbmt)) && !s1_prf
  s1_out.mmio := Pbmt.isIO(s1_pbmt)
~~~

这一段把翻译和属性结果写入 S1 输出 Bundle。前五行传递物理地址、guest 物理地址、二阶段页表信息、TLB miss 和 PTW 回传；`rep_info.debug`、`rep_info.nuke` 则附着本次 Load 的调试/恢复信息。关键在最后两行：`s1_out.nc := (s1_nc || Pbmt.isNC(s1_pbmt)) && !s1_prf` 将内部 NC 条件和 PBMT 的 NC 条件合并，同时排除预取；`s1_out.mmio := Pbmt.isIO(s1_pbmt)` 单独取 I/O 属性。Pbmt.isNC 和 Pbmt.isIO 是不同谓词，当前代码因此显式区分 NC 和 MMIO，而不是用一个“不可缓存”状态覆盖两者。

### 14.5.3 S2 的 byte-lane 合并和 replay 信息

**代码依据：xiangshan/mem/pipeline/LoadUnit.scala:1387-1447**

~~~scala
  // merge forward result
  // lsq has higher priority than sbuffer
  val s2_fwd_mask = Wire(Vec((VLEN/8), Bool()))
  val s2_fwd_data = Wire(Vec((VLEN/8), UInt(8.W)))
  s2_full_fwd := ((~s2_fwd_mask.asUInt).asUInt & s2_in.mask) === 0.U && !io.lsq.forward.dataInvalid
  // generate XLEN/8 Muxs
  for (i <- 0 until VLEN / 8) {
    s2_fwd_mask(i) := io.lsq.forward.forwardMask(i) || io.sbuffer.forwardMask(i) || io.ubuffer.forwardMask(i)
    s2_fwd_data(i) :=
      Mux(io.lsq.forward.forwardMask(i), io.lsq.forward.forwardData(i),
      Mux(s2_nc_with_data, io.ubuffer.forwardData(i),
      io.sbuffer.forwardData(i)))
  }

  s2_out.rep_info.mem_amb         := s2_mem_amb && s2_troublem
  s2_out.rep_info.tlb_miss        := s2_tlb_miss && s2_troublem
  s2_out.rep_info.fwd_fail        := s2_fwd_fail && s2_troublem
  s2_out.rep_info.dcache_rep      := s2_mq_nack && s2_troublem
  s2_out.rep_info.dcache_miss     := s2_dcache_miss && s2_troublem
  s2_out.rep_info.bank_conflict   := s2_bank_conflict && s2_troublem
  s2_out.rep_info.wpu_fail        := s2_wpu_pred_fail && s2_troublem
  s2_out.rep_info.rar_nack        := s2_rar_nack && s2_troublem
  s2_out.rep_info.raw_nack        := s2_raw_nack && s2_troublem
  s2_out.rep_info.nuke            := s2_nuke && s2_troublem
~~~

`Wire(Vec(VLEN / 8, Bool()))` 与 `Wire(Vec(VLEN / 8, UInt(8.W)))` 明确把前递结果拆成 byte lane；循环为每个 lane 生成独立逻辑。`s2_fwd_mask(i)` 用 OR 表示任一路已覆盖该 byte，`s2_fwd_data(i)` 是嵌套 Mux：SQ mask 命中时优先取 SQ 数据；否则若 `s2_nc_with_data` 为真取 UBuffer；否则取 SBuffer。`s2_full_fwd` 将 mask 向量转为 UInt 后与 Load mask 比较，用 `io.lsq.forward.dataInvalid` 再否决不完整的前递。注意 `s2_full_fwd` 写在 lane 赋值之前并不改变硬件时序，Chisel 仍把二者连成同一组合网络。下面的每个 `rep_info.* := cause && s2_troublem` 只编码当前失败原因；它本身不等价于一定 pipeline flush。

第二层数据 Mux 的条件是全局 `s2_nc_with_data`，不是 `io.ubuffer.forwardMask(i)`；因此对某个 lane，`s2_fwd_data(i)` 只有在 `s2_fwd_mask(i)` 表示已有来源时才应解释为有效前递数据。`(~s2_fwd_mask.asUInt & s2_in.mask) === 0.U` 的含义是“所有本次 Load 实际请求的 byte 都被任一路覆盖”，而不是要求 VLEN 范围内的每个 byte 都有数据。

### 14.5.4 S3 的 rollback 和依赖取消

**代码依据：xiangshan/mem/pipeline/LoadUnit.scala:1606-1714**

~~~scala
  val s3_ldld_rep_inst =
      io.lsq.ldld_nuke_query.resp.valid &&
      io.lsq.ldld_nuke_query.resp.bits.rep_frm_fetch &&
      GatedValidRegNext(io.csrCtrl.ldld_vio_check_enable)
  val s3_flushPipe = s3_ldld_rep_inst

  io.rollback.valid := s3_valid && (s3_rep_frm_fetch || s3_flushPipe || s3_frm_mis_flush) && !s3_exception
  io.rollback.bits             := DontCare
  io.rollback.bits.robIdx      := s3_out.bits.uop.robIdx
  io.rollback.bits.level       := Mux(s3_rep_frm_fetch || s3_frm_mis_flush, RedirectLevel.flush, RedirectLevel.flushAfter)
  io.rollback.bits.cfiUpdate.target := s3_out.bits.uop.pc

  val s3_revoke = s3_exception || io.lsq.ldin.bits.rep_info.need_rep || s3_mis_align || (s3_frm_mabuf && io.misalign_ldout.bits.rep_info.need_rep)
  io.lsq.ldld_nuke_query.revoke := s3_revoke
  io.lsq.stld_nuke_query.revoke := s3_revoke

  io.ldCancel.ld2Cancel := s3_valid && !s3_safe_wakeup && !s3_isvec
~~~

`s3_ldld_rep_inst` 把 RAR 响应、`rep_frm_fetch` 位和 CSR 开关三者相与；其中 `GatedValidRegNext` 的单 bit 实现见 14.1.1，会把 CSR Bool 延后一拍。`s3_flushPipe` 只是这个 Bool 的别名。`rollback.valid` 再用 `s3_valid` 和 `!s3_exception` 把恢复限制在有效、无异常的 S3 Load 上；`rollback.bits` 中只有 `robIdx`、PC 和 level 等关键字段被本段显式驱动。`Mux(s3_rep_frm_fetch || s3_frm_mis_flush, flush, flushAfter)` 是组合选择，不会自行产生 redirect；真正的 redirect 是否存在仍由 `rollback.valid` 决定。两个 `revoke := s3_revoke` 在同拍将同一撤销条件送给 RAR/RAW 查询，`ld2Cancel` 则只在不安全且非向量的 S3 Load 上发出；其如何作用于依赖者要看下面的 Bundle 组合逻辑。

**代码依据：xiangshan/backend/Bundles.scala:999-1005**

~~~scala
  object LoadShouldCancel {
    def apply(loadDependency: Option[Seq[UInt]], ldCancel: Seq[LoadCancelIO]): Bool = {
      val ld1Cancel = loadDependency.map(_.zip(ldCancel.map(_.ld1Cancel)).map { case (dep, cancel) => cancel && dep(0)}.reduce(_ || _))
      val ld2Cancel = loadDependency.map(_.zip(ldCancel.map(_.ld2Cancel)).map { case (dep, cancel) => cancel && dep(1)}.reduce(_ || _))
      ld1Cancel.map(_ || ld2Cancel.get).getOrElse(false.B)
    }
  }
~~~

`LoadShouldCancel.apply` 的 `Option.map`、`zip` 和 `getOrElse` 都是 Scala elaboration 结构：若调用者没有 `loadDependency`，只生成常量 `false.B`；若有依赖列表，则 `zip` 把第 k 个依赖位图与第 k 个 LoadCancelIO 配对。每一项的 `cancel && dep(0)` 或 `cancel && dep(1)` 是硬件 AND，`reduce(_ || _)` 把所有端口归约为 OR。于是本段的 `ld2Cancel` 只有在对应 `loadDependency` 的 bit 1 为真时才会影响最终 Bool；队列 replay 与 rollback 仍是另两种独立的恢复接口。

## 14.6 Replay：等待事件，而不是停住所有 Load

### 14.6.1 原因编码

**代码依据：xiangshan/mem/lsqueue/LoadQueueReplay.scala:38-74**

~~~scala
  // these causes have priority, lower coding has higher priority.
  // when load replay happens, load unit will select highest priority
  // from replay causes vector

  /*
   * Warning:
   * ************************************************************
   * * Don't change the priority. If the priority is changed,   *
   * * deadlock may occur. If you really need to change or      *
   * * add priority, please ensure that no deadlock will occur. *
   * ************************************************************
   *
   */
  // st-ld violation re-execute check
  val C_MA  = 0
  // tlb miss check
  val C_TM  = 1
  // store-to-load-forwarding check
  val C_FF  = 2
  // dcache replay check
  val C_DR  = 3
  // dcache miss check
  val C_DM  = 4
  // wpu predict fail
  val C_WF  = 5
  // dcache bank conflict check
  val C_BC  = 6
  // RAR queue accept check
  val C_RAR = 7
  // RAW queue accept check
  val C_RAW = 8
  // st-ld violation
  val C_NK  = 9
  // misalignBuffer Full
  val C_MF  = 10
  val allCauses = 11
~~~

这些 `val C_* = Int` 是 Scala 常量，用作后续原因向量的位索引，而不是芯片中保存原因号的寄存器。源码注释明确规定低编码优先级更高，因此改变一个数值会改变生成硬件访问原因位的含义。`allCauses = 11` 给出向量总宽度，保证 C_MA 到 C_MF 恰好覆盖 0 到 10；它们不是可随意调整的普通枚举。

**代码依据：xiangshan/mem/pipeline/LoadUnit.scala:1614-1631**

~~~scala
  val s3_lrq_rep_info = WireInit(s3_in.rep_info)
  s3_lrq_rep_info.misalign_nack := toMisalignBufferValid && !(io.misalign_enq.req.ready && s3_misalign_can_go)
  val s3_lrq_sel_rep_cause = PriorityEncoderOH(s3_lrq_rep_info.cause.asUInt)
  val s3_replayqueue_rep_cause = WireInit(0.U.asTypeOf(s3_in.rep_info.cause))

  val s3_mab_rep_info = WireInit(s3_in.rep_info)
  val s3_mab_sel_rep_cause = PriorityEncoderOH(s3_mab_rep_info.cause.asUInt)
  val s3_misalign_rep_cause = WireInit(0.U.asTypeOf(s3_in.rep_info.cause))

  s3_misalign_rep_cause := VecInit(s3_mab_sel_rep_cause.asBools)

  when (s3_rep_frm_fetch || s3_frm_mabuf) {
    s3_replayqueue_rep_cause := 0.U.asTypeOf(s3_lrq_rep_info.cause.cloneType)
  } .otherwise {
    s3_replayqueue_rep_cause := VecInit(s3_lrq_sel_rep_cause.asBools)

  }
  io.lsq.ldin.bits.rep_info.cause := s3_replayqueue_rep_cause
~~~

`WireInit(s3_in.rep_info)` 创建一个默认等于输入 replay 信息的可驱动硬件 Bundle；随后只覆写其中的 `misalign_nack` 字段。`cause.asUInt` 把 Bool Vec 打包成位图，`PriorityEncoderOH` 从多个同时为真的原因中产生一个 one-hot 原因。`VecInit(oneHot.asBools)` 又把 UInt 还原为 Bundle 字段需要的 Bool Vec，所以 ReplayQueue 收到的是单一选中的 cause，而非全部原始 cause。`when (s3_rep_frm_fetch || s3_frm_mabuf)` 会把送给 ReplayQueue 的原因向量清零，其他情况下才发送优先编码的结果；这解释了“低编码优先”如何落实到 LoadUnit 的实际输出路径。

**代码依据：xiangshan/mem/lsqueue/LoadQueueReplay.scala:275-370**

~~~scala
  val canEnqueue = io.enq.map(_.valid)
  val cancelEnq = io.enq.map(enq => enq.bits.uop.robIdx.needFlush(io.redirect))
  val needReplay = io.enq.map(enq => enq.bits.rep_info.need_rep)
  val loadReplay = io.enq.map(enq => enq.bits.isLoadReplay)
  val needEnqueue = VecInit((0 until LoadPipelineWidth).map(w => {
    canEnqueue(w) && !cancelEnq(w) && needReplay(w)
  }))

  (0 until LoadQueueReplaySize).map(i => {
    when (cause(i)(LoadReplayCauses.C_MA)) {
      blocking(i) := Mux(stAddrDeqVec(i), false.B, blocking(i))
    }
    when (cause(i)(LoadReplayCauses.C_TM)) {
      blocking(i) := Mux(io.tlb_hint.resp.valid &&
                     (io.tlb_hint.resp.bits.replay_all ||
                     io.tlb_hint.resp.bits.id === tlbHintId(i)), false.B, blocking(i))
    }
    when (cause(i)(LoadReplayCauses.C_FF)) {
      blocking(i) := Mux(stDataDeqVec(i), false.B, blocking(i))
    }
    when (cause(i)(LoadReplayCauses.C_DM)) {
      blocking(i) := Mux(io.tl_d_channel.valid && io.tl_d_channel.mshrid === missMSHRId(i), false.B, blocking(i))
    }
  })
~~~

`io.enq.map(_.valid)`、`map(enq => ...)` 分别在 Scala 集合中取出每条 Load pipeline 的硬件条件；`VecInit` 再把这些 Bool 组织为硬件向量。`needEnqueue(w)` 仅在输入有效、uop 未被 redirect 冲刷、且 `need_rep` 为真时成立。随后外层 `(0 until LoadQueueReplaySize).map` 为每个 Replay entry 展开一套条件更新：C_MA 看 Store 地址出队，C_FF 看 Store 数据出队，C_TM 比较 TLB hint，C_DM 比较 D 通道的 mshrId。`Mux(condition, false.B, blocking(i))` 表达“解除条件满足时清除 blocking，否则保持当前 blocking 值”，不是把整个 ReplayQueue 停住。

**代码依据：xiangshan/mem/pipeline/LoadUnit.scala:1319-1338**

~~~scala
  val s2_dcache_fast_rep = (s2_mq_nack || !s2_dcache_miss && (s2_bank_conflict || s2_wpu_pred_fail))
  val s2_nuke_fast_rep   = !s2_mq_nack &&
                           !s2_dcache_miss &&
                           !s2_bank_conflict &&
                           !s2_wpu_pred_fail &&
                           s2_nuke

  val s2_fast_rep = !s2_in.isFastReplay &&
                    !s2_mem_amb &&
                    !s2_tlb_miss &&
                    !s2_fwd_fail &&
                    (s2_dcache_fast_rep || s2_nuke_fast_rep) &&
                    s2_troublem
~~~

这三条 `val` 都是同拍组合 Bool。`s2_dcache_fast_rep` 把 MQ nack 与“不是 cache miss 但发生 bank conflict/way-predict fail”分开；`s2_nuke_fast_rep` 又排除前面几种 DCache 问题后只保留 nuke。最终 `s2_fast_rep` 先排除已经 Fast Replay 的请求、地址歧义、TLB miss 和前递失败，再要求两类快速重试之一与 `s2_troublem` 同时为真。括号在这里很重要：`!s2_dcache_miss && (s2_bank_conflict || s2_wpu_pred_fail)` 是一个整体条件，而非三个独立 replay 原因。

## 14.7 RAW、RAR 与最老 redirect

### 14.7.1 RAW 的入队和违规检测

**代码依据：xiangshan/mem/lsqueue/LoadQueueRAW.scala:115-165**

~~~scala
  val canEnqueue = io.query.map(_.req.valid)
  val cancelEnqueue = io.query.map(_.req.bits.uop.robIdx.needFlush(io.redirect))
  val allAddrCheck = io.stIssuePtr === io.stAddrReadySqPtr
  val hasAddrInvalidStore = io.query.map(_.req.bits.uop.sqIdx).map(sqIdx => {
    Mux(!allAddrCheck, isBefore(io.stAddrReadySqPtr, sqIdx), false.B)
  })
  val needEnqueue = canEnqueue.zip(hasAddrInvalidStore).zip(cancelEnqueue).map { case ((v, r), c) => v && r && !c }

  for ((enq, w) <- io.query.map(_.req).zipWithIndex) {
    acceptedVec(w) := false.B
    paddrModule.io.wen(w) := false.B
    maskModule.io.wen(w) := false.B
    freeList.io.doAllocate(w) := false.B
    freeList.io.allocateReq(w) := true.B
    val offset = PopCount(needEnqueue.take(w))
    val canAccept = freeList.io.canAllocate(offset)
    val enqIndex = freeList.io.allocateSlot(offset)
    enq.ready := Mux(needEnqueue(w), canAccept, true.B)
    when (needEnqueue(w) && enq.ready) {
      acceptedVec(w) := true.B
      freeList.io.doAllocate(w) := true.B
      allocated(enqIndex) := true.B
      paddrModule.io.wen(w) := true.B
      paddrModule.io.waddr(w) := enqIndex
      paddrModule.io.wdata(w) := genPartialPAddr(enq.bits.paddr)
~~~

`allAddrCheck` 比较两个 SQ 指针：相等时没有“已发射但地址尚未就绪”的区间，`hasAddrInvalidStore` 被 Mux 强制为 `false.B`。不相等时，`isBefore(stAddrReadySqPtr, sqIdx)` 为每个 Load 的 SQ 索引判断是否落在需要追踪的范围。三层 `zip(...).map` 在 elaboration 时把 canEnqueue、地址未知、取消条件逐项相与，得到每条请求的 `needEnqueue`。对第 w 条请求，`PopCount(needEnqueue.take(w))` 是硬件加法树，计算同拍更早请求已占用多少 free-list 槽；这使多路请求不会拿到同一 `allocateSlot`。`enq.ready := Mux(needEnqueue(w), canAccept, true.B)` 则表示不需 RAW entry 的请求不会被 free-list 容量阻塞。

**代码依据：xiangshan/mem/lsqueue/LoadQueueRAW.scala:246-287**

~~~scala
  def selectPartialOldest[T <: XSBundleWithMicroOp](valid: Seq[Bool], bits: Seq[T]): (Seq[Bool], Seq[T]) = {
    assert(valid.length == bits.length)
    if (valid.length == 0 || valid.length == 1) {
      (valid, bits)
    } else if (valid.length == 2) {
      val res = Seq.fill(2)(Wire(ValidIO(chiselTypeOf(bits(0)))))
      for (i <- res.indices) {
        res(i).valid := valid(i)
        res(i).bits := bits(i)
      }
      val oldest = Mux(valid(0) && valid(1), Mux(isAfter(bits(0).uop.robIdx, bits(1).uop.robIdx), res(1), res(0)), Mux(valid(0) && !valid(1), res(0), res(1)))
      (Seq(oldest.valid), Seq(oldest.bits))
    } else {
      val left = selectPartialOldest(valid.take(valid.length / 2), bits.take(bits.length / 2))
      val right = selectPartialOldest(valid.takeRight(valid.length - (valid.length / 2)), bits.takeRight(bits.length - (bits.length / 2)))
      selectPartialOldest(left._1 ++ right._1, left._2 ++ right._2)
    }
  }

  def selectOldest[T <: XSBundleWithMicroOp](valid: Seq[Bool], bits: Seq[T]): (Seq[Bool], Seq[T]) = {
    assert(valid.length == bits.length)
    val numSelectGroups = scala.math.ceil(valid.length.toFloat / SelectGroupSize).toInt

    // group info
    val selectValidGroups = valid.grouped(SelectGroupSize).toList
    val selectBitsGroups = bits.grouped(SelectGroupSize).toList
    // select logic
    if (valid.length <= SelectGroupSize) {
      val (selValid, selBits) = selectPartialOldest(valid, bits)
      val selValidNext = GatedValidRegNext(selValid(0))
      val selBitsNext = RegEnable(selBits(0), selValid(0))
      (Seq(selValidNext && !selBitsNext.uop.robIdx.needFlush(RegNext(io.redirect))), Seq(selBitsNext))
    } else {
      val select = (0 until numSelectGroups).map(g => {
        val (selValid, selBits) = selectPartialOldest(selectValidGroups(g), selectBitsGroups(g))
        val selValidNext = RegNext(selValid(0))
        val selBitsNext = RegEnable(selBits(0), selValid(0))
        (selValidNext && !selBitsNext.uop.robIdx.needFlush(io.redirect) && !selBitsNext.uop.robIdx.needFlush(RegNext(io.redirect)), selBitsNext)
      })
      selectOldest(select.map(_._1), select.map(_._2))
    }
  }
~~~

这里 `valid.length` 是 Scala 集合长度，所以 `if (valid.length == 2)` 和递归分治都在 elaboration 时决定电路拓扑；运行时并不会递归调用函数。长度为 2 时，`Wire(ValidIO(...))` 先为两个候选建立 valid/bits Bundle，内层 `Mux(isAfter(...), res(1), res(0))` 比较两个 `robIdx` 的硬件年龄并取更老者。长度更大时，`take`/`takeRight` 把 Scala 列表分成固定两半，再把左右各自归约后的结果递归交给同一函数，最终形成一棵固定深度的年龄选择树。

`selectOldest` 在这棵树外再加入分组与寄存级。`grouped(SelectGroupSize)`、`numSelectGroups` 和 Scala `if` 决定分组数量与递归拓扑；每组先调用 `selectPartialOldest`。无论是单组还是多组，`RegEnable(selBits(0), selValid(0))` 只在该组选择有效时锁存选出的 uop，`GatedValidRegNext` 或 `RegNext` 把相应 valid 推到下一拍。之后 `needFlush(io.redirect)` 与 `needFlush(RegNext(io.redirect))` 过滤在选择期间已经被 redirect 冲刷的候选。故 RAW oldest 选择既有组合年龄比较树，也有按组插入的跨拍 valid/bits 对齐，不能把它简化成单周期 Mux 链。

**代码依据：xiangshan/mem/lsqueue/LoadQueueRAW.scala:291-362**

~~~scala
    val addrMaskMatch = paddrModule.io.violationMmask(i).asUInt & maskModule.io.violationMmask(i).asUInt
    val entryNeedCheck = GatedValidRegNext(VecInit((0 until LoadQueueRAWSize).map(j => {
      allocated(j) && storeIn(i).valid && isAfter(uop(j).robIdx, storeIn(i).bits.uop.robIdx) && datavalid(j) && !uop(j).robIdx.needFlush(io.redirect)
    })))
    val lqViolationSelVec = VecInit((0 until LoadQueueRAWSize).map(j => {
      addrMaskMatch(j) && entryNeedCheck(j)
    }))

    val lqSelect: (Seq[Bool], Seq[XSBundleWithMicroOp]) = selectOldest(lqViolationSelVec, lqViolationSelUopExts)

    val lqViolation = lqSelect._1(0)
    val lqViolationUop = lqSelect._2(0).uop

    (lqViolation, lqViolationUop)
  }

  val allRedirect = (0 until StorePipelineWidth).map(i => {
    val redirect = Wire(Valid(new Redirect))
    redirect.valid := rollbackLqWb(i).valid
    redirect.bits             := DontCare
    redirect.bits.robIdx      := rollbackLqWb(i).bits.robIdx
    redirect.bits.level       := RedirectLevel.flush
    redirect.bits.cfiUpdate.target := rollbackLqWb(i).bits.pc
    redirect
  })
  io.rollback := allRedirect
~~~

`entryNeedCheck` 先以 `allocated`、Store 输入 valid、ROB 年龄、`datavalid` 和 `needFlush` 的反值筛掉不应检查的 Load；`GatedValidRegNext` 把这一候选位图跨拍。地址和 byte mask CAM 命中结果按位相与后得到 `lqViolationSelVec`，再送入前一段的年龄选择树。`Wire(Valid(new Redirect))` 先声明每条 Store pipeline 的 redirect，`DontCare` 用来覆盖无关字段，随后只在 `rollbackLqWb(i).valid` 时把关键 robIdx、PC 和 level 驱动为确定值。最终 `redirect.level := RedirectLevel.flush` 是硬连线常量，因此 RAW 违例走恢复接口而非普通 replay 接口。

### 14.7.2 RAR 的 release 检查和 flushAfter

**代码依据：xiangshan/mem/lsqueue/LoadQueueRAR.scala:224-265**

~~~scala
  for ((query, w) <- io.query.zipWithIndex) {
    ldLdViolation(w) := false.B
    paddrModule.io.releaseViolationMdata(w) := genPartialPAddr(query.req.bits.paddr)

    query.resp.valid := RegNext(query.req.valid)
    val robIdxMask = VecInit(uop.map(_.robIdx).map(isAfter(_, query.req.bits.uop.robIdx)))
    val matchMaskReg = Wire(Vec(LoadQueueRARSize, Bool()))
    for(i <- 0 until LoadQueueRARSize) {
      matchMaskReg(i) := (allocated(i) &
                         paddrModule.io.releaseViolationMmask(w)(i) &
                         robIdxMask(i) &&
                         released(i))
      }
    val matchMask = RegEnable(matchMaskReg, query.req.valid)
    val ldLdViolationMask = matchMask
    ldLdViolationMask.suggestName("ldLdViolationMask_" + w)
    query.resp.bits.rep_frm_fetch := ParallelORR(ldLdViolationMask)
  }

  when (release1Cycle.valid) {
    paddrModule.io.releaseMdata.takeRight(1)(0) := genPartialPAddr(release1Cycle.bits.paddr)
  }
~~~

`for ((query, w) <- io.query.zipWithIndex)` 为每个 Load pipeline 查询端口展开独立的匹配网络。`robIdxMask` 是每个 RAR entry 相对当前 query 的年龄比较；内层 `for` 将 allocated、物理地址 CAM 命中、年龄和 released 四个 Bool 相与。`query.resp.valid := RegNext(query.req.valid)` 将响应有效位推后一拍，`RegEnable(matchMaskReg, query.req.valid)` 只在请求有效时锁存匹配位图。`ParallelORR` 将所有 entry 的结果归约为一个 `rep_frm_fetch`，因此响应只报告“是否存在违例”，具体 entry 不在这个端口返回。

**代码依据：xiangshan/mem/pipeline/LoadUnit.scala:1606-1685**

~~~scala
  val s3_ldld_rep_inst =
      io.lsq.ldld_nuke_query.resp.valid &&
      io.lsq.ldld_nuke_query.resp.bits.rep_frm_fetch &&
      GatedValidRegNext(io.csrCtrl.ldld_vio_check_enable)
  val s3_flushPipe = s3_ldld_rep_inst

  io.rollback.valid := s3_valid && (s3_rep_frm_fetch || s3_flushPipe || s3_frm_mis_flush) && !s3_exception
  io.rollback.bits             := DontCare
  io.rollback.bits.robIdx      := s3_out.bits.uop.robIdx
  io.rollback.bits.level       := Mux(s3_rep_frm_fetch || s3_frm_mis_flush, RedirectLevel.flush, RedirectLevel.flushAfter)
~~~

RAR 响应先与 `GatedValidRegNext(io.csrCtrl.ldld_vio_check_enable)` 相与才形成 `s3_flushPipe`，所以即使地址查询报告了 `rep_frm_fetch`，CSR 开关也会参与该拍的恢复资格。`rollback.valid` 同时允许 `s3_rep_frm_fetch`、`s3_flushPipe` 和 `s3_frm_mis_flush` 三种来源；但 level 的 Mux 只检查前两类中的 `s3_rep_frm_fetch` 与 `s3_frm_mis_flush`。因此 rollback 因 `s3_flushPipe` 有效、且这两个条件都为假时，Mux 才走 false 臂并选择 `flushAfter`；RAW 的代码则固定给出 `flush`。

### 14.7.3 MemBlock 选择全局最老恢复点

**代码依据：xiangshan/mem/MemBlock.scala:1424-1441**

~~~scala
  def selectOldestRedirect(xs: Seq[Valid[Redirect]]): Vec[Bool] = {
    val compareVec = (0 until xs.length).map(i => (0 until i).map(j => isAfter(xs(j).bits.robIdx, xs(i).bits.robIdx)))
    val resultOnehot = VecInit((0 until xs.length).map(i => Cat((0 until xs.length).map(j =>
      (if (j < i) !xs(j).valid || compareVec(i)(j)
      else if (j == i) xs(i).valid
      else !xs(j).valid || !compareVec(j)(i))
    )).andR))
    resultOnehot
  }
  val allRedirect = loadUnits.map(_.io.rollback) ++ hybridUnits.map(_.io.ldu_io.rollback) ++ lsq.io.nack_rollback ++ lsq.io.nuke_rollback
  val oldestOneHot = selectOldestRedirect(allRedirect)
  val oldestRedirect = WireDefault(Mux1H(oldestOneHot, allRedirect))
  io.mem_to_ooo.memoryViolation := oldestRedirect
~~~

两层 `map` 在 elaboration 时枚举每一对 redirect 输入，生成 `isAfter` 年龄比较矩阵。对第 i 个候选，内层 `Cat(...).andR` 把“自己有效、所有更老候选无效或自己更老、所有更年轻候选无效或自己更老”合成为一位 one-hot 选择结果。`allRedirect` 是 Scala 拼接后的固定端口列表；`Mux1H(oldestOneHot, allRedirect)` 用该 one-hot 在硬件中选择一个完整 Redirect Bundle。因而 `selectOldestRedirect` 本身没有寄存器，它是一张同拍比较/选择网络，最终由 `io.mem_to_ooo.memoryViolation :=` 输出。

## 14.8 特殊路径

### 14.8.1 NC Load 与 MMIO Load

**代码依据：xiangshan/mem/lsqueue/LoadQueueUncache.scala:117-137**

~~~scala
  val pendingld = GatedValidRegNext(io.rob.pendingMMIOld)
  val pendingPtr = GatedRegNext(io.rob.pendingPtr)
  val canSendReq = req_valid && !needFlush && Mux(
    req.nc, true.B,
    pendingld && req.uop.robIdx === pendingPtr
  )
  switch (uncacheState) {
    is (s_idle) {
      when (needFlush) {
        uncacheState := s_idle
        flush := true.B
      }.elsewhen (canSendReq) {
        uncacheState := s_req
      }
    }
~~~

`pendingld` 与 `pendingPtr` 由 `Gated*RegNext` 从 ROB 输入取得保持值；它们不是 Scala 快照。`canSendReq` 的最外层先统一要求 `req_valid && !needFlush`，随后 Mux 根据 `req.nc` 选择规则：NC 走 true.B，而 I/O/MMIO Load 必须同时满足 ROB 给出的 `pendingMMIOld` 和相同 `pendingPtr`。`switch (uncacheState)`/`is(s_idle)` 是 Chisel 状态选择语法，`uncacheState := s_req` 的效果在时钟边界更新状态。因而该 Mux 只决定 s_idle 中能否转移到 s_req，并不等同于请求已经完成总线握手。

当前片段直接显示的转移如下；未展示的 `s_req` 后续状态不在此表中推断：

| 当前状态 | 条件 | 下一状态 / 同拍动作 |
| --- | --- | --- |
| `s_idle` | `needFlush` | 保持 `s_idle`，并置 `flush := true.B`。 |
| `s_idle` | `!needFlush && canSendReq` | 下一拍进入 `s_req`。 |

### 14.8.2 NC Store

**代码依据：xiangshan/mem/lsqueue/StoreQueue.scala:914-955**

~~~scala
  val nc_idle :: nc_req :: nc_req_ack :: nc_resp :: Nil = Enum(4)
  val ncState = RegInit(nc_idle)
  val rptr0 = rdataPtrExt(0).value
  switch(ncState){
    is(nc_idle) {
      when(
        nc(rptr0) && allocated(rptr0) && !completed(rptr0) && committed(rptr0) &&
        allvalid(rptr0) && !isVec(rptr0) && !hasException(rptr0) && !mmio(rptr0) && !LSUOpType.isCboAll(uop(rptr0).fuOpType)
      ) {
        ncState := nc_req
        ncWaitRespPtrReg := rptr0
      }
    }
    is(nc_req) {
      when(ncDoReq) {
        ncState := nc_req_ack
      }
    }
    is(nc_req_ack) {
      when(ncSlaveAck) {
        when(io.uncacheOutstanding) {
          ncState := nc_idle
        }.otherwise{
          ncState := nc_resp
        }
      }
    }
~~~

`val nc_idle :: nc_req :: nc_req_ack :: nc_resp :: Nil = Enum(4)` 先在 Scala 层把四个 Chisel 状态常量解构为名字，`RegInit(nc_idle)` 才生成带复位初值的 `ncState` 寄存器。`rptr0` 是第一个 SQ 读指针的硬件值；在 `s_idle` 中，所有条件相与后才把 `ncState` 置为 `s_req` 并锁存 `ncWaitRespPtrReg`。之后 `ncDoReq`、`ncSlaveAck`、`io.uncacheOutstanding` 分别控制请求、应答和是否继续等待的状态转换。每个 `is(...)` 是对当前状态寄存器的组合译码，所以普通 dataBuffer/SBuffer 条件中的 `ncStall` 有明确的独立控制路径。

| 当前状态 | 代码中的条件 | 下一状态 / 附带写入 |
| --- | --- | --- |
| `nc_idle` | NC、已分配、未完成、已提交、`allvalid`，且排除向量、异常、MMIO、CBO | `nc_req`，并写 `ncWaitRespPtrReg := rptr0`。 |
| `nc_req` | `ncDoReq` | `nc_req_ack`。 |
| `nc_req_ack` | `ncSlaveAck && io.uncacheOutstanding` | `nc_idle`。 |
| `nc_req_ack` | `ncSlaveAck && !io.uncacheOutstanding` | `nc_resp`。 |

表格只列出这段源码出现的分支；例如 `nc_resp` 的退出条件需要到同一状态机的后续源码查询，不能从 `Enum(4)` 猜出。

### 14.8.3 非对齐 Load

**代码依据：xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:39-47, 117-122**

~~~scala
class LoadMisalignBuffer(implicit p: Parameters) extends XSModule
  with HasCircularQueuePtrHelper
  with HasLoadHelper
  with HasTlbConst
{
  private val enqPortNum = LoadPipelineWidth
  private val maxSplitNum = 2

  require(maxSplitNum == 2)

  val io = IO(new Bundle() {
    val redirect        = Flipped(Valid(new Redirect))
    val enq             = Vec(enqPortNum, Flipped(new MisalignBufferEnqIO))
    val rob             = Flipped(new RobLsqIO)
    val splitLoadReq    = Decoupled(new LsPipelineBundle)
    val splitLoadResp   = Flipped(Valid(new LqWriteBundle))
~~~

`class LoadMisalignBuffer ... with ...` 是 Scala 的继承/trait 组合，提供参数和辅助方法；它自身仍由 `Module(new LoadMisalignBuffer)` 例化为硬件。`private val enqPortNum = LoadPipelineWidth` 与 `maxSplitNum = 2` 是生成期常量，`require(maxSplitNum == 2)` 在 elaboration 时检查它。`new Bundle` 内的 `Vec(enqPortNum, ...)` 才根据参数生成多个硬件入队端口；`Decoupled(new LsPipelineBundle)` 则定义 split request 的 valid/ready/bits 边界，`Flipped(Valid(...))` 的 split response 没有 ready 回压。

**代码依据：xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:314-326**

~~~scala
  when (bufferState === s_split) {
    when (!cross16BytesBoundary) {
      assert(false.B, s"There should be no non-aligned access that does not cross 16Byte boundaries.")
    } .otherwise {
      // split this unaligned load into `maxSplitNum` aligned loads
      unSentLoads := Fill(maxSplitNum, 1.U(1.W))
      curPtr := 0.U
      lowAddrLoad.uop := req.uop
      lowAddrLoad.uop.exceptionVec(loadAddrMisaligned) := false.B
      lowAddrLoad.fullva := req.fullva
      highAddrLoad.uop := req.uop
      highAddrLoad.uop.exceptionVec(loadAddrMisaligned) := false.B
      highAddrLoad.fullva := req.fullva
~~~

`bufferState === s_split` 是状态寄存器与常量的硬件比较，内层 `when`/`otherwise` 根据本次请求是否跨越 16 B 边界选择路径。`Fill(maxSplitNum, 1.U(1.W))` 在 elaboration 时展开为固定宽度的全 1 位向量；因为 `maxSplitNum` 为 2，`unSentLoads` 被置成两个待发送标记。`curPtr := 0.U` 从第一个子请求开始，随后分别复制原 uop 与 fullva 到 `lowAddrLoad`、`highAddrLoad`，并清除原始的非对齐异常位以便子请求重新进入对齐流水。`assert` 生成硬件断言；它不是 Scala 抛出的异常。

### 14.8.4 原子访问

**代码依据：xiangshan/mem/pipeline/AtomicsUnit.scala:37-69**

~~~scala
class AtomicsUnit(implicit p: Parameters) extends XSModule
  with MemoryOpConstants
  with HasDCacheParameters
  with SdtrigExt{

  val StdCnt  = backendParams.StdCnt

  val io = IO(new Bundle() {
    val hartId        = Input(UInt(hartIdLen.W))
    val in            = Flipped(Decoupled(new MemExuInput))
    val storeDataIn   = Flipped(Vec(StdCnt, Valid(new MemExuOutput)))
    val out           = Decoupled(new MemExuOutput)
    val dcache        = new AtomicWordIO
    val dtlb          = new TlbRequestIO(2)
    val pmpResp       = Flipped(new PMPRespBundle())
    val flush_sbuffer = new SbufferFlushBundle
    val feedbackSlow  = ValidIO(new RSFeedback)
    val redirect      = Flipped(ValidIO(new Redirect))
    val csrCtrl       = Flipped(new CustomCSRCtrlIO)
  })

  val s_invalid :: s_tlb_and_flush_sbuffer_req :: s_pm :: s_wait_flush_sbuffer_resp :: s_cache_req :: s_cache_resp :: s_cache_resp_latch :: s_finish :: s_finish2 :: Nil = Enum(9)
  val state = RegInit(s_invalid)
~~~

类头的四个 `with` 是 Scala trait 混入；`val StdCnt = backendParams.StdCnt` 是用于生成 `Vec(StdCnt, ...)` 端口数的参数。`IO(new Bundle { ... })` 创建实际硬件接口：`Flipped(Decoupled(...))` 表示输入的原子请求，`AtomicWordIO`、`TlbRequestIO`、`PMPRespBundle`、`SbufferFlushBundle` 则把各系统边界显式列出。`:: ... :: Nil = Enum(9)` 使用 Scala List 模式解构 9 个 Chisel 状态常量，`RegInit(s_invalid)` 生成保存当前状态的寄存器。这是原子访问在当前 MemBlock 中具有专用控制模块与接口的结构证据。

**代码依据：xiangshan/mem/pipeline/AtomicsUnit.scala:290-314**

~~~scala
    val exception_pa_mmio_nc = pmp.mmio || Pbmt.isIO(pbmtReg) || Pbmt.isNC(pbmtReg)
    val exception_pa = pmp.st || pmp.ld || exception_pa_mmio_nc
    when (exception_va || exception_pa) {
      state := s_finish
      out_valid := true.B
      atom_override_xtval := true.B
    }.otherwise {
      // if sbuffer has been flushed, go to query dcache, otherwise wait for sbuffer.
      state := Mux(sbuffer_empty, s_cache_req, s_wait_flush_sbuffer_resp);
    }
    // update storeAccessFault bit
    exceptionVec(loadAccessFault) := exceptionVec(loadAccessFault) ||
      (pmp.ld || exception_pa_mmio_nc) && isLr
    exceptionVec(storeAccessFault) := exceptionVec(storeAccessFault) || pmp.st ||
      (pmp.ld || exception_pa_mmio_nc) && !isLr
  }

  when (state === s_wait_flush_sbuffer_resp) {
    when (sbuffer_empty) {
      state := s_cache_req
    }
  }
~~~

这段先把 PMP/PBMT 结果组合成 `exception_pa_mmio_nc` 和 `exception_pa`。若 VA 或 PA 检查异常，`when` 给状态寄存器安排 `s_finish`，并置 `out_valid`；否则 `Mux(sbuffer_empty, s_cache_req, s_wait_flush_sbuffer_resp)` 为下一状态选择 DCache 请求或等待路径。注意 Mux 输出只是写入 `state` 的下一值，真正进入 s_cache_req 要到时钟边界。第二个 `when (state === s_wait_flush_sbuffer_resp)` 在后续周期持续检查 `sbuffer_empty`，所以 SBuffer 从非空变为空时才推进到 cache 请求。

| 当前状态/检查点 | 条件 | 下一状态 / 动作 |
| --- | --- | --- |
| TLB/PMP 检查完成处 | `exception_va || exception_pa` | `s_finish`，同时置 `out_valid` 与 `atom_override_xtval`。 |
| TLB/PMP 检查完成处 | 无异常且 `sbuffer_empty` | `s_cache_req`。 |
| TLB/PMP 检查完成处 | 无异常且 SBuffer 非空 | `s_wait_flush_sbuffer_resp`。 |
| `s_wait_flush_sbuffer_resp` | `sbuffer_empty` | `s_cache_req`。 |

**代码依据：xiangshan/mem/pipeline/AtomicsUnit.scala:456-461, 467-472, 491-495**

~~~scala
  io.dtlb.req.valid       := state === s_tlb_and_flush_sbuffer_req
  io.dtlb.req.bits.vaddr  := vaddr
  io.dtlb.req.bits.fullva := vaddr
  io.dtlb.req.bits.checkfullva := true.B
  io.dtlb.resp.ready      := true.B
  io.dtlb.req.bits.cmd    := Mux(isLr, TlbCmd.atom_read, TlbCmd.atom_write)

  io.flush_sbuffer.valid := !sbuffer_empty && (
    state === s_tlb_and_flush_sbuffer_req ||
    state === s_pm ||
    state === s_wait_flush_sbuffer_resp
  )

  io.dcache.req.valid := Mux(
    io.dcache.req.bits.cmd === M_XLR,
    !io.dcache.block_lr, // block lr to survive in lr storm
    data_valid // wait until src(1) is ready
  ) && state === s_cache_req
~~~

`io.dtlb.req.valid := state === s_tlb_and_flush_sbuffer_req` 用当前状态直接驱动 DTLB 请求有效位，`cmd` 的 Mux 再按 `isLr` 选择读/写原子翻译命令。`io.flush_sbuffer.valid` 同时要求 SBuffer 非空且处于三个前置状态之一，因此它不会在 s_cache_req 以后继续主动发 flush。DCache `valid` 的外层 AND 强制状态为 `s_cache_req`；内层 Mux 只对 LR 使用 `!block_lr`，其他原子操作等待 `data_valid`。和其他 Chisel 连接一样，`io.dcache.req.bits.cmd` 在源码其他位置的驱动与这里的读取组成同一张电路图，不应按文本先后误读为软件未初始化变量。两段代码共同证明原子请求在 s_cache_req 才访问 DCache，且此前会等待 SBuffer 为空；本章不从这些片段推断未显式出现的 ISA 级 aq/rl 语义。

### 14.8.5 向量访存对标量 LSU 的复用

**代码依据：xiangshan/mem/MemBlock.scala:1618-1654**

~~~scala
  (0 until VlduCnt).foreach{i =>
    vlSplit(i).io.redirect <> redirect
    vlSplit(i).io.in <> io.ooo_to_mem.issueVldu(i)
    vlSplit(i).io.in.valid := io.ooo_to_mem.issueVldu(i).valid &&
                              vLoadCanAccept(i) && !isSegment && !isFixVlUop(i)
    vlSplit(i).io.toMergeBuffer <> vlMergeBuffer.io.fromSplit(i)
    vlSplit(i).io.threshold.valid := vlMergeBuffer.io.toSplit.threshold
    vlSplit(i).io.threshold.bits.robIdx  := lsq.io.lqDeqRobIdx
    vlSplit(i).io.threshold.bits.uopIdx  := lsq.io.lqDeqUopIdx
    vlSplit(i).io.fromPipeline.foreach { case port =>
      port.zipWithIndex.map{case (sink, j) =>
        if(j == MisalignWBPort) {
          when(loadUnits(j).io.vecldout.valid) {
            sink.valid := loadUnits(j).io.vecldout.valid
            sink.bits  := loadUnits(j).io.vecldout.bits
          } .otherwise {
            sink.valid   := loadMisalignBuffer.io.vecWriteBack.valid
            sink.bits    := loadMisalignBuffer.io.vecWriteBack.bits
          }
        }else {
          sink.valid := loadUnits(j).io.vecldout.valid
          sink.bits  := loadUnits(j).io.vecldout.bits
        }

      }
    }
    NewPipelineConnect(
      vlSplit(i).io.out, loadUnits(i).io.vecldin, loadUnits(i).io.vecldin.fire,
      Mux(vlSplit(i).io.out.fire, vlSplit(i).io.out.bits.uop.robIdx.needFlush(io.redirect), loadUnits(i).io.vecldin.bits.uop.robIdx.needFlush(io.redirect)),
      Option("VlSplitConnectLdu")
    )

    //Subsequent instrction will be blocked
    vfofBuffer.io.in(i).valid := io.ooo_to_mem.issueVldu(i).valid
    vfofBuffer.io.in(i).bits  := io.ooo_to_mem.issueVldu(i).bits
  }
~~~

**代码依据：xiangshan/mem/MemBlock.scala:1655-1671**

~~~scala
  (0 until LduCnt).foreach{i=>
    loadUnits(i).io.vecldout.ready         := vlMergeBuffer.io.fromPipeline(i).ready
    loadMisalignBuffer.io.vecWriteBack.ready := true.B

    if (i == MisalignWBPort) {
      when(loadUnits(i).io.vecldout.valid) {
        vlMergeBuffer.io.fromPipeline(i).valid := loadUnits(i).io.vecldout.valid
        vlMergeBuffer.io.fromPipeline(i).bits  := loadUnits(i).io.vecldout.bits
      } .otherwise {
        vlMergeBuffer.io.fromPipeline(i).valid   := loadMisalignBuffer.io.vecWriteBack.valid
        vlMergeBuffer.io.fromPipeline(i).bits    := loadMisalignBuffer.io.vecWriteBack.bits
      }
    } else {
      vlMergeBuffer.io.fromPipeline(i).valid := loadUnits(i).io.vecldout.valid
      vlMergeBuffer.io.fromPipeline(i).bits  := loadUnits(i).io.vecldout.bits
    }
  }
~~~

第一个 `foreach` 用 VlduCnt 在生成期展开：`vlSplit(i).io.in <> issueVldu(i)` 接入向量 issue 端口，随后显式给 `vlSplit(i).io.in.valid` 加上 `vLoadCanAccept`、非 segment、非 fixed-uop 条件。`fromPipeline.foreach` 和 `zipWithIndex` 再为每个 merge 输入端口展开连接；其中 `if (j == MisalignWBPort)` 是 Scala 常量分支，只在固定端口生成一个硬件 `when(loadUnits(j).io.vecldout.valid)` 选择普通回写或非对齐回写。第二个 `foreach` 按 LduCnt 展开，把每个 `vecldout` 作为 VLMergeBuffer 输入；同一个 `if (i == MisalignWBPort)` 仍是生成期选择。由此可见 split/merge 控制是向量专用的，但中间执行端口确实是 LoadUnit 的 `vecldin`/`vecldout`。

**代码依据：xiangshan/backend/datapath/NewPipelineConnect.scala:35-80**

~~~scala
  def connect[T <: Data](
                          left: DecoupledIO[T],
                          right: DecoupledIO[T],
                          rightOutFire: Bool,
                          isFlush: Bool,
                          isOlder: Bool
                        ): T = {
    val valid = RegInit(false.B)

    left.ready := right.ready || !valid || isOlder
    val data = RegEnable(left.bits, left.fire)

    when (rightOutFire) { valid := false.B }
    when (left.fire) { valid := true.B }
    when (isFlush) { valid := false.B }

    right.bits := data
    right.valid := valid

    data
  }

  def apply[T <: Data](
                        left: DecoupledIO[T],
                        right: DecoupledIO[T],
                        rightOutFire: Bool,
                        isFlush: Bool,
                        moduleName: Option[String] = None,
                        isOlder: Bool = false.B
                      ): Option[T] = {
    if (moduleName.isDefined) {
      val pipeline = Module(new NewPipelineConnectPipe(left.bits))
      pipeline.suggestName(moduleName.get)
      pipeline.io.in <> left
      pipeline.io.rightOutFire := rightOutFire
      pipeline.io.isFlush := isFlush
      pipeline.io.isOlder := isOlder
      pipeline.io.out <> right
      pipeline.io.out.ready := right.ready
      None
    }
    else {
      // do not use module here to please DCE
      Some(connect(left, right, rightOutFire, isFlush, isOlder))
    }
  }
~~~

`NewPipelineConnect` 不是 `<>` 的别名。`connect` 内部有独立的 `valid = RegInit(false.B)` 和 `data = RegEnable(left.bits, left.fire)`：只有 left 真实握手时才锁存 payload，right 则由保存的 valid/data 输出。`left.ready` 在 right ready、内部没有待发数据或 `isOlder` 为真时放行输入；`rightOutFire` 消费已有数据，`left.fire` 装入新数据，`isFlush` 清空 valid。三个 `when` 对同一寄存器的连接在这里构成确定优先级，文本靠后的 `isFlush` 覆盖前面两项。调用 `apply` 时，`moduleName.isDefined` 是 Scala Option 判断；本章的 `Option("VlSplitConnectLdu")` 会走该分支，真实例化一个带名字的 `NewPipelineConnectPipe`，而不是把 `connect` 逻辑内联。因此向量 split 到 LoadUnit 之间确实插入了一个可保存一拍数据、可被 flush 清空的握手级。

## 14.9 源码追踪顺序

读任一段访存 Chisel 前，先按下面的顺序做标记，能避免把 Scala 控制流、接口握手和硬件周期混在一起：

1. 圈出 `RegInit`、`RegNext`、`RegEnable`：这些是跨拍边界，先问“该寄存器在什么条件下更新、无更新时保持什么”。
2. 圈出 `Wire`、`Mux`、按位 `&`/`|`、`orR` 与比较：这些构成当前拍的组合资格、掩码和选择网络。
3. 对每个 `Decoupled` 端口同时追 `valid`、`ready` 和 `fire`；只看到 `valid` 时还不能宣称一项请求已经被接收。
4. 对每个 `Vec(i)`、`Seq(i)` 和 `for` 判断 i 是硬件索引还是 Scala 生成索引；同名的 `i` 在不同循环中不代表同一条动态事务。
5. 最后查看 `needFlush`、`revoke`、`rollback`、`rep_info` 和 `ldCancel`：它们分别处在取消、恢复、重发或依赖撤销的不同接口上，不能互相替代。

### 一条标量 Load

1. 在 14.3.1 和 14.3.2 节检查它进入的 LSQ 结构以及 lqIdx/sqIdx。
2. 在 14.5.1 节检查它从 S0 的哪一种来源进入。
3. 在 14.5.2 节检查 S1 的 SQ/SBuffer/UBuffer 查询与 NC/MMIO 分类。
4. 在 14.5.3 节检查 byte-lane 数据优先级与 rep_info。
5. 在 14.5.4、14.6 和 14.7 节判断结果是取消、Replay、flush 还是 flushAfter。

### 一条标量 Store

1. 在 14.2.2 节查看 Sta 和 Std 如何分别写入 StoreQueue。
2. 在 14.4.1 节查看 allocated、addrvalid、datavalid、committed、completed 等状态。
3. 在 14.4.2 节查看对应 Load 的前递与 dataInvalid。
4. 在 14.4.3 节追踪 ROB 提交、dataBuffer、SBuffer fire、合并和 completed。
5. 若是 NC/MMIO，不沿普通 SBuffer 路径推断，而转到 14.8.1 和 14.8.2 节。

## 14.10 访存模块协作图：请求、状态和责任边界

前面的章节按 Load、Store、LSQ 和特殊路径分别阅读代码。本节换一个视角：把 `MemBlock` 直接例化或直接连接的访存模块当作一个系统，回答一笔请求从哪里来、在哪些模块保存状态、经过哪些检查、最后到哪里去。

这里的“模块”指 MemBlock 边界可见的执行单元、队列、缓冲器、DTLB/PMP、DCache 和 Uncache；不把 DCache 内每个 SRAM 宏或每个辅助组合模块都伪装成独立的请求处理器。箭头表示下面源码中存在的具名端口连接，不表示一个没有握手、没有寄存器的抽象直连。

### 14.10.1 MemBlock 的两个边界

**代码依据：xiangshan/mem/MemBlock.scala:89-188**

~~~scala
class ooo_to_mem(implicit p: Parameters) extends MemBlockBundle {
  val backendToTopBypass = Flipped(new BackendToTopBundle)

  val loadFastMatch = Vec(LdExuCnt, Input(UInt(LdExuCnt.W)))
  val loadFastFuOpType = Vec(LdExuCnt, Input(FuOpType()))
  val loadFastImm = Vec(LdExuCnt, Input(UInt(12.W)))
  val sfence = Input(new SfenceBundle)
  val tlbCsr = Input(new TlbCsrBundle)
  val lsqio = new Bundle {
    val lcommit = Input(UInt(log2Up(CommitWidth + 1).W))
    val scommit = Input(UInt(log2Up(CommitWidth + 1).W))
    val pendingMMIOld = Input(Bool())
    val pendingld = Input(Bool())
    val pendingst = Input(Bool())
    val pendingVst = Input(Bool())
    val commit = Input(Bool())
    val pendingPtr = Input(new RobPtr)
    val pendingPtrNext = Input(new RobPtr)
  }

  val isStoreException = Input(Bool())
  val isVlsException = Input(Bool())
  val csrCtrl = Flipped(new CustomCSRCtrlIO)
  val enqLsq = new LsqEnqIO
  val flushSb = Input(Bool())

  val storePc = Vec(StaCnt, Input(UInt(VAddrBits.W))) // for hw prefetch
  val hybridPc = Vec(HyuCnt, Input(UInt(VAddrBits.W))) // for hw prefetch

  val issueLda = MixedVec(Seq.fill(LduCnt)(Flipped(DecoupledIO(new MemExuInput))))
  val issueSta = MixedVec(Seq.fill(StaCnt)(Flipped(DecoupledIO(new MemExuInput))))
  val issueStd = MixedVec(Seq.fill(StdCnt)(Flipped(DecoupledIO(new MemExuInput))))
  val issueHya = MixedVec(Seq.fill(HyuCnt)(Flipped(DecoupledIO(new MemExuInput))))
  val issueVldu = MixedVec(Seq.fill(VlduCnt)(Flipped(DecoupledIO(new MemExuInput(isVector=true)))))

  def issueUops = issueLda ++ issueSta ++ issueStd ++ issueHya ++ issueVldu
}

class mem_to_ooo(implicit p: Parameters) extends MemBlockBundle {
  val topToBackendBypass = new TopToBackendBundle

  val otherFastWakeup = Vec(LdExuCnt, ValidIO(new DynInst))
  val lqCancelCnt = Output(UInt(log2Up(VirtualLoadQueueSize + 1).W))
  val sqCancelCnt = Output(UInt(log2Up(StoreQueueSize + 1).W))
  val sqDeq = Output(UInt(log2Ceil(EnsbufferWidth + 1).W))
  val lqDeq = Output(UInt(log2Up(CommitWidth + 1).W))
  // used by VLSU issue queue, the vector store would wait all store before it, and the vector load would wait all load
  val sqDeqPtr = Output(new SqPtr)
  val lqDeqPtr = Output(new LqPtr)
  val stIn = Vec(StAddrCnt, ValidIO(new MemExuInput))
  val stIssuePtr = Output(new SqPtr())

  val memoryViolation = ValidIO(new Redirect)
  val sbIsEmpty = Output(Bool())
}
~~~

`ooo_to_mem` 是后端送入 MemBlock 的边界：标量 Load、Store 地址（Sta）、Store 数据（Std）、Hybrid 和向量访存都使用独立的 `DecoupledIO` 发射端口；ROB 提交数、pending 指针、`sfence`、TLB CSR 和 SBuffer flush 请求也从此进入。`mem_to_ooo` 是反向边界：LSQ 的取消/释放信息、Store 发射信息、访存违例 redirect、SBuffer 空状态，以及下文未在本片段展示的各类 writeback 和 RS feedback 从此返回后端。

因此，MemBlock 不是“一个 Load 单元加一个 Store 单元”。它是在生成期例化多条 Load/Store/Hybrid 流水线，并在运行期把同一笔 uop 的控制、地址翻译、前递、队列生命周期和写回端口汇合。全景主线如下：

~~~text
后端 Dispatch / ROB
  | issueLda / issueSta / issueStd / enqLsq / commit / redirect
  v
MemBlock
  |-- LoadUnit[i] -- DTLB + PMP -- DCache.LoadPipe[i] -- MissQueue -- TileLink A/E
  |       |              |                |                  ^
  |       |              +-- PTW ---------+                  |
  |       |-- SQ / SBuffer / Uncache / MSHR forward ---------+
  |       |-- LoadQueue: 生命周期、Replay、RAW/RAR、异常、MMIO/NC
  |
  |-- StoreUnit[i] -- DTLB + PMP -- DCache.StorePipe[i]
  |       |                   \-- tag/meta 检查和 Store 预取
  |       +-- StoreQueue <----- Std 数据执行单元
  |               |
  |               +-- 已提交的普通 Store --> SBuffer --> DCache.MainPipe
  |
  |-- AtomicsUnit / LoadMisalignBuffer / StoreMisalignBuffer / VLSU split-merge
  v
后端 writeback / wakeup / feedback / memoryViolation
~~~

~~~mermaid
flowchart LR
  BE["后端 / ROB"]

  subgraph MB["MemBlock：执行与特殊路径"]
    direction TB
    LU["LoadUnit[i]"]
    SU["StoreUnit[i]"]
    STD["MemExeUnit[i]<br/>Std 数据"]
    HYU["HybridUnit[i]<br/>ldu_io / stu_io"]
    AMO["AtomicsUnit"]
    MIS["Load/Store<br/>MisalignBuffer"]
    VS["VLSU<br/>Split / Merge"]
  end

  subgraph Q["LSQ 与 Store 缓冲"]
    LSQ["LsqWrapper<br/>联合分配 / Uncache 仲裁"]
    LQ["LoadQueue<br/>VLQ / Replay / RAW / RAR"]
    SQ["StoreQueue"]
    SB["Sbuffer"]
    UC["Uncache"]
  end

  subgraph DC["DCacheWrapper / DCache"]
    LP["LoadPipe[i]"]
    SP["StorePipe[i]<br/>tag/meta / prefetch"]
    MP["MainPipe<br/>line Store / AMO / refill"]
    MQ["MissQueue<br/>MSHR"]
    PQ["ProbeQueue"]
    WB["WritebackQueue"]
  end

  DTLB["DTLBNonBlock"]
  PMP["PMPChecker"]
  PTW["PTW / L2TLB"]
  L2["L2 / TileLink fabric"]
  MMIO["d_mmio_port"]

  BE -->|"issueLda"| LU
  BE -->|"issueSta"| SU
  BE -->|"issueStd"| STD
  BE -->|"issueHya"| HYU
  BE -.->|"enqLsq / commit / redirect"| LSQ

  LSQ -->|"联合分配 / 交叉索引"| LQ
  LSQ -->|"联合分配 / 交叉索引"| SQ
  LQ <-->|"地址/数据就绪、违例状态"| SQ

  LU -->|"LqWriteBundle / lq.ldin"| LQ
  LU -->|"uncache request"| LQ
  LQ -->|"replay / nc response"| LU
  LU -->|"SQ forward query"| SQ
  SQ -->|"mask / data / invalid"| LU
  LU -->|"Sbuffer forward query"| SB
  SB -->|"forward data"| LU
  LU -->|"Uncache forward query"| UC
  UC -->|"forward data"| LU

  LU -->|"TlbRequestIO"| DTLB
  SU -->|"TlbRequestIO"| DTLB
  HYU -->|"Load/Store TLB request"| DTLB
  DTLB -->|"paddr / exception"| PMP
  PMP -->|"PMPRespBundle"| LU
  PMP -->|"PMPRespBundle"| SU
  PMP -->|"PMPRespBundle"| HYU
  DTLB -.->|"miss / walk"| PTW
  PTW -.->|"PTE response"| DTLB
  PTW -->|"ptw_to_l2_buffer（非 softPTW）"| L2

  LU -->|"DCacheLoadIO.req"| LP
  LP -->|"load hit / resp"| LU
  LP -->|"miss_req"| MQ
  MQ -->|"forward_mshr / refill data"| LU

  SU -->|"storeAddrIn / mask"| SQ
  STD -->|"storeDataIn"| SQ
  SU -->|"DCacheStoreIO<br/>tag/meta / prefetch"| SP
  SQ -->|"committed line request"| SB
  SB -->|"M_XWR + line data/mask"| MP
  MP -->|"store hit / replay"| SB

  MP -->|"store/AMO miss_req"| MQ
  MQ -->|"TileLink A / E"| L2
  L2 -->|"TileLink D Grant/GrantData"| MQ
  L2 -->|"TileLink B Probe"| PQ
  PQ -->|"probe request / block"| MP
  MP -->|"writeback request"| WB
  WB -->|"TileLink C Release"| L2
  L2 -->|"TileLink D ReleaseAck"| WB

  LSQ -->|"UncacheWordIO.req"| UC
  UC -->|"resp / idResp"| LSQ
  UC -->|"TL-UL request / response"| MMIO

  AMO -->|"AtomicWordIO"| MP
  AMO -->|"TLB request"| DTLB
  SB -.->|"flush / empty"| AMO
  MIS <-->|"split req / split resp"| LU
  MIS <-->|"split req / split resp"| SU
  VS -->|"split flow"| LU
  VS -->|"split flow"| SU
  VS -->|"merged writeback"| BE

  LU -->|"ldout / wakeup"| BE
  SU -->|"issue / writeback"| BE
  HYU -->|"ldout / stout / wakeup"| BE
  LSQ -->|"memoryViolation / LQ-SQ status"| BE

  classDef backend fill:#f3f4f6,stroke:#374151,color:#111827;
  classDef exec fill:#eaf2ff,stroke:#2563eb,color:#111827;
  classDef queue fill:#fff7e6,stroke:#d97706,color:#111827;
  classDef cache fill:#eaf8ef,stroke:#15803d,color:#111827;
  classDef control fill:#fceff2,stroke:#be123c,color:#111827;
  classDef bus fill:#f1ecff,stroke:#7c3aed,color:#111827;
  class BE backend;
  class LU,SU,STD,HYU,AMO,MIS,VS exec;
  class LSQ,LQ,SQ,SB,UC queue;
  class LP,SP,MP,MQ,PQ,WB cache;
  class DTLB,PMP,PTW control;
  class L2,MMIO bus;
~~~

Mermaid 图的读法与源码方向保持一致：实线表示数据或 `Decoupled/Valid` 握手路径，虚线表示翻译、提交、flush 或恢复控制；`[i]` 表示 `Seq.fill`/`Seq.tabulate` 在 elaboration 时展开的并行端口。箭头上的名称是连接中实际出现的 Bundle 或字段，例如 `LqWriteBundle`、`forward_mshr`、`storeAddrIn` 和 `AtomicWordIO`，不是把模块名当作数据流。

图中最容易误读的两条边必须分开：`StoreUnit -> StorePipe` 只携带地址侧 `DCacheStoreIO`，用于 tag/meta 检查和 Store 预取；普通、已提交 Store 的数据沿 `StoreQueue -> Sbuffer -> MainPipe` 进入 DCache。相反，Load 的 refill 数据既可以由 `LoadPipe` 返回，也可以经 `MissQueue -> forward_mshr` 提前送回 `LoadUnit`。后文 14.10.3、14.10.5、14.10.6 和 14.10.7 的 Scala 片段分别给出这些箭头的 `<>`/`:=` 依据。

上图中最重要的责任边界是：**StoreUnit 接入的 `DCache.sta` 端口不等于普通 Store 数据最终写入 DCache 的端口**。前者服务 Store 地址侧的 tag/meta 检查和预取；已提交普通 Store 的 cache-line 数据沿 `StoreQueue -> Sbuffer -> DCache.MainPipe` 前进，14.10.5 会用连线证明这一点。

#### 14.10.1.1 总图 + 分图：用同名接口展开内部细节

上一张图把全部模块放在一个平面上，适合先找路径；本组图改用“总 + 分”组织。总图只保留分图之间真正跨边界的 Bundle/字段，分图 A1-A4 和 B-E 再展开模块内部的寄存器阶段、仲裁、状态机和数据来源。一个接口名在总图和分图中保持完全相同，例如总图中 A4 到 C 的 `io.lsq.ldin` 就是 A1 的 S3 生命周期输出，并在分图 C 中被 LoadQueue 消费。

| 图 | 范围 | 关键源码锚点（对应本章的 Scala 代码依据） |
| --- | --- | --- |
| 总图 | A1-A4 与 B-E 之间的接口契约 | `MemBlock.scala:850-1025, 1239-1319, 1446-1529` |
| 分图 A1 | LoadUnit S0-S3 的逐拍流水线 | `LoadUnit.scala:290-423, 900-986, 1100-1170, 1570-1715` |
| 分图 A2 | Load 的 DTLB、PTW 与 PMP/PMA 权限路径 | `LoadUnit.scala:383-409, 930-960`; `MemBlock.scala:686-806, 935-947` |
| 分图 A3 | S1 前递查询与 S2 byte-lane 数据合并 | `LoadUnit.scala:962-986, 1238-1266, 1387-1447`; `MemBlock.scala:921-929` |
| 分图 A4 | Queue replay、fast replay 与 rollback | `LoadUnit.scala:1310-1338, 1423-1447, 1580-1714, 1813-1817`; `LoadQueueReplay.scala:338-370, 491-579` |
| 分图 B | Sta/Std、SQ entry、Sbuffer line write | `MemBlock.scala:1239-1319, 1519-1529`; `Sbuffer.scala:607-754` |
| 分图 C | LQ/SQ 分配、违例/replay、Uncache 仲裁 | `LSQWrapper.scala:155-243, 265-321`; `MemBlock.scala:1446-1507` |
| 分图 D | DCache miss/replay、Probe、Writeback、TileLink | `DCacheWrapper.scala:1474-1642`; `XSTile.scala:63-99` |
| 分图 E | 原子、非对齐、向量、Hybrid | `AtomicsUnit.scala:37-325`; `LoadMisalignBuffer.scala:117-290`; `HybridUnit.scala:45-165` |

##### 总图：跨分图的数据接口

~~~mermaid
flowchart TB
  BE["后端 / ROB"]
  CTRL["DTLBNonBlock<br/>+ PMPChecker"]
  PTW["PTW / L2TLB"]
  L2["L2 / TileLink fabric"]
  MMIO["d_mmio_port"]

  subgraph MB["MemBlock：总图"]
    direction TB
    subgraph LOAD["分图 A：LoadUnit 子系统"]
      direction LR
      A1["分图 A1<br/>S0-S3 流水线"]
      A2["分图 A2<br/>地址翻译：DTLB / PMP"]
      A3["分图 A3<br/>forwarding 旁路"]
      A4["分图 A4<br/>replay / fast replay / rollback"]
    end
    B["分图 B<br/>Sta/Std、Sbuffer、已提交 Store"]
    C["分图 C<br/>LSQ 生命周期、违例、Uncache"]
    D["分图 D<br/>DCache、MSHR、TileLink"]
    E["分图 E<br/>AMO、Misalign、VLSU、Hybrid"]
  end

  BE -->|"issueLda / vecldin"| A1
  BE -->|"issueSta / issueStd"| B
  BE -.->|"enqLsq / commit / redirect"| C
  BE -->|"issueHya / issueVldu / AMO"| E

  A1 -->|"S0: TlbRequestIO.req<br/>vaddr / cmd / size"| A2
  A2 -->|"io.tlb.req"| CTRL
  CTRL -->|"io.tlb.resp + PMPRespBundle"| A2
  A2 -->|"S1: paddr / miss / pbmt / exception"| A1

  A1 -->|"S1: vaddr / paddr / mask / sqIdx"| A3
  A3 -->|"lsq.forward + nuke query"| C
  C -->|"forwardMask / forwardData / invalid<br/>RAR/RAW response"| A3
  A3 -->|"Sbuffer forward query"| B
  B -->|"forwardMask / forwardData"| A3
  A3 -->|"forward_mshr: mshrid / paddr"| D
  D -->|"forward_D / forward_mshr data"| A3

  A1 -->|"S0: DCacheLoadIO.req<br/>S1: paddr_dup / kill"| D
  D -->|"DCacheLoadIO.resp"| A1
  A1 -->|"S2: data + rep_info"| A4
  A4 -->|"io.replay / io.fast_rep_in"| A1
  A4 -->|"S3: io.lsq.ldin (LqWriteBundle)"| C
  C -->|"io.lsq.uncache / nc_ldin / ld_raw_data"| A1

  B -->|"storeAddrIn / storeDataIn / st_mask_out"| C
  C -->|"sbuffer DCacheWordReq"| B
  B -.->|"DCacheStoreIO：tag/meta / prefetch"| D
  B -->|"M_XWR：line data/mask"| D
  D -->|"store replay / hit response"| B

  B -->|"TlbRequestIO"| CTRL
  CTRL -->|"PMPRespBundle"| B
  E -->|"TlbRequestIO"| CTRL
  CTRL -->|"PMPRespBundle"| E
  CTRL -.->|"TLB miss / walk"| PTW
  E -->|"shared LSU / LSQ / DCache ports"| B
  E -->|"shared LSU / LSQ / DCache ports"| C
  E -->|"AtomicWordIO / vector flow"| D
  PTW -->|"ptw_to_l2_buffer（非 softPTW）"| L2

  C -->|"UncacheWordIO.req"| MMIO
  MMIO -->|"resp / idResp"| C
  D -->|"TileLink A / C / E"| L2
  L2 -->|"TileLink B / D"| D

  A4 -->|"ldout / wakeup / ldCancel / rollback"| BE
  B -->|"writebackSta / stIn"| BE
  C -->|"memoryViolation / LQ-SQ status"| BE
  E -->|"writeback / feedback"| BE

  classDef root fill:#f3f4f6,stroke:#374151,color:#111827;
  classDef detail fill:#eaf2ff,stroke:#2563eb,color:#111827;
  classDef ext fill:#f1ecff,stroke:#7c3aed,color:#111827;
  class BE root;
  class A1,A2,A3,A4,B,C,D,E detail;
  class CTRL,PTW,L2,MMIO ext;
~~~

总图中的 A1-A4 是对同一个 `LoadUnit` 按关注点进行的**图的边界拆分**，并不是四个新增 RTL `Module`；B-E 也是教学图边界。A1 保留时间顺序，A2 展示翻译和权限，A3 展示同一拍并行的旁路，A4 展示回放和恢复。因此，A1 与 A2/A3/A4 之间的线是该 `LoadUnit` 内部的跨关注点信号，不应误读为模块间 `DecoupledIO`。沿带名称的边进入对应分图，就能看到该边在 Chisel 中由哪个 `DecoupledIO`、`ValidIO`、`Reg` 或 `Enum` 驱动。

对标为 `Decoupled` 或 `req` 的边，箭头方向统一表示 `valid/bits` 的生产者到消费者，`ready` 沿反方向返回；为了不把每张图画成两倍数量的线，`ready` 只在关键仲裁/回压节点写在标签或图注中。`ValidIO` 边没有 `ready`，虚线只表示控制或状态依赖，不表示数据 payload。

分图 A 现在按“时间推进”和三个横切机制拆为 A1-A4。阅读顺序是 A1 的 S0-S3 主流水线；当它在 S0/S1 需要地址信息时转看 A2，在 S1/S2 需要数据时转看 A3，在 S2/S3 不能安全完成或需要恢复时转看 A4。四图的 `LoadUnit`、`LoadQueueReplay`、DTLB、PMPChecker 等名称均与源码一致；只有 A1-A4 的边界是教学视图。

##### 分图 A1：LoadUnit 的 S0-S3 逐拍流水线和外部交互

~~~mermaid
flowchart LR
  subgraph SRC["S0 候选源（固定优先级仲裁）"]
    ISS["后端 Issue<br/>io.ldin / io.vecldin"]
    REP["LoadQueueReplay<br/>io.replay"]
    FREP["同一 LoadUnit<br/>io.fast_rep_in"]
    MAB["LoadMisalignBuffer<br/>io.misalign_ldin"]
    URET["LoadQueueUncache<br/>io.lsq.uncache / nc_ldin"]
    AUX["L2L forward / prefetch<br/>io.l2l_fwd_in / prefetch_req"]
  end

  S0["S0<br/>s0_src_valid_vec / select_vec<br/>选一个 flow；生成 vaddr、mask<br/>tlb.req 与 dcache.req"]
  S1["S1<br/>RegEnable(s0_out, s0_fire)<br/>TLB 响应成为 paddr/属性<br/>三路 forwarding query"]
  S2["S2<br/>DCache resp + bypass 数据<br/>byte-lane 合并；生成 rep_info<br/>RAR / RAW nuke query"]
  S3["S3<br/>LqWriteBundle 生命周期落账<br/>writeback / wakeup / cancel<br/>rollback / fast replay"]

  XLT["分图 A2<br/>DTLB / PMP"]
  FWD["分图 A3<br/>SQ / Sbuffer / UBuffer<br/>D-channel / MSHR bypass"]
  DC["DCache LoadPipe"]
  LQ["分图 C：LoadQueue<br/>Replay / RAR / RAW / Uncache"]
  BE["后端 / ROB"]
  REDIR["redirect 候选"]

  ISS --> S0
  REP --> S0
  FREP --> S0
  MAB --> S0
  URET --> S0
  AUX --> S0

  S0 -->|"s0_fire；捕获 s0_out"| S1
  S1 -->|"s1_fire"| S2
  S2 -->|"s2_fire"| S3

  S0 -->|"TlbRequestIO.req"| XLT
  XLT -->|"tlb.resp：paddr / miss / pbmt / excp"| S1
  XLT -.->|"PMPRespBundle：S2 mmio 判定"| S2
  S0 -->|"DCacheLoadIO.req<br/>vaddr / mask / lqIdx"| DC
  S1 -->|"s1_paddr_dup_* / s1_kill"| DC
  DC -->|"DCacheLoadIO.resp"| S2

  S1 -->|"vaddr / paddr / mask / sqIdx"| FWD
  FWD -->|"forward mask/data/invalid<br/>D / MSHR forwarded data"| S2
  S2 -->|"rep_info + nuke query"| LQ
  S3 -->|"io.lsq.ldin：LqWriteBundle"| LQ
  LQ -->|"io.replay / uncache / nc_ldin / raw_data"| S0
  S3 -->|"io.fast_rep_out"| FREP
  S3 -->|"ldout / wakeup / ldCancel"| BE
  S3 -.->|"io.rollback"| REDIR

  classDef stage fill:#eaf2ff,stroke:#2563eb,color:#111827;
  classDef source fill:#fff7e6,stroke:#d97706,color:#111827;
  classDef data fill:#eaf8ef,stroke:#15803d,color:#111827;
  classDef control fill:#fceff2,stroke:#be123c,color:#111827;
  class ISS,REP,FREP,MAB,URET,AUX source;
  class S0,S1,S2,S3 stage;
  class DC,LQ,FWD data;
  class XLT,REDIR control;
~~~

| 拍 | 在拍内保存/计算的主要信号 | 接收什么 | 发往哪里、做什么 |
| --- | --- | --- | --- |
| S0 | `s0_src_valid_vec`、`s0_src_ready_vec`、`s0_src_select_vec`、`s0_valid` 和将被捕获的 `s0_out` | 首次发射、LSQ replay、fast replay、非对齐返回、MMIO/NC 返回、向量、L2L 和预取候选 | 选出一个 flow，形成 vaddr、mask、uop、`lqIdx`；并行向 DTLB 发 `io.tlb.req`，并在可访问 DCache 的路径上发 `io.dcache.req`。 |
| S1 | `s1_valid`、`s1_in`、`s1_out`、`s1_paddr_dup_lsu`、`s1_paddr_dup_dcache`、`s1_kill` | S0 捕获的 Bundle 与 `io.tlb.resp` | 将翻译得到的 paddr、PBMT、异常和 `tlbMiss` 写入 `s1_out`；把 paddr/kill 边带给 DCache，并同时向 SQ、Sbuffer 和 UBuffer 发旁路查询。 |
| S2 | `s2_valid`、`s2_in`、`s2_out`、`s2_fwd_mask`、`s2_fwd_data`、`s2_*_miss` | DCache response、三路 Store/Uncache 前递、D-channel/MSHR refill 前递，以及 RAR/RAW 查询握手 | 合并每个 byte 的数据来源，判断 full forwarding、异常、DCache miss/replay、bank conflict、TLB miss、旁路失败和 RAR/RAW nack；将结果编码进 `rep_info`。 |
| S3 | `s3_valid`、`s3_in`、`s3_can_enter_lsq_valid`、`s3_lrq_rep_info` | S2 输出，以及 LQ 的 RAR/RAW 响应、`ldout.ready` | 以 `io.lsq.ldin` 把生命周期信息交给 LQ；安全完成时驱动写回/唤醒，必要时生成 `ldCancel`、`rollback`，或用 `io.fast_rep_out` 直接回灌 S0。 |

**代码依据：xiangshan/mem/pipeline/LoadUnit.scala:900-917**

~~~scala
  // --------------------------------------------------------------------------------
  // stage 1
  // --------------------------------------------------------------------------------
  // TLB resp (send paddr to dcache)
  val s1_valid      = RegInit(false.B)
  val s1_in         = Wire(new LqWriteBundle)
  val s1_out        = Wire(new LqWriteBundle)
  val s1_kill       = Wire(Bool())
  val s1_can_go     = s2_ready
  val s1_fire       = s1_valid && !s1_kill && s1_can_go
  val s1_vecActive        = RegEnable(s0_out.vecActive, true.B, s0_fire)
  val s1_nc_with_data = RegNext(s0_nc_with_data)

  s1_ready := !s1_valid || s1_kill || s2_ready
  when (s0_fire) { s1_valid := true.B }
  .elsewhen (s1_fire) { s1_valid := false.B }
  .elsewhen (s1_kill) { s1_valid := false.B }
  s1_in   := RegEnable(s0_out, s0_fire)
~~~

这段 Chisel 是 A1 的一拍式握手模板：`RegInit(false.B)` 生成带复位初值的 S1 有效位；`s1_in := RegEnable(s0_out, s0_fire)` 只有在 S0 真正交接时捕获 Bundle。`s1_ready` 是本拍能否接受 S0 的条件，既允许空槽，也允许本拍被 kill，或允许 S2 接走旧内容。`when/elsewhen` 的赋值优先级使 `s0_fire` 的新事务优先于 `s1_fire` 的清空。S2 与 S3 同样以各自的 `valid/ready/fire` 推进；因此“第 N 拍”是对同一条 flow 的相对阶段，不是所有 Load 都必须无停顿地每周期前进。

S0 的普通 DCache 请求和 TLB 请求是并行发出的，而不是“DTLB 翻译结束后才请求 DCache”。后续 S1 将物理地址副本和 kill 信号送进 DCache 的下一阶段，以修正 VIPT 式早期访问。Fast replay、MMIO 与 NC 等特定来源可设置 `no_translate` 或不进入普通 DCache 请求，故图中 S0 的两条请求线表示可发生的接口活动，不能理解为每个候选源都会同时使用二者。

##### 分图 A2：地址翻译、PTW 回填和 PMP/PMA 权限检查

~~~mermaid
flowchart LR
  S0["A1 / S0<br/>vaddr、fullva、size、lqIdx、robIdx"]
  TREQ["TlbRequestIO.req<br/>cmd / isPrefetch / kill<br/>no_translate / frm_mabuf"]
  DTLB["TLBNonBlock<br/>Load requestor port"]
  PTW["PTW / L2TLB<br/>ptwio.req / ptwio.resp"]
  PSIDE["DTLB pmp / pmpMode<br/>dtlb_pmps"]
  PMP["PMP + PMPChecker<br/>pmp_check(i).resp"]
  S1["A1 / S1<br/>paddr、gpaddr、miss、pbmt<br/>page/access fault、ptwBack"]
  S2["A1 / S2<br/>PMP mmio + PBMT<br/>最终 MMIO / NC / 异常判定"]
  CTRL["CSR / sfence / redirect<br/>ROB pending pointer"]
  DC["DCache LoadPipe<br/>s1_paddr_dup_* / s1_kill"]

  S0 --> TREQ --> DTLB
  DTLB -->|"tlb.resp"| S1
  DTLB -->|"ptw.req on miss"| PTW
  PTW -->|"ptw.resp / refill"| DTLB
  DTLB --> PSIDE --> PMP -->|"PMPRespBundle"| S2
  S1 -->|"paddr duplicate + kill"| DC
  CTRL -.-> DTLB

  classDef pipe fill:#eaf2ff,stroke:#2563eb,color:#111827;
  classDef translate fill:#eaf8ef,stroke:#15803d,color:#111827;
  classDef control fill:#fceff2,stroke:#be123c,color:#111827;
  class S0,S1,S2,DC pipe;
  class TREQ,DTLB,PTW,PSIDE,PMP translate;
  class CTRL control;
~~~

这里必须区分三个方向。第一，`LoadUnit` 的 S0 是 `TlbRequestIO.req` 的生产者，DTLB 响应是 S1 的输入；DTLB 不直接把请求送到 DCache。第二，MemBlock 为各个扁平化的 DTLB 请求端口配一个 `PMPChecker`，DTLB 的 `pmp/pmpMode` 侧带经 checker 形成 `PMPRespBundle`，再接回本 LoadUnit。第三，TLB miss 到 PTW、PTW 返回到 DTLB 是 DTLB 内部的缺页处理路径，LoadUnit 观察到的是 `miss`、`ptwBack` 和随后重发的机会，而不是直接操作页表 walker。

**代码依据：xiangshan/mem/pipeline/LoadUnit.scala:383-404**

~~~scala
  // query DTLB
  io.tlb.req.valid                   := s0_tlb_valid
  io.tlb.req.bits.cmd                := Mux(s0_sel_src.prf,
                                         Mux(s0_sel_src.prf_wr, TlbCmd.write, TlbCmd.read),
                                         TlbCmd.read
                                       )
  io.tlb.req.bits.isPrefetch         := s0_sel_src.prf
  io.tlb.req.bits.vaddr              := s0_tlb_vaddr
  io.tlb.req.bits.fullva             := s0_tlb_fullva
  io.tlb.req.bits.checkfullva        := s0_src_select_vec(vec_iss_idx) || s0_src_select_vec(int_iss_idx)
  io.tlb.req.bits.hyperinst          := s0_tlb_hlv
  io.tlb.req.bits.hlvx               := s0_tlb_hlvx
  io.tlb.req.bits.size               := Mux(s0_sel_src.isvec, s0_sel_src.alignedType(2,0), LSUOpType.size(s0_sel_src.uop.fuOpType))
  io.tlb.req.bits.kill               := s0_kill || s0_tlb_no_query // if does not need to be translated, kill it
  io.tlb.req.bits.memidx.is_ld       := true.B
  io.tlb.req.bits.memidx.is_st       := false.B
  io.tlb.req.bits.memidx.idx         := s0_sel_src.uop.lqIdx.value
  io.tlb.req.bits.debug.robIdx       := s0_sel_src.uop.robIdx
  io.tlb.req.bits.no_translate       := s0_tlb_no_query  // hardware prefetch and fast replay does not need to be translated, need this signal for pmp check
  io.tlb.req.bits.frm_mabuf          := s0_sel_src.frm_mabuf  // hardware prefetch and fast replay does not need to be translated, need this signal for pmp check
  io.tlb.req.bits.debug.pc           := s0_sel_src.uop.pc
  io.tlb.req.bits.debug.isFirstIssue := s0_sel_src.isFirstIssue
~~~

`Mux` 在这里不是 Scala 分支，而是二选一的硬件数据选择器：普通 Load 的 `cmd` 被驱动为 `TlbCmd.read`，预取按读/写预取类型选择。`memidx.is_ld := true.B`、`is_st := false.B` 和 `idx := lqIdx` 将请求标识为 Load 并提供队列索引；`robIdx` 和 PC 仅走调试字段。重要的细节是 `no_translate` 并不意味着该 flow 从接口上完全消失：源代码明确把 fast replay 和硬件预取等情况标出来，使 PMP 检查仍能看到所需侧带。`kill` 则使当前请求失效，不能把它误画成一份“正常但带错误”的翻译响应。

S1 用 `io.tlb.resp` 的 paddr、miss、PBMT 和异常构造 `s1_out`；S2 再把 PBMT 与 `PMPRespBundle` 合并为 MMIO/NC/访问错误判断。这样画出两个阶段是有意的：地址翻译的主要结果先进入 S1，而 PMP 的 `mmio` 结果在 S2 的 `s2_mmio` 表达式中参与最终分类。MemBlock 中 DTLB、PTW、PMP 的创建和端口绑定见 14.10.2 的代码片段。

##### 分图 A3：forwarding 旁路查询、byte-lane 优先级和 refill 前递

~~~mermaid
flowchart LR
  S1["A1 / S1<br/>s1_valid、vaddr、paddr、mask<br/>uop、sqIdx"]
  SQ["StoreQueue<br/>io.lsq.forward"]
  SB["Sbuffer<br/>io.sbuffer"]
  UB["Uncache / UBuffer<br/>io.ubuffer；仅 NC-with-data"]
  DCH["DCache / TileLink D<br/>io.tl_d_channel"]
  MQ["MissQueue<br/>io.forward_mshr"]
  MRG["A1 / S2 forwarding merge<br/>s2_fwd_mask / s2_fwd_data<br/>SQ > UBuffer(NC) > Sbuffer"]
  RAW["S2 原始数据合并<br/>DCache resp + D/MSHR data<br/>forwardMask / forwardData"]
  S2["A1 / S2 输出<br/>data、exception、rep_info"]

  S1 -->|"valid / vaddr / paddr / mask<br/>sqIdx / uop"| SQ
  S1 -->|"valid / vaddr / paddr / mask"| SB
  S1 -->|"仅 s1_nc_with_data"| UB
  SQ -->|"forwardMask / forwardData<br/>dataInvalid / addrInvalid / matchInvalid"| MRG
  SB -->|"forwardMask / forwardData<br/>matchInvalid"| MRG
  UB -->|"forwardMask / forwardData<br/>matchInvalid"| MRG
  S1 -->|"valid / mshrid / paddr"| MQ
  DCH -->|"D-channel data / denied / corrupt"| RAW
  MQ -->|"MSHR forwardData / denied / corrupt"| RAW
  MRG --> RAW --> S2

  classDef request fill:#eaf2ff,stroke:#2563eb,color:#111827;
  classDef source fill:#fff7e6,stroke:#d97706,color:#111827;
  classDef merge fill:#eaf8ef,stroke:#15803d,color:#111827;
  class S1,S2 request;
  class SQ,SB,UB,DCH,MQ source;
  class MRG,RAW merge;
~~~

S1 对 SQ、Sbuffer 和 UBuffer 的三组赋值是在同一拍生成的组合查询，源码文本先后顺序不代表串行优先级。SQ 和 Sbuffer 在正常 Load 上都可查询；UBuffer 的 `valid` 额外受 `s1_nc_with_data` 限制。每一路返回的不只是数据，还包含逐 byte 的 mask 以及 `dataInvalid`、`addrInvalid`、`matchInvalid` 等“不能安全使用”的信息。D-channel 和 MSHR 路径又是另一类旁路：它们用 `mshrid` 和 paddr 把尚未回写进 DCache data array 的 refill 数据直接送给 S2。

**代码依据：xiangshan/mem/pipeline/LoadUnit.scala:1387-1399**

~~~scala
  // merge forward result
  // lsq has higher priority than sbuffer
  val s2_fwd_mask = Wire(Vec((VLEN/8), Bool()))
  val s2_fwd_data = Wire(Vec((VLEN/8), UInt(8.W)))
  s2_full_fwd := ((~s2_fwd_mask.asUInt).asUInt & s2_in.mask) === 0.U && !io.lsq.forward.dataInvalid
  // generate XLEN/8 Muxs
  for (i <- 0 until VLEN / 8) {
    s2_fwd_mask(i) := io.lsq.forward.forwardMask(i) || io.sbuffer.forwardMask(i) || io.ubuffer.forwardMask(i)
    s2_fwd_data(i) :=
      Mux(io.lsq.forward.forwardMask(i), io.lsq.forward.forwardData(i),
      Mux(s2_nc_with_data, io.ubuffer.forwardData(i),
      io.sbuffer.forwardData(i)))
  }
~~~

`Wire(Vec(...))` 创建的是同拍组合网络，不是一个保存数据的队列；`for` 在 elaboration 时展开成每个 byte lane 一套 OR 和嵌套 Mux。`s2_fwd_mask(i)` 表示任一路是否覆盖该 byte。数据选择的最外层条件是 SQ 的 `forwardMask(i)`，所以 SQ 优先；未命中 SQ 时，只有 `s2_nc_with_data` 成立才从 UBuffer 取值，否则选 Sbuffer。UBuffer 的 mask 已参与 `s2_fwd_mask`，但其数据选择仍受全局 NC 条件约束，这是图中把它单独标注为 NC-with-data 的原因。

`s2_full_fwd` 的位运算只检查本次 `s2_in.mask` 需要的 byte 是否全部被覆盖，并额外排除 SQ 的 `dataInvalid`；它不是“整个 VLEN 宽度都前递成功”。随后 S2 把 Store/Uncache mask-data、D-channel/MSHR refill 前递与普通 `dcache.resp` 组合为最终读数，同时把旁路失败或总线错误编码进 `rep_info`。S1 三路请求赋值见 14.5.2，D-channel/MSHR 的 `forward()` 调用见 14.10.3 后续 LoadUnit 代码依据。

##### 分图 A4：Queue replay、fast replay 和 rollback 的分流

~~~mermaid
flowchart LR
  S2["A1 / S2<br/>miss、bank conflict、forward fail<br/>TLB miss、RAR/RAW nack、nuke"]
  RI["s2_out.rep_info<br/>cause + mshr_id + tlb_id<br/>data/addr invalid SQ index"]
  S3["A1 / S3<br/>PriorityEncoderOH(cause)<br/>s3_can_enter_lsq_valid"]
  LQ["LoadQueue<br/>io.lsq.ldin：LqWriteBundle"]
  RQ["LoadQueueReplay<br/>allocated / scheduled / blocking<br/>oldest-select + replay_req"]
  WAIT["等待条件解除<br/>TLB hint / D-channel / SQ ready<br/>RAR/RAW 可接收 / redirect 过滤"]
  S0["A1 / S0<br/>io.replay：LsPipelineBundle"]
  FAST["fast replay self-loop<br/>io.fast_rep_out -> io.fast_rep_in"]
  RARRAW["LQ RAR / RAW 查询响应"]
  REDIR["io.rollback：Redirect<br/>flush / flushAfter"]
  BE["后端 / ROB"]

  S2 --> RI --> S3
  S2 -->|"ldld_nuke_query / stld_nuke_query"| RARRAW
  RARRAW -->|"rep_frm_fetch / ready"| S3
  S3 -->|"非 fast-replay 时<br/>io.lsq.ldin"| LQ
  LQ -->|"need_rep entry"| RQ
  RQ --> WAIT
  WAIT -->|"replay_req：uop / vaddr<br/>mshrid / replayCarry / cause"| S0
  S3 -->|"MQ nack、bank conflict、WPU fail<br/>或 nuke fast recovery"| FAST
  FAST --> S0
  S3 -.->|"违反/恢复需从取指重来"| REDIR
  REDIR --> BE

  classDef stage fill:#eaf2ff,stroke:#2563eb,color:#111827;
  classDef queue fill:#fff7e6,stroke:#d97706,color:#111827;
  classDef control fill:#fceff2,stroke:#be123c,color:#111827;
  class S2,RI,S3,S0,FAST stage;
  class LQ,RQ,WAIT,RARRAW queue;
  class REDIR,BE control;
~~~

S2 不会直接把所有失败变成 flush。它先在 `rep_info` 中并列写入原因位和等待所需的辅助信息，例如 `mshr_id`、TLB hint ID、导致前递失败的 SQ index。S3 用 `PriorityEncoderOH` 从多个原因中选一个交给 ReplayQueue；LoadQueueReplay 保留事务、用 `allocated/scheduled/blocking` 记录其生命周期，直到相应的事件解除 blocking，再输出 `io.replay` 回到 LoadUnit S0。它并非一个单一 `Enum` 状态机，而是一组 entry 状态位和选择网络。

**代码依据：xiangshan/mem/pipeline/LoadUnit.scala:1418-1447**

~~~scala
  s2_out.handledByMSHR       := s2_cache_handled
  s2_out.miss                := s2_dcache_miss && s2_troublem
  s2_out.feedbacked          := io.feedback_fast.valid
  s2_out.uop.vpu.vstart      := Mux(s2_in.isLoadReplay || s2_in.isFastReplay, s2_in.uop.vpu.vstart, s2_in.vecVaddrOffset >> s2_in.uop.vpu.veew)

  // Generate replay signal caused by:
  // * st-ld violation check
  // * tlb miss
  // * dcache replay
  // * forward data invalid
  // * dcache miss
  s2_out.rep_info.mem_amb         := s2_mem_amb && s2_troublem
  s2_out.rep_info.tlb_miss        := s2_tlb_miss && s2_troublem
  s2_out.rep_info.fwd_fail        := s2_fwd_fail && s2_troublem
  s2_out.rep_info.dcache_rep      := s2_mq_nack && s2_troublem
  s2_out.rep_info.dcache_miss     := s2_dcache_miss && s2_troublem
  s2_out.rep_info.bank_conflict   := s2_bank_conflict && s2_troublem
  s2_out.rep_info.wpu_fail        := s2_wpu_pred_fail && s2_troublem
  s2_out.rep_info.rar_nack        := s2_rar_nack && s2_troublem
  s2_out.rep_info.raw_nack        := s2_raw_nack && s2_troublem
  s2_out.rep_info.nuke            := s2_nuke && s2_troublem
  s2_out.rep_info.full_fwd        := s2_data_fwded
  s2_out.rep_info.data_inv_sq_idx := io.lsq.forward.dataInvalidSqIdx
  s2_out.rep_info.addr_inv_sq_idx := io.lsq.forward.addrInvalidSqIdx
  s2_out.rep_info.rep_carry       := io.dcache.resp.bits.replayCarry
  s2_out.rep_info.mshr_id         := io.dcache.resp.bits.mshr_id
  s2_out.rep_info.last_beat       := s2_in.paddr(log2Up(refillBytes))
  s2_out.rep_info.debug           := s2_in.uop.debugInfo
  s2_out.rep_info.tlb_id          := io.tlb_hint.id
  s2_out.rep_info.tlb_full        := io.tlb_hint.full
~~~

每条 `:= cause && s2_troublem` 都是独立的硬件 AND，因此 S2 可以在一个 Bundle 中同时记录多个候选原因；S3 的优先编码才将进入 ReplayQueue 的原因收敛为 one-hot。`handledByMSHR`、`miss`、`mshr_id` 等字段与原因一起保存，使 miss replay 可等待正确的 refill，而不是盲目每拍重新访问 DCache。

**代码依据：xiangshan/mem/pipeline/LoadUnit.scala:1813-1817**

~~~scala
  // s3 load fast replay
  io.fast_rep_out.valid := s3_valid && s3_fast_rep
  io.fast_rep_out.bits := s3_in
  io.fast_rep_out.bits.lateKill := s3_rep_frm_fetch
  io.fast_rep_out.bits.delayedLoadError := s3_hw_err
~~~

**代码依据：xiangshan/mem/lsqueue/LoadQueueReplay.scala:544-570**

~~~scala
    replay_req(i).valid             := s2_oldestSel(i).valid
    replay_req(i).bits              := DontCare
    replay_req(i).bits.uop          := s2_replayUop
    replay_req(i).bits.uop.exceptionVec(loadAddrMisaligned) := false.B
    replay_req(i).bits.isvec        := s2_vecReplay.isvec
    replay_req(i).bits.isLastElem   := s2_vecReplay.isLastElem
    replay_req(i).bits.is128bit     := s2_vecReplay.is128bit
    replay_req(i).bits.uop_unit_stride_fof := s2_vecReplay.uop_unit_stride_fof
    replay_req(i).bits.usSecondInv  := s2_vecReplay.usSecondInv
    replay_req(i).bits.elemIdx      := s2_vecReplay.elemIdx
    replay_req(i).bits.alignedType  := s2_vecReplay.alignedType
    replay_req(i).bits.mbIndex      := s2_vecReplay.mbIndex
    replay_req(i).bits.elemIdxInsideVd := s2_vecReplay.elemIdxInsideVd
    replay_req(i).bits.reg_offset   := s2_vecReplay.reg_offset
    replay_req(i).bits.vecActive    := s2_vecReplay.vecActive
    replay_req(i).bits.is_first_ele := s2_vecReplay.is_first_ele
    replay_req(i).bits.mask         := s2_vecReplay.mask
    replay_req(i).bits.vaddr        := vaddrModule.io.rdata(i)
    replay_req(i).bits.isFirstIssue := false.B
    replay_req(i).bits.isLoadReplay := true.B
    replay_req(i).bits.replayCarry  := s2_replayCarry
    replay_req(i).bits.mshrid       := s2_replayMSHRId
    replay_req(i).bits.replacementUpdated := s2_replacementUpdated
    replay_req(i).bits.missDbUpdated := s2_missDbUpdated
    replay_req(i).bits.forward_tlDchannel := s2_replayCauses(LoadReplayCauses.C_DM)
    replay_req(i).bits.schedIndex   := s2_oldestSel(i).bits
    replay_req(i).bits.uop.loadWaitStrict := false.B
~~~

Fast replay 是 LoadUnit 的自环：MemBlock 用 `<>` 将同一实例的 `fast_rep_out` 与 `fast_rep_in` 接起来，S3 因此能把完整的 `LqWriteBundle` 不经 ReplayQueue 再送回 S0。与之不同，普通 queue replay 在 LoadQueueReplay 中恢复保留的 uop、vaddr、`mshrid` 和 `replayCarry`，并显式把 `isLoadReplay` 置真。两种回放不能混为一谈：前者适合快速可重试情形，后者携带等待条件和调度选择。`io.rollback` 又是第三条路径，只在 S3 的 `s3_rep_frm_fetch`、RAR 触发的 `s3_flushPipe` 或非对齐恢复条件成立时输出 `Redirect`；它不是每个 replay 的默认副作用。

##### 分图 B：Sta/Std 分离后怎样在 StoreQueue 和 Sbuffer 合流

~~~mermaid
flowchart LR
  BE["后端 / ROB"]
  STA["StoreUnit<br/>Sta：地址、mask、TLB/PMP"]
  STD["MemExeUnit<br/>Std：store data"]
  DTLB["DTLB + PMP"]
  SP["DCache StorePipe<br/>DCacheStoreIO<br/>tag/meta / prefetch"]
  SQ["StoreQueue entry<br/>allocated / addrvalid / datavalid<br/>allvalid / committed / mmio / nc"]
  SB["Sbuffer entry<br/>line merge + forwarding"]
  MP["DCache MainPipe"]
  LDU["LoadUnit"]
  C["分图 C：LSQ/Uncache"]

  BE -->|"issueSta"| STA
  BE -->|"issueStd"| STD
  STA -->|"TlbRequestIO"| DTLB
  DTLB -->|"PMPRespBundle"| STA
  STA -.->|"DCacheStoreIO: M_PFW<br/>vaddr + s1 paddr<br/>tag/meta probe only"| SP
  SP -.->|"miss / replay response"| STA

  STA -->|"storeAddrIn / storeAddrInRe"| SQ
  STA -->|"st_mask_out -> storeMaskIn"| SQ
  STD -->|"stData -> storeDataIn"| SQ
  SQ -.->|"MMIO / NC request"| C

  SQ -->|"committed DCacheWordReq"| SB
  SB -->|"M_XWR: addr/vaddr/data/mask/id"| MP
  MP -->|"store replay_resp / hit_resp"| SB
  LDU -->|"SQ forward query"| SQ
  SQ -->|"forwardMask / forwardData / invalid"| LDU
  LDU -->|"forward query"| SB
  SB -->|"forwardMask / forwardData"| LDU
  C -->|"SQ commit / dequeue / force_write"| SQ
  STA -->|"stIn / writebackSta"| BE

  classDef exec fill:#eaf2ff,stroke:#2563eb,color:#111827;
  classDef queue fill:#fff7e6,stroke:#d97706,color:#111827;
  classDef cache fill:#eaf8ef,stroke:#15803d,color:#111827;
  classDef control fill:#fceff2,stroke:#be123c,color:#111827;
  class STA,STD exec;
  class SQ,SB,C queue;
  class SP,MP cache;
  class DTLB control;
~~~

图中的两条 DCache 边表达不同请求：Sta 经 `DCacheStoreIO` 进入 StorePipe，只做 Store 地址侧访问；Sbuffer 发出的才是携带 `data` 和 `mask` 的 `M_XWR` cache-line 写请求。StoreQueue entry 用 `addrvalid && datavalid` 计算 `allvalid`，并在提交条件满足后才把普通 Store 交给 Sbuffer；因此 `Std` 的数据不能越过 SQ 直接写 MainPipe。

##### 分图 C：LQ/SQ 联合分配、Replay/违例子模块和 Uncache 双层仲裁

~~~mermaid
flowchart LR
  DIS["Dispatch / ROB<br/>enqLsq / commit / redirect"]
  WRAP["LsqWrapper<br/>needAlloc + lqIdx/sqIdx"]

  subgraph LQS["LoadQueue"]
    VLQ["VirtualLoadQueue<br/>allocated / committed / ptr"]
    LREP["LoadQueueReplay<br/>allocated / scheduled / cause"]
    RAW["LoadQueueRAW<br/>Store-Load violation"]
    RAR["LoadQueueRAR<br/>Load-Load release"]
    LUC["LoadQueueUncache<br/>MMIO / NC Load"]
    LEX["LqExceptionBuffer"]
  end

  SQ["StoreQueue<br/>addr/data ready + forward"]
  LDU["LoadUnit"]
  DREL["DCache release"]
  LQREG["LoadQueueUncache<br/>AddPipelineReg req/resp"]
  ARB["Wrapper Uncache arbiter<br/>pendingstate: idle/load/store<br/>selectLq by robIdx"]
  MSTATE["MemBlock uncacheState<br/>idle/scalar/vector"]
  PREQ["AddPipelineReg<br/>MemBlock request"]
  PRESP["AddPipelineReg<br/>MemBlock response"]
  UC["Uncache entries<br/>valid/inflight/waitSame/waitReturn<br/>uState: idle/inflight/wait_return"]
  IDR["idResp direct path"]
  MMIO["d_mmio_port"]
  BE["后端 / ROB"]

  DIS -->|"needAlloc(0/1)"| WRAP
  WRAP -->|"lqIdx + sqIdx"| VLQ
  WRAP -->|"lqIdx + sqIdx"| SQ
  DIS -.->|"commit / redirect / pendingPtr"| VLQ
  DIS -.->|"pendingMMIOld / pendingPtr"| LUC
  DIS -.->|"scommit / pendingst"| SQ
  LDU -->|"ldu.ldin: LqWriteBundle"| VLQ
  LDU -->|"ldu.ldin"| LREP
  LDU -->|"ldld/stld query"| RAR
  LDU -->|"ldld/stld query"| RAW
  DREL -->|"release"| RAR
  LDU -->|"ldin: MMIO / NC candidate"| LUC
  LDU -->|"ldin / vec feedback"| LEX
  LREP -->|"replay: LsPipelineBundle"| LDU
  SQ -->|"stAddrReady / stDataReady / stIssuePtr"| LREP
  SQ -->|"stAddrReady / stIssuePtr"| RAW
  SQ -->|"forward result"| LDU
  RAW -.->|"nuke_rollback"| BE
  LUC -.->|"nack_rollback"| BE
  LEX -->|"exceptionAddr"| BE

  LUC -->|"UncacheWordIO.req"| LQREG
  LQREG -->|"io.uncache.req"| ARB
  SQ -->|"UncacheWordIO.req"| ARB
  ARB -->|"selected UncacheWordReq"| PREQ
  MSTATE -.->|"gates req / resp"| PREQ
  MSTATE -.->|"gates req / resp"| PRESP
  PREQ -->|"uncache.io.lsq.req"| UC
  UC -->|"TL-UL A/D"| MMIO
  MMIO -->|"bus response"| UC
  UC -->|"UncacheWordResp"| PRESP
  PRESP -->|"resp"| ARB
  UC -->|"idResp（不经 PRESP）"| IDR
  IDR -->|"idResp"| ARB
  ARB -->|"resp / idResp: is2lq"| LQREG
  LQREG -->|"resp / idResp"| LUC
  ARB -->|"is2lq = false"| SQ

  classDef queue fill:#fff7e6,stroke:#d97706,color:#111827;
  classDef state fill:#fceff2,stroke:#be123c,color:#111827;
  classDef external fill:#f1ecff,stroke:#7c3aed,color:#111827;
  class WRAP,VLQ,LREP,RAW,RAR,LUC,LEX,SQ queue;
  class ARB,MSTATE,PREQ,PRESP,UC,IDR state;
  class DIS,LDU,MMIO,BE external;
~~~

这里有三个不同层次的状态，图中刻意没有合并它们：Wrapper 的 `pendingstate` 只决定 LQ/SQ 哪一方暂时占有非 outstanding 请求；MemBlock 的 `uncacheState` 只约束标量/向量请求跨越两级 `AddPipelineReg`；Uncache entry 的 `valid/inflight/waitSame/waitReturn` 才保存每一笔可合并、已发总线、等待返回的事务状态。响应借 `is2lq` 分流回 LQ 或 SQ。

~~~mermaid
stateDiagram-v2
  [*] --> s_idle
  s_idle --> s_load: req.fire, selectLq, non-outstanding
  s_idle --> s_store: req.fire, choose SQ, non-outstanding
  s_load --> s_idle: resp.fire
  s_store --> s_idle: resp.fire
  s_idle --> s_idle: NC request with outstanding enabled
~~~

上面的状态图只对应 `LsqWrapper.pendingstate`，不等同于 Uncache 内部状态。`selectLq` 在 LQ/SQ 同时有效时比较 `robIdx`；NC outstanding 开启时，发出请求后保持 `s_idle`，所以不应把 `s_load/s_store` 误读为每一笔 Uncache entry 的生命周期。

~~~mermaid
stateDiagram-v2
  [*] --> u_idle
  u_idle --> u_inflight: bus.a.fire
  u_inflight --> u_wait_return: bus.d.fire
  u_wait_return --> u_idle: io.lsq.resp.fire
~~~

这是 Uncache 的全局 `uState` 串行令牌；它与每 entry 的 `valid/inflight/waitSame/waitReturn` 并存。MemBlock 的 `uncacheState` 则在分图 C 的 `MSTATE` 节点中显示，其 `s_vector_uncache` 分支虽在当前源码中定义，但此处可见的向量生产者连接被注释，图中不把它虚构为一条已启用的数据边。

##### 分图 D：DCache 的 miss、Probe、writeback 和 TileLink A-E 通道

~~~mermaid
flowchart LR
  LDU["LoadUnit"]
  STU["StoreUnit"]
  SB["Sbuffer"]
  AMO["AtomicsUnit"]

  subgraph DC["DCache"]
    LP["LoadPipe[i]"]
    SP["StorePipe[i]"]
    MP["MainPipe"]
    MRA["MissReqArb<br/>+ MissReadyGen"]
    MQ["MissQueue / MSHR"]
    TLD["DCache forward_D<br/>/ tl_d_channel"]
    PQ["ProbeQueue"]
    WB["WritebackQueue"]
  end

  L2["L2 / TileLink fabric"]

  LDU -->|"DCacheLoadIO.req"| LP
  LP -->|"DCacheLoadIO.resp"| LDU
  LP -->|"miss_req"| MRA
  STU -.->|"DCacheStoreIO: M_PFW"| SP
  SP -->|"store prefetch miss_req（启用时）"| MRA
  SB -->|"io.lsu.store.req"| MP
  AMO -->|"AtomicWordIO"| MP
  MP -->|"miss_req"| MRA
  MRA -->|"missQueue.req"| MQ
  MQ -->|"forward(i) -> forward_mshr"| LDU

  MQ -->|"bus.a: mem_acquire"| L2
  MQ -->|"bus.e: mem_finish"| L2
  L2 -->|"bus.d: Grant / GrantData / CBOAck"| MQ
  L2 -->|"bus.d: GrantData"| TLD
  TLD -->|"forward_D / tl_d_channel"| LDU
  L2 -->|"bus.b: Probe"| PQ
  PQ -->|"pipe_req"| MP
  MP -->|"wb request"| WB
  WB -->|"bus.c: mem_release"| L2
  L2 -->|"bus.d: ReleaseAck"| WB

  classDef lsu fill:#eaf2ff,stroke:#2563eb,color:#111827;
  classDef cache fill:#eaf8ef,stroke:#15803d,color:#111827;
  classDef tl fill:#f1ecff,stroke:#7c3aed,color:#111827;
  class LDU,STU,SB,AMO lsu;
  class LP,SP,MP,MRA,MQ,TLD,PQ,WB cache;
  class L2 tl;
~~~

分图 D 将三类 miss 输入显式汇入 `MissReqArb`：LoadPipe 的 demand miss、MainPipe 的 Store/AMO miss，以及配置启用时 StorePipe 的预取 miss。D 通道不是单一返回线：Grant/GrantData/CBOAck 进入 MissQueue，ReleaseAck 进入 WritebackQueue；B 通道 Probe 先进入 ProbeQueue，再以 `pipe_req` 参与 MainPipe 仲裁。

##### 分图 E：特殊路径怎样复用普通 LSU 端口

~~~mermaid
flowchart TB
  BE["后端 / ROB"]
  DTLB["DTLB + PMP"]
  LSU["普通 LSU 端口<br/>LoadUnit / StoreUnit / LSQ / Sbuffer / DCache"]
  MP["DCache MainPipe"]

  subgraph AMOLANE["原子：AtomicsUnit"]
    AIN["Sta/Hybrid 改道 + Std 数据"]
    AFSM["9-state FSM<br/>invalid -> tlb_flush -> pm -> wait_flush<br/>cache_req -> cache_resp -> cache_resp_latch<br/>finish -> finish2"]
    AOUT["AtomicWordIO / out"]
    AIN --> AFSM --> AOUT
  end

  subgraph MALANE["非对齐：Load/Store MisalignBuffer"]
    MENQ["misalign_enq"]
    MFSM["Load: idle/split/req/resp<br/>comb_wakeup_rep/wb"]
    MSPLIT["splitLoadReq / splitStoreReq"]
    MRESP["splitLoadResp / splitStoreResp"]
    MWB["writeBack / vecWriteBack"]
    MENQ --> MFSM --> MSPLIT --> MRESP --> MFSM --> MWB
  end

  subgraph VLANE["向量：VSplit / VMergeBuffer"]
    VIN["issueVldu"]
    VSPLIT["VLSplit / VSSplit<br/>split pipeline + split buffer"]
    VFLOW["vecldin / vecstin"]
    VMERGE["VL/VS MergeBuffer<br/>FreeList + entries"]
    VWB["writebackVldu / feedback"]
    VIN --> VSPLIT --> VFLOW --> VMERGE --> VWB
  end

  subgraph HYLANE["Hybrid：HybridUnit"]
    HIN["issueHya -> lsin"]
    HIO["ldu_io: dcache/sbuffer/ubuffer/lsq<br/>stu_io: dcache/lsq/st_mask_out"]
    HOUT["ldout / stout / wakeup"]
    HIN --> HIO --> HOUT
  end

  AIN -->|"TlbRequestIO + flush_sbuffer"| DTLB
  DTLB --> AFSM
  AOUT -->|"AtomicWordIO"| MP
  AOUT -->|"out"| BE

  MSPLIT -->|"re-enter LoadUnit / StoreUnit"| LSU
  LSU -->|"LqWriteBundle response"| MRESP
  MWB --> BE

  VFLOW -->|"LoadUnit.vecldin / StoreUnit.vecstin"| LSU
  VWB --> BE

  HIO -->|"DTLB/PMP + DCache + LSQ/Sbuffer/Uncache"| LSU
  HOUT --> BE

  classDef special fill:#fceff2,stroke:#be123c,color:#111827;
  classDef common fill:#eaf2ff,stroke:#2563eb,color:#111827;
  classDef output fill:#eaf8ef,stroke:#15803d,color:#111827;
  class AIN,AFSM,AOUT,MENQ,MFSM,MSPLIT,MRESP,MWB,VIN,VSPLIT,VFLOW,VMERGE,VWB,HIN,HIO,HOUT special;
  class DTLB,LSU,MP common;
  class BE output;
~~~

分图 E 的共同点是“保存特殊事务的状态，然后回用普通端口”，而不是另建一套数据缓存：AtomicsUnit 在自身 FSM 中等待 TLB、PMP 和 Sbuffer flush 后才发 `AtomicWordIO`；MisalignBuffer 把子请求重新送给指定 LoadUnit/StoreUnit；VSplit 将 vector uop 变成经 `vecldin/vecstin` 执行的 flow，再由 MergeBuffer 合并；HybridUnit 的 `ldu_io/stu_io` 则直接连到同一套 DCache、LSQ、Sbuffer、Uncache、DTLB 和 PMP 端口。

### 14.10.2 共享控制面：redirect、DTLB、PMP 和 PTW

**代码依据：xiangshan/mem/MemBlock.scala:686-806**

~~~scala
  // dtlb
  val dtlb_ld_tlb_ld = Module(new TLBNonBlock(LduCnt + HyuCnt + 1, 2, ldtlbParams))
  val dtlb_st_tlb_st = Module(new TLBNonBlock(StaCnt, 1, sttlbParams))
  val dtlb_prefetch_tlb_prefetch = Module(new TLBNonBlock(2, 2, pftlbParams))
  val dtlb_ld = Seq(dtlb_ld_tlb_ld.io)
  val dtlb_st = Seq(dtlb_st_tlb_st.io)
  val dtlb_prefetch = Seq(dtlb_prefetch_tlb_prefetch.io)
  /* tlb vec && constant variable */
  val dtlb = dtlb_ld ++ dtlb_st ++ dtlb_prefetch
  val (dtlb_ld_idx, dtlb_st_idx, dtlb_pf_idx) = (0, 1, 2)
  val TlbSubSizeVec = Seq(LduCnt + HyuCnt + 1, StaCnt, 2) // (load + hyu + stream pf, store, sms+l2bop)
  val DTlbSize = TlbSubSizeVec.sum
  val TlbStartVec = TlbSubSizeVec.scanLeft(0)(_ + _).dropRight(1)
  val TlbEndVec = TlbSubSizeVec.scanLeft(0)(_ + _).drop(1)

  val ptwio = Wire(new VectorTlbPtwIO(DTlbSize))
  val dtlb_reqs = dtlb.map(_.requestor).flatten
  val dtlb_pmps = dtlb.map(_.pmp).flatten
  val dtlb_pmp_modes = dtlb.map(_.pmpMode).flatten
  dtlb.map(_.hartId := io.hartId)
  dtlb.map(_.sfence := sfence)
  dtlb.map(_.csr := tlbcsr)
  dtlb.map(_.flushPipe.map(a => a := false.B)) // non-block doesn't need
  dtlb.map(_.redirect := redirect)
  dtlb.map(_.robPendingPtr := io.ooo_to_mem.lsqio.pendingPtr)

  // pmp
  val pmp = Module(new PMP())
  pmp.io.distribute_csr <> csrCtrl.distribute_csr

  val pmp_checkers = Seq.fill(DTlbSize)(Module(new PMPChecker(4, leaveHitMux = true)))
  val pmp_check = pmp_checkers.map(_.io)
  for (((p, d), pmpMode) <- pmp_check.zip(dtlb_pmps).zip(dtlb_pmp_modes)) {
    if (HasBitmapCheck) {
      if (KeyIDBits > 0) {
        p.apply(tlbcsr.mbmc.KEYIDEN.asBool, tlbcsr.mbmc.CMODE.asBool, pmpMode, tlbcsr.priv.debug, pmp.io.pmp, pmp.io.pma, d)
      } else {
        p.apply(tlbcsr.mbmc.CMODE.asBool, pmpMode, tlbcsr.priv.debug, pmp.io.pmp, pmp.io.pma, d)
      }
    } else {
      p.apply(pmpMode, tlbcsr.priv.debug, pmp.io.pmp, pmp.io.pma, d)
    }
    require(p.req.bits.size.getWidth == d.bits.size.getWidth)
  }
~~~

这里的 `Seq`/`flatten` 是 elaboration 期的端口编排：Load、Hybrid、Store 和预取各自得到 DTLB 请求端口，`dtlb_reqs` 是这些端口的扁平集合；每个 DTLB 端口对应一个 `PMPChecker`。运行时，访存流水线给 DTLB 的请求含虚拟地址和操作类型，DTLB 返回翻译/异常信息，同时其 `pmp` 输出被对应 checker 使用；PMP/PMA 配置来自 `csrCtrl.distribute_csr`。`sfence`、CSR、`redirect` 和 ROB pending 指针被广播给 DTLB，因此翻译与队列生命周期共享同一套恢复边界。

这里不应把 DTLB、PMP 和 PTW 说成一个单一 FSM。代码实际例化了三组 `TLBNonBlock`，用 `VectorTlbPtwIO(DTlbSize)` 汇聚其 PTW 事务；每组的 miss、替换和 PTW 返回状态封装在相应模块内。对于 LoadUnit/StoreUnit 来说，可观察的接口只是 `TlbRequestIO` 请求/响应和 `PMPRespBundle` 响应。

### 14.10.3 LoadUnit：执行、旁路和生命周期回路

**代码依据：xiangshan/mem/pipeline/LoadUnit.scala:120-215**

~~~scala
  val io = IO(new Bundle() {
    // control
    val redirect      = Flipped(ValidIO(new Redirect))
    val csrCtrl       = Flipped(new CustomCSRCtrlIO)

    // int issue path
    val ldin          = Flipped(Decoupled(new MemExuInput))
    val ldout         = Decoupled(new MemExuOutput)

    // vec issue path
    val vecldin = Flipped(Decoupled(new VecPipeBundle))
    val vecldout = Decoupled(new VecPipelineFeedbackIO(isVStore = false))

    // misalignBuffer issue path
    val misalign_ldin = Flipped(Decoupled(new LsPipelineBundle))
    val misalign_ldout = Valid(new LqWriteBundle)

    // data path
    val tlb           = new TlbRequestIO(2)
    val pmp           = Flipped(new PMPRespBundle()) // arrive same to tlb now
    val dcache        = new DCacheLoadIO
    val sbuffer       = new LoadForwardQueryIO
    val ubuffer       = new LoadForwardQueryIO
    val lsq           = new LoadToLsqIO
    val tl_d_channel  = Input(new DcacheToLduForwardIO)
    val forward_mshr  = Flipped(new LduToMissqueueForwardIO)
   // val refill        = Flipped(ValidIO(new Refill))
    val l2_hint       = Input(Valid(new L2ToL1Hint))
    val tlb_hint      = Flipped(new TlbHintReq)

    // rs feedback
    val wakeup = ValidIO(new DynInst)
    val feedback_fast = ValidIO(new RSFeedback) // stage 2
    val feedback_slow = ValidIO(new RSFeedback) // stage 3
    val ldCancel = Output(new LoadCancelIO()) // use to cancel the uops waked by this load, and cancel load

    // queue-based replay
    val replay       = Flipped(Decoupled(new LsPipelineBundle))
    val lq_rep_full  = Input(Bool())

    // Load fast replay path
    val fast_rep_in  = Flipped(Decoupled(new LqWriteBundle))
    val fast_rep_out = Decoupled(new LqWriteBundle)

    // to misalign buffer
    val misalign_enq = new MisalignBufferEnqIO

    // Load RAR rollback
    val rollback = Valid(new Redirect)
  })
~~~

LoadUnit 处理可缓存标量 Load，也接收向量 flow、非对齐拆分 flow、Replay flow 和 fast-replay flow。它没有一个 `Enum` 编码的请求状态机；源码中的 S0--S3 流水线寄存器、`valid`、`ready`、`fire` 和 replay/rollback 条件共同构成其跨拍状态。每一条 LoadUnit 的重要端口可按下表追踪：

| 端口组 | 从哪里来 | LoadUnit 做什么 | 到哪里去 |
| --- | --- | --- | --- |
| `ldin` / `vecldin` / `misalign_ldin` / `replay` / `fast_rep_in` | 后端 Issue、VLSU、非对齐 buffer、LoadQueueReplay、自环 fast replay | S0 仲裁一个可执行 flow，计算地址、mask、uop 和请求类别 | DTLB、PMP、DCache 与各前递查询。 |
| `tlb`、`pmp` | MemBlock 的 DTLB 请求端口和对应 PMPChecker | 请求地址翻译，并把翻译/权限/属性结果合并入流水线元数据 | 后续 DCache、异常、MMIO/NC 和 replay 判定。 |
| `dcache`、`sbuffer`、`ubuffer`、`lsq.forward`、`tl_d_channel`、`forward_mshr` | DCache、SBuffer、Uncache、StoreQueue 和 MissQueue | 比较可前递 byte lane，选择数据或形成无法安全完成的原因 | S2/S3 的数据、`rep_info`、取消或恢复路径。 |
| `lsq.ldin`、`ldout`、`wakeup`、`ldCancel`、`rollback` | LoadUnit 本身的 S3 结果 | 将 Load 生命周期信息写入 LQ，并向后端提供结果、唤醒、取消和 redirect 候选 | LoadQueue 的多个子模块和后端。 |

**代码依据：xiangshan/mem/MemBlock.scala:850-1029**

~~~scala
  // LoadUnit
  val correctMissTrain = Constantin.createRecord(s"CorrectMissTrain$hartId", initValue = false)

  for (i <- 0 until LduCnt) {
    loadUnits(i).io.redirect <> redirect

    // get input form dispatch
    loadUnits(i).io.ldin <> io.ooo_to_mem.issueLda(i)
    loadUnits(i).io.robDeqIdx <> io.ooo_to_mem.lsqio.pendingPtr
    loadUnits(i).io.feedback_slow <> io.mem_to_ooo.ldaIqFeedback(i).feedbackSlow
    io.mem_to_ooo.ldaIqFeedback(i).feedbackFast := DontCare
    loadUnits(i).io.correctMissTrain := correctMissTrain
    io.mem_to_ooo.ldCancel.drop(HyuCnt)(i) := loadUnits(i).io.ldCancel
    io.mem_to_ooo.wakeup.drop(HyuCnt)(i) := loadUnits(i).io.wakeup

    // vector
    if (i < VlduCnt) {
      loadUnits(i).io.vecldout.ready := false.B
    } else {
      loadUnits(i).io.vecldin.valid := false.B
      loadUnits(i).io.vecldin.bits := DontCare
      loadUnits(i).io.vecldout.ready := false.B
    }

    // fast replay
    loadUnits(i).io.fast_rep_in <> loadUnits(i).io.fast_rep_out

    // forward
    loadUnits(i).io.lsq.forward <> lsq.io.forward(i)
    loadUnits(i).io.sbuffer <> sbuffer.io.forward(i)
    loadUnits(i).io.ubuffer <> uncache.io.forward(i)
    loadUnits(i).io.tl_d_channel := dcache.io.lsu.forward_D(i)
    loadUnits(i).io.forward_mshr <> dcache.io.lsu.forward_mshr(i)
    // ld-ld violation check
    loadUnits(i).io.lsq.ldld_nuke_query <> lsq.io.ldu.ldld_nuke_query(i)
    loadUnits(i).io.lsq.stld_nuke_query <> lsq.io.ldu.stld_nuke_query(i)
    // loadqueue old ptr
    loadUnits(i).io.lsq.lqDeqPtr := lsq.io.lqDeqPtr
    loadUnits(i).io.csrCtrl       <> csrCtrl
    // dtlb
    loadUnits(i).io.tlb <> dtlb_reqs.take(LduCnt)(i)
    // pmp
    loadUnits(i).io.pmp <> pmp_check(i).resp
    // st-ld violation query
    val stld_nuke_query = storeUnits.map(_.io.stld_nuke_query) ++ hybridUnits.map(_.io.stu_io.stld_nuke_query)
    for (s <- 0 until StorePipelineWidth) {
      loadUnits(i).io.stld_nuke_query(s) := stld_nuke_query(s)
    }
    loadUnits(i).io.lq_rep_full <> lsq.io.lq_rep_full

    // passdown to lsq (load s2)
    lsq.io.ldu.ldin(i) <> loadUnits(i).io.lsq.ldin
    if (i == UncacheWBPort) {
      lsq.io.ldout(i) <> loadUnits(i).io.lsq.uncache
    } else {
      lsq.io.ldout(i).ready := true.B
      loadUnits(i).io.lsq.uncache.valid := false.B
      loadUnits(i).io.lsq.uncache.bits := DontCare
    }
    lsq.io.ld_raw_data(i) <> loadUnits(i).io.lsq.ld_raw_data
    lsq.io.ncOut(i) <> loadUnits(i).io.lsq.nc_ldin

    // connect misalignBuffer
    loadMisalignBuffer.io.enq(i) <> loadUnits(i).io.misalign_enq

    if (i == MisalignWBPort) {
      loadUnits(i).io.misalign_ldin  <> loadMisalignBuffer.io.splitLoadReq
      loadUnits(i).io.misalign_ldout <> loadMisalignBuffer.io.splitLoadResp
    } else {
      loadUnits(i).io.misalign_ldin.valid := false.B
      loadUnits(i).io.misalign_ldin.bits := DontCare
    }
~~~

这段循环的 `i` 是 elaboration 索引，因此它生成 `LduCnt` 份相同拓扑。对每条通路，`issueLda(i)` 是外部输入；DCache、SQ、SBuffer、Uncache 和 MissQueue 都不是串行的“备选下一级”，而是并行提供地址匹配、数据或危险信息。LoadUnit 再用此前 14.5 节的 byte-lane 掩码和优先级逻辑决定是否能安全使用数据。`lsq.io.ldu.ldin(i)` 是 Load 完成/重放信息进入 LoadQueue 的位置；`lsq.io.replay(i)`（在本文件稍前处连接）则把已保存的 replay 事务回灌到 LoadUnit。

### 14.10.4 LSQ：谁保存顺序，谁检查违例，谁重发请求

`LsqWrapper` 是 LoadQueue 与 StoreQueue 的共同封装，而不是另一层普通 FIFO。它同时完成联合分配、交叉索引、共享 ROB 接口、Load 前递服务和 Uncache 仲裁。这里的 LQ/SQ entry 状态分属两个子模块，Wrapper 本身只为少数跨模块协议保存状态。

**代码依据：xiangshan/mem/lsqueue/LSQWrapper.scala:142-243**

~~~scala
  val loadQueue = Module(new LoadQueue)
  val storeQueue = Module(new StoreQueue)

  storeQueue.io.hartId := io.hartId
  storeQueue.io.uncacheOutstanding := io.uncacheOutstanding
  storeQueue.io.wfi <> io.wfi

  // io.enq logic
  // LSQ: send out canAccept when both load queue and store queue are ready
  // Dispatch: send instructions to LSQ only when they are ready
  io.enq.canAccept := loadQueue.io.enq.canAccept && storeQueue.io.enq.canAccept
  io.lqCanAccept := loadQueue.io.enq.canAccept
  io.sqCanAccept := storeQueue.io.enq.canAccept
  loadQueue.io.enq.sqCanAccept := storeQueue.io.enq.canAccept
  storeQueue.io.enq.lqCanAccept := loadQueue.io.enq.canAccept
  io.lqDeqPtr := loadQueue.io.lqDeqPtr
  io.sqDeqPtr := storeQueue.io.sqDeqPtr
  io.sqCommitRobIdx := storeQueue.io.sqCommitRobIdx
  io.sqCommitUopIdx := storeQueue.io.sqCommitUopIdx
  io.sqDeqIsVec := storeQueue.io.sqDeqIsVec
  io.lqDeqRobIdx := loadQueue.io.lqDeqRobIdx
  io.lqDeqUopIdx := loadQueue.io.lqDeqUopIdx
  io.sqCommitPtr    := storeQueue.io.sqCommitPtr
  for (i <- io.enq.req.indices) {
    loadQueue.io.enq.needAlloc(i)      := io.enq.needAlloc(i)(0)
    loadQueue.io.enq.req(i).valid      := io.enq.needAlloc(i)(0) && io.enq.req(i).valid
    loadQueue.io.enq.req(i).bits       := io.enq.req(i).bits
    loadQueue.io.enq.req(i).bits.sqIdx := storeQueue.io.enq.resp(i)

    storeQueue.io.enq.needAlloc(i)      := io.enq.needAlloc(i)(1)
    storeQueue.io.enq.req(i).valid      := io.enq.needAlloc(i)(1) && io.enq.req(i).valid
    storeQueue.io.enq.req(i).bits       := io.enq.req(i).bits
    storeQueue.io.enq.req(i).bits.lqIdx := loadQueue.io.enq.resp(i)

    io.enq.resp(i).lqIdx := loadQueue.io.enq.resp(i)
    io.enq.resp(i).sqIdx := storeQueue.io.enq.resp(i)
  }

  //  load queue wiring
  loadQueue.io.redirect            <> io.brqRedirect
  loadQueue.io.vecFeedback           <> io.ldvecFeedback
  loadQueue.io.ldu                 <> io.ldu
  loadQueue.io.ldout               <> io.ldout
  loadQueue.io.ld_raw_data         <> io.ld_raw_data
  loadQueue.io.ncOut               <> io.ncOut
  loadQueue.io.rob                 <> io.rob
  loadQueue.io.nuke_rollback       <> io.nuke_rollback
  loadQueue.io.nack_rollback       <> io.nack_rollback
  loadQueue.io.replay              <> io.replay
 // loadQueue.io.refill              <> io.refill
  loadQueue.io.tl_d_channel        <> io.tl_d_channel
  loadQueue.io.release             <> io.release
  loadQueue.io.exceptionAddr.isStore := DontCare
  loadQueue.io.loadMisalignFull    := io.loadMisalignFull
  loadQueue.io.lqCancelCnt         <> io.lqCancelCnt
  loadQueue.io.sq.stAddrReadySqPtr <> storeQueue.io.stAddrReadySqPtr
  loadQueue.io.sq.stAddrReadyVec   <> storeQueue.io.stAddrReadyVec
  loadQueue.io.sq.stDataReadySqPtr <> storeQueue.io.stDataReadySqPtr
  loadQueue.io.sq.stDataReadyVec   <> storeQueue.io.stDataReadyVec
  loadQueue.io.sq.stIssuePtr       <> storeQueue.io.stIssuePtr
  loadQueue.io.sq.sqEmpty          <> storeQueue.io.sqEmpty
~~~

`io.enq.canAccept` 是 LQ 与 SQ 可接收条件的组合 AND，所以带有两类表项需求的派发不会只进入其中一个队列。分配时，LoadQueue 的 entry 得到 SQ 返回的 `sqIdx`，StoreQueue 的 entry 得到 LQ 返回的 `lqIdx`；这些交叉索引随后使 Load 的前递、RAW/RAR 检查和 replay 能指向正确的更老请求。另一方面，LoadQueue 从 StoreQueue 接收地址/数据已就绪向量、指针和 `sqEmpty`，而非复制一份 StoreQueue 的状态。

**代码依据：xiangshan/mem/lsqueue/LoadQueue.scala:214-344**

~~~scala
  val loadQueueRAR = Module(new LoadQueueRAR)  //  read-after-read violation
  val loadQueueRAW = Module(new LoadQueueRAW)  //  read-after-write violation
  val loadQueueReplay = Module(new LoadQueueReplay)  //  enqueue if need replay
  val virtualLoadQueue = Module(new VirtualLoadQueue)  //  control state
  val exceptionBuffer = Module(new LqExceptionBuffer) // exception buffer
  val uncacheBuffer = Module(new LoadQueueUncache) // uncache
  /**
   * LoadQueueRAR
   */
  loadQueueRAR.io.redirect  <> io.redirect
  loadQueueRAR.io.release   <> io.release
  loadQueueRAR.io.ldWbPtr   <> virtualLoadQueue.io.ldWbPtr
  for (w <- 0 until LoadPipelineWidth) {
    loadQueueRAR.io.query(w).req    <> io.ldu.ldld_nuke_query(w).req // from load_s1
    loadQueueRAR.io.query(w).resp   <> io.ldu.ldld_nuke_query(w).resp // to load_s2
    loadQueueRAR.io.query(w).revoke := io.ldu.ldld_nuke_query(w).revoke // from load_s3
  }

  /**
   * LoadQueueRAW
   */
  loadQueueRAW.io.redirect         <> io.redirect
  loadQueueRAW.io.storeIn          <> io.sta.storeAddrIn
  loadQueueRAW.io.stAddrReadySqPtr <> io.sq.stAddrReadySqPtr
  loadQueueRAW.io.stIssuePtr       <> io.sq.stIssuePtr
  for (w <- 0 until LoadPipelineWidth) {
    loadQueueRAW.io.query(w).req    <> io.ldu.stld_nuke_query(w).req // from load_s1
    loadQueueRAW.io.query(w).resp   <> io.ldu.stld_nuke_query(w).resp // to load_s2
    loadQueueRAW.io.query(w).revoke := io.ldu.stld_nuke_query(w).revoke // from load_s3
  }

  /**
   * VirtualLoadQueue
   */
  virtualLoadQueue.io.redirect      <> io.redirect
  virtualLoadQueue.io.vecCommit     <> io.vecFeedback
  virtualLoadQueue.io.enq           <> io.enq
  virtualLoadQueue.io.ldin          <> io.ldu.ldin // from load_s3
  virtualLoadQueue.io.lqFull        <> io.lqFull
  virtualLoadQueue.io.lqDeq         <> io.lqDeq
  virtualLoadQueue.io.lqCancelCnt   <> io.lqCancelCnt
  virtualLoadQueue.io.lqEmpty       <> io.lqEmpty
  virtualLoadQueue.io.ldWbPtr       <> io.lqDeqPtr
  virtualLoadQueue.io.lqDeqRobIdx   <> io.lqDeqRobIdx
  virtualLoadQueue.io.lqDeqUopIdx   <> io.lqDeqUopIdx

  /**
   * Load uncache buffer
   */
  uncacheBuffer.io.redirect <> io.redirect
  uncacheBuffer.io.mmioOut <> io.ldout
  uncacheBuffer.io.ncOut <> io.ncOut
  uncacheBuffer.io.mmioRawData <> io.ld_raw_data
  uncacheBuffer.io.rob <> io.rob
  uncacheBuffer.io.uncache <> io.uncache

  for ((buff, w) <- uncacheBuffer.io.req.zipWithIndex) {
    // from load_s3
    val ldinBits = io.ldu.ldin(w).bits
    buff.valid := io.ldu.ldin(w).valid && !ldinBits.nc_with_data
    buff.bits := ldinBits
  }

  io.nuke_rollback := loadQueueRAW.io.rollback
  io.nack_rollback(0) := uncacheBuffer.io.rollback

  /**
   * LoadQueueReplay
   */
  loadQueueReplay.io.redirect         <> io.redirect
  loadQueueReplay.io.enq              <> io.ldu.ldin // from load_s3
  loadQueueReplay.io.storeAddrIn      <> io.sta.storeAddrIn // from store_s1
  loadQueueReplay.io.storeDataIn      <> io.std.storeDataIn // from store_s0
  loadQueueReplay.io.replay           <> io.replay
  //loadQueueReplay.io.refill           <> io.refill
  loadQueueReplay.io.tl_d_channel     <> io.tl_d_channel
  loadQueueReplay.io.stAddrReadySqPtr <> io.sq.stAddrReadySqPtr
  loadQueueReplay.io.stAddrReadyVec   <> io.sq.stAddrReadyVec
  loadQueueReplay.io.stDataReadySqPtr <> io.sq.stDataReadySqPtr
  loadQueueReplay.io.stDataReadyVec   <> io.sq.stDataReadyVec
  loadQueueReplay.io.sqEmpty          <> io.sq.sqEmpty
  loadQueueReplay.io.lqFull           <> io.lq_rep_full
  loadQueueReplay.io.ldWbPtr          <> virtualLoadQueue.io.ldWbPtr
  loadQueueReplay.io.robDeqPtr        <> io.rob.pendingPtr
  loadQueueReplay.io.rarFull          <> loadQueueRAR.io.lqFull
  loadQueueReplay.io.rawFull          <> loadQueueRAW.io.lqFull
~~~

LoadQueue 由以下子模块组成；它们多以 entry 位、FreeList、指针和寄存器阵列保存状态，而不是共用一个 `state: UInt`：

| 子模块 | 接收的事务 | 保存/计算的核心状态 | 结果去向 |
| --- | --- | --- | --- |
| `VirtualLoadQueue` | 派发分配、Load S3 生命周期结果、向量提交反馈 | `allocated`、ROB/uop 索引、提交位和环形入/出队指针 | 输出 LQ 空/满、deq 指针、取消数和最老可释放 Load。 |
| `LoadQueueRAW` | Store 地址事件和 Load 的 st-ld query | 针对可能与更老 Store 冲突的 Load entry；按年龄选择最老违例 | `rollback` 送 `nuke_rollback`，或通过 `ready`/query 影响 Load replay。 |
| `LoadQueueRAR` | DCache `release` 与 Load 的 ld-ld query | 尚需观察 release 的 Load entry 及部分物理地址 | query 响应回 LoadUnit，必要时由 LoadUnit 形成 rollback。 |
| `LoadQueueReplay` | Load S3 的 `LqWriteBundle`、Store 地址/数据就绪、TL-D 与 TLB hint | replay entry、原因、阻塞 SQ 指针/MSHR/TLB 线索和调度位 | 选择一条可重试的 `LsPipelineBundle` 经 `io.replay` 回到 LoadUnit。 |
| `LoadQueueUncache` | 非缓存 Load 的 S3 信息、ROB pending 状态、Uncache 返回 | MMIO/NC Load entry 与其请求/响应状态 | `mmioOut`、`ncOut`、原始数据和无法分配时的 rollback。 |
| `LqExceptionBuffer` | 标量/向量 Load 异常和 Uncache 异常 | 最老未冲刷的异常地址/扩展信息 | 通过 `exceptionAddr` 交给 MemBlock，再送后端异常处理。 |

StoreQueue 则保存 Store 的地址、数据、掩码和提交状态。它输出 `forward` 给所有 LoadUnit，输出 `sbuffer` 给 SBuffer，输出 `uncache` 给 Wrapper 的 Uncache 仲裁器。其 per-entry `allocated`、`addrvalid`、`datavalid`、`committed` 等状态以及 RAW/Replay/NC/MMIO 状态机已经在 14.4、14.6 和 14.8 节逐段展开；在这张互连图中，应把它理解为“Store 的事实来源”，而不是只把它看作写数据的 FIFO。

### 14.10.5 Store：Sta/Std、StoreQueue、SBuffer 与 DCache 的两条路径

**代码依据：xiangshan/mem/MemBlock.scala:1239-1529**

~~~scala
  // StoreUnit
  for (i <- 0 until StdCnt) {
    stdExeUnits(i).io.flush <> redirect
    stdExeUnits(i).io.in.valid := io.ooo_to_mem.issueStd(i).valid
    io.ooo_to_mem.issueStd(i).ready := stdExeUnits(i).io.in.ready
    stdExeUnits(i).io.in.bits := io.ooo_to_mem.issueStd(i).bits
  }

  for (i <- 0 until StaCnt) {
    val stu = storeUnits(i)

    stu.io.redirect      <> redirect
    stu.io.csrCtrl       <> csrCtrl
    stu.io.dcache        <> dcache.io.lsu.sta(i)
    stu.io.feedback_slow <> io.mem_to_ooo.staIqFeedback(i).feedbackSlow
    stu.io.stin         <> io.ooo_to_mem.issueSta(i)
    stu.io.lsq          <> lsq.io.sta.storeAddrIn(i)
    stu.io.lsq_replenish <> lsq.io.sta.storeAddrInRe(i)
    // dtlb
    stu.io.tlb          <> dtlb_st.head.requestor(i)
    stu.io.pmp          <> pmp_check(LduCnt + HyuCnt + 1 + i).resp
    stu.io.sqCommitPtr     <> lsq.io.sqCommitPtr
    stu.io.sqCommitUopIdx  <> lsq.io.sqCommitUopIdx
    stu.io.sqCommitRobIdx  <> lsq.io.sqCommitRobIdx

    // prefetch
    stu.io.prefetch_req <> sbuffer.io.store_prefetch(i)

    // Lsq to sta unit
    lsq.io.sta.storeMaskIn(i) <> stu.io.st_mask_out

    // connect misalignBuffer
    storeMisalignBuffer.io.enq(i) <> stu.io.misalign_enq

    // Lsq to std unit's rs
    if (i < VstuCnt){
      when (vsSplit(i).io.vstd.get.valid) {
        lsq.io.std.storeDataIn(i).valid := true.B
        lsq.io.std.storeDataIn(i).bits := vsSplit(i).io.vstd.get.bits
        stData(i).ready := false.B
      }.otherwise {
        lsq.io.std.storeDataIn(i).valid := stData(i).valid && !st_data_atomics(i)
        lsq.io.std.storeDataIn(i).bits.uop := stData(i).bits.uop
        lsq.io.std.storeDataIn(i).bits.data := stData(i).bits.data
        lsq.io.std.storeDataIn(i).bits.mask.map(_ := 0.U)
        lsq.io.std.storeDataIn(i).bits.vdIdx.map(_ := 0.U)
        lsq.io.std.storeDataIn(i).bits.vdIdxInField.map(_ := 0.U)
        lsq.io.std.storeDataIn(i).bits.vecDebug.map(_ := DontCare)
        stData(i).ready := true.B
      }
    }

  lsq.io.release        := dcache.io.lsu.release
  lsq.io.lqCancelCnt <> io.mem_to_ooo.lqCancelCnt
  lsq.io.sqCancelCnt <> io.mem_to_ooo.sqCancelCnt
  lsq.io.lqDeq <> io.mem_to_ooo.lqDeq
  lsq.io.sqDeq <> io.mem_to_ooo.sqDeq
  // Todo: assign these
  io.mem_to_ooo.sqDeqPtr := lsq.io.sqDeqPtr
  io.mem_to_ooo.lqDeqPtr := lsq.io.lqDeqPtr
  lsq.io.tl_d_channel <> dcache.io.lsu.tl_d_channel

  // LSQ to store buffer
  lsq.io.sbuffer        <> sbuffer.io.in
  sbuffer.io.in(0).valid := lsq.io.sbuffer(0).valid || vSegmentUnit.io.sbuffer.valid
  sbuffer.io.in(0).bits  := Mux1H(Seq(
    vSegmentUnit.io.sbuffer.valid -> vSegmentUnit.io.sbuffer.bits,
    lsq.io.sbuffer(0).valid       -> lsq.io.sbuffer(0).bits
  ))
  vSegmentUnit.io.sbuffer.ready := sbuffer.io.in(0).ready
  lsq.io.sqEmpty        <> sbuffer.io.sqempty
  dcache.io.force_write := lsq.io.force_write
~~~

一条普通标量 Store 的地址和数据先分开流动：`issueSta` 进入 StoreUnit，StoreUnit 在 S0 计算地址、发 DTLB/PMP 请求并给 StoreQueue 写地址/掩码；`issueStd` 进入独立的 `MemExeUnit`，其结果用 `lsq.io.std.storeDataIn` 写入同一个 StoreQueue entry。StoreQueue 的 `allvalid`、`committed` 等 entry 状态决定何时能向 SBuffer 交付一个完整、已提交的 Store。

`stu.io.dcache <> dcache.io.lsu.sta(i)` 是另一条地址侧路径。它对应 DCache `StorePipe`，不能因此推断 StoreUnit 直接把 `data/mask` 写入缓存。SBuffer 则接收来自 LSQ 的 `DCacheWordReqWithVaddrAndPfFlag`，合并成 cache-line 数据，并通过 `dcache.io.lsu.store` 送 MainPipe。SBuffer 的 entry 状态和自身排空状态在 14.4.3 已给出；它向 LoadUnit 提供 `forward`，向 DCache 提供最终的 line write 请求。

**代码依据：xiangshan/cache/dcache/storepipe/StorePipe.scala:52-197**

~~~scala
/** Non-Blocking Store Dcache Pipeline
  *
  *  Associated with STA Pipeline
  *  Issue a store write prefetch to dcache if miss (if EnableStorePrefetchAtIssue)
  *  Issue a prefetch train request to sms if miss (if EnableStorePrefetchSMS)
  *  Recieve prefetch request, Issue a store write prefetch to dcache if miss (if EnableStorePrefetchAtCommit or EnableStorePrefetchSPB)
  */
class StorePipe(id: Int)(implicit p: Parameters) extends DCacheModule{
  val io = IO(new DCacheBundle {
    // incoming requests
    val lsu = Flipped(new DCacheStoreIO)

/** S0:
  *   send tag and meta read req
  */
  val s0_valid = io.lsu.req.valid
  val s0_req = io.lsu.req.bits
  val s0_fire = io.lsu.req.fire

  io.meta_read.valid        := s0_valid
  io.meta_read.bits.idx     := get_idx(io.lsu.req.bits.vaddr)
  io.meta_read.bits.way_en  := ~0.U(nWays.W)

  io.tag_read.valid         := s0_valid
  io.tag_read.bits.idx      := get_idx(io.lsu.req.bits.vaddr)
  io.tag_read.bits.way_en   := ~0.U(nWays.W)

  io.lsu.req.ready := io.meta_read.ready && io.tag_read.ready

/** S1:
  * get tag and meta read resp
  * judge hit or miss
  */
  def wayMap[T <: Data](f: Int => T) = VecInit((0 until nWays).map(f))

  val s1_valid = RegNext(s0_fire)
  val s1_req = RegEnable(s0_req, s0_fire)

  val s1_meta_resp = io.meta_resp
  val s1_tag_resp  = io.tag_resp.map(tag => tag(tagBits - 1, 0))

  val s1_paddr = io.lsu.s1_paddr
~~~

**代码依据：xiangshan/cache/dcache/DCacheWrapper.scala:1540-1605**

~~~scala

  //----------------------------------------
  // mainPipe
  // when a req enters main pipe, if it is set-conflict with replace pipe or refill pipe,
  // block the req in main pipe
  probeQueue.io.pipe_req <> mainPipe.io.probe_req
  io.lsu.store.req <> mainPipe.io.store_req

  io.lsu.store.replay_resp.valid := RegNext(mainPipe.io.store_replay_resp.valid)
  io.lsu.store.replay_resp.bits := RegEnable(mainPipe.io.store_replay_resp.bits, mainPipe.io.store_replay_resp.valid)
  io.lsu.store.main_pipe_hit_resp := mainPipe.io.store_hit_resp

  mainPipe.io.atomic_req <> io.lsu.atomics.req
~~~

`StorePipe` 的代码注释和 S0/S1 实现直接显示它读取 tag/meta 并判断 hit/miss，属于 Store 地址侧的非阻塞流水线。`io.lsu.store.req <> mainPipe.io.store_req` 才是 SBuffer 的 line Store 请求接入 MainPipe 的位置；MainPipe 的 hit/replay 响应反向给 SBuffer。DCache 的 MissQueue 用 TileLink A/E 下发 miss/refill 协议，故 Store data 也可能在 MainPipe/MissQueue 中等待而非停在 StoreUnit。

### 14.10.6 DCache：内部不是一个单一 FSM

**代码依据：xiangshan/cache/dcache/DCacheWrapper.scala:819-1605**

~~~scala
class DCacheToLsuIO(implicit p: Parameters) extends DCacheBundle {
  val load  = Vec(LoadPipelineWidth, Flipped(new DCacheLoadIO)) // for speculative load
  val sta   = Vec(StorePipelineWidth, Flipped(new DCacheStoreIO)) // for non-blocking store
  //val lsq = ValidIO(new Refill)  // refill to load queue, wake up load misses
  val tl_d_channel = Output(new DcacheToLduForwardIO)
  val store = new DCacheToSbufferIO // for sbuffer
  val atomics  = Flipped(new AtomicWordIO)  // atomics reqs
  val release = ValidIO(new Release) // cacheline release hint for ld-ld violation check
  val forward_D = Output(Vec(LoadPipelineWidth, new DcacheToLduForwardIO))
  val forward_mshr = Vec(LoadPipelineWidth, new LduToMissqueueForwardIO)
}

class DCacheIO(implicit p: Parameters) extends DCacheBundle {
  val hartId = Input(UInt(hartIdLen.W))
  val l2_pf_store_only = Input(Bool())
  val lsu = new DCacheToLsuIO
  val error = ValidIO(new L1CacheErrorInfo)
  val mshrFull = Output(Bool())
  val memSetPattenDetected = Output(Bool())
  val lqEmpty = Input(Bool())
  val pf_ctrl = Output(new PrefetchControlBundle)
  val force_write = Input(Bool())
  val sms_agt_evict_req = DecoupledIO(new AGTEvictReq)
  val debugTopDown = new DCacheTopDownIO
  val debugRolling = Flipped(new RobDebugRollingIO)
  val l2_hint = Input(Valid(new L2ToL1Hint()))
  val cmoOpReq = Flipped(DecoupledIO(new CMOReq))
  val cmoOpResp = DecoupledIO(new CMOResp)
  val l1Miss = Output(Bool())
  val wfi = Flipped(new WfiReqBundle)
}

  //----------------------------------------
  // core data structures
  val bankedDataArray = if(dwpuParam.enWPU) Module(new SramedDataArray) else Module(new BankedDataArray)
  val metaArray = Module(new L1CohMetaArray(readPorts = LoadPipelineWidth + 1, writePorts = 1))
  val errorArray = Module(new L1ErrorMetaArray(readPorts = LoadPipelineWidth + 1, writePorts = 1, enableBypass = true))
  val prefetchArray = Module(new L1PrefetchSourceArray(readPorts = PrefetchArrayReadPort, writePorts = 1 + LoadPipelineWidth)) // prefetch flag array
  val accessArray = Module(new L1FlagMetaArray(readPorts = AccessArrayReadPort, writePorts = LoadPipelineWidth + 1))
  val tagArray = Module(new DuplicatedTagArray(readPorts = TagReadPort))
  val prefetcherMonitor = Module(new PrefetcherMonitor)
  val fdpMonitor =  Module(new FDPrefetcherMonitor)
  val bloomFilter =  Module(new BloomFilter(BLOOM_FILTER_ENTRY_NUM, true))
  val counterFilter = Module(new CounterFilter)
  bankedDataArray.dump()

  //----------------------------------------
  // core modules
  val ldu = Seq.tabulate(LoadPipelineWidth)({ i => Module(new LoadPipe(i))})
  val stu = Seq.tabulate(StorePipelineWidth)({ i => Module(new StorePipe(i))})
  val mainPipe     = Module(new MainPipe)
  // val refillPipe   = Module(new RefillPipe)
  val missQueue    = Module(new MissQueue(edge, MissReqPortCount))
  val probeQueue   = Module(new ProbeQueue(edge))
  val wb           = Module(new WritebackQueue(edge))

  missQueue.io.lqEmpty := io.lqEmpty
  missQueue.io.hartId := io.hartId
  missQueue.io.l2_pf_store_only := RegNext(io.l2_pf_store_only, false.B)
  missQueue.io.debugTopDown <> io.debugTopDown
  missQueue.io.l2_hint <> RegNext(io.l2_hint)
  missQueue.io.mainpipe_info := mainPipe.io.mainpipe_info
  missQueue.io.occupy_set.zip(ldu.map(_.io.occupy_set)).foreach { case (l, r) => l <> r }
  missQueue.io.occupy_fail.zip(ldu.map(_.io.occupy_fail)).foreach { case (l, r) => l <> r }
  mainPipe.io.refill_info := missQueue.io.refill_info
  mainPipe.io.replace <> missQueue.io.replace

  // load pipe
  // the s1 kill signal
  // only lsu uses this, replay never kills
  for (w <- 0 until LoadPipelineWidth) {
    ldu(w).io.lsu <> io.lsu.load(w)

    // TODO:when have load128Req
    ldu(w).io.load128Req := io.lsu.load(w).is128Req

    // replay and nack not needed anymore
    // TODO: remove replay and nack
    ldu(w).io.nack := false.B

    ldu(w).io.disable_ld_fast_wakeup :=
      bankedDataArray.io.disable_ld_fast_wakeup(w) // load pipe fast wake up should be disabled when bank conflict
  }

  // forward missqueue
  (0 until LoadPipelineWidth).map(i => io.lsu.forward_mshr(i).connect(missQueue.io.forward(i)))
~~~

DCache 的输入/输出由 `DCacheIO.lsu` 划分：LoadUnit 使用 `load(i)`，StoreUnit 使用 `sta(i)`，SBuffer 使用 `store`，AtomicsUnit 使用 `atomics`；DCache 还向 LoadUnit 输出 `forward_D` 和 `forward_mshr`，向 LSQ 输出 `release`。它的状态不是一个顶层 `Enum`：数据、tag、meta、error 与预取信息在独立 array 中；LoadPipe、StorePipe、MainPipe、MissQueue、ProbeQueue 与 WritebackQueue 分别保存自己的流水线或 entry 状态。

其中 `LoadPipe` 执行投机 Load 的 cache 数组访问，`StorePipe` 做 Store 地址相关的访问/预取，`MainPipe` 接收 SBuffer line write 和原子请求，`MissQueue` 管理 miss 与 TileLink 请求，`ProbeQueue` 接收 B 通道 Probe，`WritebackQueue` 发送 C 通道 Release。`missQueue.io.forward(i)` 被显式接到 `io.lsu.forward_mshr(i)`，所以尚未写回数组但已到达 MissQueue 的数据可以参与 LoadUnit 的提前转发。

**代码依据：xiangshan/XSTile.scala:63-68, 94-99**

~~~scala
  // =========== Components' Connection ============
  // L1 to l1_xbar
  coreParams.dcacheParametersOpt.map { _ =>
    l2top.inner.misc_l2_pmu := l2top.inner.l1d_logger := memBlock.dcache_port :=
      memBlock.l1d_to_l2_buffer.node := memBlock.dcache.clientNode
  }

  // mmio
  l2top.inner.i_mmio_port := l2top.inner.i_mmio_buffer.node := memBlock.frontendBridge.instr_uncache_node
  if (soc.EnableICacheCtrl) {
    memBlock.frontendBridge.icachectrl_node := l2top.inner.icachectrl_port_opt.get
  }
  l2top.inner.d_mmio_port := memBlock.uncache_port
~~~

这说明真实 DCache clientNode 经 MemBlock 的 L1D-to-L2 buffer 接向 L2 侧，而数据 MMIO 端口接的是 `uncache_port`。因此普通 cacheable Load/Store miss 与 MMIO/NC 请求在模块边界后走的是不同互连端口。

### 14.10.7 Uncache：LQ/SQ 共享的非缓存请求通路

`LsqWrapper` 首先在 LoadQueue 与 StoreQueue 的 `UncacheWordIO` 间选择请求；随后 MemBlock 用另一层 `uncacheState` 把该请求经 `AddPipelineReg` 送入 Uncache；最后 Uncache 自己维护多 entry 的请求、合并、TileLink 和返回状态。不要把这三层状态机误当成同一个状态寄存器。

**代码依据：xiangshan/mem/lsqueue/LSQWrapper.scala:265-321**

~~~scala
  // naive uncache arbiter
  val s_idle :: s_load :: s_store :: Nil = Enum(3)
  val pendingstate = RegInit(s_idle)
  val selectLq = (loadQueue.io.uncache.req.valid && !storeQueue.io.uncache.req.valid) || (
    loadQueue.io.uncache.req.valid && storeQueue.io.uncache.req.valid &&
    loadQueue.io.uncache.req.bits.robIdx < storeQueue.io.uncache.req.bits.robIdx
  )

  switch(pendingstate){
    is(s_idle){
      when(io.uncache.req.fire){
        pendingstate :=
          Mux(io.uncacheOutstanding && io.uncache.req.bits.nc, s_idle,
          Mux(selectLq, s_load,
          s_store))
      }
    }
    is(s_load){
      when(io.uncache.resp.fire){
        pendingstate := s_idle
      }
    }
    is(s_store){
      when(io.uncache.resp.fire){
        pendingstate := s_idle
      }
    }
  }

  when(pendingstate === s_idle){
    when(selectLq){
      io.uncache.req <> loadQueue.io.uncache.req
    }.otherwise{
      io.uncache.req <> storeQueue.io.uncache.req
    }
  }.otherwise{
    io.uncache.req.valid := false.B
    io.uncache.req.bits := DontCare
  }
  when (io.uncache.resp.bits.is2lq) {
    io.uncache.resp <> loadQueue.io.uncache.resp
  } .otherwise {
    io.uncache.resp <> storeQueue.io.uncache.resp
  }
  when(io.uncache.idResp.bits.is2lq) {
    loadQueue.io.uncache.idResp <> io.uncache.idResp
  }.otherwise {
    storeQueue.io.uncache.idResp <> io.uncache.idResp
  }
~~~

**代码依据：xiangshan/mem/MemBlock.scala:1446-1514**

~~~scala

  val s_idle :: s_scalar_uncache :: s_vector_uncache :: Nil = Enum(3)
  val uncacheState = RegInit(s_idle)
  val uncacheReq = Wire(Decoupled(new UncacheWordReq))
  val uncacheIdResp = uncache.io.lsq.idResp
  val uncacheResp = Wire(Decoupled(new UncacheWordResp))

  switch (uncacheState) {
    is (s_idle) {
      when (uncacheReq.fire) {
        when (lsq.io.uncache.req.valid) {
          when (!lsq.io.uncache.req.bits.nc || !io.ooo_to_mem.csrCtrl.uncache_write_outstanding_enable) {
            uncacheState := s_scalar_uncache
          }
        }.otherwise {
          // val isStore = vsFlowQueue.io.uncache.req.bits.cmd === MemoryOpConstants.M_XWR
          when (!io.ooo_to_mem.csrCtrl.uncache_write_outstanding_enable) {
            uncacheState := s_vector_uncache
          }
        }
      }
    }

    is (s_scalar_uncache) {
      when (uncacheResp.fire) {
        uncacheState := s_idle
      }
    }

    is (s_vector_uncache) {
      when (uncacheResp.fire) {
        uncacheState := s_idle
      }
    }
  }

  when (lsq.io.uncache.req.valid) {
    uncacheReq <> lsq.io.uncache.req
  }
  when (io.ooo_to_mem.csrCtrl.uncache_write_outstanding_enable) {
    lsq.io.uncache.resp <> uncacheResp
    lsq.io.uncache.idResp <> uncacheIdResp
  }.otherwise {
    when (uncacheState === s_scalar_uncache) {
      lsq.io.uncache.resp <> uncacheResp
      lsq.io.uncache.idResp <> uncacheIdResp
    }
  }
  // delay dcache refill for 1 cycle for better timing
  AddPipelineReg(uncacheReq, uncache.io.lsq.req, false.B)
  AddPipelineReg(uncache.io.lsq.resp, uncacheResp, false.B)
~~~

Wrapper 的 `pendingstate` 在 LQ 和 SQ 都请求时按 `robIdx` 选择更老请求，并用 `is2lq` 将响应和 `idResp` 分流回原队列。MemBlock 的 `uncacheState` 则保护非 outstanding 的标量/向量事务；打开 `uncache_write_outstanding_enable` 时，响应和 `idResp` 可以直接连接而不等待这个状态机占有通道。两个 `AddPipelineReg` 明确在 LSQ--Uncache 双方向各插入一级握手流水。

**代码依据：xiangshan/cache/dcache/Uncache.scala:136-324**

~~~scala
class UncacheEntryState(implicit p: Parameters) extends DCacheBundle {
  // valid (-> waitSame) -> inflight -> waitReturn
  val valid = Bool()
  val inflight = Bool() // uncache -> L2
  val waitSame = Bool()
  val waitReturn = Bool() // uncache -> LSQ

  def isValid(): Bool = valid
  def isInflight(): Bool = valid && inflight
  def isWaitReturn(): Bool = valid && waitReturn
  def isWaitSame(): Bool = valid && waitSame
  def can2Bus(): Bool = valid && !inflight && !waitSame && !waitReturn
  def can2Lsq(): Bool = valid && waitReturn
  def canMerge(): Bool = valid && !inflight
  def isFwdOld(): Bool = valid && (inflight || waitReturn)
  def isFwdNew(): Bool = valid && !inflight && !waitReturn && waitSame

  def updateUncacheResp(): Unit = {
    assert(inflight, "The request was not sent and a response was received")
    inflight := false.B
    waitReturn := true.B
  }
  def updateReturn(): Unit = {
    valid := false.B
    inflight := false.B
    waitSame := false.B
    waitReturn := false.B
  }
}

class UncacheIO(implicit p: Parameters) extends DCacheBundle {
  val hartId = Input(UInt())
  val enableOutstanding = Input(Bool())
  val flush = Flipped(new UncacheFlushBundle)
  val lsq = Flipped(new UncacheWordIO)
  val forward = Vec(LoadPipelineWidth, Flipped(new LoadForwardQueryIO))
  val wfi = Flipped(new WfiReqBundle)
  val busError = Output(new L1BusErrorUnitInfo())
}

  val entries = Reg(Vec(UncacheBufferSize, new UncacheEntry))
  val states = RegInit(VecInit(Seq.fill(UncacheBufferSize)(0.U.asTypeOf(new UncacheEntryState))))
  val s_idle :: s_inflight :: s_wait_return :: Nil = Enum(3)
  val uState = RegInit(s_idle)
  val noPending = RegInit(VecInit(Seq.fill(UncacheBufferSize)(true.B)))

  switch(uState){
    is(s_idle){
      when(mem_acquire.fire){
        uState := s_inflight
      }
    }
    is(s_inflight){
      when(mem_grant.fire){
        uState := s_wait_return
      }
    }
    is(s_wait_return){
      when(resp.fire){
        uState := s_idle
      }
    }
  }
~~~

Uncache 的外部输入是 `io.lsq` 的 `UncacheWordIO`，外部输出是同一协议的 `resp/idResp`、Load 前递端口、flush/WFI 状态和总线错误；内部使用 `entries` 保存命令、地址、数据和 mask，使用每 entry 的 `valid/inflight/waitSame/waitReturn` 管理可合并、可发总线、可回 LSQ 的资格。`uState` 只在非 outstanding 模式下串行约束 `mem_acquire -> mem_grant -> resp`，而不是替代各 entry 的状态。

### 14.10.8 特殊控制器：原子、非对齐、向量和 Hybrid

这些模块不是普通 Load/Store 主路径的旁注，它们通过复用同一 DTLB、PMP、LoadUnit、StoreUnit、SBuffer 和 DCache 接口进入系统；差异在于它们在进入或离开普通流水线时额外保存了事务状态。

**代码依据：xiangshan/mem/pipeline/AtomicsUnit.scala:37-325**

~~~scala
class AtomicsUnit(implicit p: Parameters) extends XSModule
  with MemoryOpConstants
  with HasDCacheParameters
  with SdtrigExt{

  val StdCnt  = backendParams.StdCnt

  val io = IO(new Bundle() {
    val hartId        = Input(UInt(hartIdLen.W))
    val in            = Flipped(Decoupled(new MemExuInput))
    val storeDataIn   = Flipped(Vec(StdCnt, Valid(new MemExuOutput)))
    val out           = Decoupled(new MemExuOutput)
    val dcache        = new AtomicWordIO
    val dtlb          = new TlbRequestIO(2)
    val pmpResp       = Flipped(new PMPRespBundle())
    val flush_sbuffer = new SbufferFlushBundle
    val feedbackSlow  = ValidIO(new RSFeedback)
    val redirect      = Flipped(ValidIO(new Redirect))
    val exceptionInfo = ValidIO(new Bundle {
      val vaddr = UInt(XLEN.W)
      val gpaddr = UInt(XLEN.W)
      val isForVSnonLeafPTE = Bool()
    })
    val csrCtrl       = Flipped(new CustomCSRCtrlIO)
  })

  // Atomics Memory Accsess FSM
  //-------------------------------------------------------
  val s_invalid :: s_tlb_and_flush_sbuffer_req :: s_pm :: s_wait_flush_sbuffer_resp :: s_cache_req :: s_cache_resp :: s_cache_resp_latch :: s_finish :: s_finish2 :: Nil = Enum(9)
  val state = RegInit(s_invalid)
  val out_valid = RegInit(false.B)
  val data_valid = RegInit(false.B)

  val uop = Reg(io.in.bits.uop.cloneType)
  val isLr = LSUOpType.isLr(uop.fuOpType)
  val isSc = LSUOpType.isSc(uop.fuOpType)
  val isAMOCAS = LSUOpType.isAMOCAS(uop.fuOpType)

  // sbuffer is empty or not
  val sbuffer_empty = io.flush_sbuffer.empty

  when (state === s_tlb_and_flush_sbuffer_req) {
    // do not accept tlb resp in the first cycle
    // this limition is for hw prefetcher
    // when !have_sent_first_tlb_req, tlb resp may come from hw prefetch
    have_sent_first_tlb_req := true.B

    when (io.dtlb.resp.fire && have_sent_first_tlb_req) {
      paddr   := io.dtlb.resp.bits.paddr(0)
      gpaddr  := io.dtlb.resp.bits.gpaddr(0)
      vaddr   := io.dtlb.resp.bits.fullva
      isForVSnonLeafPTE := io.dtlb.resp.bits.isForVSnonLeafPTE

  when (state === s_pm) {
    val pmp = WireInit(io.pmpResp)
    is_mmio := Pbmt.isIO(pbmtReg) || (Pbmt.isPMA(pbmtReg) && pmp.mmio)

    // NOTE: only handle load/store exception here, if other exception happens, don't send here
    val exception_va = exceptionVec(storePageFault) || exceptionVec(loadPageFault) ||
      exceptionVec(storeGuestPageFault) || exceptionVec(loadGuestPageFault) ||
      exceptionVec(storeAccessFault) || exceptionVec(loadAccessFault)
    val exception_pa_mmio_nc = pmp.mmio || Pbmt.isIO(pbmtReg) || Pbmt.isNC(pbmtReg)
    val exception_pa = pmp.st || pmp.ld || exception_pa_mmio_nc
    when (exception_va || exception_pa) {
      state := s_finish
      out_valid := true.B
      atom_override_xtval := true.B
    }.otherwise {
      // if sbuffer has been flushed, go to query dcache, otherwise wait for sbuffer.
      state := Mux(sbuffer_empty, s_cache_req, s_wait_flush_sbuffer_resp);
    }
~~~

`AtomicsUnit` 是显式九态 FSM。输入 `in` 是被 MemBlock 从 Sta/Hybrid 发射路径改道的原子地址 uop，`storeDataIn` 收集对应 Std 数据；输出 `out` 经 Load writeback 端口回后端。它先请求 DTLB 并请求清空 SBuffer，然后检查 PMP/PBMT/异常，只有在 SBuffer 为空时才经 `AtomicWordIO` 请求 DCache，之后等待响应并写回。这正是原子请求不与普通 SBuffer line write 随意交叉的实现边界。

**代码依据：xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:117-290**

~~~scala
  val io = IO(new Bundle() {
    val redirect        = Flipped(Valid(new Redirect))
    val enq             = Vec(enqPortNum, Flipped(new MisalignBufferEnqIO))
    val rob             = Flipped(new RobLsqIO)
    val splitLoadReq    = Decoupled(new LsPipelineBundle)
    val splitLoadResp   = Flipped(Valid(new LqWriteBundle))
    val writeBack       = Decoupled(new MemExuOutput)
    val vecWriteBack    = Decoupled(new VecPipelineFeedbackIO(isVStore = false))
    val loadOutValid    = Input(Bool())
    val loadVecOutValid = Input(Bool())
    val overwriteExpBuf = Output(new XSBundle {
      val valid  = Bool()
      val vaddr  = UInt(XLEN.W)
      val isHyper = Bool()
      val gpaddr = UInt(XLEN.W)
      val isForVSnonLeafPTE = Bool()
    })
    val flushLdExpBuff  = Output(Bool())
    val loadMisalignFull = Output(Bool())
  })

  val req_valid = RegInit(false.B)
  val req = Reg(new LqWriteBundle)

  io.loadMisalignFull := req_valid

  // buffer control:
  //  - s_idle:   idle
  //  - s_split:  split misalign laod
  //  - s_req:    issue a split memory access request
  //  - s_resp:   Responds to a split load access request
  //  - s_comb_wakeup_rep: Merge the data and issue a wakeup load
  //  - s_wb: writeback yo rob/vecMergeBuffer
  val s_idle :: s_split :: s_req :: s_resp :: s_comb_wakeup_rep :: s_wb :: Nil = Enum(6)
  val bufferState = RegInit(s_idle)
  val splitLoadReqs = RegInit(VecInit(List.fill(maxSplitNum)(0.U.asTypeOf(new LsPipelineBundle))))
  val splitLoadResp = RegInit(VecInit(List.fill(maxSplitNum)(0.U.asTypeOf(new LqWriteBundle))))
  val exceptionVec = RegInit(0.U.asTypeOf(ExceptionVec()))
  val unSentLoads = RegInit(0.U(maxSplitNum.W))
  val curPtr = RegInit(0.U(log2Ceil(maxSplitNum).W))

  switch(bufferState) {
    is (s_idle) {
      when (req_valid) {
        bufferState := s_split
      }
    }

    is (s_split) {
      bufferState := s_req
    }

    is (s_req) {
      when (io.splitLoadReq.fire) {
        bufferState := s_resp
      }
    }

    is (s_resp) {
      when (io.splitLoadResp.valid) {
        val clearOh = UIntToOH(curPtr)
~~~

**代码依据：xiangshan/mem/vector/VSplit.scala:590-641**

~~~scala

class VLSplitImp(implicit p: Parameters) extends VLSUModule{
  val io = IO(new VSplitIO(isVStore=false))
  val splitPipeline = Module(new VLSplitPipelineImp())
  val splitBuffer = Module(new VLSplitBufferImp())
  // Split Pipeline
  splitPipeline.io.in <> io.in
  splitPipeline.io.redirect <> io.redirect
  splitPipeline.io.threshold <> io.threshold
  io.toMergeBuffer <> splitPipeline.io.toMergeBuffer

  // skid buffer
  skidBuffer(splitPipeline.io.out, splitBuffer.io.in,
    Mux(splitPipeline.io.out.fire,
      splitPipeline.io.out.bits.uop.robIdx.needFlush(io.redirect),
      splitBuffer.io.in.bits.uop.robIdx.needFlush(io.redirect)),
    "VSSplitSkidBuffer")

  // Split Buffer
  splitBuffer.io.redirect <> io.redirect
  splitBuffer.io.fromPipeline.get := io.fromPipeline.get
  io.out <> splitBuffer.io.out
}
~~~

**代码依据：xiangshan/mem/vector/VMergeBuffer.scala:422-440**

~~~scala

class VLMergeBufferImp(implicit p: Parameters) extends BaseVMergeBuffer(isVStore=false){
  override lazy val uopSize = VlMergeBufferSize
  println(s"VLMergeBuffer Size: ${VlMergeBufferSize}")
  override lazy val freeList = Module(new FreeList(
    size = uopSize,
    allocWidth = VecLoadPipelineWidth,
    freeWidth = deqWidth,
    enablePreAlloc = false,
    moduleName = "VLoad MergeBuffer freelist"
  ))
  io.toSplit.threshold := freeCount <= 6.U
~~~

`LoadMisalignBuffer` 只有一个 `req` 槽位（`req_valid`）并有六态 FSM：收请求、拆分、发第一/后续子请求、收响应、合并/唤醒、写回。`splitLoadReq` 回接指定的 LoadUnit，`splitLoadResp` 从该 LoadUnit 返回；如果任一拆分子请求进入 Uncache 或有异常，状态机保存异常/属性并直接走写回，而不是把两半数据当作可合并的正常 Load。Store 非对齐缓冲器在 MemBlock 上采用同样的“StoreUnit enq -> splitStoreReq -> StoreUnit resp -> writeBack”模式，详细状态转换见 14.8.3。

向量路径由 `VLSplitImp`/`VSSplitImp` 的 split pipeline 和 split buffer 将一个向量 uop 变成可由标量 LoadUnit/StoreUnit 执行的 flow；`VLMergeBufferImp`/`VSMergeBufferImp` 用 FreeList 和 entry 数据把 flow 结果合并后写回或给队列反馈。它们没有由本片段显示的单一统一 Enum；可确认的状态包括 split buffer 的 skid/flush 行为，以及 merge buffer 的 FreeList、`freeCount` 和 `entries`。`VSegmentUnit` 与 AtomicsUnit 一样需要独占部分共享端口，MemBlock 已在 14.8.5 的连接处显式处理其 DCache/DTLB/SBuffer 复用。

`HybridUnit` 是 MemBlock 单独例化的一类执行入口。它从 `lsin` 接收混合访存 uop，分别由 `ldout` 和 `stout` 输出 Load/Store 结果；其 IO 明确拆成 `ldu_io` 与 `stu_io` 两个侧面。下面的类型定义是判断接口方向的依据，而不是依据模块名称猜测功能。

**代码依据：xiangshan/mem/pipeline/HybridUnit.scala:45-165**

~~~scala
    // flow in
    val lsin          = Flipped(Decoupled(new MemExuInput))

    // flow out
    val ldout = DecoupledIO(new MemExuOutput)
    val stout = DecoupledIO(new MemExuOutput)

    val ldu_io = new Bundle() {
      // dcache
      val dcache        = new DCacheLoadIO

      // data path
      val sbuffer       = new LoadForwardQueryIO
      val ubuffer       = new LoadForwardQueryIO
      val vec_forward   = new LoadForwardQueryIO
      val lsq           = new LoadToLsqIO
      val tl_d_channel  = Input(new DcacheToLduForwardIO)
      val forward_mshr  = Flipped(new LduToMissqueueForwardIO)
      val tlb_hint      = Flipped(new TlbHintReq)
      val l2_hint       = Input(Valid(new L2ToL1Hint))

      // queue-based replay
      val replay       = Flipped(Decoupled(new LsPipelineBundle))
      val lq_rep_full  = Input(Bool())

      // Load fast replay path
      val fast_rep_in  = Flipped(Decoupled(new LqWriteBundle))
      val fast_rep_out = Decoupled(new LqWriteBundle)

      // Load RAR rollback
      val rollback = Valid(new Redirect)
    }

    val stu_io = new Bundle() {
      val dcache          = new DCacheStoreIO
      val prefetch_req    = Flipped(DecoupledIO(new StorePrefetchReq))
      val issue           = Valid(new MemExuInput)
      val lsq             = ValidIO(new LsPipelineBundle)
      val lsq_replenish   = Output(new LsPipelineBundle())
      val stld_nuke_query = Valid(new StoreNukeQueryBundle)
      val st_mask_out     = Valid(new StoreMaskBundle)
      val debug_ls        = Output(new DebugLsInfoBundle)
    }

    // data path
    val tlb           = new TlbRequestIO(2)
    val pmp           = Flipped(new PMPRespBundle()) // arrive same to tlb now
~~~

`Flipped(Decoupled(...))` 表示 HybridUnit 是 `lsin` 的接收者：后端发送 `valid/bits`，HybridUnit 用 `ready` 回压；`ldout/stout` 的 `DecoupledIO` 则由 HybridUnit 发出。Load 侧同时拥有 DCache、SBuffer、Uncache、LSQ、DCache D 通道和 MSHR 前递接口，因而可以参与与普通 LoadUnit 相同类别的数据选择与 replay；Store 侧则拥有 `DCacheStoreIO`、StoreQueue 地址/掩码和 Store 预取接口。`tlb/pmp` 在顶层 IO 之外共享，说明地址翻译和权限检查不是两条互不相干的旁路。

**代码依据：xiangshan/mem/MemBlock.scala:1046-1167**

~~~scala
  for (i <- 0 until HyuCnt) {
    hybridUnits(i).io.redirect <> redirect

    // get input from dispatch
    hybridUnits(i).io.lsin <> io.ooo_to_mem.issueHya(i)
    hybridUnits(i).io.feedback_slow <> io.mem_to_ooo.hyuIqFeedback(i).feedbackSlow
    hybridUnits(i).io.feedback_fast <> io.mem_to_ooo.hyuIqFeedback(i).feedbackFast
    hybridUnits(i).io.correctMissTrain := correctMissTrain
    io.mem_to_ooo.ldCancel.take(HyuCnt)(i) := hybridUnits(i).io.ldu_io.ldCancel
    io.mem_to_ooo.wakeup.take(HyuCnt)(i) := hybridUnits(i).io.ldu_io.wakeup

    // fast replay
    hybridUnits(i).io.ldu_io.fast_rep_in <> hybridUnits(i).io.ldu_io.fast_rep_out

    hybridUnits(i).io.ldu_io.dcache <> dcache.io.lsu.load(LduCnt + i)
    hybridUnits(i).io.stu_io.dcache <> dcache.io.lsu.sta(StaCnt + i)

    hybridUnits(i).io.ldu_io.lsq.forward <> lsq.io.forward(LduCnt + i)
    hybridUnits(i).io.ldu_io.sbuffer <> sbuffer.io.forward(LduCnt + i)
    hybridUnits(i).io.ldu_io.ubuffer <> uncache.io.forward(LduCnt + i)
    hybridUnits(i).io.ldu_io.vec_forward := DontCare
    hybridUnits(i).io.ldu_io.tl_d_channel := dcache.io.lsu.forward_D(LduCnt + i)
    hybridUnits(i).io.ldu_io.forward_mshr <> dcache.io.lsu.forward_mshr(LduCnt + i)
    hybridUnits(i).io.csrCtrl <> csrCtrl

    hybridUnits(i).io.tlb <> dtlb_ld.head.requestor(LduCnt + i)
    hybridUnits(i).io.pmp <> pmp_check.drop(LduCnt)(i).resp
    hybridUnits(i).io.ldu_io.lq_rep_full <> lsq.io.lq_rep_full

    hybridUnits(i).io.ldu_io.replay <> lsq.io.replay(LduCnt + i)

    lsq.io.ldout.drop(LduCnt)(i) <> hybridUnits(i).io.ldu_io.lsq.uncache
    lsq.io.ld_raw_data.drop(LduCnt)(i) <> hybridUnits(i).io.ldu_io.lsq.ld_raw_data
    lsq.io.ldu.ldin(LduCnt + i) <> hybridUnits(i).io.ldu_io.lsq.ldin
    lsq.io.sta.storeMaskIn(StaCnt + i) <> hybridUnits(i).io.stu_io.st_mask_out

    hybridUnits(i).io.stu_io.prefetch_req <> sbuffer.io.store_prefetch(StaCnt + i)

    hybridUnits(i).io.stu_io.lsq <> lsq.io.sta.storeAddrIn.takeRight(HyuCnt)(i)
    hybridUnits(i).io.stu_io.lsq_replenish <> lsq.io.sta.storeAddrInRe.takeRight(HyuCnt)(i)

    lsq.io.sta.storeMaskIn.takeRight(HyuCnt)(i) <> hybridUnits(i).io.stu_io.st_mask_out
    io.mem_to_ooo.stIn.takeRight(HyuCnt)(i).valid := hybridUnits(i).io.stu_io.issue.valid
    io.mem_to_ooo.stIn.takeRight(HyuCnt)(i).bits := hybridUnits(i).io.stu_io.issue.bits
~~~

这段 `for` 循环按 `HyuCnt` 展开每个实例。`issueHya(i) -> lsin` 是输入来源；`ldCancel/wakeup`、Store `issue` 和上面的 `ldout/stout` 则回到 `mem_to_ooo`。Load 侧的 `<>` 连接把 DCache、LSQ/SBuffer/Uncache 前递、MSHR forward、DTLB/PMP 和队列 replay 集合在同一实例上；`ldu_io.fast_rep_in <> fast_rep_out` 是本地 fast replay 回环。Store 侧的 DCache 端口连 `dcache.io.lsu.sta`，地址/replenish 从 LSQ 来，`st_mask_out` 写回 LSQ，预取请求接 SBuffer。由此可按连线确认：HybridUnit 的状态/流水控制在该独立模块内部，但共享的是既有访存子系统端口，而不是绕开 LSQ 或 DCache。

### 14.10.9 按请求类型回放一次数据流

下面的流程把以上接口压缩为可逐信号追踪的检查表。每条箭头都对应本节或前文已经给出的 `:=`/`<>` 连线；`fire` 才表示一个 `Decoupled` 请求真正被接收。

| 请求类型 | 输入与中间计算 | 保存状态的模块 | 正常完成去向 | 需要等待/恢复时去向 |
| --- | --- | --- | --- | --- |
| 可缓存标量 Load | `issueLda -> LoadUnit` 计算虚拟地址/mask；DTLB/PMP；SQ/SBuffer/Uncache/DCache/MissQueue 多源前递 | LoadUnit 的流水级；VLQ 生命周期；RAR/RAW/Replay entry | `ldout`、wakeup/feedback 回后端；Load S3 信息写 LQ | fast replay 自环或 `LoadQueueReplay -> replay -> LoadUnit`；违例则最老 `memoryViolation` redirect。 |
| 可缓存标量 Store | `issueSta` 计算地址/掩码，`issueStd` 提供数据；DTLB/PMP 和 StorePipe 做地址侧检查 | StoreQueue entry 的地址/数据/提交位；SBuffer line entry | 已提交后 `StoreQueue -> SBuffer -> DCache.MainPipe` | SBuffer replay/等待 miss；RAW 检查可能对更年轻 Load 发 redirect。 |
| MMIO/NC Load | LoadUnit 分类后把 S3 包交 LoadQueueUncache | LQ Uncache entry、LsqWrapper/MemBlock 仲裁状态、Uncache entry | `mmioOut` 或 `ncOut` 回 LoadUnit/后端 | Uncache 事务或 entry 无法接收时由 LQ 产生 rollback/replay。 |
| MMIO/NC Store | StoreQueue 只在符合 ROB pending/commit 条件时发 Uncache 请求 | StoreQueue MMIO/NC FSM、LsqWrapper/Uncache 状态 | MMIO Store writeback 或 NC 完成/确认 | 等 `idResp` 或最终响应，取决于 outstanding 配置。 |
| LR/SC/AMO | MemBlock 从 Sta/Hybrid 改道至 AtomicsUnit，收集 STD 数据 | AtomicsUnit 九态 FSM 和 SBuffer flush 条件 | `AtomicsUnit.out` 复用 Load writeback 端口 | TLB/PMP/异常、SBuffer 未空、DCache miss/replay 都由原子 FSM 保持。 |
| 非对齐或向量 flow | Load/StoreUnit 将特殊请求送 MisalignBuffer 或 VSplit | 非对齐 buffer FSM；向量 split/merge buffer/FreeList | 普通 LDU/STU 结果被合并后写回 | 子请求 replay、异常、Uncache 或 redirect 由各 buffer 保存并恢复。 |

这个视角也给出阅读源码的顺序：先从 MemBlock 的一根 `<>` 找到源和汇，再到接口 Bundle 判定请求/响应方向，随后只在有 `RegInit`、`RegNext`、`RegEnable` 或 `Enum` 的模块中谈状态机和跨拍时间。这样能避免把“某个模块有一个叫 dcache 的端口”误写成“它直接完成了最终 cache 写入”。

## 14.11 小结

当前源码能直接观察到的标量主线是：

~~~text
LSQ 分配 lqIdx/sqIdx
  -> LoadUnit 或 Sta/Std 进入执行路径
  -> SQ / SBuffer / UBuffer / DCache 提供 Load 所需数据
  -> Replay 等待具体解除事件，或 RAW/RAR 提供 redirect
  -> 普通可缓存的已提交 Store 经 dataBuffer 进入 SBuffer
~~~

阅读后续 DCache、MMU 或向量模块时继续使用同一标准：模块必须由 Module 例化，接口必须由 := 或 <> 连接，跨拍状态必须由 RegInit、RegNext、RegEnable 或 when 更新。找不到这类证据，就不要把推测写成当前昆明湖的实现事实。
