### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

At PC `0x80000790`, `vfadd.vf` uses `ft1=0x000000003f800000` and `v3={1.0,2.0,-3.0,0.5}`. The scalar encodes FP32 1.0 in its low bits, but zero upper bits make it improperly NaN-boxed:

```asm
fld      ft1, 0(a2)  # 0x000000003f800000
vsetivli x0, 4, e32, m1, tu, mu
vfadd.vf v2, v3, ft1
```

Each of the four results should be canonical NaN (`0x7fc00000`). Instead, the result is `{2.0,3.0,-2.0,1.5}`: `v2_low=0x4040000040000000`, `v2_high=0x3fc00000c0000000`. Both expected halves are `0x7fc000007fc00000`.


### Expected behavior

## Evidence

All three seeds reproduce the mismatch: [seed 0 log](logs/replay-20260926/seed0.log) / [metadata](logs/replay-20260926/seed0.json), [seed 1 log](logs/replay-20260926/seed1.log) / [metadata](logs/replay-20260926/seed1.json), [seed 2 log](logs/replay-20260926/seed2.log) / [metadata](logs/replay-20260926/seed2.json).


<img width="1440" height="1100" alt="Image" src="https://github.com/user-attachments/assets/220b5d25-98c0-4c82-bea3-e5c6c7999cb4" />

The selected FP bypass contains `0x000000003f800000`; its low bits become four ordinary `1.0` operands, followed by a finite result, RF write and commit. The unselected FP RF read port is not the operand.


<img width="3680" height="2240" alt="Image" src="https://github.com/user-attachments/assets/b48d78c7-fd06-439d-9bf3-2945c5cc6f9c" />

Traced replay at the same DUT revision: [full log](waveform/run.log), [metadata](waveform/run.json), [waveform analysis](waveform/analysis.md).

The preceding scalar `fadd.s` control correctly returns `0xffffffff7fc00000` with the same input, as recorded in the logs. Only SEW=32 `vfadd.vf` is confirmed; later subcases are excluded.

### Environment

Tested on Kunminghu-v3 `b9636416b8`, `MinimalConfig`, single core, VLEN=128. This issue was not reproduced in the latest Kunminghu-v2 version.


### To Reproduce

RISC-V Unprivileged ISA **20240411**, §31.10, requires checking NaN-boxing when SEW is narrower than FLEN and substituting canonical NaN for an improperly boxed scalar: [official PDF, page 330](https://docs.riscv.org/reference/isa/v20240411/_attachments/riscv-unprivileged.pdf#page=330).


<img width="1273" height="1801" alt="Image" src="https://github.com/user-attachments/assets/88f98e43-fe66-4ad9-b59f-a303f6bebe13" />

[C test](poc/main.c) · [assembly](poc/probe.S) · [BIN](poc/build/f12-vf-no-nanbox-check-riscv64-xs-light-kunminghu-v3.bin) · [ELF](poc/build/f12-vf-no-nanbox-check-riscv64-xs-light-kunminghu-v3.elf) · [disassembly](poc/build/f12-vf-no-nanbox-check-riscv64-xs-light-kunminghu-v3.txt) · [replay script](replay.sh)

Run `bash replay.sh 0` (then `1` and `2`), with `XS_EMU` and `XS_REF` pointing to the pinned models.

### Additional context

Related: [#6528](https://github.com/OpenXiangShan/XiangShan/issues/6528)/[#6532](https://github.com/OpenXiangShan/XiangShan/pull/6532) concern scalar `fmadd.s` NaN-box validation. Here scalar `fadd.s` succeeds while vector `vfadd.vf` fails.

[05-F12-vf-no-nanbox-check.zip](https://github.com/user-attachments/files/32700428/05-F12-vf-no-nanbox-check.zip)
