# 访存依赖关系检测与 Replay 机制

## 香山昆明湖 V3 - LoadQueueRAW 模块分析

LoadQueueRAW 模块是香山昆明湖 V3 中, 负责实现内存依赖关系检测的模块. 在访存子系统的 LoadQueue 中被实例化 (LoadQueue 又在 LSQWrapper 中被实例化, LSQWrapper 则在 MemBlock 中被实例化). 该模块接受来自 Load pipeline 的 rawNukeQuery, 来自 Store pipeline 的 storeAddrIn, 来自 Store Queue 的 stAddrReadySqPtr, 并输出 nuke\_rollback 和 mdpTrain. 这个模块负责解决 Load 指令乱序提前执行时, 如果前面还有更老的 Store 地址未知, Load 可能会绕过一个真正有地址依赖的 Store 的情况, 等那条更老的 Store 指令地址计算出来后, 发现和年轻 Load 指令地址存在重叠, 就必须回滚到该 Load 指令处, 重新执行这条 Load 以及后续的指令. 可以在 LoadQueue 的实现中看见该模块的实例化和与其父模块的信号交互:

```scala
  val loadQueueRAR = Module(new LoadQueueRAR)  //  read-after-read violation
  val loadQueueRAW = Module(new LoadQueueRAW)  //  read-after-write violation
  val loadQueueReplay = Module(new LoadQueueReplay)  //  enqueue if need replay
  val virtualLoadQueue = Module(new VirtualLoadQueue)  //  control state
  val uncacheBuffer = Module(new LoadQueueUncache) // uncache
  
  // ...

  /**
   * LoadQueueRAW
   */
  loadQueueRAW.io.redirect         <> io.redirect
  loadQueueRAW.io.storeIn          <> io.sta.storeAddrIn
  loadQueueRAW.io.stAddrReadySqPtr <> io.sq.stAddrReadySqPtr
  loadQueueRAW.io.query            <> io.ldu.rawNukeQuery
  io.mdpTrain                      := loadQueueRAW.io.mdpTrain

  // ...

  io.nuke_rollback := loadQueueRAW.io.rollback
  io.nack_rollback(0) := uncacheBuffer.io.rollback
```

### LoadQueueRAW 的输入输出信号

分析 LoadQueueRAW 模块的输入输出, 并研究其作用:

```scala
class LoadQueueRAW(implicit p: Parameters) extends XSModule
  with HasDCacheParameters
  with HasCircularQueuePtrHelper
  with HasLoadHelper
  with HasPerfEvents
{
  val io = IO(new Bundle() {
    // control
    val redirect = Flipped(ValidIO(new Redirect))

    // violation query
    val query = Vec(LoadPipelineWidth, Flipped(new LoadRAWNukeQuery()))

    // from store unit s1
    val storeIn = Vec(StorePipelineWidth, Flipped(Valid(new StoreAddrIO)))

    // global rollback flush
    val rollback = Vec(StorePipelineWidth,Output(Valid(new Redirect)))

    // mdp train io
    val mdpTrain        = ValidIO(new Redirect)

    // to LoadQueueReplay
    val stAddrReadySqPtr = Input(new SqPtr)
    val lqFull           = Output(Bool())
  })
```

其中 `redirect`, `query`, `storeIn`, 和`stAddrReadySqPtr`为输入类信号; `rollback`, `mdpTrain`, 和 `lqFull`为输出类信号. 输入信号 `redirect`负责接收全局的 flush 和 redirect 信号, 用来取消 RAW 队列中已经被冲刷掉的 load 指令信息 (如果某条 load 指令之前的分支预测指令预测错误, 就要冲刷掉这条 load 那么这条 load 指令是否违例就没有必要进行检查了); `query`来自 load pipeline, 每个 load pipeline 一路, 里面有关于一条 load 是否造成违例的查询信息 (在这组信号的 req 部分中), 并由该模块返回 revokeLastCycle 和 revokeLastLastCycle 来决定是否去要撤回上一个周期或者上上个周期执行的操作; `storeIn`来自 store address pipeline, 里面包括了关于本条存储指令的地址信息以及内存操作的长度信息; `stAddrReadySqPtr`提供了 Store Queue 给出的 store 地址 ready 的指针, 在这个指针值值钱的 store 地址都已经准备好了. 输出信号 `rollback`用于输出 LoadQueueRAW 计算出的是否发现出现了 RAW 冒险而需要进行 replay (重放, 也就是重定向 PC 到出现 RAW 违例的指令地址); `mdpTrain`用于告知依赖预测器出现了违例就把违例的 load-store 指令对, 用来对预测器进行训练 (对于 WaitTable, 记录那一条 load 存在危险, 对于 SSIT 则需要将这个指令对分配到一个 Store Set 中); `lqFull`用来告知外界 RAW 检测队列已满, 后续操作不能入队.

LoadQueueRAW 可以被理解成一个队列 (但是比队列又多了违例检测的功能), 接下来我们就逐一解析其队列的每个表项所携带的数据; 入队逻辑; 出队逻辑; 以及违例检测逻辑.

### LoadQueueRAW	表项分析

```scala
  //  LoadQueueRAW field
  //  +-------+--------+-------+-------+-----------+
  //  | Valid |  uop   |PAddr  | Mask  | Datavalid |
  //  +-------+--------+-------+-------+-----------+
  //
  //  Field descriptions:
  //  Allocated   : entry has been allocated already
  //  MicroOp     : inst's microOp
  //  PAddr       : physical address.
  //  Mask        : data mask
  //  Datavalid   : data valid
  //
  class UopEntry(implicit p: Parameters) extends XSBundle {
    val robIdx = new RobPtr()
    val sqIdx = new SqPtr()
    val isRVC = Bool()
    val ftqPtr = new FtqPtr()
    val ftqOffset = UInt(FetchBlockInstOffsetWidth.W)
    // only fo
    val pc = UInt(VAddrBits.W)
    val debugInfo = new PerfDebugInfo
  }
```

Valid (即注释中的 Allocated) 用来表示该表项所包含的数据是否有效. 当新的表项进入队列后, 对应表项的 allocated (代码中的实现为一个数据位宽为 LoadQueueRAWSize 的寄存器, 每一位被初始化为 0) 会被拉高. 当一个表项需要被释放时 (在代码中, 需要释放一个表项有以下几种可能: 当前表项的 load uop 中保存的 sqIdx 大于等于 LoadQueueRAW 接收到的 stAddrReadySqPtr; 当前表项之前的表项发生了 replay, 需要释放后面所有的表项), 对应的 allocated 值会被拉低.

MicroOp 的类型是 UopEntry, 通过 `Reg(Vec(LoadQueueRAWSize, new UopEntry))`进行初始化. 每个 UopEntry 保存了当前微操作对应的 ROB 表项号; 对应的 Store Queue 表项号 (Load 指令并不会分配 Store Queue 表项号, 这里传输进来 Store Queue 表项号是这条 Load 之前最年轻的一个 Store 类型指令的 Store Queue 表项号, 用来降低违例检查的开销. 即, 对于一条 Load 微操作, 我们不需要检查比这条 Load 为操作记录的 sqIdx 更老的 Store 是否违例); 是否是压缩指令; 其 FTQ (Fetch Target Queue) 指针和偏移量; 以及这条微操作对应的 PC; 和一些调试用的 debugging 信息.

PAddr 负责保存对应的 Load 微操作的部分物理地址 (不是完整的物理地址, 在代码中可以看到其位宽是 `UInt(PartialPAddrWidth.W)`, 其位宽长度是 24). Mask 负责保存所加载的内存字节掩码 (可以减少一些「假违例」情况, 比如说一对 Load-Store 的地址一样, 但是字节掩码不一样, 那么他们其实不存在冲突, 不需要进行 replay). Datavalid 表示 Load 指令是否已经拿到了数据 (代码中的实现为一个数据位宽为 LoadQueueRAWSize 的寄存器, 每一位被初始化为 0).

### LoadQueueRAW 入队逻辑

```scala
  //  LoadQueueRAW enqueue
  val canEnqueue = io.query.map(_.req.valid)
  val cancelEnqueue = io.query.map(_.req.bits.robIdx.needFlush(io.redirect))
  val hasAddrInvalidStore = io.query.map(_.req.bits.sqIdx).map(sqIdx => {
    io.stAddrReadySqPtr.isBefore(sqIdx)
  })
  val needEnqueue = canEnqueue.zip(hasAddrInvalidStore).zip(cancelEnqueue).map { case ((v, r), c) => v && r && !c }

  // Allocate logic
  val acceptedVec = Wire(Vec(LoadPipelineWidth, Bool()))
  val enqIndexVec = Wire(Vec(LoadPipelineWidth, UInt(log2Up(LoadQueueRAWSize).W)))
```

以上是 LoadQueueRAW 的入队逻辑, `canEnqueue`表示 load 流水线发来的 query 请求是有效的; `cancelEnqueue`表示该 Load 请求已经因为发生重定向而被冲刷掉, 所以请求已经没有意义了; `hasAddrInvalidStore`的计算算法为 `stAddrReadySqPtr.isBefore(load.sqIdx)`表示该 Load 微操作所携带的 sqIdx 比地址已经准备好的最年轻的 Store 对应的 sqIdx 还要年轻, 存在发生违例的可能性; `needEnqueue`等价于 `valid && hasAddrInvalidStore && !flush`, 也就是说, RAW Queue 只跟踪一种 Load: 已经执行过, 但它前面仍有 Store 地址未知. 如果 Load 执行时, 所有老 Store 地址都已经准备好了, 就不需要进这个队列.

如果一条 Load 微操作被判定为需要入队 LoadQueueRAW 队列, 则在 FreeList 中查找 enqIndex, 根据 enqIndex 将 allocated 对应的寄存器位拉高电平; 向 paddrModule, maskModule, 和 uopModule 中写入该微操作的信息, 供后续违例检查时使用.

### LoadQueueRAW 出队逻辑

```scala
  //  LoadQueueRAW deallocate
  val freeMaskVec = Wire(Vec(LoadQueueRAWSize, Bool()))

  // init
  freeMaskVec.map(e => e := false.B)

  // when the stores that "older than" current load address were ready.
  // current load will be released.
  for (i <- 0 until LoadQueueRAWSize) {
    val deqNotBlock = io.stAddrReadySqPtr.isNotBefore(uop(i).sqIdx)
    val needCancel = uop(i).robIdx.needFlush(io.redirect)

    when (allocated(i) && (deqNotBlock || needCancel)) {
      allocated(i) := false.B
      freeMaskVec(i) := true.B
    }
  }

  // ...

  for ((query, w) <- io.query.zipWithIndex) {
    val revokeLastCycle = query.revokeLastCycle && lastCanAccept(w)
    val revokeLastLastCycle = query.revokeLastLastCycle && lastLastCanAccept(w)
    val revokeLastIndex = lastAllocIndex(w)
    val revokeLastLastIndex = lastLastAllocIndex(w)

    when (allocated(revokeLastIndex) && revokeLastCycle) {
      allocated(revokeLastIndex) := false.B
      freeMaskVec(revokeLastIndex) := true.B
      willRevoke(revokeLastIndex) := true.B
    }
    when (allocated(revokeLastLastIndex) && revokeLastLastCycle) {
      allocated(revokeLastLastIndex) := false.B
      freeMaskVec(revokeLastLastIndex) := true.B
      willRevoke(revokeLastLastIndex) := true.B
    }
  }
  freeList.io.free := freeMaskVec.asUInt
```

以上是 LoadQueueRAW 的出队逻辑, `freeMaskVec`告知 FreeList 那些队列的表项被释放了. `deqNotBlock`计算当前 Store Queue 最年轻的一条地址就绪的微操作对应的 Store Queue Index 是否比当前 Load 所保存的在其前面最年轻一条 Store 的 Store Queue Index更不年轻, 如果更不年轻的话, 说明 Load 前面的所有 Store 地址均已就绪, 不会再出现新的 RAW 违例了; `needCancel`计算当前的 Load 指令是否被重定向冲刷掉, 如果冲刷掉了, 那么这条指令就不会被执行了, 也就不需要再检查 RAW 违例了. 因此, 对于所有已经分配的 LoadQueueRAW 表项, 如果 `deqNotBlock`或 `needCancel`, 则释放这些表项.

除此之外, 如果 query 输入的 `revokeLastCycle`或 `revokeLastLastCycle`则表示之前从 Load 流水线发来的查询请求已经没有必要再查询了 (Load Pipe 已经得知先前入对的微操作不会出现 RAW 违例的话), 相应的 LoadQueueRAW 表项也会被释放.

### LoadQueueRAW 违例检测与重放逻辑

```scala
  def detectRollback(i: Int) = {
    paddrModule.io.violationMdata(i) := genPartialPAddr(RegEnable(storeIn(i).bits.paddr, storeIn(i).valid))
    paddrModule.io.violationCheckLine.get(i) := RegEnable(storeIn(i).bits.wlineflag, storeIn(i).valid)
    maskModule.io.violationMdata(i) := RegEnable(storeIn(i).bits.mask, storeIn(i).valid)

    val addrMaskMatch = paddrModule.io.violationMmask(i).asUInt & maskModule.io.violationMmask(i).asUInt
    val entryNeedCheck = GatedValidRegNext(VecInit((0 until LoadQueueRAWSize).map(j => {
      allocated(j) && storeIn(i).valid && isAfter(uop(j).robIdx, storeIn(i).bits.uop.robIdx) && datavalid(j) && !uop(j).robIdx.needFlush(io.redirect) && !willRevoke(j)
    })))
    val lqViolationSelVec = VecInit((0 until LoadQueueRAWSize).map(j => {
      addrMaskMatch(j) && entryNeedCheck(j)
    }))

    // select logic
    val lqSelect: (Seq[Bool], Seq[UopEntry]) = selectOldestByGroup(lqViolationSelVec, uop, 0, isOlder)

    // select one inst
    val lqViolation = lqSelect._1(0)
    val lqViolationUop = lqSelect._2(0)

    if(debugEn) {
      XSDebug(
        lqViolation,
        "need rollback (ld wb before store) pc %x robidx %d target %x\n",
        storeIn(i).bits.uop.pc.get, storeIn(i).bits.uop.robIdx.asUInt, lqViolationUop.robIdx.asUInt
      )
    }

    (lqViolation, lqViolationUop)
  }

  // select rollback (part1) and generate rollback request
  // rollback check
  // Lq rollback seq check is done in s3 (next stage), as getting rollbackLq MicroOp is slow
  val rollbackLqWb = Wire(Vec(StorePipelineWidth, Valid(new UopEntry)))
  val stFtqIdx = Wire(Vec(StorePipelineWidth, new FtqPtr))
  val stFtqOffset = Wire(Vec(StorePipelineWidth, UInt(FetchBlockInstOffsetWidth.W)))
  val stIsRVC = Wire(Vec(StorePipelineWidth, Bool()))
  val stIsFirstIssue = Wire(Vec(StorePipelineWidth, Bool()))
  for (w <- 0 until StorePipelineWidth) {
    val detectedRollback = detectRollback(w)
    rollbackLqWb(w).valid := detectedRollback._1 && DelayN(storeIn(w).valid && !storeIn(w).bits.tlbMiss, TotalSelectCycles)
    rollbackLqWb(w).bits  := detectedRollback._2
    stFtqIdx(w) := DelayNWithValid(storeIn(w).bits.uop.ftqPtr, storeIn(w).valid, TotalSelectCycles)._2
    stFtqOffset(w) := DelayNWithValid(storeIn(w).bits.uop.ftqOffset, storeIn(w).valid, TotalSelectCycles)._2
    stIsRVC(w) := DelayNWithValid(storeIn(w).bits.uop.isRVC, storeIn(w).valid, TotalSelectCycles)._2
    stIsFirstIssue(w) := DelayNWithValid(storeIn(w).bits.uop.isFirstIssue, storeIn(w).valid, TotalSelectCycles)._2 // for perf
  }

  // select rollback (part2), generate rollback request, then fire rollback request
  // Note that we use robIdx - 1.U to flush the load instruction itself.
  // Thus, here if last cycle's robIdx equals to this cycle's robIdx, it still triggers the redirect.

  // select uop in parallel

  val allRedirect = (0 until StorePipelineWidth).map(i => {
    val redirect = Wire(Valid(new Redirect))
    redirect.valid := rollbackLqWb(i).valid
    redirect.bits             := DontCare
    redirect.bits.isRVC       := rollbackLqWb(i).bits.isRVC
    redirect.bits.robIdx      := rollbackLqWb(i).bits.robIdx
    redirect.bits.ftqIdx      := rollbackLqWb(i).bits.ftqPtr
    redirect.bits.ftqOffset   := rollbackLqWb(i).bits.ftqOffset
    redirect.bits.stIsRVC     := stIsRVC(i)
    redirect.bits.stFtqIdx    := stFtqIdx(i)
    redirect.bits.stFtqOffset := stFtqOffset(i)
    redirect.bits.level       := RedirectLevel.flush
    redirect.bits.target      := rollbackLqWb(i).bits.pc
    redirect.bits.debug_runahead_checkpoint_id := rollbackLqWb(i).bits.debugInfo.runahead_checkpoint_id
    redirect
  })
  io.rollback := allRedirect

  val oldestOH = Redirect.selectOldestRedirect(allRedirect)
  io.mdpTrain := Mux1H(oldestOH, allRedirect)
```

从上面的代码中可以看出, 在 LoadQueueRAW 中, 定义了违例检测的函数 detectRollback, 顾名思义, 就是检测某一个 Load 微操作是否需要回滚重放. 对于每一个 Store Pipeline, 我们都需要对其送入的 query 请求进行违例检查. 检查结束后, 如果出现了违例的情况, 就需要挑出来最老的一条违例的 Load 微操作, 并将包括这条微操作在内的后续所有微操作一并通过发起 redirect 请求冲刷掉, 并重新执行这些指令. 以下是检测和可能出现的 replay 逻辑的伪代码, 我们会对检测条件/算法进行细致的分析:

```plain
  for each store pipeline i:
    store_paddr = storeIn(i).paddr
    store_mask  = storeIn(i).mask

    for each RAW queue entry j:
      hit[j] =
        allocated[j] &&
        storeIn(i).valid &&
        load[j].robIdx is younger than store.robIdx &&
        load[j].dataValid &&
        load[j] not flushed &&
        load[j] not revoked &&
        paddr_match(store, load[j]) &&
        mask_overlap(store, load[j])

    victim = oldest_load_among(hit)
    if victim valid and store valid and !store.tlbMiss:
      generate Redirect to victim.pc
```

#### 违例检测 (1) - Store 地址 & 掩码检测

在上面的代码中可以看出, detectRollback 会把来自每一个 Store Pipeline 发来的地址信息 (地址和写掩码) 送到 paddrModule, 进行 CAM (Content Addressed Memory) 匹配. 在这里, 为了减轻 CAM 查询的时序压力, 我们并不是使用完成的 Store 物理地址, 而是将完成的物理地址进行位截断操作后生成的部分物理地址 (Partial PAddr) 送入 CAM 进行查表. detectRollback 中给的输入是 RegEnable 的输出, 表示只有在该路 Store Pipeline 发来的消息有效的情况下, 才把这些信息锁存到寄存器中. 接下来 CAM 侧的读取逻辑如下:

```scala
// 注意: 以下代码属于 LqPAddrModule (Load Queue physical address) 模块
// content addressed match
// 128-bits aligned
val needCacheLineCheck = enableCacheLineCheck && DCacheLineOffset > paddrOffset
for (i <- 0 until numCamPort) {
  for (j <- 0 until numEntries) {
    if (needCacheLineCheck) {
      val cacheLineOffset = DCacheLineOffset - paddrOffset
      val cacheLineHit    = io.violationMdata(i)(dataWidth - 1, cacheLineOffset) === data(j)(dataWidth - 1, cacheLineOffset)
      val lowAddrHit      = io.violationMdata(i)(cacheLineOffset - 1, 0) === data(j)(cacheLineOffset - 1, 0)
      io.violationMmask(i)(j) := cacheLineHit && (io.violationCheckLine.get(i) || lowAddrHit)
    } else {
      io.violationMmask(i)(j) := io.violationMdata(i) === data(j)
    }

  }
}

  // 注意: 以下代码属于 LqMaskModule (Load Queue Mask) 模块
  // content addressed match
  for (i <- 0 until numCamPort) {
    for (j <- 0 until numEntries) {
      io.violationMmask(i)(j) := (io.violationMdata(i) & data(j)).orR
    }
  }
```

如果 Store 是写入整条 Cache Line 的 (例如 RISC-V 的 cbo.zero 指令), 则 wlineflag 为高电平. 因此, 地址匹配的规则应该是: 如果是一条普通的 Store 类型指令, 同一个 Cache Line 并且 DCache Word 的低位地址匹配, 或者作为一个整条的 Cache Line 写类型指令, 只要 Cache Line 一样就算地址是匹配的. 对于掩码的检测来说, violationMmask 的计算逻辑等价于 `storeMask & loadMask != 0`也就是说, 只要检测的 Store 和 Load 之间访问的字节有交集, 就可能会出现匹配的违例情况.

回到 LoadQueueRAW 模块, 只有在地址匹配和掩码匹配的情况下才可能出现真正的 Load-Store 违例, 所以该模块会将 LqPAddr 模块和 LqMask 模块的可能违例输出进行合并 (`val addrMaskMatch = paddrModule.io.violationMmask(i).asUInt & maskModule.io.violationMmask(i).asUInt`) 对应上述伪代码中的 <code>paddr_match(store, load[j]) && mask_overlap(store, load[j])</code>.

#### 违例检测 (2) - 请求有效性验证

请求有效性的验证主要体现在 LoadQueueRAW 模块中的:

```scala
val entryNeedCheck = GatedValidRegNext(VecInit((0 until LoadQueueRAWSize).map(j => {
      allocated(j) && storeIn(i).valid && isAfter(uop(j).robIdx, storeIn(i).bits.uop.robIdx) && datavalid(j) && !uop(j).robIdx.needFlush(io.redirect) && !willRevoke(j)
    })))
```

这句代码给每一次违例检查提出了几点额外的要求: `allocated(j)`表示对应的 LoadQueueRAW 表项必须是有效的, 被分配的, 没有被分配的表项不参与违例检测; `storeIn(i).valid`表示如果本周期的 Store Pipeline 没能传来有效的 Store 地址, 那么本周期这条数据通路就不需要参与违例检测; `isAfter(uop(j).robIdx, storeIn(i).bits.uop.robIdx)`是 RAW 违例的基本的年龄条件, 表示只有较为年轻的 Load 微操作比较为年长的 Store 指令先执行才是问题, 如果 Load 本来就是更年轻的指令, 那么越过去执行就是正常的行为, 不产生违例; `datavalid(j)`表示对应的 Load 微操作已经拿到了 (可能错误的) 数据, 这个字段来自于 Load Query 时候传来的 dataValid, 如果这条 Load 微操作没有拿到有效数据, 那么也就不必要因为读取了错误的值而需要 replay (这种情况下有可能在拿到有效数据前, 较为年长的 Store 就已经执行完成了, 那么这时候 Load 才读取到有效数据的话就不算违例了); `!uop(j).robIdx.needFlush(io.redirect)`表示 Load 微操作没有因为重定向被冲刷掉, 如果这条 Load 因为某种原因出现重定向而被冲刷掉, 那么这条 Load 是不会修改体系结构状态的, 就不再需要重放了; 最后 `!willRevoke(j)`表示只有不会被撤回的 Load RAW Query 请求才需要计算是否会发生违例. 在请求有效性被验证之后, 以下代码会将地址和写掩码检测和请求有效性检测结果进行合并, 生成最终的是否真的发生违例的位图:

```scala
val lqViolationSelVec = VecInit((0 until LoadQueueRAWSize).map(j => {
      addrMaskMatch(j) && entryNeedCheck(j)
    }))
```

#### 重放逻辑 - 重放 Load 微操作选取

如果一个 Store 命中多个年轻的 Load 微操作, 我们不能随便挑一个 Load 微操作并从这个微操作开始重放. 必须选择 ROB 顺序最老的那个更年轻的 Load, 因为回滚到最老错误的 Load 之后可以覆盖它 (这条已经拿到错误的数据的指令) 及其后续执行的一切指令, 否则会出现状态跑飞的情况. 在 LoadQueueRAW 中, 使用 selectOldestByGroup 进行分组递归的选择 (出于时序的考量) 最老的出现问题的 Load 微操作, 并将其作为重放 redirect 的目标.

最后, 代码中的 `io.mdpTrain := Mux1H(oldestOH, allRedirect)`用来将 (新的) 违例信息告知内存依赖关系预测器, 预测器根据违例信息进行训练, 以后再遇到对应的指令就不允许 Load 再越过 Store 投机之行了.

## 香山昆明湖 V3 - SSIT 模块分析

SSIT 由 MenCtrl 实例化, 参数来自全局 Parameters. 通过 SSITSize 决定表项数, DecodeWidth / RenameWidth 决定读口数 (这两个参数必须是一样的), SSIDWidth (Store Set Identifier 的位宽) 由 LFST 决定. 考虑到乱序核心允许更年轻的 Load 在更年长的 Store 地址没有计算出来提前执行, 如果 LoadQueueRAW 模块后续发现出现了 RAW 违例, LoadQueueRAW 模块会发起重定向请求来冲刷掉错误执行的 Load 微操作, 这样的代价是非常大的, 所以我们需要在出现违例后即刻训练内存依赖关系预测器 (MDP). SSIT 用来记录某一对 Load-Store 指令属于同一个 Store Set (即这对指令的 Load 地址可能和 Store 有地址依赖), 下次再遇到这条 Load 指令执行的时候, 需要通过 LFST 查询是否还有可能有依赖关系的 Store 指令地址没有被计算出来, 从而减少反复因为内存依赖关系违例而造成的重定向冲刷.

具体来说, SSIT 是两张同步的表: `valid_array`与 `data_array`, 两张表都有 SSITSize 个表项 (也就是 2^foldPCWidth, 因为我们使用 foldPC 来查阅这两张表). valid\_array 负责记录每一个表项是否有效, 也就是对应的 foldPC 是否已经被分配到了一个 Store Set. data\_array 负责保存对应 foldPC 的 Store Set ID 号和 strict 位. Decode 阶段用每条指令的 foldPC 查阅 valid\_array 和 data\_array, 查阅后的结果会在 Rename 阶段送出 `{valid, ssid, strict}`记录. 当 Load QueueRAW 违例检测模块检测到真实的内存 RAW 违例, CtrlBlock 从 redirect 数据中获取 load/store PC, 并生成对应的 foldPC, SSIT 读取旧 entry 并按照 Store Set 合并机制更新两张表.

```scala
// Store Set Identifier Table Entry
class SSITEntry(implicit p: Parameters) extends XSBundle {
  val valid = Bool()
  val ssid = UInt(SSIDWidth.W) // store set identifier
  val strict = Bool() // strict load wait is needed
}

// ...

val io = IO(new Bundle {
  // to decode
  val ren = Vec(DecodeWidth, Input(Bool()))
  val raddr = Vec(DecodeWidth, Input(UInt(MemPredPCWidth.W))) // xor hashed decode pc(VaddrBits-1, 1)
  // to rename
  val rdata = Vec(RenameWidth, Output(new SSITEntry))
  // misc
  val update = Input(new MemPredUpdateReq) // RegNext should be added outside
  val csrCtrl = Input(new CustomCSRCtrlIO)
})
```

上述代码是 SSIT 的输入输出端口定义. `ren`作为 Decode 阶段传来的 SSIT 读使能信号, `raddr`是 DecodeWidth 个 foldPC, 用来查询该指令是否被 StoreSet 记录在册, `rdata`作为 SSIT 的主要输出, 输出 DecodeWidth 个 SSITEntry, 每个 entry 记录了这个 entry 是否 valid, 如果 valid 就关注给出的 ssid 和 strict, 其中 strict 表示同一个 SSID 发生了多次违例, 执行这条指令需要格外小心.

```scala
  private def hasRen: Boolean = true
  val valid_array = Module(new SyncDataModuleTemplate(
    Bool(),
    SSITSize,
    SSIT_READ_PORT_NUM,
    SSIT_WRITE_PORT_NUM,
    hasRen = hasRen,
  ))

  val data_array = Module(new SyncDataModuleTemplate(
    new SSITDataEntry,
    SSITSize,
    SSIT_READ_PORT_NUM,
    SSIT_WRITE_PORT_NUM,
    hasRen = hasRen,
  ))

for (i <- 0 until DecodeWidth) {
    // read SSIT in decode stage
    valid_array.io.ren.get(i) := io.ren(i)
    data_array.io.ren.get(i) := io.ren(i)
    valid_array.io.raddr(i) := io.raddr(i)
    data_array.io.raddr(i) := io.raddr(i)

    // gen result in rename stage
    io.rdata(i).valid := valid_array.io.rdata(i)
    io.rdata(i).ssid := data_array.io.rdata(i).ssid
    io.rdata(i).strict := data_array.io.rdata(i).strict
  }
```

上述代码是 SSIT 的 valid\_array 和 data\_array 的读取逻辑. 可以看出 SSIT 通过实例化带有读使能的 `SyncDataModuleTemplate` (在 `utility/src/main/scala/utility/DataModuleTemplate.scala`中定义) 来实现 valid 和 data array. 这个 SyncDataModule 会先把每个读口的 raddr 打一拍 (同地址读写具有 bypass 功能, 所以同一个周期写进去的数据, 下一拍会读到新值), 所以在 Decode 的时钟周期向 valid/data array 发送的读请求, 会在 Rename 对应的那个时钟周期拿到数据, 这也就是为什么 SSIT 的代码在没有创建寄存器的情况下实现译码阶段发起读请求, 重命名阶段拿到结果的效果.

```scala
  val resetCounter = RegInit(0.U(ResetTimeMax2Pow.W))
  resetCounter := resetCounter + 1.U

  // ...

  // flush SSIT
  // reset period: ResetTimeMax2Pow
  val resetStepCounter = RegInit(0.U(log2Up(SSITSize + 1).W))
  val s_idle :: s_flush :: Nil = Enum(2)
  val state = RegInit(s_flush)

  switch (state) {
    is(s_idle) {
      when(resetCounter(ResetTimeMax2Pow - 1, ResetTimeMin2Pow)(RegNext(io.csrCtrl.lvpred_timeout))) {
        state := s_flush
        resetCounter := 0.U
      }
    }
    is(s_flush) {
      when(resetStepCounter === (SSITSize - 1).U) {
        state := s_idle // reset finished
        resetStepCounter := 0.U
      }.otherwise{
        resetStepCounter := resetStepCounter + 1.U
      }
      valid_array.io.wen(SSIT_MISC_WRITE_PORT) := true.B
      valid_array.io.waddr(SSIT_MISC_WRITE_PORT) := resetStepCounter
      valid_array.io.wdata(SSIT_MISC_WRITE_PORT) := false.B
      debug_valid(resetStepCounter) := false.B
    }
  }
  XSPerfAccumulate("reset_timeout", state === s_flush && resetCounter === 0.U)
```

上述代码是 SSIT 的定期重置逻辑. 对于运行复杂的程序的时候 (例如运行操作系统), 往往会出现 PC/foldPC 碰撞的情况: 比如说操作系统在运行多个不一样的用户进程, 有多个进程在某个 PC 值有同样的内存 Load 指令, 但是只有一个 Load 指令是危险, 这时候如果一味的使用 SSIT 给出的需要等待的预测结果, 会降低其他用户进程 (那些 Load 其实乱序执行也不会造成违例的程序) 的执行效率. 因此 SSIT 支持定期的清空, 用来避免过时的预测数据带来的准确性干扰. 在昆明湖的 SSIT 中, 设有 resetCounter, 配合昆明湖自定义的 CSR 来配置重置 SSIT 记录的频率. resetCounter 是一个长度为 `ResetTimeMax2Pow`的寄存器, 在每个时钟周期会自动加一; resetStepCounter 是一个长度为 `log2Up(SSITSize + 1)`的寄存器, 用来追踪目前正在清空的 SSIT 记录编号. state 是一个状态寄存器, 控制 SSIT 的工作状态, 有 idle 和 flush 两个状态: idle 状态属于 SSIT 的常规状态, 可以正常的查询和更新记录, flush 状态表示 SSIT 处于刷新过程中, 这个状态会持续一段时间, 直到所有的 SSIT valid 位都被清空, 在 flush 模式下, 取决于重置的进度, 读取有违例风险的 Load 指令可能会读到 valid 或 invalid.

上述代码展示了 SSIT 的有限状态机, 初始化为 idle 状态, 在处理器执行过程中, 如果 resetCounter 足够大 (取决于 CSR 中设置的 lvpred\_timeout 到底看 resetCounter 的第几位), 就会把 state 寄存器状态更新为 flush, 并吧 resetCounter 清零. 在 flush 状态, SSIT 会每个周期会通过将 SSIT 的 valid\_array 的第 resetStepCounter 个表项设置为 false 来清除该表项. 如果所有的表项都被清空了, 就把 resetStepCounter 恢复成 0, 并把 state 状态寄存器恢复成 idle 状态.

接下来需要重点分析一下当 LoadQueueRAW 模块检测到发生 Store-Load 违例后, SSIT 如何根据或得到的 redirect 重定向信息, 来训练预测器的. 训练的过程也就是将出现问题的 Store 和 Load 指令分配到一个 Store Set 中, 并依此更新 SSIT. 更新的过程需要三个周期 (阶段) 分别是 S0, S1, 和 S2.

```scala
  // update stage 0: read ssit
  val s1_mempred_update_req_valid = RegNext(io.update.valid)
  val s1_mempred_update_req = RegEnable(io.update, io.update.valid)

  // when io.update.valid, take over ssit read port
  when (io.update.valid) {
    valid_array.io.raddr(SSIT_UPDATE_LOAD_READ_PORT) := io.update.ldpc
    valid_array.io.raddr(SSIT_UPDATE_STORE_READ_PORT) := io.update.stpc
    data_array.io.raddr(SSIT_UPDATE_LOAD_READ_PORT) := io.update.ldpc
    data_array.io.raddr(SSIT_UPDATE_STORE_READ_PORT) := io.update.stpc

    valid_array.io.ren.get(SSIT_UPDATE_LOAD_READ_PORT)  := true.B
    valid_array.io.ren.get(SSIT_UPDATE_STORE_READ_PORT) := true.B
    data_array.io.ren.get(SSIT_UPDATE_LOAD_READ_PORT)   := true.B
    data_array.io.ren.get(SSIT_UPDATE_STORE_READ_PORT)  := true.B
  }
```

SSIT 更新的 S0 阶段任务是读取 SSIT, 可以看到上述代码中 SSIT 对 valid\_array 和 data\_array 对应的更新读端口发起了读请求, 送入 ldpc 和 stpc. 由于 SSIT 使用的是 `SyncDataModuleTemplate`读地址信息会在模块内打一拍, 所以读出数据是在下一拍会发生的事情. 在这一拍, 我们同时会通过使用 RegNext 和 RegEnable 寄存器将 MDP 的更新请求数据保存到下一个周期, 也就是 SSIT 更新的 S1 阶段.

```scala
  // update stage 1: get ssit read result

  // Read result
  // load has already been assigned with a store set
  val s1_loadAssigned = valid_array.io.rdata(SSIT_UPDATE_LOAD_READ_PORT)
  val s1_loadOldSSID = data_array.io.rdata(SSIT_UPDATE_LOAD_READ_PORT).ssid
  val s1_loadStrict = data_array.io.rdata(SSIT_UPDATE_LOAD_READ_PORT).strict
  // store has already been assigned with a store set
  val s1_storeAssigned = valid_array.io.rdata(SSIT_UPDATE_STORE_READ_PORT)
  val s1_storeOldSSID = data_array.io.rdata(SSIT_UPDATE_STORE_READ_PORT).ssid
  val s1_storeStrict = data_array.io.rdata(SSIT_UPDATE_STORE_READ_PORT).strict
  // val s1_ssidIsSame = s1_loadOldSSID === s1_storeOldSSID
```

SSIT 更新的 S1 阶段任务是获得 SSIT 的读数据, 这些数据会在 S2 的代码中通过 RegEnable (Enable 的条件是 S0 保留到 S1 的 update 请求 valid 信号) 保留到 S2 阶段所在的周期.

```scala
  // update stage 2, update ssit data_array
  val s2_mempred_update_req_valid = RegNext(s1_mempred_update_req_valid)
  val s2_mempred_update_req = RegEnable(s1_mempred_update_req, s1_mempred_update_req_valid)
  val s2_loadAssigned = RegEnable(s1_loadAssigned, s1_mempred_update_req_valid)
  val s2_storeAssigned = RegEnable(s1_storeAssigned, s1_mempred_update_req_valid)
  val s2_loadOldSSID = RegEnable(s1_loadOldSSID, s1_mempred_update_req_valid)
  val s2_storeOldSSID = RegEnable(s1_storeOldSSID, s1_mempred_update_req_valid)
  val s2_loadStrict = RegEnable(s1_loadStrict, s1_mempred_update_req_valid)

  val s2_ssidIsSame = s2_loadOldSSID === s2_storeOldSSID
  // for now we just use lowest bits of ldpc as store set id
  val s2_ldSsidAllocate = XORFold(s2_mempred_update_req.ldpc, SSIDWidth)
  val s2_stSsidAllocate = XORFold(s2_mempred_update_req.stpc, SSIDWidth)
  val s2_allocSsid = Mux(s2_ldSsidAllocate < s2_stSsidAllocate, s2_ldSsidAllocate, s2_stSsidAllocate)
  // both the load and the store have already been assigned store sets
  // but load's store set ID is smaller
  val s2_winnerSSID = Mux(s2_loadOldSSID < s2_storeOldSSID, s2_loadOldSSID, s2_storeOldSSID)

  def update_ld_ssit_entry(pc: UInt, valid: Bool, ssid: UInt, strict: Bool) = {
    valid_array.io.wen(SSIT_UPDATE_LOAD_WRITE_PORT) := true.B
    valid_array.io.waddr(SSIT_UPDATE_LOAD_WRITE_PORT) := pc
    valid_array.io.wdata(SSIT_UPDATE_LOAD_WRITE_PORT) := valid
    data_array.io.wen(SSIT_UPDATE_LOAD_WRITE_PORT) := true.B
    data_array.io.waddr(SSIT_UPDATE_LOAD_WRITE_PORT) := pc
    data_array.io.wdata(SSIT_UPDATE_LOAD_WRITE_PORT).ssid := ssid
    data_array.io.wdata(SSIT_UPDATE_LOAD_WRITE_PORT).strict := strict
    debug_valid(pc) := valid
    debug_ssid(pc) := ssid
    debug_strict(pc) := strict
  }

  def update_st_ssit_entry(pc: UInt, valid: Bool, ssid: UInt, strict: Bool) = {
    valid_array.io.wen(SSIT_UPDATE_STORE_WRITE_PORT) := true.B
    valid_array.io.waddr(SSIT_UPDATE_STORE_WRITE_PORT) := pc
    valid_array.io.wdata(SSIT_UPDATE_STORE_WRITE_PORT):= valid
    data_array.io.wen(SSIT_UPDATE_STORE_WRITE_PORT) := true.B
    data_array.io.waddr(SSIT_UPDATE_STORE_WRITE_PORT) := pc
    data_array.io.wdata(SSIT_UPDATE_STORE_WRITE_PORT).ssid := ssid
    data_array.io.wdata(SSIT_UPDATE_STORE_WRITE_PORT).strict := strict
    debug_valid(pc) := valid
    debug_ssid(pc) := ssid
    debug_strict(pc) := strict
  }

  when(s2_mempred_update_req_valid){
    switch (Cat(s2_loadAssigned, s2_storeAssigned)) {
      // 1. "If neither the load nor the store has been assigned a store set, two are allocated and assigned to each instruction."
      is ("b00".U(2.W)) {
        update_ld_ssit_entry(
          pc = s2_mempred_update_req.ldpc, valid = true.B,
          ssid = s2_allocSsid, strict = false.B
        )
        update_st_ssit_entry(
          pc = s2_mempred_update_req.stpc, valid = true.B,
          ssid = s2_allocSsid, strict = false.B
        )
      }
      // 2. "If the load has been assigned a store set, but the store has not, one is allocated and assigned to the store instructions."
      is ("b10".U(2.W)) {
        update_st_ssit_entry(
          pc = s2_mempred_update_req.stpc, valid = true.B,
          ssid = s2_ldSsidAllocate, strict = false.B
        )
      }
      // 3. "If the store has been assigned a store set, but the load has not, one is allocated and assigned to the load instructions."
      is ("b01".U(2.W)) {
        update_ld_ssit_entry(
          pc = s2_mempred_update_req.ldpc, valid = true.B,
          ssid = s2_stSsidAllocate, strict = false.B
        )
      }
      // 4. "If both the load and the store have already been assigned store sets, one of the two store sets is declared the "winner". The instruction belonging to the loser’s store set is assigned the winner’s store set."
      is ("b11".U(2.W)) {
        update_ld_ssit_entry(
          pc = s2_mempred_update_req.ldpc, valid = true.B,
          ssid = s2_winnerSSID, strict = false.B
        )
        update_st_ssit_entry(
          pc = s2_mempred_update_req.stpc, valid = true.B,
          ssid = s2_winnerSSID, strict = false.B
        )
        when(s2_ssidIsSame){
          data_array.io.wdata(SSIT_UPDATE_LOAD_READ_PORT).strict := true.B
          debug_strict(s2_mempred_update_req.ldpc) := true.B
        }
      }
    }
  }
```

SSIT 更新的 S2 阶段是真正对 valid\_array 和 data\_array 作修改的阶段. 这个阶段会利用 S1 阶段保留下来的更新请求信息, `loadAssigned`,`loadOldSSID`,`loadStrict`,`storeAssigned`,`storeOldSSID`, 和`storeStrict`信息. 判断从 data\_array 中读到的指令的 loadSSID 和 storeSSID 是否一致 (可能出现读出无效值的情况, 如果对应的 valid\_array 值为 false), 并对 Load 和 Store 分别通过 对其 PC 进行 XORFold 获得新分配的 SSID 表项号, 取两个 XORFold 结果较小的一个作为 allocSsid. 还会对已经读出的 loadOldSSID 和 storeOldSSID 选取较小的一个座位 winnerSSID.

代码中定义了两个辅助函数 `update_ld_ssit_entry`和 `update_st_ssit_entry`, 他们会分别使用 `SSIT_UPDATE_LOAD_WRITE_PORT`和 `SSIT_UPDATE_STORE_WRITE_PORT`对 valid\_array 和 data\_array 发起写请求. 对 valid\_array 来说, 会将对应 foldPC 的 valid 位置为 true; 对 data\_array 来说, 会写入传入的 SSID 和是否为 strict 信息 (还会更新 debug\_valid, debug\_ssid 和 debug\_strict, 这些都是用于调试的).

接下来的代码就是 S2 SSIT 更新的核心部分, 如果 S2 当前周期的 `s2_mempred_update_req_valid`为真, 表示两个周期前收到了 update SSIT 的请求. SSIT 会根据 `Cat(s2_loadAssigned, s2_storeAssigned)`, 也就是违例的 Load 指令是否已经被分配 SSID 和违例的 Store 指令是否被分配 SSID, 所以一共有四种情况: Load 和 Store 都没有被分配 SSID; Load 有被分配 SSID, Store 没有被分配 SSID; Load 没有被分配 SSID, Store 有被分配 SSID; Load 和 Store 都有被分配 SSID.

对于第一种情况, 如果 Load 和 Store 都没有被分配 SSID, 就会调用 `update_ld_ssit_entry`和 `update_ld_ssit_entry`, 将 Load 和 Store 组合到新分配的 Store Set 中.

对于第二种情况, 如果 Load 已经被分配 SSID 但 Store 还没有, 就只会调用 `update_st_ssit_entry`, 将 Store 融入到 Load 已经被分配的 SSID 中.

对于第三种情况, 如果 Store 已经被分配 SSID 但 Load 还没有, 就只会调用 `update_ld_ssit_entry`, 将 Load 融入到 Store 已经被分配的 SSID 中.

对于第四种情况, 如果 Load 和 Store 都有被分配 SSID, 就会调用`update_ld_ssit_entry`和 `update_ld_ssit_entry`, 将 Load 和 Store 组合到 winnerSSID 对应的 Store Set 中. 这个 winnterSSID 是出现违例的 Store 和 Load 的 SSID 中较小的一个, 由于 SSID 最初是通过 XORFold 算法产生的, 所以 winnerSSID 总是能让 Load 绑定到之前那个出现问题的 Store 指令 (否则可能会有连续的 Load-Store-Load-Store 中 Load 被错误的绑定到后面的 Store 指令里, 造成低准确率的预测). 如果发现读到的 loadOldSSID 和 storeOldSSID 是一样的, 说明这是第二次发生违例了, 这时候会将 Load 对应的 SSIT 表项的 Strict 置位, 表示投机执行这条 Load 风险是相对较高的.

## 香山昆明湖 V3 - LFST 模块分析

LFST (Last Fetched Store Table) 是 Store Set 的动态部分, 它不用 PC (foldPC) 进行寻址, 只用来追踪当前这个 Store Set 里最近一次已经从分派模块 (Dispatch) 进入后端, 但还没有在 store unit S1 发射的 Store 指令是哪个. LFST 解决了 SSIT 只能告知某条 Load 指令属于哪个 Store Set, 但不知道这个 Store Set 发生了什么的问题, 也解决了同一个 Store Set 可能有多个进入窗口的 Store 指令, 不知道要等待哪一个的问题. 所以 LFST 以 SSID 为索引, 用 robIdx 位动态窗口值, 对进行查询的 Load 指令返回 shouldWait (LFST 是否建议这条 Load 指令先不要投机乱序执行) 以及 waitForRobIdx (LFST 告诉这条 Load 如果先不要投机乱序执行的话, 需要等待那个 Store 指令的地址就绪之后就可以执行了).

```scala
class LFSTReq(implicit p: Parameters) extends XSBundle {
  val isstore = Bool()
  val ssid = UInt(SSIDWidth.W) // use ssid to lookup LFST
  val robIdx = new RobPtr
}

class LFSTResp(implicit p: Parameters) extends XSBundle {
  val shouldWait = Bool()
  val robIdx = new RobPtr
}

class DispatchLFSTIO(implicit p: Parameters) extends XSBundle {
  val req = Vec(RenameWidth, Valid(new LFSTReq))
  val resp = Vec(RenameWidth, Flipped(Valid(new LFSTResp)))
}

// ...

  val io = IO(new Bundle {
    // when redirect, mark canceled store as invalid
    val redirect = Input(Valid(new Redirect))
    val dispatch = Flipped(new DispatchLFSTIO)
    // when store issued, mark store as invalid
    val storeIssue = Vec(backendParams.StaExuCnt, Flipped(Valid(new StoreUnitToLFST)))
    val csrCtrl = Input(new CustomCSRCtrlIO)
  })
```

上述代码是 LFST 的输入输出的定义. `redirect`负责采集重定向的信息 (主要包括重定向是否真的发生了, 以及发生重定向的 robIdx) 用来更新 LFST 中最近完成分派, 但还没有发射的 Store 指令信息. `dispatch`信号则更像一个 Bundle, 包括 RenameWidth 组`LFSTReq`和`LFSTResp`.`LFSTReq`是来自分派 Dispatch 阶段的 LFST 查询请求信号, 包括`isstore`这条查询的指令是否是 Store 类型的指令,`ssid`告知 LFST 这条指令所在的 Store Set ID 号,`robIdx`告知 LFST 这条指令对应的 ROB 表项号, 此外, 请求信号由 `Valid(new LFSTReq)`定义, 说明还隐含有 valid 信号, 告知 LFST 这个请求是否有意义. `LFSTResp`是返回给分派阶段的 LFST 查询结果信号, 包括布尔信号`shouldWait`, 告知分派模块这条指令是否建议等待流水线中其他的 Store 指令, 如果`shouldWait`为真, 那么`robIdx`就包含了这条指令建议等待哪条 Store 指令的地址就绪后再进行发射.

```scala
  val validVec = RegInit(VecInit(Seq.fill(LFSTSize)(VecInit(Seq.fill(LFSTWidth)(false.B)))))
  val robIdxVec = Reg(Vec(LFSTSize, Vec(LFSTWidth, new RobPtr)))
  val allocPtr = RegInit(VecInit(Seq.fill(LFSTSize)(0.U(log2Up(LFSTWidth).W))))
  val valid = Wire(Vec(LFSTSize, Bool()))
  (0 until LFSTSize).map(i => {
    valid(i) := validVec(i).asUInt.orR
  })
```

上述代码是 LFST 的存储器的定义. LFST 使用普通的 Reg 寄存器来保存其状态. validVec 寄存器可以被看作一个二维数组, validVec(i)(j) 表示第 i 个 SSID 的第 j 个记录是否是有效的. 目前 LFSTWidth 是 2. robIdxVec 寄存器也可以被看作一个二维数组, robIdxVec(i)(j) 表示第 i 个 SSID 的第 j 个记录的 Store 指令对应的 ROB 表项号. allocPtr 寄存器可以看作一个一位数组, allocPtr(i) 表示第 i 个 SSID 应该被分配第几个 robIdx 表项位.

```scala
  // read LFST in rename stage
  for (i <- 0 until RenameWidth) {
    io.dispatch.resp(i).valid := io.dispatch.req(i).valid

    // If store-load pair is in the same dispatch bundle, loadWaitBit should also be set for load
    val hitInDispatchBundleVec = if(i > 0){
      WireInit(VecInit((0 until i).map(j =>
        io.dispatch.req(j).valid &&
        io.dispatch.req(j).bits.isstore &&
        io.dispatch.req(j).bits.ssid === io.dispatch.req(i).bits.ssid
      )))
    } else {
      WireInit(VecInit(Seq(false.B))) // DontCare
    }
    val hitInDispatchBundle = hitInDispatchBundleVec.asUInt.orR
    // Check if store set is valid in LFST
    io.dispatch.resp(i).bits.shouldWait := (
        (valid(io.dispatch.req(i).bits.ssid) || hitInDispatchBundle) &&
        io.dispatch.req(i).valid &&
        (!io.dispatch.req(i).bits.isstore || io.csrCtrl.storeset_wait_store)
      ) && !io.csrCtrl.lvpred_disable || io.csrCtrl.no_spec_load
    io.dispatch.resp(i).bits.robIdx := robIdxVec(io.dispatch.req(i).bits.ssid)(allocPtr(io.dispatch.req(i).bits.ssid)-1.U)
    if(i > 0){
      (0 until i).map(j =>
        when(hitInDispatchBundleVec(j)){
          io.dispatch.resp(i).bits.robIdx := io.dispatch.req(j).bits.robIdx
        }
      )
    }
  }
```

上述代码是 LFST 的读取逻辑. LFST 模块在每个周期都有可能收到 RenameWidth 宽度的读取请求. LFST 会在当拍返回读取数据, 所以 response 的 valid 信号直接和 request 的 valid 信号连通 (这种情况下这个信号可能会直接被 Chisel 后端优化掉了). `hitInDispatchBundleVec`和`hitInDispatchBundle`用来计算同一个周期内, 相比于当前指令是否有更年老的 Store 指令和本条指令在同一个 SSID.

LFST 模块判断是否需要等待的逻辑是: <code>[(valid(io.dispatch.req(i).bits.ssid) || hitInDispatchBundle) && io.dispatch.req(i).valid && (!io.dispatch.req(i).bits.isstore || io.csrCtrl.storeset_wait_store)] && !io.csrCtrl.lvpred_disable || io.csrCtrl.no_spec_load</code>. 首先来看第一个大条件 (方括号中的布尔表达式): 传送进来的请求所对应的 SSID 在 validVec 中有有效的记录, 发送来的请求不是 Store 指令或者我们在 CSR 中设置让 Store 指令也进行等待, 请求所携带的 SSID 必须是有效的或者在本周期内有更年长的指令传来相同的 SSID 请求. 如果该条件满足, 且预测器不处于关闭状态, 就需要等待. 如果我们在 CSR 中设置了 no\_spec\_load, 也就是不能够冒险的执行 Load 指令, 那样的话 shouldWait 就一直是高电平, 永远不会允许 Load 指令投机执行.

有了 shouldWait, 还需要知道这条被 LFST 建议需要等待的指令到底要等谁, 所以接下来分析 LFST 计算需要等待的指令的 robIdx 逻辑: 对于一般情况, 我们会返回 `robIdxVec(io.dispatch.req(i).bits.ssid)(allocPtr(io.dispatch.req(i).bits.ssid)-1.U)`, 也就是读取对应 SSID 的最近一次被写入的 LFST ROB 表项号. 还有一种特殊情况, 那就是在当前周期内, 有更年老的 Store 指令发来了查询请求, 那我们就会把建议等待的 ROB 表项号更新成这个更年老的 Store 指令的 ROB 表项号. 借助循环的语义, 如果一个周期内有多个较为年长的 Store 指令共享 SSID, 会选取哪个稍微更年轻的 Store 指令作为等待的对象.

```scala
  // when store is issued, mark it as invalid
  (0 until backendParams.StaExuCnt).map(i => {
    // TODO: opt timing
    (0 until LFSTWidth).map(j => {
      when(io.storeIssue(i).valid && io.storeIssue(i).bits.storeSetHit && io.storeIssue(i).bits.robIdx.value === robIdxVec(io.storeIssue(i).bits.ssid)(j).value){
        validVec(io.storeIssue(i).bits.ssid)(j) := false.B
      }
    })
  })
```

上述代码是 LFST 在 Store 指令被发射的时候更新 LFST 的逻辑. 如果一条 Store 指令已经被发射了, 就可以在 LFST 中清除有关这条指令的记录. 可以从代码中看出, 当满足 `io.storeIssue(i).valid && io.storeIssue(i).bits.storeSetHit && io.storeIssue(i).bits.robIdx.value === robIdxVec(io.storeIssue(i).bits.ssid)(j).value`(也就是说, 某个 Store 指令已经被发射了, 并且这条 Store 指令命中了 Store Set, 而且在 LFST 中有关于这条指令的 ROB index 的记录) 的时候, 在 validVec 寄存器取消 valid 置位.

```scala
  val overflowVec = WireInit(VecInit(Seq.fill(RenameWidth)(false.B)))
  // when store is dispatched, mark it as valid
  (0 until RenameWidth).map(i => {
    when(io.dispatch.req(i).valid && io.dispatch.req(i).bits.isstore){
      val waddr = io.dispatch.req(i).bits.ssid
      val wptr = allocPtr(waddr)
      allocPtr(waddr) := allocPtr(waddr) + 1.U
      validVec(waddr)(wptr) := true.B
      robIdxVec(waddr)(wptr) := io.dispatch.req(i).bits.robIdx
      when(validVec(waddr)(wptr)) {
        overflowVec(i) := true.B
      }
    }
  })
```

上述代码是 LFST 在 Store 指令离开分派阶段的时候更新 LFST 的逻辑. 如果一条 Store 指令离开了分派模块, 就需要把这条 Store 指令的 SSID 和 ROB 表项号等级在 LFST 中. 可以从代码中看出, 当分派模块发来有效的请求, 且这个请求的指令属于 Store 指令的时候, 会更新 LFST 的 allocPtr (自增 1), 置位对应的 validVec, 并把这条 Store 指令的 ROB 表项号写入 robIdxVec.

```scala
  // when redirect, cancel store influenced
  (0 until LFSTSize).map(i => {
    (0 until LFSTWidth).map(j => {
      when(validVec(i)(j) && robIdxVec(i)(j).needFlush(io.redirect)){
        validVec(i)(j) := false.B
      }
    })
  })

  // recover robIdx after squash
  // behavior model, to be refactored later
  when(RegNext(io.redirect.fire)) {
    (0 until LFSTSize).map(i => {
      (0 until LFSTWidth).map(j => {
        val check_position = WireInit(allocPtr(i) + (j+1).U)
        when(!validVec(i)(check_position)){
          allocPtr(i) := check_position
        }
      })
    })
  }
```

上述代码是 LFST 在出现重定向时的更新逻辑. 当重定向发生后, 某些 LFST 中记录的 Store 指令会被取消掉. 在代码中, 每个周期都会对 LFST 的每一个 SSID 的每一个有效的 (validVec 为真的) ROB 表项记录 (共 LFSTWidth 个) 逐一进行 needFlush 检查, 如果发现某条指令已经因为重定向被冲刷掉了, 就会将对应的 validVec 位置低. LFST 模块还需要在重定向发生的下一个周期 (这个周期已经完成了对 validVec 的修改) 根据最新的 validVec 更新每个 SSID 对应的 LFST 分配指针 allocPtr.

## 香山昆明湖 V3 - MDP 模块间的交互

### MemCtrl - 后端内存控制模块

```scala
class MemCtrl(params: BackendParams)(implicit p: Parameters) extends XSModule {
  val io = IO(new MemCtrlIO(params))

  private val ssit = Module(new SSIT)
  private val lfst = Module(new LFST)
  ssit.io.update <> RegNext(io.memPredUpdate)
  ssit.io.csrCtrl := RegNext(io.csrCtrl)

  for (i <- 0 until RenameWidth) {
    ssit.io.ren(i) := io.mdpFoldPcVecVld(i)
    ssit.io.raddr(i) := io.mdpFlodPcVec(i)
  }
  lfst.io.redirect <> RegNext(io.redirect)
  lfst.io.storeIssue <> RegNext(io.stIn)
  lfst.io.csrCtrl <> RegNext(io.csrCtrl)
  lfst.io.dispatch <> io.dispatchLFSTio

  //  io.waitTable2Rename := waittable.io.rdata
  io.waitTable2Rename := DontCare
  io.ssit2Rename := ssit.io.rdata
}

class MemCtrlIO(params: BackendParams)(implicit p: Parameters) extends XSBundle {
  val redirect = Flipped(ValidIO(new Redirect))
  val csrCtrl = Input(new CustomCSRCtrlIO)
  val stIn = Vec(params.StaExuCnt, Flipped(ValidIO(new StoreUnitToLFST))) // use storeSetHit, ssid, robIdx
  val memPredUpdate = Input(new MemPredUpdateReq)
  val mdpFoldPcVecVld = Input(Vec(DecodeWidth, Bool()))
  val mdpFlodPcVec = Input(Vec(DecodeWidth, UInt(MemPredPCWidth.W)))
  val dispatchLFSTio = Flipped(new DispatchLFSTIO)
  val waitTable2Rename = Vec(DecodeWidth, Output(Bool()))   // loadWaitBit
  val ssit2Rename = Vec(RenameWidth, Output(new SSITEntry)) // ssit read result
}
```

上述代码是后端的 MenCtrl 部分, 也就是后端负责内存控制的模块. Store Set 的两张表 SSIT 和 LFST 都在 MemCtrl 中初始化. 如果收到了访存单元发来的重定向 (也就是预测器更新), 就回吧更新内容打一拍之后发到 SSIT 中. 由于我们可以通过 CSR 配置内存预测的工作参数 (比如说, 多久重置一次 SSIT), 所以也把这些数据打一拍后送到 SSIT 中. 在代码中也有每个周期给对应的指令查询 SSIT / LFST 并返回查询数据的代码.

### MemCtrl 和后端 CtrlBlock 的交互

```scala
  private val memViolation = io.fromMem.violation
  val loadReplay = Wire(ValidIO(new Redirect))
  loadReplay.valid := GatedValidRegNext(memViolation.valid)
  loadReplay.bits := RegEnable(memViolation.bits, memViolation.valid)
  loadReplay.bits.debugIsCtrl := false.B
  loadReplay.bits.debugIsMemVio := true.B

  pcMem.io.ren.get(pcMemRdIndexes("redirect").head) := memViolation.valid
  pcMem.io.raddr(pcMemRdIndexes("redirect").head) := memViolation.bits.ftqIdx.value
  val mdpTrainValid = io.fromMem.mdpTrain.valid
  for ((pcMemIdx, i) <- pcMemRdIndexes("memPredLoad").zipWithIndex) {
    val ren   = mdpTrainValid
    val raddr = io.fromMem.mdpTrain.bits.ftqIdx.value
    val offset = RegEnable(io.fromMem.mdpTrain.bits.getPcOffset, mdpTrainValid)
    pcMem.io.ren.get(pcMemIdx) := ren
    pcMem.io.raddr(pcMemIdx) := raddr
    memCtrl.io.memPredUpdate.ldpc := XORFold((pcMem.io.rdata(pcMemIdx).toUInt + offset)(VAddrBits - 1, 1), MemPredPCWidth)

    // update wait table, will be remove in the future
    memCtrl.io.memPredUpdate.waddr := XORFold((pcMem.io.rdata(pcMemIdx).toUInt + offset)(VAddrBits - 1, 1), MemPredPCWidth)
    memCtrl.io.memPredUpdate.wdata := true.B
  }
  for ((pcMemIdx, i) <- pcMemRdIndexes("memPredStore").zipWithIndex) {
    val ren   = mdpTrainValid
    val raddr = io.fromMem.mdpTrain.bits.stFtqIdx.value
    val offset = RegEnable(io.fromMem.mdpTrain.bits.getStPcOffset, mdpTrainValid)
    pcMem.io.ren.get(pcMemIdx) := ren
    pcMem.io.raddr(pcMemIdx) := raddr
    memCtrl.io.memPredUpdate.stpc := XORFold((pcMem.io.rdata(pcMemIdx).toUInt + offset)(VAddrBits - 1, 1), MemPredPCWidth)
  }
  memCtrl.io.memPredUpdate.valid := RegNext(mdpTrainValid) // pc is ready, 1 cycle later

  // ...

  // memory dependency predict
  // when decode, send fold pc to mdp
  private val mdpFlodPcVecVld = Wire(Vec(DecodeWidth, Bool()))
  private val mdpFlodPcVec = Wire(Vec(DecodeWidth, UInt(MemPredPCWidth.W)))
  for (i <- 0 until DecodeWidth) {
    mdpFlodPcVecVld(i) := decode.io.out(i).fire
    mdpFlodPcVec(i) := decode.io.out(i).bits.foldpc
  }

  // currently, we only update mdp info when isReplay
  memCtrl.io.redirect := s1_s3_redirect
  memCtrl.io.csrCtrl := io.csrCtrl                          // RegNext in memCtrl
  memCtrl.io.stIn := io.fromMem.stIn                        // RegNext in memCtrl
  memCtrl.io.mdpFoldPcVecVld := mdpFlodPcVecVld
  memCtrl.io.mdpFlodPcVec := mdpFlodPcVec
  memCtrl.io.dispatchLFSTio <> dispatch.io.lfst

  rename.io.hartId := io.fromTop.hartId
  rename.io.ratDiffCommits.foreach(_ := rob.io.diffCommits.get)
  rename.io.ratDiffVlCommits.foreach(_ := rob.io.diffVlCommits.get)

  rename.io.redirect := s1_s3_redirect
  rename.io.rabCommits := rob.io.rabCommits
  rename.io.vlCommits := rob.io.vlCommits
  rename.io.singleStep := GatedValidRegNext(io.csrCtrl.singlestep)
  rename.io.waittable := (memCtrl.io.waitTable2Rename zip decode.io.out).map{ case(waittable2rename, decodeOut) =>
    RegEnable(waittable2rename, decodeOut.fire)
  }
  rename.io.ssit := memCtrl.io.ssit2Rename
```

上述代码是后端控制块, 后端内存控制块, 和内存模块发来的违例信息进行交互的逻辑. `io.fromMem.violation`是 MemBlock 报给后端的内存回滚请求, 来源包括 load replay, RAW/RAR 违例, uncache/nuke rollback 等, CtrlBlock 把它包装成 loadReplay. pcMem 保存的是前端 FTQ 每个 entry 的 startPC, 内存侧 redirect 里只有 ftqIdx + ftqOffset + isRVC, 所以 CtrlBlock 要通过`pcMem[ftqIdx] + getPcOffset()`还原出真实指令 PC. `io.fromMem.mdpTrain` 也是一个 `Valid[Redirect]`, 但语义不是 “发起恢复”，而是“拿这次真实 store-load 违例训练内存依赖预测器”. 它来自 LoadQueueRAW：store 写回时查 LoadQueue, 发现更年轻 load 已经错误取数, 就生成 redirect, 并把 load 的 ftqIdx/ftqOffset 和 store 的 stFtqIdx/stFtqOffset 都填进去.

下面一半的代码是在 CtrlBlock 里把 “取指/译码阶段看到的内存指令 PC 信息” 送进内存依赖预测器，并把预测结果接回 rename/dispatch 使用: 每个 decode lane 在 decode.io.out(i).fire 时, 把该指令已经由前端算好的 foldpc 作为 mdpFlodPcVec(i) 送给 memCtrl, memCtrl 内部用它去读 SSIT, 判断这条 load/store 是否属于某个 store set; 同时 memCtrl 还接收 redirect; CSR 控制; 和 store issue 信息, 用于清理 LFST; 响应 CSR 开关; 以及当 store 真正发射后释放对应依赖. dispatch.io.lfst 和 memCtrl.io.dispatchLFSTio 相连, 表示 dispatch 阶段会把 store/load 的 store set 请求送到 LFST: store 会登记为最近未完成 store, load 会查询自己是否应该等待某个更老 store. 后面这些 rename.io.\* 是把 ROB 提交; redirect; 单步调试等正常控制信息接给 rename, 其中 rename.io.ssit := memCtrl.io.ssit2Rename 是关键, 表示 rename 阶段拿到 SSIT 的预测结果, 给指令打上 store set 依赖信息.