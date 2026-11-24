### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A single-precision floating-point operation that reads a source register which is **not
correctly NaN-boxed** (upper 32 bits ≠ all-ones) uses the register's raw low 32 bits as the
operand, instead of substituting the **canonical single NaN** as the RISC-V spec requires.

Minimal observed case (stock `e43c569f8` run):

```
800002c0: fmadd.s  fs1, ft1, ft2, fs8, rup     ; fs1 = ft1*ft2 + fs8  (single precision)

source registers at this point (from the difftest FP dump):
  ft1 = 0xffffffff_aaaaaaaa   (properly NaN-boxed)
  ft2 = 0xffffffff_e1267b16   (properly NaN-boxed)
  fs8 = 0x20fedb64_4c0bf0f2   (NOT NaN-boxed -- upper 32 bits are not all-ones)
...
fs1 different at pc = 0x800002e6:
   right (NEMU) 0xffffffff_7fc00000     ; NaN-boxed canonical single NaN
   wrong (XS)   0xffffffff_4cb4f532     ; finite -> XS used fs8's raw low bits 0x4c0bf0f2
```

`fs8` was loaded earlier by a double `fld`, so its upper bits are arbitrary (here
`0x20fedb64`). Because `fs8` is not correctly boxed, `fmadd.s` must treat it as the
canonical NaN `0x7fc00000`; a NaN addend forces the fused-multiply-add result to NaN,
independent of `ft1*ft2`. NEMU produces exactly the NaN-boxed canonical NaN. XiangShan
instead consumed `fs8`'s raw low 32 bits (`0x4c0bf0f2`) and produced a finite result —
proving the read-side NaN-boxing check is not applied.

Deterministic and build-independent: byte-identical divergence on two independently built
stock emus (`0cdabfc7c` and `e43c569f8`).

### Expected behavior

For any single-precision operation, a source register whose upper 32 bits are not all-ones
must be read as the canonical single NaN (`0x7fc00000`). For `fmadd.s fs1, ft1, ft2, fs8`
with `fs8 = 0x20fedb644c0bf0f2`, the result must be the NaN-boxed canonical NaN
(`0xffffffff7fc00000`), matching NEMU and the RISC-V spec.

### Environment

- Software
  - Operating system: Linux
  - firtool version: 1.144.0
  - verilator version: Verilator 5.028 2024-08-21
- Repo
  - XiangShan commit id: `e43c569f8` (kunminghu-v3; **reproduced on this, the latest
    default-branch commit**, stock / non-instrumented emu, unmodified difftest). Also
    reproduces identically at `0cdabfc7c`.
  - NEMU commit id (difftest reference): shipped `ready-to-run` build
  - difftest submodule: shipped pinned commit
- Build & Run
  - Build command: `make emu` for `SimTop`, rv64gcv, VLEN=128
  - Run command: `./build/emu -i id_5_1672.elf -C 20000 --diff <nemu-so>`

### To Reproduce

1. Build a difftest emu of `SimTop` at kunminghu-v3 `e43c569f8` (rv64gcv, VLEN=128), stock
   `make emu`.
2. Run the attached workload:
   ```
   ./build/emu -i id_5_1672.elf -C 20000 --diff ready-to-run/riscv64-nemu-interpreter-so
   ```
3. Observe: `fs1 different ... right = 0xffffffff7fc00000, wrong = 0xffffffff4cb4f532`. The
   producer is `fmadd.s fs1,ft1,ft2,fs8` at `0x800002c0` reading the un-boxed `fs8`.
   Deterministic.

Attachments:
- `id_5_1672.elf` — reproducer binary
- `id_5_1672.disasm` — disassembly (see `fmadd.s fs1,ft1,ft2,fs8,rup` at `0x800002c0`)
- `stock_replay_e43c569f8.log` — stock-emu run at the latest tip showing the `fs1` divergence
  and the FP source-register dump

The workload is a bare-metal RV64 test; not hand-minimized. The trigger is `fmadd.s` at
`0x800002c0` reading the un-NaN-boxed `fs8`.

[xs_v3_nanbox_readside_fmadd_attachments.tar.gz](https://github.com/user-attachments/files/31996075/xs_v3_nanbox_readside_fmadd_attachments.tar.gz)

### Additional context

_No response_
