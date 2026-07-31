# 10. 访存依赖关系检测与 Replay 机制

## 概述

在乱序处理器中, 内存指令带来的核心矛盾是: Load 指令越早执行, 越有机会暴露 cache miss, 唤醒后续以来指令, 提高内存级并行度; 但 Load 执行的越早, 就越可能越过尚未解析地址的旧 Store, 从而破坏程序规定的内存顺序. 与寄存器以来不同, Load 和 Store 之间是否相关并不能只靠指令编码或寄存器号判断, 它最终取决于运行时计算出来的地址. 因此, 内存依赖关系检测本质上是在回答一个问题: 一条年轻的 Load, 能不能在前面那些 Store 完全确定之前先执行?

一种极端的方案是完全放开, 让所有 Load / Store 都按照调度器和执行单元的可用情况乱序执行. 这种方式看起来最激进, 也有利于性能, 因为 Load 不必等待旧 Store, 内存访问可以尽早发出. 但它的问题同样致命: 如果程序顺序中较老的 Store 和较年轻的 Load 访问同一个地址, Load 却先执行了, 那么 Load 可能读到旧值. 后续依赖这条 Load 的指令都会基于这个错误数据继续执行, 错误会沿着数据依赖链扩散. 更严重的是, 这种错误不一定能靠普通寄存器重命名或发射队列机制发现, 因为问题发生在内存别名关系上. 完全乱序如果没有检测和恢复机制, 就无法保证单线程程序语义正确.

另一种极端的方案是完全保守, 严格按照程序顺序处理所有内存指令, 尤其是让每条 Load 等待所有更老的 Store 地址甚至数据都确定后再执行. 这样确实可以避免 Store-Load 顺序违例, 因为 Load 执行时已经知道前面所有的 Store 是否与它同地址, 也可以正确进行转发或等待. 但这个策略会严重牺牲乱序处理器的价值. 真实程序中, 大多数 Store 和后续 Load 并不访问同一个地址; 如果所有 Load 都因为 “可能相关” 而等待, 就会把大量实际上无关的内存访问串行化. Load miss 不能被提前发出, 后续依赖链不能及时启动, ROB 和发射队列会被等待中的指令占住, 执行窗口的并行性被认为压缩. 结果是正确性得到了保证, 但性能接近一种过度保守的顺序内存流水线.

因此, 高性能处理器通常采用折中方案: 预测 + 检测 + replay. 预测机制根据历史性温判断某条 Load 是否可能依赖某些旧的 Store. 对于预测为无关的 Load, 允许它今早执行, 从而保留乱序执行和内存级并行度; 对于预测为相关的 Load, 则让它等待待定 Store, 而不是等待所有旧 Store. 这样可以避免完全顺序方案中过度阻塞的问题.

预测错误时, 则由检测和 replay 机制兜底. 也就是说, 处理器允许自己在内存依赖上做有根据的冒险, 但不会无条件相信预测结果. 当旧 Store 地址解析后, 如果发现某条年轻 Load 已经错误的越过了它并读取了错误数据, 硬件会识别出这次违例, 取消受影响的执行结果, 并从出错 Load 或相关位置重新执行. 这个 replay 的代价只在预测失败时支付, 而不是让所有 Load 在正常情况下都提前支付等待的代价.

这种设计的优势在于, 它把问题从 “所有 Load 都必须保守等待” 转化为 “只有高风险 Load 才等待, 低风险 Load 先执行, 少数错判再恢复”. 完全乱序追求性能但缺少正确性保障; 完全顺序保证正确性但浪费了大量的并行执行机会; 预测 + replay 机制则把正确性和性能拆开处理: 用预测获取大多数情况下的性能, 用检测和 replay 保证少数情况下仍然能回到正确的执行路径 (状态). 本文将先分析香山昆明湖 V3 中的内存预测模块 (算法), 再通过执行并分析一些测试程序的波形图来解析内存预测模块的工作流程.

## 内存依赖关系预测算法 - WaitTable

WaitTable 是一种非常简单, 非常保守的内存依赖预测算法. 它的核心思想不是精确预测 “一条 Load 应该等待哪一条 Store”, 而是预测 “一条 Load 是否属于高风险 Load”. 如果某条静态 Load 指令在历史生曾经发生过 Store-Load 顺序违例, 那么处理器就认为这条 Load 以后再次出现时仍然有较高概率越过相关 Store, 从而让它再执行前等待更老的 Store 条件满足. 反过来, 如果一条 Load 从未发生过违例, 就默认允许它继续激进的提前执行.

从算法结构看, WaitTable 通常是一张按 Load PC 索引的小表. 每个表项保存一个预测状态, 最简单的可以是 1 bit: 0 表示不需要等待, 1 表示需要等待. 处理器在前端或重命名/分派附近用 Load PC 查询 WaitTable. 如果查询到该 Load 的 wait bit 为 0, 就认为它可以像普通的 Load 一样尽早进入执行; 如果查询到 wait bit 为 1, 就给这条 Load 打上 “需要等待” 的标记, 让它在后端不要越过仍未确定的旧 Store. 等到后续执行中发现某条 Load 曾经错误越过旧 Store, 触发内存顺序违例时, 硬件会用这条 Load 的 PC 更新 WaitTable, 把对应表项置为 “以后需要等待”.

这个算法的优点是实现代价很低. 它不需要记录具体 Store PC, 也不需要维护 Load-Store 配对关系, 只需要记住 “这条 Load 过去是否危险”. 这很适合早期高频乱序处理器, 因为它的查询路径短, 状态少, 更新逻辑简单. Alpha 21264 的内存依赖处理通常就被归纳为这类思路: 对曾经发生过顺序违例的 Load 做标记, 后续遇到同一类 Load 时让它更保守的等待, 从而减少反复 replay 的代价.

但 WaitTable 的局限性也很明显. 它只知道某条 Load “可能有风险”, 却不知道它到底依赖哪条 Store. 因此, 一旦某个 Load 被标记为需要等待, 它往往需要等待较宽泛的条件, 例如等待更老的 Store 的地址解析完成, 而不是只等待真正相关的那一条 Store. 这会带来假依赖: 很多时候, 这条 Load 本次动态执行其实不和前面的 Store 指令访问相同地址, 但因为历史上发生过一次违例, 它仍然被保守阻塞. 换句话说, WaitTable 可以减少错误乱序带来的 replay, 但也会引入额外等待.

可以把 WaitTable 理解成 “负反馈式” 的预测器. 默认状态是乐观的: Load 可以提前执行; 一旦出错, 就把这条静态 Load 记下来, 让未来更谨慎. 这和分支预测里的饱和计数器有些相似: 硬件根据历史行为调整后续策略, 只不过 WaitTable 预测的不是分支方向, 而是 Load 是否应该保持等待. 实际设计中也常见 2-bit 或带老化机制的表项, 用来避免一次偶然违例永久污染预测结果. 例如第一次违例只把状态推向 “可疑”, 多次违例后才真正强制等待; 或者经过一段时间后清空表项, 让长期不再冲突的 Load 重新获得提前执行机会.

WaitTable 的本质折中是: 它比 “所有 Load 都等待旧 Store” 激进, 因为绝大多数没有历史违例的 Load 仍然可以提前执行; 它又比 “所有 Load 都自由乱序” 保守, 因为曾经出错的 Load 会被限制, 减少重复违例和流水线冲刷. 它不是最精确的内存依赖预测器, 但它抓住了一个重要经验: 许多 Store-Load 违例并不是完全随机的, 而是和特定的静态 Load 指令相关. 只要能记住这些高风险 Load, 就能以很小的硬件代价过滤掉一部分代价高昂的错误推测执行.

不过, 正因为 WaitTable 只按照 Load 记忆风险, 它无法区分 “这条 Load 这次到底应该等谁”. 这也是后续 Store Sets 等更复杂算法出现的原因. Store Sets 不再只回答 “这个 Load 要不要等”, 而是进一步尝试回答 “这个 Load 属于哪个依赖集合, 应当等待该集合中的哪些 Store”. 因此, WaitTable 可以看作内存依赖预测的基础形态: 实现起来更简单, 电路面积更低, 更具有性价比, 但是精度有限; 如果需要更高精度的内存依赖预测, 就需要使用其他的预测算法 (Store Sets).

## 内存依赖关系预测算法 - SSIT 和 LFST

Store Sets 是比 WaitTable 更精确的一类内存依赖预测算法. 它最早由 G. Z. Chrysos 和 J. S. Emer 在论文 Memory Dependence Prediction Using Store Sets 中提出, 用来解决 WaitTable 的一个核心缺陷: WaitTable 只能回答 “这条 Load 是否危险”, 却不能回答 “这条 Load 应该等待哪一类 Store”. Store Sets 的目标更进一步: 把历史上发生过内存顺序违例的 Load 和 Store 归入同一个依赖集合, 让后续同类 Load 只等待这个集合中相关的旧 Store, 而不是等待所有旧 Store.

这类算法的基本原理是: 程序中的内存依赖关系往往具有重复性 (循环, 或者是一个函数中的代码被调用多次). 某条静态 Load 如果曾经越过某条静态 Store 并发生违例, 那么它们在后续动态执行中仍然可能再次发生依赖. Store Sets 就利用这种历史相关性, 把曾经发生过冲突的 Load-PC 和 Store-PC 绑定到同一个 Store Set. 这个 Store Set 不是精确的地址集合, 而是一组 “历史上可能互相依赖的静态内存指令”. 预测时, 处理器不需要知道具体地址, 只需要知道当前 Load 属于哪个 Store Set, 以及这个 Store Set 中是否有尚未完成的旧 Store.

经典 Store Sets 结构通常包含两张表. 第一张是 SSIT (Store Set Identifier Table). 他用 Load 或 Store 的 PC (或者经过哈希折叠后的 PC) 进行索引, 记录该静态内存指令所属的 Store Set ID (SSID). 如果这条 Load 查询 SSIT 后没有命中, 说明它还没有已知的历史依赖关系, 可以按照普通方式提前乱序执行; 如果命中, 则说明它属于某个 Store Set, 需要进一步检查这个集合中是否存在未完成的旧 Store. 第二张是 LFST (Last Fetched Store Table), 以 SSID 为索引, 记录该 Store Set 中最近进入流水线, 仍可能影响后续 Load 的 Store. Load 查询到 SSID 后, 再用 SSID 查询 LFST; 如果 LFST 中存在对应 Store, Load 就应该等待该 Store, 不能盲目的乱序执行.

这样一来, Store Sets 相比 WaitTable 的关键进步在于 “等待对象更具体”. WaitTable 看到某条 Load 曾经出错, 往往只能让这条 Load 以后更保守地等待所有旧 Store 或一大类旧 Store; Store Sets 则把等待的范围缩小到 “同一个 Store Sets 中的旧 Store”. 如果某条 Load 曾经只和某几条 Store 发生过冲突, 那么它不必因为历史违例而等待所有无关 Store. 它只需要等待与自己同属于同一依赖集合的 Store. 这个机制保留了内存乱序执行的大部分性能, 同时减少了反复发生 Store-Load 违例.

Store Sets 的训练发生在 replay 或 violation 检测之后. 当处理器发现一条年轻 Load 错误越过了一条旧 Store, 并且两者访问同一地址时, 说明这对静态指令之间存在真实的历史依赖. 此时算法会用 Load PC 和 Store PC (或者对应的经过哈希折叠后的 PC) 更新 SSIT. 如果两者此前都没有 SSID, 就为他们分配一个新的 Store Set (即分配一个新的 SSID), 把出现依赖的 Load 和 Store 归入同一个集合. 如果其中一个已经属于某个 Store Set, 另一个还没有, 就把未分配的一方加入已有集合. 如果两者已经分别属于不同的 Store Set, 就需要把两个集合合并, 使他们以后共享同一个 SSID. Chrysos 和 Emer 的 Store Sets 论文强调的正是这种 “集合化” 的依赖表达: 不再单纯的记录一个 Load-Store 组合, 而是把可能相关的多个 Load/Store 组织成一个预测的集合.

LFST 则负责把静态集合关系转化为动态等待关系. SSIT 告诉我们 “这条 Load 属于哪个集合”, 但它并不知道当前流水线里这个集合有没有尚未完成的旧 Store. LFST 负责解决这个问题. 每当一个 Store 被取指, 分派, 或进入发射阶段时, 如果它在 SSIT 中有 SSID, 就用这个 SSID 更新 LFST, 表示该集合当前最近活跃的 Store 是它. 之后同一 Store Set 中的 Load 查询 LFST, 如果发现对应项有效, 就等待这个 Store. 等 Store 地址解析, 执行完成或离开需要约束 Load 的阶段后, LFST 中对应状态可以被清除或推进. 由此, SSIT 负责管理 “历史上谁和谁有关”, LFST 则负责管理 “当前动态执行中应该等待哪条指令”.

Store Sets 仍然是一种预测机制, 而不是精确依赖证明. 它可能产生假依赖: 两个指令因为历史上某次冲突被放进同一个集合, 但某次动态执行中它们访问的地址其实不通, Load 却仍然等待了 Store. 它也可能因为表项别名, 集合合并过度, SSID 数量有限而扩大等待范围. 不过, 这种假依赖通常比完全保守等待 Store 要轻得多. 与此同时, 它能显著减少真依赖被错过的情况, 因为一旦某组 Load/Store 发生过违例, 后续就会被同一个 Store Set 约束起来.

从算法思想看, Store Sets 是介于 “精确依赖预测” 和 “粗粒度等待预测” 之间的折中. 精确依赖预测试图预测某条 Load 应该等待哪一条具体 Store, 理论上更精准, 但硬件状态和更新复杂度高; WaitTable 只预测某条 Load 是否有风险, 实现简单但等待范围太粗. Store Sets 把多个相关的内存指令压缩成集合, 用 SSIT 记录静态归属, 用 LFST 记录动态最近 Store, 从而以相对有限的硬件成本表达多对多的内存依赖关系.

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

## 简单情况下的波形图分析

### 演示程序与解析

为了演示违例检测, Replay 过程, MDP (Store Set) 训练的行为, 和后续同一个 PC 的 Store-Load 执行情况, 我们编写一个简易的演示程序, 这个程序通过插入 `DEP`宏 (其实就是给一个寄存器值加一之后减一) 制造较长的依赖链. 那我们就可以把某个内存地址写入到两个寄存器中, 第一个寄存器直接可以使用 (所以分配给想要投机乱序执行的 Load), 第二个寄存器通过 DEP 宏拉出较长的依赖链, 所以需要等很多个周期之后才能就绪 (所以分配给想要稍微晚些就绪的 Store):

```c
#include <klib.h>
#include <stdint.h>

static volatile uint64_t x __attribute__((aligned(64))) = 0;

int main(void) {
    uint64_t sum;

    asm volatile(
        "li t1, 10\n"
        "li t3, 1\n"
        "li %[sum], 0\n"
        "1:\n"
        "mv t0, %[p]\n"
        #define DEP "addi t0,t0,1\naddi t0,t0,-1\n"
        DEP DEP DEP DEP DEP DEP DEP DEP
        DEP DEP DEP DEP DEP DEP DEP DEP
        DEP DEP DEP DEP DEP DEP DEP DEP
        DEP DEP DEP DEP DEP DEP DEP DEP
        #undef DEP
        "sd t3, 0(t0)\n"
        "ld t4, 0(%[p])\n"
        "add %[sum], %[sum], t4\n"
        "addi t3, t3, 1\n"
        "addi t1, t1, -1\n"
        "bnez t1, 1b\n"
        : [sum] "=&r"(sum)
        : [p] "r"(&x)
        : "t0", "t1", "t3", "t4", "memory");

    printf("mdp raw demo: x=%lu sum=%lu\n", (uint64_t)x, sum);
    return 0;
}
```

将其编译, 在香山昆明湖 V3 的 EMU 中执行该程序, 保存 FST 波形图 ([附件: demoMDP.zip](./attachments/kivYo6g6dphb9DiV/demoMDP.zip)), 可以得到反汇编代码:

```plain
000000008000012a <main>:
    8000012a:   1141                    addi    sp,sp,-16
    8000012c:   e406                    sd      ra,8(sp)
    8000012e:   00001797                auipc   a5,0x1
    80000132:   51278793                addi    a5,a5,1298 # 80001640 <x>
    80000136:   4329                    li      t1,10
    80000138:   4e05                    li      t3,1
    8000013a:   4601                    li      a2,0
    8000013c:   82be                    mv      t0,a5
    8000013e:   0285                    addi    t0,t0,1
    80000140:   12fd                    addi    t0,t0,-1
    // ... 重复的 addi 1 和 addi -1
    800001ba:   0285                    addi    t0,t0,1
    800001bc:   12fd                    addi    t0,t0,-1
    800001be:   01c2b023                sd      t3,0(t0)
    800001c2:   0007be83                ld      t4,0(a5)
    800001c6:   9676                    add     a2,a2,t4
    800001c8:   0e05                    addi    t3,t3,1
    800001ca:   137d                    addi    t1,t1,-1
    800001cc:   f60318e3                bnez    t1,8000013c <main+0x12>
    800001d0:   638c                    ld      a1,0(a5)
    800001d2:   00001517                auipc   a0,0x1
    800001d6:   17e50513                addi    a0,a0,382 # 80001350 <printf_+0x32>
    800001da:   144010ef                jal     8000131e <printf_>
    800001de:   60a2                    ld      ra,8(sp)
    800001e0:   4501                    li      a0,0
    800001e2:   0141                    addi    sp,sp,16
    800001e4:   8082                    ret
```

从上面的汇编代码中可以看出 a5 的值只想了某个内存地址, t0 复制了这个值, 并植入了很长的 DEP 依赖链. 最后的 sd 和 ld 指令操作同一个地址. 这个程序是一个有循环的程序, 所以也方便观察在发现违例后, 后续的循环中 MDP 预测器的行为.

### LoadQueueRAW 违例检测分析

本节使用开源的 `wavekit` 库解析 `2026-07-21-10-21-43.fst`。以下周期均在
`TOP.clock` 的上升沿采样；FST 中相邻周期的仿真时间相差 2。关注的第一对指令为
`0x800001be: sd t3, 0(t0)` 和 `0x800001c2: ld t4, 0(a5)`：二者最终都访问
`x = 0x80001640`，但 load 的基址 `a5` 已就绪，而 store 的 `t0` 仍被长 DEP
链阻塞，因此 load 可以先于 store 地址写回。

| 周期（仿真时间） | 波形证据 | 含义 |
| --- | --- | --- |
| 4337（8674） | `inner_LoadUnit_0.io_lqWrite_valid=1`；`ftqPtr=17`、`ftqOffset=18`、`robIdx=164`、`fullva=0x80001640`；`storeSetHit=0`、`loadWaitBit=0` | 首次 `ld t4, 0(a5)` 进入 LQ 时尚无 Store Set 预测，因而直接投机执行。`ftqOffset=18` 与下面 store 的 16 相差一个 32-bit 指令，和反汇编中 `0x1c2-0x1be=4` 字节相符。 |
| 4396（8792） | `inner_lsq.io_nuke_rollback_0_valid=1`；load 的 `robIdx=164, ftqIdx=17, ftqOffset=18`；携带 store 的 `stFtqIdx=17, stFtqOffset=16` | Store 地址就绪后，`LoadQueueRAW` 找到了这个更年轻、同地址且已取数的 load，产生 RAW 违例 redirect。`backend.io_mem_mdpTrain_valid` 在同一拍也为 1，说明同一个违例同时提供训练样本。 |

这里不是由数据缓存 miss 引起的 replay：load 的有效地址在第一次发射时已经是
`0x80001640`，违例请求中也明确携带了同一 Fetch Block 内的 store/load 身份。硬件的
判定方式正是 store 写回时对 RAW 队列中较年轻 load 做地址和 mask 匹配，再选出最老的
违例 load。源码中的注释与实现如下：

[`LoadQueueRAW.scala:234`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala#L234)

```scala
// When store writes back, it searches LoadQueue for younger load instructions
// with the same load physical address. They loaded wrong data and need re-execution.
val rollbackLqWb = Wire(Vec(StorePipelineWidth, Valid(new UopEntry)))
rollbackLqWb(w).valid := detectedRollback._1 &&
  DelayN(storeIn(w).valid && !storeIn(w).bits.tlbMiss, TotalSelectCycles)
```

[`LoadQueueRAW.scala:377`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala#L377)

```scala
redirect.valid := rollbackLqWb(i).valid
redirect.bits.robIdx := rollbackLqWb(i).bits.robIdx
redirect.bits.ftqIdx := rollbackLqWb(i).bits.ftqPtr
redirect.bits.ftqOffset := rollbackLqWb(i).bits.ftqOffset
redirect.bits.stFtqIdx := stFtqIdx(i)
redirect.bits.stFtqOffset := stFtqOffset(i)
redirect.bits.target := rollbackLqWb(i).bits.pc
```

### Replay 机制分析

这次 RAW 违例使用的是 **从违例 load 本身重新取指** 的 pipeline flush，而不是把该
load 放入 `LoadQueueReplay` 后在 RS 内局部重发。`LoadQueueRAW` 生成的 redirect 的
`robIdx` 是 load 的 ROB 项；该模块随后以 `robIdx - 1` 为 flush 边界，使 load 自身和
所有更年轻指令失效，再把 `target` 设为该 load 的 PC。因此，错误读取的旧值不会提交。

波形给出了完整的跨模块延迟链：

| 周期（仿真时间） | 有效信号 | 作用 |
| --- | --- | --- |
| 4396（8792） | `inner_lsq.io_nuke_rollback_0_valid=1` | LoadQueueRAW 输出针对 `robIdx=164`、PC `0x800001c2` 的内存违例。 |
| 4397（8794） | `backend.inner_ctrlBlock.loadReplay_valid_last_REG=1` | CtrlBlock 用寄存器接住 MemBlock 的违例请求。 |
| 4398（8796） | `backend.inner_ctrlBlock.redirectGen.io_stage2Redirect_valid=1`，`backend.io_frontend_toFtq_redirect_valid=1` | 后端仲裁得到该条 memory redirect，并将其送入前端恢复接口。 |
| 4399（8798） | `frontend.inner_ftq.io_toIfu_redirect_valid=1` | FTQ 向 IFU 发出重定向，重新从 `ld t4, 0(a5)` 取指。 |

从模块边界看，这次 replay 的流程可以拆成下面六步：

1. **Store 写回并触发查询。** 延迟地址链完成后，`sd t3, 0(t0)` 的 store pipeline 将地址写回；`LoadQueueRAW` 以该 store 的物理地址和 mask 查询已记录的、SQ 序号更年轻的 load。
2. **选择最老违例 load。** 若命中多个候选项，`detectRollback` 的分组年龄矩阵选择最老的 load。这里选中 `robIdx=164`、`(ftqIdx, ftqOffset)=(17,18)` 的 `ld t4, 0(a5)`，并同时保留触发者 store 的 `(17,16)` 身份；中间的多级选择延迟由 `TotalSelectCycles` 吸收。
3. **封装为 memory redirect。** 选中的 load 作为 redirect 的 `robIdx/ftqIdx/ftqOffset/target`，store 的 FTQ 信息放在 `stFtqIdx/stFtqOffset` 中。注释说明该类 redirect 以 `robIdx - 1` 为 flush 边界，所以违例 load 本身也会被冲刷，不能把已经读到的旧值继续写回或提交。
4. **CtrlBlock 还原准确 PC 并参与仲裁。** MemBlock 上报的 redirect 只有 FTQ 身份；CtrlBlock 用 `pcMem[ftqIdx] + getPcOffset()` 恢复 load 的真实 PC，随后将其送入 `RedirectGenerator`。本例波形中，4397 拍的 `loadReplay` 是这一拍寄存后的请求，4398 拍才成为被仲裁的 stage-2 redirect。
5. **FTQ/IFU 从 load PC 恢复。** 后端将仲裁结果送给 FTQ；4399 拍 `io_toIfu_redirect_valid=1`，IFU 从 `0x800001c2` 重取。因而 load 及其所有更年轻指令会重新经历 fetch、decode、rename、dispatch 和 issue；更老的 store 保留，避免错误地回滚已确定的程序顺序状态。
6. **重新执行时读取正确数据。** 重取后的 load 必须等到该 store 地址已经可见；本例中 MDP 同时完成训练，后续同一 PC 的 load 被赋予 `loadWaitBit=1`，从源头避免再次发生“load 先读、store 后到”的 RAW 违例。

`LoadQueueRAW` 把选中 load 的身份和触发 store 的身份组装为 redirect 的逻辑如下：

[`LoadQueueRAW.scala:353`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala#L353)

```scala
rollbackLqWb(w).valid := detectedRollback._1 &&
  DelayN(storeIn(w).valid && !storeIn(w).bits.tlbMiss, TotalSelectCycles)

redirect.bits.robIdx      := rollbackLqWb(i).bits.robIdx
redirect.bits.ftqIdx      := rollbackLqWb(i).bits.ftqPtr
redirect.bits.ftqOffset   := rollbackLqWb(i).bits.ftqOffset
redirect.bits.stFtqIdx    := stFtqIdx(i)
redirect.bits.stFtqOffset := stFtqOffset(i)
redirect.bits.target      := rollbackLqWb(i).bits.pc
```

CtrlBlock 对该 redirect 重建 PC 并将它送入前端的关键连接如下：

[`CtrlBlock.scala:324`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L324)

```scala
redirectGen.io.loadReplay <> loadReplay
loadRedirectTargetOffset := Mux(memViolation.bits.flushItself(), thisPcOffset, nextPcOffset)
val load_target = loadRedirectStartPcRead + loadRedirectTargetOffset
redirectGen.io.loadReplay.bits.target := load_target

io.frontend.toFtq.redirect.valid := s5_flushFromRobValid || s3_redirectGen.valid
io.frontend.toFtq.redirect.bits := Mux(s5_flushFromRobValid, frontendFlushBits, s3_redirectGen.bits)
```

这四拍也解释了为什么 `mdpTrain` 不能被误认为一次额外的 redirect：训练信号在
4396 拍和 RAW redirect 同时产生，而真正恢复前端的是 `violation -> loadReplay ->
redirectGen -> FTQ` 这条链。CtrlBlock 对 memory violation 的寄存和标记逻辑如下：

[`CtrlBlock.scala:208`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L208)

```scala
val loadReplay = Wire(ValidIO(new Redirect))
loadReplay.valid := GatedValidRegNext(memViolation.valid)
loadReplay.bits := RegEnable(memViolation.bits, memViolation.valid)
loadReplay.bits.debugIsCtrl := false.B
loadReplay.bits.debugIsMemVio := true.B
```

这一恢复会清除该 load 之后的投机工作，代价至少包括上述 3 个后端/前端传递周期以及
重新取指、rename、调度的气泡；也正是 MDP 值得训练的原因。

### MDP 训练行为分析

`LoadQueueRAW` 不只把最老违例 load 送往 redirect 仲裁，同时把同一条 redirect 送到
`mdpTrain`：`io.mdpTrain := Mux1H(oldestOH, allRedirect)`。因此，训练样本的 load 和
store 身份与实际导致 flush 的那一对完全一致，而不是由提交阶段猜测得到。

第一次违例的训练流水如下；`ldpc/stpc` 是 CtrlBlock 用 FTQ 起始 PC 与 offset 还原
静态 PC 后，再做 `XORFold` 得到的 10 位 SSIT 索引，而不是原始 PC：

| 周期（仿真时间） | 波形证据 | 训练阶段 |
| --- | --- | --- |
| 4396（8792） | `backend.io_mem_mdpTrain_valid=1`；load `(ftqIdx, offset)=(17,18)`，store `(17,16)` | 收到真实 RAW 违例样本。 |
| 4397（8794） | `memCtrl_io_memPredUpdate_valid_REG=1` | CtrlBlock 已完成 FTQ PC 存储体读请求，向 MemCtrl 提交 update。 |
| 4399（8798） | `ssit.s1_mempred_update_req_valid=1`，`ldpc=224, stpc=222` | SSIT 的读阶段取得 load/store 旧表项。 |
| 4400（8800） | `ssit.s2_mempred_update_req_valid=1`，`ldpc=224, stpc=222` | SSIT 写阶段；之后 `io_ssit2Rename_*_valid=1, ssid=29, strict=0`。 |

在这次首次训练前，两端都没有匹配表项，因此命中 `Cat(loadAssigned,
storeAssigned) == "b00"` 的分支：为 store 和 load 分配同一个 SSID。波形中最后可见的
`SSID=29` 是该配置下 `XORFold`/分配逻辑得到的 Store Set 号；`strict=0` 表示普通的
Store Set 等待，而不是 strict wait。

[`CtrlBlock.scala:217`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L217)

```scala
val mdpTrainValid = io.fromMem.mdpTrain.valid
memCtrl.io.memPredUpdate.ldpc := XORFold(
  (pcMem.io.rdata(pcMemIdx).toUInt + offset)(VAddrBits - 1, 1), MemPredPCWidth)
memCtrl.io.memPredUpdate.stpc := XORFold(
  (pcMem.io.rdata(pcMemIdx).toUInt + offset)(VAddrBits - 1, 1), MemPredPCWidth)
memCtrl.io.memPredUpdate.valid := RegNext(mdpTrainValid)
```

[`StoreSet.scala:246`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala#L246)

```scala
when(s2_mempred_update_req_valid){
  switch (Cat(s2_loadAssigned, s2_storeAssigned)) {
    is ("b00".U(2.W)) {
      update_ld_ssit_entry(pc = s2_mempred_update_req.ldpc,
        valid = true.B, ssid = s2_allocSsid, strict = false.B)
      update_st_ssit_entry(pc = s2_mempred_update_req.stpc,
        valid = true.B, ssid = s2_allocSsid, strict = false.B)
    }
  }
}
```

波形中还在 4680（9360）观察到第二次 RAW redirect，但它的训练索引为
`ldpc=233, stpc=222`，不同于循环内 `ld t4, 0(a5)` 的 `ldpc=224`。它对应循环退出后
`0x800001d0: ld a1, 0(a5)` 与同一 store 的新 load/store 对，不能算作循环内目标 load
训练失败；这也说明 SSIT 以静态 load PC 区分不同依赖对。

### 后续同 Load 指令执行分析

第一次训练完成后，后续重新取到的同一静态 `ld t4, 0(a5)` 不再以“无依赖”的方式直接
发射。波形中所有 `ftqOffset=18` 且 `fullva=0x80001640` 的循环内 load，在 4430（8860）
起均携带：

```plain
storeSetHit = 1
loadWaitBit = 1
loadWaitStrict = 0
ssid = 29
waitForRobIdx = 本轮更老 sd 的 ROB index
```

例如，4430 拍的 load 为 `robIdx=235`、`waitForRobIdx=234`；4445 拍为
`robIdx=306`、`waitForRobIdx=305`；随后 4476、4498、4522、4547、4573、4601、4625、
4658、4681 拍仍可看到同样的 `SSID=29` 和相邻的 store ROB index。也就是说，预测器没有
把 load 粗暴地完全串行化，而是让它等待 **LFST 中同一 Store Set 的最近未完成 store**。
在 store 地址发射后 LFST 清除该 store，load 才可进入 LoadUnit；因此长 DEP 链的等待被
显式归因到预测到的 store，而不是再次依赖“先读、再由 RAW 检查兜底”。

从 4400 的首次 SSIT 写入到循环内后续各次 `ftqOffset=18` load 的执行窗口，FST 没有再
出现以该静态 load（folded `ldpc=224`）为目标的 `LoadQueueRAW` rollback；唯一后续
rollback 是上节说明的 `0x800001d0` 新 load。这与 `storeSetHit/loadWaitBit` 的变化相互
印证：MDP 已把循环内的 store-load 依赖从“违例后恢复”转为“发射前等待”。

Rename 先把 SSIT 结果写入 micro-op，Dispatch 再通过 LFST 给出真正应等待的最近 store：

[`Rename.scala:453`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala#L453)

```scala
uops(i).storeSetHit := io.ssit(i).valid
uops(i).loadWaitStrict := io.ssit(i).strict && io.ssit(i).valid
uops(i).ssid := io.ssit(i).ssid
uops(i).loadWaitBit := io.waittable(i)
```

[`Dispatch.scala:760`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala#L760)

```scala
io.lfst.req(i).bits.ssid := updatedUop(i).ssid
io.lfst.req(i).bits.robIdx := updatedUop(i).robIdx
fromRenameUpdate(i).bits.loadWaitBit := io.lfst.resp(i).bits.shouldWait
fromRenameUpdate(i).bits.waitForRobIdx := io.lfst.resp(i).bits.robIdx
```

## 复杂情况下的波形图分析

### 复杂演示程序解析

为覆盖 SSIT 更新状态机的四个分支，在 Kunminghu V3 环境中新增了
[`mdp-ssit-complex/main.c`](/home/yanyusong/mdp-kmhv3/nexus-am/tests/mdp-ssit-complex/main.c)。测试使用三个独立的静态 store（`S0`、`S1`、`S2`）和三个独立的静态 load（`L0`、`L1`、`L2`）；每个 store 都通过 32 组 `addi +1/-1` 形成地址依赖链，load 的地址则直接可用。store 函数返回后，处理器可以沿 RAS 预测继续进入 load 函数，因此 load 有机会在该 store 地址就绪前投机执行；每个 pair 后的 `fence rw,rw` 只用于隔离相邻测试 pair，不会撤销已经产生的 RAW 违例训练。

测试按以下顺序执行，其中括号中为该 pair 希望触发的 SSIT 更新分支：

```c
run_pair(store_s0, load_l0, 1); // b00：L0、S0 均未分配
run_pair(store_s1, load_l1, 2); // b00：建立第二个独立 Store Set
run_pair(store_s2, load_l0, 3); // b10：L0 已分配，S2 未分配
run_pair(store_s1, load_l2, 4); // b01：L2 未分配，S1 已分配
run_pair(store_s1, load_l0, 5); // b11：L0、S1 均已分配，合并两个 set
run_pair(store_s0, load_l2, 6); // b11：再次观察两个已分配 set 的合并
```

#### 测试程序关键代码

下面是测试程序中实际编译的 C 代码。`DELAYED_STORE` 中的 `DEP_CHAIN` 使 `t0` 在
`sd` 之前经历 32 组相互依赖的加一/减一；`SPECULATIVE_LOAD` 则只有一条直接使用
`a0` 的 load。三个宏展开实例分别保留不同的静态 PC，这一点是构造 SSIT 不同表项的关键。

[`mdp-ssit-complex/main.c`](/home/yanyusong/mdp-kmhv3/nexus-am/tests/mdp-ssit-complex/main.c)

```c
static volatile uint64_t shared_word __attribute__((aligned(64)));
static volatile uint64_t observed_sum;

#define DEP_CHAIN \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n"

#define DELAYED_STORE(name) \
  static __attribute__((noinline)) void name(volatile uint64_t *address, uint64_t value) { \
    asm volatile( \
      "mv t0, %[address]\n" DEP_CHAIN "sd %[value], 0(t0)\n" \
      : : [address] "r"(address), [value] "r"(value) : "t0", "memory"); \
  }

#define SPECULATIVE_LOAD(name) \
  static __attribute__((noinline)) uint64_t name(volatile uint64_t *address) { \
    uint64_t value; \
    asm volatile("ld %[value], 0(%[address])\n" \
      : [value] "=&r"(value) : [address] "r"(address) : "memory"); \
    return value; \
  }

DELAYED_STORE(store_s0)
DELAYED_STORE(store_s1)
DELAYED_STORE(store_s2)
SPECULATIVE_LOAD(load_l0)
SPECULATIVE_LOAD(load_l1)
SPECULATIVE_LOAD(load_l2)

static __attribute__((noinline)) uint64_t run_pair(
    void (*store)(volatile uint64_t *, uint64_t),
    uint64_t (*load)(volatile uint64_t *), uint64_t value) {
  store(&shared_word, value);
  uint64_t observed = load(&shared_word);
  asm volatile("fence rw, rw" ::: "memory");
  return observed;
}

int main(void) {
  shared_word = 0;
  observed_sum += run_pair(store_s0, load_l0, 1);
  observed_sum += run_pair(store_s1, load_l1, 2);
  observed_sum += run_pair(store_s2, load_l0, 3);
  observed_sum += run_pair(store_s1, load_l2, 4);
  observed_sum += run_pair(store_s1, load_l0, 5);
  observed_sum += run_pair(store_s0, load_l2, 6);
  printf("mdp ssit complex: final=%lu sum=%lu\n", shared_word, observed_sum);
  return shared_word == 6 && observed_sum == 21 ? 0 : 1;
}
```

#### 相关反汇编

二进制 `mdp-ssit-complex-riscv64-xs.elf` 使用
`riscv64-linux-gnu-objdump -d -M no-aliases` 得到下面的相关部分。`store_s0`、
`store_s1`、`store_s2` 的 DEP 指令序列仅起始地址不同，且每段的末尾分别在
`0x8000016c`、`0x800001b4`、`0x800001fc` 执行 `sd`；中间重复的 32 组压缩
`c.addi` 在表中以省略号表示。反汇编中的 `c.jalr a5`、`c.jalr s0` 对应 `run_pair`
对 store/load 函数指针的两次间接调用，随后 `fence rw,rw` 隔离下一组测试。

```plain
000000008000012a <store_s0>:
    8000012a:  82aa        c.mv    t0,a0
    8000012c:  0285        c.addi  t0,1
    8000012e:  12fd        c.addi  t0,-1
    ...                     # 继续执行 31 组 c.addi +1/-1
    8000016c:  00b2b023    sd      a1,0(t0)       # S0
    80000170:  8082        c.jr    ra

0000000080000172 <store_s1>:
    80000172:  82aa        c.mv    t0,a0
    ...
    800001b4:  00b2b023    sd      a1,0(t0)       # S1
    800001b8:  8082        c.jr    ra

00000000800001ba <store_s2>:
    800001ba:  82aa        c.mv    t0,a0
    ...
    800001fc:  00b2b023    sd      a1,0(t0)       # S2
    80000200:  8082        c.jr    ra

0000000080000202 <load_l0>:
    80000202:  611c        c.ld    a5,0(a0)       # L0
    80000204:  853e        c.mv    a0,a5
    80000206:  8082        c.jr    ra

0000000080000208 <load_l1>:
    80000208:  611c        c.ld    a5,0(a0)       # L1
    8000020a:  853e        c.mv    a0,a5
    8000020c:  8082        c.jr    ra

000000008000020e <load_l2>:
    8000020e:  611c        c.ld    a5,0(a0)       # L2
    80000210:  853e        c.mv    a0,a5
    80000212:  8082        c.jr    ra

0000000080000214 <run_pair>:
    80000228:  9782        c.jalr  a5             # 调用 store
    80000232:  9402        c.jalr  s0             # 调用 load
    80000234:  0330000f    fence   rw,rw
    8000023e:  8082        c.jr    ra
```

`main` 将函数地址传给 `run_pair`。下面的地址装载序列可以直接验证 C 源码所列的
`S0/L0 → S1/L1 → S2/L0 → S1/L2 → S1/L0 → S0/L2` 顺序，而不是六个临时复制的
load/store 指令：

```plain
80000248:  ... # a1 = 0x80000202 <load_l0>
80000250:  ... # a0 = 0x8000012a <store_s0>
8000026a:  jal ra,80000214 <run_pair>
80000276:  ... # a1 = 0x80000208 <load_l1>
80000280:  ... # a0 = 0x80000172 <store_s1>
80000286:  jal ra,80000214 <run_pair>
80000292:  ... # a1 = 0x80000202 <load_l0>
8000029c:  ... # a0 = 0x800001ba <store_s2>
800002a2:  jal ra,80000214 <run_pair>
800002ae:  ... # a1 = 0x8000020e <load_l2>
800002b8:  ... # a0 = 0x80000172 <store_s1>
800002be:  jal ra,80000214 <run_pair>
800002ca:  ... # a1 = 0x80000202 <load_l0>
800002d4:  ... # a0 = 0x80000172 <store_s1>
800002da:  jal ra,80000214 <run_pair>
800002e6:  ... # a1 = 0x8000020e <load_l2>
800002f0:  ... # a0 = 0x8000012a <store_s0>
800002f6:  jal ra,80000214 <run_pair>
```

测试使用下面的命令编译和运行；仿真使用 `--dump-wave-full`，**没有**设置
`--max-cycles/-C`，因此 FST 覆盖从复位到 `HIT GOOD TRAP` 的全部 9764 个周期：

```bash
cd ~/mdp-kmhv3/nexus-am/tests/mdp-ssit-complex
source ~/mdp-kmhv3/env.sh
make ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=1 -j2

~/mdp-kmhv3/XiangShan/build/emu \
  -i build/mdp-ssit-complex-riscv64-xs.bin \
  --no-diff --dump-wave-full \
  --wave-path=~/mdp-kmhv3/XiangShan/build/mdp-ssit-complex.fst \
  --force-dump-result
```

仿真输出 `mdp ssit complex: final=6 sum=21` 并在 `PC=0x8000033e` 进入 `HIT GOOD TRAP`。
本节使用开源 `wavekit` 解析生成的
`/home/yanyusong/mdp-kmhv3/XiangShan/build/mdp-ssit-complex.fst`，仍以 `TOP.clock`
上升沿采样。反汇编确认各静态内存指令 PC 如下：

| 指令 | 静态 PC | FST 中的 10 位 folded PC |
| --- | --- | --- |
| `S0: sd a1, 0(t0)` | `0x8000016c` | 183 |
| `S1: sd a1, 0(t0)` | `0x800001b4` | 219 |
| `S2: sd a1, 0(t0)` | `0x800001fc` | 255 |
| `L0: ld a5, 0(a0)` | `0x80000202` | 256 |
| `L1: ld a5, 0(a0)` | `0x80000208` | 261 |
| `L2: ld a5, 0(a0)` | `0x8000020e` | 262 |

`ldpc/stpc` 是 CtrlBlock 从 FTQ 身份恢复 PC 后做 `XORFold` 的结果，不能直接把 256、219 等值当作完整虚拟地址。对于每个真实 RAW 违例，`backend.io_mem_mdpTrain_valid` 在 redirect 产生拍有效，四拍后 SSIT 的 `s2_mempred_update_req_valid` 有效并携带更新前的 `loadAssigned/storeAssigned` 与旧 SSID。下面的总表是后续四个小节的共同波形依据：

| `mdpTrain` 周期（时间） | SSIT s2 周期（时间） | `ldpc/stpc` | `loadAssigned/storeAssigned` | 旧 `loadSSID/storeSSID` | 覆盖分支 |
| --- | --- | --- | --- | --- | --- |
| 4407（8814） | 4411（8822） | 256 / 183（L0/S0） | 0 / 0 | 无 / 无 | b00 |
| 4584（9168） | 4588（9176） | 261 / 219（L1/S1） | 0 / 0 | 无 / 无 | b00 |
| 4709（9418） | 4713（9426） | 256 / 255（L0/S2） | 1 / 0 | 4 / 无 | b10 |
| 4832（9664） | 4836（9672） | 262 / 219（L2/S1） | 0 / 1 | 无 / 1 | b01 |
| 4943（9886） | 4947（9894） | 256 / 219（L0/S1） | 1 / 1 | 4 / 1 | b11，合并 |
| 5066（10132） | 5070（10140） | 262 / 183（L2/S0） | 1 / 1 | 24 / 4 | b11，合并 |

SSIT 的四个更新分支均在同一段源码中实现：

[`StoreSet.scala:246`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala#L246)

```scala
switch (Cat(s2_loadAssigned, s2_storeAssigned)) {
  is ("b00".U(2.W)) { /* allocate and assign both entries */ }
  is ("b10".U(2.W)) { /* assign a store-set ID to the store */ }
  is ("b01".U(2.W)) { /* assign a store-set ID to the load */ }
  is ("b11".U(2.W)) { /* choose the winner and rewrite both entries */ }
}
```

### SSIT 四种情况下的更新流程分析

#### 情况 (一): 违例的 Load 和 Store 均不在 Store Set 中

这一情况对应 `Cat(loadAssigned, storeAssigned) == b00`。波形中有两次独立观测，用于先建立两个不同的 Store Set：

* 4411（8822）更新 `L0/S0`：`loadAssigned=0`、`storeAssigned=0`，随后 L0 与 S0 获得同一组 SSID；后续 4713 拍再次读取 L0 时，其旧 SSID 为 4，证明第一组已经写入并可被查询。
* 4588（9176）更新 `L1/S1`：同样为 `0/0`。后续 4836 拍读取 S1 时 `storeAssigned=1`、旧 SSID 为 1，证明第二组独立表项已经建立。

硬件并不为每次 b00 简单使用一个全局递增编号，而是先对 load/store 的 folded PC 分别做
`XORFold(..., SSIDWidth)`，再选择较小者作为 `s2_allocSsid`；所以两次 b00 在本波形中形成了可区分的 Store Set（后续可见的 4 与 1）。这正是后续 b11 能观察到“两个已分配但不同 ID 的 set 合并”的前提。

[`StoreSet.scala:198`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala#L198)

```scala
val s2_ldSsidAllocate = XORFold(s2_mempred_update_req.ldpc, SSIDWidth)
val s2_stSsidAllocate = XORFold(s2_mempred_update_req.stpc, SSIDWidth)
val s2_allocSsid = Mux(s2_ldSsidAllocate < s2_stSsidAllocate,
  s2_ldSsidAllocate, s2_stSsidAllocate)

is ("b00".U(2.W)) {
  update_ld_ssit_entry(..., ssid = s2_allocSsid, strict = false.B)
  update_st_ssit_entry(..., ssid = s2_allocSsid, strict = false.B)
}
```

#### 情况 (二): 违例的 Load 在 Store Set 中, Store 不在 Store Set 中

第三个 pair 为 `S2 -> L0`。L0 已在第一组 b00 中出现，而 S2 是新的静态 store PC；在
4713（9426）的 SSIT s2 拍，波形为 `ldpc=256`、`stpc=255`、`loadAssigned=1`、
`storeAssigned=0`、`loadOldSSID=4`，精确命中 b10 分支。

该分支不改变 load 的既有归属，而是将新 store 写入 load 的 set。因此下一次遇到 S2 时，
它会被视为 L0 所属依赖集合中的候选 producer；这是 Store Set 从“观察到一次依赖”扩展到“同一 load 的多个可能 store”的过程。

[`StoreSet.scala:264`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala#L264)

```scala
is ("b10".U(2.W)) {
  update_st_ssit_entry(
    pc = s2_mempred_update_req.stpc,
    valid = true.B,
    ssid = s2_ldSsidAllocate,
    strict = false.B
  )
}
```

#### 情况 (三): 违例的 Load 不在 Store Set 中, Load 在 Store Set 中

第四个 pair 为 `S1 -> L2`。S1 已由第二次 b00 建立，而 L2 是新的静态 load PC。4836（9672）的波形显示 `ldpc=262`、`stpc=219`、`loadAssigned=0`、`storeAssigned=1`、`storeOldSSID=1`，因此命中 b01 分支。

一个值得注意的源码细节是：此实现给新 load 写入的是 `s2_stSsidAllocate`（由 store PC 计算的分配 ID），而不是直接复制波形中的 `s2_storeOldSSID`。本例中 `S1` 的旧 SSID 是 1，而 L2 在下一次 b11 查询时显示旧 SSID 为 24；这正是后面 `L2/S0` 能形成两个不同 Store Set 并再次触发合并的原因。换言之，波形不仅证明 b01 被执行，也揭示了该实现的“按 store PC 再折叠分配”语义。

[`StoreSet.scala:274`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala#L274)

```scala
is ("b01".U(2.W)) {
  update_ld_ssit_entry(
    pc = s2_mempred_update_req.ldpc,
    valid = true.B,
    ssid = s2_stSsidAllocate,
    strict = false.B
  )
}
```

#### 情况 (四): 违例的 Load 和 Store 均在 Store Set 中

第五个 pair `S1 -> L0` 在 4947（9894）触发 b11：`loadAssigned=1`、
`storeAssigned=1`，但旧 SSID 分别为 4 与 1，`s2_ssidIsSame=0`。这不是重复写入同一
表项，而是一次真正的 Store Set 合并：源码比较两个旧 SSID，选择较小的 winner，并把
L0、S1 都重写为 winner。本例 winner 为 1。

第六个 pair `S0 -> L2` 在 5070（10140）再次命中 b11，旧 SSID 为 24 与 4，
`s2_ssidIsSame=0`，因此又执行一次合并，winner 为 4。两次 b11 都不是 same-set 的
strict 升级路径；后者需要 `s2_ssidIsSame=1`，本完整 FST 中针对这两个测试 pair 没有
观察到该条件为 1。这里的结论应明确为：测试覆盖了 b11 的 **不同 Store Set 合并** 子路径，
而非“已同 set 后再违例”的 strict 子路径。

[`StoreSet.scala:284`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala#L284)

```scala
is ("b11".U(2.W)) {
  update_ld_ssit_entry(..., ssid = s2_winnerSSID, strict = false.B)
  update_st_ssit_entry(..., ssid = s2_winnerSSID, strict = false.B)
  when(s2_ssidIsSame){
    data_array.io.wdata(SSIT_UPDATE_LOAD_READ_PORT).strict := true.B
    debug_strict(s2_mempred_update_req.ldpc) := true.B
  }
}
```

从性能角度看，b00/b10/b01 都是在扩大预测器已知的依赖关系，b11 则把两个原本独立的依赖图连通。合并后，LFST 会把同一 SSID 的最近未完成 store 暴露给 dispatch；这降低 RAW replay 的概率，但也可能让更多 load 因 `loadWaitBit` 等待无关但被合并到同一 Store Set 的 store。因此，SSIT 的准确性直接决定了“避免 replay”与“过度串行化”之间的平衡。


> 更新: 2026-07-28 09:32:32  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/huxv0oxbmiv2svqa>
