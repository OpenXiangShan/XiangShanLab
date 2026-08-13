### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

An atomic memory operation whose address takes a **store/AMO page fault** modifies its **destination register (`rd`)** — it must not. On a precise trap, architectural registers must be preserved so the instruction is restartable.

Reproducer sequence (rd = `x9`, address in `x9`):
```asm
        la    x9, d_1_0            # x9 = &d_1_0
        addi  x9, x9, -24          # x9 = 0x20008  (address with no store permission)
        amoxor.w x9, x13, (x9)     # -> store/AMO page fault (scause=0x0f, stval=0x20008)
```

Both XS and the NEMU reference agree on the trap itself (`scause=0x0f`, `sepc` = the AMO PC `0x2b5c`, `stval=0x20008`). They diverge only on **`x9` after the trap**:
- NEMU (reference): `x9 = 0x20008`  ← the AMO's address operand, **preserved** (correct)
- XS (DUT):         `x9 = 0x0`      ← **clobbered to 0** (wrong)

Because `x9` was the AMO's own address input, a handler that maps the page and returns to re-execute the AMO would find its address operand destroyed → silent wrong execution on trap-resume.

The divergence is first reported by difftest as a mismatch on the integer register (`s1`/`x9`) inside the trap handler's register save/restore — XS's saved slot holds `0`, NEMU's holds `0x20008` — but the underlying cause is the faulting AMO writing `rd`, not a save/restore problem.

Note: this is **not** the self-modifying-code / `fence.i` difftest class; the reproducer performs a data-region atomic and never writes the code section.

### Expected behavior

`amoxor.w x9, x13, (x9)` that takes a store/AMO page fault must raise the fault with **`x9` unchanged** (still `0x20008`), as NEMU does. A trapping instruction must not modify `rd`.

### Environment

- Hardware
    - CPU: Intel(R) Xeon(R) CPU E5-2683 v4 @ 2.10GHz
    - Memory (GB): 503
  - Software
    - Operating system: Ubuntu 24.04.3 LTS
    - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
    - java version: openjdk version "20.0.2-internal" 2023-07-18
    - mill version: 0.12.15
  - Repo
    - XiangShan commit id: `2b5769e8b2` (kunminghu-v3 tip) — also reproduced on `d97789de12`.
    - NEMU commit id (if difftest failed with NEMU): tip-matching prebuilt ref in the `ready-to-run` submodule
  - Build & Run
    - Build command: `make emu CONFIG=DefaultConfig`
    - Run command: `./build/emu -i repro_amo_fault_clobber.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 20000 --dump-commit-trace`

### To Reproduce

Attached `repro_amo_fault_clobber.elf` — a bare-metal RV64GC test under Sv39 paging with the AMO's target page left without store permission. Steps:
1. Build the difftest emu on kunminghu-v3 (`make emu CONFIG=DefaultConfig`), using the NEMU from the same commit's `ready-to-run` submodule.
2. Run: `./build/emu -i repro_amo_fault_clobber.elf --diff .../riscv64-nemu-interpreter-so -C 20000`
3. Observed: difftest aborts on an integer-register mismatch (`x9`): XS `= 0x0`, NEMU `= 0x20008`, after the `amoxor.w` at `sepc=0x2b5c` took a store page fault (`scause=0x0f`, `stval=0x20008`).
4. Expected: `x9 = 0x20008` (unchanged), as NEMU reports.

Deterministic across RTL reset seeds; reproduces on both `2b5769e8b2` and `d97789de12`. Attached `nemu_reftrace_excerpt.txt` shows the trap + the divergence; a full VerilatedVcd waveform (~101 MB, windowed around the AMO fault) was captured and is available on request for RTL inspection of the writeback.

[xs_amo_fault_clobbers_rd.zip](https://github.com/user-attachments/files/30443820/xs_amo_fault_clobbers_rd.zip)

### Additional context

Suspected area: the AMO / LSU store-unit exception path — the faulting AMO's uop appears to still drive its integer writeback (writing `rd`) instead of suppressing it when the access faults.

Possibly related (none covers this exact case — AMO rd not preserved on a store page fault): #3830 (a different faulting-access deadlock); riscv-isa-sim#873 (AMO + store/guest page-fault, reference-model side).

Filing for maintainer debugging assistance — reproducible, deterministic, spans two tips, single-register architectural divergence with a 3-instruction faulting sequence.
