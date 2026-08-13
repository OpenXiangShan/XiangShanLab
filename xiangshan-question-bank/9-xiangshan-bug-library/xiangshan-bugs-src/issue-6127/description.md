### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

Location: ```HardwarePerfMonitor.scala```, Lines 52-55

```HPerfCounter``` uses software-writable ```mhpmevent``` CSR fields as a dynamic index into the event-pool Vec. The index width (10 bits, range 0..1023) is far larger than the Vec capacity (```numPCnt```, < 20 in all real configurations), and there is no bounds checking. When software writes an event selector ≥ ```numPCnt```, the hardware does not ignore it or raise any error. Instead, per Chisel dynamic-index semantics, it reads a wrong event slot (equivalent to ```idx % numPCnt``` for a power-of-two Vec), so the value read back from mhpmcounter reflects an unintended event.

```io.events_sets``` is ```Vec(numPCnt, new PerfEvent). io.hpm_event``` is sliced into four 10-bit fields, each used directly as an index with no clamp/mask.

End-to-end path (software-controllable)
1. In ```MachineLevel.scala```, ```MhpmeventBundle``` defines ```EVENT0..EVENT3``` as plain RW fields (RW(9,0) / RW(19,10) / RW(29,20) / RW(39,30)), with no WARL / legalValues constraint. Software can write any 10-bit value.
2. ```NewCSR.scala:1286 / CSR.scala:762```: mhpmevents.slice(24,29) rdata is wired through HPerfMonitor directly to HPerfCounter.io.hpm_event. The path is closed with no intermediate clamp.

### Expected behavior

When software writes an event selector that is out of range (≥ numPCnt) into an mhpmevent EVENT field, the hardware should behave in a well-defined, documented way rather than silently aliasing to an unrelated slot.

### Environment

detected by a static analysis tool.

### To Reproduce

detected by a static analysis tool.

### Additional context

_No response_
