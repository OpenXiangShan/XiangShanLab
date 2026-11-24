### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs. [#6594](https://github.com/OpenXiangShan/XiangShan/issues/6594) and [PR #6546](https://github.com/OpenXiangShan/XiangShan/pull/6546) describe this same defect; #6546 was closed without being merged, so the buggy code is still present on the default branch. See "Additional context".
- [x] I have reproduced the issue using the latest commit on the default branch. (See "Environment": `VTypeGen.scala` is byte-identical between the build I ran and the current default-branch head.)
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content.

### Branch

kunminghu-v3

### Describe the bug

A **legal** `vsetivli` whose AVL immediate is `0` sets `vtype.vill` whenever the requested configuration changes VLMAX with respect to the current configuration.

`VTypeGen` reuses one keep-VL detection predicate for both `vsetvli` and `vsetivli`:

```scala
// src/main/scala/xiangshan/backend/decode/VTypeGen.scala:91-93
private val keepVlMask = VecInit((0 until DecodeWidth).map { i =>
  isVsetiVec(i) && instFieldVec(i).RD === 0.U && instFieldVec(i).RS1 === 0.U
})
```

`isVsetiVec = isVsetivli || isVsetvli` (line 36). For `vsetivli`, bits `[19:15]` are the five-bit AVL immediate rather than `rs1`, so a zero immediate satisfies `RS1 === 0.U`. `vlmaxChange` (line 99) then ORs `illegal` on top of an otherwise fully legal configuration (lines 107-111). That vtype metadata is attached to the instruction by IBuffer and reaches architectural state through VTypeBuffer at retirement, so this is an architectural correctness issue rather than harmless speculative metadata.

The smallest reproducer needs **no preceding vector instruction at all**, because the reset value of `vtypeSpec` is `VType.init()` (`illegal = 1, vsew = 0, vlmul = 0`, i.e. VLMAX = VLEN/8), which already differs from any other SEW/LMUL ratio:

```asm
  li       t0, 0x600                 # mstatus.VS = 3
  csrw     mstatus, t0
  vsetivli x0, 0, e16, m1, tu, mu    # legal: vsew = e16, vlmul = m1
  csrr     a1, vtype
```

difftest output (`right` = NEMU, `wrong` = DUT):

```
  vtype different at pc = 0xffff8cc333dda55a, right = 0x0000000000000008, wrong = 0x8000000000000008
Core 0: ABORT at pc = 0xffff8cc333dda55a
Core-0 instrCnt = 9, cycleCnt = 8,282, IPC = 0.001087
```

(The `pc` in the message is a speculative PC; the `vsetivli` itself is at `0x80000008`.) The CSR state snapshot confirms the committed architectural value:

```
CSR_STATE_COVER_V1|...|group=vector|vstart=0000000000000000|vl=0000000000000000|vtype=8000000000000008|vlenb=0000000000000010
```

Controls, all on the same build and the same reference:

| Program | Result |
|---|---|
| `vsetivli x0, 0, e16, m1, tu, mu` | **mismatch**, `wrong = 0x8000000000000008` |
| `vsetivli x0, 4, e16, m1, tu, mu` (AVL immediate 4) | no mismatch |
| `vsetivli t0, 0, e16, m1, tu, mu` (rd != x0) | no mismatch |
| kunminghu-v2 `7a1bbc6087` running the same minimal program | no mismatch, commits `vtype = 0x0000000000000008` |

The two-instruction sequence below separates the legal and illegal paths cleanly. The CSR snapshots, in order, show the reset value, the value after the legal `vsetivli x0, 0, e8, m2`, and the value after the illegal `vsetivli x0, 0, 0x23`:

```
vtype=8000000000000000   # reset (VType.init(): illegal = 1, rest zero)
vtype=8000000000000001   # after the LEGAL vsetivli x0, 0, e8, m2   <-- should be 0x1
vtype=8000000000000000   # after the ILLEGAL vsetivli x0, 0, 0x23   <-- correct: vill-only
```

So the illegal-`vtypei` path is correct (`VSetFuncUnit` clears `vma/vta/vsew/vlmul` when `vill` is set), and the defect is confined to `VTypeGen` applying the keep-VL predicate to the immediate form of a **legal** instruction.

### Expected behavior

RISC-V V 1.0, section 3.4.4:

> When the `vill` bit is set, the other XLEN-1 bits in `vtype` shall be zero.

Section 6 (Configuration-setting instructions):

> If the `vtype` setting is not supported by the implementation, then the `vill` bit is set in `vtype`, the remaining bits in `vtype` are set to zero, and the `vl` register is also set to zero.

`e16, m1` is supported by the implementation (it is accepted with a nonzero AVL immediate and with a nonzero destination register on the same build), so neither clause applies. The expected committed state is `vtype = 0x8`, `vl = 0`, `vill = 0` — which is what NEMU and kunminghu-v2 both produce. The DUT value `0x8000000000000008` has `vill` set together with a nonzero `vsew`, which section 3.4.4 explicitly forbids.

### Environment

- Hardware
  - CPU: AMD EPYC 7543 32-Core Processor
  - Memory (GB): 247
  - Storage (GB): 3724
- Software
  - Operating system: Ubuntu 26.04.1 LTS (x86_64)
  - gcc version: gcc (Ubuntu 15.2.0-16ubuntu1) 15.2.0
  - java version: OpenJDK 21 (emulator build), OpenJDK 25.0.2 (host default)
  - mill version: 0.12.17
  - verilator version: Verilator 5.038 2025-07-08
- Repo
  - XiangShan commit id: `a253c1163982ae0b485b3a2ed9b61253d980d837` (kunminghu-v3, 2026-11-08). `src/main/scala/xiangshan/backend/decode/VTypeGen.scala` is **byte-identical** (blob `71d087529fafae26e77ee140a41f052ecfb10b30`) at the current default-branch head `9df386608e590e9d2e1e80d10ed5710de21f9d08` (2026-11-17), so the defect is present on the latest mainline; I did not rebuild from that head.
  - NEMU commit id: `d2083355b0eec5edc2a85586f3475fcfd50a9c8f` (upstream master, built with `make riscv64-xs-ref_defconfig`)
  - difftest submodule: `ecdf41e225a93699b9a1141ec4d0fc1ff485b890`
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig`, with `--csr-state-cover --uarch-state-cover` added to the RTL generation step for the state snapshots quoted above. `VTypeGen.scala` is unmodified in this build.
  - Run command: `./bin/emu -i probe.elf --diff riscv64-nemu-interpreter-so`

### To Reproduce

Assemble and link the following with
`riscv64-unknown-elf-gcc -nostdlib -nostartfiles -static -fno-pic -march=rv64gcv -mabi=lp64d -Wl,-T,xiangshan.ld -Wl,-e,_start -o probe.elf probe.S`:

```asm
.option norvc
.section .text.init
.globl _start
_start:
  li   t0, 0x600
  csrw mstatus, t0
  vsetivli x0, 0, e16, m1, tu, mu
  csrr a1, vtype
  .word 0x0000006b        # difftest GOOD TRAP
```

```ld
OUTPUT_ARCH("riscv")
ENTRY(_start)
SECTIONS { . = 0x80000000; .text.init : { KEEP(*(.text.init)) } /DISCARD/ : { *(.comment) *(.note*) *(.riscv.attributes) }
```

Run `emu -i probe.elf --diff ready-to-run/riscv64-nemu-interpreter-so` and observe the `vtype` mismatch at `instrCnt = 9`.

The two-instruction variant used for the CSR sequence above:

```asm
  vsetivli x0, 0, e8, m2, tu, mu   # legal
  vsetivli x0, 0, 0x23             # illegal vtypei (vsew = 4)
  csrr     a1, vtype
```

Encodings, for convenience:

| Program | Encoding |
|---|---|
| `vsetivli x0, 0, e16, m1, tu, mu` | `0xc0807057` |
| `vsetivli x0, 4, e16, m1, tu, mu` | `0xc0827057` |
| `vsetivli t0, 0, e16, m1, tu, mu` | `0xc08072d7` |
| `vsetivli x0, 0, e8, m2, tu, mu` | `0xc0107057` |
| `vsetivli x0, 0, 0x23` (illegal `vtypei`) | `0xc2307057` |

### Additional context

**How this was found.** Differential fuzzing driven by symbolic execution (Isla over a Sail RISC-V IR), comparing kunminghu-v3 against NEMU with vector-CSR coverage enabled. In one campaign run on this build, 16 of 494 cases mismatched, and all 16 carried the same signature `right = 0x0000000000000001, wrong = 0x8000000000000001` (the seed used `e8, m2`, so the legal value is `0x1`). Every replayed case differed in `vtype` only, with no other register or memory difference. The narrow trigger — a VLMAX-changing `vtypei` combined with a zero AVL immediate — is why ordinary legal-`vtypei` fuzzing does not hit it.

**Relation to #6594 and PR #6546.** This is the same defect as [#6594](https://github.com/OpenXiangShan/XiangShan/issues/6594), and the root cause and fix in [PR #6546](https://github.com/OpenXiangShan/XiangShan/pull/6546) match what we derived independently. PR #6546 was closed without being merged, and the buggy line is still present at the current default-branch head. This report adds two things beyond #6594:

1. The trigger is **not** conditional on a preceding LMUL = 8 configuration. The reset value of `vtypeSpec` (`VType.init()`, `vsew = 0, vlmul = 0`, VLMAX = VLEN/8) already differs from any other SEW/LMUL ratio, so a bare `vsetivli x0, 0, e16, m1, tu, mu` as the first vector instruction is enough. The reproducer therefore needs no vector setup at all, and the defect is much easier to hit than #6594 suggests.
2. The illegal-`vtypei` path is correct. `VSetFuncUnit` clears `vma/vta/vsew/vlmul` when `vill` is set, so an unsupported `vtypei` yields the required vill-only `0x8000000000000000`. The defect is confined to `VTypeGen`'s keep-VL predicate being applied to the immediate form.

**Suggested fix.** Restrict keep-VL detection to the register form, where bits `[19:15]` really are `rs1`:

```diff
- isVsetiVec(i) && instFieldVec(i).RD === 0.U && instFieldVec(i).RS1 === 0.U
+ isVsetvli(i)  && instFieldVec(i).RD === 0.U && instFieldVec(i).RS1 === 0.U
```

The cumulative VLMAX chain, the register-form `vsetvl` execution path, and the rest of the vector configuration logic are unaffected.

**Out of scope.** The register form `vsetvli x0, x0, e16, m1, tu, mu` on this build does not report a `vtype` value mismatch; it trips an assertion in `VTypeBuffer.sv`, which belongs to the known keep-VL / VTypeBuffer family rather than to this report. We did not investigate it further.

**AI use disclosure.** This report was drafted with AI assistance. The analysis, RTL reading, and differential experiments were carried out by an automated investigation agent, and the conclusions were reviewed by the reporting contributor. All quoted values are taken directly from the experiment logs.

**No attachments.** Per the reporter's preference, all material is inline. The reproducer above is complete: it assembles, links, and reproduces the mismatch as shown.
