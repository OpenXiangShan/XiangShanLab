### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

An Sv39 leaf PTE whose PPN has a bit set above XiangShan's physical address
width (PTE bit 52, PPN bit 42) maps to a physical address that does not exist;
XiangShan normally raises an access fault for it (`ppn_af`). After loads and
stores through the **other** PTEs of the same aligned block of 8 PTEs, however,
a load through that PTE completes without any exception and reads the page at
the PPN with the high bit dropped.

```asm
# VA 0x40000000 + i*0x1000 -> l0[i]
# l0[0..6] = PPN of `tgt`, D A X W R V;  l0[7] = the same leaf with PTE bit 52 set
# (S-mode, Sv39, PMP allows everything)
  li  a4, 0x40000000;  ld t4, 0(a4);  sd t1, 8(a4)
  li  a4, 0x40001000;  ld t4, 0(a4);  sd t1, 8(a4)
  ...                                                # the same for 0x40002000 .. 0x40005000
  li  a4, 0x40007000;  ld t4, 0(a4)                  # must raise a load access fault
```

difftest (left = NEMU, right = XiangShan). Before the load, difftest's L1 TLB
check already reports the truncated PPN:

```
Warning: l1tlb resp test of core 0 failed! vpn = 0x40007
  DUT probe signals:
    vpn = 0x40007, ppn = 0x80006 (aligned = 0x80006)
  REF walk result:
    pte.val = 0x00100000200018cf (v=1, r=1, w=1, x=1, u=0, g=0, a=1, d=1)
    ppn = 0x40000080006, perm = 0x67, level = 0, pf = 0, pbmt = 0, napot = 0
  Mismatch: REF ppn 0x40000080006 != DUT ppn 0x80006 (diff = 0x40000000000)
...
[30] commit pc 000000008000213a inst 00073e83 wen 1 dst 29 data 1122334455667788 idx 079 (0a)
...
   mepc different at pc = ..., right = 0x000000008000213a, wrong = 0x00000000800020ec
  mtval different at pc = ..., right = 0x0000000040007000, wrong = 0x0000000000000000
 mcause different at pc = ..., right = 0x0000000000000005, wrong = 0x0000000000000000
```

The `ld` at `0x8000213a` commits the contents of `tgt`
(`0x1122334455667788`); NEMU and Spike take a load access fault (`mcause` 5,
`mtval` = `0x40007000`). Without difftest the self-checking image ends at its
`fail` label (`0x80002170`) instead of `test_exit`.

The access history matters. The same PTE raises the access fault correctly when
it is the first access after `sfence.vma`, after a load through one other PTE of
the block, after `ld`+`sd` through `l0[0]` only, after `ld` only through
`l0[0..5]`, or after `ld`+`sd`+`lw` through `l0[0..2]` (all clean under
difftest, attached as `xs_ppn_high_seq_{c,e,h}` and `xs_ppn_high_leaf`); it is
skipped after `ld`+`sd` through `l0[0..5]` (this image) and after
`ld`+`sd`+`lw` through `l0[0..5]` (`xs_ppn_high_seq_d`). kunminghu-v2 (`e7bab53e66`) is affected too:
this image, `xs_ppn_high_seq_d` and the reduced original program
(`xs_ppn_high_r1`) show the same mismatch there.

This lets S-mode software reach physical memory that a PTE does not name (an
alias of the address with the high PPN bits removed) instead of faulting.

### Expected behavior

A load access fault (`mcause` 5, `mtval` = `0x40007000`) for every access
through `l0[7]`, whatever was accessed before, as on Spike and NEMU.

### Environment

- Software
  - Operating system: Ubuntu 24.04.5 LTS
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0 (emu build)
  - riscv toolchain: riscv-none-elf-gcc (xPack) 15.2.0 (reproducer build)
  - java version: openjdk 17.0.20.1
  - mill version: 0.12.17
  - verilator version: Verilator 5.052 2026-09-05
- Repo
  - XiangShan commit id: `09453ff8f44e9d2b526e6149c60e1dc3f08c7192` (kunminghu-v3, **the latest default-branch commit**)
  - NEMU commit id: the `ready-to-run` interpreter of that commit (`4cf9983`)
  - difftest submodule: `4c64fe86b17e0564232b52bb3800d9b15c45a3a0`
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig OPT_FAST="-O3 -fno-tree-slp-vectorize"` (g++ does not accept difftest's default `-fno-slp-vectorize`)
  - Run command: `./build/emu -i xs_ppn_high_seq_g.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 1000`

### To Reproduce

1. Build a difftest emu at kunminghu-v3 (`DefaultConfig`).
2. Run the attached image:
   ```
   ./build/emu -i xs_ppn_high_seq_g.bin --diff ready-to-run/riscv64-nemu-interpreter-so -I 1000
   ```
3. Observe the `l1tlb resp test` warnings for vpn `0x40007` and the
   `mcause`/`mepc`/`mtval` mismatch at `instrCnt = 127`.
4. Optionally without a reference model (`--no-diff -I 1000`): the image spins
   in `test_exit` when the load faulted and at `fail` when it did not.

[xs_ppn_high_seq_g.elf.zip](https://github.com/user-attachments/files/33040112/xs_ppn_high_seq_g.elf.zip)

### Additional context

_No response_
