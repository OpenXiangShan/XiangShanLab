### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

## Reproducer and expected behavior

The PoC loads FP32 `{1,2,3,4}` into v3 and zero into v4[0], then executes the target at PC `0x80000410`:

```asm
vsetivli     x0, 4, e32, m1, tu, mu
vfredusum.vs v2, v3, v4
```

If executed correctly, destination element zero is FP32 **10.0 (`0x41200000`)**. The semantics are specified in RISC-V Unprivileged ISA Version 20240411, [§31.14.3, PDF page 365](https://docs.riscv.org/reference/isa/v20240411/_attachments/riscv-unprivileged.pdf#page=365) and [page 366](https://docs.riscv.org/reference/isa/v20240411/_attachments/riscv-unprivileged.pdf#page=366).



<img width="1273" height="1801" alt="Image" src="https://github.com/user-attachments/assets/6f42a154-a9e3-4a97-a9e3-60e47a4ef20e" />



<img width="1273" height="1801" alt="Image" src="https://github.com/user-attachments/assets/caf8e61b-b296-4706-97a2-1197e9deb555" />

### Expected behavior

## Observed behavior

All three runs report no commits for **15,000 cycles**, then exit with the watchdog. This threshold belongs to the test harness; it is **not an ISA execution deadline**. After the watchdog, the harness forcibly advances the reference by one instruction. The resulting v2 difference is diagnostic and **does not mean the DUT committed an incorrect reduction result**.

Logs and command/hash records: [seed 0](logs/replay-20260926/seed0.log) ([JSON](logs/replay-20260926/seed0.json)), [seed 1](logs/replay-20260926/seed1.log) ([JSON](logs/replay-20260926/seed1.json)) and [seed 2](logs/replay-20260926/seed2.log) ([JSON](logs/replay-20260926/seed2.json)).



<img width="1440" height="1100" alt="Image" src="https://github.com/user-attachments/assets/ffbe85fa-6c38-4d98-a289-dc0fed4c21f0" />

## Waveform evidence

The tracing rebuild reproduces the watchdog at `cycleCnt=23239`, after 1,541 committed instructions: [trace log](waveform/run.log), [command/hash record](waveform/run.json). Below, cycles are falling-edge FST sample indices, not DUT cycle counts.



<img width="2400" height="1680" alt="Image" src="https://github.com/user-attachments/assets/9da34e3b-a397-49a7-a282-ab9467498c0e" />

At sample **8298**, the target has `valid=ready=fired=1`, ROB `(flag=1,index=0x29)`, and all 19 IQ-selection bits zero. At 8300, its ROB entry is valid with one unfinished micro-op.



<img width="2400" height="1680" alt="Image" src="https://github.com/user-attachments/assets/846cd240-1a80-46d0-b56f-18540179b4af" />

From **8312 through 23317**, inclusive (**15,006 samples**), the same target occupies the ROB head with one unfinished micro-op and no commits. The observed global commit-block, redirect, flush and exception controls are inactive. An earlier redirect at 8304 concerns a younger ROB entry; the target remains valid. The [analysis](waveform/analysis.md) links samples and extraction summaries; derived rows are labelled.

This supports a routing/execution-path problem. The finite capture proves neither permanent deadlock nor a unique repair.


### Environment

On Kunminghu-v3 **`b9636416b8`, MinimalConfig**, the attached reduction test triggers difftest's no-commit watchdog for seeds 0, 1 and 2. This issue was not reproduced in the latest Kunminghu-v2 version.

### To Reproduce

Run [replay.sh](replay.sh) with seeds 0, 1 and 2. Files: [binary](poc/build/f14-vfred-no-exu-hang-riscv64-xs-light-kunminghu-v3.bin), [ELF](poc/build/f14-vfred-no-exu-hang-riscv64-xs-light-kunminghu-v3.elf), [disassembly](poc/build/f14-vfred-no-exu-hang-riscv64-xs-light-kunminghu-v3.txt), [C](poc/main.c) and [assembly](poc/probe.S). This adds a reduction reproducer within the v3 vector WIP context.


### Additional context

[07-F14-vfred-no-exu-hang.zip](https://github.com/user-attachments/files/32700511/07-F14-vfred-no-exu-hang.zip)
