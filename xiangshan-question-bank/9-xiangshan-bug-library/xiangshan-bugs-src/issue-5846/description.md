### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

When a cross-16B unaligned store is split into two sbuffer writes, the two halves can be accepted or retired at different times. In `NewStoreQueue`, `cross16BDeqReg` is updated from the current SQ head only when `writeSbufferWire(0).fire`, so it can be cleared after the first half is observed, even though the second half of the same split store has not been fully accounted for yet.

After `cross16BDeqReg` is cleared, the dequeue-side bookkeeping for this split store becomes inconsistent. In particular, because `sbufferFireNum` is no longer generated under the cross-16B path, `sqDeqCnt` can count an extra dequeue advance and push `deqPtr` too far. This can break `StoreQueue` pointer ordering and eventually trigger internal assertions such as `deqPtr` > `rdataPtr` or `cmtPtr` < `deqPtr` / `rdataPtr`.

Please see the attached logs and waveform for the failing evidence.

### Expected behavior

* `cross16BDeqReg` should not be cleared after only the first half is observed.
* `sqDeqCnt` should not include an extra dequeue advance caused by an early `cross16BDeqReg` clear.
* Also, `sqDeqCnt` can only be increased only when the second half of the split store also dequeues, according to #5615.

### Environment

[bug-report.tar.gz](https://github.com/user-attachments/files/26975783/bug-report.tar.gz)


### To Reproduce

The  `bug-report.tar.gz` contains:
* binary (`test.bin` & `test.elf`)
* waveform (`lightsss-wave`)
* logs (`stdout.log` & `stderr.log`)

### Additional context

Two possible directions that may be worth evaluating are:
*  For a cross-16B store, only allow the two split requests to enter the `SBuffer` together, so the dequeue-side bookkeeping does not need to track a partially completed split across cycles.
* Refactor the `cross16BDeqReg` logic so that the dequeue count for a cross-16B split store is advanced only when the second split request is dequeued, instead of allowing the cross-16B context to be cleared too early.
