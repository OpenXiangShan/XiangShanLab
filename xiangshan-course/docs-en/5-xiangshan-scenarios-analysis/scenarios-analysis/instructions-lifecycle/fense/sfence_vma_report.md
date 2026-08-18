# SFENCE.VMA 演示程序与波形分析报告

## 方法与结论摘要

- 演示程序位于 `/home/yanyusong/cbo-kmhv2/nexus-am/apps/sfence_vma`，通过 `make -C nexus-am/apps/sfence_vma ARCH=riscv64-xs` 构建。
- 生成镜像：`/home/yanyusong/cbo-kmhv2/nexus-am/apps/sfence_vma/build/sfence_vma-riscv64-xs.bin`。
- 使用的仿真命令为：

  ```bash
  cd ~/cbo-kmhv2
  source env.sh
  cd "$NOOP_HOME"
  ./build/emu --dump-wave-full --no-diff \
    -i "$AM_HOME/apps/sfence_vma/build/sfence_vma-riscv64-xs.bin"
  ```

- 仿真正常结束，输出 `HIT GOOD TRAP at pc = 0x8000023e`。程序在栅栏前后的校验和分别为 `0x0` 与 `0xa5a5a5a5a5a5a5a5`，说明栅栏后的 store 和读取均正确完成。
- 波形文件：`/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-17-26-53.fst`（生成于 2026-07-30）。
- 本报告使用 wavekit 开源库的 `FstReader` 解析并查询该 FST；采样时钟经层级确认是 `TOP.SimTop.clock`，默认在下降沿采样。周期号和 time 均为波形中的绝对值。

**结论：场景满足要求。** 目标 `sfence.vma` 被解码为带 `flushPipe` 的 Fence uop；它先等待 store buffer 清空，再向前端 ITLB 与内存侧 DTLB/PTW 广播 SFENCE 请求。随后 ROB 发出 `flushOut` 和 redirect，前端从顺序下一条 `0x800001c4` 恢复取指，最后该 Fence 在周期 `25991` 正式退休。波形同时证明这条指令无 GPR/FPR/向量寄存器写回。

## 演示程序与指令锚点

程序在执行目标指令前写入并读取一个 64B 的 `working_set`，之后调用：

```c
__asm__ volatile("sfence.vma %0, %1" : : "r"(virtual_address), "r"(asid)
                 : "memory");
```

其中 `virtual_address = 0x80001800`，`asid = 0`。反汇编的目标指令为：

```text
800001be:  4781        li          a5,0
800001c0:  12f90073    sfence.vma  s2,a5
800001c4:  00001517    auipc       a0,0x1
```

因此目标 PC 是 `0x800001c0`，机器码是 `0x12f90073`。ROB 提交调试口也观测到同一 PC 与机器码，二者一致。注意 `rs2` 的**值**为零，但该源寄存器是 `a5` 而不是 `x0`；波形的 `rs1zero=0`、`rs2zero=0` 也证实这是带具体虚拟地址和 ASID 操作数的编码形式，而非 `sfence.vma x0,x0` 全局形式。

## 全局时间线

| 周期 | time | 事件 | 关键波形证据 |
|---:|---:|---|---|
| 25965 | 51931 | Fence 接收 uop | `Fence.io_in_valid=1`、`io_in_ready=1`，因此 `fire=1`；输入 `robIdx=45`、`src0=0x80001800`、`src1=0`。 |
| 25966–25979 | 51933–51959 | 等待 store buffer | `Fence.state=1 (s_wait)`，`sbIsEmpty=0`，输入 `valid=0`、`ready=0`；该 Fence 不再接收新 uop。 |
| 25980 | 51961 | store buffer 已排空 | 仍在 `s_wait`，但 `sbIsEmpty=1`，满足转入 TLB 失效态的条件。 |
| 25981 | 51963 | 广播 SFENCE/TLB 失效 | `state=2 (s_tlb)`，`io_out_valid=1`、`io_out_ready=1`、`io_fenceio_sfence_valid=1`；地址 `0x80001800`、ID `0`、`flushPipe=1`。 |
| 25983 | 51967 | 一层 TLB 消费 | 前端 `inner_itlb.io_sfence_valid=1`，DTLB load/store/prefetch repeater 和 PTW 输入也为 1。 |
| 25984 | 51969 | ROB 发起 flush | `rob.io_flushOut_valid=1`，载荷为 `robIdx=45`、`ftqIdx=1`、`ftqOffset=1`。 |
| 25985 | 51971 | TLB storage 失效、ROB redirect | ITLB/DTLB storage 的 `io_sfence_valid=1`；`inner_ctrlBlock.io_redirect_valid=1`，其 `robIdx=45`。 |
| 25990 | 51981 | 前端接收恢复 redirect | `io_frontend_toFtq_redirect_valid=1`，`target=0x800001c4`、`ftqIdx=1`、`ftqOffset=1`、`level=1`。 |
| 25991 | 51983 | 架构退休 | `rob.io_commits_isCommit=1` 且 lane 0 `commitValid=1`，PC/指令仍为 `0x800001c0/0x12f90073`。 |

## 逐级分析

### Decode、Rename、Dispatch 与 Issue

目标指令的译码定义为：[DecodeUnit.scala:228](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L228)

```scala
SFENCE_VMA -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X,
  FuType.fence, FenceOpType.sfence, SelImm.X,
  noSpec = T, blockBack = T, flushPipe = T),
```

这说明两个源操作数均来自寄存器，功能单元类型为 `fence`，并且该 uop 不可投机、阻塞后端且带 `flushPipe`。波形在 Fence 输入端观察到稳定标识 `robIdx=45`，以及 `src0=0x80001800`、`src1=0`，与演示程序和反汇编相符。

全量波形中，目标 PC 的前端 FTQ/IBuffer/译码握手存在大量并行复制信号，未找到能与 ROB `45` 一一对应的单一稳定前端 uop 信号；因此报告不把仅按 PC 搜索得到的前端候选信号误称为该 uop 的唯一来源。可由提交信息确定该 uop 的 FTQ 标识为 `ftqIdx=1`、`ftqOffset=1`，并且 redirect 使用相同标识。

### Fence 执行、store buffer 依赖与写回

Fence 功能单元定义了六态 FSM：[Fence.scala:47](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/Fence.scala#L47)

```scala
val s_idle :: s_wait :: s_tlb :: s_icache :: s_fence :: s_nofence :: Nil = Enum(6)
val state = RegInit(s_idle)

sbuffer      := state === s_wait
fencei       := state === s_icache
sfence.valid := state === s_tlb &&
  (func === FenceOpType.sfence || func === FenceOpType.hfence_v || func === FenceOpType.hfence_g)
sfence.bits.rs1 := uop.data.imm(4, 0) === 0.U
sfence.bits.rs2 := uop.data.imm(9, 5) === 0.U
sfence.bits.flushPipe := uop.ctrl.flushPipe.get
```

其状态转移和握手定义为：[Fence.scala:79](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/Fence.scala#L79)

```scala
when (state === s_idle && io.in.valid) { state := s_wait }
when (state === s_wait && ((func === FenceOpType.sfence ||
  func === FenceOpType.hfence_g || func === FenceOpType.hfence_v) && sbEmpty)) {
  state := s_tlb
}
when (state =/= s_idle && state =/= s_wait) { state := s_idle }
io.in.ready := state === s_idle
io.out.valid := state =/= s_idle && state =/= s_wait
```

对应波形为：

- `25965`：`in_valid && in_ready`，uop 进入 Fence；这是唯一一次输入握手。
- `25966–25979`：`s_wait`，`sbIsEmpty=0`；`in_ready=0`，该 14 个完整采样周期是由 store buffer 未清空造成的、可归因于本指令的反压。
- `25980`：`sbIsEmpty` 变为 1。
- `25981`：`s_tlb`，`out_valid && out_ready`，同时 `sfence_valid=1`；这是唯一一次 Fence 输出和 SFENCE 广播握手。
- `25982`：状态回到 `s_idle`，输入 ready 恢复为 1。

这条 uop 不产生计算结果。最终提交窗口内 `rfWen=0`、`fpWen=0`、`vecWen=0`、`ldest=0`、`pdest=0`，故没有架构寄存器写回。

### SFENCE 的消费者：前端与内存侧 TLB

Backend 将同一 `FenceIO.sfence` 分别送到内存侧和前端：[Backend.scala:836](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/Backend.scala#L836)、[Backend.scala:860](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/Backend.scala#L860)

```scala
io.mem.sfence := fenceio.sfence
io.frontendSfence := fenceio.sfence
```

TLB 侧先经过参数化延迟，再决定 MMU 和流水线失效：[TLB.scala:66](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala#L66)

```scala
val sfence = DelayN(io.sfence, q.fenceDelay)
val flush_mmu = sfence.valid || csr.satp.changed || csr.vsatp.changed ||
  csr.hgatp.changed || csr.priv.virt_changed
val mmu_flush_pipe = sfence.valid && sfence.bits.flushPipe
```

波形中的实际消费者时序如下：

- `25981`：Backend 输出以及 `frontend.io_sfence_valid` 有效。
- `25983`：前端 ITLB、DTLB load/store/prefetch repeater、PTW 输入端的 `io_sfence_valid` 有效。
- `25985`：ITLB、DTLB load/store/prefetch 的 entries/storage，以及 PTW 内部的 bitmap、bitmapcache、LLPTW、missQueue、prefetch 和 PTW 子模块的 `io_sfence_valid` 有效。

因此可以证明该场景同时覆盖 instruction-side 和 data-side 的地址转换缓存失效广播；它不是仅在 Fence 单元内结束的空操作。

## Redirect、Flush 与提交阻塞

ROB 对含 `flushPipe` 的队首 uop 会产生 `flushOut`，且源码明确说明该输出在下一周期触发 redirect：[Rob.scala:631](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L631)

```scala
// io.flushOut will trigger redirect at the next cycle.
val lastCycleFlush = RegNext(io.flushOut.valid)
io.flushOut.valid := (state === s_idle) && deqPtrEntryValid &&
  (intrEnable || deqCanException || deqCanFlushPipe) && !lastCycleFlush
io.flushOut.bits.robIdx := Mux(needModifyFtqIdxOffset, firstVInstrRobIdx, deqPtr)
io.flushOut.bits.ftqIdx := Mux(needModifyFtqIdxOffset, firstVInstrFtqPtr, deqPtrEntry.ftqIdx)
io.flushOut.bits.ftqOffset := Mux(needModifyFtqIdxOffset, firstVInstrFtqOffset, deqPtrEntry.ftqOffset)
XSPerfAccumulate("flush_pipe_num", io.flushOut.valid && isFlushPipe)
```

ROB 还会在 flush/redirect 期间阻塞正常提交：[Rob.scala:781](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L781)

```scala
val blockCommit = misPredBlock || lastCycleFlush || hasWFI || io.redirect.valid ||
  (deqNeedFlush && !deqHasFlushed) || deqFlushBlock || criticalErrorState || traceBlock
io.commits.isCommit := state === s_idle && !blockCommit
```

波形与源码完全一致：

1. `25984`：`rob.io_flushOut_valid=1`，其 `robIdx=45`、`ftqIdx=1`、`ftqOffset=1`。
2. `25985`：`rob.io_redirect_valid=1` 且 CtrlBlock `io_redirect_valid=1`，两者 ROB 标识为 `45`。
3. `25986–25987`：ROB `isWalk=1`；此时不进行正常架构提交。
4. `25990`：前端 `io_frontend_toFtq_redirect_valid=1`，恢复目标为 `0x800001c4`，即 `sfence.vma` 的顺序下一条指令；FTQ 标识仍为 `1/1`。
5. `25991`：`isCommit=1 && commitValid[0]=1`，该 Fence 正式退休。

这里的 `cfiUpdate_isMisPred` 是 redirect bundle 的通用编码字段；本场景的原因并非分支预测错误，而是 `flushPipe=1` 的 SFENCE.VMA。因果依据是 Fence 的 `flushPipe`、ROB 的 `flush_pipe_num` 路径、相同的 ROB 标识以及 TLB 广播的连续时序。

## Bubble / 性能影响

| 周期范围 | 边界/模块 | valid / ready / fire | 阻塞原因 | 时长与影响 |
|---|---|---|---|---|
| 25966–25979 | Fence 输入 | `valid=0, ready=0, fire=0` | `Fence.state=s_wait` 且 `sbIsEmpty=0` | 14 个完整采样周期；Fence 等待此前 store 排空，不能接收新的 Fence uop。 |
| 25980 | Fence → TLB | `sfence_valid=0` | 刚观察到 `sbIsEmpty=1`，状态转换在下一周期生效 | 1 周期转换开销。 |
| 25981–25985 | SFENCE 广播链 | Fence 输出在 25981 `fire=1`；TLB 分级消费者后续有效 | `fenceDelay` 与 TLB/repeater/storage 级联 | 4 周期内覆盖 ITLB、DTLB 和 PTW。 |
| 25984–25990 | ROB / frontend 恢复 | `flushOut`→redirect→frontend redirect | `flushPipe` 的架构要求 | 正常提交被阻塞，前端在 25990 才接收恢复 redirect。 |

可归因于目标指令的最大延迟是 `s_wait` 阶段的 store-buffer drain。若要缩短该场景中的停顿，应减少 SFENCE 之前未排空的 store，或改善 store buffer 的清空带宽；在此波形中没有证据显示 DCache miss、TLB miss、LSQ replay 或功能单元竞争是本次等待的主因。

## 架构态与异常检查

- 目标指令提交调试信息：PC `0x800001c0`，指令 `0x12f90073`，FTQ `1/1`，ROB `45`。
- 最终退休点为周期 `25991`、time `51983`；`isCommit=1` 且 `commitValid[0]=1`。
- 提交时 `rfWen=fpWen=vecWen=0`，无整数、浮点或向量寄存器写回；`ldest=pdest=0`。
- 波形中未观察到该 uop 的异常或中断 trap。仿真使用 `--no-diff`，因此没有启用外部 difftest 比较结果；本报告以 ROB 提交调试口和最终 `GOOD TRAP` 作为架构完成证据。

## FSM 状态汇总

| 模块 | 信号 | 状态值 | 周期范围 | 作用 |
|---|---|---:|---|---|
| `exus_7.Fence` | `state[2:0]` | `0 (s_idle)` | 25965 | 接收目标 uop，`in_valid && in_ready`。 |
| `exus_7.Fence` | `state[2:0]` | `1 (s_wait)` | 25966–25980 | 请求 store-buffer flush 并等待 `sbIsEmpty`。 |
| `exus_7.Fence` | `state[2:0]` | `2 (s_tlb)` | 25981 | 产生 `sfence_valid` 和 `out_valid`。 |
| `exus_7.Fence` | `state[2:0]` | `0 (s_idle)` | 25982 起 | SFENCE 请求已发送，可接收后续 uop。 |
| ROB | `isWalk` | `1` | 25986–25987 | 处理 redirect 后的 ROB walk，阻塞正常提交。 |

## 场景判定与局限

**满足的关键检查项：**

1. 演示程序实际执行了非零寄存器形式的 `sfence.vma s2,a5`，其地址操作数为 `0x80001800`、ASID 值为 `0`。
2. Fence FSM 完整经历 `s_idle → s_wait → s_tlb → s_idle`，并明确证明等待条件是 `sbIsEmpty=0`。
3. SFENCE 广播到前端 ITLB 与内存侧 DTLB/PTW 的多级消费者。
4. `flushPipe` 触发 ROB `flushOut`、backend redirect 与前端恢复；恢复地址是顺序下一条 `0x800001c4`。
5. 指令最终正常退休、无寄存器写回、无异常，整个程序以 `GOOD TRAP` 结束。

**局限：** 本次使用的是全量 FST 和 `--no-diff` 模式。目标 uop 的后端稳定身份可由 `robIdx=45` 贯穿 Fence、flush 和 redirect；但前端存在大量复制的 PC/uop 调试信号，未从中建立一个可证明唯一的 fetch→IBuffer→decode 对应链。因此前端部分只报告已由 FTQ 标识和有效 redirect 直接证明的事实，未把不具唯一性的候选 PC 信号作为结论。

## 代码依据

- [DecodeUnit.scala:228](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L228)：`SFENCE_VMA` 的 `FenceOpType.sfence`、`noSpec`、`blockBack`、`flushPipe` 译码属性。
- [Fence.scala:26](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/Fence.scala#L26)：`FenceIO`、FSM、store-buffer 等待和 SFENCE 载荷产生逻辑。
- [Backend.scala:836](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/Backend.scala#L836)：向内存侧和前端复制 `SfenceBundle`。
- [TLB.scala:66](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala#L66)：`DelayN` 后的 MMU/流水线失效条件。
- [Rob.scala:631](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L631)：`flushOut` 的产生和下一周期 redirect 的关系。
- [Rob.scala:781](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L781)：flush/redirect 对提交的阻塞逻辑。
