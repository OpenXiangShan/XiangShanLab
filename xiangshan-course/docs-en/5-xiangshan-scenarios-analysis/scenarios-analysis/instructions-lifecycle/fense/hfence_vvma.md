# 香山昆明湖执行 HFENCE_VVMA 指令的流程分析

## HFENCE_VVMA 指令介绍

### 这条指令是什么
TODO

### 这条指令会做什么
TODO

### 这条指令对程序执行有什么帮助
TODO

## 香山昆明湖源代码分析
TODO

## HFENCE_VVMA 演示程序
TODO

## 波形图分析

### 分析方法、对象与结论

本节使用 `/home/yanyusong/wavekit` 开源仓库的 `wavekit.FstReader`，按照
`TOP.clock` **上升沿**对全量波形
`/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-17-26-50.fst`
取样。cycle `N` 对应的仿真时间为 `2*N`。目标静态指令位于
`PC=0x8000013a`，指令字为 `0x22000073`，即 `HFENCE.VVMA x0, x0`；最终动态
实例以 `ROB=126` 串联各后端阶段。

结论是：该指令被 Decode 正确分类为 `FuType.fence / FenceOpType.hfence_v`，带
`blockBackward=1`、`flushPipe=1`；它不是 Load/Store，因此不分配 LQ/SQ，不向
LoadUnit/StoreUnit 发普通地址翻译或 DCache 访问。它先通过 StoreBuffer drain 保证
更早的 store 已可见，再同时通知前端 ITLB 与 MemBlock 的 load/store/prefetch DTLB
及 PTW；TLB 延迟级之后执行 `hfencev_valid` 的失效。最后，`flushPipe` 产生指向
顺序下一条 `0x8000013e` 的 redirect，目标在 cycle `7896` 正常退休。

> 说明：下文只将 `valid && ready` 记为真实边界传输（fire）。一些内部 IssueQueue
> 选中位、ROB entry 的状态机及每个 TLB entry 的 `v` 位没有以能和 ROB=126 一一对应的
> 形式导出；对这些信号只说明 Chisel 逻辑和已观察到的相邻接口，不把源码行为伪称为
> 波形观测值。

### 全局逐周期时间线

| cycle / time | 阶段、稳定身份 | `valid/ready/fire` 或状态 | 关键值与解释 |
|---|---|---|---|
| `6809 / 13618` | Decode lane 2 的早期动态副本 | `decode.io_in_2.valid=decode.io_out_2.valid=1` | PC 与指令字均为目标值，译码为 `fuType=512`、`fuOpType=19`、`blockBackward=1`、`flushPipe=1`。 |
| `6812 / 13624` | 早期副本所在的前端恢复窗口 | `backend.io_frontend_toFtq_redirect_valid=1` | 此副本尚未成为最后退休实例；之后同 PC 再次出现。 |
| `6870 / 13740` | Decode lane 0，最终实例的 Decode 输入/输出 | `io_in_0.valid=io_out_0.valid=1` | `PC=0x8000013a`、`instr=0x22000073`、`fuType=512`、`fuOpType=19`、`blockBackward=1`、`flushPipe=1`、异常 bit 0 为 0。 |
| `6871–6930 / 13742–13860` | Decode→Rename，lane 0 | Rename `io_in.valid=1, ready=0`；`io_out.valid=1, ready=0` | 目标被 backpressure 保留 60 cycles；PC 仍为目标，预分配/携带的 ROB ID 为 `126`。 |
| `6931 / 13862` | Rename→RenamePipeDispatch | Rename 入/出端和 `renamePipeDispatch.io_in` 都是 `1/1/fire` | 目标正式离开 Rename，带 ROB `126`。 |
| `6932–7815 / 13864–15630` | RenamePipeDispatch→Dispatch | `rpd.io_out.valid=1, ready=0`，`dispatch.fromRename.valid=1, ready=0` | 目标在 Dispatch 前被阻塞 884 cycles；PC=`0x8000013a`、ROB=`126` 保持为有效载荷。 |
| `7816 / 15632` | Dispatch / IssueQueue 6 | `rpd.out=1/1/fire`；`dispatch.toIssueQueues_6=1/1/fire` | 目标被 Dispatch 接收并进入整数 IssueQueue 6。 |
| `7820 / 15640` | Issue→Fence Exu | Fence `io_in=1/1/fire` | `fuOpType=19`、ROB=`126`、`flushPipe=1`；Fence 从 `s_idle` 接收该 uop。 |
| `7821–7881 / 15642–15762` | Fence `s_wait` | `state=1`、`io_in.ready=0`、`flushSb=1`、`sbIsEmpty=0` | Fence 要求清空 StoreBuffer，但后者尚未空；这是本指令最直接的 61-cycle 执行停顿。 |
| `7822–7827 / 15644–15654` | MemBlock SBuffer / DCache | `inner_sbuffer.io_flush_valid=1`；`io_dcache_req_valid=1` 于 `7824–7827` | SBuffer 接到 flush 后向 DCache 发出既有 store 的 drain 请求；这不是 HFENCE.VVMA 自己的 data cache invalidate 请求。 |
| `7881–7882 / 15762–15764` | SBuffer→Fence 反馈 | SBuffer `flush.empty=sbempty=1` 于 `7881`；Backend `sbIsEmpty=1` 于 `7882` | 一拍反馈延迟后，Fence 获准从 `s_wait` 转到 TLB 阶段。 |
| `7883 / 15766` | Fence `s_tlb` / 写回 | `state=2`；`sfence.valid=1`；`io_out=1/1/fire` | `rs1=1, rs2=1, addr=0, id=0, hv=1, hg=0`，精确选择 HFENCE.VVMA 且操作数为 `x0,x0`。 |
| `7885 / 15770` | ITLB、三类 DTLB、PTW 接收 | 各 `io_sfence_valid=1` | ITLB repeater、load DTLB、store DTLB、prefetch DTLB、PTW 同时收到这次全局 TLB 控制消息。 |
| `7886 / 15772` | PTW sfence delay | `sfence_tmp_delay.io_out_valid=1` | PTW 的一拍 delay 后输出控制载荷。 |
| `7887 / 15774` | TLB storage / MMU flush | ITLB `flush_mmu=1`；load/store/prefetch TLB storage `io_sfence_valid=1`；PTW cache `hfencev_valid=1` | 各 TLB/页表缓存真正消耗 HFENCE.VVMA；无普通 load/store 请求身份可与此 fence 对应。 |
| `7892 / 15784` | Backend→FTQ redirect | `redirect.valid=1` | `ftqIdx=56`、`offset=0`、`level=1`、target=`0x8000013e`；不是预测错误、取指异常、内存违例。 |
| `7896 / 15792` | Commit trace lane 0 | `valid=1`、`iretire=2` | `iaddr=0x8000013a`，目标正常退休。 |

### 1. 前端、Decode 与前端预测/redirect

#### 1.1 Decode 输入与译码结果

波形中目标 PC 先后出现两次：lane 2 的 cycle `6809` 和 lane 0 的 cycle `6870`。二者的
`instr` 均为 `0x22000073`、PC 均为 `0x8000013a`，Decode 控制字段也一致。cycle `6812`
出现 redirect，因此本分析以后一次 lane 0 实例为最终进入 ROB=126 并退休的动态指令。

最终实例在 `decode.io_in_0` 与 `decode.io_out_0` 同周期均为 valid；字段的含义如下：

| Decode 波形信号 | cycle `6870` 的值 | 来源与去向 | 含义 |
|---|---:|---|---|
| `io_in_0_bits_pc` / `io_out_0_bits_pc` | `0x8000013a` | IBuffer/Decode 输入 → Rename 前的 decoded bundle | 将静态指令锚定到正确 PC。 |
| `io_out_0_bits_instr` | `0x22000073` | 原始指令 → decoded bundle | 与反汇编的 `.word 0x22000073` 一致。 |
| `io_out_0_bits_fuType` | `512` | Decode 表 → 调度/执行功能单元选择 | `FuType.fence` 的 one-hot 编码。 |
| `io_out_0_bits_fuOpType` | `19` | Decode 表 → Fence `func` | `FenceOpType.hfence_v`。 |
| `io_out_0_bits_blockBackward` | `1` | Decode → 后端控制 | 该 fence 会阻止后续工作越过其控制边界。 |
| `io_out_0_bits_flushPipe` | `1` | Decode → Fence 输出/ROB redirect | 要求在完成时触发流水线恢复。 |
| `io_out_0_bits_exceptionVec_0` | `0` | Decode 异常向量 → ROB | 此处没有取指/非法指令异常标志。 |

Chisel 的 Decode 表直接给出这种分类：
[`DecodeUnit.scala:489`](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L489>)。

```scala
HFENCE_VVMA -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.fence,
  FenceOpType.hfence_v, SelImm.X, noSpec = T, blockBack = T, flushPipe = T),
```

这解释了为何波形的 `fuOpType=19`、`blockBackward=1`、`flushPipe=1` 同时成立。CSR
还会禁止 HU 或虚拟化模式的非法执行；本次 AM 程序运行于 M 模式，且最终实例的异常位和
后续 redirect 异常位均为 0。相关判定见
[`NewCSR.scala:1479`](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L1479>)：

```scala
io.toDecode.illegalInst.hfenceVVMA := isModeHU
io.toDecode.virtualInst.hfence     := isModeVS || isModeVU
```

#### 1.2 前端恢复而非分支误预测

对最终实例，Fence 的 `flushPipe=1` 在后端完成后成为 cycle `7892` 的
`backend.io_frontend_toFtq_redirect_valid=1`。载荷为：

| redirect 字段 | 值 | 解释 |
|---|---:|---|
| `ftqIdx.value` / `ftqOffset` | `56` / `0` | 被恢复的 FTQ 块和块内位置。 |
| `level` | `1` | 后端产生的 redirect 级别。 |
| `cfiUpdate.target` | `0x8000013e` | HFENCE.VVMA 后顺序下一条指令。 |
| `cfiUpdate.isMisPred` | `0` | 非分支预测错误。 |
| `backendIGPF/IPF/IAF` | `0/0/0` | 非取指 guest/page/access fault。 |
| `debugIsMemVio` | `0` | 非 Load/Store 内存违例恢复。 |

因此该 redirect 的生产者是带 `flushPipe` 的 fence 完成路径，而不是分支单元、LoadUnit
replay 或异常处理路径。波形没有提供能将 cycle `7892` 后第一个正确路径 IBuffer fire
与 ROB=126 直接配对的 frontend valid/ready 总线；这里仅报告已观测到的 Backend→FTQ
控制消息，不能把顺序 target 误称为分支预测 target。

### 2. Rename、Dispatch、ROB 与 Issue

#### 2.1 Decode→Rename：ROB 身份的建立

cycle `6871`，最终实例从 `decodePipeRenameModule` 到 `rename.io_in_0`。该接口的
`valid=1, ready=0` 持续到 cycle `6930`；因而虽然 PC/指令字段稳定，只有 `6931` 的
`valid=ready=1` 才是一次真正 fire。波形中同一有效载荷的
`rename.io_out_0_bits_robIdx_value=126`，之后的 `renamePipeDispatch`、Dispatch、Fence
输入、Fence 输出均保留 `126`，故它是本分析跨阶段使用的稳定身份。

Rename 并不为此指令分配整数目的物理寄存器：Decode 的第三源/目的类型为 `SrcType.X`，
Fence 输出也只携带 ROB/控制信息和恒定的 `res.data=0`。FST 对该目标没有导出可证明的
`pdest` 分配/释放事件，因此不报告虚构的物理寄存器号码。

#### 2.2 Rename→Dispatch：可量化的后压

目标在 `renamePipeDispatch.io_out_0` 从 cycle `6932` 到 `7815` 均为
`valid=1 && ready=0`，并且 `dispatch.io_fromRename_0` 同样是 `valid=1 && ready=0`；
PC=`0x8000013a`、ROB=`126` 始终由这个有效载荷携带。该区间为 **884 cycles**，是比
Fence 自身等待更长的上游后压。

cycle `7816`，`renamePipeDispatch.io_out_0` 与 `dispatch.io_fromRename_0` 均变为
`1/1/fire`；同一周期 `dispatch.io_toIssueQueues_6` 也为 `1/1/fire`，说明 Dispatch
接受该 uop 并将其送至整数 IssueQueue 6。FST 没有导出可把该周期 `ready=0` 的根因
唯一归结到 ROB 满、某个 IQ 满或 rename allocation 的单一门控信号，因此只能确认它是
Dispatch 边界后压，不应进一步臆测原因。

#### 2.3 Issue→Fence

cycle `7816` 的 IssueQueue 6 入队后，cycle `7820` Fence 单元的
`io_in_valid=1 && io_in_ready=1`。这两个已观察边界之间有 3 个完整周期；FST 没有导出
能直接按 ROB=126 索引的 scheduler select/grant 位，因此不能进一步把这 3 cycles 分成
唤醒、仲裁和执行端口冲突。可以确定的是，执行端接收时载荷仍为：

```text
ROB=126, fuOpType=19 (hfence_v), flushPipe=1, src0=0, src1=0
```

### 3. Fence 执行状态机、Store Unit 与 StoreBuffer

#### 3.1 Fence 状态机

Fence Chisel 的状态定义和握手逻辑见
[`Fence.scala:47`](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/Fence.scala#L47>) 与
[`Fence.scala:65`](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/Fence.scala#L65>)：

```scala
val s_idle :: s_wait :: s_tlb :: s_icache :: s_fence :: s_nofence :: Nil = Enum(6)
sbuffer := state === s_wait
sfence.valid := state === s_tlb &&
  (func === FenceOpType.sfence || func === FenceOpType.hfence_v || func === FenceOpType.hfence_g)
sfence.bits.hv := func === FenceOpType.hfence_v
sfence.bits.hg := func === FenceOpType.hfence_g
when (state === s_wait &&
  ((func === FenceOpType.sfence || func === FenceOpType.hfence_g ||
    func === FenceOpType.hfence_v) && sbEmpty)) { state := s_tlb }
io.in.ready := state === s_idle
io.out.valid := state =/= s_idle && state =/= s_wait
```

它与波形一一对应：

| Fence 波形信号 | `7820` | `7821–7881` | `7883` | 作用 |
|---|---:|---:|---:|---|
| `io_in.valid/ready` | `1/1` | `0/0` | `0/0` | cycle `7820` 接收目标；`s_wait` 禁止下一条 Fence 进入。 |
| `state` | `0` (`s_idle`) | `1` (`s_wait`) | `2` (`s_tlb`) | 输入 fire 后进入等待；SBuffer 空后进入 TLB 操作。 |
| `io_fenceio_sbuffer_flushSb` | `0` | `1` | `0` | 在等待阶段持续请求 StoreBuffer drain。 |
| `io_fenceio_sbuffer_sbIsEmpty` | `0` | `0` | `1` | SBuffer 空是状态转换门控条件。 |
| `io_fenceio_sfence_valid` | `0` | `0` | `1` | 仅在 `s_tlb` 输出 TLB 控制消息。 |
| `io_out.valid/ready` | `0/1` | `0/1` | `1/1` | cycle `7883` 对 ROB/写回端 fire。 |

#### 3.2 StoreBuffer、Store Unit 与 DCache 的特殊处理

HFENCE.VVMA 不是 StoreUnit 发射的 store，也没有 SQ index；对 Store Unit 的特殊影响是：
Fence 必须令**已在 StoreBuffer 中的更早 store**排空，之后才能失效地址翻译。波形在此
提供了完整因果链：

1. `7821`：Backend `io_fenceio_sbuffer_flushSb=1`，但 `sbIsEmpty=0`。
2. `7822`：`memBlock.inner_sbuffer.io_flush_valid=1`，SBuffer 收到 flush。
3. `7824–7827`：`inner_sbuffer.io_dcache_req_valid=1`，SBuffer 向 DCache 的 store 端口
   排出已有数据；这是观察到的唯一与 DCache 直接相关的活动。
4. `7881`：`inner_sbuffer.io_flush_empty=1`、`io_sbempty=1`；`7882`：经 `RegNext`
   回到 Backend 的 `sbIsEmpty=1`。
5. `7883`：Fence 退出 `s_wait`，开始 TLB 失效。

MemBlock 的连接正是上述一拍关系的源码依据，见
[`MemBlock.scala:1769`](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L1769>)：

```scala
val fenceFlush = io.ooo_to_mem.flushSb
val stIsEmpty = sbuffer.io.flush.empty && uncache.io.flush.empty
io.mem_to_ooo.sbIsEmpty := RegNext(stIsEmpty)
sbuffer.io.flush.valid := RegNext(fenceFlush || atomicsFlush || cmoFlush)
uncache.io.flush.valid := sbuffer.io.flush.valid
lsq.io.flushSbuffer.empty := sbuffer.io.sbempty
```

这也解释了为何 `sbuffer.io_flush_empty` 先在 `7881` 变高、Fence 端的 `sbIsEmpty` 在
`7882` 才可见。LoadUnit/StoreUnit 本身没有收到“把 ROB=126 当成 load/store 执行”的
控制，因为该 uop 的 `fuType` 是 fence；它们受到的间接影响是自己的 DTLB 在后续全局
sfence 扇出中失效。波形未见针对 ROB=126 的 LQ/SQ 分配、LoadUnit replay、StoreUnit
地址/数据写回，因此不存在应追踪的 load/store 地址、掩码或 DCache load miss。

HFENCE.VVMA 也**不执行 ICache/DCache line invalidation**：Fence 在本次只进入
`s_tlb`，而 `fencei` 仅在 `s_icache` 才为高。DCache 的上述请求是 SBuffer drain，
不是 cache flush。该区分避免把“清空脏 store”误写成“使 DCache 失效”。

### 4. Backend→MemBlock→MMU/TLB 的控制扇出

#### 4.1 Backend 与 MemBlock

cycle `7883`，Fence 的 `sfence.valid` 在同一拍复制到：

```text
backend.io_mem_sfence_valid       = 1
backend.io_frontendSfence_valid   = 1
```

这不是两个独立的 fence，而是同一个 `FenceIO.sfence` 的两个消费者。连接代码见
[`Backend.scala:836`](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/Backend.scala#L836>)：

```scala
io.mem.sfence := fenceio.sfence
...
io.frontendSfence := fenceio.sfence
```

两拍后（cycle `7885`），波形同时看到下列 MemBlock 接口的 `io_sfence_valid=1`：

- `inner_itlbRepeater3`：前端 ITLB 控制通路；
- `inner_dtlb_ld_tlb_ld`：LoadUnit 使用的 DTLB；
- `inner_dtlb_st_tlb_st`：StoreUnit 使用的 DTLB；
- `inner_dtlb_prefetch_tlb_prefetch`：DTLB prefetch 通路；
- `inner_ptw.ptw`：PageTableWalker。

PTW 的 sfence 输入由 MemBlock 直接连接，见
[`MemBlock.scala:669`](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L669>)：

```scala
ptw.io.hartId := io.hartId
ptw.io.sfence <> sfence
```

这说明 HFENCE.VVMA 的“特殊处理”不是向某个 LoadUnit 或 StoreUnit 发普通内存事务，
而是将同一翻译失效控制广播到所有可能保存 stage-1/stage-2 地址翻译状态的消费者。

#### 4.2 ITLB、DTLB、PTW 与 TLB storage

TLB 模块对 sfence 有 `fenceDelay` 级延迟，并用 `flush_mmu` 统一失效正在保存的转换
状态。源码见 [`TLB.scala:66`](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala#L66>)：

```scala
val sfence = DelayN(io.sfence, q.fenceDelay)
val csr = DelayN(io.csr, q.fenceDelay)
val flush_mmu = sfence.valid || csr.satp.changed || csr.vsatp.changed ||
  csr.hgatp.changed || csr.priv.virt_changed
val mmu_flush_pipe = sfence.valid && sfence.bits.flushPipe
```

波形的延迟链与之匹配：

| 控制路径 | 接收 / 生效 cycle | 波形观察 | 含义 |
|---|---:|---|---|
| Fence、Backend `mem.sfence`、`frontendSfence` | `7883` | `valid=1` | Fence 输出是控制消息的源。 |
| ITLB repeater、load/store/prefetch DTLB、PTW 输入 | `7885` | 每个 `io_sfence_valid=1` | 控制广播抵达各 consumer。 |
| PTW `sfence_tmp_delay.io_out_valid` | `7886` | `valid=1` | PTW 延迟寄存器传出消息。 |
| ITLB `flush_mmu` | `7887` | `1` | ITLB 真正执行 MMU flush。 |
| load/store/prefetch TLB `entries.io_sfence_valid` | `7887` | 均为 `1` | 三类 DTLB storage 消耗消息。 |
| PTW cache `hfencev_valid` | `7887` | `1` | PTW cache 明确走 HFENCE.VVMA 分支。 |

因此，**ITLB、Load DTLB、Store DTLB、Prefetch DTLB 和 PTW 都有特殊控制响应**；但该
响应是广播式翻译状态失效，不是目标指令触发的普通取指/load/store translation request。
特别地，ITLB 波形直接导出了 `flush_mmu=1`；DTLB 的相同泛化逻辑由 `entries.io_sfence_valid`
和 TLB 源码共同证明，FST 未导出可单独命名为 `flush_mmu` 的 DTLB 信号。

#### 4.3 `x0,x0` 对 HFENCE.VVMA 失效范围的影响

cycle `7883` 的 Fence 输出为：

```text
sfence.valid=1, rs1=1, rs2=1, addr=0, id=0, hv=1, hg=0
```

这里 `rs1/rs2` 是“对应编码字段是否为 x0”的布尔信号，而不是 x0 寄存器读出的数值；
所以二者为 1 正确表示 `HFENCE.VVMA x0,x0`。TLBStorage 以 `hv` 选择 HFENCE.VVMA
分支，见 [`TLBStorage.scala:216`](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala#L216>)：

```scala
val hfencev_valid = sfence.valid && sfence.bits.hv
when (hfencev_valid) {
  when (hfencev.bits.rs2) {
    // all addr and all asid
    v.zipWithIndex.map { case (a, i) =>
      a := a && !(entries(i).s2xlate =/= noS2xlate &&
                  entries(i).vmid === io.csr.hgatp.vmid)
    }
  }
}
```

由于 `rs2=1`，本例选择“全部地址、全部 ASID、当前 VMID 的 stage-2 translation
entry”范围。源码在紧随其后的注释中还说明：当前 L1TLB 对 two-stage translation 的
地址匹配有限制，HFENCE.VVMA 不按地址做细粒度匹配，而按该实现的规则清除相应 entry。
FST 没有逐 entry 导出 `v` 数组的前后快照，故不能列出“第几个 entry 从 1 变 0”，但
`hfencev_valid=1` 已在 cycle `7887` 由 PTW cache 波形确认。

### 5. 写回、ROB、提交与性能影响

Fence 的 `io_out_valid=1 && io_out_ready=1` 出现在 cycle `7883`，输出携带同一
ROB=`126`；这个 fire 代表 Fence 功能单元完成，而不是指令已经提交。`flushPipe` 的
redirect 在 `7892` 才出现，最终 commit trace 在 `7896` 出现：

| 项目 | 波形值 | 结论 |
|---|---|---|
| Fence 写回 | `7883`，`out.valid=out.ready=1` | 功能单元完成并把完成状态交给后端。 |
| redirect | `7892`，target=`0x8000013e`，`isMisPred=0` | 清除/恢复 younger pipeline，而非异常或分支纠正。 |
| commit | `7896`，lane 0 `valid=1`、`iaddr=0x8000013a`、`iretire=2` | ROB 未冲刷该 fence，正常退休。 |
| GPR/FPR/vector 写回 | 未导出且该 fence 无目的寄存器 | 不应期待寄存器写回。 |
| LQ/SQ、load/store difftest | 无目标对应项 | 该指令不是 memory data operation；只有对既有 store 的 SBuffer drain。 |

可量化的性能影响有两类：

1. **上游后压**：最终动态实例的 RenamePipeDispatch→Dispatch 边界在
   `6932–7815` 为 `valid=1, ready=0`（884 cycles）。波形没有导出足以唯一归因的
   ROB/IQ/LSQ 门控源，故记录为 Dispatch 后压而不作进一步归因。
2. **Fence 固有 drain**：Fence 在 `7821–7881` 等待 SBuffer 非空（61 cycles），这是
   能由 `flushSb=1`、`sbIsEmpty=0`、`sbuffer.io_dcache_req_valid` 共同证明的直接原因。
   若要缩短该类 HFENCE.VVMA 延迟，应优先减少到达 fence 时仍待排出的 store，或优化
   SBuffer→DCache drain 路径，而不是把延迟归因给 TLB miss。

### 信号来源与去向汇总

| 生产者 | 信号 | 消费者 | 波形证据 / 作用 |
|---|---|---|---|
| Decode | `fuType=512`、`fuOpType=19`、`blockBackward=1`、`flushPipe=1` | Rename、Dispatch、Issue、Fence | cycle `6870` 确认目标被当作 HFENCE.VVMA fence。 |
| Rename | `robIdx=126` | RenamePipeDispatch、Dispatch、Fence、ROB | 从 cycle `6871` 起作为稳定动态身份。 |
| Dispatch | `toIssueQueues_6.valid/ready` | IssueQueue 6 | cycle `7816` fire。 |
| Issue/Exu 输入 | Fence `io_in.valid/ready`、`fuOpType`、ROB | Fence FSM | cycle `7820` fire。 |
| Fence FSM | `flushSb` | Backend→MemBlock SBuffer | `7821` 置位，触发 drain。 |
| SBuffer | `flush.empty/sbempty` | MemBlock→Backend `sbIsEmpty` | `7881` 内部为空，`7882` Fence 观察到为空。 |
| Fence FSM | `sfence.valid, hv, hg, rs1, rs2, addr, id` | Backend mem/front-end sfence、MemBlock、TLB | `7883` 发出 HFENCE.VVMA 控制消息。 |
| Backend | `mem.sfence` / `frontendSfence` | ITLB、三类 DTLB、PTW | `7885` 各接口收到 valid。 |
| TLB / PTW | `flush_mmu` / `hfencev_valid` | TLB storage、PTW cache | `7887` 完成翻译状态失效。 |
| Backend/ROB | `toFtq.redirect` | FTQ/Frontend | `7892` 恢复到 `0x8000013e`。 |
| Commit trace | `iaddr/iretire` | Difftest/trace endpoint | `7896` 确认目标退休。 |

### 波形、代码与语义一致性检查

1. **一致**：反汇编编码 `0x22000073`、Decode `fuOpType=19`、Fence `hv=1/hg=0` 和
   TLB `hfencev_valid=1` 构成完整的 HFENCE.VVMA 证据链。
2. **一致**：Decode `flushPipe=1` 与 cycle `7892` 的顺序 PC redirect 一致；该 redirect
   无 `isMisPred`、取指 fault 或 memory violation 标记。
3. **一致**：Fence 源码要求 `sbEmpty` 后进入 `s_tlb`；波形中先有 SBuffer drain，再有
   `sbIsEmpty=1`、`sfence.valid=1`。
4. **限制**：FST 未导出能逐项观察的 L1TLB entry valid 阵列、按 ROB=126 索引的 Issue
   select/grant、完整 difftest CSR/寄存器快照，且本次仿真命令使用 `--no-diff`。本节因此
   用已转储的 TLB 输入/存储 valid、ITLB `flush_mmu`、commit trace 和程序 GOOD TRAP
   证明执行完成，不声称进行了 NEMU 架构态比较。
