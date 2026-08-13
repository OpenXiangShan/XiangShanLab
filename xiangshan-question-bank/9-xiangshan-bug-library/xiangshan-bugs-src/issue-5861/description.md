### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

For a `cross16B && !cross4KPage` store, the current `staNukeQueryReq` matching can miss a younger load that overlaps the second 16B half of the store.

The current address match mechanism already treats this kind of store as spanning two adjacent 16B regions. In other words, a younger load can be considered address-matched if it falls in either the first 16B region of the store or the next 16B region.

However, the `staNukeQueryReq` mask is encoded relative to the low 16B-aligned part of the store, and the current mask match mechanism still compares the store mask and the load mask directly.

As a result, the address match may succeed, but the mask match can still fail for the second-half overlap, causing `staNukeQueryReq` to miss a real dependency.

### Expected behavior

For a `cross16B && !cross4KPage` store, `staNukeQueryReq` matching should correctly detect overlap with younger loads in both adjacent 16B regions. When address matching hits the second 16B half, mask matching should use the corresponding second-half semantics as well.

### Environment

[bug-report.tar.gz](https://github.com/user-attachments/files/27081325/bug-report.tar.gz)

### To Reproduce

`bug-report.tar.gz` includes:
- workload binary: `test.bin`
- run logs: `stdout.log`, `stderr.log`
- disassembly: `disasm`
- waveform: `lightsss-wave` & `nuke.gtkw`

### Additional context

`LoadQueueRAW` has a similar limitation: from `storeIn`, it only receives a single store address and mask, without extra information indicating that the store spans two adjacent 16B regions or how the mask should be interpreted for the second 16B half. As a result, it also cannot correctly represent the second-half overlap of a `cross16B && !cross4KPage` store.
