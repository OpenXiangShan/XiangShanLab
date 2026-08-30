### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

Hello, while inspecting code for the statistical corrector (SC.scala), I noticed a possible bug in the write conflict and forwarding mechanism.

Background: When there is a read-write conflict to the table (read and write to the same index in the same cycle), the read takes priority, and the write data is instead saved to conflict_buffer until it can be written to the table in a later cycle. When there is a read to the same index while the conflict_buffer is valid (including the read of the original read-write conflict), then the read result is forwarded from the conflict_buffer, since the value in the table is stale.

Problem: The table contains four ways per entry, but only one of the ways is changed during a write. So when forwarding from the conflict_buffer, we should expect that one of the ways is from the conflict_buffer, and the other three ways are from the table. However, it seems that in the current logic, when forwarding from the conflict_buffer, the other three ways are always zero. I also looked at a waveform from microbench to confirm this behavior.

<img width="905" alt="Image" src="https://github.com/user-attachments/assets/5957c18b-55f1-4209-a601-6d1ff4e16593" />

At marker 0: Read at index 84, the read result is {02, 05, 00, 00}
At marker 1: Read-write conflict at index 84 way 1, the read result is {00, 06, 00 00}
At marker 2: Read at index 84, the read result is {02, 06, 00, 00}
-> I expected that the read result at marker 1 would be {02, 06, 00, 00}.

However, since I am not very familiar with the XiangShan design, I am not sure whether this behavior will cause any issue downstream. If this is a real bug, I can try to write a fix.

The conflict_buffer logic was added in #3671.

Thanks, Sam

### Expected behavior

During read-write conflict to the statistical corrector table, the read result should match the case where the write occurred before the read. In other words, the read result for the write way should be forwarded from the write data, and the read result for the other ways should be from the table.

### To Reproduce

Steps:
1. Gather waveform for a workload with non-trivial branch activity, I used microbench from ready-to-run folder.
2. Locate read-write conflict to SC table with use_conflict_data signal in SCTable, the entry should also have more than one non-zero way.
3. Observe that the read result of the read-write conflict contains zero for all ways other than the write way.
4. Observe that reads to this index following the read-write conflict have the correct value. (The problem is only present during write forwarding.)

### Environment

- XiangShan branch: master
- XiangShan commit id: 977ac3b188267f31bfb32c7fa358f8b923a0e985
- NEMU commit id: N/A
- SPIKE commit id: N/A


### Additional context

_No response_
