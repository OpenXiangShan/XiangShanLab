### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

At commit `e5c70547f3a966accf20a4b065ec1d8e33443180`, the optional MPT path
uses `csr.mmpt.changed` as its context-generation boundary, but both publication
and consumption of that event are incomplete.

The MMPT CSR contains `MODE`, `SDID`, `optOutInNode`, and `PPN`, while
`NewCSR` derives the changed event only from `SDID`. Therefore a new root PPN,
mode, or intermediate-node policy can leave `mmpt.changed` deasserted.

Even when `mmpt.changed` is asserted, retained consumers do not all observe
it:

- `L2TlbMissQueue` flushes for ordinary translation changes but omits MMPT;
- `MemBlock.ptw_resp_v` can replay MPT permission metadata across MMPT change;
- PTW repeater/filter queues omit MMPT from their local flush predicates;
- the Bare-mode/MPT-only TLB refill branch is ORed around `!flush_mmu`;
- `MptOutputSwitchBox` resets its state on flush but can still assert
  `valid/ready` combinationally in the flush cycle.

These are grouped as one issue because they share one repair boundary: define
the effective MMPT context change once and prevent any untagged request,
response, replay slot, refill, or output handshake from crossing that epoch.
Filing one issue per consumer would repeat the same-root duplication problem
identified in the previous XiangShan reports.

MPT is disabled by default (`HasMptCheck=false`); this report does not claim a
failure in `DefaultConfig`.

### Expected behavior

Changing any effective MMPT context field should either invalidate every
retained untagged MPT/MMU state element or compare an explicit generation tag
before reuse. No stale refill or response should be accepted in the flush
cycle or afterward.

### Environment

- XiangShan branch: `kunminghu-v3`
- XiangShan commit: `e5c70547f3a966accf20a4b065ec1d8e33443180`

### To Reproduce

This required step has not yet been executed. A focused test should:

1. enable MPT and populate an MPT-backed translation/permission result;
2. change `PPN`, `MODE`, or `optOutInNode` while retaining the same `SDID`;
3. issue an access whose permission differs under the new context;
4. assert that no pre-change miss-queue, repeater, replay, switchbox, or TLB
   refill result is accepted;
5. repeat with an `SDID` change and force backpressure during the flush cycle.

### Additional context
