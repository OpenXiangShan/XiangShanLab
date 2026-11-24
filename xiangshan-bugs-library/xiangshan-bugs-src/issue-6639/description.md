### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

The first e64 case initializes `v2` with bytes `c0..cf` and loads FP64 1.5 (`0x3ff8000000000000`) into `ft1`. After `vfmv.s.f v2,ft1` at PC `0x800006cc`, both 64-bit elements are zero:

```asm
fld       ft1, 0(a2)
vl1re64.v v2, (a1)
vsetivli x0, 2, e64, m1, tu, mu
vfmv.s.f v2, ft1
```

Element 0 should become `0x3ff8000000000000`; element 1 should retain `0xcfcecdcccbcac9c8` under `tu`. Instead, both are `0x0000000000000000`. Only the first e64 execution is covered.

### Expected behavior

## Evidence

All three seeds reproduce the mismatch: [seed 0 log](logs/replay-20260926/seed0.log) / [metadata](logs/replay-20260926/seed0.json), [seed 1 log](logs/replay-20260926/seed1.log) / [metadata](logs/replay-20260926/seed1.json), [seed 2 log](logs/replay-20260926/seed2.log) / [metadata](logs/replay-20260926/seed2.json).

<img width="1440" height="1100" alt="Image" src="https://github.com/user-attachments/assets/e327feea-ba3a-45fb-88e0-1fffc3415eb6" />

The raw move correctly produces `0x3ff80000000000003ff8000000000000`. The following merge receives execution-path VL/end zero and zero old-destination data, writes zero to the RF, and the target commits. This does **not** show scalar replication directly overwriting the tail.

<img width="2400" height="2430" alt="Image" src="https://github.com/user-attachments/assets/80e58eed-f70b-45ce-84d7-de2a0cf6e51c" />

Traced replay at the same DUT revision: [full log](waveform/run.log), [metadata](waveform/run.json), [waveform analysis](waveform/analysis.md).

The test requests VL=2; the internal VL zero is a separate observation. The analysis uses actual RF writes and register mapping, not the generic commit `data` field.


### Environment

Tested on Kunminghu-v3 `b9636416b8`, `MinimalConfig`, single core, VLEN=128. This issue was not reproduced in the latest Kunminghu-v2 version.

### To Reproduce

RISC-V Unprivileged ISA **20240411**, §31.16.2, specifies transfer into element 0, with the remaining elements treated as tail: [official PDF, page 375](https://docs.riscv.org/reference/isa/v20240411/_attachments/riscv-unprivileged.pdf#page=375).

<img width="1273" height="1801" alt="Image" src="https://github.com/user-attachments/assets/73963d86-c532-4cb3-afc1-cdd0c60211a4" />

[C test](poc/main.c) · [assembly](poc/probe.S) · [BIN](poc/build/f09-vfmv-s-f-writes-tail-riscv64-xs-light-kunminghu-v3.bin) · [ELF](poc/build/f09-vfmv-s-f-writes-tail-riscv64-xs-light-kunminghu-v3.elf) · [disassembly](poc/build/f09-vfmv-s-f-writes-tail-riscv64-xs-light-kunminghu-v3.txt) · [replay script](replay.sh)

Run `bash replay.sh 0` (then `1` and `2`), with `XS_EMU` and `XS_REF` pointing to the pinned models.


### Additional context

[02-F09-vfmv-s-f-writes-tail.zip](https://github.com/user-attachments/files/32700228/02-F09-vfmv-s-f-writes-tail.zip)
