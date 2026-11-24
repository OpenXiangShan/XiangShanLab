### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

At PC `0x80000718`, the PoC multiplies v4={0x00010203, 5} by v5={7, 0x10000001}, after initializing the destination with recognizable bytes:

```asm
vsetivli x0, 2, e32, m1, tu, mu
vwmul.vv v2, v4, v5
```

Expected v2 is `0x0000000050000005_0000000000070e15`; observed v2 is `0xcfcecdcccbcac9c8_0000000000070e15`. The first product is correct; the second retains its old destination value.

RISC-V Unprivileged ISA **20240411**, section **31.11.12**, defines widening integer multiplication with double-width results: [official PDF, page 342](https://docs.riscv.org/reference/isa/v20240411/_attachments/riscv-unprivileged.pdf#page=342). Both products are active at VL=2; `tu` does not permit leaving the second unchanged.


<img width="1273" height="1801" alt="Image" src="https://github.com/user-attachments/assets/cde0cef1-0681-4fe0-859c-e46eb1f42bd9" />


### Expected behavior

## Evidence

All three difftest runs reproduce this mismatch without a no-commit watchdog: seed **0** [log](logs/replay-20260926/seed0.log) / [metadata](logs/replay-20260926/seed0.json), **1** [log](logs/replay-20260926/seed1.log) / [metadata](logs/replay-20260926/seed1.json), **2** [log](logs/replay-20260926/seed2.log) / [metadata](logs/replay-20260926/seed2.json).


<img width="1440" height="1100" alt="Image" src="https://github.com/user-attachments/assets/aecedb90-3ba4-4826-a85f-3c8db173215e" />

A separate seed-0 FST rebuild of the same DUT source shows correct raw products, then a 32-bit merge width, byte end=8 and active mask=`0x00ff`; the high product is replaced by oldVd before the actual RF write. This trace covers `vwmul.vv` only: [trace log](waveform/run.log) / [capture metadata](waveform/run.json), [signal paths, samples and analysis](waveform/analysis.md).


<img width="2400" height="2130" alt="Image" src="https://github.com/user-attachments/assets/fd4b5089-541a-4190-b27e-dddc2a068b34" />

A single seed-0 no-reference self-check stores the same partial result and completes: [log](logs/no-diff-comparison-20260926/seed0.log), [metadata](logs/no-diff-comparison-20260926/seed0.json), [screenshot](screenshots/no-diff-difference.png). `HIT GOOD TRAP` means completion, not correctness. The later `vfwmul.vv` output has no waveform coverage and is outside this report's scope.

### Environment

Tested on Kunminghu-v3 **`b9636416b8`, `MinimalConfig`**, VLEN=128. This issue was not reproduced in the latest Kunminghu-v2 version.

### To Reproduce

Use [replay.sh](replay.sh) with the included tested [BIN](poc/build/f10-widen-tail-boundary-riscv64-xs-light-kunminghu-v3.bin); also included are the [ELF](poc/build/f10-widen-tail-boundary-riscv64-xs-light-kunminghu-v3.elf), [disassembly](poc/build/f10-widen-tail-boundary-riscv64-xs-light-kunminghu-v3.txt), [C source](poc/main.c) and [assembly](poc/probe.S).

```bash
bash replay.sh 0
bash replay.sh 1
bash replay.sh 2
```

Set `XS_EMU` and `XS_REF` to the pinned binaries; replay checks their hashes.


### Additional context

[03-F10-widen-tail-boundary.zip](https://github.com/user-attachments/files/32700281/03-F10-widen-tail-boundary.zip)
