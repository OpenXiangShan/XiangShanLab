# XiangShan Issue #6265：非对齐 MMIO Load 为何 DUT 报 5、REF 报 4

## 0. 结论

测试程序在 `0x800012f8` 执行：

```asm
ld s8, 240(t6)
```

运行时 `t6=1`，有效地址为 `0xf1`。`ld` 是 8 字节访问，其自然对齐要求是地址按 8 字节对齐，而
`0xf1` 不满足；非对齐访问是否被支持以及如何报异常，还取决于实现和内存属性。双方对“哪条指令、哪个地址、是否未对齐”没有分歧，分歧只在异常分类：

- XiangShan（DUT）把该地址经 PMA 判为“读权限允许、缓存属性 C=0”，在本实现中归类为 MMIO，走
  `非对齐 + MMIO` 分支，报告 **load access fault（加载访问异常），mcause=5**。
- NEMU（REF）把 `0xf1` 归入它配置的 MMIO 空间，在 MMIO 路径里先做非对齐检查，直接
  报告 **load address misaligned（加载地址非对齐），mcause=4**。

XiangShan 的 cause 5 不是异常编码错误，而是它对该低地址区域非对齐访问的分类结果；
`summary.txt` 记录维护者将 XiangShan 的行为归为有意设计，但当前文件未保存具体标签。
此前分析未能通过 GitHub API 重新读取 issue 页面，本次修订也未在线复核，
因此不将 `type: bug/invalid` 作为已核实事实。

### 分析对象与版本

本次修订只核对现有产物与源码，未执行构建、仿真或重新提取波形。
以下路径均相对于本报告所在的 `6265/` 目录；正文中的 `runtime.log`、
`testcase.dump`、`seed.elf` 和 `replay.fst` 均简称 `replay-work/` 下的同名文件。
当前日志记录模拟器编译时间为 **2026-09-08 16:34:45**；下表为本次修订时实测的文件哈希。
§8 的逐周期结果来自较早的波形快照，不能直接视为对当前 FST 的复验。

| 对象 | 版本或摘要 |
|---|---|
| XiangShan | `b90dbba40d16d54f2814870ff8b4d8809a41e680` |
| xs-env | `33d5f6f611d15a65c6194290fa62caf0c0c27f41` |
| 当前 `xs-env/NEMU` 工作树 | `53bcb5686f8fd05248ae98546b7dc04bdca1bbb0` |
| 实际加载的 REF | `XiangShan/ready-to-run/riscv64-nemu-interpreter-so` |
| bundled REF 声明的 NEMU 来源 | `36342a16bb49b90ba4397c9e8193ac7733ae1088` |
| testcase | `seed.elf`，SHA-256 `4a71cf417cbfcc490b989f12031ad441d0068ad17ac01f46a7726247ecccf16e` |
| waveform | `replay.fst`，SHA-256 `5188e36628d539feefa9c6562811ebb1f31bba878026553990c04c299e323cde` |
| runtime log | `runtime.log`，SHA-256 `dc35bd03eca59558ed652259061fdf7f9f505ee37df49b57201b8603ffe1fb4a` |
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

`size="b11"`(D) 检查 `bankOffset.take(3)`；`0xf1` 低 3 位为 `001`，故对齐检查结果
`align=false`，下文简写为 `align=0`（即非对齐）。
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

对上述有效、non-miss 的普通 load 响应，源码可推导 `tlbHit=1`、`pbmt=0`。
这里的 `pbmt` 是内存类型编码，不代表本次实际读取过页表；TLB 对不翻译请求强制输出 0：

```scala
// TLB.scala:421-432
resp(idx).bits.pbmt(d) := Mux(portTranslateEnable(idx), res, 0.U)
```

PBMT 编码（`MMUBundle.scala:453-466`）：`00`=PMA、`01`=NC、`10`=IO。因此本例
`pbmt=0` 表示“类型交给物理地址的 PMA 判定”，它不是 NC(`01`)。

### 2.3 PMA 判 0xf1：resp.ld=0、resp.mmio=1

根据 DUT 的默认配置 `SoC.scala:57-71`，`0xf1` 命中低地址 TOR 区间
`[0, 0x10000000)`，对应 PMA 项 `r=1,c=0`。这属于源码配置依据；历史波形只直接
观察到合成的 PMP/PMA 输出，并未采样 DUT PMA 表项。`runtime.log` 中的 PMA 表属于
REF dump，不能当作 DUT 配置的直接观测。PMA 权限/属性计算：

```scala
// PMA.scala:210-218
resp.ld := TlbCmd.isRead(cmd) && !cfg.r
resp.mmio := !cfg.c
```

命令是读、`cfg.r=1`、`cfg.c=0`，代入：

```text
resp.ld   = isRead && !r = 1 && !1 = 0   // 无读权限 fault
resp.mmio = !c = !0 = 1                  // 不可缓存 → MMIO 属性
```

注意 `resp.ld` 不是“这是不是 load”，而是“这次 load 有没有读权限 fault”；`ld=0` 表示
**无读权限 fault**，而不是“load 被禁止”。LoadUnit 的 `io_pmp_ld` 同样是该接口中的
load fault 指示，不是读使能；历史波形还直接观察到 `pmpUnaccessable=0`。
因此不能把 `io_pmp_ld=0` 误读为 PMP 禁止本次读取。

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

`af` 是多个“或”项。确认 `afUnalignMMIO=1` 足以解释 AF 的产生。
历史波形记录还支持对有限窗口内指定竞争响应的排查，但不构成所有 AF 来源的独占性证明。
下表将源码推导与历史波形观测分开，波形快照范围见 §8。

| af 来源 | 本例 | 依据 |
|---|---|---|
| `afUnaccessable` | 0（结合源码路径推导） | 历史采样 `pmpUnaccessable=0`；本例 Bare 普通 load 的分析以输入无既有 access fault 位为前提，该输入位未列为独立采样断言 |
| `afVectorUncache` | 0 | 本条是标量 `ld`，不是向量 |
| `afUnalignMMIO` | 1（源码推导） | `!align && isMMIO`，其中 `isMMIO` 使用 §2 的完整分类条件 |
| `afTagError` | 0（源码 wiring） | `cache/dcache/loadpipe/LoadPipe.scala:466` 将 `resp.bits.tag_error := false.B`；结合 LoadUnit 的 `afTagError` 公式可排除该项，但不是 FST 直接采样 |
| `afForwardDenied / afBypassDenied` | 指定输入未形成竞争条件（历史有限窗口） | 在 `[7104,7115)` 未观察到 MSHR/TLD 的 `valid && denied`；bypass response valid/nderr 为 0。不等于所有响应均不存在 |

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
历史波形的写回采样为 `exceptionVec[4]=0`、`exceptionVec[5]=1`（快照限定见 §8），
与源码判据一致，因此不是 CSR 用 5 覆盖了 4。

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
  `0xf1` 有无注册设备。按引用版本的源码，若关闭该宏，流程预计继续检查设备映射；
  若随后判定该地址没有真实设备，则会因设备不存在而报告 load access fault（mcause=5）。
  这是源码级条件推断，本次未执行修改配置后的对照仿真，
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
       resp.ld=0 (无读权限 fault), resp.mmio=1 (MMIO)
  -> isNC=0 (pbmt 非 NC), isMMIO=1
  -> afUnalignMMIO = 1, am = 0
  -> exceptionVec bit5 -> mcause = 5

同地址在 NEMU:
  -> 0xf1 在配置 MMIO 空间
  -> MMIO 非对齐检查先行 -> EX_LAM
  -> mcause = 4
```

## 7. 证据阅读约定

- **直接观测**：日志字段，或注明快照和有效周期的波形值；无效周期的保持值不作为事务证据。
- **源码推导**：由所列版本的公式、枚举或 wiring 得出，不等同于采样了同名内部节点。
- **条件性推断**：例如关闭 REF 配置后的预计分支，尚无对照仿真。
- **未验证**：完整端到端事务因果、MMIO 总线访问缺席及修复后的回归结果等，不作已证明结论。

`isNC=0` 不意味着区域可缓存；本例 PMA 的 `C=0` 经该实现分类为 MMIO，
不是 PBMT=NC。异常处理也不等于故障 load 正常退休。

## 8. 波形验证

本节保留**历史波形快照**的验证摘要，使用 Wavekit 0.7.2，checkout
`bdfc13664d8e118ecc9733f0fb2158a085c41698`，以 `TOP.clock` 上升沿采样
（`sample_on_posedge=True`），窗口为 `[7104,7118)`，周期采用工具的绝对编号。

历史输入标识：

| 文件 | 验证时 SHA-256 |
|---|---|
| FST | `d5c97e32c67b6a2460f053b363e940fcf7c38644d461879cb498e1364832d53d` |
| runtime log | `534d5eee1fcbd756d12f25836f8fe0962fd504fcc1d7e4e4764d883d561318c7` |

历史 FST 的原生时间范围为 `0..14241`（约 7120 周期；物理时间单位未确定）。
上述哈希与 §0 当前文件不一致，说明文件内容已经变化；即使大小相同，也不能认为是
同一快照。本次未对当前 FST 重新采样，因此以下周期值仅属于历史记录。
本次重新读取的当前日志仍明确包含同一故障 PC、指令、地址及 DUT=5/REF=4 分歧。

验证脚本和机器可读记录不随本文上传；本节自包含列出关键取值、信号路径和限制，
但不将这份摘要等同于可独立重跑的验证包。复核当前 FST 时须重新检查有效性、
事务标识、复位和周期位置，不能仅沿用下列周期编号。

### 8.1 事务定位

先排除干扰事务。LoadUnit 有 3 条（`memBlock.inner_LoadUnit_0/1/2`），它们的写回端口
`io_ldout_toRob_bits_debugInfo_paddr` 都可能在运行中短暂为 `0xf1`（例如 robIdx 131/
134/137）。这些不是目标指令。真正触发本次异常的是**robIdx 140**：

- ROB 异常事件：`io_robio_exception_valid=1` @ cycle **7112**，
  `io_robio_exception_bits_pc=0x800012f8`。
- 同一周期 ROB deq 队首 `io_robDeqPtr_value = 140`，故故障指令 robIdx=140。
- `trapToM_mcause_bits_ExceptionCode=5` @ cycle 7114，与上述 ROB 事件相隔两个采样周期。
  这是 trap 写入数据端口的观测，不能仅凭该端口值推断 CSR 寄存器写入的精确时刻。

### 8.2 目标 LoadUnit 及其链路取值

目标 load 落在 **`inner_LoadUnit_0`**。cycle **7106** 同时满足
`s2.io_pipeIn_valid=1`、`s2.io_pipeIn_bits_uop_robIdx_value=140`；7105 的
robIdx 保留值为 134，且 S2 输入无效。cycle **7107** 的 `io_ldout_toRob_valid=1`，
而 S2 输入已经无效。7108–7111 的输入、写回均无效，不能因为 robIdx 和数据保持不变，
就把这些周期解释为目标事务仍在有效处理。

下表只列有效阶段和 CSR 状态观测。S2 属性在 **7106** 解读，写回数据在
**7107** 解读；无效周期的保持值不构成事务证据。层级前缀为：

```text
LDU = TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_LoadUnit_0
ROB = TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_ctrlBlock
CSR = TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_intRegion.intExuBlock.exuALU0_AluCsrFence.csr.csrMod
```

表中 LoadUnit 信号均相对 `LDU`；ROB PC/valid 相对 `ROB`，队首指针相对 `ROB.rob`；
cause 数据相对 `CSR.mcause`。内部组合项的源码推导与直接观测在 §8.3 分开说明。

| 阶段 | cycle | 有效性 / 标识 | 直接观测 |
|---|---:|---|---|
| S2 输入 | 7106 | `s2.io_pipeIn_valid=1`；robIdx `(flag,value)=(1,140)` | `s2.io_pipeIn_bits_align=0`、`pbmt=0`、`tlbAccessResult=2`；`s2.pmpUnaccessable=0`；`io_pmp_ld=0`、`io_pmp_mmio=1` |
| 地址端口旁证 | 7106 | 与 S2 事件同周期，不单独证明 TLB 请求握手 | `io_tlb_req_bits_vaddr=0xf1` |
| LoadUnit 写回 | 7107 | `io_ldout_toRob_valid=1`；robIdx `(flag,value)=(1,140)` | `io_ldout_toRob_bits_debugInfo_paddr=0xf1`；`io_ldout_toRob_bits_exceptionVec_5=1`、`_4=0`；S2 输入 valid=0 |
| ROB 异常 | 7112 | `io_robio_exception_valid=1`；`rob.io_robDeqPtr_value=140` | `io_robio_exception_bits_pc=0x800012f8` |
| Trap 写入端口 | 7114 | `mcause.trapToM_mcause_valid=1`、`mtval.trapToM_mtval_valid=1` | `mcause.trapToM_mcause_bits_ExceptionCode=5`；`mtval.trapToM_mtval_bits_ALL=0xf1` |
| CSR 存储状态 | 7115 | regOut 状态采样，不是额外的写握手 | `mcause.regOut_ExceptionCode=5`；`mtval.regOut_ALL=0xf1` |

S2 表项中的 `pbmt`、`tlbAccessResult` 均为 `s2.io_pipeIn_bits_` 字段。
S2 robIdx 为 `s2.io_pipeIn_bits_uop_robIdx_{flag,value}`，写回 robIdx 为
`io_ldout_toRob_bits_robIdx_{flag,value}`。ROB 队首在本摘要中只核对 value，
不能仅凭 value 相等证明跨回绕的完整事务身份。

### 8.3 关于内部组合项

历史验证记录没有对 `isNC`、`isMMIO`、`afUnalignMMIO`、`af`、`am` 建立直接采样断言。
这既不能证明 FST 中一定没有同名节点，也不能声称这些内部项均已直接测得。

`mem/pipeline/package.scala:141-148` 将 `TlbAccessResult` 定义为 one-hot 枚举，
其中 hit 对应编码 2；`NewLoadUnit.scala:917` 使用 `isHit(tlbAccessResult)`。
因此历史采样的 `tlbAccessResult=2` 支持 S2 的 `tlbHit=1` **源码推导**。
S1 的 `tlbResp.valid/miss/noQuery` 和 `portTranslateEnable` 未作为独立项采样。

完整公式还要求 `tlbAccessable=1`。以下分类以 §2 所述无翻译异常的请求为前提，
结合历史采样属性进行源码推导，不把被省略的输入当作已直接测量：

```text
tlbHit=1 && tlbAccessable=1 && pbmt=0（PMA） && pmp.mmio=1
  → isNC=0（Pbmt.isNC(pbmt) 为假）
  → isMMIO=1（完整分类公式成立）
  → afUnalignMMIO = !align && isMMIO = 1
  → am = !align && scalar && isNC = 0
结果：exceptionVec_5=1、exceptionVec_4=0（8.2 直接测得）
```

`PMA cfg r=1,c=0` 的 DUT 依据是 SoC 默认配置源码；`runtime.log` 的 PMA 表打印在
REF 状态中，是参考模型一侧的配置证据。DUT 波形以合成的 PMP/PMA 输出
`io_pmp_ld=0`、`io_pmp_mmio=1` 印证该分类，不能将 REF 的 CSR dump 当作 DUT 内部观测。

### 8.4 结论

历史采样与第 2–4 节的源码链一致：观察到 S2 属性、下一周期写回的
`exceptionVec[5]=1 / [4]=0`，以及之后 ROB 异常和 CSR 的 `mcause=5`、`mtval=0xf1`。
这支持所述解释，但不是所有中间握手及完整端到端因果的独立证明；尤其不能把
历史采样摘要直接视为当前不同哈希 FST 的验证结果。

### 8.5 历史验证范围与未闭合项

历史验证汇总为 **35 项检查：31 PASS、0 FAIL、4 INCONCLUSIVE**。
31 项 PASS 包括 25 项单周期值检查，以及采样完整性、指针对应、读取期间文件稳定性、
流水级有效性传播、指定竞争响应排查和有限窗口内存路径观测各一项。
单周期检查采用 `[cycle,cycle+1)` 窗口；采样完整性检查包含 clock/time 对齐、
采样点完整、复位无效以及所读 X/Z mask 为零。两态仿真丢失的 X 状态不能靠 mask 恢复。

| 周期 | 单点检查范围 | 数量 |
|---|---|---:|
| 7106 | S2 valid、robIdx value、align、pbmt、tlbAccessResult、pmpUnaccessable、pmp_ld、pmp_mmio、TLB 请求 VA | 9 |
| 7107 | 写回 valid、robIdx value、PA、exceptionVec[5]/[4]、S2 valid | 6 |
| 7108 | 写回 valid=0 | 1 |
| 7112 | ROB exception valid、PC、队首 value | 3 |
| 7114 | mcause/mtval 写入 valid 和数据 | 4 |
| 7115 | CSR 存储的 cause 和 mtval | 2 |

四项未闭合命题为：

| 命题 | 状态及原因 |
|---|---|
| S2 接受握手（`s2-accepted-handshake`） | INCONCLUSIVE：未独立采样 ready；相邻阶段 valid 的传播不是直接的 `valid && ready` 握手证明 |
| AF 唯一来源（`unique-af-source`） | INCONCLUSIVE：只检查指定竞争输入；tag-error 的排除来自 §3 的源码 wiring，不是内部 AF 项的完整波形证明 |
| 端到端事务（`end-to-end-transaction`） | INCONCLUSIVE：S2/写回指针和后续 ROB/CSR 事件一致，但未逐级闭合所有事务身份、握手和 flush 条件 |
| 无 MMIO 总线请求（`no-mmio-bus-request`） | INCONCLUSIVE：所检查的端口不是完整 MMIO 总线，有限窗口的响应缺席不能证明设备请求或副作用不存在 |

有限窗口内存路径观测的具体内容是：在 `[7104,7115)`，DCache 请求 valid 仅在
7104 为 1，对应 VA=`0xf1`，bypass response valid 为 0。它既不证明 DCache 请求已握手，
也不证明 MMIO 总线未发请求。竞争响应排查只排除指定信号中的 `valid && denied`
组合及本窗口的 bypass error 条件，不能解释为所有 MSHR/TLD/bypass 响应都不存在。

早期记录曾错误地将上述四个未闭合命题标为 PASS，该结论作废。
本文保留纠正后的历史统计，但未重新运行验证；31 项 PASS 不能扩大解释为四项均已闭合。
波形周期也不能与日志 `cycleCnt=7066` 直接混用，因为两者计数基准尚未对齐。

## 9. 最小复现与原始 seed

`minimal/` 保存了一份更小的独立复现程序和运行日志，使用相同 DUT 源码版本
及 bundled REF。它设置 MPP、mtvec 和 PMP 放行配置后执行 `ld x1,1(x0)`，
trap handler 将 mepc 加 4 后返回。

| 项目 | 原始 seed | minimal |
|---|---|---|
| 指令 PC | `0x800012f8` | `0x80000034` |
| 编码 | `0f0fbc03` | `00103083` |
| 指令 | `ld s8,240(t6)` | `ld x1,1(x0)` |
| 动态地址 | `1+240=0xf1` | `0+1=0x1` |
| DUT cause | 5，`runtime.log:65` | 5，`minimal/run.log:51` |
| REF cause / mtval | 4 / 0xf1，`runtime.log:91` | 4 / 0x1，`minimal/run.log:77` |
| mismatch 位置 | `runtime.log:189` | `minimal/run.log:175` |

两者不是逐指令等价程序，但都在同一低地址 PMA 区域对 8 字节非对齐 load
复现了 5/4 分歧。最小用例说明，不需要原始随机程序的全部复杂指令上下文即可
触发此分类差异。本报告引用的历史 FST 来自原始 seed，不能把其 7106 等周期套用到 minimal。

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
当前 DUT commit 包含 #5700 引入的 `afUnalignMMIO` 逻辑，本例满足其触发条件，
观察结果与该提交定义的实现路径一致；没有证据要求把当前 DUT 的 mcause=5 改回 4。**
若要消除 difftest 分歧，应进一步核对 REF 对 MMIO、PBMT=NC/IO、权限失败及
真实设备映射的规则，而不是直接关闭全部对齐检查。尚未确定并验证针对 #6265
的 REF 修复提交，本次也未执行修复后仿真。

## 11. 产物完整性与边界

- 当前 `replay-work/runtime.log:65,91,189` 分别给出 DUT 异常、REF 状态和
  mcause mismatch；最小用例另有独立日志。当前 FST 非空，但本次未重新解析它。
- `replay-work/build.log` 和 `replay-work/return_code` 当前均存在；后者内容为 `1`。
  根据 `replay.sh`，该文件保存 emulator 的 `PIPESTATUS[0]`，不是构建退出码，
  也不是波形验证器退出码。当前日志的中止原因是 difftest mcause mismatch。
  构建日志存在不等于本次已逐项复核完整构建过程。
- `replay.sh` 会下载、构建并覆盖日志、反汇编和 FST，本次未执行。
  它固定 DUT 和 xs-env，实际 REF 来自 DUT 的 ready-to-run 子模块，而非当前 NEMU 工作树构建。
  再次执行前应归档现有产物及其哈希；该脚本不是只读复核工具。
- 当前脚本使用 `--dump-wave-full --wave-path`，没有 `-e 0`。
  脚本文本表示导出意图，不等于对实际波形覆盖范围的证明。
- `minimal/run.sh` 仅以 `mcause different` 字符串判复现成功，未自动核对故障 PC、
  地址和 4/5 方向。本文使用保存日志中的具体字段，不单独依赖脚本退出码。
- §0 的当前文件哈希与 §8 的历史快照哈希不同。报告明确区分两者，不能用当前文件
  替代历史输入来声称逐字节复验；上传前若再次生成产物，应重新计算哈希并更新验证结果。
- 运行日志未记录当时 REF SO 的哈希，bundled 来源声明也不等同于可复现构建证明。
- 维护者意图来自 `summary.txt`，具体 issue 标签未在当前元数据中保存且未在线复核；
  #5700 的设计依据来自本地提交记录。两类历史依据分开引用。

## 12. 后续修改与回归建议

本节列出建议，**不是已完成的修复或回归结果**。

1. **先统一 REF/DUT 的异常策略，而非直接修改 DUT 的 mcause。** 核对 PMA `C=0`、
   PBMT=PMA/NC/IO 与非幂等设备区域的对应关系；不能将“不可缓存”一律等同于非幂等。
2. **建立对照用例矩阵。** 覆盖本例 `0xf1`、最小用例 `0x1`、已注册设备地址、
   无设备映射地址，以及 NC/IO 区域；分别测试自然对齐和非对齐 load/store、权限允许
   和权限失败。真实设备测试需考虑副作用，不应随意对真实硬件执行。
3. **锁定 REF 来源。** 保存实际加载 SO 的 SHA-256、来源 revision、配置和构建命令；
   若调整 `CONFIG_MMIO_AC_SOFT`，先做条件性实验，不能把关闭宏直接当作最终修复。
4. **执行修复前后对照。** 同时记录 DUT/REF 的 `mcause`、`mepc`、`mtval`、故障指令和
   difftest 结果；除消除本例 mismatch 外，还需确认权限错误、真实设备和 NC/IO 用例未回归。
5. **重新核对待上传波形。** 对实际上传 FST 重新定位事务并采样 §8 的有效性、指针、
   exceptionVec 和 CSR 字段，将新哈希与结果一起写入正文；未执行之前保留历史快照限定。
