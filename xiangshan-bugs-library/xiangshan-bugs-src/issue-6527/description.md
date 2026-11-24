### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

The scalar conversion `fcvt.d.w` (signed int32 → float64) sets the **invalid-operation
(`NV`, `fflags[4]`)** accrued exception flag in XiangShan. Per the RISC-V spec an
integer→floating-point conversion can **never** signal invalid, and int32→float64 is
moreover always **exact** (every 32-bit integer is representable in float64), so the
correct result raises **no** flag at all. The reference model (NEMU) leaves `NV` clear;
XiangShan sets it.

Observed (stock `e43c569f8` run):

```
80000340: fcvt.d.w  ft1, s10          ; int32 -> float64 (always exact, never invalid)
...
fcsr different at pc = 0x80000340:  right (NEMU) 0x01 (NX)   wrong (XS) 0x11 (NX | NV)
```

The pre-existing `NX` (0x1) is agreed by both models (from an earlier legitimate rounding
op). The **only** difference is bit 4, **`NV`**, which XiangShan sets at the `fcvt.d.w`
commit and NEMU does not.

Deterministic and build-independent: the divergence is byte-identical on two independently
built stock emus (`0cdabfc7c` and `e43c569f8`).

### Expected behavior

`fcvt.d.w` must not modify `fcsr.fflags` for an exact conversion (and can never set `NV`
under any input). After `fcvt.d.w ft1, s10`, `fcsr.NV` must remain 0, matching NEMU and the
RISC-V spec.

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
  - Run command: `./build/emu -i id_2_1686.elf -C 20000 --diff <nemu-so>`

### To Reproduce

1. Build a difftest emu of `SimTop` at kunminghu-v3 `e43c569f8` (rv64gcv, VLEN=128), stock
   `make emu`.
2. Run the attached workload:
   ```
   ./build/emu -i id_2_1686.elf -C 20000 --diff ready-to-run/riscv64-nemu-interpreter-so
   ```
3. Observe at `pc = 0x80000340`: `fcsr different ... right = 0x1, wrong = 0x11` — XiangShan
   set `NV` on the `fcvt.d.w`; NEMU did not. Deterministic.

Attachments:
- `id_2_1686.elf` — reproducer binary
- `id_2_1686.disasm` — disassembly (see `fcvt.d.w ft1,s10` at `0x80000340`, `fmv.d.x` at `0x80000334`)
- `stock_replay_e43c569f8.log` — stock-emu run at the latest tip showing the `fcsr` `NV` divergence

The workload is a bare-metal RV64 test; not hand-minimized. The trigger is the `fcvt.d.w`
at `0x80000340`.

[xs_v3_fcvt_dw_spurious_nv_attachments.tar.gz](https://github.com/user-attachments/files/31995986/xs_v3_fcvt_dw_spurious_nv_attachments.tar.gz)

### Additional context

_No response_
