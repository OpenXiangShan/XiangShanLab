# 一次 trap 跳转究竟是怎样发生的？——从 ECALL 到异常入口，再用 MRET 回来

## 1. 先把我们要看的事情说清楚

你不需要一开始就认识 ROB、FTQ、CSR 的所有信号。先记住这件事：

> 程序执行到一条不能继续正常完成的指令时，处理器需要记下“哪里出事、出了什么事”，停止让这条指令和它后面的指令正常提交，再到指定的异常处理程序执行。异常处理程序完成后，还可以返回。

我们要观察的不是“PC 突然变了”这么简单，而是把这件事拆开：

1. 谁最先发现异常？
2. 为什么发现后没有立刻改取指地址？
3. 谁决定现在可以正式处理异常？
4. 异常 PC、异常原因、跳转目标分别从哪里来？
5. 后端什么时候发出清空通知，前端什么时候真正收到目标？
6. `mret` 如何返回？它和进入异常是同一条路径吗？
7. 年轻指令即使执行过，为什么最终程序仍然正确？

**本文使用本仓库 `tools/wavekit-xslab` 的 `wavekit.VcdReader` 实际解析、查询完整 VCD，不是按照源码推测出来的一张理想时序图。**
分析流程遵照 `tools/analyze-xiangshan-wavekit/SKILL.md`、其 workflow/signal-map，以及 `tools/xiangshan-wave-analysis/SKILL.md` 和后端/CSR 参考说明。

### 1.1 版本和材料：先确保我们在看同一颗“处理器”

| 项目 | 本次分析使用的内容 |
| --- | --- |
| 仿真程序 | `/nfs/home/wanghao/emuByYuan/emu` |
| emu 内嵌版本 | `abd0f867a8`，日志 dirty=0 |
| 本地源码 HEAD | `abd0f867a86b66a92d4fc5d3c6d62944725c747f` |
| 源码根目录 | `/nfs/home/wanghao/emuByYuan/stable-kmh-v2` |
| 测试源码 | `/nfs/home/wanghao/doCIE/xs-env/nexus-am/apps/learnTrap/trap.S` |
| 反汇编 | `/nfs/home/wanghao/doCIE/xs-env/nexus-am/apps/learnTrap/build/learnTrap.txt` |
| 波形 | `/nfs/home/wanghao/doCIE/xs-env/nexus-am/apps/learnTrap/runs/run-20260922-150415-Hlo0qH/learnTrap.vcd` |
| 日志 | 同一个运行目录的 `emu.log` |
| 结果 | 配套 NEMU 差分已启用；`HIT GOOD TRAP at pc = 0x80000098` |

本地源码树有未跟踪文件，difftest 子模块也有本地修改。因此这里准确的说法是：**所读主仓库 Scala 源码版本与 emu 报出的主仓库 commit 相符**，不是声称整个本地仿真环境完全无修改。不要用另一棵 v3 源码树来解释本波形。

### 1.2 看图前先统一时间和数字显示方式

- VCD 的 `$timescale` 是 **1 ps**；全文时间单位都为 ps。
- 时钟是 **`TOP.clock`**，使用 `sample_on_posedge=True`。
- wavekit 的采样序号从 0 开始。本文件 `C0=0 ps`，`C1=2 ps`，所以 **时间 = 2 × C**。
- 波形最后时间是 **8913 ps**。`C` 是文件中的绝对采样序号，**不是**日志末尾的 `cycleCnt=4401`，也不是 Guest cycle spent。
- 时间 0 的 clock 已记录为 1，wavekit 将它计入第一个样本；它不是复位释放后的第一条指令。
- 原始 `N.io_fromRob_trap_valid` 在时间 0 也有初始化高值。本文的两次真实异常由程序 PC、ROB flush 和后续 CSR 更新联合确认，不把初始化值算成一次程序 trap。
- 地址、指令编码按十六进制显示；ROB/FTQ 编号和周期按十进制显示。比如 ROB `20` 等于波形十六进制 `0x14`。
- 这里的值是 **VCD 在该时间点记录的稳定值**。wavekit 对同时间戳使用最后记录值，不区分 delta cycle。不要把文章中的一拍标签理解为额外证明了真实 RTL 边沿前后所有瞬时组合变化。

**插图位置 ①：时间基准。** 拉出 `TOP.clock`、`TOP.reset`，放大 `8544～8560 ps`。相邻高电平采样点相差 2 ps。把光标放在 8546 ps，对应本文 C4273。

### 1.3 先约定模块简称，否则信号名会把页面挤满

以下简称只是文章排版，不是让你在波形浏览器里搜索字母 B、X。

```text
K = TOP.SimTop.cpu.l_soc.core_with_l2.core
B = K.backend.inner_ctrlBlock
R = B.rob
E = R.exceptionGen
X = K.backend.inner_intExuBlock.exus_7.csr
N = X.csrMod
Q = K.backend.inner_intScheduler.IssueQueueAluCsrFenceDiv
F = K.frontend.inner_ftq
I = K.frontend.inner_ibuffer
```

例如本文 `R.io_flushOut_valid` 的完整路径是：

```text
TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_ctrlBlock.rob.io_flushOut_valid
```

`evidence/column-map.json` 提供主表信号的完整路径，`evidence/signal-map.json` 还保留实际位宽后缀，便于直接搜索。

## 2. 程序到底安排了什么？先别打开几十个模块

本测试只在 **M 模式**运行；中断关闭，异常不委托给 S 模式，`mtvec=0x80000100`，低两位为 0，即 Direct 模式。

先把三个容易混淆的地址分清楚：

| 名字 | 它回答的问题 | 第一次异常的值 |
| --- | --- | --- |
| 出错指令 PC | 哪条指令导致异常？ | `0x80000044` |
| `mtvec` 指定的入口 | 异常处理代码放在哪里？ | `0x80000100` |
| handler 改写后的 `mepc` | 处理结束后想返回哪里？ | `0x80000050` |

**这三个地址本来就不应该一样。** `mepc` 刚进入异常时保存 `0x80000044`，后来软件把它改成 `0x80000050`。别看到它变化就以为硬件保存错了。

测试的重要指令如下；省略的是设置寄存器和比较检查的指令，不改变这里的控制流设计。

```asm
trap_ecall:                         # PC = 0x80000044
    ecall                           # 编码 0x00000073
    addi s1, s1, 1                  # PC = 0x80000048，不能最终提交
    addi s2, s2, 1                  # PC = 0x8000004c，不能最终提交
resume_ecall:                       # PC = 0x80000050
    ...
trap_illegal:                       # PC = 0x80000078
    .word 0xffffffff
    addi s1, s1, 1                  # PC = 0x8000007c，不能最终提交
    addi s2, s2, 1                  # PC = 0x80000080，不能最终提交
resume_illegal:                     # PC = 0x80000084
    ...
trap_handler:                       # PC = 0x80000100
    csrr t0, mcause
    csrr t1, mepc
    csrr t2, mtval
    csrr t3, mstatus
    ...                             # 保存上述值并检查
    csrw mepc, s5                   # PC = 0x80000154
trap_return:                        # PC = 0x80000158
    mret                            # 编码 0x30200073
```

handler 第一次返回 `0x80000050`，第二次返回 `0x80000084`。它不是通用的“`mepc += 4`”处理程序，而是**刻意跳过异常后两条 addi**，检查它们没有最终修改架构状态。

为什么用两个异常？因为它们的“发现地点”不同：

- `ecall` 是可识别的合法系统指令；本次在 CSR 执行单元根据当前特权态生成 M-mode ECALL 异常。
- `0xffffffff` 在译码时就不匹配支持的指令，译码结果已经带着非法指令标记。

两者最后都必须等到 ROB 允许处理，再进入异常。**发现得早，不等于处理得早。**

## 3. 读波形必须先懂 valid、ready 和编号

### 3.1 不能只看 PC 出现就说“指令过去了”

对 Decoupled 接口：

```text
valid=1：发送方说这里有一条有效指令。
ready=1：接收方说现在可以接。
fire=valid && ready：本采样拍显示一次有效传输。
```

`valid=1, ready=0` 时指令在等，不是每拍又传了一次。`valid=0` 时地址字段可能保持旧值，也可能任意变化；**此时不要解释它的 target、PC、cause。**

但 `R.io_flushOut`、`R.io_exception`、`N.io_fromRob_trap`、`B.io_frontend_toFtq_redirect` 是 **Valid 类接口**，不是 valid/ready 握手。本文表格对它们只列 valid，不虚构 ready。

IBuffer 输出的 ready 在本 VCD 的 IBuffer 层级没有单独保留；本文使用 `B.decode.io_in_i_ready` 检查 **Decode 自身的接收条件**，而不是捏造 `I.io_out_i_ready`。CtrlBlock 中还存在 decode buffer，因此不能未经连接核对就把 Decode 的 fire 等同于同拍 IBuffer 的 fire；本文不作这一等同。

### 3.2 ROB 编号是临时身份，不是永久身份证

第一次 ECALL 在 rename 得到 `(flag=0,value=20)`，第一次 `mret` 是 `(0,42)`；第二次非法指令是 `(0,52)`，第二次 `mret` 是 `(0,74)`。

异常清空后会回收位置，因此 handler 第一条指令也会复用 `20` 或 `52`。**只搜索 ROB=20 的整个波形，会把 ECALL 和 handler 的读 CSR 混在一起。** 要同时限制生命周期窗口，并保留 FTQ 编号/偏移。可参考 [S2：Rename 的 ROB 指针分配和 redirect 回收](source-excerpts.md#s2)。

另一个细节：本实现能让部分指令共享 ROB 项。第二次异常后两条 addi 在 dispatch 都看到 ROB=53，不能据此说反汇编重复或工具读错；逐条身份还需要 PC、FTQ 偏移以及有效通道。

## 4. 第一站：ECALL 被取到、译码、重命名

### 4.1 取指是按块来的，不必要求 startAddr 恰好等于指令 PC

在 **C4198 / 8396 ps**，`F.io_toIfu_req_valid=1`、`ready=1`，`bits_startAddr=0x80000040`。

你可能会问：“目标是 0x80000044，怎么给我一个 0x80000040？”

因为 FTQ 发给 IFU 的是取指块请求，不是一条已经切好的指令。ECALL 在块内。后面它的 `ftqPtr.value=5`、`ftqOffset=2`；偏移采用半字粒度，`0x80000040 + 2×2 = 0x80000044`。

取指请求到译码之间相隔 52 拍。本文没有完成这 52 拍的 ICache/ITLB 内部原因归因，**不能直接把 52 拍叫“Cache miss 延迟”**。这里确认的是请求端和译码端的两个可审查锚点。[S10](source-excerpts.md#s10) 给出 FTQ 请求生成与 IBuffer/Decode 的源码入口。

**插图位置 ②：从取指块找到 ECALL。**
- 模块 F：`io_toIfu_req_valid`、`io_toIfu_req_ready`、`io_toIfu_req_bits_startAddr`，看 **8396 ps** 的 `1/1/0x80000040`。
- 模块 `B.decode`：`io_in_3_valid`、`io_in_3_ready`、`io_in_3_bits_pc`、`io_in_3_bits_instr`，看 **8500 ps** 的 `1/1/0x80000044/0x73`。

### 4.2 C4250：译码知道它是 ECALL，但还没有 EX_MCALL

在 **C4250 / 8500 ps**，第 3 通道输入输出都对应 ECALL。这里“第 3 通道”从 0 开始计数，是第 4 个位置。

| `B.decode` 信号 | 值 | 先用一句人话理解 |
| --- | --- | --- |
| `io_in_3_bits_pc` | `0x80000044` | 是我们选定的那条指令 |
| `io_in_3_bits_instr` | `0x00000073` | 编码和反汇编一致 |
| `io_out_3_bits_fuType` | `0x20` | 译码选择 CSR 类功能单元 |
| `io_out_3_bits_fuOpType` | `0x10` | 选择本版本 CSR 的 jmp 操作类型 |
| `io_out_3_bits_exceptionVec_11` | `0` | 此时还没有 M-mode ECALL 异常位 |
| `io_out_3_bits_exceptionVec_2` | `0` | 它也不是非法编码 |

不要套用另一版本的 fuType 编号。这里的含义由本版本 DecodeUnit 中 `ECALL -> ... FuType.csr, CSROpType.jmp ...` 和实际波形共同确定。[S1](source-excerpts.md#s1) 附上原始映射。

### 4.3 C4251：给它一个 ROB 身份

在 **C4251 / 8502 ps**，`B.rename.io_out_3_valid=1`、`ready=1`：

```text
pc          = 0x80000044
robIdx      = flag 0, value 20（十六进制 0x14）
ftqPtr      = flag 0, value 5
ftqOffset   = 2
pdest       = 0
psrc_0..4   = 0
```

ECALL 不是我们要追踪某个加法结果的指令；这些物理寄存器字段为 0，不能硬编一个“ECALL 写回某个计算结果”的故事。接下来主要追踪 ROB=20 的控制信息。

**插图位置 ③：身份接力。** 在 `8500～8504 ps` 拉出 `B.decode.io_out_3_*` 和 `B.rename.io_out_3_*` 的 valid、ready、pc、instr、robIdx_flag/value、ftqPtr_value、ftqOffset。看到“同一 PC 在下一拍拿到 ROB=20”即可，不要把附近其他通道当成这条指令。

## 5. 第二站：为什么 ECALL 卡在 dispatch？

### 5.1 C4252～C4263：不是没取到，而是在等前面的指令完成

ECALL 已进入 dispatch 输入缓冲。**C4252～C4263 / 8504～8526 ps，共 12 拍**：

```text
B.dispatch.io_fromRename_3_valid = 1
B.dispatch.io_fromRename_3_ready = 0
B.dispatch.blockedByWaitForward_3 = 1
```

ECALL 在这里要求前方指令排空。在起始拍 `io_enqRob_isEmpty=0`，尽管 `io_enqRob_canAccept=1`，它仍不能走。**“ROB 还有容量”和“ROB 已经空了”不是同一个条件。**

NewDispatch 的源码明确用 `blockedByWaitForward` 检查 `io.enqRob.isEmpty` 和前序通道，见 [S3](source-excerpts.md#s3)。因此这里可以有证据地把阻塞解释为 waitForward，而不是猜“CSR 算得慢”。

### 5.2 C4264：等到了，现在才真正派遣

在 **C4264 / 8528 ps**：

```text
io_enqRob_isEmpty = 1
blockedByWaitForward_3 = 0
io_fromRename_3_valid / ready = 1 / 1
ROB = 20
```

注意 rename 在 8502 ps 就输出了它，dispatch 到 8528 ps 才真正接受。中间有缓冲和阻塞，不能把“rename fire”直接写成“同拍进入执行单元”。

**插图位置 ④：这张图最适合解释什么叫等待。** 拉 `B.dispatch.io_fromRename_3_valid/ready/bits_pc/bits_robIdx_value`、`blockedByWaitForward_3`、`io_enqRob_isEmpty`、`io_enqRob_canAccept`。窗口 **8504～8530 ps**，重点对比 **8526 和 8528 ps**。读者应亲眼看到 ready 从 0 变 1，而不是只听“等了 12 拍”的结论。

## 6. 第三站：发射、执行、上报异常

### 6.1 C4266：从发射队列出来

在 **C4266 / 8532 ps**，`Q.io_deqDelay_1_valid=1`、`ready=1`、`bits_common_robIdx_value=20`。队列选择结果经过注册后输出；这不是只凭模块名字猜它“应该发射了”。[S4](source-excerpts.md#s4) 给出 `deqDelay` 的注册和输出连接。

本节确认的边界是队列出队，未把队列每个 entry 的选择、唤醒仲裁都展开。它们不是整个 IssueQueue 只有一个状态机，也不应该编一个通用的 `busy/idle` 数值。

### 6.2 C4268：CSR 执行单元收到了 ECALL

**C4268 / 8536 ps**：`X.io_in_valid=1`、`io_in_ready=1`，`io_in_bits_ctrl_robIdx_value=20`，`N.state=0`。

这里的 `state=0` 有明确编码：`s_idle=0`、`s_waitIMSIC=1`、`s_finish=2`。本测试不是访问 IMSIC，所以不需要走状态 1。

### 6.3 C4269：产生的不是跳转目标，而是异常位

**C4269 / 8538 ps**：

| 信号 | 值 |
| --- | --- |
| `X.io_out_valid / io_out_ready` | `1 / 1` |
| `X.io_out_bits_ctrl_robIdx_value` | `20` |
| `X.io_out_bits_ctrl_exceptionVec_11` | `1` |
| `X.io_out_bits_ctrl_exceptionVec_2` | `0` |
| `X.io_out_bits_res_redirect_valid` | `0` |
| `N.state` | `2`，s_finish |

最重要的是倒数第二行：**ECALL 执行完成时没有从这个执行单元直接发出有效 xRET redirect。** 它先把“ROB=20 的指令有异常”报告回去。

为什么异常位是 11？[S5](source-excerpts.md#s5) 的 wrapper 代码直接给出：

```scala
exceptionVec(EX_MCALL) := DataHoldBypass(isEcall && privState.isModeM, false.B, io.in.fire)
```

这不是 mcause 寄存器已经写成 11，而是异常向量的第 11 位被置 1。把向量拼成整数就是 `0x800`；最后的 `mcause` 异常号才是 `0xb`。**0x800 和 0xb 不相等，不代表错误，它们的编码方式不同。**

C4270，CSR FSM 回到 `s_idle=0`，但这不等于异常处理已经完成；执行单元交出报告后，后面的 ROB/CSR trap 通路还要继续工作。

**插图位置 ⑤：不要漏掉 redirect_valid=0。** 在 **8532～8540 ps** 先拉 Q 的 `io_deqDelay_1_valid/ready/bits_common_robIdx_value`，再拉 X 的 `io_in_valid/ready`、`io_out_valid/ready`、输入输出 ROB、`io_out_bits_ctrl_exceptionVec_11`、`io_out_bits_res_redirect_valid`，以及 `N.state`。需要看到 `20 → 20 → EX11=1`，而不是一上来就找 mtvec。

## 7. 第四站：正式进入异常——把这几拍一拍一拍看清楚

### 7.1 先理解谁在做什么

- **ExceptionGen**：保存当前需要关注的异常及其 ROB 身份。它不是“发现一个异常就立即通知前端”。
- **ROB**：检查轮到的最老指令是否需要异常处理，决定何时清空。
- **CSR trap 入口**：根据异常信息和当前特权态，产生 CSR 更新及异常入口 PC。
- **CtrlBlock**：将后端清空信息与准备好的目标地址对齐，送给前端。
- **FTQ/IFU**：改按新的取指目标继续工作。

### 7.2 C4273：ROB 真正拉高 flushOut

**C4273 / 8546 ps**，E 的 `io_state_valid=1`，异常身份是 ROB=20，`isEnqExcp=0`，第 11 位为 1。`isEnqExcp=0` 表示这一条异常从执行后写回路径进入异常选择器，而不是一入 ROB 就带着前端异常。

同拍 `R.io_flushOut_valid=1`，`bits_robIdx_value=20`，`bits_level=1`，`R.state=0`。这里的 level=1 是 flush，包含异常指令本身；不是 flushAfter。

[S6](source-excerpts.md#s6) 里 ROB 的逻辑同时要求 idle 状态、队首有效、异常条件成立，以及上一拍没有重复 flush。它还明确令 `io.exception.valid := RegNext(exceptionHappen)`。因此下一拍出现 exception 是有寄存器依据的。

### 7.3 T0～T6：不是一根线，而是一条有延迟的控制路径

下面两组数字来自实际波形，**每一行都是一拍，没有跳过中间的拍**。`—` 代表该拍没有新有效事件，不代表相关 bits 一定为 0。

| 相对拍 | ECALL：C / ps | 非法指令：C / ps | 本拍确认的事件 |
| --- | --- | --- | --- |
| T0 | 4273 / 8546 | 4365 / 8730 | `R.io_flushOut_valid=1`，ROB 决定清空 |
| T1 | 4274 / 8548 | 4366 / 8732 | `R.io_exception_valid=1`，异常信息进入后续控制路径 |
| T2 | 4275 / 8550 | 4367 / 8734 | `R.state=1`（walk），`N.io_fromRob_trap_valid=0` |
| T3 | 4276 / 8552 | 4368 / 8736 | `N.io_fromRob_trap_valid=1`，`targetPcUpdate=1`，目标已经为 `0x80000100` |
| T4 | 4277 / 8554 | 4369 / 8738 | difftest CSR 快照反映更新；`R.state=0`，目标继续保持 |
| T5 | 4278 / 8556 | 4370 / 8740 | 目标保持，`B.io_frontend_toFtq_redirect_valid=0` |
| T6 | 4279 / 8558 | 4371 / 8742 | `B.io_frontend_toFtq_redirect_valid=1`，target=`0x80000100`、level=1 |

你现在应该能区分：**T0 是决定清空，T3 是 trap 输入及组合目标有效，T6 才是本文检查的前端重定向接口有效。**

尤其注意一个 review 点：[S7](source-excerpts.md#s7) 的 CtrlBlock 注释把 `csr.trapTarget` 写在 T4。但本波形中 `N.io_out_bits_targetPc_pc` 和 `B.io_robio_csr_trapTarget_pc` 在 T3 就已经出现 `0x80000100`。这是实际观测；[S8](source-excerpts.md#s8) 的 `DataHoldBypass` 说明目标可在更新时旁路输出。**不要照抄注释把波形人为向后挪一拍。** 注释和当前信号的对应关系，应以有效赋值和本次波形复核。

第一次 T0～T2，target 总线上能看到旧值 `0x38020800`；第二次则是上次返回目标 `0x80000050`。这不代表处理器错误跳去了那些地址。先看 `targetPcUpdate` 和最终 `redirect_valid`，再判断目标被使用的时机。

**插图位置 ⑥：正文最核心的一张图。** 窗口 **8544～8562 ps**，按以下顺序拉信号：

1. R：`io_flushOut_valid`、`io_flushOut_bits_robIdx_value`、`io_flushOut_bits_level`、`io_exception_valid`、`state`。
2. E：`io_state_valid`、`io_state_bits_robIdx_value`、`io_state_bits_isEnqExcp`、`io_state_bits_exceptionVec_11`。
3. N：`io_fromRob_trap_valid`、`io_fromRob_trap_bits_pc`、`io_fromRob_trap_bits_trapVec`、`io_fromRob_trap_bits_isInterrupt`。
4. N：`io_out_bits_targetPcUpdate`、`io_out_bits_targetPc_pc`；B：`io_robio_csr_trapTarget_pc`。
5. B：`io_frontend_toFtq_redirect_valid`、`io_frontend_toFtq_redirect_bits_cfiUpdate_target`、`io_frontend_toFtq_redirect_bits_level`。

在 **8552 ps** 检查输入 `pc=0x80000044`、`trapVec=0x800`、`isInterrupt=0`；在 **8558 ps** 检查输出 `target=0x80000100`。别把输入 pc 和输出 target 当作同一个字段。

### 7.4 T4 的 CSR 更新到底说明什么？

**C4277 / 8554 ps**，拉 N 中以下信号：

| 信号 | 本次值 | 意义 |
| --- | --- | --- |
| `diffCSRState_csr_mepc` | `0x80000044` | 保存异常指令 PC，不是下一条指令 PC |
| `diffCSRState_csr_mcause` | `0xb` | M 模式 ECALL |
| `diffCSRState_csr_mtval` | `0` | 本次 ECALL 没有额外 tval 内容 |
| `diffCSRState_csr_mstatus` | `0x40a00001800` | 包含 MPP=3、MIE=0、MPIE=0、MDT=1 |

这里 `mstatus` 的高位并非全是无关随机数。本版本 trap entry 明确写 MDT=1，所以不能拿一份没有 MDT 的简化 mstatus 示例，断言高位异常。[S9](source-excerpts.md#s9) 附出保存 MPP、关闭 MIE、更新 mepc/mcause/mtval 的赋值。

`TrapEntryMEvent` 的 `out.mepc.bits.epc` 是去掉最低位的字段，而 `diffCSRState_csr_mepc` 是软件看到的完整地址。若你拉的是内部 `mepc.reg_epc`，请注意字段位宽和拼接，不要直接拿其原始数值与完整 PC 比较。

### 7.5 前端收到通知后，什么时候真的取 handler？

- **C4279 / 8558 ps**：CtrlBlock 的前端 redirect 有效，目标为 handler。
- **C4284 / 8568 ps**：`F.io_toIfu_req_valid/ready=1/1`，startAddr=`0x80000100`。
- **C4288 / 8576 ps**：`B.decode.io_in_0_valid/ready=1/1`，pc=`0x80000100`。
- **C4289 / 8578 ps**：handler 第一条指令 rename 输出，ROB 项复用 20。
- **C4290 / 8580 ps**：handler 第一条指令 dispatch 接收。
- **C4297 / 8594 ps**：`R.difftest_commit_valid=1`，`difftest_commit_pc=0x80000100`。

**插图位置 ⑦：别停在 redirect，要看到执行入口。** 拉 B 的前端 redirect，F 的 `io_toIfu_req_*`，`B.decode.io_in_0_*` 和 R 的 `difftest_commit_valid/pc/instr`。窗口 **8558～8596 ps**，依次定位 **8558、8568、8576、8594 ps**。这四个点分别说明“通知前端”“发出请求”“译码看到”“最终提交”，不是同一件事。

## 8. handler 处理完以后，MRET 怎么回来？

### 8.1 软件先改 mepc，硬件不会替你猜下一条该去哪

handler 已检查 `mcause/mepc/mstatus`，并将记录保存到 `trap_records`。随后执行 `csrw mepc,s5`。

**C4326 / 8652 ps**，`N.diffCSRState_csr_mepc` 变为 `0x80000050`。这由软件写 CSR 指令造成，不是第二次硬件异常。`mcause` 仍然保持 11。

记录 store 是本程序的辅助观测，不是 trap 跳转本身。本篇没有逐条展开这八条 store 的 LSQ、StoreBuffer、DCache 生命周期；不能把 CSR 快照等同于证明该拍记录已写进 DCache。

### 8.2 MRET 也先等待前面的指令完成

`mret` PC=`0x80000158`：

| 阶段 | C / ps | 身份及条件 |
| --- | --- | --- |
| Decode 第 2 通道 | 4306 / 8612 | pc=`0x80000158`，有效握手 |
| Rename 第 2 通道 | 4307 / 8614 | ROB=(0,42)，有效握手 |
| Dispatch 等待 | 4308～4328 / 8616～8656 | 第 2 通道 valid=1、ready=0，21 拍 |
| Dispatch 真正接收 | 4329 / 8658 | waitForward 阻塞解除，ROB 已空，1/1 |
| Q 出队通道 1 | 4331 / 8662 | ROB=42，1/1 |
| X 输入 | 4333 / 8666 | ROB=42，1/1，N.state=0 |
| X 输出 | 4334 / 8668 | ROB=42，1/1，N.state=2，redirect.valid=1 |
| B 到前端 redirect | 4336 / 8672 | target=`0x80000050`，level=0 |
| MRET 提交 | 4339 / 8678 | difftest PC=`0x80000158` |
| F 请求返回地址 | 4340 / 8680 | startAddr=`0x80000050`，1/1 |
| Decode 恢复 | 4344 / 8688 | pc=`0x80000050`，1/1 |

### 8.3 为什么这次不经过“ECALL 的 T0～T6”？

因为 MRET 不是再产生一次 ECALL 异常。它属于 xRET，CSR wrapper 直接在有效输出上产生 redirect；源码见 [S5](source-excerpts.md#s5)：

```scala
io.out.bits.res.redirect.get.valid := io.out.valid && RegEnable(isXRet, false.B, io.in.fire)
redirect.level := RedirectLevel.flushAfter
redirect.cfiUpdate.target := csrMod.io.out.bits.targetPc.pc
```

对照波形：ECALL 的 `X.io_out_bits_res_redirect_valid=0`，MRET 的同一信号=1。MRET 的 level=0（flushAfter），**保留 MRET 自己，让它能正常提交**；进入异常的 level=1 则包含异常指令自己。

[S9](source-excerpts.md#s9) 的 MretEvent 使用 `in.mepc.asUInt` 作为返回 PC，并从旧 MPP 恢复当前特权态，再清除 MPP 和 MDT、设置 MPIE。

在 **C4334 / 8668 ps**，`mstatus=0xa00000080`。当前执行模式仍为 M：这里清成 U 的是“留给下次返回参考的 MPP 字段”，**不能看到 MPP=0 就断言处理器已经进了 U 模式**。请同时查看 `N.io_status_privState_PRVM=3`、`V=0`。

**插图位置 ⑧：进入异常和返回异常放在一起对比。** 窗口 **8650～8688 ps**：N 的 mepc、mstatus、privState；X 的输入输出 valid/ready、ROB、`io_out_bits_res_redirect_valid`、`...bits_level`、`...bits_cfiUpdate_target`；B 的前端 redirect；F 的请求。重点看 **8652 ps** 的软件改 mepc、**8668 ps** 的执行单元返回通知、**8672 ps** 的前端通知。

## 9. 第二次：非法指令的异常为什么在更早的阶段出现？

### 9.1 C4347：译码当场发现，而不是送进 CSR 再判断编码

**C4347 / 8694 ps**，`B.decode` 第 2 通道：

```text
io_in_2_bits_pc              = 0x80000078
io_in_2_bits_instr           = 0xffffffff
io_in_2_bits_exceptionVec_2  = 0
io_out_2_bits_exceptionVec_2 = 1
```

同一个译码阶段，输入尚未有 EX_II，输出已经有了。这就把异常产生位置夹住了，而不是仅凭源码里存在 illegalInstr 一词来断言。[S1](source-excerpts.md#s1) 的输出赋值解释了这个变化。

随后：

- **C4348 / 8696 ps**：rename 第 2 通道，ROB=(0,52)，FTQ=(0,10)，offset=4。
- **C4349 / 8698 ps**：dispatch 第 2 通道有效握手，EX2=1。
- **C4352 / 8704 ps**：E 的 `io_state_valid=1`，ROB=52，EX2=1，**isEnqExcp=1**。
- **C4352～C4365**：该异常被保留，等待能够正式处理。

和 ECALL 的 `isEnqExcp=0` 对比，你现在知道它的名字到底表示什么：**这个异常是不是在进入 ROB 时就已携带的异常**。[S6](source-excerpts.md#s6) 中 `current` 的更新区分 enq 和 writeback 两条来源。

在本条非法指令的活跃窗口，未观察到它以 CSR 指令身份在 X 输入执行；后面看到 ROB=52 的 CSR 访问是 handler 第一条 `csrr` 复用位置，不能误归到非法指令。

**插图位置 ⑨：非法指令与 ECALL 最大的区别。** 在 **8694～8706 ps** 拉 Decode 第 2 通道输入输出 pc/instr/exceptionVec_2、Rename/Dispatch 第 2 通道 valid/ready/ROB/EX2，再拉 E 的 `io_state_valid/bits_robIdx_value/bits_exceptionVec_2/bits_isEnqExcp`。最后与第一张 ECALL 执行图比较：一个在译码置位，一个在 CSR 执行置位。

### 9.2 为什么已经有异常了，前端还会跑到 test_fail？

这是本次波形特别适合教学的地方。

非法指令后的两条 addi，以及 `resume_illegal` 的分支，曾经进入流水线。**C4349 / 8698 ps**，dispatch 接受：

| 通道 | PC | ROB value | 指令 |
| --- | --- | --- | --- |
| 2 | `0x80000078` | 52 | 非法指令 |
| 3 | `0x8000007c` | 53 | `addi s1,s1,1` |
| 4 | `0x80000080` | 53 | `addi s2,s2,1` |
| 5 | `0x80000084` | 54 | `bnez s1,test_fail` |

**C4354 / 8708 ps**，`K.backend.inner_intExuBlock.exus_5.brh` 的输出 ROB=54，FTQ=10、offset=10，`taken=1`、`predTaken=0`、`isMisPred=1`、target=`0x800000a0`。

**C4356 / 8712 ps**，B 的前端 redirect 确实有效：target=`0x800000a0`，isMisPred=1、level=0。这是年轻分支的方向预测纠正，**不是把 mcause 或 mtvec 写错了**。

随后更老的 ROB=52 异常在 **C4365 / 8730 ps** 获得正式处理；到 **C4371 / 8742 ps**，新的异常 redirect 又把目标改成 `0x80000100`。这些年轻路径最终没有提交。

需要克制的一点：这里用分支输出证明了分支被执行并判定 taken；没有完成两条 addi 到物理寄存器、旁路网络的全链追踪。因此可以说“年轻分支已经执行并重定向”，但不能把未逐点检查的旁路值写成已证明事实。

还有一个容易被截图误导的细节：**C4363 / 8726 ps** 也出现过 `startAddr=0x80000100` 的取指请求，但它在正式异常 redirect 之前，不能拿它充当“异常恢复完成”的证据。我们选的是 **C4376 / 8752 ps**，即本次有效异常 redirect 之后的请求。

**插图位置 ⑩：年轻分支与老异常的先后顺序。**
- `K.backend.inner_intExuBlock.exus_5.brh`：输出 valid、ROB、redirect.valid、FTQ/offset、cfiUpdate.taken/predTaken/isMisPred/target，看 **8708 ps**。
- B：前端 redirect valid/target/isMisPred/level，先看 **8712 ps** 的 `0x800000a0 / 1 / 0`，再看 **8742 ps** 的 `0x80000100 / 0 / 1`。
- R：flushOut.valid/ROB，看 **8730 ps** 的 ROB=52。
- E：state.valid/ROB，确认老异常从 **8704 ps** 起一直保留，未被年轻分支替换掉。

brh 输出的 `cfiUpdate.pc` 在本版本是送入执行通路的 PC 基值，本次该字段为 `0x80000070`，不能直接认作分支单条指令 PC。FTQ offset=10 对应 `0x80000070 + 10×2 = 0x80000084`；再用 dispatch 的 ROB=54/PC 配对确认。[S11](source-excerpts.md#s11) 附上这个字段的赋值，避免看见 `0x80000070` 就追错指令。

### 9.3 第二次正式 trap：只换原因，后半条控制路径相同

第 7 节逐拍表已列出 C4365～C4371，每一拍都可以逐行 review。

重点值：

| 时间 | 应看哪个模块/信号 | 应看到什么 |
| --- | --- | --- |
| 8730 ps | R.flushOut | valid=1，ROB=52，level=1 |
| 8732 ps | R.exception | valid=1，EX2=1 |
| 8736 ps | N.io_fromRob_trap | valid=1，pc=`0x80000078`，trapVec=`0x4`，isInterrupt=0 |
| 8736 ps | N.targetPc | update=1，pc=`0x80000100` |
| 8738 ps | N.diffCSRState | mepc=`0x80000078`，mcause=2，mtval=`0xffffffff` |
| 8742 ps | B.toFtq.redirect | valid=1，target=`0x80000100`，level=1 |
| 8752 ps | F.toIfu.req | valid/ready=1/1，startAddr=`0x80000100` |
| 8760 ps | B.decode.io_in_0 | valid/ready=1/1，pc=`0x80000100` |
| 8778 ps | R.difftest_commit | valid=1，pc=`0x80000100` |

本次 `mtval=0xffffffff` 保存了非法编码，源码中非法指令选择 trapInst 作为 tval，见 [S9](source-excerpts.md#s9)。这不是说所有 RISC-V 实现遇到非法指令都必须填这个值；这里只报告本实现、本测试的实际结果。

**插图位置 ⑪：第二次异常完整闭环。** 复制插图⑥、⑦的信号组，窗口改成 **8730～8780 ps**。让读者一眼看到相同的硬件路径，只有输入异常 PC、向量、最终 mcause/mtval 不同。

## 10. 第二次 MRET：同一条 PC，不是同一次执行

MRET 的代码地址还是 `0x80000158`，但它是第二次动态执行，ROB 变成 74。

| 阶段 | C / ps | 关键内容 |
| --- | --- | --- |
| Decode | 4398 / 8796 | 第 2 通道，PC=`0x80000158` |
| Rename | 4399 / 8798 | ROB=(0,74) |
| Dispatch 等待 | 4400～4420 / 8800～8840 | valid=1，ready=0，共 21 拍 |
| 软件写 mepc 已反映 | 4418 / 8836 | mepc=`0x80000084` |
| Dispatch 接收 | 4421 / 8842 | waitForward=0，ROB empty=1 |
| Q 出队 | 4423 / 8846 | ROB=74，1/1 |
| X 输入 | 4425 / 8850 | ROB=74，1/1 |
| X 输出 | 4426 / 8852 | xRET redirect.valid=1，目标=`0x80000084` |
| B 前端 redirect | 4428 / 8856 | level=0，目标=`0x80000084` |
| MRET 提交 | 4431 / 8862 | PC=`0x80000158` |
| F 请求 | 4432 / 8864 | startAddr=`0x80000084`，1/1 |
| Decode 恢复 | 4436 / 8872 | PC=`0x80000084` |
| 返回地址第一条提交 | 4444 / 8888 | PC=`0x80000084` |

**插图位置 ⑫：两次返回并排。** 第二次拉图窗口 **8834～8890 ps**，使用插图⑧信号组。关键对照：第一次 ROB=42、target=`0x80000050`；第二次 ROB=74、target=`0x80000084`。若只用 PC 搜索 mret 而不分窗口，你会误以为一次指令停留了很久。

## 11. 怎么证明程序最后真的正确，而不是只看到 PC 跳来跳去？

### 11.1 架构 CSR 的变化表

以下都是 N 的 `diffCSRState_csr_*`，是同一采样坐标下的状态快照；它们不是保证与每个 RTL 内部 CSR 组合更新完全同拍的另一套接口。

| 事件 | C / ps | mepc | mcause | mtval | mstatus |
| --- | --- | --- | --- | --- | --- |
| 第一次进入异常后 | 4277 / 8554 | `0x80000044` | `0xb` | `0` | `0x40a00001800` |
| 第一次软件改返回 PC | 4326 / 8652 | `0x80000050` | `0xb` | `0` | `0x40a00001800` |
| 第一次 MRET 输出 | 4334 / 8668 | `0x80000050` | `0xb` | `0` | `0xa00000080` |
| 第二次进入异常后 | 4369 / 8738 | `0x80000078` | `2` | `0xffffffff` | `0x40a00001800` |
| 第二次软件改返回 PC | 4418 / 8836 | `0x80000084` | `2` | `0xffffffff` | `0x40a00001800` |
| 第二次 MRET 输出 | 4426 / 8852 | `0x80000084` | `2` | `0xffffffff` | `0xa00000080` |

更多已提取的 CSR、HCSR、向量 CSR 状态字段放在 `evidence/csr-snapshots.csv`，避免正文塞满与这两条异常没有直接关系的数值。本文未完成全体 GPR/FPR/向量架构映射快照重建；波形里存在物理寄存器 difftest 数据，**不能将物理寄存器编号直接当作架构寄存器编号**。这是分析范围限制，不是说波形没有这些信号。

### 11.2 全程八个提交通道的检查

`evidence/commits.csv` 是扫描 `R.difftest_commit[_1.._7]_valid/pc/instr` 的结果。以下 PC **没有有效提交记录**：

```text
异常指令本身：0x80000044、0x80000078
被跳过的 addi：0x80000048、0x8000004c、0x8000007c、0x80000080
失败路径：    0x800000a0、0x800000a4
```

因此“年轻分支曾经把前端带到失败路径”和“失败路径最终没有提交”可以同时成立。**投机执行、取指可见、架构提交，是三个层次。**

异常指令通过异常通道处理，而不是作为普通成功指令退休；MRET 则确实在 C4339 和 C4431 有有效提交记录。

最后程序自己的检查和配套 NEMU 差分都通过，日志以 `HIT GOOD TRAP` 结束。这里的 GOOD TRAP 是仿真器识别测试结束指令 `0x0000006b` 的提示，**不是第三次进入本测试 handler 的证据**。

**插图位置 ⑬：提交 review。** 拉出 R 的八组 `difftest_commit[_i]_valid/pc/instr`，观察 **8490～8913 ps**。不要只看 lane0。先定位两次 `0x80000158` 提交，再搜索四条 addi 和失败路径 PC 是否在 valid=1 时出现。CSV 可用来核对浏览器搜索结果。

## 12. “等了几拍”要怎样算才不误导？

### 12.1 已有直接证据的阻塞和恢复间隔

| 窗口 | 拍数 | 波形证据 | 能说什么、不能说什么 |
| --- | --- | --- | --- |
| ECALL dispatch C4252～4263 | 12 | valid=1、ready=0、blockedByWaitForward=1 | 明确是前方排空条件阻塞；不是执行延迟 |
| 第一次 MRET dispatch C4308～4328 | 21 | 第 2 通道同样的 waitForward 条件 | 不是 mret 在 CSR FSM 中算了 21 拍 |
| 第二次 MRET dispatch C4400～4420 | 21 | 同上 | 本次两个 handler 的等待拍数相同，不推出永远相同 |
| 第一次 trap C4273～4287 | 15 | Decode 六通道没有 fire，C4288 首次恢复 | 是本窗口的无译码传输拍数，不全归因于某一缓存 |
| 第二次 trap C4365～4379 | 15 | 同样六通道没有 fire，C4380 首次恢复 | 同上 |
| 第一次 MRET 输出 C4334～4343 | 10 | Decode 无 fire，C4344 恢复 | 以执行输出为起点，别与 ROB flush 起点混算 |
| 第二次 MRET 输出 C4426～4435 | 10 | Decode 无 fire，C4436 恢复 | 同上 |

另外，ROB flush 到前端 redirect 是 **6 拍**，到新的 handler 请求是 **11 拍**，到 handler decode 是 **15 拍**。这是三个不同终点的间隔，不要统称“异常处理延迟”。整个软件 handler、记录 store、比较、返回的开销也不包含在这 6 拍里。

### 12.2 状态机的数值要有源码依据

| 模块 | 波形状态 | 本次观察 | 为什么重要 |
| --- | --- | --- | --- |
| ROB | `R.state` | 0=idle；C4275～4276、C4367～4368 为 1=walk | 清空后恢复相关状态，再回 idle；不是向前正常提交 |
| NewCSR | `N.state` | ECALL 输入 C4268 为 0，输出 C4269 为 2，C4270 回 0；两次 MRET 同样 0→2→0 | 同步系统指令无需进入 IMSIC 等待态 1 |
| ExceptionGen | `E.io_state_valid/bits_*` | ECALL 在 C4273 保存来自 WB 的异常；非法指令 C4352～4365 保存来自 enq 的异常 | 这是“保存的异常记录”，不是把 state_valid 当成 Enum 状态编号 |
| Decode/Rename/FTQ/IssueQueue | 多个流水寄存器、指针/entry 状态 | 本文给出边界与 ROB/FTQ 身份，没有宣称完成所有内部 FSM/entry 跟踪 | 不应发明一个不存在的全模块单一状态编号 |

源码中 ROB 的 enum/转换和 NewCSR 的 enum/转换见 [S6](source-excerpts.md#s6)、[S8](source-excerpts.md#s8)。

若讨论优化，只能从已证明的等待开始，例如研究严格串行化是否能在保证精确异常的前提下缩短，或分析前端恢复路径的实际瓶颈。**不能直接删除 waitForward，也不能仅凭这一次短程序建议改缓存结构。** 本文没有提出未经验证的 RTL 优化补丁。

## 13. 对着源码，把这条“消息传递链”再读一遍

这里用箭头表示生产者、信号、消费者，帮助你在源码与波形层级之间来回跳转。相关原文摘录集中在 `source-excerpts.md`，每项都有当前本地源码绝对路径及行号。

```text
DecodeUnit
  ├─ ECALL：译码选择 CSR，暂时 EX11=0
  └─ 非法指令：输出 exceptionVec(2)=1
        ↓ Rename：分配可回收的 robIdx，携带 FTQ 身份
        ↓ Dispatch：检查 waitForward/ROB 条件
        ├─ ECALL → IssueQueue → CSR wrapper → 写回 EX11=1
        └─ 非法指令 → ROB 入队时已携带 EX2=1
                      ↓ ExceptionGen：保存异常身份
                      ↓ ROB：最老指令的异常条件成立
             flushOut（清空请求） + 下一拍 exception（异常信息）
                      ↓ CtrlBlock / 后端寄存传递
                      ↓ NewCSR.fromRob.trap
                      ↓ TrapEntryMEvent
          mepc / mcause / mtval / mstatus 更新 + targetPc
                      ↓ CtrlBlock 对齐前端通知
                      ↓ toFtq.redirect
                      ↓ FTQ → IFU 请求 handler

handler 的 csrw mepc
                      ↓ MretEvent 读取 mepc
                      ↓ CSR wrapper 输出 xRET redirect（flushAfter）
                      ↓ CtrlBlock → FTQ → IFU 请求返回地址
```

**进入异常时先决定清空，再准备/对齐异常入口；返回时由合法 MRET 的 CSR 执行结果产生 redirect。** 两者最后都改变前端取指，但不是同一个触发源，也不使用相同的 flush 范围。

## 14. 你可以怎样复现本次分析和做 review？

### 14.1 Skill 不是一条神奇的命令

`SKILL.md` 是工作流程和证据要求；真正读波形的是 wavekit。此次环境的 Python 原先缺少 pylibfst/vcdvcd，已在 `/tmp/trap-wave-env` 创建临时环境补齐，没有修改工具库的功能。

在这台机器上复跑：

```bash
cd /nfs/home/wanghao/XiangShanLab
/tmp/trap-wave-env/bin/python \
  xiangshan-course/docs/5-xiangshan-scenarios-analysis/scenarios-analysis/exception/analyze_trap.py \
  --wavekit-src /nfs/home/wanghao/XiangShanLab/tools/wavekit-xslab/src
```

`/tmp` 环境可能被清理；届时请按 `tools/wavekit-xslab/README.md` 的安装说明准备 Python 环境。脚本需要 wavekit 本地源码及其依赖；本机使用了仓库已有的 Python 3.12 扩展，不承诺换一个 Python ABI 后仍可直接导入已有 `.so`。

脚本是**这个固定测试/固定版本的证据回归脚本**，不是自动适配所有香山版本的分析器。它检查两次 flush 周期、CSR 值和禁止提交的 PC；若换了 emu 或程序，断言失败应该重新分析，不能删掉断言后沿用旧结论。

### 14.2 CSV 不是让人硬读几千列

- 主文给出每节需拉的少量信号。
- `cycle-review.md` 将 C4250～C4444 的 195 拍逐行排成 Markdown 表，适合一拍一拍移动光标核对；它不替代正文的因果解释。
- `cycle-by-cycle.csv` 保留 C4190 到结束的**每一个周期**，不是只输出发生事件的几行。0x 前缀表示十六进制。
- `column-map.json` 把简短列名还原为完整信号。
- `stage-events.csv` 只记录有效握手，适合按 PC/ROB 查动态指令；不能用它单独计算 stall，因为 stall 已被筛掉。
- `csr-snapshots.csv` 用于复核更多状态，正文只解释当前相关字段。
- `commits.csv` 扫描了全程八个普通提交通道。
- `unknown-masks.json` 检查关键 trap/CSR/异常选择信号的 X/Z；不能把默认 X/Z→0 当作“确定为 0”的证据。

### 14.3 Review 时请优先检查这六件事

1. **同一版本、同一波形、同一单位吗？** 先查 commit 和 timescale，不要先争论一拍差异。
2. **有效信号检查了吗？** target 有值但 valid=0，不能算一次跳转。
3. **同一条动态指令吗？** handler 会复用 ROB，mret 会复用 PC。
4. **输入 PC 和输出 target 分清了吗？** 第一次是 `0x80000044 → 0x80000100 → 0x80000050`。
5. **T3 的旁路目标有没有被错误地写成 T4？** 本文故意保留了与源码注释不完全相同的实际观测。
6. **投机分支与最终提交分清了吗？** 8712 ps 去过失败路径，不等于测试失败。

### 14.4 本文还没有证明的部分，明确留给后续扩展

这篇文章完成的是 **trap 接口主链、两个异常来源、两次返回、关键状态及提交核对**。没有声称完整覆盖 skill 所列的一切微架构细节：

- ECALL 取指请求到译码的 52 拍，尚未逐层分析 ICache/ITLB 状态，不能归因到某个 miss。
- IssueQueue 逐 entry 仲裁、DataPath 读寄存器/旁路、所有内部状态未全部展开；已确认的是队列出队和执行输入输出边界。
- handler 辅助 store 未逐条追到 DCache；全体 GPR/FPR/vector difftest 状态未重建。
- 逐周期证据表记录的是选定接口，不等于逐行解释百万个波形信号。
- 本测试不覆盖委托到 S 模式、异步中断向量、页故障、嵌套 trap。它也不是验证某个错误已经被修复的 bug 报告。

如果你只记住一句话，请记住：**异常不是“执行单元算出一个地址然后立刻跳过去”；它需要把异常身份交给顺序提交体系，再保存架构状态、清空错误路径、通知前端，而 MRET 则是另一条有自己有效条件的返回路径。**
