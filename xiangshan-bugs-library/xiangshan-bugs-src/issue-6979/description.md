### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

The DUT is XiangShan `kunminghu-v3` with `TestkitV3Config`, commit `4cf8b8d4ae343e01dfb54e5ae8febdaeaa9992ae` (`dirty: 1`). The program executes `amoxor.w s1,a3,(s1)` at `0x80002b5c` with `s1 = 0x20008`, an address without store permission. The AMO takes a store/AMO page fault: `scause = 0x0f`, `sepc = 0x2b5c`, `stval = 0x20008`. The trap handler saves `s1` with `sd s1,88(sp)` at `0x80002394` (`sp = 0xffffffffffe65072`, so the destination is `0xffffffffffe650ca`) and later reloads it with `ld s1,88(sp)` at `0x80002494`.

That 8-byte load is misaligned to 8 bytes and straddles the 16-byte boundary `0xffffffffffe650d0`, so the load unit splits it into a lower half at `0x3ffffffe650ca` and an upper half at `0x3ffffffe650d0`. Per the Load Value Axiom the load must return the value the earlier store wrote, `0x20008`. The DUT commits `0` instead:

- the load's writeback at anchor cycle 9086 drives `intRf_data = 0`
- the commit trace records the reload `pc 0xffffffffffe02494 inst 05813483 wen 1 dst 09 data 0000000000000000`
- DiffTest reports `s1 different at pc = 0xffffffffffe02370, right = 0x0000000000020008, wrong = 0x0000000000000000`

The load unit's full-forward decision is mask-based, and `mergeData` falls back to the DCache word for any byte whose forward mask is clear, without forcing a replay. In this run the lower half's S2 SQ forward mask was `0xfc03` and the SQ forward data did contain `0x20008`, so coverage was present; the loss occurs downstream in the merge or unaligned head/tail selection. The exact stage that drops the bytes was not localized with byte-level probes, so this report claims only the Load Value Axiom violation.


### Expected behavior

The reload must return the stored value. NEMU, the bundled DiffTest reference, records the same address written and read back:

```text
mmu_write: vaddr 0xffffffffffe650ca, paddr 0x800650ca, len 8, data 0x20008
mmu_read:  vaddr 0xffffffffffe650ca, paddr 0x800650ca, rdata 0x20008
```

The architectural `s1` stays `0x20008`, which is also what the handler's `sd s1,88(sp)` writes. Spike cannot serve as the oracle here: on this ELF its first trap is `instruction_page_fault` at `0x265c` and it never reaches the faulting AMO at `0x2b5c`.



### Environment

- Hardware
  - CPU:
  - Memory (GB):
  - Storage (GB):
- Software
  - Operating system:
  - gcc version: <!-- run `gcc --version 2>&1 | head -n 1` to get the version -->
  - clang version: <!-- run `clang --version 2>&1 | head -n 1` to get the version, only needed when you use clang -->
  - java version: <!-- run `java -version 2>&1 | head -n 1` to get the version -->
  - mill version: <!-- run `mill -i --version 2>&1 | head -n 1` to get the version -->
- Repo
  - XiangShan commit id: ``
  - NEMU commit id (if difftest failed with NEMU): ``
  - SPIKE commit id (if difftest failed with SPIKE): ``
- Build & Run
  - Build command: ``
  - Run command (if applicable): ``
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.


### To Reproduce

1. Run the supplied ELF `repro_amo_fault_clobber.elf` on the KMHv3 build with DiffTest against the bundled NEMU reference.
2. Execute `amoxor.w s1,a3,(s1)` at `0x80002b5c` with `s1 = 0x20008` and observe the store/AMO page fault (`scause = 0x0f`, `sepc = 0x2b5c`, `stval = 0x20008`).
3. Let the trap handler save `s1` with `sd s1,88(sp)` at `0x80002394` and reload it with `ld s1,88(sp)` at `0x80002494`.
4. Compare the reloaded `s1` against the reference model; DiffTest stops at the mismatch.

Reproducer ELF SHA-256: `07e2d159ca9b47a097d3350a1c03d26f735360c4e3fba26437c76952c567b04a`

[xiangshan-amo-fault-forward-miss-upload.zip](https://github.com/user-attachments/files/34893981/xiangshan-amo-fault-forward-miss-upload.zip)

### Additional context

_No response_
