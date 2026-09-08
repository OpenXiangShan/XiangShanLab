# XiangShan Issue #6265：非对齐 MMIO Load 为何 DUT 报 5、REF 报 4

## 0. 结论

测试程序在 `0x800012f8` 执行：

```asm
ld s8, 240(t6)
```

运行时 `t6=1`，有效地址为 `0xf1`。`ld` 是 8 字节访问，要求地址按 8 字节对齐，而
`0xf1` 不满足。双方对“哪条指令、哪个地址、是否未对齐”没有分歧，分歧只在异常分类：

- XiangShan（DUT）把该地址经 PMA 判为“可读但不可缓存”，从而具有 MMIO 属性，走
  `非对齐 + MMIO` 分支，报告 **load access fault，mcause=5**。
- NEMU（REF）把 `0xf1` 归入它配置的 MMIO 空间，在 MMIO 路径里先做非对齐检查，直接
  报告 **load address misaligned，mcause=4**。

XiangShan 的 cause 5 不是异常编码错误，而是它对该低地址区域非对齐访问的分类结果；
该 issue 的维护者结论为 `type: bug/invalid`。

## 1. 触发指令与地址

反汇编给出目标指令及上下文：

```asm
# testcase.dump
800012f8: ld s8,240(t6)
800012fc: srli s3,s3,0x1
80001300: roriw tp,s9,0xf
```

运行时 REF dump 提供计算地址所需状态：

```text
# runtime.log
t6: 0x0000000000000001
satp/hgatp/vsatp: 0x0000000000000000   # Bare，无页表
mcause: 0x0000000000000004  mepc: 0x00000000800012f8  mtval: 0x00000000000000f1
```

地址计算与对齐判断：

```text
base   = t6  = 0x1
imm    = 240 = 0xf0
vaddr  = base + imm = 0xf1
访问宽度 = ld = 8 bytes
0xf1 & 0x7 = 1        # 非 8 字节对齐
```

因此这是一次 8 字节未对齐的 load。DUT 的 commit trace 也确认异常落在同一条指令：

```text
# runtime.log
[31] exception pc 0x00000000800012f8 inst 0f0fbc03 cause 0x0000000000000005
```

`0x80001314`（日志中 DiffTest 终止的 PC）只是比较 mcause 的暂停位置，不是异常指令本身。

## 2. XiangShan 的判定链

### 2.1 地址与对齐：align=0

LoadUnit 对 8 字节访问检查地址低 3 位：

```scala
// NewLoadUnit.scala:310-335
val align = LookupTree(size, List(
  "b00".U -> true.B,
  "b01".U -> (bankOffset.take(1) === 0.U),
  "b10".U -> (bankOffset.take(2) === 0.U),
  "b11".U -> (bankOffset.take(3) === 0.U)
))
```

`size="b11"`(D) 检查 `bankOffset.take(3)`；`0xf1` 低 3 位为 `001`，故 `align=0`。
但这一步只说明“地址未对齐”，并不直接决定最终是 4 还是 5。

### 2.2 翻译与 PBMT：为何 tlbHit=1、pbmt=0

运行在 M 态且三个翻译 CSR 均为 Bare，TLB 不启用页表翻译：

```scala
// TLB.scala:121-143
val vmEnable = ... && (mode(i) < ModeM)   // M 态 + Bare → 0
val portTranslateEnable = privNeedTranslate && !useReqS1Paddr
```

注意：**不做页表翻译 ≠ 不经过 TLB**。普通 `ld` 的 `noQuery=0`（只有软件指令预取才
`noQuery=1`），请求仍走 TLB 正常请求/响应流程。由于 `enable=0`，TLB 的 miss 项被关
掉，返回的是 non-miss 的有效响应，物理地址直接取虚拟地址：

```scala
// TLB.scala:337-348, 394
val miss = (!hit && enable) || ...          // enable=0 → 该项恒 0
resp(i).bits.paddr(d) := Mux(enable, paddr, notTranslatePaddr)  // → paddr = vaddr
```

LoadUnit 据此定义：

```scala
// NewLoadUnit.scala:615-619
val tlbHit = tlbResp.valid && !tlbResp.bits.miss && !noQuery
val pbmt = Mux(tlbHit, tlbResp.bits.pbmt.head, Pbmt.pma)
```

于是 `tlbHit=1`、`pbmt=0`。`pbmt` 是这条 load 的页表内存类型值；TLB 对不翻译请求强制
输出 0：

```scala
// TLB.scala:421-432
resp(idx).bits.pbmt(d) := Mux(portTranslateEnable(idx), res, 0.U)
```

PBMT 编码（`MMUBundle.scala:453-466`）：`00`=PMA、`01`=NC、`10`=IO。因此本例
`pbmt=0` 表示“类型交给物理地址的 PMA 判定”，它不是 NC(`01`)。

### 2.3 PMA 判 0xf1：resp.ld=0、resp.mmio=1

`0xf1` 命中低地址 TOR 区间 `[0, 0x10000000)`（对应 PMA 项 `r=1,c=0`，见
`SoC.scala:57-71` 与运行时 PMA 表）。PMA 权限/属性计算：

```scala
// PMA.scala:210-218
resp.ld := TlbCmd.isRead(cmd) && !cfg.r
resp.mmio := !cfg.c
```

命令是读、`cfg.r=1`、`cfg.c=0`，代入：

```text
resp.ld   = isRead && !r = 1 && !1 = 0   // 允许读，无读权限 fault
resp.mmio = !c = !0 = 1                  // 不可缓存 → MMIO 属性
```

注意 `resp.ld` 不是“这是不是 load”，而是“这次 load 有没有读权限 fault”；`ld=0` 表示
**允许读**。PMP 侧同样不拒绝本次读取，因此本例不是无权限访问。

### 2.4 分类 isNC / isMMIO

```scala
// NewLoadUnit.scala:926-928
val isNC   = tlbHit && tlbAccessable && Pbmt.isNC(pbmt)
val isMMIO = tlbHit && tlbAccessable && (Pbmt.isIO(pbmt) || Pbmt.isPMA(pbmt) && pmp.mmio)
```

这里容易混的是：PMA 的 `c=0`（不可缓存）走的是 `mmio` 输出，而 `isNC` 只看 PBMT 是否
为 `01`。本例没有页表、`pbmt=0`(PMA)，所以：

```text
Pbmt.isNC(pbmt) = false  → isNC  = 0
Pbmt.isPMA(pbmt)= true
pmp.mmio        = 1      → isMMIO = 1
```

即“不可缓存”被分类成 MMIO 型（isMMIO=1），而不是 NC 型（isNC=0）。这是后续选择
cause 5 的关键前提。

## 3. 异常合成：af 与 am

核心两行（`NewLoadUnit.scala:931-950`）：

```scala
val afUnalignMMIO = !in.align.get && isMMIO
...
val af = afUnaccessable || afVectorUncache || afUnalignMMIO ||
         afTagError || afForwardDenied || afBypassDenied
...
val am = !in.align.get && accessType.isScalar() && isNC && !pmpUnaccessable

exceptionVec(loadAddrMisaligned) := am   // bit4
exceptionVec(loadAccessFault)    := af   // bit5
```

### 3.1 为什么锁定 afUnalignMMIO

`af` 是多个“或”项，只有逐项确认其它都为 0，才能说结果由 `afUnalignMMIO` 决定：

| af 来源 | 本例 | 依据 |
|---|---|---|
| `afUnaccessable` | 0 | `pmp.ld=0` → `pmpUnaccessable=0`；指令本身无 access fault 位 |
| `afVectorUncache` | 0 | 本条是标量 `ld`，不是向量 |
| `afUnalignMMIO` | 1 | `align=0 && isMMIO=1` |
| `afTagError` | 0 | 需 DCache tag error 且使能，本例无 |
| `afForwardDenied / afBypassDenied` | 0 | 无对应 forward/bypass 被拒源 |

所以：

```text
af = 0 || 0 || 1 || 0 || 0 || 0 = 1
```

另一边 `am` 需要 `isNC=1`，而本例 `isNC=0`：

```scala
val am = !align && scalar && isNC && !pmpUnaccessable
= 1 && 1 && 0 && 1 = 0
```

因此 LoadUnit 写入异常向量的结果是：

```text
exceptionVec[loadAddrMisaligned] = bit4 = 0
exceptionVec[loadAccessFault]    = bit5 = 1
```

## 4. 从 exceptionVec 到 mcause：两级

要澄清一点：上面两行赋值**并不直接决定 mcause**。它们只是设置这条指令 `exceptionVec`
里的 bit4/bit5。`mcause` 由后续 ROB + CSR 流水决定，分两级理解：

第一级（§3）：把这条 load 的异常向量写成 `bit5=1, bit4=0`。

第二级：异常向量随指令进入 ROB；ROB 只有在该指令到达**队首**时才处理异常，再用
`TrapHandleModule` 在整条异常向量里按优先级仲裁选出置位 bit，编码成号，最后写进
`mcause`：

```scala
// TrapHandleModule.scala:75-79
private val exceptionRegular = OHToUInt(highestPrioEX)
...
private val causeNO = Mux(hasIR, interruptNO, exceptionNO)

// TrapEntryMEvent.scala:117-118
out.mcause.bits.Interrupt     := isInterrupt && !isDTExcp
out.mcause.bits.ExceptionCode := Mux(isDTExcp, ExceptionNO.EX_DT.U, highPrioTrapNO)
```

因此“这两句一定等于 mcause=5”并不自动成立——还需要：该指令无其它更高优先级异常位、
且最终被 ROB 选中提交。对本例这两点都成立（Bare 无 page/guest fault、日志确认最终提交
的就是这条 `ld`），所以最终 `mcause=5`。日志中 DUT 的 cause5 也正是这条指令的结果。

## 5. NEMU REF 为何是 4

REF 是 `ready-to-run/riscv64-nemu-interpreter-so`，对应 NEMU 源码 `36342a16`、配置
`riscv64-xs-ref_defconfig`，其中：

```text
CONFIG_ENABLE_CONFIG_MMIO_SPACE=y
CONFIG_MMIO_SPACE_RANGE="0x0, 0x7FFFFFFF"
CONFIG_MMIO_AC_SOFT=y
```

`0xf1` 落在配置的 MMIO 空间内。NEMU 读路径先判断是否在 MMIO 空间，进入后先做非对齐
检查，之后才判断是否真实设备：

```c
// paddr.c:351-382
if (is_in_mmio(addr)) {
    ...
    isa_mmio_misalign_data_addr_check(addr, vaddr, len, READ, cross_page_load);
    if (!mmio_is_real_device(addr)) { raise_read_access_fault(...); }   // 若走到这才报 access fault
}
```

非对齐检查函数：

```c
// paddr.c:233-243
if ((paddr & (len-1)) != 0) {
    if (CONFIG_MMIO_AC_SOFT) {
        int ex = cpu.amo || type==WRITE ? EX_SAM : EX_LAM;   // EX_LAM = 4
        longjmp_exception(ex);
    }
}
```

代入：`0xf1 & (8-1)=1 != 0`、`CONFIG_MMIO_AC_SOFT=y`、type=read，于是 NEMU 直接抛
`EX_LAM`（cause 4），**不会**走到后面的真实设备检查。这与日志 REF dump 的
`mcause=4, mepc=0x800012f8, mtval=0xf1` 一致。

需要澄清两点，避免误读为“硬件检测开关”：

- `CONFIG_MMIO_AC_SOFT` 是 NEMU（纯软件参考模型）的**仿真顺序开关**，与“硬件是否先
  检测非对齐”无关。它决定：对进入 MMIO 空间的非对齐访问，是先按 misaligned(4) 上报，
  还是继续往下走。
- `0xf1` 虽落在 NEMU 配置的 MMIO 空间，但该地址**没有注册任何设备**
  （`mmio_is_real_device(0xf1)=false`）。若此宏关闭，非对齐检查变空操作，流程会落到
  “非真实设备”检查并报 access fault(5)。也就是说，NEMU 里这个宏决定的是 4 还是 5，
  且宏关时的 5 来自“不是真实设备”，并非“硬件检测到非对齐”。

另外注意：XiangShan 的 5 与 NEMU 的 5 归因不同——XiangShan 报 5 是因为“非对齐的
MMIO 不能安全拆分→access fault”（`afUnalignMMIO`），而非“地址不是注册设备”。两者异常
号相同，但判据不同。

## 6. 两侧对照

同一指令、同一地址、同一“8 字节未对齐”事实：

| 实现 | 分类依据 | 关键前提 | 结果 |
|---|---|---|---|
| XiangShan | `afUnalignMMIO = !align && isMMIO` | `c=0` → MMIO，`isMMIO=1`, `isNC=0` | cause 5 |
| NEMU | MMIO 路径非对齐检查先行 | `0xf1` 在配置 MMIO 空间、未对齐、`MMIO_AC_SOFT=y` | cause 4 |

RISC-V 特权架构手册规定，load/store/AMO 的地址未对齐异常相对于访问异常和页异常可以具有
更高或更低的优先级，具体优先级由实现定义。因此，本例中 XiangShan 报告 cause 5、NEMU
报告 cause 4，均属于规范允许的实现选择。参见 [RISC-V Privileged Specification：同步异常
优先级](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html#norm:exc_priority)。

因果链：

```text
ld s8,0xf0(t6), t6=1
  -> vaddr = paddr = 0xf1
  -> 8-byte 未对齐 -> align = 0
  -> Bare: pbmt = PMA
  -> PMA [0,0x10000000): r=1, c=0
       resp.ld=0 (允许读), resp.mmio=1 (MMIO)
  -> isNC=0 (pbmt 非 NC), isMMIO=1
  -> afUnalignMMIO = 1, am = 0
  -> exceptionVec bit5 -> mcause = 5

同地址在 NEMU:
  -> 0xf1 在配置 MMIO 空间
  -> MMIO 非对齐检查先行 -> EX_LAM
  -> mcause = 4
```

## 7. 边界与判定

- 本例**不是**“无读权限”分支：`pmp.ld=0`（地址可读），cause5 来自 `afUnalignMMIO`
  （非对齐 + MMIO 属性）。
- `isNC=0` 不等于“该区域可缓存”：`isNC` 只由 PBMT 决定，PMA 的 `c=0` 走的是
  `mmio` 输出，两者独立。
- 该 DUT 的 cause5 是 `afUnalignMMIO` 的直接结果，并非 `mcause` 编码错误。
- 复现成立：DUT 提交 cause5、REF 为 cause4，DiffTest 因 `mcause` 不同中止。issue 记录
  结论为 `type: bug/invalid`。

## 8. 波形验证

用 wavekit（`~/xs/wavekit`，`PYTHONPATH=src .venv/bin/python`）读取 `replay.fst`，
以 `TOP.clock` 为采样时钟（`sample_on_posedge=True`），按 posedge 周期编号核对。该
`replay.fst` 时间范围为 `0..14241`（约 7120 周期），已覆盖目标事务。

### 8.1 事务定位

先排除干扰事务。LoadUnit 有 3 条（`memBlock.inner_LoadUnit_0/1/2`），它们的写回端口
`io_ldout_toRob_bits_debugInfo_paddr` 都可能在运行中短暂为 `0xf1`（例如 robIdx 131/
134/137）。这些不是目标指令。真正触发本次异常的是**robIdx 140**：

- ROB 异常提交：`io_robio_exception_valid=1` @ cycle **7112**，`io_robio_exception_bits_pc
  =0x800012f8`。
- 同一周期 ROB deq 队首 `io_robDeqPtr_value = 140`，故故障指令 robIdx=140。
- trap 后一拍 `trapToM_mcause_bits_ExceptionCode=5` @ cycle 7114。

### 8.2 目标 LoadUnit 及其链路取值

目标 load 落在 **`inner_LoadUnit_0`**。用 S2 段 robIdx 定时间窗：`s2.io_pipeIn_bits_uop_robIdx_value` 于 cycle 7106 变为 `140`（7105 前为 134=0x86，是上一驻留者，排除），故目标在 S2 的观测窗为 **7106–7111**；`io_ldout_toRob_valid=1` 单脉冲在 **7107**。以下为逐信号在 7106–7111 各 cycle 的实测值：

| # | 论断 | 信号 | cycle=7106 | 7107 | 7108 | 7109 | 7110 | 7111 |
|---|---|---|---|---|---|---|---|---|
| 1 | vaddr = paddr = 0xf1 | `io_tlb_req_bits_vaddr`；`io_ldout_toRob_bits_debugInfo_paddr` | 0xf1 | 0xf1 | 0xf1 | 0xf1 | 0xf1 | 0xf1 |
| 2 | 未对齐 | `s1.io_pipeIn_bits_align`（= `s0.io_pipeOut_bits_align`） | 0 | 0 | 0 | 0 | 0 | 0 |
| 3 | Bare: pbmt=PMA | `s2.io_pipeIn_bits_pbmt[1:0]` | 0 | 0 | 0 | 0 | 0 | 0 |
| 4 | resp.ld=0（允许读） | `io_pmp_ld` | 0 | 0 | 0 | 0 | 0 | 0 |
| 5 | resp.mmio=1（MMIO） | `io_pmp_mmio` | 1 | 1 | 1 | 1 | 1 | 1 |
| 6 | 非权限问题 | `s2.pmpUnaccessable` | 0 | 0 | 0 | 0 | 0 | 0 |
| 7 | exceptionVec bit5/bit4 | `io_ldout_toRob_bits_exceptionVec_5` / `_4` | 5=1,4=0 | 5=1,4=0 | 5=1,4=0 | 5=1,4=0 | 5=1,4=0 | 5=1,4=0 |
| 8 | mcause=5 | ROB exception pc=0x800012f8 @ **7112**；`trapToM_mcause_bits_ExceptionCode=5` @ **7114** | — | — | — | — | — | — |

### 8.3 关于内部组合项

`isNC`/`isMMIO`/`afUnalignMMIO`/`am` 是 LoadUnit 内的组合逻辑 val，被综合内联，
`replay.fst` 中无对应同名节点（`afUnalignMMIO`/`afUnaccessable`/`am` 探测为空）。其推导
由**可测输入 + 可测结果 + 源码公式**闭合：

```text
pbmt=0（非 NC）  &&  pmp.mmio=1
  → isNC=0（Pbmt.isNC(pbmt) 为假）
  → isMMIO=1（Pbmt.isPMA(pbmt) && pmp.mmio）
  → afUnalignMMIO = !align && isMMIO = 1
  → am = !align && scalar && isNC = 0
结果：exceptionVec_5=1、exceptionVec_4=0（8.2 直接测得）
```

`PMA cfg r=1,c=0` 属架构配置事实（来源：`runtime.log` 的 pma 表 + SoC 配置源码），波形
侧以其输出 `io_pmp_ld=0`、`io_pmp_mmio=1` 印证。

### 8.4 结论

波形观测与第 2–4 节的源码推导一致：目标 `ld`（robIdx 140）在 LoadUnit 0 中呈现
`vaddr/paddr=0xf1`、`align=0`、`pbmt=0`、`pmp_ld=0`、`pmp_mmio=1`，
最终 `exceptionVec_5=1 / _4=0`，并经 ROB 提交为 `mcause=5`。这使此前仅由源码/日志
推断的 `tlbHit=1`、`pbmt=0` 等环节获得逐周期观测支撑。
