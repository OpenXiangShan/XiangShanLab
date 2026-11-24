### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU with a minimal reproduction.
The test first configures `pmpaddr0 = -1` (full address-space coverage) and a permissive `pmpcfg0 = 0x1f` (`NAPOT`, `RWX`), then `main` executes three instructions:

```asm
main:
  li   s3, 0x99        # 80001020: 09900993
  csrw pmpcfg0, s3     # 80001024: 3a099073
  nop                  # 80001028: 00000013  <-- this fetch must fault
```

`0x99 = 0b10011001` rewrites PMP entry 0 to `A=NAPOT`, `R=1`, `W=0`, `X=0`, `L=1`
(locked, read-only, no execute). Combined with `pmpaddr0 = -1` (`0x3fffffffffff` after
the address mask), entry 0 is a locked NAPOT region covering the whole address space
`[0, 0x1fffffffffffc)`, so the `nop` at `0x80001028` is no longer executable. The
final PMP state confirms entry 0:

```
0: cfg:0x99 addr:0x00003fffffffffff
```

The reference model (NEMU) independently checks PMP on this fetch and rejects it: an instruction fetch (`MEM_TYPE_IFETCH`) at `0x80001028` in M-mode fails the permission check, so NEMU takes an Instruction Access Fault. DiffTest then reports:

```
mstatus different: right = 0x8000040a00567fa0, wrong = 0x8000000a005667a8
   mepc different: right = 0x0000000080001028, wrong = 0x000000008000008e
  mtval different: right = 0x0000000080001028, wrong = 0x0000000000000000
 mcause different: right = 0x0000000000000001, wrong = 0x0000000000000000
```

- **NEMU**: takes an Instruction Access Fault on the `nop` fetch   (`mcause=1`, `mepc=0x80001028`, `mtval=0x80001028`).
- **XiangShan**: commits the `nop` (commit trace shows `commit group [15]:   pc 0x80001028 cmtcnt 1`) and records no exception at all (`mcause=0`, `mtval=0`).
  Its `mepc` still holds the stale `0x8000008e` installed by the earlier `mepc_setup`   block, because no new trap ever overwrote it.

As specified in the RISC-V Privileged Specification:

> In addition to locking the PMP entry, the L bit indicates whether the R/W/X permissions are additionally enforced on M-mode accesses. When the L bit is set, these permissions are enforced for all privilege modes. When the L bit is clear, any M-mode access matching the PMP entry will succeed; the R/W/X permissions apply only to S and U modes.

> Attempting to fetch an instruction from a PMP region that does not have execute permissions raises an instruction access-fault exception.

> PMP violations are always trapped precisely at the processor.

Taken together, once `csrw pmpcfg0, s3` commits entry 0 as `L=1`, `X=0`, the following fetch of the `nop` is an M-mode access to a no-execute PMP region, so XiangShan must raise an Instruction Access Fault on it. It does not.

The root cause is likely a missing pipeline flush on the PMP configuration write. The frontend fetches instructions speculatively, far ahead of the backend, so the `nop` at `0x80001028` may have been fetched and PMP-checked while `pmpcfg0` still held the old permissive value `0x1f` (`X=1`), passing the check. When `csrw pmpcfg0, s3` subsequently commits and updates entry 0 to the locked no-execute configuration, the already-fetched `nop` appears not to be invalidated, so it commits with the stale permission result. One possible source-level explanation is that the `flushPipe` condition in `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala` (around line 996) covers `satp`, debug-trigger changes, FP/vector state transitions, `vstart` and `frm`, but does not include `pmpcfg*` / `pmpaddr*`:

```scala
val flushPipe = resetSatp ||
  triggerFrontendChange || floatStatusOnOff || vectorStatusOnOff ||
  vstartChange || frmChange
```

The DiffTest report is as follows：

[seeds_min.log](https://github.com/user-attachments/files/32241659/seeds_min.log)

### Expected behavior

A write to `pmpcfg*` / `pmpaddr*` (and any other CSR that affects instruction-fetch permissions) must take effect for all instructions following it in program order. After `csrw pmpcfg0, s3` changes entry 0 to a locked no-execute region, the fetch of the following `nop` at `0x80001028` must be denied and XiangShan must raise an Instruction Access Fault (`mcause=1`, `mepc=0x80001028`, `mtval=0x80001028`), exactly as NEMU does.

A likely fix is to add `pmpcfg*` / `pmpaddr*` writes to the `flushPipe` condition in `NewCSR.scala`, so that committing such a write flushes the pipeline and refetches all younger instructions under the new PMP configuration.

### Environment

- Repo
    - XiangShan commit id: `d76ee7f890`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds_min.zip](https://github.com/user-attachments/files/32241667/seeds_min.zip)

### Additional context

_No response_
