### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

 In the old LoadUnit design, when vp_match_fail is detected, the instruction takes a single terminal path: it
  raises rollback to trigger backend redirect, and all replay causes are cleared before writeback to LSQ. As a
  result, no replay entry is created for that instruction.

  In the current NewLoadUnit design, this semantic guarantee is lost. When matchInvalid occurs together with
  other replay causes such as dataInvalid or nuke, the load still raises rollback, but its lqWrite.rep_info.cause
  is not cleared and is forwarded to LSQ/LRQ. This allows the same instruction to simultaneously:

  - request backend redirect through rollback, and
  - establish a replay path in LSQ/LRQ.

  This violates the intended single-destination behavior for a poisoned load and makes correctness depend on a
  later redirect flushing the replay entry. That behavior is fragile and differs from the old design contract.

  A reproducing scenario is:

  - no TLB error,
  - no DCache replay/miss/bank-conflict issue,
  - StoreQueue returns both dataInvalid=1 and matchInvalid=1,
  - a store-load violation (nuke) is also present in the pipeline.

  Under this condition, waveform observation shows that rollback is asserted, but replay cause is still preserved
  and sent to LSQ.

### Expected behavior

When vp_match_fail / matchInvalid is detected, the instruction must take a unique terminal path:

  - assert rollback to trigger backend redirect,
  - clear all replay causes before lqWrite,
  - do not create any replay entry in LSQ/LRQ.

  In other words, matchInvalid must dominate replay generation, and the instruction must not simultaneously have
  both a rollback path and a replay path.

### Environment

    XiangShan commit id: `89261767d3f8d6d358c9b9411d833f0e09607496`



### To Reproduce
Use Case: sq_datainvalid_matchinvalid_nuke

Goal
  Verify that a younger translated load can trigger `dataInvalid + matchInvalid + memoryViolation(level=1)` even when cache/TLB paths are normal.

 Scenario
  1. Warm up the target physical line to guarantee `dcache hit`.
  2. Issue an older store with `STA(main_va)` only in bare mode, so SQ keeps address dependency information.
  3. Switch to `Sv39 root-B`.
  4. Send a TLB-prime load to stabilize translation path.
  5. Send the younger main load and observe SQ invalid-match response and pipeline nuke behavior.

  Expected Result
  - Main load is `dcache hit`, with no cache error, no TLB/page exception, and no outer/uncache path.
  - StoreQueue reports:
    - `dataInvalid = 1`
    - `matchInvalid = 1`
  - The load is identified as having `stld` violation risk and triggers `memoryViolation(level=1)`.
  - DUT performs flush/nuke recovery instead of degrading into miss/error replay.

  ———


### Additional context

_No response_
