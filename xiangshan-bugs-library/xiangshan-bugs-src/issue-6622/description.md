### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A legal keep-VL `vsetvli x0, x0, …` (rd=x0, rs1=x0, VLMAX unchanged) must retire: keep `vl`, update `vtype`, `vill=0`. XiangShan maps that encoding to opcode `uvset_vtypei_nop` (`isVset`, **no** `VlWen`). Decode then forces `uop.isVset := true.B` while `vlWen` stays 0. VTypeBuffer allocates only on `vlWen`. ROB consumes VTB on `isVset`. One keep-VL therefore dequeues a slot that was never enqueued. The hart aborts on a VTB pointer assertion; the instruction never retires.

Observed on kunminghu-v3 (pinned CI emu commit 4e0288460d; assignment still present on origin HEAD e4566c29e), twice, identical instrCnt:

```
vec01-keepvl-polarity start
Difftest enabled.
Assertion failed at .../VTypeBuffer.sv:1585
deqPtr is older than enqPtr!
ABORT at pc = 0x80000214
instrCnt = 786  cycleCnt = 12,400
```

The workload is two `vsetvli`s: `vsetvli rd, 4, e8, m1, tu, mu` then `vsetvli zero, zero, e16, m2, ta, ma`. DefaultConfig VLEN makes VLMAX(e8,m1) = VLMAX(e16,m2), so the second instruction is a **legal** keep-VL, not the reserved VLMAX-change case. The AM never prints pass/fail: the first keep-VL already trips VTB.

`--diff` ABORT is the observation. This is a DUT RTL assertion, not a NEMU architectural mismatch dump.

This is not VEC-01 (keep-VL vill polarity when VLMAX is unchanged). Origin already flipped that polarity (`vlmaxChange = !((vlmax & oldVlmax).orR)`). This ticket is the VTB alloc/consume mismatch, which is still live. It is also not PR #6546 (`vsetivli uimm=0` misclassified as keep-VL).

No matching issue or PR found on kunminghu-v3.


### Expected behavior

For S-mode (or M-mode with VS=On) `vsetvli x0, x0, e16, m2, ta, ma` after a legal `vsetvli rd, 4, e8, m1` on DefaultConfig (same VLMAX):

1. The keep-VL form is legal. It must retire. The hart must not abort.
2. `vl` must stay 4. `vtype` may change (SEW/LMUL/policy). `vill` must stay 0.
3. VTypeBuffer occupancy must be conserved: every VTB dequeue has a matching enqueue.
4. Implementations may set `vill` only when the keep-VL form is **reserved** (VLMAX would change, or `vill` was already 1). That is not this encoding.

ISA (riscv-isa-manual `src/unpriv/vector-common.adoc`, `norm:vsetvl_op_rs1_x0_rd_x0`): when rs1=x0 and rd=x0, the instructions operate as if the current `vl` is used as AVL; this form can only be used when VLMAX and hence `vl` is not actually changed by the new SEW/LMUL ratio. Use with a new SEW/LMUL ratio that would change VLMAX is reserved (`norm:vtype_vset_rsv`).

### Environment

- Hardware
  - CPU: AMD EPYC 7H12 64-Core Processor
  - Memory (GB): 1007
  - Storage (GB): 4946 (NFS home; 2297 available)
- Software
  - Operating system: Ubuntu 24.04.3 LTS
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
  - clang version: Ubuntu clang version 19.1.1 (1ubuntu1~24.04.2)
  - java version: openjdk version "21.0.12" 2026-07-21
  - mill version: Mill Build Tool version 0.12.3
- Repo
  - XiangShan commit id: `4e0288460d483e6048000a13fa7825f0f81d8e38` (commit that built the emu which produced the attached logs). Source assignment re-checked on default-branch HEAD `e4566c29e251c60159c6796db60a2569dd7700f2` (origin/kunminghu-v3, 2026-09-21): `uvset_vtypei_nop = Value(vset, notIll, vtypeI, avlVl)` still has no `VlWen`; `fromVSetChannelUop` still sets `isVset := true.B`; VTB still `needAllocVec = req.valid && req.bits.vlWen`; ROB still `needVTB := robEnq.isVset`.
  - NEMU commit id (if difftest failed with NEMU): `e884041bb6ab8be8c4123c7bc566d3bd32d7f7a6` (difftest enabled; DUT aborted on a VTypeBuffer assertion before a register-mismatch dump)
  - SPIKE commit id (if difftest failed with SPIKE): n/a
- Build & Run
  - Build command: used existing CI emu, not rebuilt. Binary compiled Sep 2 2026, XSNoCTopConfig --enable-difftest, DefaultConfig tile. emu sha256 `6b322b984c3f868a7c09246200f9b098803e8556f276cf69a39359c70eba5222`. NEMU so sha256 `ed85429bd4ea0ab53ea498120d7deebe67f121e11085122ca4098508ffa6feb5`.
  - Run command: `<emu> -i vec01-keepvl-polarity-riscv64-xs.bin --max-instr=500000 --diff=<nemu-so>`
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.

### To Reproduce

Minimal AM workload: enable VS, `vsetvli rd, 4, e8, m1`, then `vsetvli zero, zero, e16, m2, ta, ma` (same VLMAX on DefaultConfig). DUT should keep `vl=4`, `vill=0`, and retire. It instead aborts in VTypeBuffer (`deqPtr is older than enqPtr!`) at the first keep-VL.

Workload (source + bin) is attached: vec01-keepvl-polarity.zip
  - main.c
  - Makefile
  - build/vec01-keepvl-polarity-riscv64-xs.bin
    sha256 abd96ce735d9c468542e8466452b388afa00c1d87cc68fe1f21d133ff1c8d1d5

The directory is named for a vill-polarity AM. On this pin the first keep-VL already trips VTB, so this zip is the RR-01 reproducer. Do not substitute `rr01-keepvl-vtb` (that image hits a different assignment).

Steps:

1. Obtain the XiangShan emu from Environment (commit 4e0288460d, or any kunminghu-v3 emu that still has `uvset_vtypei_nop` without `VlWen`, VTB alloc on `vlWen`, and ROB `needVTB := isVset`).
2. Unzip the attached workload. Optional rebuild with AM:

   make -C vec01-keepvl-polarity ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=0 image

3. Run (same command shape as Environment / Run command):

   <emu> -i vec01-keepvl-polarity/build/vec01-keepvl-polarity-riscv64-xs.bin \
         --max-instr=500000 \
         --diff=<nemu-so>

Observed, twice, identical instrCnt:

```
vec01-keepvl-polarity start
Difftest enabled.
Assertion failed at .../VTypeBuffer.sv:1585
deqPtr is older than enqPtr!
ABORT at pc = 0x80000214
instrCnt = 786  cycleCnt = 12,400
```

`--diff` aborting is the observation. The AM self-check never runs.

[vec01-keepvl-polarity.zip](https://github.com/user-attachments/files/32503419/vec01-keepvl-polarity.zip)

### Additional context

Root cause (v3 HEAD e4566c29e, same assignment on pin 4e0288460d):

```
Opcode.scala:
  uvset_vtypei_nop = Value(vset, notIll, vtypeI, avlVl)   // no + VlWen
  // comment: VSETVLI when rs1==x0 and rd==x0; does not change vl

VsetDecoder.scala:
  (rdIsZero && rs1IsZero) -> uvset_vtypei_nop

DecodeChannels.scala fromVSetChannelUop:
  uop.isVset := true.B
  uop.vlWen  := vuop.renameInfo.bits.vlWen   // false for nop
  uop.isFirstUop := true.B
  uop.isLastUop  := true.B                   // single uop

VTypeBuffer.scala:
  // "There are two uops mapped to one vset inst. Only record the last here."
  needAllocVec = req.valid && req.bits.vlWen

RobBundles.scala:
  needVTB := robEnq.isVset
```

VTB still treats `vlWen` as “last uop of a two-uop vset”. Keep-VL `vsetvli` is a single uop that updates `vtype` and does not write `vl`. ROB counts it as a VTB consumer; VTB does not allocate. Occupancy underflows.

A fix must make VTB enqueue and ROB `needVTB` use the same predicate for this uop (for example allocate on `isVset`, not `vlWen`). Do not add `VlWen` to `uvset_vtypei_nop`: that would write the vl RF and break “keep existing vl”. Reserved keep-VL (`uvset_ill`) already has `VlWen` (vill=1, vl=0).

Related, not this bug:

- VEC-01: pin `VSetFuncUnit` used `(vlmax & oldVlmax).orR` as “VLMAX changed” (equality). Origin already flipped that polarity. Do not re-report it against origin HEAD. Pin emu still has it; this AM never reaches that check because VTB aborts first.
- PR #6546 / #6549: `vsetivli uimm=0` misclassified as keep-VL via `isVsetiVec`. Different encoding.

Please do not close this as VEC-01 or as fixed by the origin `vlmaxChange` polarity flip. That flip does not restore VTB conservation.

DefaultConfig. VLEN makes e8/m1 and e16/m2 share VLMAX. No extra inject.

AI: draft assembled with Claude Code. RTL quotes, log signatures, image SHA256, and origin greps were checked against origin/kunminghu-v3 e4566c29e and the archived emu logs before filing.
