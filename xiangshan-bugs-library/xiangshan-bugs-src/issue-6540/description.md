### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

This is the **S-mode / page-fault / `stval`** analog of #6399 and #6482 (which are
**M-mode / access-fault / `mtval`**). After a vector memory instruction takes a **page
fault**, a following same-direction memory instruction that **also page-faults** does not
update `stval` — the CSR retains the *vector* instruction's faulting address instead of the
current instruction's.

The fault itself is detected correctly (right instruction, right cause); only the recorded
faulting virtual address in `stval` is wrong.

Minimal observed case (attached stock `5d39341327` run, `id_0_1043.elf`):

```
80002688: ... (setup)
800026d8: vse32.v  v26,(t1)     ; t1 = 0x10020 (VA of symbol d_0_0) -> STORE page-faults here
...
800026f4: addi     t3,t3,2      ; t3 = 0x60112
800026f6: fsd      fs10,-24(t3) ; scalar STORE, target = t3-24 = 0x600fa -> STORE page-faults here
```

Difftest (only `stval` differs; `scause`, `sepc`, `mstatus` and all GPRs match):

```
scause = 0x0f (store page fault)   sepc = 0x800026f6 (the fsd)   <-- both models agree
stval different at pc = ...:  right (NEMU) 0x600fa   wrong (XS) 0x10020
```

- **`0x600fa`** is the correct faulting address of the `fsd` (`t3 - 24`). NEMU reports it.
- **`0x10020`** is the target address of the **earlier vector store** `vse32.v v26,(t1)`
  (`t1 = 0x10020`, symbol `d_0_0`), which page-faulted before the `fsd`. XiangShan leaves
  `stval` at this stale vector-fault address.

## Systematic

The pattern reproduces deterministically for both fault directions:
- `scause = 0x0f` (store page fault) and `scause = 0x0d` (load page fault).
- The stale `stval` value is the target address of a **vector** memory instruction (`vse32.v`,
  `vle8.v`, `vluxei16.v`, `vsuxei16.v`, `vsse8.v`, `vlse8.v`, …), and the following faulting
  instruction may be scalar (`fsd`/`lh`/`lw`) or vector — matching the #6399/#6482
  "vector → {vector, scalar}, same direction" shape.

### Expected behavior

When the `fsd` at `0x800026f6` takes a store page fault, `stval` must be updated with the
current faulting virtual address (`0x600fa`), matching NEMU and the RISC-V spec — not left at
the stale `0x10020` from the earlier vector store's page fault.

### Environment

- Software
  - Operating system: Linux
  - verilator version: Verilator 5.028
- Repo
  - XiangShan commit id: `5d39341327` (kunminghu-v2; **reproduced on this commit**, stock
    `make emu`, unmodified difftest — same tip as #6482)
  - NEMU commit id (difftest reference): bundled `ready-to-run/riscv64-nemu-interpreter-so`
- Build & Run
  - Build command: `make emu` for `SimTop`, rv64gcv, VLEN=128
  - Run command: `./build/emu -i id_0_1043.elf -C 20000 --diff ready-to-run/riscv64-nemu-interpreter-so`

### To Reproduce

1. Build a difftest emu of `SimTop` at kunminghu-v2 `5d39341327` (rv64gcv, VLEN=128), stock
   `make emu`.
2. Run the attached workload:
   ```
   ./build/emu -i id_0_1043.elf -C 20000 --diff ready-to-run/riscv64-nemu-interpreter-so
   ```
3. Observe: `stval different ... right = 0x600fa, wrong = 0x10020` at the `fsd` store page
   fault (`sepc = 0x800026f6`). `scause`/`sepc`/GPRs all match; only `stval` is wrong.
   Deterministic (byte-identical across repeated runs).

Attachments:
- `id_0_1043.elf` — reproducer binary
- `id_0_1043.disasm` — disassembly (`vse32.v` at `0x800026d8`, `fsd` at `0x800026f6`)
- `stock_repro_5d393413.log` — stock-emu run showing the `stval` divergence

The trigger is a vector store page fault followed by a same-direction store page fault, in a
bare-metal RV64 S-mode/paged test.

[xs_v2_stale_stval_attachments.tar.gz](https://github.com/user-attachments/files/32037855/xs_v2_stale_stval_attachments.tar.gz)

### Additional context

_No response_
