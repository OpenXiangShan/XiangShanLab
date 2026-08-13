### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

Buggy decode/commit interaction around `wfflags` and `dirtyFs`.

The instruction `vfsgnj.vv` is currently included in `wfflagsInsts`:

<img width="1540" height="524" alt="Image" src="https://github.com/user-attachments/assets/bcde456f-f742-4539-aa51-a50e0bc69671" />

but in unprivileged spec

<img width="1032" height="166" alt="Image" src="https://github.com/user-attachments/assets/3117fd6a-f321-4f36-82e8-bf723e1ee4cd" />

This causes `decodedInst.wfflags` to be asserted for `vfsgnj.vv`.

Later, ROB commit treats any instruction with `wflags` as dirtying the scalar floating-point state:

`
robCommitEntry.dirtyFs := robEntry.fpWen || robEntry.wflags

So even though `vfsgnj.vv` writes only a vector register and does not write scalar FP registers or `fflags`, the DUT marks `mstatus.FS` as Dirty after the instruction retires.

The failing instruction in this testcase is:

```text
pc 0x80000054, inst 0x22219257, vfsgnj.vv v4, v2, v3
```

Spike reference reports the expected status after the instruction:

```text
mstatus = 0x8000000a00002600
sstatus = 0x8000000200002600
```

This encodes:

```text
FS = Initial
VS = Dirty
```

The DUT reports:

```text
mstatus = 0x8000000a00006600
sstatus = 0x8000000200006600
```

This encodes:

```text
FS = Dirty
VS = Dirty
```

So the DUT incorrectly dirties scalar FP state (`FS`) when retiring `vfsgnj.vv`.

### Expected behavior


For `vfsgnj.vv v4, v2, v3`:

- `VS` should become Dirty, because the instruction writes vector register `v4`.
- `FS` should remain Initial, because the instruction does not write scalar FP registers or `fflags`.
- `fflags` should remain unchanged.

Expected status after the instruction:

```text
mstatus FS=Initial, VS=Dirty
mstatus low status bits = 0x2600
```

### Environment

- XiangShan commit id: f3cc750109cc2a0ff6c12a920221f1a5a324bc75
 - SPIKE commit id (if difftest failed with SPIKE): 611a62b7cee89447ae722b1fb5e55bf9b9ff4297



### To Reproduce

[fsgnj_wfflags_spike_bundle.zip](https://github.com/user-attachments/files/28330893/fsgnj_wfflags_spike_bundle.zip)

### Additional context

_No response_
