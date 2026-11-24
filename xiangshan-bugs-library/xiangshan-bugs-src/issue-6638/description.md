### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


With SEW=8, LMUL=1 and VL=16, `vmv.v.x v2,t1` at PC `0x8000050c` produces an all-zero vector after `t1` is explicitly loaded with `0x5a`:

```asm
vsetivli x0, 16, e8, m1, ta, ma
li       t1, 0x5a
vmv.v.x  v2, t1
```

Both 64-bit halves of `v2` should be `0x5a5a5a5a5a5a5a5a`; both are `0x0000000000000000`. All 16 bytes are active, so tail policy cannot explain this result.


### Expected behavior

## Evidence

All three seeds reproduce the mismatch: [seed 0 log](logs/replay-20260926/seed0.log) / [metadata](logs/replay-20260926/seed0.json), [seed 1 log](logs/replay-20260926/seed1.log) / [metadata](logs/replay-20260926/seed1.json), [seed 2 log](logs/replay-20260926/seed2.log) / [metadata](logs/replay-20260926/seed2.json).


<img width="1440" height="1100" alt="Image" src="https://github.com/user-attachments/assets/7ead5aae-f6c3-4613-9d6b-3d32fea25f83" />

For the matched instruction, the waveform shows the scalar producer writing `0x5a`, but the selected vector operand and RF write are zero; the target commits.


<img width="3520" height="1920" alt="Image" src="https://github.com/user-attachments/assets/a64f0468-28f1-4bb1-ba07-71ad387ffcec" />

Traced replay at the same DUT revision: [full log](waveform/run.log), [metadata](waveform/run.json), [waveform analysis](waveform/analysis.md).

A seed-0 no-reference self-check also stores incorrect results and completes ([log](logs/no-diff-comparison-20260926/seed0.log), [metadata](logs/no-diff-comparison-20260926/seed0.json), [screenshot](screenshots/no-diff-difference.png)). `GOOD TRAP` means completion, not correctness. Its later `vadd.vx`/`vmv.s.x` cases are outside the three-seed coverage.

### Environment


Tested on Kunminghu-v3 `b9636416b8`, `MinimalConfig`, single core, VLEN=128. This issue was not reproduced in the latest Kunminghu-v2 version.

### To Reproduce

## Specification and replay

RISC-V Unprivileged ISA **20240411**, §31.11.16, specifies the scalar broadcast: [official PDF, page 344](https://docs.riscv.org/reference/isa/v20240411/_attachments/riscv-unprivileged.pdf#page=344).

<img width="1273" height="1801" alt="Image" src="https://github.com/user-attachments/assets/fe139b92-ddea-453a-97e0-624bf2db8569" />

[C test](poc/main.c) · [assembly](poc/probe.S) · [BIN](poc/build/f05-vx-scalar-read-as-zero-riscv64-xs-light-kunminghu-v3.bin) · [ELF](poc/build/f05-vx-scalar-read-as-zero-riscv64-xs-light-kunminghu-v3.elf) · [disassembly](poc/build/f05-vx-scalar-read-as-zero-riscv64-xs-light-kunminghu-v3.txt) · [replay script](replay.sh)

Run `bash replay.sh 0` (then `1` and `2`), with `XS_EMU` and `XS_REF` pointing to the pinned models.

Related: [#6543](https://github.com/OpenXiangShan/XiangShan/issues/6543) reports `vdiv.vx` failing to retire with an unwritten zero divisor; this case produces a nonzero scalar and commits a wrong result. Shared operand-read causes remain possible.

### Additional context

[01-F05-vx-scalar-read-as-zero.zip](https://github.com/user-attachments/files/32700202/01-F05-vx-scalar-read-as-zero.zip)
