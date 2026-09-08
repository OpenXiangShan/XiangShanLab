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
根据 `summary.txt` 的 issue 上下文记录，维护者将该 issue 标记为
`type: bug/invalid`，并将 XiangShan 的行为归为有意设计。本次分析未能通过
GitHub API 重新读取 issue 页面，因此该标签应视为 replay 元数据中的记录，而
不是本次网络查询直接验证的结果。

### 分析对象与版本

本报告针对的是现有 replay 产物，不是重新构建或重新仿真的结果：

| 对象 | 版本或摘要 |
|---|---|
| XiangShan | `b90dbba40d16d54f2814870ff8b4d8809a41e680` |
| xs-env | `33d5f6f611d15a65c6194290fa62caf0c0c27f41` |
| 当前 `xs-env/NEMU` 工作树 | `53bcb5686f8fd05248ae98546b7dc04bdca1bbb0` |
| 实际加载的 REF | `XiangShan/ready-to-run/riscv64-nemu-interpreter-so` |
| bundled REF 声明的 NEMU 来源 | `36342a16bb49b90ba4397c9e8193ac7733ae1088` |
| testcase | `seed.elf`，SHA-256 `4a71cf417cbfcc490b989f12031ad441d0068ad17ac01f46a7726247ecccf16e` |
| waveform | `replay.fst`，SHA-256 `d5c97e32c67b6a2460f053b363e940fcf7c38644d461879cb498e1364832d53d` |
| runtime log | `runtime.log`，SHA-256 `534d5eee1fcbd756d12f25836f8fe0962fd504fcc1d7e4e4764d883d561318c7` |
| disassembly | `testcase.dump`，SHA-256 `2216af0c7719630610c8c34cbf2089b6e65eab0b56776fa0f8fadbab0d8a3c7a` |

运行时实际通过 difftest 动态加载的是 `ready-to-run` 下的 bundled SO，不能将
当前 `xs-env/NEMU` 工作树的 HEAD 直接当作该 SO 的构建 revision。当前 NEMU
工作树可用于辅助源码对照；涉及 REF 实际控制流的行号，应优先标注 bundled
SO 对应的历史 revision，或以 SO 反汇编为准。

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

`af` 是多个“或”项。确认 `afUnalignMMIO=1` 足以解释 AF 的产生；新版波形验证
进一步排除了有限窗口内已导出的竞争响应来源，但未导出 `afTagError` 本身。

| af 来源 | 本例 | 依据 |
|---|---|---|
| `afUnaccessable` | 0 | `pmp.ld=0` → `pmpUnaccessable=0`；指令本身无 access fault 位 |
| `afVectorUncache` | 0 | 本条是标量 `ld`，不是向量 |
| `afUnalignMMIO` | 1 | `align=0 && isMMIO=1` |
| `afTagError` | 未直接采样 | DCache tag-error 输入未导出，不能声称已对该内部节点做波形断言 |
| `afForwardDenied / afBypassDenied` | 0（有限窗口） | 导出的 MSHR/TLD denied、bypass response/nderr 在 `[7104,7115)` 未形成竞争 AF |

所以：

```text
af = 0 || 0 || 1 || ... = 1
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
且最终被 ROB 选中处理异常。日志确认最终陷入来自这条 `ld`，而不是该 load 正常完成
提交。当前优先级表中 load address misaligned 高于 load access fault
（`xiangshan/package.scala:1102-1122`）；若 bit4、bit5 同时为 1，常规仲裁会选择 4。
本例写回实测 bit4=0、bit5=1，因此不是 CSR 用 5 覆盖了 4。

## 5. NEMU REF 为何是 4

REF 是 `ready-to-run/riscv64-nemu-interpreter-so`。ready-to-run 提交
`377a8548f5cde0fc0c37468d055ef46e52870784` 声明其 NEMU 来源为 `36342a16`、配置为
`riscv64-xs-ref_defconfig`。以下 NEMU 行号指该历史版本，而非当前工作树 `53bcb568`。
该来源属于提交记录，不是重新构建后的逐字节证明；当前 SO 的静态反汇编也支持
“MMIO 非对齐检查早于真实设备检查”的关键控制流。相关配置为：

```text
CONFIG_ENABLE_CONFIG_MMIO_SPACE=y
CONFIG_MMIO_SPACE_RANGE="0x0, 0x7FFFFFFF"
CONFIG_MMIO_AC_SOFT=y
```

`0xf1` 落在配置的 MMIO 空间内。对于本例 `pbmt=0` 的普通 load，NEMU 先经
`check_paddr` 检查 PMP/PMA 权限；通过后进入 MMIO 分支，在分支内先做非对齐检查，
之后才判断是否真实设备。因此不是“任何物理地址访问都无条件先报非对齐”：

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
- 本次异常发生在 `mmio_is_real_device` 执行之前，因此 cause 4 本身不能证明
  `0xf1` 有无注册设备。若关闭该宏，流程将继续检查设备映射；只有该地址没有真实
  设备时，后续才因设备不存在而报 access fault(5)。本次未执行修改配置后的对照仿真，
  不能把“关闭宏即可修复”当作已验证结论，也不能据此覆盖所有 MMIO/NC 场景。

另外注意：XiangShan 的 5 与 NEMU 的 5 归因不同——XiangShan 报 5 是因为“非对齐的
MMIO 不能安全拆分→access fault”（`afUnalignMMIO`），而非“地址不是注册设备”。两者异常
号相同，但判据不同。

## 6. 两侧对照

同一指令、同一地址、同一“8 字节未对齐”事实：

| 实现 | 分类依据 | 关键前提 | 结果 |
|---|---|---|---|
| XiangShan | `afUnalignMMIO = !align && isMMIO` | `c=0` → MMIO，`isMMIO=1`, `isNC=0` | cause 5 |
| NEMU | MMIO 路径非对齐检查先行 | `0xf1` 在配置 MMIO 空间、未对齐、`MMIO_AC_SOFT=y` | cause 4 |

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
  结论在 `summary.txt` 中记录为 `type: bug/invalid`，本次未在线复核。

## 8. 波形验证

用已安装的 Wavekit 0.7.2（解释器
`/nfs/home/sunyuhang/.local/opt/wavekit/.venv/bin/python`，模块
`/nfs/home/sunyuhang/.local/opt/wavekit/src/wavekit/__init__.py`，checkout
`bdfc13664d8e118ecc9733f0fb2158a085c41698`）读取 `replay.fst`，
以 `TOP.clock` 为采样时钟（`sample_on_posedge=True`），按 posedge 周期编号核对。该
  `replay.fst` 时间范围为 `0..14241`（约 7120 周期），已覆盖目标事务。

### 8.1 事务定位

先排除干扰事务。LoadUnit 有 3 条（`memBlock.inner_LoadUnit_0/1/2`），它们的写回端口
`io_ldout_toRob_bits_debugInfo_paddr` 都可能在运行中短暂为 `0xf1`（例如 robIdx 131/
134/137）。这些不是目标指令。真正触发本次异常的是**robIdx 140**：

- ROB 异常提交：`io_robio_exception_valid=1` @ cycle **7112**，`io_robio_exception_bits_pc
  =0x800012f8`。
- 同一周期 ROB deq 队首 `io_robDeqPtr_value = 140`，故故障指令 robIdx=140。
- `trapToM_mcause_bits_ExceptionCode=5` @ cycle 7114，与上述 ROB 事件相隔两个采样周期。
  这是 trap 写入数据端口的观测，不能仅凭该端口值推断 CSR 寄存器写入的精确时刻。

### 8.2 目标 LoadUnit 及其链路取值

目标 load 落在 **`inner_LoadUnit_0`**。cycle **7106** 同时满足
`s2.io_pipeIn_valid=1`、`s2.io_pipeIn_bits_uop_robIdx_value=140`；7105 的
robIdx 保留值为 134，且 S2 输入无效。cycle **7107** 的 `io_ldout_toRob_valid=1`，
而 S2 输入已经无效。7108–7111 的输入、写回均无效，不能因为 robIdx 和数据保持不变，
就把这些周期解释为目标事务仍在有效处理。

下表保留原始多周期取值以展示保留值现象。S2 属性应在 **7106** 解读，写回数据应在
**7107** 解读，其余无效周期的数值不独立构成事务证据。层级前缀为：

```text
LDU = TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_LoadUnit_0
ROB = TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_ctrlBlock
CSR = TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_intRegion.intExuBlock.exuALU0_AluCsrFence.csr.csrMod
```

表中 LoadUnit 信号均相对 `LDU`；ROB PC/valid 相对 `ROB`，队首指针相对 `ROB.rob`；
cause 数据相对 `CSR.mcause`。内部组合项的源码推导与直接观测在 §8.3 分开说明。

| # | 论断 | 信号 | cycle=7106 | 7107 | 7108 | 7109 | 7110 | 7111 |
|---|---|---|---|---|---|---|---|---|
| 1 | vaddr = paddr = 0xf1 | `io_tlb_req_bits_vaddr`；`io_ldout_toRob_bits_debugInfo_paddr` | 0xf1 | 0xf1 | 0xf1 | 0xf1 | 0xf1 | 0xf1 |
| 2 | 未对齐 | `s2.io_pipeIn_bits_align` | 0 | 0 | 0 | 0 | 0 | 0 |
| 3 | Bare: pbmt=PMA | `s2.io_pipeIn_bits_pbmt[1:0]` | 0 | 0 | 0 | 0 | 0 | 0 |
| 4 | resp.ld=0（允许读） | `io_pmp_ld` | 0 | 0 | 0 | 0 | 0 | 0 |
| 5 | resp.mmio=1（MMIO） | `io_pmp_mmio` | 1 | 1 | 1 | 1 | 1 | 1 |
| 6 | 非权限问题 | `s2.pmpUnaccessable` | 0 | 0 | 0 | 0 | 0 | 0 |
| 7 | exceptionVec bit5/bit4 | `io_ldout_toRob_bits_exceptionVec_5` / `_4` | 5=1,4=0 | 5=1,4=0 | 5=1,4=0 | 5=1,4=0 | 5=1,4=0 | 5=1,4=0 |
| 8 | cause 写入数据为 5 | ROB exception pc=0x800012f8 @ **7112**；`trapToM_mcause_bits_ExceptionCode=5` @ **7114** | — | — | — | — | — | — |
| 9 | S2 输入有效 | `s2.io_pipeIn_valid` | 1 | 0 | 0 | 0 | 0 | 0 |
| 10 | 写回有效 | `io_ldout_toRob_valid` | 0 | 1 | 0 | 0 | 0 | 0 |

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

`PMA cfg r=1,c=0` 的 DUT 依据是 SoC 默认配置源码；`runtime.log` 的 PMA 表打印在
REF 状态中，是参考模型一侧的配置证据。DUT 波形以合成的 PMP/PMA 输出
`io_pmp_ld=0`、`io_pmp_mmio=1` 印证该分类，不能将 REF 的 CSR dump 当作 DUT 内部观测。

### 8.4 结论

波形观测与第 2–4 节的源码推导一致：目标 `ld`（robIdx 140）在 LoadUnit 0 中呈现
`vaddr/paddr=0xf1`、`align=0`、`pbmt=0`、`pmp_ld=0`、`pmp_mmio=1`，
最终 `exceptionVec_5=1 / _4=0`，并经 ROB 提交为 `mcause=5`。这使此前仅由源码/日志
推断的 `tlbHit=1`、`pbmt=0` 等环节获得逐周期观测支撑。

### 8.5 本次修改的核验范围

本次验证使用项目级新版 Wavekit skill 和 Wavekit 0.7.2，以 `TOP.clock` 上升沿采样，
在 case-local `waveform-verification/6265-misaligned-load/verify.py` 中对
`[7104,7118)` 执行 **35 项检查：31 PASS、0 FAIL、4 INCONCLUSIVE**。
早期 `complete2/complete3` 将四项未闭合结论无条件标为 PASS，这是验证脚本错误，
这些结果作废；以下正式路径指向纠正后的 `audited/` 结果。不能通过缩小或改名命题
把未验证的握手、独占 AF 来源、完整事务链和 MMIO 总线缺席变成 PASS。
其中包含单周期数值、clock/time 对齐、复位、X/Z mask、流水级传播、竞争异常响应和
有限区间总线请求检查。正式证据记录在
`../waveform-verification/6265-misaligned-load/audited/evidence.json`，汇总报告在
`../waveform-verification/6265-misaligned-load/audited/report.md`。
其中 DCache 请求检查不是 MMIO 总线检查，bypass 响应无效也不证明设备请求未发出；
31 项 PASS 仅代表脚本列出的有限采样命题，不能解释为上述四项均已闭合。
每个单周期断言使用 `[cycle,cycle+1)` 窗口，保留 wavekit 的绝对 cycle 编号。

| cycle | 断言内容 | 数量 |
|---|---|---:|
| 7106 | S2 valid=1、robIdx=140、align=0、pbmt=0、tlbAccessResult=2、pmpUnaccessable=0、pmp_ld=0、pmp_mmio=1、TLB 请求 VA=0xf1 | 9 |
| 7107 | 写回 valid=1、debug PA=0xf1、bit5=1、bit4=0、S2 valid=0 | 5 |
| 7108 | 写回 valid=0 | 1 |
| 7112 | ROB exception valid=1、PC=0x800012f8、队首 value=140 | 3 |
| 7114 | cause 写入数据端口=5 | 1 |

重新执行的正式输出位于
`waveform-verification/6265-misaligned-load/revalidated-20260908/`；其
`evidence.json` 记录了本次解释器、模块位置、Wavekit checkout、层级解析、信号宽度、
值/XZ mask、窗口和输入 SHA-256。仍需限定的是：验证的是本 FST 中 `[7104,7118)` 的有限窗口，不是所有运行周期的
全局性质；波形 cycle 也不能直接与日志的 `cycleCnt=7066` 混用，两者计数基准未对齐。
S2 内部 ready 端口在生成 RTL 中被消除；源代码表明该实现的 S2 输入 ready 由下游级联为
真，因此本次目标输入的 Decoupled 接受条件可退化为 `valid`，但 FST 本身没有独立的
ready 采样。已验证的是 `s2.io_pipeIn_valid=1` 到下一阶段 `io_pipeOut_valid=1` 的
流水级传递。本次 FST 还直接导出了并采样了 `s2.isMMIO=1`、`s2.isNC=0`、`s2.af=1`、
`s2.am=0`。S2 的 tag-error 输入在生成 RTL 中因 LoadPipe 的 `tag_error := false.B`
被优化掉，故该项由源码 wiring 排除，而不是由 FST 直接采样。

## 9. 最小复现与原始 seed

`../minimal/` 保存了一份更小的独立复现程序和运行日志，使用相同 DUT 编译版本
及 bundled REF。它设置 MPP、mtvec 和 PMP 放行配置后执行 `ld x1,1(x0)`，
trap handler 将 mepc 加 4 后返回。

| 项目 | 原始 seed | minimal |
|---|---|---|
| 指令 PC | `0x800012f8` | `0x80000034` |
| 编码 | `0f0fbc03` | `00103083` |
| 指令 | `ld s8,240(t6)` | `ld x1,1(x0)` |
| 动态地址 | `1+240=0xf1` | `0+1=0x1` |
| DUT cause | 5，`runtime.log:65` | 5，`../minimal/run.log:51` |
| REF cause / mtval | 4 / 0xf1，`runtime.log:91` | 4 / 0x1，`../minimal/run.log:77` |
| mismatch 位置 | `runtime.log:189` | `../minimal/run.log:175` |

两者不是逐指令等价程序，但都在同一低地址 PMA 区域对 8 字节非对齐 load
复现了 5/4 分歧。最小用例说明，不需要原始随机程序的全部复杂指令上下文即可
触发此分类差异。本报告的 FST 来自原始 seed，不能把其 7106 等周期套用到 minimal。

原始 seed 的 t6 不能沿用初始化地址：`testcase.dump:1243` 的
`0x80001254: sc.d t6,s10,(t6)` 将 SC 返回状态写回 t6；后续 REF dump 实测 t6=1。
因此不能仅按最初 `t6=0x80101000` 推算目标 load 的地址。

## 10. 历史修复与判定

本地 XiangShan 历史中，提交
`cfd6b14162bc195b1362b9dfc662c6325d9756da`，
`fix(LoadUnit): raise af for unalign access on MMIO region (#5700)`，
明确引入了本例相关的异常分类：

```diff
+ val afUnalignMMIO = !in.align.get && isMMIO
- val af = afUnaccessable || afVectorUncache || afTagError || afForwardDenied || afBypassDenied
+ val af = afUnaccessable || afVectorUncache || afUnalignMMIO || afTagError || afForwardDenied || afBypassDenied
- val am = !in.align.get && accessType.isScalar() && isUncache && !pmpUnaccessable
+ val am = !in.align.get && accessType.isScalar() && isNC && !pmpUnaccessable
```

提交说明引用 RISC-V 对非幂等区域的规则：未对齐访问应报 access fault，提示软件
不要用多个较小访问模拟，否则可能引入副作用；NC 区域仍保留 misaligned 分类。
这是已存在于本次 DUT 中的历史设计依据，不是本次新修复，也不是 #6265 的
修复后回归结果。不能把所有“不可缓存”都等同于非幂等；这里是 XiangShan
在 PBMT=PMA、PMA.C=0 条件下采用的 MMIO 分类。

本次 DUT 实例化 `NewLoadUnit`（`MemBlock.scala:494`）。访问字节范围为
`0xf1..0xf8`，不跨 16-byte bank，也不跨页；因此无需用旧独立
`LoadMisalignBuffer` 的拆分或跨页异常地址覆盖机制解释本例。

综合判定：**复现了 DUT 与 bundled REF 对 MMIO 非对齐 load 的异常策略差异；
DUT 行为与已有 #5700 设计一致，没有证据要求把当前 DUT 的 cause 5 改回 4。**
若要消除 difftest 分歧，应进一步核对 REF 对 MMIO、PBMT=NC/IO、权限失败及
真实设备映射的规则，而不是直接关闭全部对齐检查。尚未确定并验证针对 #6265
的 REF 修复提交，本次也未执行修复后仿真。

## 11. 产物完整性与边界

- 已有原始日志、反汇编和非空 FST 支持本次 mismatch；最小用例另有独立日志证据。
- `replay-work/build.log`、`replay-work/return_code` 当前缺失，不能报告精确退出码，
  也不能声称已复核完整构建过程。复现判据是日志中的目标异常与 mcause mismatch。
 - `replay.sh` 会下载、构建并覆盖产物，本次未执行；它固定 DUT 和 xs-env，实际 REF
   来自固定 DUT 的 ready-to-run 子模块，而非当前 NEMU 工作树构建。
 - `replay.sh` 的 `-e 0` 与当前 host trace-gating 源码需要谨慎区分；保存的 FST 确实
   覆盖到目标周期，但仅凭脚本文本不能证明晚期全程波形会被导出。因此本报告把 FST
   内容作为实际证据，把脚本作为复现意图，不把脚本当作波形完整性证明。
- `../minimal/run.sh:60` 仅以 `mcause different` 字符串判成功，并未自动核对故障 PC、
  地址和 4/5 方向。本文结论来自保存日志的具体字段，而不是单独信任脚本退出码。
- 当前产物哈希标识本次分析的文件内容；运行日志未记录当时 REF SO 的哈希，
  bundled 来源声明也不等同于可复现构建证明。
- GitHub 网页/API 访问超时，`gh` 未认证。维护者标签来自本地元数据；#5700 的
  设计依据来自本地完整提交补丁。这两类历史证据应分开引用。
