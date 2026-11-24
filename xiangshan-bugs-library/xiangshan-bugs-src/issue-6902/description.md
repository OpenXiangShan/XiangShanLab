### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [ ] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

In a full AM environment (flash boot + UART initialization + difftest), writing ≥1MB of contiguous data to physical address region 0x88000000 causes the emulator to permanently deadlock—no instruction progress, no exception raised, no difftest mismatch; the emulator process remains running but hangs forever.

Deadlock mechanism (confirmed by bisection of trigger conditions):
1. Writing 1MB contiguously to 0x88000000 fills L1 D$ dirty lines, which are evicted to L2
2. L2 accumulates dirty lines; when the write volume exceeds L2 capacity, L2 itself needs to evict dirty lines
3. L2's writeback request targets 0x88000000—a region with no write-capable slave on the bus (no responder)
4. The writeback transaction waits forever for a bus response → the entire memory hierarchy blocks → emulator deadlocks

Trigger threshold precisely bisected: 64KB contiguous writes complete normally (below L2 capacity, no writeback needed); 1MB contiguous writes deadlock (exceeds L2 capacity, triggers writeback); 256KB bare-metal 64B-stride writes also complete normally (each line dirtied only once, total dirty volume ≤ L1, fully absorbed by L2).

### Expected behavior

When an L2 writeback request targets an address with no write-capable responder, the bus should return an error response (e.g., TileLink-UH DENIED), triggering a store access fault (mcause=7) or at least causing the emulator to exit with an error—it should not hang permanently.

Specification basis: The TileLink-UH protocol specifies that slaves should return a DENIED response for write requests they cannot service; the RISC-V Privileged Architecture specifies that a store to an unresponsive address produces a store access fault (mcause=7).

On real silicon, if the bus similarly lacks a timeout/error-response mechanism, this constitutes an unrecoverable hardware deadlock.

Security impact: any user-mode program can permanently hang the processor by executing ≥1MB contiguous writes to an unmapped region (denial of service), requiring no privileges.

### Environment

- Hardware
  - CPU: Intel(R) Xeon(R) CPU E5-2680 v2 @ 2.80GHz
  - Memory (GB): 62
  - Storage (GB): 468
- Software
  - Operating system: Ubuntu 20.04.6 LTS
  - gcc version: 9.4.0
  - clang version: 10.0.0-4ubuntu1
  - java version: 11.0.27
  - mill version: 
- Repo
  - XiangShan commit id: ``a863b899b17c80936b6a4ada27cfc0ccfaeed5a0
  - NEMU commit id (if difftest failed with NEMU): ``53bcb5686f8fd05248ae98546b7dc04bdca1bbb0
  - SPIKE commit id (if difftest failed with SPIKE): ``
- Build & Run
  - Build command: make ARCH = riscv64-xs


### To Reproduce

cd /xs-env && source env.sh
$NOOP_HOME/build/emu -i ./build/tc_xs-riscv64-xs.bin -e 0 \
    --diff /xs-env/XiangShan/ready-to-run/riscv64-nemu-interpreter-so \
    --max-cycles=30000000

[xs_am_deadlock.c](https://github.com/user-attachments/files/34590286/xs_am_deadlock.c)

### Additional context

The writeback pipeline of the L2 cache (Coupled L2) waits indefinitely for a response on channel D after issuing a TileLink write request. Because the target address 0x88000000 has no corresponding slave route on the bus interconnect (OpenNCB), the request never receives a response; and the L2 MSHR writeback path lacks a timeout or error abort mechanism, causing the entire memory hierarchy to be forward-blocked.
