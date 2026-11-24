### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

In [`WriteBuffer.scala#L262`](https://github.com/OpenXiangShan/XiangShan/blob/8b15f06520bc7fc264095b11ece1d0e268153de7/src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala#L262):

```scala
val touchWays = Seq(writeTouchVec(nRows)) ++ hitTouchVec(nRows).filter(_.valid == true.B).take(numPorts)
replacerWay(nRows) := replacer.way
replacer.access(touchWays)
```

`_.valid == true.B` is Scala object equality, not the Chisel hardware comparison `===`. A hardware `Bool` field is never the same object as a new `true.B` literal, so the predicate is `false` for every element during elaboration and the `filter` always returns an empty `Seq`. The PLRU therefore only receives the allocation touch (`writeTouchVec`). The hit touches set at [L201-L203](https://github.com/OpenXiangShan/XiangShan/blob/8b15f06520bc7fc264095b11ece1d0e268153de7/src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala#L201-L203) are never used.

Effect: when the buffer is full of pending entries and a new write misses, the victim is `replacerWay`. Because hits never update the PLRU, an entry that was just written again (hit) can be chosen as the victim as if it had not been touched, and its newest data is overwritten before it reaches the SRAM.

`WriteBuffer` is used by TAGE, SC, ITTAGE, MBTB and ABTB. This affects prediction training only; we do not claim any incorrect architectural execution.

Module-level result on the real `WriteBuffer` (the ScTable instantiation: `WriteBuffer(new ScTableSramWriteReq(256, 2), 4, numPorts = 1, numWays = 2, hasWayMask = true)`), with the SRAM write port held busy so that the buffer fills:

1. write sets 1, 2, 3, 4 (buffer full);
2. write set 1 three more times (hits; newest value of set 1 = 13);
3. write set 5 (full-buffer miss, one pending entry is overwritten);
4. drain and record which sets reach the SRAM.

| | control: no hits on set 1 | set 1 hit 3 times |
|---|---|---|
| original | set 1 is overwritten (it is the oldest; correct) | **set 1 is overwritten; its newest value 13 never reaches the SRAM**. Drain order `5, 2, 3, 4` |
| with the fix below | set 1 is overwritten (same as original) | set 3 is overwritten; set 1 value 13 reaches the SRAM. Drain order `1(13), 2, 5, 4` |

### Expected behavior

A write that hits an existing entry updates that entry in the PLRU, so that a full-buffer miss replaces the least recently used entry, not the one that was just written again. (Alternatively, if hit updates are intentionally not wanted, the `filter` line is dead code and could be removed; currently it does not do what it appears to do.)

### Environment

- Hardware: Intel Xeon Platinum 8163 @ 2.50GHz (96 threads), 629 GB memory
- Software: Ubuntu 22.04.5 LTS; gcc 11.4.0 (host), riscv64-unknown-elf-gcc 10.2 (benchmarks); OpenJDK 21.0.12.1; Chisel 7.13.0, firtool 1.149.0 (sources compiled with scalac using the classpath of the project's mill build; mill not used directly); Verilator 5.052
- XiangShan commit: `b3f903bf2e7630e582500599629e7b4c5c981711`. The only later commit on `kunminghu-v3`, `8b15f06520bc7fc264095b11ece1d0e268153de7` (current HEAD), changes CI workflow files only; `WriteBuffer.scala` is identical and the line is still present.
- Not run with the upstream `emu` + difftest flow (see below).

### To Reproduce

Module test (harness, testbench, logs): https://gist.github.com/fireceW/b6efcb74cc2f5b2bea2da58ae15ecb87

- `WbHarness.scala` instantiates the real `WriteBuffer` with the ScTable parameters above; `tb.cpp` drives the sequence and prints the drained entries.
- `ORIG-run.log` / `FIX-run.log` are the outputs for the original code and for the fix below; `spec.json` is the exact text replacement used for the fix.
- `WHOLECORE-SUMMARY.txt` is the whole-core table below.

Fix used for the "with the fix" rows:

```diff
-    val touchWays = Seq(writeTouchVec(nRows)) ++ hitTouchVec(nRows).filter(_.valid == true.B).take(numPorts)
+    val touchWays = Seq(writeTouchVec(nRows)) ++ hitTouchVec(nRows)
```

`PseudoLRU.access(Seq[Valid[UInt]])` (rocket-chip `Replacement.scala`) already skips elements with `valid = 0`, so the whole `Vec` can be passed. The exact change is up to the maintainers.

Whole-core measurement (`DefaultConfig`, 1 core, own Verilator testbench, original vs. fixed, short bare-metal benchmarks; read-only counters on every `WriteBuffer` instance; all runs pass and commit the same number of instructions):

| benchmark | hit touches dropped (original) | cycles original | cycles fixed | change |
|---|---:|---:|---:|---:|
| CoreMark (1 iteration) | 54,373 | 336,720 | 333,493 | −0.96 % |
| qsort | 57,152 | 242,911 | 243,803 | +0.37 % |
| dhrystone | 6,233 | 62,966 | 63,049 | +0.13 % |
| median, multiply, rsort, towers, vvadd, spmv, memcpy | 241 – 16,042 | — | identical | 0 |

The condition is common (thousands of dropped hit touches per benchmark), but the end-to-end effect of the change on these short runs is small and mixed in sign. (The fixed model also contained an unrelated PtwCache change, which is inactive in all of these M-mode benchmarks: the page cache has 0 accesses.)

### Additional context

- The line was introduced by c8a1ab7e7 (#5081).
- Searches for `WriteBuffer PLRU`, `WriteBuffer replacer`, `hitTouchVec`, `touchWays` found other WriteBuffer fixes (#5526, #5898, #6315) but none covering this line. Open PR #6892 also edits `WriteBuffer.scala` but does not change this line.
- This report was prepared with AI assistance (opencode, Claude Opus 5.5).
