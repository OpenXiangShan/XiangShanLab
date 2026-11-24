### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

The source contains sixteen 16-bit values `source[i]=(i<<8)|0xab`. At PC `0x8000054c`, the PoC narrows them by shifting right eight bits:

```asm
vl2re64.v v4, (a2)
vsetivli x0, 16, e8, m1, ta, ma
vnsrl.wi v2, v4, 8
```

Expected v2 is `0x0f0e0d0c0b0a0908_0706050403020100` (bytes `00..0f`); observed v2 is `0xffffffffffffffff_ffffffffffffffff`.

RISC-V Unprivileged ISA **20240411**, section **31.11.7**, defines narrowing right shifts: [official PDF, page 337](https://docs.riscv.org/reference/isa/v20240411/_attachments/riscv-unprivileged.pdf#page=337). Here VL=VLMAX=16; every destination byte is active, so tail-agnostic policy cannot justify the all-ones result.


<img width="1273" height="1801" alt="Image" src="https://github.com/user-attachments/assets/a2837c0c-f9ca-4c69-8708-9221af24f52d" />

### Expected behavior

## Evidence

All three difftest runs reproduce the **ta** mismatch without a no-commit watchdog: seed **0** [log](logs/replay-20260926/seed0.log) / [metadata](logs/replay-20260926/seed0.json), **1** [log](logs/replay-20260926/seed1.log) / [metadata](logs/replay-20260926/seed1.json), **2** [log](logs/replay-20260926/seed2.log) / [metadata](logs/replay-20260926/seed2.json).

<img width="1440" height="1100" alt="Image" src="https://github.com/user-attachments/assets/47d69306-639f-4d3f-bf6e-e76aed2d1ec3" />


A separate seed-0 FST rebuild shows both uops computing internal narrow bytes `00..0f`, while the merge selects their normal-shaped outputs; the odd uop receives end=0/activeEn=0 with vta=1 and writes all ones to the physical register subsequently mapped to v2. This waveform covers **ta only**: [trace log](waveform/run.log) / [capture metadata](waveform/run.json), [signal paths, samples and analysis](waveform/analysis.md).


<img width="2400" height="3180" alt="Image" src="https://github.com/user-attachments/assets/1e702e37-e075-4660-8484-25d60a2240c0" />

Both oldVd inputs are repeated `08`; the tu forwarding mechanism remains unverified.

One seed-0 no-reference self-check confirms ta all-ones and tu=`0x07ab06ab05ab04ab_03ab02ab01ab00ab`, also incorrect: [log](logs/no-diff-comparison-20260926/seed0.log), [metadata](logs/no-diff-comparison-20260926/seed0.json) and [screenshot](screenshots/no-diff-difference.png). `HIT GOOD TRAP` means completion, not correctness. Tu has only this single self-check.

### Environment


Tested on Kunminghu-v3 **`b9636416b8`, `MinimalConfig`**, VLEN=128. This issue was not reproduced in the latest Kunminghu-v2 version.

### To Reproduce

Use [replay.sh](replay.sh) with the included tested [BIN](poc/build/f11-vnsrl-odd-uop-tail-riscv64-xs-light-kunminghu-v3.bin); also included are the [ELF](poc/build/f11-vnsrl-odd-uop-tail-riscv64-xs-light-kunminghu-v3.elf), [disassembly](poc/build/f11-vnsrl-odd-uop-tail-riscv64-xs-light-kunminghu-v3.txt), [C source](poc/main.c) and [assembly](poc/probe.S).

```bash
bash replay.sh 0
bash replay.sh 1
bash replay.sh 2
```

Set `XS_EMU` and `XS_REF` to the pinned binaries; replay checks their hashes.

### Additional context

## Related reports

[#6598](https://github.com/OpenXiangShan/XiangShan/issues/6598) lists `vnsra/vnsrl` stalls; this case completes with corrupted active bytes.

[04-F11-vnsrl-odd-uop-tail.zip](https://github.com/user-attachments/files/32700405/04-F11-vnsrl-odd-uop-tail.zip)
