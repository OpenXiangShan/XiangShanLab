### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A misaligned 8-byte load whose data address splits across a 16-byte (VWord) boundary can miss store-to-load forwarding on its lower half and commit stale data — the pre-store value — instead of the value written by an in-flight store to the same address. NEMU (golden) returns the correct just-stored value. Same-hart, same-address, program-order store→load ordering is architecturally required even for non-atomic misaligned accesses, so the load must observe the store.

Concretely (see attached difftest_capture.log), difftest aborts at instrCnt = 3,613:
- a1 different at pc = 0xffffffffffe02340, right (NEMU) = 0x0f, wrong (XiangShan) = 0x0c
- a1 is written by ld a1, 280(a0) at pc = 0xffffffffffe024bc (encoding 0x11853583).
- An earlier sd t0, 280(sp) (with a0 == sp) wrote 0x0f to the same effective address.
- sp is 8-byte-misaligned (VA ends …e7; PA 0x800611ff), so the 8-byte access spans 0x800611ff … 0x80061206, crossing the 16-byte boundary at 0x80061200.
- XiangShan splits the load at that boundary; the lower part (byte at 0x800611ff) does not receive store-to-load forwarding and reads stale memory → commits 0x0c (pre-store) instead of 0x0f.

[difftest_capture.log](https://github.com/user-attachments/files/29948910/difftest_capture.log)

### Expected behavior

The load must observe the most recent same-address store in program order and commit 0x0f (as NEMU does), not the stale 0x0c. Splitting the access at the 16-byte boundary must not drop store-to-load forwarding coverage on the lower half.

### Environment

- Hardware
    - CPU: Intel(R) Xeon(R) CPU E5-2683 v4 @ 2.10GHz
    - Memory (GB): 503
    - Storage (GB): (n/a)
  - Software
    - Operating system: Ubuntu 24.04.3 LTS
    - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
    - java version: openjdk version "20.0.2-internal" 2023-07-18
    - mill version: 0.12.15
  - Repo
    - XiangShan commit id: `8a42e727cc58db2b00d4bb2de7b397355f89257c` (kunminghu-v3 HEAD, 2026-07-10)
    - NEMU commit id (if difftest failed with NEMU): prebuilt reference from XiangShan `ready-to-run` submodule @ `955e6e2a5b5a51426d000597710cd632aeba2f52` (built via OpenXiangShan/NEMU CI)
    - SPIKE commit id (if difftest failed with SPIKE): n/a (difftest vs NEMU)
  - Build & Run
    - Build command: `make emu NUM_CORES=1`
    - Run command: `./build/verilator-compile/emu -i repro_misaligned_split_load.elf -C 5000000 --diff ./ready-to-run/riscv64-nemu-interpreter-so`

### To Reproduce

1. Build the difftest emu at commit 8a42e72: make emu NUM_CORES=1.
2. Run the attached workload under NEMU difftest: ./build/verilator-compile/emu -i repro_misaligned_split_load.elf -C 5000000 --diff ./ready-to-run/riscv64-nemu-interpreter-so
3. Difftest aborts deterministically at instrCnt = 3,613 with a1 different at pc = 0xffffffffffe02340, right = 0x0f, wrong = 0x0c.

Minimal workload: attached repro_misaligned_split_load.elf (RV64, M-mode boot + S-mode paging scaffold, deterministic, Seed=0). The essential logical sequence is a store then a load to the same 8-byte-misaligned address that crosses a 16B boundary:
```
// asm
# a0 == sp, sp = 0x…e610e7   (8-byte-misaligned)
sd  t0, 280(sp)     # writes 0x0f to VA sp+280 -> PA 0x800611ff (spans …ff..06, crosses 0x80061200)
...
ld  a1, 280(a0)     # reads the SAME address; must return 0x0f; XiangShan returns stale 0x0c
```
(A standalone hand-written minimal was not separable: the difftest emu needs the full boot + page-table scaffold to reach the faulting context, so a bare ELF stalls in the bootrom.)

[repro_misaligned_split_load.elf.zip](https://github.com/user-attachments/files/29948917/repro_misaligned_split_load.elf.zip)

### Additional context

Root-cause localization from a Verilator waveform of the LSU (cycles ~7700–7885):
- The misaligned 8-byte load is split at the 16B boundary → lower part PA 0x800611ff, upper part 0x80061200.
- Lower-part LoadUnit stage-2: fullForward = 0 (the store-forward mask did not cover byte 0x800611ff); s2_replayCauses = 0x40 = C_DM (dcache-miss replay).
C_NK (nuke, bit 11) and C_FF (bit 4) are not set.
- So the lower half neither forwarded from the store queue nor waited — it fell through to the dcache, missed, and refetched a stale line while the store
data (0x0f) was still resident in the store queue / sbuffer → committed stale 0x0c.

Comparison with existing issues:
- Not #6002 (open): that is a store→load NUKE / ordering violation (younger load executes first; unshifted nuke-mask 0xf000 & 0x000f == 0), replay cause C_NK. Here the load is never nuked — it is a forwarding-coverage miss (C_DM, fullForward = 0).
- Not #6209 (open): that is a split-load tail page-fault not being retired (a fault, no wrong data). Here there is no fault; wrong data is committed.
- Related, already fixed and present in the tested build: #5851 (cross-page misaligned sd→stale load) and #5998 (StoreQueue cross-16B partial-forward treated as full overlap). Both fixes are in commit 8a42e72, yet this reproduces → this appears to be a residual gap of the cross-16B forwarding fix on the misaligned-split lower-VWord path.

Reproduced on the latest kunminghu-v3 HEAD at time of testing (8a42e72, 2026-07-10).
