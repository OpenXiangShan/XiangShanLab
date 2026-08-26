# 香山昆明湖执行 PREFETCH_W 指令的流程分析

## PREFETCH_W 指令介绍

### 这条指令是什么

`PREFETCH.W`（汇编格式为 `prefetch.w offset(base)`）是 RISC-V **Zicbop**（Cache-Block Prefetch Operations）扩展中的一条数据缓存预取提示指令。它告诉硬件：软件预计后续会对包含目标有效地址的缓存块执行**写访问**，因此可以提前为这个缓存块准备数据缓存层次中的状态。

它的有效地址为：

```text
EA = x[rs1] + sign_extend(imm[11:0])
```

其中 `rs1` 是汇编里的 `base`，立即数字段给出 12 bit 偏移。虽然写法类似 load/store 的地址操作数，但它不是普通 store：指令本身不携带 store data，不修改内存，也不会向通用寄存器写回结果。`PREFETCH.W` 的 “W” 表示 **prefetch with intent to write**，即“带写意图的预取”。

```asm
# 提示硬件：后续很可能写入 a3 + 32 所在的缓存块。
prefetch.w 32(a3)
```

在本文的演示程序中，目标指令编码为 `0x0236e013`，反汇编中显示为 `.word 0x0236e013`，对应语义是 `PREFETCH.W 32(a3)`。`objdump` 没有显示助记符不影响昆明湖后端识别它；昆明湖 Decode 会按软件预取编码把它归类为 `prefetch_w`。

### 这条指令会做什么

从软件视角，执行 `prefetch.w offset(base)` 可以理解为：

1. 计算 `base + offset` 得到目标有效地址；
2. 以目标地址所在的 cache block 为粒度，向硬件发出“之后可能写这个缓存块”的 hint；
3. 程序继续向后执行，不等待该缓存块一定完成填充或获得某种缓存权限；
4. 硬件可以选择发起缓存访问、合并已有 miss、提前获取更适合写入的缓存状态，也可以在资源不足、地址已命中或实现策略不支持时忽略该提示。

因此，`PREFETCH.W` 只影响微架构的性能路径，不改变程序的架构状态。它不会把目标地址的数据读到寄存器，也不会像 `sw/sd` 那样把寄存器中的值写入内存。后续真正改变内存内容的仍然必须是普通 store 指令。

在昆明湖实现中，这条指令会作为 LSU 软件预取 uop 进入后端，由 LoadUnit 计算地址并向 DCache 发出 `M_PFW` 命令；DCache/MissQueue 再根据“软件预取来源 + 写意图命令”处理它。完整代码路径见后面的“香山昆明湖源代码分析”章节。

### 这条指令对程序执行有什么帮助

`PREFETCH.W` 的价值在于提前暴露未来写访问的地址，让处理器把地址翻译、cache tag 查询、miss 分配、下级缓存访问等工作尽量和当前独立指令重叠。如果后续 store 到来时目标缓存块已经在更近的缓存层次中，或者已经具备更适合写入的状态，那么普通 store 看到的等待时间就可能下降。

典型适用场景包括：

| 场景 | 预取对象 | 为什么可能有效 |
|---|---|---|
| 分块写回 | 下一块即将写入的输出数组 cache line | 当前块计算期间可以提前准备下一块的写目标。|
| 顺序初始化 / 清零 | 后续循环将连续写入的数据块 | 写地址规则稳定，软件很早就知道未来写入位置。|
| 生产者写入缓冲区 | 即将填充的 ring buffer 或队列 slot | 在真正 store 数据前提前触发缓存层次动作。|
| copy / transform 输出端 | 即将写入的 destination cache line | 源数据读取和计算可以掩盖输出端写 miss 延迟。|

收益并不保证存在。预取距离太短时，请求可能来不及完成；距离太长时，目标 cache line 可能被替换；对不会实际写入的数据预取会浪费带宽和 MissQueue 等资源，还可能污染缓存。因此它适合地址模式可预测、后续确实会写、且中间有足够独立工作的场景。

本文演示程序采用的正是“先提示、后使用”的最小场景：先进行普通数据访存和 `printf`，然后发出 `PREFETCH.W 32(a3)`，再对同一 64 B cache line 中的目标地址执行普通 store/load 校验。这样可以清楚地区分预取 hint 和后续真实写访问，同时避免把预取误解成会直接修改内存的指令。

## 香山昆明湖源代码分析

本节只依据昆明湖实现的 Chisel 源代码，解释 `PREFETCH.W` 从后端译码到退休的代码路径。这里的“预取”不是一条产生整数结果的普通 Load，也不是需要存储数据的普通 Store；它在后端以一个无目的寄存器的软件预取 uop 进入内存执行通路，由 LoadUnit 计算地址并向 DCache 发出带写意图的预取请求。

### 1. Decode：由编码识别 `PREFETCH.W`

昆明湖在 `DecodeUnit` 中先判断这类指令是否属于软件预取。识别条件包括：I-type opcode、`funct3=110`、`rd=0`，随后用 `rs2` 区分预取类型：

```scala
val isSoftPrefetch = inst.OPCODE === BitPat("b0010011") &&
  inst.FUNCT3 === BitPat("b110") && inst.RD === 0.U
val isPreW = isSoftPrefetch && inst.RS2 === 3.U(5.W)
```

其中 `rd=0` 是重要约束：该指令没有架构目的寄存器。`rs1` 仍是地址基址寄存器，立即数字段提供地址偏移；`rs2=3` 只参与指令类别识别，并不是一个需要读出的普通源操作数。[`DecodeUnit.scala:1102`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L1102 )

识别完成后，译码结果把 `isPreW` 映射为内存功能单元类型和 `LSUOpType.prefetch_w`：

```scala
when (isPreW) {
  fuType := FuType.lsu
  fuOpType := LSUOpType.prefetch_w
}
```

因此 Decode 输出的核心信息不是“执行一个普通整数 ALU 操作”，而是：这是 LSU 操作、操作码为 `prefetch_w`、不写回 GPR，并携带基址源寄存器和立即数。这个 `fuType/fuOpType` 会随 uop 传递给 Rename、Dispatch 和后续内存执行逻辑。[`DecodeUnit.scala:1166`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L1166 )

### 2. Rename：保留地址源，不分配整数目的寄存器

Rename 根据 Decode 生成的源类型，从逻辑寄存器映射表中选择物理源寄存器。对 `PREFETCH.W` 而言，`rs1` 对应的 `a3` 会正常映射为物理源；`rs2` 的编码用途是 Decode 分类，不会使该指令变成普通的两个源操作数。

```scala
val psrc = MuxLookup(srcType, 0.U, Seq(
  SrcType.reg -> mapTable(...),
  SrcType.fp  -> fpMapTable(...),
  SrcType.vec -> vecMapTable(...)
))
```

Rename 对每个目的类型检查 `needIntDest`、`needFpDest` 和 `needVecDest`。只有需要目的寄存器的 uop 才分配新的 `pdest`；软件预取没有普通整数目的，因此 `pdest` 保持无效/零值，不进入整数写回分配路径。[`Rename.scala:384`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala#L384 ) [`Rename.scala:402`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala#L402 )

Rename 仍会为该 uop 分配 ROB 序号并输出完整的重命名 uop。这样做的原因是预取虽然没有架构寄存器结果，却仍然要遵守程序顺序、异常/重定向规则和退休规则。Rename 文件中与软件预取相关的旧代码位于注释区域，当前有效路径依赖 Decode 已经设置好的 `fuType/fuOpType`，不能把注释代码当成运行时分支。[`Rename.scala:414`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala#L414 ) [`Rename.scala:426`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala#L426 )

### 3. Dispatch：进入内存调度器、ROB 与 LDU IssueQueue

控制块把 Rename 的输出通过 `PipeGroupConnect` 连接到 Dispatch：

```scala
renameOut(i) <> dispatch.io.fromRename(i)
```

Dispatch 同时发起两类动作：一方面把 uop 送到 ROB enqueue，建立该指令的顺序状态；另一方面按照 `fuType` 选择对应的 issue queue。ROB enqueue 的有效条件还会排除 redirect：

```scala
sink.valid := RegNext(source.valid && !rob.io.redirect.valid)
rob.io.enq.req := enqRob.req
```

因此，`PREFETCH.W` 与普通 LSU uop 一样拥有 ROB 项，但不会因为没有目的寄存器而绕过 ROB。[`CtrlBlock.scala:694`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L694 ) [`CtrlBlock.scala:707`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L707 )

Dispatch 根据 `fuType` 生成 one-hot 选择，并把内存 uop 送入 `MemScheduler` 管理的内存 IssueQueue。代码使用 `backendParams.allIssueParams` 枚举各 issue queue，再通过 `fuTypeOH` 和 `uopSelIQ` 选择目标队列；`PREFETCH.W` 的 `fuType=lsu`，所以它进入 LDU 侧的内存队列，而不是整数、浮点或向量队列。[`NewDispatch.scala:163`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L163 ) [`NewDispatch.scala:398`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L398 )

### 4. IssueQueue 与 DataPath：以内存 uop 方式选择执行

`MemScheduler` 是专门的内存调度器类型：

```scala
case class MemScheduler() extends SchedulerType
```

`PREFETCH.W` 在内存 IssueQueue 中等待源操作数 ready 和执行端 ready。被选择后，uop 从内存 IQ 进入 DataPath 的 memory Exu 接口，再连接到 MemBlock 中的 LoadUnit。DataPath 明确区分 `fromMemIQ`、`toMemIQ` 和 `toMemExu`，说明这条路径不是普通整数执行端的旁路，而是内存执行专用路径。[`Scheduler.scala:25`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/issue/Scheduler.scala#L25 ) [`DataPath.scala:44`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/datapath/DataPath.scala#L44 )

Backend 再把 DataPath 的内存输出连接到 LoadUnit/内存执行单元和旁路网络。对 `PREFETCH.W` 来说，旁路网络不会产生可供整数消费者使用的寄存器结果；它只需要完成地址计算、访存请求及与 ROB/LQ 状态相关的反馈。[`DataPath.scala:892`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/datapath/DataPath.scala#L892 ) [`Backend.scala:513`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/Backend.scala#L513 )

### 5. LoadUnit：软件预取复用整数 Load 的首次发射通路

昆明湖没有把 `PREFETCH.W` 当作需要 store data 的普通 Store 来执行。LoadUnit 的源选择表把 `io.ldin` 明确标为“integer read / software prefetch first issue”：该输入既接收整数 Load 的第一次发射，也接收软件预取的第一次发射。

```scala
// src 8: int read / software prefetch first issue from RS (io.in)
io.ldin.valid       // int flow first issue or software prefetch
```

这意味着 `PREFETCH.W` 先进入 LoadUnit 的地址生成、地址翻译和 DCache load port，而不是走普通 StoreUnit 的地址/数据配对流程。源仲裁器选中 `io.ldin` 后，只有在没有 kill 且 DCache 请求端 ready 时，s0 才能形成有效请求；LoadUnit 又把同一组条件反馈到 `io.ldin.ready`，从而保证 issue→s0 不会在请求端已经无法接收时提前消费 uop。[`LoadUnit.scala:303`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L303 ) [`LoadUnit.scala:330`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L330 ) [`LoadUnit.scala:840`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L840 )

#### s0：计算有效地址并生成 `M_PFW`

s0 使用重命名后的基址物理寄存器值和立即数形成虚拟地址。随后根据源类型决定 DCache 命令：普通 Load 使用 `M_XRD`，读预取使用 `M_PFR`，写预取使用 `M_PFW`。

```scala
io.dcache.req.valid := s0_valid && !s0_sel_src.prf_i && !s0_nc_with_data
io.dcache.req.bits.cmd := Mux(
  s0_sel_src.prf_rd,
  MemoryOpConstants.M_PFR,
  Mux(s0_sel_src.prf_wr, MemoryOpConstants.M_PFW, MemoryOpConstants.M_XRD)
)
io.dcache.req.bits.vaddr := s0_dcache_vaddr
io.dcache.req.bits.isFirstIssue := s0_sel_src.isFirstIssue
io.dcache.req.bits.instrtype :=
  Mux(s0_sel_src.prf, DCACHE_PREFETCH_SOURCE.U, LOAD_SOURCE.U)
io.dcache.req.bits.debug_robIdx := s0_sel_src.uop.robIdx.value
```

这里有三个关键控制信号。`cmd=M_PFW` 表示“带写意图的预取”，不是执行一次真正的 Store；`instrtype=DCACHE_PREFETCH_SOURCE` 让下游缓存逻辑保留软件预取身份；`debug_robIdx` 使缓存请求能够与后端 uop 调试/跟踪信息关联。请求的 valid 还排除了内部预取和带数据的非缓存情况，保证这条软件预取走的是明确的 DCache 请求接口。[`LoadUnit.scala:406`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L406 )

#### s1：延续预取标志

s1 保存从 s0 传来的 uop、虚拟地址、`isPrefetch` 和 `isFirstIssue` 等字段。`isPrefetch` 不是装饰性信息：后续 s2 会据此抑制普通 Load 的异常行为、区分 load/store 相关性检查，并统计预取命中或未命中。

#### s2：完成翻译、接受结果并抑制普通 Load 语义

在 s2，LoadUnit 处理 TLB 结果、DCache response、异常和 MissQueue 反馈。软件预取不会把 cache miss 当成普通 Load 的可见异常；相关逻辑对预取异常向量进行清零，源码注释明确说明软件预取不触发普通异常。`s2_prf` 同时被用于排除普通 load-load/store-load nuke 查询，避免把预取错误地当成会产生寄存器数据的 demand load。[`LoadUnit.scala:1198`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L1198 ) [`LoadUnit.scala:1369`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L1369 )

LoadUnit 还分别生成 `s0_software_prefetch_fire`、`s2_prefetch_miss`、`s2_prefetch_accept` 和 `s2_prefetch_hit` 等状态，用于区分请求是否发出、是否 miss、是否被 MissQueue 接受以及是否命中。这些信号服务于预取流程控制与统计，而不是生成整数写回值。[`LoadUnit.scala:1932`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L1932 )

### 6. MemBlock：把 LoadUnit 接到 DCache，把 Store pipeline 分开

MemBlock 为每个 LoadUnit 建立独立的 DCache LSU load 端口：

```scala
loadUnits(i).io.dcache <> dcache.io.lsu.load(i)
```

因此软件预取从 LoadUnit 发出的请求会进入 DCache 的 load-side 接口。MemBlock 还可能在向量 segment 访问抢占资源时拉低 LoadUnit ready；这属于共享内存端口仲裁，并不是 `PREFETCH.W` 独有的 store-data 流程。[`MemBlock.scala:880`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L880 )

MemBlock 中另有 Store pipeline，以及 Hybrid/Store prefetch 的连接。它们说明普通 StoreUnit 的职责是处理真实 Store 的地址、数据和 store buffer 状态；`PREFETCH.W` 虽然命令名含有“write”，但在执行入口仍由 LoadUnit 发起，写意图只作为 DCache command 传递。[`MemBlock.scala:950`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L950 ) [`MemBlock.scala:1155`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L1155 )

### 7. DCache：软件预取使用独立 source 编码

DCacheWrapper 为请求来源定义了明确的 source 编码：

```scala
LOAD_SOURCE             = 0
STORE_SOURCE            = 1
AMO_SOURCE              = 2
DCACHE_PREFETCH_SOURCE  = 3
SOFT_PREFETCH           = 4
```

LoadUnit 通过 `instrtype=DCACHE_PREFETCH_SOURCE` 选择 source=3，使 DCache 能在共享 load port、MissQueue 和主流水线中区分软件预取与 demand load。DCacheWrapper 将 LDU 端口接入 DCache load 端口，并把预取 usefulness、late hit、late miss 等监控信号接到该路径。[`DCacheWrapper.scala:96`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L96 ) [`DCacheWrapper.scala:1381`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L1381 )

主流水线请求和 LDU miss 请求在 `missReqArb` 汇合，然后由 MissQueue 统一管理；MissQueue 的 response 再回传到对应 LDU。StoreUnit 的 miss request 端口则只有在 `StorePrefetchL1Enabled` 打开时才接入，否则 ready 被置为 `false.B`。这进一步表明软件 `PREFETCH.W` 的主执行路径不是 StoreUnit miss 请求端口。[`DCacheWrapper.scala:1475`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L1475 ) [`DCacheWrapper.scala:1489`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L1489 )

### 8. LoadPipe 与 MissQueue：以“预取写”处理 cache miss

LoadPipe 对请求命令做了显式约束，允许的命令包括普通读、`M_PFR` 和 `M_PFW`：

```scala
assert(cmd === M_XRD || cmd === M_PFR || cmd === M_PFW)
```

这不是把 `M_PFW` 当作普通 Store，而是说明 LoadPipe 专门允许软件预取读/写请求进入其流水线。LoadPipe 还检查 `instrtype == DCACHE_PREFETCH_SOURCE`，并据此累计 `total_prefetch`、`useless_prefetch` 和 `useful_prefetch`，为预取效果提供专门统计。[`LoadPipe.scala:140`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala#L140 ) [`LoadPipe.scala:490`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala#L490 )

缓存命令常量中，`M_PFW` 的定义是“prefetch with intent to write”：

```scala
def M_PFW = "b00011".U // prefetch with intent to write
```

该编码被 LoadUnit 生成并一路带入 DCache。MissQueue 根据 `source` 和 `cmd` 组合产生 `isFromPrefetch`、`isPrefetchWrite` 与 `isPrefetchRead`：当 source 是 `DCACHE_PREFETCH_SOURCE` 且 command 是 `M_PFW` 时，`isPrefetchWrite` 为真。因此 MissQueue 可以在不需要 store data 的前提下，对这条请求保留“写意图预取”的身份。[`CacheConstants.scala:32`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/CacheConstants.scala#L32 ) [`MissQueue.scala:88`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L88 )

MissQueue entry 在接收到主流水线请求后置位 `s_mainpipe_req` 和 `mainpipe_req_fired`，表示该 miss 已经向 main pipe 发出后续请求；在 replay 或 eviction 等条件下这些状态会被清除。entry 重新满足 `w_l2hint` 或 `w_grantlast` 时，MissQueue 通过 `main_pipe_req` 输出请求，并原样传递 source、command、虚拟地址、line 地址和预取来源字段。因此，预取的特殊身份不会在 miss 队列边界丢失。[`MissQueue.scala:701`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L701 ) [`MissQueue.scala:881`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L881 )

### 9. ROB：执行完成、顺序提交与退休

LoadUnit 的执行反馈仍然带有 uop 的 `robIdx`。ROB 用执行单元 writeback 端口中的 `wb.bits.robIdx` 更新对应 ROB entry 的 debug 信息、结果状态以及 LQ/SQ 和 writeback 相关字段。即使 `PREFETCH.W` 没有整数结果，它仍然必须让 ROB 知道该 uop 已经完成，才能满足头部顺序提交条件。[`Rob.scala:528`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L528 )

ROB 的提交信息会根据头部 entry 的完成状态、异常状态和提交位置形成 commit valid。`RobDeqPtrWrapper` 负责检查头部异常和 `commit_exception`；如果头部存在需要处理的异常，正常 commit 会被阻止，直到异常/重定向流程完成。对没有异常的 `PREFETCH.W`，它按正常程序顺序等待前面的 ROB 项退休，再从 ROB 头部提交。[`RobDeqPtrWrapper.scala:55`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/RobDeqPtrWrapper.scala#L55 )

退休时，ROB 的 Difftest/架构写回控制会使用 `commitInfo.rfWen` 和 `ldest` 决定是否写整数寄存器：

```scala
difftest.valid := commitValid && isCommit
difftest.rfwen := commitValid && commitInfo.rfWen && ldest =/= 0
```

`PREFETCH.W` 在 Decode/Rename 阶段没有整数目的寄存器，因此 `rfWen=0`、`ldest=0`，退休时不会产生 GPR 写回。它的架构效果是“完成一次带写意图的缓存预取请求”，而不是产生一个可读的寄存器结果。[`Rob.scala:1547`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L1547 )

ROB 还单独判定 Load/Store commit event，但这类事件判定不改变软件预取没有寄存器写回的事实。预取的缓存副作用由 DCache、LoadPipe 和 MissQueue 完成；ROB 只负责让该 uop 以正确的程序顺序完成、提交和退休。[`Rob.scala:1580`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L1580 )

### 10. 源代码层面的完整数据流

从昆明湖源码可以把这条指令概括为以下链路：

```text
DecodeUnit
  -> 识别 opcode/funct3/rd/rs2
  -> fuType=LSU, fuOpType=prefetch_w
Rename
  -> 映射 rs1 物理源
  -> 不分配 GPR pdest
Dispatch
  -> 建立 ROB entry
  -> 按 lsu fuType 进入 MemScheduler
IssueQueue/DataPath
  -> 等待源 ready 和执行端 ready
  -> 送入 memory Exu
LoadUnit
  -> io.ldin 首次发射
  -> s0 计算 vaddr
  -> cmd=M_PFW, instrtype=DCACHE_PREFETCH_SOURCE
MemBlock/DCache
  -> LoadUnit DCache load port
  -> LoadPipe 接受 M_PFW
  -> miss 时进入 MissQueue
  -> 按 source/cmd 保留预取写身份
ROB
  -> 接收执行完成反馈
  -> 等待顺序条件满足
  -> Commit/Retire，rfWen=0
```

由此可见，昆明湖对 `PREFETCH.W` 的特殊支持集中在三层：第一，Decode 用专门的 `prefetch_w` uop 类型识别它；第二，LoadUnit 把它作为软件预取的首次发射，并生成 `M_PFW`；第三，DCache 的 LoadPipe 和 MissQueue 通过 `DCACHE_PREFETCH_SOURCE` 与 `M_PFW` 组合保留并处理“写意图预取”语义。StoreUnit 只承担独立的真实 Store/Store-prefetch 相关接口，不是这条软件 `PREFETCH.W` 的主要执行入口。

## PREFETCH_W 演示程序

### 1. 程序目标与文件位置

演示程序位于：

```text
/home/yanyusong/cbo-kmhv2/nexus-am/apps/prefetch_w/
├── Makefile
└── prefetch_w.c
```

`Makefile` 很简单，和 `hello` 一类示例保持同样的 AM 构建方式：只声明应用名和源码文件，其余由 `Makefile.app` 接管。这个程序的目标不是做复杂算法，而是稳定构造一条 `PREFETCH.W` 的前后内存访问链路，方便后续的波形和源码分析对齐。

程序的安排可以概括为四步：

1. 先初始化并访问 `cache_demo_data[0]`、`cache_demo_data[1]`，形成第一段真实的数据访存；
2. 再通过内联汇编发出一条固定编码的 `PREFETCH.W`，目标地址选在 `cache_demo_data[16]` 对应的缓存行；
3. 之后继续打印和对 `target` 做普通写读，保证预取后还有后续使用；
4. 最后返回一个固定校验值，证明程序语义没有被破坏。

其中 `cache_demo_data` 按 64 B 对齐，`prefetch_base = &cache_demo_data[16]` 指向地址 `0x80001700`，`target = prefetch_base + 8` 指向 `0x80001720`。这两个地址落在同一条 64 B cache line 内，便于把“预取的对象”和“后续真正使用的对象”放在同一条线里观察。

### 2. 完整 C 代码

```c
#include <klib.h>

#define CACHE_LINE_BYTES 64
#define PREFETCH_OFFSET_BYTES 32

static volatile unsigned int cache_demo_data[64] __attribute__((aligned(CACHE_LINE_BYTES)));

static inline void prefetch_w_32(void *address)
{
  register void *prefetch_base asm("a3") = address;

  asm volatile(
    ".word 0x0236e013\n"
    :
    : "r"(prefetch_base)
    : "memory");
}

int main(void)
{
  volatile unsigned int *prefetch_base = &cache_demo_data[16];
  volatile unsigned int *target = &prefetch_base[PREFETCH_OFFSET_BYTES / sizeof(cache_demo_data[0])];
  unsigned int observed_value;

  printf("PREFETCH.W demo: initialize cache-line data\n");
  cache_demo_data[0] = 0x13579bdf;
  cache_demo_data[1] = 0x2468ace0;
  observed_value = cache_demo_data[0] + cache_demo_data[1];
  printf("before PREFETCH.W: observed=0x%x, target=%p\n", observed_value, target);

  prefetch_w_32((void *)prefetch_base);
  asm volatile("" ::: "memory");

  printf("after PREFETCH.W: write target cache line\n");
  *target = observed_value ^ 0xa5a5a5a5;
  observed_value = *target;
  printf("after PREFETCH.W: target=0x%x\n", observed_value);

  return observed_value == 0x9265ed1a ? 0 : 1;
}
```

这个写法里最关键的是两点：第一，`register void *prefetch_base asm("a3")` 把操作数固定放进 `a3`，这样汇编里的 `PREFETCH.W` 就会用 `a3` 作为基址寄存器；第二，`asm volatile` 和 `"memory"` clobber 保证这条指令不会被编译器删除或跨越重排。

### 3. 反汇编与关键地址

构建后的反汇编文件是：

[prefetch_w-riscv64-xs.txt](/home/yanyusong/cbo-kmhv2/nexus-am/apps/prefetch_w/build/prefetch_w-riscv64-xs.txt:86)

`main` 的关键片段如下：

```text
000000008000012a <main>:
    8000012a: 1101                 addi    sp,sp,-32
    8000012c: 00001517             auipc   a0,0x1
    80000130: 20450513             addi    a0,a0,516 # 80001330 <printf_+0x32>
    80000138: 1c6010ef             jal     800012fe <printf_>
    80000140: 00001417             auipc   s0,0x1
    80000144: 58040413             addi    s0,s0,1408 # 800016c0 <cache_demo_data>
    8000014c: c01c                 sw      a5,0(s0)
    80000156: c05c                 sw      a5,4(s0)
    80000158: 400c                 lw      a1,0(s0)
    8000015a: 405c                 lw      a5,4(s0)
    80000170: 18e010ef             jal     800012fe <printf_>
    80000174: 00001697             auipc   a3,0x1
    80000178: 58c68693             addi    a3,a3,1420 # 80001700 <cache_demo_data+0x40>
    8000017c: 0236e013             .word   0x0236e013
    8000018c: 65a2                 ld      a1,8(sp)
    80000198: d02c                 sw      a1,96(s0)
    8000019a: 502c                 lw      a1,96(s0)
```

这里的关系很直接：

- `auipc a3` + `addi a3, ...` 把 `a3` 设成 `0x80001700`，也就是 `cache_demo_data+0x40`；
- 紧跟着的 `.word 0x0236e013` 就是程序里那条固定编码的 `PREFETCH.W`；
- 后面的 `sw a1,96(s0)` 和 `lw a1,96(s0)` 访问的是 `0x80001720`，仍然在 `0x80001700` 这条 cache line 内。

`objdump` 没有把这条指令打印成助记符，而是保留成 `.word`。这不代表它是非法指令，只是反汇编器没有显示出对应的名字；在昆明湖的 Decode 中，这个 32 位编码会被识别成 `PREFETCH.W 32(a3)`。

### 4. 场景讲解

这个演示程序的场景设计很克制，目的只有一个：让 `PREFETCH.W` 前后都出现真实的内存活动，并且让预取目标和后续使用落在同一条 cache line 上。

1. **预取前先做真实访存。** `cache_demo_data[0]` 和 `cache_demo_data[1]` 的写入、读取都发生在 `0x800016c0` 所在的第一条缓存行上；这给前半段提供了可见的数据访问背景。
2. **用固定寄存器和固定编码发出 `PREFETCH.W`。** `prefetch_w_32()` 把地址放入 `a3`，因此这条指令天然对应 `rs1=a3`；指令编码里的偏移是 32 B，代码中的 `PREFETCH_OFFSET_BYTES=32` 也让后续 `target` 对齐到同一个地址 `0x80001720`。
3. **预取后继续做普通操作。** `printf("after PREFETCH.W...")` 和后面的 `*target = ...` 让预取之后还有独立工作，不会把整条程序压成一条孤立 hint。
4. **再读回并校验结果。** `observed_value ^ 0xa5a5a5a5` 的结果应当是 `0x9265ed1a`，程序用这个值判断功能是否正确。预取本身不改变架构数据，真正的语义校验来自后续 store/load。

因此，这个 demo 的作用不是证明“预取一定提升了性能”，而是构造一个足够清楚的功能场景：先访问别的缓存行，再对目标缓存行发出 `PREFETCH.W`，最后用同一缓存行里的普通访存验证程序仍然按预期执行。

## 波形图分析

### 分析对象、方法与可信边界

本节分析最终仿真波形
`/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-17-23-02.fst`，目标指令为：

```text
PC       = 0x8000017c
instr    = 0x0236e013
语义     = PREFETCH.W 32(a3)
a3       = 0x80001700
目标 VA  = 0x80001720
目标 line= 0x80001700（64-byte 对齐）
```

使用 `/home/yanyusong/wavekit` 开源库中的 `wavekit.FstReader` 读取 FST；采样时钟为 `TOP.clock` 的**上升沿**。本文所有 `fire` 均按 `valid && ready` 定义。目标指令在 ROB Difftest commit lane 7 上于周期 18757、仿真时间 37514 提交，提交 PC 与指令字均匹配上述目标。

需要特别说明两个边界：第一，波形中带 PC 的流水寄存器在 `valid=0` 时会保留旧值，因此本文只在相应 `valid=1` 时认定该指令驻留在该级。第二，DCache/MissQueue 内有很多无 PC 的共享状态；进入该部分后，本文以 `vaddr=0x80001720`、line 地址 `0x80001700`、`source=3`、`cmd=3` 的组合识别本请求，而不把无关的同值寄存器残留误关联到目标指令。

### 总体时间线

| 周期 | 时间 | 阶段/接口 | 目标身份和握手 | 关键控制/数据 | 含义 |
|---:|---:|---|---|---|---|
| 17873 | 35746 | `Decode.io_out_5` | `valid=1, ready=1, fire=1` | PC `0x8000017c`，instr `0x0236e013` | 译码结果交给 Rename。 |
| 17874 | 35748 | `Rename.io_in_5/out_5` | 输入、输出均 `fire=1` | ROB=`79`，`psrc0=82`，`pdest=0`，imm=`0x20` | 为 `a3` 建立物理源寄存器身份；无目的寄存器分配。 |
| 17875 | 35750 | Dispatch/ROB/`IssueQueueLdu_1.io_enq_0` | `valid=1, ready=1, fire=1` | PC=`0x8000017c`，ROB=`79` | 分派到内存调度器，并被 LDU 1 的 issue queue 接收。 |
| 17876–17883 | 35752–35766 | `IssueQueueLdu_1` 驻留 | 队列入口已无 `valid`；`LoadUnit_1.io_ldin.valid=0` | `io_ldin.ready=1` | 指令在 IQ 中等待被选择；本波形未转储选择器的目标 entry 编号，不能把这 8 个周期归因于某一个特定阻塞信号。 |
| 17884 | 35768 | `LoadUnit_1.io_ldin` / s0 | `ldin.valid=1, ldin.ready=1, fire=1`；`s0_valid=1` | ROB=`79`，src0=`0x80001700`；DCache req `valid=1, ready=1, cmd=3, vaddr=0x80001720` | 软件预取以 LoadUnit 的整数发射通路进入；有效地址相加并发出 `M_PFW`。 |
| 17885 | 35770 | `LoadUnit_1` s1 | `s1_valid=1` | PC/ROB=`0x8000017c`/`79`，`vaddr=0x80001720`，`isPrefetch=1`，`isFirstIssue=1` | s1 保存预取属性并进入地址翻译/缓存访问后续阶段。 |
| 17886 | 35772 | `LoadUnit_1` s2 | `s2_valid=1` | `paddr=0x80001720`，`tlb_hit=1`，`exception=0`；DCache resp `valid=1, miss=1`，`s2_mq_nack=0` | 地址翻译成功；L1D miss，且 MissQueue 接受预取请求。 |
| 17888 | 35776 | `MissQueue.entries_2` | entry 2 接收目标 line | `req_addr=0x80001700`，`source=3`，`cmd=3`，`s_mainpipe_req=0` | 为此冷 line 分配 miss entry；等待可回送 main pipe 的条件。 |
| 17934 | 35868 | `DCache.main_pipe_req_arb.io_out` | `valid=1, ready=1, fire=1` | `source=3, cmd=3, vaddr=0x80001720, addr=0x80001700, miss=1` | entry 2 的预取 miss 被仲裁回 DCache main pipe。 |
| 17935 | 35870 | `MissQueue.entries_2` | `mainpipe_req_fired=1` | `s_mainpipe_req=1` | main-pipe 请求已被接受，entry 不再等待该请求。 |
| 18757 | 37514 | ROB commit lane 7 | `valid=1` | PC=`0x8000017c`，instr=`0x0236e013` | 指令按程序顺序提交；没有目的寄存器写回。 |

从周期 17884 的 LoadUnit 发射到周期 18757 的 ROB 提交相隔 873 周期。这里不能把 873 周期解释成 `PREFETCH.W` 自身的执行延迟：该指令是无结果的软件预取，已在 s2 的周期 17886 获得 DCache 的 miss 接受结果；提交时间还受 ROB 前面的大量 `printf`/UART 相关老指令的顺序退休影响。

### 1. Decode：识别为软件写预取

波形接口：

```text
TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_ctrlBlock.decode.io_out_5
```

周期 17873 的关键值为：

```text
valid=1, ready=1, fire=1
pc=0x8000017c, instr=0x0236e013
lsrc0=13 (x13/a3), lsrc1=3, ldest=0
srcType0=1, srcType1=0
fuType=0x8000, fuOpType=10
rfWen=0, selImm=14
exceptionVec[0]=0, preDecodeInfo.valid=1
ftqPtr=(flag=0,value=40), ftqOffset=4
```

`lsrc0=13` 是基址 `a3`；`ldest=0`、`rfWen=0` 说明这不是把数据读回 GPR 的普通 load。`selImm=14` 的实际立即数在 Rename 输出端变为 `0x20`，故有效地址计算为 `a3 + 0x20`。`exceptionVec[0]=0` 表明 decode 时未携带前端异常。

[DecodeUnit.scala:1102](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L1102) 用 opcode、funct3、rd 和 rs2 识别软件预取；`RS2==3` 专门选择 `prefetch.w`：

```scala
// decode for SoftPrefetch instructions (prefetch.w / prefetch.r / prefetch.i)
val isSoftPrefetch = inst.OPCODE === BitPat("b0010011") && inst.FUNCT3 === BitPat("b110") && inst.RD === 0.U
val isPreW = isSoftPrefetch && inst.RS2 === 3.U(5.W)
val isPreR = isSoftPrefetch && inst.RS2 === 1.U(5.W)
val isPreI = isSoftPrefetch && inst.RS2 === 0.U(5.W)
```

[DecodeUnit.scala:1166](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L1166) 随后把该识别结果编码为 `LSUOpType.prefetch_w`；这解释了波形的 `fuOpType=10`（本配置中该操作码的数值表现）：

```scala
(isPreW || isPreR || isPreI) -> Mux1H(Seq(
  isPreW -> LSUOpType.prefetch_w,
  isPreR -> LSUOpType.prefetch_r,
  isPreI -> LSUOpType.prefetch_i,
)),
```

**信号去向。** `Decode.io_out_5.bits` 是完整的 decoded uop，`fire` 后送入 `Rename.io_in_5.bits`；其中 PC/instr 用于后续调试和 commit 对照，`lsrc0` 用于物理寄存器映射，`fuType/fuOpType` 决定进入内存调度与软件预取控制分支，`rfWen=0` 禁止为它分配普通整数写回。

### 2. Rename、Dispatch 与 ROB：保留 `a3` 的物理依赖，不建立写回依赖

周期 17874，`Rename.io_in_5` 和 `Rename.io_out_5` 均为 `valid=1, ready=1`，说明没有 free-list 或 rename 反压。输出值：

```text
pc=0x8000017c, instr=0x0236e013
robIdx=(flag=0,value=79)
psrc0=82, psrc1=0, psrc2=0
pdest=0, ldest=0, rfWen=0
fuType=0x8000, fuOpType=10
imm=0x20, selImm=14
hasException=0, waitForward=0, blockBackward=0, flushPipe=0
```

这组值的因果关系如下：

- `psrc0=82` 是架构寄存器 `a3` 被 Rename 映射后的物理寄存器；后续实际发射时，波形在 `LoadUnit_1.io_ldin.bits.src_0` 观察到其数据值 `0x80001700`。
- `pdest=0`/`rfWen=0` 保证软件预取不会在物理寄存器文件制造一个“假 load 返回值”，也不需要普通 writeback 仲裁。
- `robIdx=79` 是从 Rename 后用来追踪本 uop 的稳定身份；它在 LDU IQ 入队、`LoadUnit_1.io_ldin`、s1、s2 中均保持为 79。
- `hasException=0`、`waitForward=0`、`blockBackward=0`、`flushPipe=0` 说明 Rename 既未发现异常，也没有要求等待 forward、阻塞反向传播或冲刷流水线。

周期 17875，`Dispatch.io_fromRename_5` 和 `Dispatch.io_enqRob_req_5` 均看到该 PC，并且 `IssueQueueLdu_1.io_enq_0` 的 `valid=1, ready=1, fire=1, rob=79`。因此数据路径是：

```text
Rename.io_out_5
  -> Dispatch.io_fromRename_5
  -> Dispatch.io_enqRob_req_5          （ROB 建项）
  -> MemScheduler.io_fromDispatch_uops （内存调度广播）
  -> IssueQueueLdu_1.io_enq_0          （实际接收）
```

`IssueQueueLdu_1` 在周期 17876–17883 持有该 uop，到周期 17884 才选择它送往执行端。波形证明 queue 当时可接受新 uop（`io_enq_0.ready=1`），且 `LoadUnit_1.io_ldin.ready=1`；但没有转储能唯一对应 ROB 79 的 IQ entry-select/源操作数 ready 细节，故只能将这段定义为**可观测的调度驻留**，不能无证据地宣称是 cache、TLB 或依赖造成的 stall。

### 3. Issue 与 LoadUnit：软件预取走 LDU 整数发射口，而非普通 StoreUnit 地址发射口

这一点是该指令最重要的特殊处理。波形清楚显示目标 uop 出现在：

```text
TOP...core.memBlock.inner_LoadUnit_1.io_ldin
```

而不是带有该 PC 的 `StoreUnit.io_issue` 流中。周期 17884：

```text
io_ldin.valid=1, io_ldin.ready=1, fire=1
io_ldin.bits.uop.pc=0x8000017c
io_ldin.bits.uop.robIdx=79
io_ldin.bits.src_0=0x0000000080001700
s0_valid=1
io_dcache_req.valid=1, io_dcache_req.ready=1, fire=1
io_dcache_req.bits.vaddr=0x80001720
io_dcache_req.bits.cmd=3
```

[LoadUnit.scala:303](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L303) 把 `io.ldin` 明确说明为“整数 read / 软件预取的第一次 issue”源；它在 source arbitration 中对应 `int_iss_idx`：

```scala
// src 8: int read / software prefetch first issue from RS (io.in)
// ...
io.ldin.valid, // int flow first issue or software prefetch
```

[LoadUnit.scala:341](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L341) 规定只有选中该源、未 kill 且 DCache ready 时，s0 才有效；[LoadUnit.scala:848](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L848) 则把同一 ready 条件反馈给 `io.ldin`：

```scala
s0_valid := !s0_kill && ( /* selected source valid */
  ... s0_src_valid_vec(int_iss_idx) ...
  && io.dcache.req.ready
)

io.ldin.ready := s0_can_go && io.dcache.req.ready && s0_src_ready_vec(int_iss_idx)
```

因此本周期 `ldin.ready=1`、`dcache.req.ready=1` 和 `s0_valid=1` 共同证明：uop 没有在 issue→s0 边界被 backpressure；而是作为被选中的软件预取直接进入 s0。

**StoreUnit 的角色。** `PREFETCH.W` 的名字包含 W，但在昆明湖的执行入口不是普通 `StoreUnit` 地址/数据双发射路径：它先走 LoadUnit 的共享地址翻译和 L1D load-port 流程，并把缓存命令标成写预取。这就是为何该指令无 store data、无 `rfWen`，同时仍然能请求“以写意图预取”的 cache line。StoreUnit/StorePipe 的特殊逻辑在 DCache miss 侧识别并构造 `M_PFW` 请求；后面的“Cache 与 MissQueue”小节说明这一点。

### 4. LoadUnit s0/s1/s2：地址、TLB、异常抑制与 DCache 接受

#### s0：构造 `M_PFW` 与 cache 请求

在周期 17884，src0 为 `0x80001700`，立即数为 `0x20`，故 s0 形成 `vaddr=0x80001720`。该周期的 DCache 请求已 `fire`。下列 Chisel 是请求字段的直接来源：[LoadUnit.scala:406](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L406)。

```scala
io.dcache.req.valid := s0_valid && !s0_sel_src.prf_i && !s0_nc_with_data
io.dcache.req.bits.cmd := Mux(s0_sel_src.prf_rd,
  MemoryOpConstants.M_PFR,
  Mux(s0_sel_src.prf_wr, MemoryOpConstants.M_PFW, MemoryOpConstants.M_XRD)
)
io.dcache.req.bits.vaddr := s0_dcache_vaddr
io.dcache.req.bits.isFirstIssue := s0_sel_src.isFirstIssue
io.dcache.req.bits.instrtype := Mux(s0_sel_src.prf, DCACHE_PREFETCH_SOURCE.U, LOAD_SOURCE.U)
io.dcache.req.bits.debug_robIdx := s0_sel_src.uop.robIdx.value
```

这解释了波形中的 `cmd=3`、`vaddr=0x80001720`、`isFirstIssue=1` 和 ROB 79 的关联。`instrtype` 会被置为 `DCACHE_PREFETCH_SOURCE`，所以 DCache/MissQueue 不会把它当作普通 demand load。

#### s1：保留软件预取身份

周期 17885：

```text
s1_valid=1
s1_in_r.uop.pc=0x8000017c
s1_in_r.uop.robIdx=79
s1_in_r.vaddr=0x80001720
s1_in_r.isPrefetch=1
s1_in_r.isFirstIssue=1
```

`isPrefetch=1` 是关键控制位：它把同一条 uop 从普通 load 语义分叉为软件预取语义，供 s2 的异常、MMIO、LSQ 查询和性能计数逻辑使用。

#### s2：TLB hit、L1D miss、请求未被 MQ 拒绝

周期 17886：

```text
s2_valid=1
s2_in_r.uop.pc=0x8000017c, robIdx=79
s2_in_r.vaddr=0x80001720, paddr=0x80001720
s2_in_r.isPrefetch=1
s2_tlb_hit=1
s2_exception=0
io_dcache_resp.valid=1
io_dcache_resp.bits.miss=1
io_dcache_s2_mq_nack=0
```

这组信号的意义是：地址翻译命中；该 line 在 L1D 中未命中；但 MissQueue 没有 nack，因此软件预取被 L1D 接纳而非因为 MSHR 满或 miss request 端口冲突被丢弃。

[LoadUnit.scala:1231](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L1231) 对软件预取显式清空异常向量；这与 `s2_exception=0` 相一致：

```scala
// soft prefetch will not trigger any exception (but ecc error interrupt may
// be triggered)
when (!s2_in.delayedLoadError && (s2_prf || s2_in.tlbMiss && !s2_tlb_unrelated_exceps)) {
  s2_exception_vec := 0.U.asTypeOf(s2_exception_vec.cloneType)
}
```

[LoadUnit.scala:1958](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L1958) 定义了预取的 miss/accept 判定，正好对应上述波形组合：

```scala
XSPerfAccumulate("s2_prefetch_miss", s2_fire && s2_prf && io.dcache.resp.bits.miss)
XSPerfAccumulate("s2_prefetch_accept", s2_fire && s2_prf &&
  io.dcache.resp.bits.miss && !io.dcache.s2_mq_nack)
```

此外，软件预取不会走普通 load 的返回数据写回路径：Rename 时已见 `rfWen=0,pdest=0`，而 s2 的预取属性使其不需要把 cache data 作为 GPR 结果交给 writeback。其“执行完成”的体系结构结果是：预取请求已经被 DCache/MissQueue 接收，uop 无异常，可等待 ROB 按序退休。

### 5. MemBlock：把 LoadUnit 的软件预取请求接到 L1D load port

目标 LDU 是 `LoadUnit_1`。MemBlock 中的连接为 `loadUnits(i).io.dcache <> dcache.io.lsu.load(i)`；代码见 [MemBlock.scala:880](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L880)：

```scala
// dcache access
loadUnits(i).io.dcache <> dcache.io.lsu.load(i)
```

因此信号的生产者/消费者关系为：

```text
LoadUnit_1.s0
  -> LoadUnit_1.io.dcache.req
  -> MemBlock.dcache.io.lsu.load(1).req
  -> DCache LoadPipe / MissQueue
```

这也解释了为何 `LoadUnit_1.io_dcache_req.ready=1` 是本指令能从 s0 前进的直接许可。若 MemBlock 的 vector segment 请求抢占 load port，源码会把 `loadUnits(i).io.dcache.req.ready` 拉低；本次目标周期不是该情况，ready 一直为 1。

### 6. DCache 与 MissQueue：从 `M_PFW` 预取 miss 到 main pipe

#### 初始 L1D 请求与 MissQueue 分配

LDU s2 在周期 17886 得到 `miss=1 && mq_nack=0`。到周期 17888，DCache MissQueue entry 2 持有：

```text
req_addr=0x80001700
req_source=3
req_cmd=3
s_mainpipe_req=0
mainpipe_req_fired=0
```

这里 `req_addr` 是由 byte 地址 `0x80001720` 截成的 64-byte line 地址。`source=3` 是 `DCACHE_PREFETCH_SOURCE`，`cmd=3` 对应 `M_PFW`；二者共同区分“写预取”与普通 store/load。

[DCacheWrapper.scala:103](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L103) 和 [CacheConstants.scala:32](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/CacheConstants.scala#L32) 给出该数值映射：

```scala
def DCACHE_PREFETCH_SOURCE = 3
def M_PFW = "b00011".U // prefetch with intent to write
```

虽然 `PREFETCH.W` 由 LoadUnit 入口送入 DCache，StorePipe 提供的写预取 miss 构造逻辑说明了 DCache 内部对这种命令的共同处理方式。[StorePipe.scala:165](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala#L165) 在预取 miss 时以 `DCACHE_PREFETCH_SOURCE`、`M_PFW` 和 line-aligned paddr 构造 MissQueue 请求：

```scala
io.miss_req.valid := s2_valid && !s2_hit && s2_is_prefetch
io.miss_req.bits.source := DCACHE_PREFETCH_SOURCE.U
io.miss_req.bits.pf_source := L1_HW_PREFETCH_STORE
io.miss_req.bits.cmd := MemoryOpConstants.M_PFW
io.miss_req.bits.addr := get_block_addr(s2_paddr)
io.miss_req.bits.vaddr := s2_req.vaddr
```

[MissQueue.scala:92](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L92) 则把这一组合定义为 `isPrefetchWrite`：

```scala
def isFromPrefetch = source >= DCACHE_PREFETCH_SOURCE.U
def isPrefetchWrite = source === DCACHE_PREFETCH_SOURCE.U && cmd === MemoryOpConstants.M_PFW
def isPrefetchRead = source === DCACHE_PREFETCH_SOURCE.U && cmd === MemoryOpConstants.M_PFR
```

#### entry 2 到 main pipe 的等待与发射

从周期 17888 到 17934，entry 2 的 `s_mainpipe_req=0`，表示 main-pipe 请求尚未发送。周期 17934 的仲裁器输出为：

```text
main_pipe_req_arb.io_out.valid=1
main_pipe_req_arb.io_out.ready=1
fire=1
bits.miss=1
bits.source=3, bits.cmd=3
bits.vaddr=0x80001720
bits.addr=0x80001700
bits.pf_source=0
```

紧接着周期 17935，entry 2 的 `s_mainpipe_req=1`、`mainpipe_req_fired=1`。这正符合 [MissQueue.scala:701](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L701) 的状态更新：

```scala
when (io.main_pipe_req.fire) {
  s_mainpipe_req := true.B
  mainpipe_req_fired := true.B
}
```

[MissQueue.scala:881](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L881) 说明 main-pipe 请求仅在收到 L2 hint 或最后一个 grant 后才有效，并将 entry 中的 `source/cmd/vaddr/addr` 原样传出：

```scala
io.main_pipe_req.valid := !s_mainpipe_req && (w_l2hint || w_grantlast)
io.main_pipe_req.bits.miss := true.B
io.main_pipe_req.bits.source := req.source
io.main_pipe_req.bits.cmd := req.cmd
io.main_pipe_req.bits.vaddr := req.vaddr
io.main_pipe_req.bits.addr := req.addr
```

波形没有把 entry 2 对应的 `w_l2hint` 和 `w_grantlast` 同时转储为可直接关联的信号，所以 17888–17933 的 46 周期等待不能进一步断言究竟是在等 hint 还是等最后一个 grant；可以严格确认的是，17934 已满足该有效条件并完成 `fire`，且请求字段没有被改写。

### 7. Commit、无 redirect 与架构效果

目标 uop 在执行窗口内有以下无异常证据：

- Decode：`exceptionVec[0]=0`；
- Rename：`hasException=0, flushPipe=0`；
- LoadUnit s2：`tlb_hit=1, exception=0`；
- ROB：周期 18757 的 Difftest commit lane 7 输出 `valid=1, pc=0x8000017c, instr=0x0236e013`。

本波形没有出现能够和 `ROB=79` 关联的 redirect/flush 事件；更强的正证据是该 uop 最终以原 PC 提交。由于 `rfWen=0`、`pdest=0`，其 Difftest 架构效果不是寄存器更新，而是“允许的预取请求已被送入 cache miss 路径”。后续程序在 PC `0x80000198` 对同一地址 `0x80001720` 执行普通 `sw`，StoreBuffer Difftest 记录在周期 24179 写入数据 `0x9265ed1a`、mask `0xf`；最终仿真以 GOOD TRAP 结束。

### 8. 结论：各模块的特殊处理汇总

| 模块 | 对 `PREFETCH.W` 的特殊处理 | 波形证据 | 信号去向 |
|---|---|---|---|
| Decode | 用 `RS2==3` 识别软件写预取并生成 `LSUOpType.prefetch_w`；禁止 GPR 写回。 | `fuOpType=10`、`rfWen=0`、`ldest=0`。 | decoded uop → Rename。 |
| Rename/ROB | 只映射基址源寄存器；保留 ROB 身份，不分配目的物理寄存器。 | `psrc0=82`、ROB=79、`pdest=0`。 | uop → Dispatch、ROB、LDU IQ。 |
| IssueQueueLdu_1 | 作为 LoadUnit 的整数/软件预取队列接收并延后选择。 | 周期 17875 enq fire；17884 才 `io_ldin` fire。 | deq → LoadUnit_1。 |
| LoadUnit_1 | 将软件预取作为 `io.ldin` 首发；s0 发 `M_PFW`，s1/s2 保留 `isPrefetch`，s2 清异常且判断 cache miss/nack。 | s0 cmd=3、s1 `isPrefetch=1`、s2 `tlb_hit=1/miss=1/nack=0`。 | `io.dcache.req` → MemBlock L1D load port。 |
| StoreUnit/StorePipe | 本条指令不走普通后端 StoreUnit 地址/数据发射；DCache 的 store-prefetch 逻辑以 `source=3,cmd=M_PFW` 定义写预取 miss 处理。 | 目标 PC 在 LoadUnit_1 而非可识别的 StoreUnit issue PC 流；DCache/MQ 中出现 `source=3,cmd=3`。 | 写预取 miss 信息 → MissQueue。 |
| MemBlock | 直连 LDU DCache 接口，并把 DCache ready 反馈给 LoadUnit 发射条件。 | `LoadUnit_1.io_dcache_req.valid && ready`。 | LDU → `dcache.io.lsu.load(1)`。 |
| DCache/MissQueue | 为冷 line 创建 entry 2；把虚拟 byte 地址转换为 line 地址；等待 refill/hint 后回送 main pipe。 | entry2 `0x80001700/source=3/cmd=3`；17934 arbiter fire。 | MissQueue entry 2 → main pipe。 |

因此，昆明湖对 `PREFETCH.W` 的核心实现不是“执行一条普通 store”，而是：**Decode 标记为软件写预取 → LDU 整数发射端计算地址并携带 `M_PFW` → MemBlock 送入 L1D load port → DCache/MissQueue 以 prefetch-write source 建立并处理 cold-line miss**。这解释了为何它既没有 GPR 写回，也能在后续真实 store 前提前把目标 line 送进缓存系统。
