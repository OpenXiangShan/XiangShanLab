# 昆明湖 V2：HFENCE.VVMA 演示程序与波形分析报告

## 结论摘要

**场景满足。** 演示程序在机器模式执行了 `HFENCE.VVMA x0, x0`，其编码为
`0x22000073`。全量 FST 波形证明该指令被译码为 Fence 功能单元的
`hfence_v`（`fuOpType=19`）、携带 `flushPipe=1`、等待 StoreBuffer 清空后向
TLB 发出 `hv=1/hg=0` 的失效请求，随后触发恢复到下一条指令的前端 redirect，
并在 commit trace 中正常退休；仿真打印 `HFENCE.VVMA demo: retired`，最后命中
GOOD TRAP。

本分析使用 `/home/yanyusong/wavekit` 开源仓库中的 `wavekit.FstReader` 解析和
查询 FST，采样时钟为 `TOP.clock` 上升沿。波形时钟周期与仿真时间的关系为
`time = 2 * cycle`。

| 项目 | 值 |
|---|---|
| 指令 PC | `0x8000013a` |
| 指令 / 操作数 | `0x22000073`，`HFENCE.VVMA x0, x0` |
| 稳定 ROB 标识 | `ROB=126` |
| 波形文件 | `/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-17-26-50.fst` |
| 仿真结束 | GOOD TRAP，PC=`0x8000015c` |
| 指令退休 | cycle `7896`，time `15792`，commit lane 0，`iretire=2` |

## 演示程序与构建

源程序位于 `/home/yanyusong/cbo-kmhv2/nexus-am/apps/hfence_vvma/hfence_vvma.c`，
Makefile 位于 `/home/yanyusong/cbo-kmhv2/nexus-am/apps/hfence_vvma/Makefile`。
默认 `riscv64-xs` 的 `MARCH` 未列出 H 扩展，故程序用 `.word` 固定编码，避免
汇编器因助记符扩展检查拒绝汇编；全局符号 `hfence_vvma_target` 保留精确 PC。

```c
asm volatile(
    ".globl hfence_vvma_target\n"
    "hfence_vvma_target:\n"
    ".word 0x22000073\n"
    ::: "memory");
```

构建命令（每次均先配置环境）：

```bash
source ~/cbo-kmhv2/env.sh
make -C "$AM_HOME/apps/hfence_vvma" ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=1
```

生成镜像：
`/home/yanyusong/cbo-kmhv2/nexus-am/apps/hfence_vvma/build/hfence_vvma-riscv64-xs.bin`。

反汇编确认：

```text
000000008000013a <hfence_vvma_target>:
    8000013a:  22000073    .word 0x22000073
```

仿真命令：

```bash
source ~/cbo-kmhv2/env.sh
cd "$NOOP_HOME"
./build/emu --dump-wave-full --no-diff \
  -i "$AM_HOME/apps/hfence_vvma/build/hfence_vvma-riscv64-xs.bin"
```

`--no-diff` 是题目要求的一部分，因此本次没有 NEMU difftest 比较；程序自身的
“retired”输出、commit trace 与 GOOD TRAP 是本次运行成功的判据。

## 波形时间线

下表所有周期均为 `TOP.clock` 上升沿采样，`fire = valid && ready`。

| cycle / time | 边界或模块 | 波形证据 | 解释 |
|---|---|---|---|
| `7700–7815` / `15400–15630` | Rename → Dispatch | `fromRename[0].valid=1`、`ready=0`，PC=`0x8000013a`，ROB=`126` | 指令已重命名，但 Dispatch 后压；共 116 个采样周期未 fire。 |
| `7816` / `15632` | Rename → Dispatch → IssueQueue 6 | `fromRename[0].valid=ready=1`；`toIssueQueues[6].valid=ready=1`，ROB=`126` | 两个边界均 fire，目标离开 Rename 并进入整数 IssueQueue。 |
| `7820` / `15640` | Issue → Fence | Fence `io_in.valid=ready=1`，`fuOpType=19`，ROB=`126`，`flushPipe=1` | 目标发射到 Fence 功能单元。 |
| `7821–7881` / `15642–15762` | Fence `s_wait` | `state=1`，`sbIsEmpty=0`，`flushSb=1`，`io_in.ready=0` | StoreBuffer 未清空，Fence 必须等待；这是该指令主要的可归因停顿（61 cycles）。 |
| `7882` / `15764` | Fence 等待结束 | `sbIsEmpty=1` | 满足状态机从 `s_wait` 转向 TLB 阶段的条件。 |
| `7883` / `15766` | Fence `s_tlb` / Writeback | `state=2`，`sfence.valid=1`，`hv=1`，`hg=0`，`rs1=rs2=1`，`addr=id=0`；`io_out.valid=ready=1` | 向 TLB 发送 HFENCE.VVMA 请求并完成 Fence 输出 fire。`rs1=rs2=1` 是“寄存器字段为 x0”的编码判定布尔值。 |
| `7892` / `15784` | Backend → FTQ redirect | `redirect.valid=1`，`target=0x8000013e`，`ftqIdx=56`，`offset=0`，`level=1`，`isMisPred=0`，异常位均为 0 | `flushPipe` 引发前端恢复；目标为该 Fence 的顺序下一条指令，不是分支误预测或异常。 |
| `7896` / `15792` | Commit trace lane 0 | `valid=1`，`iaddr=0x8000013a`，`iretire=2` | 指令正常退休。 |

## 执行路径与握手

### 前端、Decode、Rename、Dispatch

- 在所选窗口中，目标在 `Dispatch.io_fromRename_0` 上保持 PC=`0x8000013a`、ROB=`126`。
  `valid=1 && ready=0` 从 cycle `7700` 延续到 `7815`，是已由波形证明的后端背压，
  不能误认为 PC 线的稳定值等于反复传输。
- cycle `7816`，`fromRename[0]` 和 `toIssueQueues[6]` 都是 `valid=ready=1`，因此
  目标真正进入 IssueQueue；该 ROB ID 随后在 Fence 输入/输出端仍为 `126`。
- Decode 源码将 `HFENCE_VVMA` 分类为 `FuType.fence`、`FenceOpType.hfence_v`，并设置
  `noSpec`、`blockBack` 与 `flushPipe`。波形的 `fuOpType=19` 和 `flushPipe=1` 与之吻合。

### Issue、Fence、TLB

- Fence 输入在 cycle `7820` fire；`io_in.ready` 在 `s_wait` 期间为 0，所以任何随后
  Fence 指令不能进入该单元。
- cycle `7821–7881` 的 `sbIsEmpty=0` 直接解释了停顿：状态机同时输出 `flushSb=1`，
  等待 StoreBuffer 清空。此处并非 TLB miss、Load/Store replay 或异常造成的延迟。
- cycle `7883`，`sfence.valid=1 && hv=1 && !hg` 精确标识为 HFENCE.VVMA 通路；输出
  `valid=ready=1`，故写回边界确实 fire。该指令无目的寄存器，也没有 LQ/SQ/地址数据
  事务，故不应把它当作普通 load/store 跟踪。

### Redirect、异常与提交

- `backend.io_frontend_toFtq_redirect_valid` 仅在目标后面的 cycle `7892` 观察到有效。
  载荷为 `target=0x8000013e`、`isMisPred=0`、`backendIGPF=0`、`backendIPF=0`、
  `backendIAF=0`、`debugIsMemVio=0`。因此它是 Fence 的流水线 flush/recovery，而不是
  分支预测、取指异常或内存违例。
- cycle `7896` 的 commit trace 以 PC 匹配目标，证明它未被 redirect 冲刷。FST 未导出该
  commit 的 GPR/FPR/vector 写回或完整 CSR difftest 记录；对于无目的寄存器的 Fence，
  可见的架构态证据是正常退休与无异常 redirect 载荷。

## Bubble / 性能影响

| 区间 | 边界 | `valid/ready/fire` | 归因 | 影响 |
|---|---|---|---|---|
| `7700–7815` | Rename → Dispatch | `1/0/0` | 波形证明为 Dispatch backpressure；该窗口没有导出足以继续归因到具体 ROB/IQ 门控条件的信号 | 指令在 Rename 端保留 116 cycles。 |
| `7821–7881` | Fence 状态机 | Fence 入端 `ready=0`，`sbIsEmpty=0`，`flushSb=1` | StoreBuffer 未空 | Fence/TLB 请求延后 61 cycles。 |
| `7883` | Fence 输出 | `1/1/1` | StoreBuffer 已空 | 单周期发送 TLB 请求和写回。 |
| `7892–7896` | Redirect → Commit | redirect 后仍正常提交 | `flushPipe` 的必要恢复 | 无异常、无误预测；从 redirect 到退休为 4 cycles。 |

本例最大的可归因延迟是 StoreBuffer drain。若要降低该类 Fence 的尾延迟，应优先减少
Fence 到达时的未提交 store 数量，或缩短 StoreBuffer drain/ack 路径；不能从本波形把
该 61-cycle 延迟归因给 TLB，因为 TLB 请求尚未发出时 `sbIsEmpty` 已明确为 0。

## 关键源码依据

### Decode 分类

[`DecodeUnit.scala:489`](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L489>)：

```scala
HFENCE_GVMA -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.fence,
  FenceOpType.hfence_g, SelImm.X, noSpec = T, blockBack = T, flushPipe = T),
HFENCE_VVMA -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.fence,
  FenceOpType.hfence_v, SelImm.X, noSpec = T, blockBack = T, flushPipe = T),
```

### Fence 状态机与 TLB 输出

[`Fence.scala:47`](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/Fence.scala#L47>)：

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
```

### TLB 接收 HFENCE.VVMA

[`TLBStorage.scala:216`](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala#L216>)：

```scala
val hfencev_valid = sfence.valid && sfence.bits.hv
when (hfencev_valid) {
  when (hfencev.bits.rs2) {
    v.zipWithIndex.map { case (a, i) =>
      a := a && !(entries(i).s2xlate =/= noS2xlate && entries(i).vmid === io.csr.hgatp.vmid)
    }
  }
}
```

### 特权检查

[`NewCSR.scala:1479`](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L1479>)：

```scala
io.toDecode.illegalInst.hfenceVVMA := isModeHU
io.toDecode.virtualInst.hfence     := isModeVS || isModeVU
```

本演示由 AM 启动在 M 模式，波形中目标正常进入 Fence、没有异常 redirect，符合上述
特权检查未将其阻止的预期。

## 场景判定

满足以下全部检查项：

1. 反汇编与波形均锚定到 PC `0x8000013a`、编码 `0x22000073`。
2. Fence 输入的 `fuOpType=19`、`hv=1`、`hg=0`、`rs1=rs2=1` 与
   `HFENCE.VVMA x0,x0` 语义一致。
3. Fence 因 StoreBuffer 非空正确等待，清空后向 TLB 发出请求。
4. `flushPipe` 产生的 redirect 指向顺序下一条 `0x8000013e`，无误预测、无异常、无内存违例。
5. 指令在 cycle `7896` 正常退休，程序输出 retired 并以 GOOD TRAP 结束。
