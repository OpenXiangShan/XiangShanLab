### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

## Reproducer and expected behavior

The PoC loads FP32 `{1,2,3,4}` into v4, then executes:

```asm
vsetivli x0, 4, e32, m1, tu, mu
vfmv.f.s ft3, v4
fsd      ft3, 0(a0)
```

At PC `0x800003cc`, the move should copy element zero and NaN-box it to FLEN=64, giving **`ft3 = 0xffffffff3f800000`**. This follows from RISC-V Unprivileged ISA Version 20240411, [§31.16.2, PDF page 375](https://docs.riscv.org/reference/isa/v20240411/_attachments/riscv-unprivileged.pdf#page=375) and [§21.2, PDF page 139](https://docs.riscv.org/reference/isa/v20240411/_attachments/riscv-unprivileged.pdf#page=139).


<img width="1273" height="1801" alt="Image" src="https://github.com/user-attachments/assets/ea18e6a0-865d-47ee-8f2f-34963530c98b" />


<img width="1273" height="1801" alt="Image" src="https://github.com/user-attachments/assets/d6dc1e8b-f400-4c1b-b9d8-6663a4f99102" />


### Expected behavior

## Observed behavior

Difftest stops at the move with an incorrect ft3 value. Original-emulator seeds 0, 1 and 2 produce `0xb94d8df476a85878`, `0x66946d935958af90` and `0x0a654f9d8691bfb2`, respectively. None triggers a no-commit watchdog.

Logs and command/hash records: [seed 0](logs/replay-20260926/seed0.log) ([JSON](logs/replay-20260926/seed0.json)), [seed 1](logs/replay-20260926/seed1.log) ([JSON](logs/replay-20260926/seed1.json)) and [seed 2](logs/replay-20260926/seed2.log) ([JSON](logs/replay-20260926/seed2.json)).



<img width="1440" height="1100" alt="Image" src="https://github.com/user-attachments/assets/f296d24a-0825-461f-b5d8-3532108b6750" />

The tracing rebuild fails at the same `cycleCnt=8880`, but its seed-0 value is **`0x87a3f4f2982f71b7`**, not the original emulator's **`0xb94d8df476a85878`**. The original [seed-0 log](logs/replay-20260926/seed0.log) and [traced seed-0 log](waveform/run.log) preserve both values; [trace metadata](waveform/run.json) records the capture.



<img width="2400" height="2280" alt="Image" src="https://github.com/user-attachments/assets/e2c0ec44-53e4-420b-8938-80ee5df9fc7d" />

At FST sample 8951, VMove produces the correct boxed value for physical FP register 33. The separate `out.fpWbNext` is enabled, but the connected FP-region interface has write enable zero. The target commits at 8953 with p33 unchanged; the subsequent RAT snapshot maps ft3 to p33. Inspection of **8,959 samples across all seven FP write ports** found no enabled write to p33. Its wrong value was already present at the first sample. The [analysis](waveform/analysis.md) includes the full-history scan summary; the figure shows only one write port. Lost writeback is the supported inference, without fix-and-retest confirmation.

### Environment


On Kunminghu-v3 **`b9636416b8`, MinimalConfig**, `vfmv.f.s ft3,v4` commits an incorrect scalar FP value. Both the low 32-bit payload and upper boxing bits are wrong in all three seeds. The test includes a dependent `fsd`; its completion or a subsequent hang is not established. This issue was not reproduced in the latest Kunminghu-v2 version.

### To Reproduce

Use [replay.sh](replay.sh) with seeds 0, 1 and 2. Files: [binary](poc/build/f13-vfmv-f-s-writeback-stub-riscv64-xs-light-kunminghu-v3.bin), [ELF](poc/build/f13-vfmv-f-s-writeback-stub-riscv64-xs-light-kunminghu-v3.elf), [disassembly](poc/build/f13-vfmv-f-s-writeback-stub-riscv64-xs-light-kunminghu-v3.txt), [C](poc/main.c) and [assembly](poc/probe.S).

### Additional context

[06-F13-vfmv-f-s-writeback-stub.zip](https://github.com/user-attachments/files/32700463/06-F13-vfmv-f-s-writeback-stub.zip)
