### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

A 4 KiB-crossing `HLV.D` whose upper half misses in the G-stage page tables is reported as an ordinary load page fault for the first byte of the instruction, instead of a load guest-page fault for the upper half.

Setup, all in M/HS, standard `HLV.D` (no extra implementation switch):

- VA `0xffc` (page 0) translates through both stages.
- VA `0x1000` (page 1) has a VS-stage leaf whose GPA `0x40000000` has no G-stage mapping.
- An aligned `HLV.WU` of the already-mapped address `0xffc` returns `0x11111111`.
- An aligned `HLV.WU` of the unmapped address `0x1000` is correct: `mcause=21`, `mtval=0x1000`, `mtval2=0x10000000` (`0x40000000 >> 2`).
- `HLV.D` at the same boundary is wrong: `mcause=13`, `mtval=0xffc`. The program stops at label `c13_lo`.

```
Core 0: HIT BAD TRAP at pc = 0x8000032c
Core-0 instrCnt = 8,319, cycleCnt = 7,009
Seed=0
```

Difftest does not print a register mismatch. The failing `mcause` / `mtval` / `mtval2` are fed into NEMU by guided execution, so both sides commit the same PC. The oracle is the program's own `HIT BAD TRAP`, not a difftest `different` line. `--no-diff` and `--diff` stop at the same PC. Seed 0 and the original seed 1 runs match.

### Expected behavior

Load guest-page fault for the upper half: `mcause=21`, `mtval=0x1000`, `mtval2=0x10000000`. The program then takes the pass path and prints `HIT GOOD TRAP`.

The hypervisor otherwise fills the already-valid lower page and never sees the missing guest-physical page. Nested virtualization also loses `mtval2`, so the second-stage table cannot be repaired.

### Environment

- Published Hunt Verilator emu, not rebuilt for this report. Compiled 2026-09-24 02:43:15. emu sha256 `9f402402f5f2e8d15d1ef730650c9437d02a712940f23409b4454b991bfb7c55`.
- Workload toolchain: riscv64-unknown-linux-gnu-gcc (GCC) 12.0.1 20220505 (prerelease), `-march=rv64gcv -mabi=lp64d`.
- Image `case.bin` sha256 `7a222b2750bfad0307c7a165c71f15a5a9c70969953936beda5936e3b6b3c8b9`.
- Source `hlv_xfault.S` sha256 `4d907faae4c16d2f3c41290aff5e9f402596af37cae85355f38ebd14825177be`.
- Farm evidence: run `run-20260923-163852-a147a6`, finding `bug-13f2ae1f84`, key `rtl-hlv-cross-gpf-as-lpf`.
- Reconfirmed 2026-10-08 on that same published emu with `-s 0`.


### To Reproduce

`link.ld` places `.text.init` at `0x80000000`. `hlv_xfault.S` is self-contained.

```
riscv64-unknown-linux-gnu-gcc -nostdlib -nostartfiles -static -fno-pic -fno-pie -no-pie \
  -mcmodel=medany -march=rv64gcv -mabi=lp64d -Wl,--build-id=none \
  -T link.ld hlv_xfault.S -o case.elf
riscv64-unknown-linux-gnu-objcopy -O binary case.elf case.bin

./build/emu -b 0 -e 0 -s 0 -i case.bin --max-instr=1000000 --no-diff
```

`case.bin` in this folder is the image that produced the log above. A failing run stops at `HIT BAD TRAP at pc = 0x8000032c` and does not print `HIT GOOD TRAP`.

### Additional context

Out of scope, and already matching the reference in the same program: the aligned guest-page fault, delegated `ecall`, and an ordinary VS-stage page fault. This report is only the case "lower half translates, upper half misses in the G stage".

### Possible cause

Reading of `f86f50b834`, not confirmed by editing the RTL:

- `mem/lsqueue/LoadMisalignBuffer.scala` computes `shouldOverwrite` when the upper half takes an exception, and the comment says that exception should replace the fault address. `io.overwriteExpBuf.valid` is tied to `false.B`, so the override never fires.
- `package.scala` lists `loadPageFault` (13) ahead of `loadGuestPageFault` (21). A lower-half page fault therefore beats an upper-half guest-page fault.

The upper half of a cross-page load that is a guest-page fault needs to overwrite the exception buffer with that half's VA/GPA and retire as cause 21.

[xs-rt-017-hlv.zip](https://github.com/user-attachments/files/33190003/xs-rt-017-hlv.zip)
