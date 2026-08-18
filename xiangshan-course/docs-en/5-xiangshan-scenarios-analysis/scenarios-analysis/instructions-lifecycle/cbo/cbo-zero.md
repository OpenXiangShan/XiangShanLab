# 香山昆明湖执行 CBO Zero 指令的流程分析

## CBO Zero 指令介绍

### 这条指令是什么

`cbo.zero`（Cache Block Zero）是 RISC-V `Zicboz`（Cache-Block Zero）扩展定义的缓存块操作（Cache Block Operation，CBO）指令。它的目标不是一个通用寄存器，也不产生返回值；指令唯一的操作数是内存地址。汇编中通常写作 `cbo.zero 0(a0)`，其中 `a0` 对应指令编码中的 `rs1`。该指令使用 `rs1` 中的有效地址定位目标缓存块，并把该地址所属的**整个缓存块**写为 `0`。

这里的“缓存块”（cache block）是 ISA 定义的操作粒度，而不是某条具体的标量 store 所写的 1、4 或 8 个字节。可以用下面的伪代码理解它的功能；这只描述软件可见结果，并不限制硬件如何实现：

```text
block_base = 包含有效地址 rs1 的缓存块起始地址
for address in [block_base, block_base + CBZ_BLOCK_SIZE):
    Memory[address] = 0
```

`CBZ_BLOCK_SIZE` 表示 `cbo.zero` 的缓存块大小。该大小由实现决定，软件不能假定它一定等于某种特定缓存的 cache line 大小，也不应将其写死为 64 B。操作系统、运行时或固件应通过执行环境提供的发现机制获取该值；随后，编译器库函数或手写汇编才能据此把一段内存拆分为“首部未对齐部分、若干完整缓存块、尾部未对齐部分”。汇编中的 `offset` 可以省略；如果写出，则必须为 `0`，因为该指令只允许零偏移的间接寻址。

`cbo.zero` 与 `cbo.clean`、`cbo.flush`、`cbo.inval` 等缓存管理指令同属 CBO 家族，但目的不同：后几类指令管理已有缓存数据的写回或失效，`cbo.zero` 则直接把一个内存块初始化为零。它不等价于“把缓存行失效”，也不承诺把该块立即写回主存；若软件还需要满足 DMA、非一致设备或跨层缓存维护的可见性要求，仍须采用相应的缓存管理与同步操作。

### 这条指令会做什么

执行 `cbo.zero` 后，目标缓存块内的全部字节在架构上都成为 `0`。`rs1` 本身不需要按缓存块大小对齐：例如软件传入块中间的任意地址，硬件仍会选择包含该地址的完整缓存块。地址翻译、权限检查和异常报告仍以这条指令给出的有效地址为基础；如果访问发生页故障、存储访问故障或权限异常，报告的故障虚拟地址是 `rs1` 的原始值，而不是硬件向下取整得到的缓存块起始地址。

从 RISC-V 内存模型的角度看，`cbo.zero` 等价于覆盖目标块内字节的一组普通 store，而不是单个不可分割的大 store。这带来三个重要结果：

1. **不是原子清零。** 实现可以按任意顺序、任意粒度执行块内写入。另一个 hart、DMA 引擎或调试单元若与其并发观察同一块，可能看到部分字节已经清零、部分字节仍为旧值；程序不能把该指令作为发布一个完整对象的原子操作。
2. **遵循 store 的排序规则。** `cbo.zero` 的效果受 RISC-V 内存模型约束，可由能排序 store 的 `FENCE` 与其他内存访问建立顺序。若一个 hart 清零并初始化数据后要把对象交给另一个 hart 使用，仍需采用正确的同步协议，例如 release/acquire 原子操作或适当的栅栏；仅执行 `cbo.zero` 不会自动完成跨 hart 的同步。
3. **可能与普通写入交错。** 同一 hart 在该块内继续执行普通 store 时，软件也应通过程序顺序和所需的屏障保证最终内容；特别是在块首尾还需要由普通 store 处理时，不能把 `cbo.zero` 当作无条件覆盖一切写入的屏障。

该指令只适用于允许普通写入的地址。硬件会进行地址翻译与权限检查，目标地址必须具备写权限，且平台内存属性（PMA）必须允许 cache-block zero 访问。`Zicboz` 扩展本身存在并不代表任意特权级都能直接使用该指令：执行环境还可以通过相关控制位限制较低特权级的 CBO zero 访问，使其产生非法指令异常。操作系统因此需要在向用户态暴露该能力前完成扩展发现、块大小发布和权限配置。

从缓存层次结构看，ISA 只规定最终的写零结果，不规定具体在哪一级缓存分配数据、是否需要读取旧数据、何时回写到更低层，或内部采用多少次总线事务。这些均由实现决定。对昆明湖的执行流程分析而言，除了确认指令已被译码为 `cbo.zero` 外，还需要重点观察：地址经过 TLB 翻译后的块地址如何生成、权限与 PMA/PMP 检查在哪一级完成、请求是否进入 DCache，以及缓存未命中或资源冲突时如何在流水线中等待、重放和提交。

### 这条指令对程序执行有什么帮助

清零是一类常见但带宽需求很高的操作：操作系统分配新页时通常需要清除旧数据，内存分配器需要初始化对象，语言运行时也会为新对象、栈帧或垃圾回收区域填零。若只使用标量 store，清零一个缓存块需要发射多条 store 指令；即使使用向量 store，也需要准备向量寄存器、循环控制及处理尾部。对于完整覆盖的缓存块，`cbo.zero` 用一条指令表达“整块初始化为零”的意图，从而减少动态指令数和前端、寄存器及地址生成压力。

这条指令还允许实现针对整块初始化进行优化。例如，硬件可以将目标块以零数据的形式分配到合适的缓存层次中，避免先取得无用的旧数据再逐字写覆盖；后续程序若马上读取或继续修改这个对象，也可能直接命中已分配的缓存块。不过这些都是实现可选的优化，而不是 `Zicboz` 的性能保证：实际速度仍取决于缓存块大小、DCache 资源、TLB 命中率、内存系统带宽、目标数据是否很快被读取，以及与其他访存请求的竞争情况。

软件使用时应把 `cbo.zero` 视作按缓存块工作的快速路径，而不是完全替代 `memset` 的通用指令。一个典型的零初始化流程如下：

1. 查询 `CBZ_BLOCK_SIZE`，并确认当前执行环境允许使用 `Zicboz`。
2. 对起始地址到第一个缓存块边界之间的字节使用普通 store 或普通 `memset`。
3. 对中间每个完整缓存块执行一次 `cbo.zero`。
4. 对末尾不足一个缓存块的字节继续使用普通 store；若数据要交给其他 hart 或设备，再按共享协议加入原子操作、`FENCE` 或缓存维护操作。

这种分段方式既避免误清零范围外的数据，也保留了 `cbo.zero` 在大块初始化中的优势。对于具有非一致 DMA 的设备，`cbo.zero` 不能单独保证设备看到最新的零值；对于并发共享对象，它也不能代替锁、原子变量或发布协议。也就是说，它解决的是“高效产生整块零数据”的问题，而不是“完成所有一致性、可见性和同步”的问题。

参考资料：[RISC-V Instruction Set Manual, Volume I，CMO Extensions for Base Cache Management Operation ISA（Version 1.0.0）](https://docs.riscv.org/reference/isa/unpriv/cmo.html)。

## 香山昆明湖源代码分析

本节**只依据**昆明湖 Chisel 源码
[`XiangShan/src/main`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main) 进行分析；不使用本节
后面的波形时间点，也不把 `cbo.clean`、`cbo.flush`、`cbo.inval` 的实现混入
`cbo.zero` 的执行路径。源码显示，昆明湖将 `cbo.zero` 实现为一条经 Store Unit、
StoreQueue、StoreBuffer 和 DCache 流动的**整缓存行零写**；它不是 Load Unit 操作，
也不会进入用于 clean/flush/inval 的 Cache-Block-Operation 总线控制通道。

### 源码中的指令身份与译码检查

`LSUOpType` 在
[`package.scala`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/package.scala)
中把 `cbo_zero` 定义为 `0b0111`，并提供 `isCboZero()`；`isCboAll()` 同时覆盖
zero、clean、flush、inval。这个划分很关键：Store Unit 对整个 CBO 家族做“整行
store”标记，而 StoreQueue 的 CMO 总线状态机只以 `isCbo()` 识别 clean/flush/inval，
因此 zero 会在后续路径被明确分流。

Decode 位于
[`backend/decode/DecodeUnit.scala`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala)：

```scala
CBO_ZERO -> XSDecode(
  SrcType.reg, SrcType.DC, SrcType.X,
  FuType.stu, LSUOpType.cbo_zero, SelImm.IMM_S)
```

这组控制字段决定了后端的初始语义。

| 字段 | 源码赋值 | 对流水线的意义 |
|---|---|---|
| `srcType(0)` | `SrcType.reg` | 从 `rs1` 读取基址；目标地址由 Store Unit 计算。 |
| `srcType(1)` | `SrcType.DC` | 第二源操作数不来自普通寄存器读口。 |
| `ldest` / 写回类别 | `SrcType.X` | 不产生架构目的寄存器值；后端仍须回写“已完成”状态给 ROB。 |
| `fuType` | `FuType.stu` | 作为 scalar Store Unit 微操作进入内存调度资源，而不是 Load Unit。 |
| `fuOpType` | `LSUOpType.cbo_zero` | 保存到 uop，供 Store Unit、StoreQueue 对 zero 作精确判定。 |
| 立即数选择 | `SelImm.IMM_S` | 和 Store 类指令共用 S 型立即数通路。 |

同一文件还把 `CBODecode.table` 并入总 `decode_table`，并在 `exceptionII` 中检查
`(io.fromCSR.illegalInst.cboZ || !HasCMO.B) && isCboZero`；虚拟化场景则以
`io.fromCSR.virtualInst.cboZ && isCboZero` 产生虚拟指令异常。CSR 侧的生成逻辑在
[`backend/fu/NewCSR/NewCSR.scala`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala)：
它根据当前特权级及 `menvcfg`、`senvcfg`、`henvcfg` 的 `CBZE` 位驱动
`illegalInst.cboZ` 与 `virtualInst.cboZ`。所以权限不允许时，该指令在 Decode 就带上
异常，而不会作为一次成功的零写继续执行。

### Rename、Dispatch 与调度：复用普通 Store 骨架

在 `backend/rename`、`backend/dispatch/NewDispatch.scala` 和 `backend/issue` 中没有
以 `cbo_zero` 为条件的独立 Rename、Dispatch 或 IssueQueue 实现。原因不是它被忽略，
而是 Decode 已将 `fuType` 定为 `FuType.stu`：Rename 按普通微操作保留 `rs1` 的物理
源寄存器映射、分配 ROB 项；由于没有目的寄存器，不分配需要写回的数据目的寄存器。

Dispatch 随后按 Store 类资源为该 uop 分配 Store Queue 索引 `sqIdx`，并把它路由到
memory scheduler 的 store-address issue queue。`backend/issue/IssueQueue.scala` 的
memory entry 会随 uop 保存 `sqIdx`，dequeue 时再把该索引放回 `deq.bits.common.sqIdx`；
`backend/Backend.scala` 将内存 scheduler 的发射端交给 MemBlock，
`MemBlock.scala` 再把 `issueSta` 输入连接至标量 Store Unit 的 `stin`。因此这一段的
控制依赖与普通 store 相同：等待 `rs1` 就绪、等待 Store Unit 可接收、遇到 redirect
时通过 `robIdx.needFlush` 被取消。

这里有两点语义区别已经由前端控制字段保证：第一，`cbo.zero` 不依赖普通 store 的
`rs2` 写数据；第二，虽然它使用 Store Queue 身份和 Store Unit 发射口，却不能在地址
阶段就宣告执行完成，必须等完整缓存行零写经 StoreBuffer 被 DCache 接收后才能完成。
后一条由 StoreQueue 的专用 `cboZeroStout` 完成通道保证。

### Store Unit：生成地址、写权限翻译与整行标志

实现位于
[`mem/pipeline/StoreUnit.scala`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala)。
它先以
`s0_saddr = s0_stin.src(0) + SignExt(s0_stin.uop.imm(11, 0), VAddrBits)`
计算虚拟地址；随后把 `vaddr`、`fullva`、uop、mask 和 `sqIdx` 组成
`LsPipelineBundle`。这意味着地址翻译、PMP/PMA/页表异常和 redirect 处理都走 Store
流水线，而不绕过 LSU。

`cbo.zero` 在此处的专用控制可概括为：

```scala
val s0_wlineflag = LSUOpType.isCboAll(s0_uop.fuOpType)
val s0_isCbo     = ... && LSUOpType.isCboAll(...)

val s0_mask = Mux(s0_use_flow_rs,
  Mux(s0_isCbo, Fill(VLEN / 8, 1.U(1.W)), ...), ...)

io.tlb.req.bits.cmd := Mux(s0_isCbo_noZero, TlbCmd.read, TlbCmd.write)
```

1. `wlineflag` 对所有 CBO 置位，随 store-address 结果写入 StoreQueue 的地址、虚拟
   地址元数据；它是“该项要整行写”的内部标识。
2. 对 CBO，`s0_mask` 是 `VLEN/8` 个全 1 byte-enable；普通 scalar store 则调用
   `genVWmask128()` 按访问宽度生成掩码。StoreQueue 和 StoreBuffer 会把这个整行意图
   扩展到完整 DCache line。
3. `s0_addr_aligned` 最后的 `|| s0_isCbo` 使 CBO 不走普通 h/w/d store 的地址非对齐
   判定；块内任意地址仍可作为 CBO 的地址输入。
4. `s0_isCbo_noZero` 仅代表 clean/flush/inval。因此 zero 的 TLB 请求为
   `TlbCmd.write`，要求按写访问完成翻译与权限检查；非 zero CBO 用 `TlbCmd.read`。

Store Unit 的 s1 接收 TLB 返回，向 StoreQueue 的 `storeAddrIn` 送出物理地址、异常、
`mask` 与 `wlineflag`；其 store-data 通道同时向 `storeDataIn` 传送 uop。普通 store
会携带 `rs2` 数据，但 zero 的数据值由下一阶段改写为零，不能把无意义的 `rs2` 内容
写进目标行。

### StoreQueue：把一条 CBO Zero 变成一笔完整零写

[`mem/lsqueue/StoreQueue.scala`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala)
是本指令最主要的专用实现位置。

* **记录地址和整行属性。** `storeAddrIn.fire` 时，StoreQueue 将 `paddr`、`vaddr`、
  `mask` 写入地址模块，并把 `bits.wlineflag` 写入 `paddrModule.io.wlineflag` 与
  `vaddrModule.io.wlineflag`。uop（包括 `robIdx`、`sqIdx`、`fuOpType`）也同步保存。
* **强制写零。** `storeDataIn.fire` 的数据写入处使用：

  ```scala
  dataModule.io.data.wdata(i) := Mux(
    io.storeDataIn(i).bits.uop.fuOpType === LSUOpType.cbo_zero,
    0.U,
    Mux(isVec, io.storeDataIn(i).bits.data, genVWdata(...))
  )
  ```

  因此 zero 不会依赖通用 store-data 值；StoreQueue 中该 `sqIdx` 对应的数据被硬件
  固定为零。
* **遵从提交次序后才下送。** StoreQueue 的 commit 逻辑只给已分配、地址/数据有效、
  无须取消的队首 store 置 committed 标记；随后 `dataBuffer` 将已提交项形成
  `DCacheWordReqWithVaddrAndPfFlag`，并把 `wline`、地址、数据与 mask 交给
  `io.sbuffer`。这是 `cbo.zero` 不因乱序发射而提前对缓存可见的关键约束。
* **专用完成等待。** 当一个发往 StoreBuffer 的请求同时满足 `bits.wline` 与
  `bits.vecValid` 时，`isCboZeroToSbVec` 识别到该项；`cboZeroToSb` 锁存原始 uop 和
  `sqIdx`，置位 `cboZeroValid`、`cboZeroWaitFlushSb`。随后
  `io.flushSbuffer.valid := ... || cboZeroFlushSb` 请求排空 StoreBuffer；只有
  `io.flushSbuffer.empty` 后才清除 wait 位，并使 `io.cboZeroStout.valid` 成立。
  `cboZeroStout.fire` 时才将该 SQ 项置 `completed`。源码还断言一次不能同时处理两条
  `cbo.zero`，避免专用锁存寄存器覆盖。

这一状态机将“Store Unit 已算出地址”与“CBO Zero 已完成”分开：后者至少要求该整行
零写离开 StoreBuffer，且 StoreBuffer 已排空，才向 ROB 报告完成。

### StoreBuffer 与 DCache：有整行支持，没有名为 CBO Zero 的 DCache 命令

StoreBuffer 的实现位于
[`mem/sbuffer/Sbuffer.scala`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala)。
它的 `DataWriteReq` 明确定义 `wline: Bool()`，注释为“write full cacheline”。写数据
阵列时，每个 byte 的写使能为：

```scala
line_write_buffer_mask(byte) &&
  (line_write_buffer_offset === word.U) ||
line_write_buffer_wline
```

故 `wline=1` 覆盖行内的每个 word、每个 byte；StoreQueue 给 zero 的数据为零，所以
StoreBuffer 最终形成“全字节 mask + 全零 line data”。源码的 difftest 断言也明确
`wline only supports whole zero write now`，说明该整行写能力当前就是为完整零写约束的。

MemBlock 在
[`mem/MemBlock.scala`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala)
中将 `sbuffer.io.dcache` 直接连接到 `dcache.io.lsu.store`。DCache 入口的
[`cache/dcache/DCacheWrapper.scala`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala)
定义 `DCacheLineReq`：它只有常规的 `cmd`、`vaddr`、`addr`、整行 `data` 和整行
`mask` 字段，没有 `cbo_zero` opcode。`DCacheWrapper` 将该请求接入
`mainPipe.io.store_req`；`cache/dcache/mainpipe/MainPipe.scala` 再以
`convertStoreReq()` 转为普通 `MainPipeReq`，与 probe、refill、atomic 请求仲裁，并用
一般 store 的 hit/miss、数据阵列写入和 MissQueue 路径处理。

因此 DCache 对 `cbo.zero` 的“特殊支持”是由上游提供的**完整行全零数据和全掩码**来
实现的，而不是在 MainPipe 中另写一个 CBO Zero 状态机。命中时它是一次普通的整行
cache 写；未命中或需要写权限时，也复用普通 store 的 MissQueue/一致性取得路径。源码
中没有从 `cbo_zero` 直接生成 TileLink `CacheBlockOperation` 的连接。

### 与 clean/flush/inval 的缓存控制总线路径的区别

为了避免混淆，需要把 zero 与其他 CBO 分开。StoreQueue 的
`deqCanDoCbo` 使用 `LSUOpType.isCbo(...)`，而 `isCbo()` 不包含 `cbo_zero`；因此
只有 clean/flush/inval 会经 `io.cmoOpReq` 送入 DCache。MemBlock 把该接口连接为：

```text
StoreQueue.cmoOpReq / cmoOpResp
    ↕
DCacheWrapper.cmoOpReq / cmoOpResp
    ↕
MissQueue.CMOUnit
```

`cache/dcache/mainpipe/MissQueue.scala` 中的 `CMOUnit` 把 CMO 请求锁存后，通过
`edge.CacheBlockOperation(...)` 构造 TileLink A 通道请求，等待 D 通道响应，再返回
`CMOResp` 给 LSQ。这是 clean/flush/inval 的专用 Cache-Block-Operation 总线实现，
不是 `cbo.zero` 的路径。对 zero 而言，StoreQueue 走的是前述 `wline` → StoreBuffer
→ `DCacheLineReq` 普通写请求；DCache 或下级一致性层是否需要取得写权限、处理其他
缓存副本，由普通写请求的缓存一致性机制决定，而非 zero 专用 CMO 消息。

### 完成回写、Commit 与 Retire

`cboZeroStout` 是 StoreQueue 向后端声明 zero 已完成的唯一专用端口。MemBlock 将
`lsq.io.cboZeroStout` 与 MMIO store 回写复用为 `sqOtherStout`，但对 zero 给更高优先级，
再通过 `stOut(0)` 导出；源码还断言 MMIO 回写与 CBO Zero 回写不能同时有效。
Backend 将 MemBlock 的 `writeBack` 接到统一 bypass/writeback 网络；对该指令，
`uop.rfWen` 为假，所以它不写整数寄存器文件，但 writeback 仍携带同一个 `robIdx`，
让 ROB 更新完成状态。

ROB 的实现位于
[`backend/rob/Rob.scala`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala)。
入队时，`FuType.isStore(req.bits.fuType)` 使该项的 `stdWritebacked` 初值为假；当带有
匹配 `robIdx` 的 store 回写到来，`canStdWbSeq` 置位，ROB 将该项
`stdWritebacked := true.B`，并按写回数量递减 `uopNum`。所以 `cboZeroStout.fire` 既
完成 StoreQueue 项，也提供 ROB 等待的 store 标准完成事件。

ROB 只会从队首选择所有完成、未异常且未被 redirect 取消的项提交；当该项到达队首并
满足提交条件时，Commit 端口把它作为 store 提交。StoreQueue 接收 ROB 的提交边界，
其 `commitVec` 只对已分配、地址/数据齐备、未取消的项置位，从而把该项正式标记为
committed。此时 CBO Zero 的专用状态机已经保证零写已通过 StoreBuffer 并得到完成回写，
故 ROB 可以使该项 Retire，推进 ROB dequeue 指针。它没有寄存器结果、没有 Load Unit
响应；其架构可见结果完全是目标 cache line 的零数据以及该 store 类 uop 的正常退休。

## CBO Zero 演示程序

### 演示程序 C 代码

演示程序源码位于
[`cbo_zero.c`](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_zero/cbo_zero.c)。程序定义了
一个 **64 B 对齐且恰好 64 B 大小**的 `volatile` 目标块；这使一次 `cbo.zero` 正好
覆盖该数组的 8 个 64-bit 元素。内联汇编的 `memory` clobber 防止编译器把调用两侧的
内存访问跨越该指令重排。

```c
#define CBO_ZERO_BLOCK_BYTES 64
#define WORD_COUNT (CBO_ZERO_BLOCK_BYTES / sizeof(uint64_t))

static volatile uint64_t demo_block[WORD_COUNT]
    __attribute__((aligned(CBO_ZERO_BLOCK_BYTES)));

static inline void cbo_zero(void *address) {
  __asm__ volatile("cbo.zero 0(%0)" : : "r"(address) : "memory");
}
```

`main()` 的测试步骤如下：

```c
const uint64_t seed = 0x1122334455667700ULL;

fill_block(seed);                     // word[i] = seed + i
// 打印 word[0]、word[7] 和清零前校验和

cbo_zero((void *)demo_block);         // 清零目标 64 B cache block
// 读取并打印 word[0]、word[7]，统计全部 8 个字中的非零项

for (int index = 0; index < WORD_COUNT; index++) {
  demo_block[index] = 0xa500000000000000ULL + (uint64_t)index;
}
// 再次读取并打印，以验证清零后普通 store 仍然可用
```

### 演示程序反汇编结果

已编译 ELF 为
[`cbo_zero-riscv64-xs.elf`](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_zero/build/cbo_zero-riscv64-xs.elf)。
与场景直接相关的指令片段如下；`s0` 在 `0x80000168` 被设置为
`demo_block=0x800017c0`。

```text
80000168: 00001417  auipc  s0,0x1
8000016c: 65840413  addi   s0,s0,1624  # 0x800017c0 <demo_block>

# 循环写入 seed + index，共 8 个 64-bit store
8000017c: 20876733  sh3add a4,a4,s0
80000180: e314      c.sd   a3,0(a4)
80000182: 0785      c.addi a5,1
80000184: feb798e3  bne    a5,a1,0x80000174

# 打印清零前数据后执行目标指令
800001ac: 0044200f  cbo.zero (s0)

# 清零后读取块首尾，并遍历 8 个元素统计非零项
800001b0: 600c      c.ld   a1,0(s0)
800001b2: 7c10      c.ld   a2,56(s0)
800001ba: 2087e733  sh3add a4,a5,s0
800001be: 6318      c.ld   a4,0(a4)
800001c2: c311      c.beqz a4,0x800001c6
800001c4: 2685      c.addi a3,1

# 最后重新向 8 个元素写入 0xa5... + index
800001e8: 20876733  sh3add a4,a4,s0
800001ec: e31c      c.sd   a5,0(a4)
```

其中目标指令编码 `0x0044200f` 与波形提交端口的 `instr` 完全相同，且其执行地址
`0x800001ac` 是后续波形分析的唯一 PC 锚点。

### 测试逻辑与预期行为

该程序不是只检查一两个字是否变为零，而是构造了“**写入非零模式 → CBO Zero →
完整块验证 → 再次普通写入**”的闭环场景：

1. **建立前置状态。** `fill_block()` 依次写入 `0x1122334455667700` 至
   `0x1122334455667707`；因此块首尾字都为非零，且预期校验和为
   `0x89119a22ab33b81c`。
2. **执行目标操作。** `cbo.zero 0(s0)` 以 `demo_block` 地址定位一个完整 64 B
   cache block。因为本程序的对象本身已按 64 B 对齐，待清零范围精确为
   `0x800017c0` 至 `0x800017ff`。
3. **验证清零效果。** 程序读取 `word[0]` 与 `word[7]`，并遍历全部 8 个 64-bit
   元素统计 `nonzero_words`。预期为 `word[0]=0`、`word[7]=0`、
   `nonzero_words=0`；这同时验证块首、块尾和中间全部元素均已被清零。
4. **验证后续可写性。** 程序再次逐字写入 `0xa500000000000000` 至
   `0xa500000000000007`，预期首尾字分别为 `0xa500000000000000` 与
   `0xa500000000000007`，校验和为 `0x280000000000001c`。这排除了 CBO Zero 仅仅
   让读回路径返回零、却破坏后续普通 store 的情况。

在本次波形仿真中，UART 输出与上述预期完全一致：

```text
before cbo.zero: word[0]=0x1122334455667700 word[7]=0x1122334455667707 checksum=0x89119a22ab33b81c
after cbo.zero: word[0]=0x0 word[7]=0x0 nonzero_words=0
after post-zero stores: word[0]=0xa500000000000000 word[7]=0xa500000000000007 checksum=0x280000000000001c
```

## 波形图分析

### 方法、对象与结论

本节使用 `/home/yanyusong/wavekit` 开源库中的 `wavekit.FstReader` 解析
`/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-11-31-16.fst`；所有值均在
`TOP.clock` **上升沿**采样。目标核层级为
`TOP.SimTop.cpu.l_soc.core_with_l2.core`。反汇编的目标指令和波形提交记录一致：

```text
0x800001ac: 0044200f  cbo.zero (s0)
```

`s0` 指向演示块 `0x800017c0`，该块为 64 B 对齐。目标指令在波形中以
`ROB=26`、`SQ=7` 贯穿 Rename、Dispatch、store-address 调度器、StoreQueue 专用
CBO 状态机和专用写回通道。最终 DCache 接收到地址 `0x800017c0`、掩码全 1、
512-bit 数据全 0 的整行写请求；随后该指令正常提交。因此，本实现不是把
`cbo.zero` 当作 Load Unit 操作，也不是向 DCache 发送一个单独命名为 CBO 的接口；
而是译码为 Store Unit 微操作，经 StoreQueue 生成 `wline` 的整行零写。

### 全局时间线

| cycle / time(ps) | 阶段与信号 | `valid/ready/fire` 或状态 | 目标身份与关键值 | 含义 |
|---|---|---|---|---|
| 25635 / 51270 | `decode.decoders_3` | 本 lane 的显式 `valid/ready` 未转储 | `pc=0x800001ac`，`instr=0x0044200f`，`fuType=0x10000`，`fuOp=0x7` | Decode 将该指令识别为 Store Unit 的 CBO Zero。 |
| 25636 / 51272 | `rename.io_in_3` → `rename.io_out_3` | 两侧均为 `1/1/1` | `lsrc0=8(s0)`，`ldest=0`，`rfWen=0`；输出 `robIdx=26` | 无目的寄存器分配；Rename 只保留源寄存器映射并分配 ROB 项。 |
| 25637 / 51274 | `dispatch.io_fromRename_3` | `1/1/1` | `ROB=26`，`fuType=0x10000`，`fuOp=0x7` | Dispatch 接收该 uop，无反压。 |
| 25637 / 51274 | `dispatch.io_enqRob_req_3`、`io_toIssueQueues_22` | 两者 `valid=1`；IssueQueue 接口 `ready=1` | `SQ=7` | 同拍进入 ROB 和 store-address Issue Queue。 |
| 25638 / 51276 | `IssueQueueStaMou_1.entries.enqEntries_0` | `valid=1` | `ROB=26`，`SQ=7`，`fuOp=0x7` | uop 已落入 store-address 队列。 |
| 25641 / 51282 | `IssueQueueStaMou_1.io_deqDelay_0` | `1/1/1`，`isFirstIssue=1` | `ROB=26`，`SQ=7`，`fuOp=0x7` | 调度器选择并发射本指令。 |
| 25641 / 51282 | `inner_lsq.io_std_storeDataIn_1` → `storeQueue.dataModule` | `valid=1`，`wen=1` | `SQ=7`，输入/写入数据均为 128-bit `0` | Store Unit 的 store-data 阶段将 CBO Zero 的数据写进 SQ#7。 |
| 26533 / 53066 | `StoreQueue.cboZeroToSb`、`io_sbuffer_0` | `cboZeroToSb=1`；LSQ→SB 为 `1/1/1` | `addr=vaddr=0x800017c0`，`mask=0xffff`，`vecValid=1`，`wline=1`，128-bit data 为 0 | StoreQueue 将已完成的 CBO 转成整行 StoreBuffer 请求。 |
| 26534–26548 / 53068–53096 | `cboZeroValid`、`cboZeroWaitFlushSb` | 两者保持 1，末尾 wait 位清零 | 锁存 `ROB=26`、`SQ=7` | CBO 专用状态机等待 StoreBuffer 排空/flush 条件。 |
| 26537 / 53074 | `inner_sbuffer.io_dcache_req` → `inner_dcache.io_lsu_store_req` | 两个边界均为 `1/1/1` | `addr=vaddr=0x800017c0`，`mask=0xffffffffffffffff`，512-bit data 全 0 | StoreBuffer 向 DCache 发出并被接受的完整 64 B 零写。 |
| 26548 / 53096 | `inner_lsq.io_cboZeroStout` | `1/1/1` | `ROB=26`，`SQ=7`，`rfWen=0`，`flushPipe=0`，异常位全 0 | CBO 专用 StoreQueue 回写进入 MemBlock/ROB 路径。 |
| 26551 / 53102 | `rob.difftest_commit` | `valid=1` | `pc=0x800001ac`，`instr=0x0044200f`，`ROB=26`，`isStore=1`，`isLoad=0`，`rfWen=0` | 正常退休；软件可见的清零操作完成。 |

### Decode：从指令字到 Store Unit CBO 微操作

在 cycle 25635，`decoders_3.io_enq_ctrlFlow_pc` 和
`io_deq_decodedInst_pc` 都为目标 PC，指令字均为 `0x0044200f`。译码结果为：

- `fuType=0x10000`：波形中的 Store Unit 类别；
- `fuOpType=0x7`：`LSUOpType.cbo_zero`；
- `imm=0`：符合 `cbo.zero 0(s0)` 的零偏移；
- `lsrc0=8`：架构源寄存器是 `s0/x8`；`srcType0=1` 表明该操作数来自整数寄存器；
- `ldest=0`、`rfWen=0`：本指令不写整数目的寄存器。

该 lane 的 `io_enq_valid/io_enq_ready` 和 `io_deq_valid/io_deq_ready` 未作为
`decoders_3` 子层级信号转储，故不能用本 lane 的两条线直接计算 `fire`；但下一周期
Rename 输入、输出均 `valid=ready=1`，证明该译码结果已被下游接受，未停留在 Decode。

信号来源和去向为：前端控制流 `io_enq_ctrlFlow` 产生 `pc/instr`，
`DecodeUnit` 产生 `DecodedInst` 的 `fuType/fuOpType/srcType/imm` 字段，随后由
`io_deq_decodedInst` 送至 Rename。

[DecodeUnit.scala:476](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L476)
中的 CBO 译码表给出了该波形值的来源：

```scala
object CBODecode extends DecodeConstants {
  val decodeArray: Array[(BitPat, XSDecodeBase)] = Array(
    CBO_ZERO  -> XSDecode(SrcType.reg, SrcType.DC, SrcType.X,
      FuType.stu, LSUOpType.cbo_zero, SelImm.IMM_S),
```

`LSUOpType.cbo_zero` 的值为 `0b0111`，对应波形的 `fuOpType=0x7`，见
[package.scala:582](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/package.scala#L582)：

```scala
// l1 cache op
// bit encoding: | cbo_zero 01 | size(2bit) 11 |
def cbo_zero  = "b0111".U
def isCboZero(op: UInt): Bool = op(3, 0) === cbo_zero
```

### Rename、Dispatch 与 ROB：身份保持、资源分配和无背压入队

cycle 25636 的 `rename.io_in_3` 与 `rename.io_out_3` 同时为
`valid=1, ready=1`。Rename 输入仍为 `lsrc0=8`、`ldest=0`、`rfWen=0`；输出保持
同一 PC、指令字、`fuType/fuOpType`，并分配 `robIdx.flag=0`、
`robIdx.value=26`。由于该指令无架构目的寄存器，波形中没有以该指令为对象的整数
`pdest` 分配需求；这与 `rfWen=0` 一致。

cycle 25637，`dispatch.io_fromRename_3.valid && ready` 为 1：

1. `io_enqRob_req_3.valid=1`，把带 `ROB=26` 的 uop 送往 ROB；
2. `io_toIssueQueues_22.valid && ready=1`，同一个 uop 被送入 store-address
   Issue Queue；该输出中 `sqIdx.flag=1`、`sqIdx.value=7`，即分配 Store Queue 项 7；
3. `rfWen=0` 保持为 0，因此没有物理整数寄存器写回依赖。

这解释了为什么后续不再仅靠 PC 识别指令：从 Rename 起，波形用 `ROB=26` 和 `SQ=7`
把 StoreQueue、StoreBuffer 和专用回写事件关联为同一条 `cbo.zero`。

Dispatch 对 ROB 的有效请求来源于 `fromRename(i).fire`，见
[NewDispatch.scala:819](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L819)：

```scala
thisCanActualOut := VecInit((0 until RenameWidth).map(i =>
  !blockedByWaitForward(i) && notBlockedByPrevious(i) && io.enqRob.canAccept))
val thisActualOut = (0 until RenameWidth).map(i =>
  io.enqRob.req(i).valid && io.enqRob.canAccept)

io.enqRob.needAlloc(i) := fromRename(i).valid
io.enqRob.req(i).valid := fromRename(i).fire
io.enqRob.req(i).bits := updatedUop(i)
```

本例的 `fromRename_3.fire=1`、Issue Queue `ready=1` 说明该处没有由 ROB 容量、
wait-forward 或 IQ 容量引起的目标指令反压。

### Issue、Store Unit 与 Load Unit

`IssueQueueStaMou_1` 是 store-address 调度队列。cycle 25638 时，
`entries.enqEntries_0.io_commonOut_transEntry.valid=1`，其中仍保留
`ROB=26/SQ=7/fuOp=0x7`；cycle 25641 时，
`io_deqDelay_0.valid && ready=1`，因此 `fire=1`，且 `isFirstIssue=1`。这就是
该 uop 第一次、也是本次观察到的发射：调度器把它交给 Store Unit 的执行管线。

同一 cycle 的 `inner_lsq.io_std_storeDataIn_1` 是 Store Unit 的 store-data
输出进入 LSQ 的边界：`valid=1`、`fuOp=0x7`、`SQ=7`。其输入 `data` 是 128-bit 0，
`storeQueue.dataModule.io_data_wen_1=1`，`waddr_1=7`，`wdata_1` 也是 128-bit 0。
这不是普通源数据碰巧为零：StoreQueue 代码专门以 `fuOpType` 选择 CBO Zero 分支：

[StoreQueue.scala:594](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L594)

```scala
when (io.storeDataIn(i).fire) {
  dataModule.io.data.waddr(i) := stWbIndex
  dataModule.io.data.wdata(i) := Mux(
    io.storeDataIn(i).bits.uop.fuOpType === LSUOpType.cbo_zero,
    0.U,
    Mux(isVec, io.storeDataIn(i).bits.data,
      genVWdata(io.storeDataIn(i).bits.data,
        io.storeDataIn(i).bits.uop.fuOpType(2, 0)))
  )
  dataModule.io.data.wen(i) := true.B
}
```

信号去向由 [LSQWrapper.scala:189](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala#L189) 明确连接：Store Unit 的 `storeDataIn` 进入
`storeQueue.io.storeDataIn`，StoreQueue 的 `sbuffer` 和 `cboZeroStout` 再分别
接回 LSQWrapper 对外端口。

```scala
storeQueue.io.storeAddrIn <> io.sta.storeAddrIn
storeQueue.io.storeDataIn <> io.std.storeDataIn
storeQueue.io.sbuffer     <> io.sbuffer
storeQueue.io.cboZeroStout <> io.cboZeroStout
```

**Load Unit 的特殊结论：** 我使用 wavekit 扫描了三条 Load Unit 的 42 个已转储
uop-PC 信号（`inner_LoadUnit_{0,1,2}`）在 cycle 25630--25650 的值，未发现
`0x800001ac`。这与 Decode 的 `FuType.stu` 和 Dispatch 的 store-address IQ 路径一致：
本条 CBO Zero 不经过 Load Unit、不分配 LQ、不产生 load response、load replay 或
load-to-store forwarding。演示程序中 *CBO 之后用于验证的普通读* 会使用 Load Unit，
但它们是不同 PC、不同 ROB 项，不能混入本指令的路径。

### MemBlock、StoreQueue 专用 CBO 状态机和 StoreBuffer

从 cycle 25641 的 StoreQueue 数据写入到 cycle 26533 的 `cboZeroToSb` 脉冲相隔
892 cycles。该段时间说明 SQ#7 已保存结果但尚未进入 StoreBuffer；波形没有同时导出
足够的所有更老 ROB/SQ 项、head 指针和仲裁选择信号，因此不能把这段驻留时间归因于
“CBO Zero 自身 cache miss”或某一个确定的资源阻塞。

当该项可下发时，StoreQueue 的 CBO 专用寄存器给出精确的完成协议：

- cycle 26533：`cboZeroToSb=1`；
- cycle 26534：`cboZeroValid=1`、`cboZeroWaitFlushSb=1`，并锁存
  `cboZeroRobIdx=26`、`cboZeroSqIdx=7`；
- cycle 26534：`cboZeroFlushSb_next_r=1` 一个周期，要求 StoreBuffer drain；
- cycle 26548：`cboZeroWaitFlushSb` 由 1 变 0；
- cycle 26548：专用 `io_cboZeroStout.valid && ready=1`，完成对 ROB 的回写；
- cycle 26549：`cboZeroValid` 清零。

cycle 26533 的 `inner_lsq.io_sbuffer_0` 为 `valid=ready=1`，并携带：

```text
addr = vaddr = 0x800017c0
mask = 0xffff                 # 128-bit StoreBuffer 输入的 16 个字节全有效
data = 0x00000000000000000000000000000000
vecValid = 1
wline = 1
```

`vecValid=1 && wline=1` 是普通标量 store 所没有的关键控制组合：它要求
StoreBuffer 把该操作扩展/组织为整条 cache line 的零写，而不是只按 16 B 输入宽度
提交。StoreQueue 同时禁止两个 CBO Zero 并行进入该流程，并以
`cboZeroValid/cboZeroWaitFlushSb` 保证在 StoreBuffer 排空前不向 ROB 宣告完成。

[StoreQueue.scala:989](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L989)

```scala
val isCboZeroToSbVec = (0 until EnsbufferWidth).map { i =>
  io.sbuffer(i).fire && io.sbuffer(i).bits.vecValid && io.sbuffer(i).bits.wline &&
  allocated(dataBuffer.io.deq(i).bits.sqPtr.value) &&
  memBackTypeMM(dataBuffer.io.deq(i).bits.sqPtr.value)
}
val cboZeroToSb = isCboZeroToSbVec.reduce(_ || _)
val cboZeroFlushSb = GatedRegNext(cboZeroToSb)
val cboZeroUop = RegEnable(PriorityMux(isCboZeroToSbVec,
  dataBuffer.io.deq.map(x => uop(x.bits.sqPtr.value))), cboZeroToSb)
val cboZeroSqIdx = RegEnable(PriorityMux(isCboZeroToSbVec,
  dataBuffer.io.deq.map(_.bits.sqPtr)), cboZeroToSb)
```

[StoreQueue.scala:1003](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1003)

```scala
when (cboZeroToSb) {
  cboZeroValid       := true.B
  cboZeroWaitFlushSb := true.B
}

io.flushSbuffer.valid := deqCanDoCbo && !cboFlushedSb &&
  (mmioState === s_req) && !io.flushSbuffer.empty || cboZeroFlushSb
```

### Cache：StoreBuffer 将 CBO 表示为整行零写，DCache 接收该请求

StoreBuffer 在 cycle 26537 的 `io_dcache_req` 发起请求，DCache 的
`io_lsu_store_req` 在同一周期 `valid=ready=1` 接收。对目标请求，两个接口的
地址和值完全一致：

```text
addr = vaddr = 0x800017c0
mask = 0xffffffffffffffff     # 64 个字节全有效
data[511:0] 全为 0
```

因此，DCache 接口层并没有额外的 `cboZero` 标志；CBO 语义已经由
`wline=1`、全 64 B mask 和全零 512-bit data 编码成一条普通的完整 cache-line store
request。cycle 26538--26542 的其他 DCache 请求地址为 `0x80009e**/0x80009f80`，
数据并非全零，属于并发背景流量，不应误归因给本条 CBO。

StoreBuffer 内部 FSM 的 Chisel 定义见
[Sbuffer.scala:220](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala#L220)：

```scala
val x_idle :: x_replace :: x_drain_all :: x_drain_sbuffer :: Nil = Enum(4)
def needDrain(state: UInt): Bool = state(1)
val sbuffer_state = RegInit(x_idle)
```

本次重点采样的是 CBO 专用的 `cboZeroWaitFlushSb`，它在 StoreBuffer 请求被接受后、
专用 writeback 前清零；`sbuffer_state` 数值本身未被纳入本次目标相关的标量采样表，
故不对它在该窗口中的具体 enum 值作无证据推断。FSM 的状态转移由
[Sbuffer.scala:552](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala#L552)
控制：

```scala
switch(sbuffer_state) {
  is(x_idle) {
    when(io.flush.valid) { sbuffer_state := x_drain_all }
    .elsewhen(do_uarch_drain) { sbuffer_state := x_drain_sbuffer }
    .elsewhen(do_eviction) { sbuffer_state := x_replace }
  }
  is(x_drain_sbuffer) {
    when(io.flush.valid) { sbuffer_state := x_drain_all }
    .elsewhen(sbuffer_empty) { sbuffer_state := x_idle }
  }
}
```

### `cbo.zero` 的总线与下游 Cache 语义：本例没有“下游失效”命令

这里需要先区分两种常被混淆的动作：

1. **`cbo.zero` 的语义是把一整条 cache line 写成 0。** 它不是
   `cbo.inval`，不要求把 L2/LLC 这类下游 cache line 置为 invalid。
2. **若系统中另一个 L1 持有同一地址的副本，失效动作由 L2/目录的普通一致性协议
   按需发起。** 即为获得写权限而向其他上游 L1 发送 Probe；这不是 CBO Zero 额外
   发出的“向下失效”消息。L2 是一致性点，通常更新/维护目录状态，而不是把自身的
   数据副本失效。

#### 本次 `cbo.zero` 的波形证据

在目标 line 进入 DCache 的窗口 cycle 26530--26555，wavekit 逐项查询了：

- 17 个 `MissQueue.entries_*.io_mem_acquire` 端口；
- 19 个 `WritebackQueue/Release` 对应的 `io_mem_release` 端口；
- StoreQueue、LSQ、DCache 三层 `cmoOpReq.valid`。

结果是：

```text
target address = 0x800017c0
mem_acquire.fire(target address) = 未观察到
mem_release.fire(target address) = 未观察到
cmoOpReq.valid                  = 0（整个目标窗口）
```

相反，唯一与目标地址匹配并完成握手的缓存请求是 cycle 26537 的本地 L1D store：

```text
inner_sbuffer.io_dcache_req.fire = 1
inner_dcache.io_lsu_store_req.fire = 1
addr/vaddr = 0x800017c0
mask       = 0xffffffffffffffff
data       = 512-bit 全 0
```

因此，**在这段波形窗口内，CBO Zero 命中/更新 L1D，未因该操作向 L2 发送
Acquire，也没有因 eviction 向 L2 发送 Release；更没有 CMO invalidation 命令。**
这也符合演示程序先前对同一 64 B 块写入种子数据的访问历史：目标 line 已在当前
L1D 可操作的路径中。该结论只覆盖 cycle 26530--26555；若该脏 line 在更晚时刻
被替换，普通写回路径仍可能通过 Release 将它写回 L2。

#### 为什么 `cbo.zero` 不走 CMO 控制通道

昆明湖把 CBO 操作划分为两类。`cbo_zero=0b0111` 虽属于 `isCboAll`，但
`isCbo` 仅匹配 clean/flush/inval 的编码；代码见
[package.scala:582](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/package.scala#L582)：

```scala
def cbo_zero  = "b0111".U

def isCbo(op: UInt): Bool = op(3, 2) === "b11".U && (op(6, 4) === "b000".U)
def isCboAll(op: UInt): Bool = isCbo(op) || op(3, 0) === cbo_zero
def isCboClean(op: UInt): Bool = isCbo(op) && (op(3, 0) === cbo_clean)
def isCboFlush(op: UInt): Bool = isCbo(op) && (op(3, 0) === cbo_flush)
def isCboInval(op: UInt): Bool = isCbo(op) && (op(3, 0) === cbo_inval)
```

Store Unit 对所有 CBO 设置 `wlineflag`；所以 Zero 被带到 StoreQueue 的整行 store
数据路径。见 [StoreUnit.scala:117](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala#L117)：

```scala
val s0_wlineflag = Mux(s0_use_flow_rs,
  LSUOpType.isCboAll(s0_uop.fuOpType), false.B)
val s0_isCbo = s0_use_flow_rs && LSUOpType.isCboAll(s0_stin.uop.fuOpType)
val s0_isCbo_noZero = s0_use_flow_rs && LSUOpType.isCbo(s0_stin.uop.fuOpType)

s0_out.wlineflag := s0_wlineflag
```

而 StoreQueue 只有 `isCbo(...)` 为真时才允许 `cmoOpReq` 有效；本例的
`fuOp=0x7` 是 `cbo_zero`，故 `deqCanDoCbo=0`，不会到达该接口。见
[StoreQueue.scala:985](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L985)：

```scala
val deqCanDoCbo = GatedRegNext(
  LSUOpType.isCbo(uop(deqPtr).fuOpType) && allocated(deqPtr) &&
  addrvalid(deqPtr) && !hasException(deqPtr)
) && memBackTypeMM(deqPtr)

io.cmoOpReq.valid := deqCanDoCbo && cboFlushedSb &&
  (mmioState === s_req) && !io.wfi.wfiReq
io.cmoOpReq.bits.opcode  := cmoOpCode
io.cmoOpReq.bits.address := cboMmioPAddr
```

这正好解释了波形中 `cmoOpReq.valid=0`、但 `wline=1` 与全零 DCache 请求同时成立的
组合：**Zero 走 data path，非 Zero CBO 才走 CMO control path。**

#### 若执行的是 `cbo.clean`、`cbo.flush` 或 `cbo.inval`，如何传到总线

这三类指令的 `fuOpType` 满足 `isCbo`，因此会经 StoreQueue 的
`cmoOpReq(opcode,address)` 进入 DCache。DCache 再把它连到 MissQueue 的 CMO 单元：

[DCacheWrapper.scala:1532](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L1532)

```scala
missReqArb.io.out <> missQueue.io.req
io.cmoOpReq  <> missQueue.io.cmo_req
io.cmoOpResp <> missQueue.io.cmo_resp
```

MissQueue 把 CMO 请求给 `cmo_unit`，并把 CMO 单元的 TileLink A-channel 请求纳入
`mem_acquire` 仲裁；DCacheWrapper 再将 `mem_acquire` 接到 `bus.a`。对应代码为
[MissQueue.scala:1230](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L1230)：

```scala
cmo_unit.io.req <> io.cmo_req
io.cmo_resp <> cmo_unit.io.resp_to_lsq

when (io.mem_grant.valid && io.mem_grant.bits.opcode === TLMessages.CBOAck) {
  cmo_unit.io.resp_chanD <> io.mem_grant
}

val acquire_sources = Seq(cmo_unit.io.req_chanA, acquire_from_pipereg) ++
  entries.map(_.io.mem_acquire)
TLArbiter.lowest(edge, io.mem_acquire, acquire_sources:_*)
```

以及 [DCacheWrapper.scala:1550](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L1550)：

```scala
bus.a <> missQueue.io.mem_acquire
bus.e <> missQueue.io.mem_finish

missQueue.io.probe.req.valid := bus.b.valid
missQueue.io.probe.req.bits.addr := bus.b.bits.address

bus.c <> wb.io.mem_release
```

所以非 Zero CBO 的完整控制链是：

```text
StoreQueue.cmoOpReq
  -> DCache.io.cmoOpReq
  -> MissQueue.cmo_req
  -> CMOUnit.req_chanA
  -> MissQueue.mem_acquire
  -> DCache TileLink bus.a
  -> L2/LLC CMO 处理
  -> TileLink D-channel CBOAck
  -> CMOUnit.resp_to_lsq
  -> StoreQueue.cmoOpResp
  -> ROB writeback / commit
```

其中真正面向其他 L1 cache 的失效/Probe 由 L2/LLC 根据 CMO opcode 和目录状态发起，
经 TileLink `bus.b` 返回 L1D；DCacheWrapper 将它交给 `missQueue.io.probe.req`。因此，
应把“失效其他缓存副本”理解为**下游目录发起的 coherence Probe**，而不是 L1D 的
`cbo.zero` 向下广播 invalidate。

本演示程序只有 `cbo.zero`，所以没有 `CBOAck`、`cmoOpReq.fire` 或目标地址的
CMO A-channel 事务可供观察。若要验证 clean/flush/inval 的下游失效，应分别运行
`cbo-clean`、`cbo-flush`、`cbo-inval` 镜像，并以 `cmoOpReq.opcode/address`、
`mem_acquire`、D-channel `CBOAck` 和 `bus.b` Probe 为联合锚点。

### 专用回写、Commit、异常与 Redirect

StoreQueue 在 `cboZeroWaitFlushSb` 解除后，通过 `io.cboZeroStout` 回写：cycle 26548
该接口 `valid=ready=1`，`ROB=26`、`SQ=7`、`rfWen=0`、`flushPipe=0`；24 个已转储
`exceptionVec` 位均为 0。该数据在 MemBlock 中优先于 MMIO store 回写，随后连接至
普通 store writeback 端口：

[StoreQueue.scala:1074](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1074)

```scala
io.cboZeroStout.valid := cboZeroValid && !cboZeroWaitFlushSb
io.cboZeroStout.bits.uop := cboZeroUop
io.cboZeroStout.bits.uop.sqIdx := cboZeroSqIdx

when (cboZeroWaitFlushSb && io.flushSbuffer.empty) {
  cboZeroWaitFlushSb := false.B
}
when (io.cboZeroStout.fire) {
  completed(cboZeroSqIdx.value) := true.B
  cboZeroValid := false.B
}
```

[MemBlock.scala:1361](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L1361)

```scala
val sqOtherStout = WireInit(0.U.asTypeOf(DecoupledIO(new MemExuOutput)))
sqOtherStout.valid := lsq.io.mmioStout.valid || lsq.io.cboZeroStout.valid
sqOtherStout.bits := Mux(lsq.io.cboZeroStout.valid,
  lsq.io.cboZeroStout.bits, lsq.io.mmioStout.bits)
assert(!(lsq.io.mmioStout.valid && lsq.io.cboZeroStout.valid))
```

cycle 26551，ROB 的 difftest commit 端口记录：`valid=1`、
`pc=0x800001ac`、`instr=0x0044200f`、`ROB=26`、`isStore=1`、`isLoad=0`、
`rfWen=0`。这与 CBO 是无目的寄存器的 store 类微操作完全一致。

本指令的 redirect/异常结论完全基于波形：专用 writeback 的异常位全 0，
`flushPipe=0`，并且随后正常 commit；故没有由 `ROB=26` 这条 CBO Zero 产生的异常
redirect、load replay、store-load violation recovery 或前端 flush。注意这不排除同一
长仿真中其他指令的全局 redirect；本结论只覆盖带 `ROB=26/SQ=7` 身份的目标 uop。

`--no-diff` 运行没有启用外部参考模型比较；本波形仍转储了 commit 的 PC、指令字、
load/store 分类和寄存器写使能。CSR、特权级、trap cause/tval/epc、向量/浮点寄存器
状态没有作为该目标提交的可用标量记录转储，且本指令不写这些状态。

### Bubble / 性能影响分析

| 区间 | 接口 | 观察到的值 | 归因结论 |
|---|---|---|---|
| 25636 | Rename 输入/输出 | 两侧 `valid=ready=1` | 无 Rename 或物理寄存器分配背压。 |
| 25637 | `fromRename_3`、`toIssueQueues_22` | 均为 `valid=ready=1` | 无 Dispatch/ROB/IQ 接收背压。 |
| 25638--25641 | store-address IQ | 25638 入队，25641 `deqDelay_0.fire=1` | 等待 3 cycles 后首次发射；波形未提供可归因的选择竞争/源操作数阻塞字段，不能断言具体原因。 |
| 25641--26533 | SQ#7 驻留 | 25641 写入全零数据，26533 才 `cboZeroToSb=1` | 892-cycle 驻留；缺少所有更老项与 SQ 仲裁状态的联合证据，不能归因于 CBO 自身或 DCache miss。 |
| 26533 | LSQ→StoreBuffer | `valid=ready=1`、`wline=1` | 无 StoreBuffer 输入背压。 |
| 26537 | StoreBuffer→DCache | `valid=ready=1` | DCache 当拍接受完整零写；没有该请求的 nack/replay 证据。 |
| 26534--26548 | CBO 专用完成状态 | `cboZeroWaitFlushSb=1` | 15 cycles 等待 StoreBuffer flush/empty 条件；这是代码和波形共同可见的 CBO 特有等待。 |

若要缩短本例中最显著的时间段，应首先在后续实验中同时转储 StoreQueue deq 指针、
ROB head、older-store pending、StoreBuffer empty 和 DCache miss/replay 原因，而不是
直接优化 CBO decode 或 Issue Queue：前端到首次发射没有目标背压，DCache 也当拍接收
了整行零写；目前唯一有直接波形因果关系的等待是
`cboZeroWaitFlushSb` 对 StoreBuffer 排空条件的等待。

### 信号来源与去向汇总

| 生产者 | 信号 | 消费者 | 本例值 / 作用 |
|---|---|---|---|
| 前端控制流 | `io_enq_ctrlFlow.pc/instr` | `DecodeUnit` | `0x800001ac / 0x0044200f`，提供待译码指令。 |
| `DecodeUnit` | `DecodedInst.fuType/fuOpType/srcType/imm` | Rename | `stu/cbo_zero/reg/0`，决定进入 store-address 路径。 |
| Rename | `robIdx`、保留的 uop 字段 | Dispatch/ROB | `ROB=26`，用于 OOO 生命周期追踪。 |
| Dispatch/LSQ 分配 | `sqIdx` | Store-address IQ、StoreQueue | `SQ=7`，定位 CBO 的数据和地址条目。 |
| Store-address IQ | `io_deqDelay_0` | Store Unit / LSQ store-data | `fire` 于 cycle 25641，开始执行。 |
| Store Unit | `io_std_storeDataIn_1` | StoreQueue data module | `fuOp=7` 触发硬件数据强制为 0。 |
| StoreQueue | `io_sbuffer_0`、`cboZero*` 状态 | StoreBuffer、专用 writeback | `wline=1/vecValid=1`，把 SQ#7 表示成整行零写并等待 flush。 |
| StoreBuffer | `io_dcache_req` | DCache `io_lsu_store_req` | 64 B full-mask、512-bit 全零请求。 |
| StoreQueue | `io_cboZeroStout` | MemBlock/ROB | CBO 专用完成回写，`ROB=26`、无异常。 |
| ROB | `difftest_commit` | 架构提交/仿真观察点 | 正确提交目标 PC/指令，无寄存器写回。 |

综上，波形与昆明湖 Chisel 实现形成了完整闭环：`Decode → Rename → Dispatch →
Store-address Issue → StoreQueue 全零数据 → CBO 专用状态机 → StoreBuffer 整行零写 →
DCache 接收 → CBO 专用回写 → ROB Commit`。 
