# 香山微架构篇题库
香山微架构篇题库

1. **IQ 唤醒机制**

• 香山如何区分固定延迟指令（如MUL）与非固定延迟指令（如DIV）的唤醒方式？各依赖什么硬件组件？

• wakeUpQueues 的作用是什么？其延迟参数 lat 如何配置？

• 画出一条MUL指令唤醒其后继ADD指令的时序图（标注发射、唤醒、写回时刻）。

2. **Bypass 网络**

• 香山Bypass网络包含哪几条主要路径？分别连接哪些执行单元？

• 一条ALU指令的结果最快能在哪个流水级被后续指令通过Bypass获取？

• 当多个执行单元同时写回同一物理寄存器时，Bypass如何仲裁？

3. **回写端口与 RegCache**

• 香山后端有多少个回写端口？它们与功能单元的映射关系如何？

• RegCache 的作用是什么？它如何缓解 RegFile 的写端口压力？

• 一条指令写回时，数据是先写入 RegFile 还是 RegCache？后续读操作如何保证读到最新值？

4. **AMO 指令生命周期与内存序约束**

• 在单核且不考虑中断的情况下，一条 AMO 指令和一组 load-arithmetic-store 指令在乱序执行核中有什么微架构差异？为什么后者可能触发内存 replay？

• 对 `0x08e7a7af` 这条指令进行手动译码时，如何确定它是 `amoswap.w`，以及它的 `rs1`、`rs2`、`rd`、`aq`、`rl` 位分别是什么？

• 香山昆明湖的 Decode 阶段会为 `amoswap.w` 生成哪些关键控制信号？这些信号如何对应 `srcType`、`fuType`、`fuOpType`、`rfWen`、`waitForward`、`blockBackward` 和 `canRobCompress`？

• 为什么香山当前会把所有 AMO 指令默认按 `aq/rl` 置位处理？这对前后访存指令的执行顺序提出了什么要求？

• `waitForward` 信号在 AMO 指令中承担什么作用？如果没有该信号，前序 store 和 AMO 之间可能出现什么错误乱序？

• `blockBackward` 信号在 AMO 指令中承担什么作用？如果没有该信号，后序 load 和 AMO 之间可能出现什么错误乱序？

• Decode 代码中 `noSpec` 是如何隐式转换为 `DecodedInst.waitForward` 的？这种字段顺序依赖会带来什么可维护性风险？

• 为什么 AMO、访存、分支、跳转、特权指令等不能与相邻指令共享同一个 ROB 表项？

• 如果一条可能异常的 AMO 指令和一条普通 ALU 指令被 ROB 压缩到同一个表项，发生异常冲刷时会破坏什么体系结构语义？

• 普通 AMO 指令的 `uopSplitType` 为什么保持默认值，而 AMOCAS 指令需要显式设置拆分类型？

• 香山中的逻辑寄存器和物理寄存器在 Rename 阶段如何建立映射？AMO 指令的 `lsrc`、`ldest` 如何转换为 `psrc`、`pdest`？

• 整数物理寄存器空闲列表 `intFreeList` 如何根据 `allocateReq` 和 `PopCount` 分配新的物理寄存器？

• Rename 阶段为什么需要同时维护推测态 `spec_table` 和体系结构态 `arch_table`？AMO 的目的寄存器映射何时从推测态变为体系结构态？

• CompressUnit 如何决定一个微操作是否可以进行 ROB 压缩？AMO 的 `canRobCompress=0` 会如何影响 `needRobFlags` 和 ROB 表项分配？

• Rename 阶段如何根据 `robIdxHead + count(previous lanes that allocate ROB)` 为每个微操作预分配 ROB 表项？

• Dispatch 阶段中 `fromRename(i).ready` 由哪些条件共同决定？`allowDispatch`、`uopBlockByIQ`、`thisCanActualOut`、`lsqCanAccept` 分别负责什么约束？

• 对于 AMO 指令，为什么即使 Dispatch 不直接发起 LSQ 入队请求，`lsqCanAccept` 仍然可能阻塞流水线？

• `blockedByWaitForward` 如何保证带有 `waitForward` 标记的 AMO 在前方 ROB 未清空时不能离开 Dispatch？

• `notBlockedByPrevious` 如何保证带有 `blockBackward` 标记的 AMO 会阻塞同组中更年轻的微操作？

• AMO 入队 ROB 后，ROB 为什么要拉高 `hasWaitForward` 和 `hasBlockBackward`？这两个状态如何影响后续指令入队？

• AMO 指令为什么被标记为非 `interrupt_safe`？如果 AMO 被外部中断随意打断，可能破坏什么执行语义？

• Dispatch 阶段如何根据 `fuType=mou` 为 AMO 选择 STA/MOU 相关发射队列？

• 为什么 Dispatch 阶段不会直接把 AMO 微操作送入 STD Issue Queue？STD 侧微操作是如何从 STA 侧复制或派生出来的？

• BusyTable 如何为 AMO 的源操作数生成 `srcState`？发射队列如何知道 AMO 的地址源操作数和数据源操作数是否已经就绪？

• 调度和发射阶段为什么要把 AMO 拆成 Sta 和 Std 两个微操作？这两个微操作为什么必须共享同一个 ROB、Load Queue 和 Store Queue 表项？

• 如果 AMO 的 Sta 和 Std 微操作使用不同 ROB 表项或不同 LSQ 表项，可能如何破坏原子性？

• AMO 的 Sta 微操作和 Std 微操作分别对应哪些源操作数？在 `amoswap.w a5, a4, (a5)` 中，地址和写入数据分别来自哪个寄存器？

• AMO 进入执行阶段后，`toMemExu` 不同通路分别携带哪些信息？如何从 `rfWen`、`pdest`、`src0` 判断某一路是 Sta 还是 Std？

• 为什么裸机程序没有开启地址翻译时，AMO 访问 DCache 前仍会经过地址翻译相关状态机？

• 香山 L1 DCache 采用 VIPT 结构时，AMO 的虚拟地址、物理地址和 DCache index/tag 之间有什么关系？

• atomicUnit 在真正向 DCache 发起 AMO 请求前为什么必须清空 Store Buffer？

• Store Buffer 的 `flush_valid` 和 `flush_empty` 如何配合，保证 AMO 执行前已有 store 的结果已经反映到 DCache？

• 如果 AMO 执行前 Store Buffer 中仍有同地址或重叠地址写入未完成，会如何破坏 AMO 的 release 语义或原子读改写语义？

• atomicUnit 和 DCache 之间的 `req_valid/req_ready` 握手表示什么？为什么 AMO 请求握手后 DCache ready 会暂时拉低？

• DCache 对 AMO 返回的 `resp_bits_data` 表示新写入的数据还是旧内存数据？该值后续会写回到哪个目的寄存器？

• AMO 执行完成后，结果如何经过旁路网络提前转发给依赖指令？

• AMO 写回阶段中，来自内存执行单元的结果如何经过 `wbDataPath`、`intWbArbiter` 路由到控制块和寄存器写回端口？

• 香山为什么要在写回路径中使用仲裁器，而不是让所有执行单元都直接写物理寄存器堆？

• AMO 写回 RegCache/物理寄存器堆后，为什么仍然不能认为它已经对体系结构状态产生了不可回退的影响？

• Retire 阶段中，ROB 的 commit 信号和 ROB 表项 valid 位如何标志 AMO 指令正式提交并释放 ROB 表项？

• 写回完成但尚未退休的 AMO 指令，在前序指令发生异常或重定向时为什么仍可能被回滚？

5. **重命名快照与误预测恢复**

• 在支持寄存器重命名和分支预测的乱序核中，为什么 Rename 阶段不能只维护一份当前映射表？

• 如果处理器每拍最多重命名 3 条指令、最多维护 8 个快照槽位，硬件应如何支持“同拍多分支申请快照”？

• 当某个分支被证明预测正确时，应释放哪些快照状态；当其被证明误预测时，又应额外释放哪些更年轻状态？

• 如果 Free List 与 Rename Table 的恢复编号不同步，会破坏什么性质？请举例说明可能出现的错误结果。

• 试比较“保存整张映射表”和“保存增量日志”两种恢复思路在时延、面积和实现复杂度上的差异。

6. **Dispatch 到多 Issue Queue 的分流策略**

• 一个 3 发射乱序核若同时拥有 ALU、乘除法、分支、Load/Store 地址、Store Data 等不同执行资源，Dispatch 阶段为什么不能简单地按程序顺序把指令推给单一队列？

• 当一条 ALU 指令可以被多个执行队列接受时，Dispatch 应该如何做负载均衡？轮询、固定优先级和最空队列策略各有什么代价？

• 对于 Store 指令，为什么往往既要占用地址侧资源，又要占用数据侧资源？如果只分配了其中之一，会在后续流水级留下什么隐患？

• 如果更老的一条访存指令因为队列容量不足而卡住，为什么更年轻的访存指令有时也必须一并阻塞？

• 串行化指令或强顺序指令进入 Dispatch 时，为什么需要显式阻止前后指令继续穿越？

7. **ROB、LSQ 与精确提交**

• 为什么访存指令即使已经完成地址计算、拿到数据返回，也不能直接修改体系结构状态，而必须等待 ROB 提交？

• Store Queue 为什么通常在“提交”和“真正写入缓存/内存”之间再插入一层解耦？

• Load Queue 为什么需要感知 Store Queue 中更老 store 的状态？这与前递、内存序和 replay 有什么关系？

• 当处理器发生异常或重定向时，ROB、Load Queue、Store Queue 三者各自最关键的恢复动作分别是什么？

• 试说明“写回完成”和“精确提交完成”不是一回事。若把两者混为一谈，会导致什么类型的错误？

8. **前后端 Redirect 与 Flush 协同**

• 前端预译码发现的重定向与后端执行/提交阶段发起的重定向，在作用范围上通常有什么区别？

• 为什么很多实现里后端 redirect 不仅要刷掉取指队列和发射队列，还要同步清理 MMU、访存地址翻译状态机等“看似独立”的模块？

• 如果只 flush 前端而不恢复 Rename/ROB/LSQ 中的推测状态，会留下哪些不可见但致命的错误状态？

• 为什么 redirect 控制往往需要携带 ROB 位置、是否来自 ROB、是否需要恢复快照等额外元信息，而不是只给一个目标 PC？

• 请画出一次“分支误预测导致恢复”的典型时序，标出至少前端、Rename、Issue Queue、ROB、LSQ 五类模块的动作先后关系。

9. **Booth 乘法器与 Wallace Tree 部分积压缩**

• 设计一个支持 32 位有符号乘法的 Booth2/4/8 乘法器，并比较不同编码方式的部分积数量、面积和关键路径。

• 使用 4:2 compressor 或全加器构建 4/8/16 路 Wallace Tree，将部分积压缩为两行数据，再接入最终进位传播加法器。

• 说明符号扩展、负部分积、最高位溢出和流水线切分的处理方式，给出至少 3 组边界测试。

• 提交 RTL、结构示意图、功能仿真结果和综合后的时序/面积对比，解释设计取舍。

10. **TLB 与 Page Table Walk**

• 设计一个支持多级页表的 TLB 与 Page Table Walker，完成虚拟地址翻译、权限检查、异常返回和 TLB 回填。

• 明确 TLB 命中、未命中、Page Fault、Access Fault、`sfence.vma` 以及地址空间切换时的状态转移和握手时序。

• 说明页表项读取、有效位/权限位检查、超级页处理及并发请求阻塞策略；分析翻译结果如何与 Cache 访问衔接。

• 提交状态机、接口时序图、定向测试和性能分析，至少覆盖命中、冷启动、权限失败、旧映射失效和多次 miss 场景。

11. **访存值预测器**

• 面向乱序核设计一个 Last-Value Predictor、Stride Predictor 或二者融合的访存值预测器，定义预测表项、索引方式、更新时机和替换策略。

• 比较 LVP、2-level LVP/Stride、gDiff、Path-based Stride 和 D-FSM 对重复值、固定步长及路径相关访问模式的适应性。

• 设计预测命中、预测错误、load replay、旁路和提交确认的完整数据通路，保证错误预测不会改变体系结构状态。

• 使用代表性访存程序评估准确率、覆盖率、额外存储开销和 IPC/延迟收益，并分析错误预测带来的性能损失。



12. **CBO 指令执行、权限检查与 StoreQueue 提交（Issue #4702）**

以下 20 个问题及答案均基于本文分析所使用的旧版昆明湖源码；题目保留完整，供微架构学习和 bug 复盘使用。

### Q1. 为什么昆明湖会让 CBO 指令走 Store Unit？

**结论：这里的“Store Unit”特指 Store Address（STA）流水，也就是 `StoreUnit`，不是传递 store data 的 STD 流水。** 昆明湖把 CBO 放进这条路径，是因为 CBO 的微架构需求是“计算一个内存地址、完成地址翻译和权限检查、与 store/LSQ 保持顺序、在 ROB 提交时触发 cache-block 操作”，而不是“读取数据并写回整数寄存器”。STA 正好是这些功能的现成载体。

#### 1. 解码结果已经把 CBO 定义成 store-family 的地址操作

旧版 `DecodeUnit` 对四条 CBO 指令都使用同一个控制形状：一个整数源寄存器、S-type immediate、没有目的寄存器，并选择 `FuType.stu`：

```scala
// src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:473-482
CBO_INVAL -> XSDecode(SrcType.reg, SrcType.DC, SrcType.X,
  FuType.stu, LSUOpType.cbo_inval, SelImm.IMM_S)
```

这表示 CBO 的输入本质上是 `rs1 + imm` 形成的虚拟地址。`FuType.stu` 只表示它属于 store/地址侧的执行族，并不表示该指令的页表或 PMP 检查一定需要写权限。实际的 STA/STD 划分由执行单元配置和 issue 参数完成：

- `src/main/scala/xiangshan/backend/fu/FuConfig.scala:415-459` 中，`StaCfg` 只有一个 `IntData` 地址源，不写回寄存器，并声明 `storeAccessFault`、`storePageFault`、`storeGuestPageFault` 等异常输出；
- 同一处的 `StdCfg` 是数据流水，只接收整数或浮点数据源，延迟为 0，不提供 TLB、PMP 或这些地址异常；
- `src/main/scala/xiangshan/mem/MemBlock.scala:407-412` 分别实例化 `storeUnits`（STA）和 `stdExeUnits`（STD），说明“store unit”在代码中已经被拆成地址和数据两个不同的微结构单元。

所以 CBO 选择的是“store address execution”，而不是“store data execution”。

#### 2. `StoreUnit` 已经包含 CBO 所需的完整地址侧流水

`StoreUnit` 对普通 store 和 CBO 共用地址生成和地址检查的前半段：

```scala
// src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:134-165
val s0_saddr = s0_stin.src(0) + SignExt(s0_stin.uop.imm(11,0), VAddrBits)
val s0_isCbo = s0_use_flow_rs && LSUOpType.isCboAll(s0_stin.uop.fuOpType)
val s0_addr_aligned = ... || s0_isCbo
```

这段代码给出了两个重要的设计事实：CBO 使用和 store 相同的 `rs1 + S-immediate` 地址形成器；同时 CBO 不受普通 store 数据宽度对齐判断的约束。之后的 S0/S1/S2 流水继续完成：

- `StoreUnit.scala:205-223` 发出 DTLB 请求并携带虚拟地址、ROB/PC 等身份信息；
- `StoreUnit.scala:285-324` 锁存 `s1_isCbo`、TLB 响应和 replay/violation 信息；
- `StoreUnit.scala:364-385` 把 TLB 结果转换为 store 类异常向量；
- `StoreUnit.scala:447-477` 接收 PMP 结果并生成 access fault；
- `StoreUnit.scala:510-522` 通过 `lsq_replenish` 把地址、异常和 CBO/uncache 标记交给 LSQ。

这些功能都是“地址侧的内存操作基础设施”。`MemBlock.scala:668-680` 还为 load 和 store address 配置了独立的 DTLB 请求端；`MemBlock.scala:1221-1241` 将 StoreUnit 同时连接到 DCache 的 STA 端、StoreQueue 的 `storeAddrIn`、store DTLB、PMP 和 redirect。新建一个 CBO 单元会重复这整套翻译、权限、replay、flush 和精确异常逻辑，没有微架构收益。

#### 3. CBO 必须进入 StoreQueue 的顺序和提交协议

CBO 的 cache-block 副作用不能在指令刚被发射时就对 cache 可见，它必须像 store 一样遵守 ROB/LSQ 顺序。代码把地址和数据明确分开：`LSQWrapper.scala:79-86` 将 `storeAddrIn` 接到 STA，将 `storeDataIn` 接到 STD。CBO 只需要前者，真正的 CMO 请求由 StoreQueue 在提交阶段处理：

- `StoreQueue.scala:811-819` 把 “Memory mapped IO / other uncached operations / CMO” 作为同一类需要延迟提交的操作；
- `StoreQueue.scala:872` 让 CBO 绕过普通 MMIO store request；
- `StoreQueue.scala:957-1006` 只有 ROB 头部的 CBO、并且 store buffer 已 flush 后，才发出 `cmoOpReq`；
- `StoreQueue.scala:1024-1060` 通过 `mmioStout`/`cboZeroStout` 完成 CMO 的 ROB 写回和流水 flush；
- `StoreQueue.scala:1394-1398` 以 `mmioStout.fire` 和 CBO opcode 产生 difftest 的 CBO 提交事件。

这解释了为什么 CBO 不能简单地放进 LoadUnit：它需要 store buffer drain、ROB-head gating、CMO response 和 StoreQueue writeback，这些状态机都已经存在于 store/uncache 侧。`CBO.ZERO` 也有 store-like 的特殊性，`StoreQueue.scala:583-610` 直接把它的数据定义为 `0.U`，但这仍然是在 StoreQueue 中完成的，并不要求 CBO 经过 STD 的普通数据生产路径。

#### 4. 为什么不是 Load Unit 或 STD？

- `LoadUnit.scala:380-400` 的地址请求默认使用 load/read 语义（`memidx.is_ld`、`is_st=false`），并围绕 load queue、load replay 和数据返回组织流水；`LoadUnit.scala:1750-1767` 的最终输出还包含 load 数据和整数寄存器写回。CBO 没有要返回的软件可见数据，也不应该产生 load writeback。
- `MemBlock.scala:81-87` 中 `Std` 的功能几乎只是把 `src(0)` 数据转发到输出；它没有地址生成、TLB、PMP、触发器或地址异常接口。CBO 解码的第二个源是 `SrcType.DC`，因此它没有需要送入 STD 的 store data。

从微架构分工看，STA 是“地址、权限、顺序和异常的载体”，STD 是“数据的载体”，LoadUnit 是“读数据并返回的载体”。CBO 只需要第一类能力，最终的 cache-block 副作用则由 StoreQueue 的 CMO 状态机完成。

#### 5. 复用 StoreUnit 的边界：执行载体不等于权限语义

旧版 `StoreUnit.scala:205-223` 为 TLB 请求设置了 store 的默认 command。这个默认值是实现复用留下的语义耦合：CBO 借用了 store 的地址流水，但 CBO 的页表/PMP权限检查并不因此变成“必须有写权限”。因此应当把两个问题分开：

1. **走哪条流水？** 走 STA/StoreUnit，以复用地址生成、TLB、PMP、LSQ、ROB 和精确异常基础设施；
2. **这次 TLB/PMP 请求检查什么权限？** 由具体 LSU opcode 决定。普通 store 需要写权限，management CBO 需要按 ISA 进行 read/load 权限检查，`CBO.ZERO` 仍保持其自身的 store-like 语义。

因此，“CBO 走 Store Unit”是一个合理的微架构复用选择；真正的问题是旧代码把“使用 StoreUnit”错误地等同成了“使用 write command”。这也正是该 bug 能够在地址和提交流程都正确的情况下，仍然产生错误 store exception 的根源。

### Q2. CBO 指令从解码到提交的完整执行路径是什么？

旧版源码可以把路径串成一条明确的链：

```text
DecodeUnit
  -> STA issue queue
  -> StoreUnit S0/S1/S2
  -> StoreQueue storeAddrIn
  -> ROB head / CMO state machine
  -> cmoOpReq / cmoOpResp
  -> mmioStout
  -> ROB commit / difftest
```

代码依据是：`DecodeUnit.scala:476-482` 将 CBO 解码为 `FuType.stu`；`NewDispatch.scala:49-52` 明确说明 STD IQ 不直接 dispatch；`Scheduler.scala:380-405` 将 memory issue queue 按 STA、LDU 和 STD 分开；`MemBlock.scala:1229-1241` 把 `issueSta` 接到 `StoreUnit`，并把它连接到 `StoreQueue.storeAddrIn` 和 store DTLB。StoreUnit 在 `StoreUnit.scala:134-164` 形成地址，在 `205-223` 发出 TLB/PMP 相关请求，在 `364-385` 产生地址异常，在 `447-522` 形成最终的地址侧结果。

真正的 CMO 副作用不在 StoreUnit 内直接发生。`StoreQueue.scala:811-820` 定义了 MMIO/uncached/CMO 的五阶段状态机，`957-1006` 在 ROB 头部、store buffer 已清空后发出 `cmoOpReq`，`1024-1041` 通过 `mmioStout` 写回，`1394-1398` 再产生 `cbo.inval` difftest 事件。因此 StoreUnit 是地址和异常入口，StoreQueue 才是有序的 CMO 提交者。

### Q3. CBO 为什么只需要 Store Address（STA），不需要 Store Data（STD）？

CBO 的唯一软件输入是目标地址。`DecodeUnit.scala:478-481` 对 CBO 使用 `SrcType.reg, SrcType.DC, SrcType.X`，其中第二个源是 `DC`，没有第二个数据寄存器，也没有目的寄存器。`StoreUnit.scala:135` 用 `src(0) + imm` 计算地址，而 `LSQWrapper.scala:79-86` 把 `storeAddrIn` 和 `storeDataIn` 设计成两个独立接口。

STD 的实现也证明它不是 CBO 的执行单元：`MemBlock.scala:81-87` 中的 `Std` 只把 `io.in.bits.data.src(0)` 转发为输出数据；`ExeUnit.scala:443-468` 的 `MemExeUnit` 只搬运数据、ROB index 和 SQ index，没有 TLB、PMP 或地址异常接口。源码中 STD IQ 会复制 STA IQ 的 uop（`Scheduler.scala:500-517`），这是为了普通 store 的数据就绪协议，并不意味着 management CBO 需要一个数据副作用。对于唯一真正需要“写零数据”的 `CBO.ZERO`，数据值由 `StoreQueue.scala:583-610` 特判为 `0.U`，仍然由 StoreQueue 管理，而不是由 CBO 读取一个软件数据源。

### Q4. `FuType.stu` 如何区分 STA 和 STD？

`FuType.stu` 本身并不能独立区分两个执行单元。`FuConfig.scala:108-112` 的 `fuSel` 只是比较 `uop.fuType === this.fuType.U`，而 `StaCfg` 与 `StdCfg` 在 `FuConfig.scala:434-459` 中都使用 `FuType.stu`。真正的区分来自配置名称和 issue topology：

- `ExeUnitParams.scala:269-281` 用配置名分别定义 `hasStoreAddrFu`（`name == "sta"`）和 `hasStdFu`（`name == "std"`）；
- `IssueBlockParams.scala:177-185` 分别统计 `StaCnt` 和 `StdCnt`；
- `Parameters.scala:469-496` 明确建立 `STA0/STA1` 与 `STD0/STD1` 两组不同的 issue block；
- `Scheduler.scala:383-396` 用 `StaCnt` 找地址 IQ，用 `StdCnt` 找数据 IQ。

因此准确的说法不是“CBO 由 `FuType.stu` 自动选择了唯一的 StoreUnit”，而是：CBO 被标记为 store family，memory scheduler 根据 STA 配置和地址源形状把它送入 Store Address 路径；STD 是与 STA 配对的数据通路。

### Q5. CBO 为什么必须经过 StoreQueue，而不能在 StoreUnit 中直接执行 cache 操作？

StoreUnit 处于乱序执行的地址流水中，而 CMO 副作用必须按程序顺序、在 ROB 提交边界发生。`StoreQueue.scala:811-820` 的注释直接把 CMO 放入“写回、等待 ROB head、请求、响应、写回 ROB、提交”的状态机。`StoreQueue.scala:960` 的 `deqCanDoCbo` 只在当前 `deqPtr` 对应的 SQ entry 已分配、地址有效且没有异常时成立；`StoreQueue.scala:996-1005` 还要求先完成 `flushSbuffer`，再发 `cmoOpReq`。

如果 StoreUnit 在发射时直接操作 cache，年轻 CBO 可能越过更老的 store 或异常指令，造成 cache 已经改变但 ROB 最终 flush 的不可恢复副作用。把操作交给 StoreQueue，才能利用已有的 store buffer drain、ROB-head gating、CMO response 和精确提交机制。

### Q6. CBO 指令什么时候分配 StoreQueue entry？

StoreQueue entry 在 dispatch/enqueue 阶段分配，而不是等 StoreUnit 计算完地址才分配。`StoreQueue.scala:260-277` 定义了 `allocated`、`addrvalid`、`datavalid`、`pending`、`hasException` 等状态；`StoreQueue.scala:366-417` 根据 `io.enq.req` 设置 `allocated(i) := true.B`，保存 `uop`，并把地址/数据有效位清零、`waitStoreS2` 置位。

随后 STA 地址结果通过 `storeAddrIn` 回填：`StoreQueue.scala:499-542` 保存 `paddr`、`vaddr`、mask、`uop` 和 `sqIdx`；S2 的补充结果在 `554-576` 更新 `pending/mmio/hasException`。这使 CBO 在地址翻译尚未完成时就已经拥有 SQ/ROB 身份，发生 TLB miss、redirect 或 exception 时可以精确地找到并回收同一条指令。

### Q7. 为什么 CBO 的 cache 副作用必须等到 ROB head 才能发生？

源码用“SQ 头指针 + ROB pending 信息”实现这个约束。`StoreQueue.scala:832-839` 只有在 `io.rob.pendingst`、当前 uop 的 ROB index 等于 `pendingPtr`、该 entry 的 `pending/datavalid/addrvalid` 都有效且没有异常时，才进入 uncache/CMO 状态机；对 CBO 而言，`StoreQueue.scala:960` 又以 `uop(deqPtr)` 为基础生成 `deqCanDoCbo`。

这不是人为增加的延迟，而是精确状态的必要条件：CBO 会使 cache 状态发生可见变化，必须保证所有更老的 store 已完成、所有更老的异常已经决定，并且不会被更老的 redirect 撤销。`StoreQueue.scala:1029` 还把 `deqCanDoCbo` 写入 `flushPipe`，显式要求 CMO 期间保持流水顺序。

### Q8. CBO.INVAL、CBO.CLEAN、CBO.FLUSH 在昆明湖内部有什么区别？

三条 management CBO 在解码时分别绑定不同的 `LSUOpType`（`DecodeUnit.scala:479-481`）。操作码编码和分类在 `package.scala:582-596`：`cbo_clean = 1100b`、`cbo_flush = 1101b`、`cbo_inval = 1110b`，`isCbo` 识别这三类，而 `isCboAll` 还额外包含 `cbo_zero`。

StoreQueue 在 ROB-head 阶段保存完整 uop（`StoreQueue.scala:832-838`），取出 `uncacheUop.fuOpType(1,0)` 作为 `cmoOpCode`（`StoreQueue.scala:823-828`），再由 `cmoOpReq.bits.opcode` 发送（`957-999`）。因此三条指令共用地址翻译、顺序和响应状态机，但通过 `fuOpType` 保留各自的 CMO 子操作。`cbo.inval` 还有专门的 difftest 事件条件（`StoreQueue.scala:1394-1398`）。

### Q9. CBO.ZERO 为什么不能和 INVAL/CLEAN/FLUSH 使用完全相同的权限命令？

源码已经把它们分成两类：`LSUOpType.isCboAll` 包含 ZERO，而 `LSUOpType.isCbo` 只包含 management CBO（`package.scala:592-596`）。StoreUnit 在 S2 同时保存 `s2_isCbo` 和 `s2_isCbo_noZero`（`StoreUnit.scala:465-466`），只有后者会进入 `lsq_replenish.mmio` 的 CMO/uncache 分支（`StoreUnit.scala:510-514`）。

ZERO 还会经过 StoreQueue 的特殊路径：`StoreQueue.scala:963-979` 记录它进入 store buffer 并等待 buffer flush，`StoreQueue.scala:1043-1060` 通过 `cboZeroStout` 写回；数据写入在 `StoreQueue.scala:594-599` 对 `cbo_zero` 强制使用 `0.U`。这表明 ZERO 具备实际写入零值的 store-like 语义，不能因为 management CBO 需要 read 权限，就把所有 CBO 无条件改成同一种命令。

### Q10. TLB request 的 `cmd` 是如何影响 PTE 权限检查的？

`MMUBundle.scala:382-396` 定义 `TlbCmd.read = 00b`、`write = 01b`，并由 `isRead/isWrite` 解释。旧版 StoreUnit 在 `StoreUnit.scala:205-215` 无条件设置：

```scala
io.tlb.req.bits.cmd          := TlbCmd.write
io.tlb.req.bits.memidx.is_ld := false.B
io.tlb.req.bits.memidx.is_st := true.B
```

TLB 的 `perm_check` 在 `TLB.scala:407-421` 依据命令得到 `isLd`/`isSt`，分别检查 `perm.r` 和 `perm.w`；stage-2 也在 `TLB.scala:426-435` 依据同一命令计算 guest load/store 权限。因此 command 并不是调试字段，而是决定 PTE/G-stage 选择哪一组权限位和哪一组异常位的控制信号。旧代码把 CBO 放进 StoreUnit 后仍使用 write command，正是本 bug 的直接入口。

### Q11. 为什么旧版产生的是 store exception，而不是 load exception？

旧版 StoreUnit 的异常向量映射完全读取 TLB 的 store 结果：`StoreUnit.scala:383-385` 分别执行

```scala
storePageFault       := resp.excp(0).pf.st
storeAccessFault     := resp.excp(0).af.st
storeGuestPageFault  := resp.excp(0).gpf.st
```

PMP 也把 write command 的失败放到 `st`：`PMP.scala:405-409` 中 `resp.st := (TlbCmd.isWrite(cmd) || TlbCmd.isAmo(cmd)) && !cfg.w`。S2 再把 `s2_pmp.st` OR 入 `storeAccessFault`（`StoreUnit.scala:470-477`），S3 输出时只保留 `StaCfg` 声明的异常（`StoreUnit.scala:632-635`）。所以旧波形中的 store exception 不是 CBO 本身规定了写异常，而是“StoreUnit 的 store command -> st 结果 -> StaCfg 异常向量”这条实现链的必然结果。

### Q12. PTE、PMP 和 G-stage 权限检查分别在哪一层完成？

PTE 和 G-stage 权限在 DTLB 内完成。`TLB.scala:407-421` 计算 stage-1 的 `pf.ld/pf.st`，`TLB.scala:426-435` 计算 stage-2 的 `gpf.ld/gpf.st`，并在 `TLB.scala:468-480` 将页故障和地址故障写入返回 bundle。StoreUnit 的 S1 在 `StoreUnit.scala:364-385` 把这些返回字段写进 STA exception vector。

PMP/PMA 则由独立的 PMP checker 完成。`PMP.scala:404-413` 将同一个 `cmd` 映射成 `resp.ld`、`resp.st`、`resp.instr`；StoreUnit 在 `StoreUnit.scala:447-477` 等待物理地址产生后读取 `io.pmp`，再生成最终 access fault。两层的共同点是都依赖 command，区别是 TLB 负责页表/G-stage 权限，PMP 负责物理地址区域权限。旧版只消费 `.st` 字段，因此即使将 command 改成 read，也还需要显式把 `.ld` 结果转换到 CBO 应报告的 STA 异常类别。

### Q13. CBO 的虚拟地址、物理地址和 cache-block 对齐约束分别在哪里处理？

虚拟地址在 StoreUnit S0 形成：`StoreUnit.scala:134-149` 计算 `rs1 + sign-extended imm`，并将该地址送入 TLB（`StoreUnit.scala:205-208`）。普通 store 的 byte/half/word/double 对齐判断在 `StoreUnit.scala:159-164`，但 `|| s0_isCbo` 明确让 CBO 不受普通 store 宽度对齐条件限制。

TLB 命中后，物理地址在 S1 保存到 `s1_paddr` 和 `s1_out.paddr`（`StoreUnit.scala:289-299、364-375`）。StoreQueue 在提交 CMO 前使用 `get_block_addr`（`StoreQueue.scala:957-960`）对物理地址取 cache block 基地址；该函数定义在 `L1Cache.scala:81-88`，实现为 `(addr >> blockOffBits) << blockOffBits`。因此“指令地址是否可翻译/可访问”和“CMO 最终操作哪个 cache block”是两个连续但不同的步骤。

### Q14. CBO 在 TLB miss、replay、redirect 或 backpressure 下如何保持指令身份？

StoreUnit 用流水寄存器和反馈携带同一个 uop 的身份。`StoreUnit.scala:279-299` 在 S1 锁存 `s0_out`、`s1_isCbo`、TLB 命中和物理地址；`StoreUnit.scala:307-313` 用 `robIdx.needFlush(io.redirect)` 或 TLB miss kill 当前阶段；`StoreUnit.scala:344-354` 的 `s1_feedback` 回传 `robIdx`、`sqIdx` 和 TLB miss 状态给 issue queue。

StoreQueue 侧也按 SQ/ROB 身份处理取消：`StoreQueue.scala:373-417` 用 `enqCancel := robIdx.needFlush(...)` 避免被 redirect 的 entry 分配，`StoreQueue.scala:538-542` 按 `uop.sqIdx` 写回地址和 uop。由于 CMO 请求只读取 `uop(deqPtr)`，并且 `mmioState` 通过 ready/fire 保持状态，backpressure 不会把一个 CBO 的地址、opcode 和异常状态与另一条指令混在一起。

### Q15. CBO 异常发生时，StoreQueue 中已经产生的状态如何回收？

StoreUnit 在 S2 把异常随地址结果送回 LSQ：`StoreUnit.scala:510-522` 设置 `lsq_replenish.af`、`hasException` 和 `updateAddrValid`。StoreQueue 在 `StoreQueue.scala:554-576` 将 `hasException` 写入对应 SQ entry，并再次送进 `StoreExceptionBuffer`；该 buffer 的端口定义在 `StoreQueue.scala:73-93`，专门接收 STA 产生的异常地址和 exception vector。

随后 `deqCanDoCbo` 要求 `!hasException(deqPtr)`（`StoreQueue.scala:957-961`），所以有异常的 CBO 不会发出 `cmoOpReq`。异常 entry 仍可沿 ROB 精确地触发 trap，完成后由 SQ 的正常 dequeue 逻辑清理 `allocated/completed`（`StoreQueue.scala:341-355`）。这保证了“异常可提交”与“cache 副作用不发生”同时成立。

### Q16. 为什么普通 store 的权限行为不能被 CBO 修复影响？

在旧版结构中，普通 store 和 CBO 共用 StoreUnit 的地址流水，但分类信号已经存在：`StoreUnit.scala:152` 用 `LSUOpType.isCboAll` 识别全部 CBO，`StoreUnit.scala:465-466` 又把 management CBO 和 ZERO 分开；普通 store 不满足这些条件，仍走普通 store 的对齐、DCache probe 和 store buffer 数据路径。

因此 command 选择必须以 opcode 为条件：普通 store 保持 `TlbCmd.write`；`CBO.INVAL/CLEAN/FLUSH` 使用其 ISA 所需的 read 检查；`CBO.ZERO` 依据自身的写零语义单独处理。`StoreQueue.scala:594-599` 对 ZERO 的数据特判和 `StoreQueue.scala:872` 对 management CBO 的 MMIO request 排除，都是不能把三类指令合并成一个无条件规则的源码证据。

### Q17. CBO 与普通 store、MMIO、uncache 操作在 StoreQueue 中如何区分？

它们共享“ROB head 后再产生外部副作用”的框架，但请求分支不同。`StoreQueue.scala:811-820` 统一描述了 MMIO、uncached 和 CMO 的状态机；普通 MMIO store 通过 `mmioReq`，请求命令在 `StoreQueue.scala:872-881` 为 `MemoryOpConstants.M_XWR`，数据来自 SQ。

management CBO 在 `StoreQueue.scala:872` 被显式排除出 `mmioReq`，之后由 `StoreQueue.scala:981-1000` 发出 `cmoOpReq`，opcode 来自 `fuOpType`，地址是 cache block 基地址。NC store 则走另一组 `ncReq`（`StoreQueue.scala:934-955`）。因此三者共用等待响应和 ROB 写回，但“写内存”“非 cacheable store”和“cache-block management”不会使用同一条外部请求语义。

### Q18. 这个 bug 应该用哪些断言和 directed tests 固化？

源码已经给出了可以直接转化为验证点的边界：

- `StoreUnit.scala:152-155` 有 `cbo_assert_flag`，可扩展为“CBO 必须从 RS 地址流进入 StoreUnit”的选择断言；
- `StoreUnit.scala:205-215` 的 TLB command、`TLB.scala:409-420` 的 PTE 权限分支和 `PMP.scala:405-409` 的 `ld/st` 分支，应组成 command-to-permission scoreboard；
- `StoreUnit.scala:383-385、474-477` 应验证 TLB/PMP 返回与最终 STA exception vector 的映射；
- `StoreQueue.scala:974` 已断言不能同时执行多个 CBO.ZERO，`MemBlock.scala:1338-1341` 已断言 `mmioStout` 与 `cboZeroStout` 不得同时有效。

测试矩阵至少应覆盖 management CBO 在 R-only、RW、无 R、无 W 的 PTE/PMP/G-stage 区域，另加 CBO.ZERO 和普通 store 回归；并交错注入 TLB miss、redirect、StoreQueue backpressure。只测试 RW 页无法区分旧版 write command 的错误行为。

### Q19. 多个连续 CBO，或 CBO 与普通 store 交错执行时，会不会发生顺序错误？

旧版 StoreQueue 的设计是串行化 CMO 状态机：只有一个 `mmioState`（`StoreQueue.scala:823-839`），只有 `deqPtr` 对应的 `deqCanDoCbo` 才能发起请求（`StoreQueue.scala:957-1000`），请求完成并 `mmioStout.fire` 后才推进后续状态（`StoreQueue.scala:1024-1041`）。CBO 发起前还必须等待 `flushSbuffer`（`StoreQueue.scala:1002-1006`），所以它不会越过尚未排空的老 store。

对于 ZERO，源码还用 `cboZeroValid/cboZeroWaitFlushSb` 管理独立写回，并用 `PopCount(isCboZeroToSbVec) > 1.U` 断言禁止同时执行多个 ZERO（`StoreQueue.scala:963-979`、`974`）。这说明当前实现优先选择保守的单操作序列化，以换取 cache 副作用和 ROB 顺序的清晰性。

### Q20. 这个 bug 暴露了昆明湖哪一种设计风险？

风险不是“不能复用 StoreUnit”，而是**执行载体的默认控制信号被误当成了指令语义**。代码链条非常直接：

1. `DecodeUnit.scala:478-481` 把 CBO 放入 `FuType.stu`；
2. `StoreUnit.scala:205-215` 因复用 store 地址流水而无条件发出 `TlbCmd.write`；
3. `TLB.scala:409-420、426-435` 因此选择 W/D 权限和 `.st/.gpf.st` 结果；
4. `PMP.scala:405-409` 将无 W 区域变成 `pmp.st`；
5. `StoreUnit.scala:474-477` 把 `pmp.st` 写入 `storeAccessFault`，再由 `StoreQueue`/ROB 精确提交。

这说明微架构复用必须分离两个维度：**哪条流水承载地址、顺序和异常**，以及**本条 opcode 对地址要求哪一种 ISA 权限**。前者可以共用 STA/StoreQueue，后者必须由 CBO 分类显式选择。否则即使地址翻译、SQ 分配、ROB 顺序和 CMO 提交全部正确，也会因为一个默认 command 产生架构可见的错误异常。
