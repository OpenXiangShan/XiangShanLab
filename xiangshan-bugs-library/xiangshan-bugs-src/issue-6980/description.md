### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

Commit: XiangShan KMHv3 `c4cfd2b`.
The test is a module-level harness that drives `AtomicsUnit` directly (no core, no architectural program). It performs two `amoswap.d` operations in sequence:

- operation A: `amoswap.d` to `0x1000`. The DCache response is driven with `tl_corrupt = 1`, `tl_denied = 0`, so the writeback carries `bit19 = 1` (`hardwareError`).
- operation B: `amoswap.d` to `0x2003`. The address is not 8-byte aligned, so the operation exits early through `s_finish` without issuing any DCache request. Its writeback carries `bit6 = 1` (`storeAddrMisaligned`) and also `bit19 = 1`.

`hardwareError` is defined for an instruction that attempts to access corrupted data. Operation B performs no memory access at all, so its exception vector must not contain that bit. The DUT reports it because `exceptionVec` is a persistent register that only the cache-response path ever writes; the early-exit path and `resetFSM()` leave the previous operation's `hardwareError` in place.


### Expected behavior

Operation B's exception vector contains only its own `storeAddrMisaligned` bit:

- operation A writeback: `bit6 = 0`, `bit19 = 1`
- operation B writeback: `bit6 = 1`, `bit19 = 0`
- operation B issues zero DCache requests in both variants

This is what the fixed RTL and the harness's `kExpectStale = false` configuration check. Spike and QEMU are not applicable here: the trigger is a `tl_corrupt` injected into a module-level TileLink response, which they do not model.



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

1. Build the harness against the generated `AtomicsUnit.sv` at the affected commit (`run.sh` takes the generated file as its argument), or run the prebuilt original and fixed executables from the issue attachment.
2. Run operation A: `amoswap.d` to `0x1000`, with the DCache response driven as `tl_corrupt = 1`, `tl_denied = 0`. Observe the writeback `bit6 = 0`, `bit19 = 1`.
3. Run operation B: `amoswap.d` to `0x2003`. Confirm the operation issues zero DCache requests and observe the writeback `bit6 = 1`, `bit19 = 1`.
4. Run the same harness against the fixed RTL variant, in which `resetFSM()` clears `exceptionVec`. Observe operation B's writeback `bit6 = 1`, `bit19 = 0`.

Harness source SHA-256: `e7ce4a4b96d776ae03029f3b29b3a259cdcf7cbd69afe5695ebb59138c4bed41`

[xiangshan-amo-stale-hwerror-upload.zip](https://github.com/user-attachments/files/34894004/xiangshan-amo-stale-hwerror-upload.zip)

### Additional context

_No response_
