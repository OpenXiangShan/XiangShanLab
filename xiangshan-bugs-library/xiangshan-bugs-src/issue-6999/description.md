### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

On `kunminghu-v3`, the DUT stops committing for 15000 cycles after two illegal FP traps. The trigger is `sstatus.FS=0`, which makes both `fcvt.lu.s` instructions illegal. The first trap is handled normally. After the second trap handler returns, the DUT commits `csrr zero,scause` but sticks on `csrr zero,mtval`.

The seven-instruction test body:

```
csrwi sstatus, 0
add   x0, x0, x0
fcvt.lu.s x0, f21, rup
lw    x0, 0(x30)
fcvt.lu.s x0, f21, rup
csrr  x0, scause
csrr  x0, mtval
```

Disassembly:

```
80001100: 10005073  csrwi sstatus,0
80001104: 00000033  add   zero,zero,zero
80001108: c03ab053  fcvt.lu.s zero,fs5,rup
8000110c: 000f2003  lw    zero,0(t5)
80001110: c03ab053  fcvt.lu.s zero,fs5,rup
80001114: 14202073  csrr  zero,scause
80001118: 34302073  csrr  zero,mtval
```

Execution:

1. `csrwi sstatus,0` clears `FS`.
2. `fcvt.lu.s` at `0x80001108` traps as illegal. The handler advances `mepc` from `0x80001108` to `0x8000110c` and `mret`s.
3. `lw` at `0x8000110c` commits.
4. `fcvt.lu.s` at `0x80001110` traps again. The handler advances `mepc` from `0x80001110` to `0x80001114` and `mret`s.
5. `csrr zero,scause` at `0x80001114` commits.
6. `csrr zero,mtval` at `0x80001118` never commits.

The DUT reports:

```
No instruction of core 0 commits for 15000 cycles, maybe get stuck
Let REF run one more instruction.
[CROB-STUCK] headPC=0000080001118
Core 0: ABORT at pc = 0x50f583752669
```

`headPC=0x80001118` is the `csrr zero,mtval` instruction.

After REF runs one more instruction, DiffTest reports:

```
   mode different at pc = 0x50f583752669, right = 0x0000000000000003, wrong = 0x0000000000000001
mstatus different at pc = 0x50f583752669, right = 0x0000040a00300800, wrong = 0x0000000a00300080
   mepc different at pc = 0x50f583752669, right = 0x0000000080001118, wrong = 0x0000000080001114
  mtval different at pc = 0x50f583752669, right = 0x0000000034302073, wrong = 0x00000000c03ab053
```

These CSR differences are a consequence of the stall. The DUT is still holding the state from the second illegal FP trap, while REF has advanced to the next instruction.

Control experiments:

- Replacing `add x0,x0,x0` with `nop` or `addi x0,x0,0`: `HIT GOOD TRAP`.
- Removing `lw`: `HIT GOOD TRAP`.
- Removing either `csrr zero,scause` or `csrr zero,mtval`: `HIT GOOD TRAP`.
- Replacing `add x0,x0,x0` with `div`, `divu`, `divuw`, `sub`, `and`, `or`, `slt`, `xor`, or `sll`: still stalls.
- Replacing the second `fcvt.lu.s` with `fcvt.s.w f21,x16`: still stalls.

The DiffTest report is as follows：

[run.log](https://github.com/user-attachments/files/35011172/run.log)

### Expected behavior

Both illegal FP traps should be handled normally. After the second handler returns, `csrr zero,scause` and `csrr zero,mtval` should retire, and the DUT should continue without a 15000-cycle no-commit stall or a stuck CROB. The trap CSRs should reflect the current trap, not stay frozen at the previous trap entry.

### Environment

- Repo
    - XiangShan commit id: `1e05481a4c`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seed_min.zip](https://github.com/user-attachments/files/35011144/seed_min.zip)

### Additional context

_No response_
