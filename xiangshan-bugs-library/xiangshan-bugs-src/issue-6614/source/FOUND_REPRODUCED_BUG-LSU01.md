# XS-IT-015 [IT][MMU/LSU][H/PMA] HLVX 最终 PMA/PMP 检查漏掉 execute permission，R=1 X=0 区域被当成普通 load 读出

## Verdict

| 字段 | 结论 |
| --- | --- |
| Classification | **CONFIRMED_DEFECT / REPRODUCIBLE_FAILURE**：标准语义明确、源码根因闭合、`--diff` 两次稳定复现 |
| Ticket action | **keep / release blocker**：必须修 NewLoadUnit 发往 DTLB/PMA/PMP 的 `TlbCmd`：HLVX 最终物理检查要用 execute 语义，并以 **load** access fault 上报；PMP sibling（LSU-02 SPVP/dmode）另案，不能混入本 verdict |
| Oracle | RISC-V Privileged Spec H 扩展 HLVX 最终物理属性契约 + AM self-check。本 pin 的 NEMU 与 DUT **同缺陷**（双方都不 trap），因此 `HIT GOOD TRAP` **不是**架构通过；判别以 AM `FAIL` 打印与 H 规范为准 |
| Stable signature | `hlvx-pma-xperm`；`trap_count=0 mcause=0 mtval=0x0 mtval2=0x0 htval=0x0 hlvx=0xc33ca55a`；`FAIL hlvx bypassed PMA execute permission`；`Difftest enabled`；`HIT GOOD TRAP at pc = 0x800002ca`；`instrCnt = 8,627` `cycleCnt = 46,983`，两次相同 |
| XS V3 pin（复现 emu） | `4e0288460d483e6048000a13fa7825f0f81d8e38`（`XSNoCTopConfig --enable-difftest`，DefaultConfig tile） |
| Commit under audit / HEAD 仍活 | workspace HEAD `01d1bbf3665ec912c4cc5cdab4c34bd24f774de5` 上 NewLoadUnit/PMA/PMP 赋值等价（行号 cmd=429、hlvx=431、pmpUnaccessable=936）；不是 V2 `#6017` 的回归结论，V2 修复不是本 pin 祖先 |
| Image | `cases/lsu01-hlvx-pma-xperm/build/lsu01-hlvx-pma-xperm-riscv64-xs.bin` (`0a8b81fc0483e34277231ab3cc6ff21ddd3158075fad1aad7d7cff84d06d8296`) |
| 默认配置可达 | 是：`HasHExtension=true`；`HLVX_WU` 在译码表；默认 DRAM PMA `0x80000000` 已是 `R=1 W=1 X=0`；无需特殊配置位 |
| 业务严重度 | **High correctness / isolation**：hypervisor 用 HLVX 模拟 guest 取指时，最终物理 X 检查被丢掉，可从不可执行区域读出指令字节，破坏 guest 隔离与 PMA/PMP 执行权限 |

## Summary（60 秒）

| 项目 | 期望 | 实际 DUT |
| --- | --- | --- |
| 最终物理检查 | `HLVX.WU` 对最终物理地址使用 **execute** 权限（PMA/PMP X）；缺 X 必须 fault | 发往 DTLB 的 `cmd := TlbCmd.read`；PMA/PMP 只在 `isExec` 时抬 `resp.instr`；load 路径只看 `pmp.ld` |
| 异常类型 | 即使检查的是 X，仍报 **load** access fault（`mcause=5`），不是 instruction access fault | 无 trap：`trap_count=0 mcause=0` |
| 数据 | 不得写回目标字节 | DUT 读到 `hlvx=0xc33ca55a`（页内 `5a a5 3c c3` 的 LE word） |
| DIFFTEST | 本 pin NEMU 与 DUT 同路径：都不 trap、同一 GPR | `--diff` 开着，双方一致 → `HIT GOOD TRAP`。这只证明 REF 也错，**不能**当通过 |
| 负控（源码） | 页表级 HLVX 已按 `perm.x` 检查（`ldPermFail := Mux(hlvx, perm.x, …)`） | 本用例 bare translation、最终 PMA 才是触发点；不是页表 X 漏检 |

### 白话

`HLVX` 是给 hypervisor 模拟 guest 取指的：最后落到物理地址时必须按 **可执行** 来查，查不过仍按 **load 异常** 上报。当前 Load 单元把 HLVX 当成普通 load 发给 PMA/PMP（`TlbCmd.read`），PMA/PMP 的 X 位只在 `cmd=Exec` 时生效，load 路径又只消费 `pmp.ld`。于是一块 `R=1 X=0` 的物理页（默认 DRAM PMA 已是这样，AM 再显式写一条 NAPOT 4KiB）被 HLVX 直接读出数据，没有任何 trap。

## 直观图

```text
软件/ISA 视角
  M-mode, H 扩展
  目标 4KiB 页写入 5a a5 3c c3
  PMA NAPOT 4KiB: R=1 W=1 C=1 X=0
  hstatus.SPVP = 0
  hlvx.wu a0, (a1)          # 0x6835c573

  期望：load access fault, mcause=5, a0 不取得目标字
  实际：无 trap, a0 = 0xc33ca55a

RTL 实际链路
  Decode HLVX_WU -> LSUOpType.hlvxwu
      -> NewLoadUnit tlbHlvx = isHlvx(fuOpType)
      -> tlbReq.cmd  := TlbCmd.read          // 不是 exec
      -> tlbReq.hlvx := true
      -> PMA.pma_check: resp.instr := isExec(cmd) && !cfg.x
                        resp.ld    := isRead(cmd) && !cfg.r
         cmd=read, cfg.r=1, cfg.x=0  => ld=0, instr=0
         （X 缺失只在 isExec 时抬 instr；read 路径完全静默）
      -> Load 路径 pmpUnaccessable := pmp.ld && tlbHit
         只看 ld；即便 instr 被抬起也不会变成 load AF
      -> 数据写回 a0

DIFFTEST:
  DUT 与本 pin NEMU 都不 trap，GPR 一致
  AM 打印 FAIL；emu 因 DUT==REF 打 HIT GOOD TRAP
```

## Spec 对照

Spec 路径相对 xs-it workspace。

| Tag | Source line | Short original-text anchor |
| --- | --- | --- |
| `XS-IT-015-SPEC-01` | `standards/riscv-isa-manual/src/priv/hypervisor.adoc:1488` | same as HLV.HU and HLV.WU |
| `XS-IT-015-SPEC-02` | `standards/riscv-isa-manual/src/priv/hypervisor.adoc:1489` | execute permission takes the place of read permission |
| `XS-IT-015-SPEC-03` | `standards/riscv-isa-manual/src/priv/hypervisor.adoc:1493` | supervisor physical memory attributes must grant both execute and read |
| `XS-IT-015-SPEC-04` | `standards/riscv-isa-manual/src/priv/hypervisor.adoc:1501` | HLVX cannot override machine-level physical memory protection |
| `XS-IT-015-SPEC-05` | `standards/riscv-isa-manual/src/priv/hypervisor.adoc:1505` | require execute permissions |
| `XS-IT-015-SPEC-06` | `standards/riscv-isa-manual/src/priv/hypervisor.adoc:1506` | still raise the same exceptions as other load instructions |
| `XS-IT-015-SPEC-07` | `standards/riscv-isa-manual/src/priv/machine.adoc:2855` | termed _physical memory attributes_ (PMAs) |
| `XS-IT-015-SPEC-08` | `standards/riscv-isa-manual/src/priv/machine.adoc:2956` | can specify whether instruction fetch is supported |
| `XS-IT-015-SPEC-09` | `standards/riscv-isa-manual/src/priv/machine.adoc:3419` | fetch … without execute permissions raises an instruction access-fault |
| `XS-IT-015-SPEC-10` | `standards/riscv-isa-manual/src/priv/machine.adoc:3421` | load … without read permissions raises a load access-fault |

| 条款 | 对本问题的约束 | DUT 偏差 |
| --- | --- | --- |
| `XS-IT-015-SPEC-01` / `XS-IT-015-SPEC-02` | HLVX 在地址翻译中用 X 代替 R | 页表路径已按 `hlvx → perm.x` 做对；本 bug 不在页表 |
| `XS-IT-015-SPEC-03` / `XS-IT-015-SPEC-04` | 最终物理地址的 PMA（经 PMP 修饰后的 supervisor PMA）必须同时给 X 和 R；HLVX 不能覆盖 PMP；缺 X 不得完成访问 | 最终 PMA/PMP 按普通 load 查 R，X 位被 `cmd=read` 屏蔽 |
| `XS-IT-015-SPEC-05` / `XS-IT-015-SPEC-06` | 即便查的是 X，异常仍是 **load** 类，不是 fetch 异常 | DUT 两者都没有：`trap_count=0` |
| `XS-IT-015-SPEC-07` / `XS-IT-015-SPEC-08` | 主存 PMA 可以禁止取指（`X=0` 合法） | 默认 DRAM PMA 正是 `R=1 X=0`；AM 再写 NAPOT 只是把触发写死 |
| `XS-IT-015-SPEC-09` / `XS-IT-015-SPEC-10` | 普通取指缺 X → instruction AF；普通 load 缺 R → load AF。HLVX 是“用 X 判、用 load 报” | DUT 既没按 X 判，也没报 load AF |

### 交叉结论

```text
HLVX 页表权限用 X（已实现）
          + 最终物理 PMA/PMP 也必须用 X
          + 失败时仍报 load access fault（mcause=5）
          ↓
NewLoadUnit 必须让 PMA/PMP 看到 Exec（或等价地消费 pmp.instr）
          ↓
当前 cmd=read 且只看 pmp.ld
          ↓
R=1 X=0 页被当成普通 load 读出，无 trap
```

### 结论边界

| 可以严谨地说 | 不能直接说 |
| --- | --- |
| 该 DUT 对 `HLVX.WU` 到 `R=1 X=0` 最终物理页稳定不 trap 并写回数据 | 页表级 HLVX X 检查也坏了（`TLB.scala` `ldPermFail` 已用 `perm.x`） |
| AM `FAIL` + H 规范是本票 oracle；`HIT GOOD TRAP` 只说明本 pin NEMU 与 DUT 同错 | 必须以 NEMU mismatch 才算 confirmed |
| 默认 DRAM PMA 已是 `R=1 X=0`，AM 再写 NAPOT 是把触发钉死，不是引入额外假设 | 没有 PMA CSR 编程就不可达 |
| 异常必须是 load AF 而不是 instruction AF | 修法只要把 `cmd` 改成 `exec` 就完事——若改成 `exec` 却走了 `pmp.instr`→instruction AF，会违反 SPEC-05 |
| LSU-02（HLV PMP 用 dmode 而非 SPVP）是独立根因 | 本票已证明 SPVP/PMP 也 confirmed |

## Chisel 源码证据

Source 路径相对 locked V3 CI emu 源码 `4e0288460d`。HEAD `01d1bbf36` 同赋值仍在，行号括注。

| Tag | Source line | Short original-text anchor |
| --- | --- | --- |
| `XS-IT-015-SRC-01` | `src/main/scala/xiangshan/Parameters.scala:60` | `HasHExtension: Boolean = true` |
| `XS-IT-015-SRC-02` | `src/main/scala/xiangshan/backend/decode/isa/Instructions.scala:357` | `HLVX_WU :BitPat = BitPat("b011010000011?????100?????1110011")`（4e028） |
| `XS-IT-015-SRC-03` | `src/main/scala/xiangshan/backend/vector/Decoder/Uop/ScalaUopTable.scala:394` | `_.HLVX_WU -> hlvxwu`（4e028）；HEAD 等价：`DecodeUnit.scala:531` `HLVX_WU -> LSUOpType.hlvxwu` |
| `XS-IT-015-SRC-04` | `src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala:406` | `io.tlbReq.bits.cmd := TlbCmd.read`（HEAD:429） |
| `XS-IT-015-SRC-05` | `src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala:408` | `io.tlbReq.bits.hlvx := tlbHlvx`（HEAD:431） |
| `XS-IT-015-SRC-06` | `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:438` | `def read = "b00".U` / `def exec = "b10".U` |
| `XS-IT-015-SRC-07` | `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:446` | `def isRead` / `def isExec` |
| `XS-IT-015-SRC-08` | `src/main/scala/xiangshan/backend/fu/PMA.scala:214` | `resp.ld := TlbCmd.isRead(cmd) && !cfg.r` |
| `XS-IT-015-SRC-09` | `src/main/scala/xiangshan/backend/fu/PMA.scala:216` | `resp.instr := TlbCmd.isExec(cmd) && !cfg.x` |
| `XS-IT-015-SRC-10` | `src/main/scala/xiangshan/backend/fu/PMP.scala:406` | `resp.ld := TlbCmd.isRead(cmd) && !TlbCmd.isAmo(cmd) && !cfg.r` |
| `XS-IT-015-SRC-11` | `src/main/scala/xiangshan/backend/fu/PMP.scala:408` | `resp.instr := TlbCmd.isExec(cmd) && !cfg.x` |
| `XS-IT-015-SRC-12` | `src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala:913` | `val pmpUnaccessable = pmp.ld && tlbHit`（HEAD:936） |
| `XS-IT-015-SRC-13` | `src/main/scala/xiangshan/cache/mmu/TLB.scala:466` | `ldPermFail := Mux(hlvx, perm.x, perm.r \|\| mxr && perm.x)` |
| `XS-IT-015-SRC-14` | `src/main/scala/system/SoC.scala:61` | `PMAConfigEntry(0x80000000L, a = 1, w = true, r = true)` |
| `XS-IT-015-SRC-15` | `src/main/scala/xiangshan/backend/fu/util/CSRConst.scala:33` | `PmacfgBase = 0x7C0` / `PmaaddrBase = 0x7C8` |

| 文件:行 | 事实 | 错误对照 |
| --- | --- | --- |
| `Parameters.scala:60` | 默认开 H | HLVX 默认配置可达 |
| `Instructions.scala:357` / `ScalaUopTable.scala:394`（HEAD `DecodeUnit.scala:531`） | `HLVX_WU` 编码 `0x6835c573`（rd=a0, rs1=a1）进入 load 译码 | 不是非法指令路径 |
| `NewLoadUnit.scala:406` | 所有 load（含 HLVX）`cmd := TlbCmd.read` | 最终 PMA/PMP 看不到 Exec |
| `NewLoadUnit.scala:408` | `hlvx` 旁路位置 1，只喂给 TLB 页表检查 | 页表用得到，PMA/PMP 不用 `hlvx` 只看 `cmd` |
| `MMUBundle.scala:438-448` | `read` 与 `exec` 是互斥 2-bit | 不能靠 `hlvx` 旁路让 `isExec(read)` 变真 |
| `PMA.scala:214-216` / `PMP.scala:406-408` | X 缺失只抬 `resp.instr`，且仅当 `isExec(cmd)` | `cmd=read` 时 `X=0` 完全静默 |
| `NewLoadUnit.scala:913` | load 不可访问 := `pmp.ld` | 即使将来 PMA 在 read 上误抬 `instr`，load 也不看 |
| `TLB.scala:466` | 页表 load 权限：`hlvx` 时查 `perm.x` | 页表契约是对的；本票根因在最终物理检查 |
| `SoC.scala:61` | 默认 DRAM PMA `R=1 W=1 X=0`（无 `x = true`） | 不写 PMA CSR 也满足 `R=1 X=0`；AM 写 NAPOT 是显式钉死 |
| `CSRConst.scala:33-34` | PMA CSR `0x7C0/0x7C8` | 与 AM `csrw 0x7c0/0x7c8` 对齐 |

## Root-Cause Chain

```text
HLVX.WU to a R=1 X=0 physical page
        -> decode hlvxwu, firstIssue to NewLoadUnit
        -> tlbReq.cmd = TlbCmd.read
        -> tlbReq.hlvx = true
        -> TLB page-walk uses perm.x for hlvx   (this stage is correct)
        -> PMA/PMP pma_check/pmp_check(cmd=read, cfg.x=0)
              resp.ld=0 (cfg.r=1), resp.instr unused because !isExec
        -> pmpUnaccessable = pmp.ld && tlbHit = 0
        -> data returns; no load access fault
        -> AM: trap_count=0, hlvx=0xc33ca55a
        -> NEMU (this pin) takes the same path → HIT GOOD TRAP
```

## 业务影响

| 场景 | 影响 |
| --- | --- |
| Hypervisor 用 HLVX 模拟 guest 取指 | guest 不可执行物理页（ROM 数据、MMIO、W^X 数据段）仍可被 HLVX 读出指令字节，破坏隔离 |
| PMA 划分的 NX DRAM / 外设窗口 | 平台把主存标成不可取指是合法 PMA；HLVX 把这条属性当不存在 |
| PMP execute-only vs read-only 分区 | 最终物理检查与页表检查不一致：页表拦得住缺 X 的 VA，PMA 拦不住缺 X 的 PA |
| 安全/W^X 策略 | 数据页 `R=1 X=0` 被当成可“取指窥探”的窗口，削弱 W^X |
| 不是性能问题 | GPR 被写成不该存在的值，异常路径完全没走 |

限制边界：触发需要 H 扩展 + 一次 HLVX 落到 `R=1 X=0` 的最终物理页。页表若已因缺 X 而 page-fault，本 PMA 洞不会暴露。本票用 bare 翻译 + 显式 PMA，把最终物理检查单独钉死。

## Minimal AM reproducer

AM workload 路径相对本 bug-hunt session。

| 文件:行 | 事实 |
| --- | --- |
| `cases/lsu01-hlvx-pma-xperm/main.c:4` | `CSR_PMACFG0 0x7c0` |
| `cases/lsu01-hlvx-pma-xperm/main.c:5` | `CSR_PMAADDR0 0x7c8` |
| `cases/lsu01-hlvx-pma-xperm/main.c:6` | `PMA_R` / `PMA_W` / `PMA_X=0x04` / `PMA_A_NAPOT` / `PMA_C` |
| `cases/lsu01-hlvx-pma-xperm/main.c:11` | 期望 `CAUSE_LOAD_ACCESS_FAULT = 5` |
| `cases/lsu01-hlvx-pma-xperm/main.c:21` | 4KiB 对齐目标页 |
| `cases/lsu01-hlvx-pma-xperm/main.c:85` | `.word 0x6835c573` = `hlvx.wu a0, (a1)` |
| `cases/lsu01-hlvx-pma-xperm/main.c:97` | 写入 `5a a5 3c c3` |
| `cases/lsu01-hlvx-pma-xperm/main.c:108` | PMA NAPOT 4KiB：`C\|A_NAPOT\|W\|R`，**不含 X** |
| `cases/lsu01-hlvx-pma-xperm/main.c:109` | 清 `hstatus.SPVP` |
| `cases/lsu01-hlvx-pma-xperm/main.c:112` | 触发 `hlvx.wu` |
| `cases/lsu01-hlvx-pma-xperm/main.c:122` | `trap_count==0` → FAIL |
| `cases/lsu01-hlvx-pma-xperm/main.c:126` | `mcause != 5` → FAIL |

## Reproduction

```sh
cd XiangShan/build/bug-hunt/session-015-backend-p0-20260921

./scripts/build-emu.sh          # 只校验 pinned CI emu，不重建
./scripts/build-case.sh lsu01-hlvx-pma-xperm
./scripts/rerun-case.sh lsu01-hlvx-pma-xperm 2 diff
```

预期复现结果：AM 打印 `FAIL hlvx bypassed PMA execute permission`；emu 因 DUT==NEMU 退出码 0 并打 `HIT GOOD TRAP`。**不要**把退出码 0 读成架构通过。两次日志签名（`trap_count`/`hlvx`/`instrCnt`）必须相同。

两次稳定日志：

```text
logs/20260921-132841-lsu01-hlvx-pma-xperm-diff-rerun1.log
logs/20260921-133025-lsu01-hlvx-pma-xperm-diff-rerun2.log
```

稳定签名：

```text
hlvx-pma-xperm start
trap_count=0 mcause=0 mtval=0x0 mtval2=0x0 htval=0x0 hlvx=0xc33ca55a
FAIL hlvx bypassed PMA execute permission
The first instruction of core 0 has commited. Difftest enabled.
HIT GOOD TRAP at pc = 0x800002ca
instrCnt = 8,627  cycleCnt = 46,983
```

`0xc33ca55a` 是目标页 LE word：`[0]=0x5a [1]=0xa5 [2]=0x3c [3]=0xc3`。

负控（源码，不是第二条 AM）：

```text
TLB.scala:466  ldPermFail := Mux(hlvx, perm.x, …)
```

页表级 HLVX 已按 X 检查。本 AM 走 bare 翻译，专门打最终 PMA。LSU-02 是 PMP+SPVP 另案，不并入。

## Evidence package

| 项目 | 路径 |
| --- | --- |
| 本票报告 | `FOUND_REPRODUCED_BUG-LSU01.md` |
| 会话总览 | `FOUND_REPRODUCED_BUG.md` |
| 设计链 | `notes/source-chain.md` |
| 可判定性 | `notes/decidability.md` |
| Lean 命题链 | `formal/lean4/BackendP0.lean`（`lsu01_hlvx_pma_x_lost`） |
| Evidence chain | `evidence/EVIDENCE_CHAIN.md` |
| Runtime binding | `evidence/emu-pin.txt` |
| Compact extract | `evidence/compact/20260921-132841-lsu01-hlvx-pma-xperm-diff-rerun1.log.key.txt` |
| Rerun summary | `results/lsu01-hlvx-pma-xperm.rerun-summary.txt` |
| Image sha256 | `results/lsu01-hlvx-pma-xperm.image.sha256` |

Runtime binding：

```text
validation_tier=ci-emu-pin
host=open03 Ubuntu 24.04.3 / glibc 2.39
toolchain=riscv64-linux-gnu-gcc 13.3.0
emu_runtime=rob-kmhv3-ci-validation-20260902
XS_EMU=4e0288460d483e6048000a13fa7825f0f81d8e38
HEAD=01d1bbf3665ec912c4cc5cdab4c34bd24f774de5
EMU sha256=6b322b984c3f868a7c09246200f9b098803e8556f276cf69a39359c70eba5222
NEMU sha256=ed85429bd4ea0ab53ea498120d7deebe67f121e11085122ca4098508ffa6feb5
AM=f6dededaaec0a31cd3623941c84a7192e7e968f7
IMAGE sha256=0a8b81fc0483e34277231ab3cc6ff21ddd3158075fad1aad7d7cff84d06d8296
```

## Fix / decision required

1. HLVX 发往 PMA/PMP 的最终物理检查必须使用 execute 语义。可选实现：HLVX 时 `cmd := TlbCmd.exec`，或 PMA/PMP 在 `hlvx` 时对 X 走与 Exec 相同的比较。
2. 失败必须从 **load** 路径变成 `loadAccessFault`（`mcause=5`），不要变成 instruction access fault。若只把 `cmd` 改成 `exec` 却让 load 管道开始消费 `pmp.instr` 当取指异常，会违反 SPEC-05。
3. load 路径今日只看 `pmp.ld`。修法必须让 HLVX 缺 X 时 `pmpUnaccessable`（或等价异常）为真。
4. 不得只修页表：`ldPermFail` 已经看 `perm.x`。回归必须覆盖 **bare + PMA R=1 X=0** 和 **分页 + 页表 X=1 但 PMA X=0**。
5. LSU-02（PMP 检查器用 `dmode` 而非 SPVP）继续单独出票。

## Promotion gate

- [x] 源码链闭合：Decode HLVX_WU → NewLoadUnit `cmd=read`/`hlvx` → PMA/PMP `resp.instr` 仅 `isExec` → load 只看 `pmp.ld`
- [x] 标准语义闭合：最终物理属性必须给 X；失败报 load AF；DUT 无 trap 且写回数据
- [x] AM C/asm 最小业务触发（NAPOT PMA + `hlvx.wu`）
- [x] DIFFTEST 已开；本 pin NEMU 与 DUT 同错，oracle 是 H 规范 + AM self-check，不是 DUT≠REF
- [x] 两次稳定复现（`trap_count`/`hlvx`/`instrCnt` 相同）
- [x] 页表级 X 检查作为负控（源码），不与本 PMA 洞混因
- [x] runtime/toolchain/emu/NEMU/AM 哈希固定
- [ ] 修复补丁和回归验证尚未做

当前可严谨升级为 `CONFIRMED_DEFECT`。这不是 V2 `#6017` 的继承结论；本 pin 在 V3 CI emu 上独立复现，且 HEAD 同赋值仍在。
