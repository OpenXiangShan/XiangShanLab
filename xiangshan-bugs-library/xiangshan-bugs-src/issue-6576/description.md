### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A vector strided load (`vlse32.v`) executed in U-mode hits a PMP region without read permission and correctly raises a
**load access fault**: `mcause = 5`, `mepc` at the `vlse32.v`, privilege transition to M — all agree with the reference
model. But XiangShan **does not write `mtval`**. The register keeps whatever it held before the trap; the reference model
(NEMU, and Spike) writes the faulting virtual address.

The privileged spec (§3.1.16) says that on a trap `mtval` "is either set to zero or written with exception-specific
information", and that the platform "will specify which exceptions must set mtval informatively and which may
unconditionally set it to zero". Writing zero would therefore be a legal platform choice; leaving the register
**untouched** is not. XiangShan does write `mtval` for scalar load access faults and for some vector faults (see #6482,
where the first vector fault does set it), so this is a dropped update on a specific path, not a hardwired-zero choice.

Evidence that the register is *unwritten* rather than *zeroed*: the value read back is the emulator build's reset content
and differs from build to build, while NEMU always reads the faulting address:

| build (stock `make emu`, DefaultConfig) | XiangShan `mtval` | NEMU `mtval` |
|---|---|---|
| `e43c569f8` | `0x5e232bafe8fed59d` | faulting address |
| `37ce1b50b` | `0xffffffffffffffff` | faulting address |
| `ede751b7c` (latest) | `0xffffffffffffffff` | faulting address |

Difftest output (attached `run_difftest_ede751b7c.log`):

```
[31] exception pc 000000008000016c inst 0bd96187 cause 0000000000000005 <--
  mtval different at pc = ..., right = 0x0000000080002000, wrong = 0xffffffffffffffff
```

`right` is the address of the load's first element (`target` in the reproducer); `mcause`, `mepc`, `mstatus` and all
GPRs match — `mtval` is the only differing field. Deterministic across runs.

### Expected behavior

After the load access fault, `mtval` holds the faulting virtual address of the `vlse32.v` (`0x80002000` here), or — if the
platform decided so — zero. In either case it must be written by the trap, as it is for scalar load access faults.

### Environment

- Hardware
  - CPU: Intel(R) Xeon(R) 6530P
  - Memory (GB): 498
  - Storage (GB): 1742
- Software
  - Operating system: Ubuntu 26.04 LTS
  - gcc version: gcc (conda-forge gcc 13.4.0-19) 13.4.0
  - clang version: not used
  - java version: openjdk version "20.0.2-internal" 2023-07-18
  - mill version: Mill Build Tool version 0.12.17
- Repo
  - XiangShan commit id: `ede751b7c5478ab4963340d8f193f38ca784982d`
  - NEMU commit id (if difftest failed with NEMU): `513786c4c597dd628c6126144937372d6f7fc92e`
- Build & Run
  - Build command: `make sim-verilog DISABLE_PERF=1 -j32 && make -C ./difftest emu-mk NUM_CORES=1 RTL_SUFFIX=sv EMU_TRACE= && make -C ./difftest emu NUM_CORES=1 RTL_SUFFIX=sv EMU_TRACE= OBJCACHE= -j32`
  - Run command (if applicable): `./build/emu -i mtval_vlse_u.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 20000`
  - Workload (binary and source code) attached: `xs_v3_mtval_vlse_attachments.tar.gz`

### To Reproduce

`mtval_vlse_u.S` (attached) is a plain riscv-tests **env-p** program: standard `RVTEST_CODE_BEGIN` reset sequence, then

```asm
        li      t0, MSTATUS_VS
        csrs    mstatus, t0             # enable the vector unit
        li      t0, -1
        csrw    pmpaddr0, t0            # pmp0: NAPOT over the whole address space,
        li      t0, 0x1c
        csrw    pmpcfg0, t0             #   X only -> in U-mode fetch is allowed, loads/stores fault
        la      s2, target
        li      t4, 4
        li      t0, MSTATUS_MPP
        csrc    mstatus, t0             # MPP = U
        la      t0, umode
        csrw    mepc, t0
        mret
umode:
        vsetivli t0, 4, e32, m1, tu, mu
        vlse32.v v3, (s2), t4           # load access fault (5); mtval must be s2
        ...                             # mtvec_handler records mcause/mtval, re-opens PMP, mepc += 4, mret
        bne     s6, s2, fail            # s6 = recorded mtval  <-- XiangShan takes this branch
        RVTEST_PASS
```

Build and run:

```
riscv64-unknown-elf-gcc -march=rv64gcv -mabi=lp64 -static -mcmodel=medany -nostdlib -nostartfiles \
    -I env -I env/p -T env/link.ld mtval_vlse_u.S -o mtval_vlse_u.elf
./build/emu -i mtval_vlse_u.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 20000
```

Difftest aborts at the `vlse32.v` trap with the `mtval` mismatch above. Without `--diff` the program takes the `fail`
branch (XiangShan's own `mtval` comparison fails), on a correct core it reaches `RVTEST_PASS`.

[xs_v3_mtval_vlse_attachments.tar.gz](https://github.com/user-attachments/files/32270557/xs_v3_mtval_vlse_attachments.tar.gz)

### Additional context

## Relation to existing reports (why this is filed separately)

| | #5943 | #6399 | #6482 | **this report** |
|---|---|---|---|---|
| branch | kunminghu-v2 | kunminghu-v2 | kunminghu-v2 | **kunminghu-v3** |
| instruction that faults | `vsse16.v` (strided **store**) | `vsse32.v` (**store**), then scalar `sb` | RVV load/store, then a 2nd RVV or scalar access of the same direction | `vlse32.v` (strided **load**) |
| privilege / fault | store access fault | store access fault (M) | access fault | **U-mode**, PMP load access fault |
| number of faults involved | one | two (vector, then scalar) | two (consecutive) | **one** — the first fault of the program |
| symptom | `mtval` = address of a *later* element (0x31 instead of 0x19) | `mtval = 0` at the vector fault, and `0` again at the following scalar fault | second fault keeps the **first fault's** `mtval` (stale) | `mtval` **never written**: reads back the reset content, which differs per build |
| what it shows about the mechanism | wrong element selected in the store exception merge | a vector store fault leaves a wrong value that also poisons the next scalar fault | the update is skipped on the second fault; the first one *was* written | the update is skipped on a **single, first** vector **load** fault — no earlier fault is needed |
