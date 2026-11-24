### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A saturating vector fixed-point instruction that **saturates** does not set the
`vxsat` saturation flag (`vcsr[0]`). The computed result is correct (the element is
clamped), but `vxsat` is left 0. The reference model (NEMU) sets `vxsat = 1`; XiangShan
leaves it 0.

Minimal observed case (attached stock `e43c569f8` run, `id_2_259.elf`):

```
800002d8: vsetvli t0, zero, e8, m1, tu, mu   ; SEW=8, LMUL=1, vl=16
800002dc: li      t2, 267                    ; low SEW(8) bits = 267 & 0xFF = 11
800002e0: vssubu.vx v12, v25, t2             ; v12[i] = saturate_u8( v25[i] - 11 )
```

At least one element of `v25` is `< 11`, so the unsigned subtract **underflows and
clamps to 0** — a saturation, which per the RVV spec must set `vxsat`.

Difftest (only `vxsat`/`vcsr` differ; the result register `v12`, `vl`, `vtype`, `vxrm`
and everything else match):

```
vxsat different at pc = 0x80000306:  right (NEMU) 0x1   wrong (XS) 0x0
vcsr  different:                     right (NEMU) 0x1   wrong (XS) 0x0
```

Because the **result `v12` does not diverge**, XiangShan clearly produced the clamped
(saturated) value — i.e. it *did* saturate — but failed to set `vxsat`. So this is a
missing-flag bug, not a wrong-result bug.

Deterministic and stock-reproducible: byte-identical `vxsat` divergence across repeated
runs on an unmodified `make emu` build with an unmodified difftest.

## Systematic

The same missing-`vxsat` behavior appears across multiple `vssubu.vx` workloads that
saturate (isolated to `vxsat`/`vcsr` in every case, with matching result and other CSRs),
so it is not a one-off. A fix should cover the vector fixed-point saturating family
generally (`vssubu`/`vsaddu`/`vssub`/`vsadd`/`vnclip*`/`vsmul`, …), whose saturation must
set `vxsat`.

### Expected behavior

When a vector fixed-point instruction saturates, `vxsat` (`vcsr[0]`) must be set to 1,
matching the RVV spec and the reference model. After the `vssubu.vx` above saturates,
`vxsat` must become 1.

### Environment

- Software
  - Operating system: Linux
  - firtool version: 1.144.0
  - verilator version: Verilator 5.028
- Repo
  - XiangShan commit id: `e43c569f8` (kunminghu-v3; **reproduced on this, the latest
    default-branch commit**, stock / non-instrumented emu, unmodified difftest)
  - NEMU commit id (difftest reference): bundled `ready-to-run/riscv64-nemu-interpreter-so`
- Build & Run
  - Build command: `make emu` for `SimTop`, rv64gcv, VLEN=128
  - Run command: `./build/emu -i id_2_259.elf -C 20000 --diff ready-to-run/riscv64-nemu-interpreter-so`

### To Reproduce

1. Build a difftest emu of `SimTop` at kunminghu-v3 `e43c569f8` (rv64gcv, VLEN=128), stock
   `make emu`.
2. Run the attached workload:
   ```
   ./build/emu -i id_2_259.elf -C 20000 --diff ready-to-run/riscv64-nemu-interpreter-so
   ```
3. Observe at `pc = 0x80000306`: `vxsat different ... right = 0x1, wrong = 0x0` (and the same
   for `vcsr`). The `vssubu.vx` at `0x800002e0` saturated; NEMU set `vxsat`, XiangShan did
   not. Deterministic; the result register `v12` matches.

Attachments:
- `id_2_259.elf` — reproducer binary
- `id_2_259.disasm` — disassembly (`vssubu.vx v12,v25,t2` at `0x800002e0`)
- `stock_repro_e43c569f.log` — stock-emu run showing the `vxsat` divergence

The trigger is a saturating `vssubu.vx`; the workload is a bare-metal RV64 test.

[xs_v3_vxsat_attachments.tar.gz](https://github.com/user-attachments/files/32086287/xs_v3_vxsat_attachments.tar.gz)

### Additional context

_No response_
