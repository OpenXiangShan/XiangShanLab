### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

Flushing an integer DIV during iteration leaves the divider idle with its clock gate still open.

When the divider accepts a DIV, `ExeUnit` sets `uncer_en_reg` to keep its clock enabled. Normally, the divider returns a result and the output handshake clears that bit. A flush discards the DIV and takes the divider straight back to idle without producing a result. The handshake that would clear the bit never happens for that DIV, so the clock stays enabled even when no new request arrives.

### Expected behavior

After a flush returns the divider to idle, the clock gate should close if there is no new request.


### Environment

- Hardware
  - CPU: AMD EPYC 7543
  - Memory (GB): 1,082
  - Storage (GB): 23,042 (build filesystem capacity)
- Software
  - Operating system: Ubuntu 22.04.4 LTS, x86-64
  - gcc version: 11.4.0
  - clang version: 14.0.0
  - java version: OpenJDK 11.0.32.1
  - mill version: 0.12.3
  - Verilator version: 5.021
- Repo
  - XiangShan commit id: `7b16188f7f18a97be69c9b1a2dae8a446eec3b47`
  - NEMU commit id: N/A (RTL clock-gating observation)
  - SPIKE commit id: N/A
- Build & Run
  - Build command (base configuration): `make emu CONFIG=KunminghuV2Config NUM_CORES=1`. The recorded waveforms used an FST-enabled build.
  - Run command (normal control, from the extracted ZIP): `/path/to/emu -i control/normal_gap.bin --no-diff -C 12000 --dump-wave --wave-path control.fst`



### To Reproduce

1. In a clock-gating-enabled RTL simulation, compare a DIV that completes normally with an in-flight DIV that is flushed during iteration. Leave the divider idle afterward.
2. To verify the recorded runs, extract `div-clock-gate-evidence.zip` and run `python3 check_traces.py`. It checks the gate after the divider returns to idle in both traces.
3. To inspect the signals in GTKWave, open `flushed.vcd` at 8346 and `normal.vcd` at 8313. The ZIP also includes the normal control workload (`normal_gap.S`, `normal_gap.bin`) and a performance-log excerpt for each run.


### Additional context

In the flushed trace, `kill_r` rises at 8346. From 8347 through the last sample at 9201, the divider is idle, `io_in_valid` and `io_out_valid` are both low, and `ClockGate_2.EN` stays high. In the normal trace, the divider returns to idle at 8314 and the gate closes at 8315. [Gate control](https://github.com/OpenXiangShan/XiangShan/blob/7b16188f7f18a97be69c9b1a2dae8a446eec3b47/src/main/scala/xiangshan/backend/exu/ExeUnit.scala#L102-L123) and [divider state transitions](https://github.com/OpenXiangShan/XiangShan/blob/7b16188f7f18a97be69c9b1a2dae8a446eec3b47/src/main/scala/xiangshan/backend/fu/SRT16Divider.scala#L90-L105).

Verified on the v2 commit above. Latest default-branch reproduction is pending. Prepared with OpenAI Codex (GPT-6).

[div-clock-gate-evidence.zip](https://github.com/user-attachments/files/35066206/div-clock-gate-evidence.zip)
