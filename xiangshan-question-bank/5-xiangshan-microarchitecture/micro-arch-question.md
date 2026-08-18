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
