### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

The program is the upstream riscv-tests case `hypervisor/2-stage_translation` (unmodified apart from the repository's exit-code patch). It builds a VS-stage page table and a G-stage page table, maps a GVA through GPA to a physical page, performs a `sw` and an `hlv.w` through the two-stage translation, and checks the loaded value.

A store that is squashed by a speculative redirect must not write the store buffer and must not consume store-queue dequeue credit. On KMHv3 the store queue instead marks such an entry committed and dequeues it anyway, which trips its own invariant and aborts the simulation:

```text
Assertion failed at PhysicalStoreQueue.sv:17820.
[ERROR][time=1949] ...lsq.storeQueue.physicalQueue: double deq! index: 11
Core 0: ABORT
Core-0 instrCnt = 162, cycleCnt = 1,945
```

The redirect that squashes the entry is a normal load replay of the `hlv.w` at `0x800002f8` (`isFromLoad = 1`, `robIdx = 75`); store-queue entry 11 also carries `robIdx = 75`, so the redirect covers it. The root cause is not a second dequeue of a live entry: pre-commit marks entry 11 committed in the same cycle the raw redirect arrives, and the cancellation path does not clear `committed`, so the dequeue path later treats the cancelled entry as ready.


### Expected behavior

The store squashed by the load-replay redirect must not enter the store buffer and must not consume dequeue credit. The same ELF on KMHv2 completes normally: `a0 = 0`, 2585 cycles, and the store queue drains without tripping the invariant.

## Observed behavior

On KMHv3 the simulation aborts at `instrCnt = 162`, `cycleCnt = 1,945`. Waveform (`wave.fst`, window 1929–1945, raw value changes, anchor t=1944):

| t | `raw_redirect_valid` | `redirectReg_valid` | `preCommitPtr_valid` | `committed_11` | `needCancel_11` | `addrValid_11` | `deqCancel_11` | `sqDeqCnt` |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1933 | 0 | 0 | 1 | 0 | 0 | 1 | 0 | 0 |
| 1934 | 1 | 0 | 1 | 1 | 0 | 1 | 0 | 0 |
| 1935 | 0 | 1 | 0 | 1 | 0 | 1 | 0 | 0 |
| 1936 | 0 | 0 | 1 | 1 | 0 | 1 | 0 | 0 |
| 1937 | 0 | 0 | 0 | 1 | 0 | 1 | 0 | 0 |
| 1938 | 0 | 0 | 0 | 1 | 0 | 1 | 0 | 0 |
| 1939 | 0 | 0 | 0 | 1 | 1 | 1 | 0 | 0 |
| 1940 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| 1941 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| 1942 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| 1943 | 0 | 0 | 0 | 1 | 0 | 0 | 0 | 0 |
| **1944** | 0 | 0 | 0 | 1 | 0 | 0 | **1** | **1** |
| 1945 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

At t=1934 the raw redirect is already valid while `redirectReg_valid` is still 0; pre-commit stays enabled and entry 11 becomes committed. At t=1935 the registered redirect masks pre-commit, but the entry keeps `committed = 1`, and the store-buffer request becomes valid. At t=1940 the cancellation clears `addrValid`, yet `committed` remains 1. At t=1943 the store buffer accepts the write (`sbuf0_valid = 1`, `sbuf0_ready = 1`); at t=1944 `sqDeqCnt = 1` and `deqCancel_11 = 1` with `addrValid_11 = 0` and `vecInactive_11 = 0`, so the assertion fires. At t=1945 the dequeue pointers advance from 11/12 to 12/13.

The simulator reports `dirty: 1` for the KMHv3 run. A re-collection on the default-branch HEAD `a0c2a26` (`dirty: 1`, already containing #6768 and #6769) reproduces the identical assertion at `double deq! index: 11`, `time=1947`, `instrCnt = 162`, `cycleCnt = 1,943`. A local Spike build is not usable as an oracle here because it lacks the Smrnmi CSR `0x744` used during initialization; the KMHv2 comparison provides the reference result instead.


### Environment


Hardware and host software were not captured for the run that produced this evidence, and are not reconstructed here. The run used a prebuilt emulator, so local build toolchain versions do not apply to it.

Emulator: prebuilt. `sim.log` records only `emu compiled at Oct 28 2026, 08:58:44` and `Core 0's Commit SHA is: 6705b63685, dirty: 1`. The full historical build command and tool versions are not asserted here, and the emulator binary itself is not included in the evidence package, so no emulator SHA-256 is claimed.

XiangShan: `6705b63685` (`dirty: 1`), kunminghu-v3. The same assertion also reproduces on the default-branch HEAD `a0c2a26` (`dirty: 1`), which already contains #6768 and #6769.

difftest: none — the run used `--no-diff`; no NEMU or Spike comparison was performed. NEMU: not applicable. SPIKE: not applicable — the local build lacks the Smrnmi CSR `0x744` used during workload initialization; the reference result is the same ELF on KMHv2 (`a0 = 0`, 2585 cycles).

Workload: `prog.elf` SHA-256 `efab33369a6ccd16461323e093cd95aaaeedfad7e8edf227e0fba25615c22283`; `image.bin` SHA-256 `c87ccde4bc306a0eb9096ab27dae0ee53f5f1f7c933ac89bae8e62fa82573de8`. Run arguments: `./build/emu --no-diff --ram-size=128MB -C 100000000 -i image.bin -b0 -e0`.

A static-only read of the current default-branch tip `980935fd3c` on 2026-11-04 still shows `preCommitValid` masked only by `redirectReg.valid` (`VirtualStoreQueue.scala:328`) and `committed` cleared only in the dequeue loop (`NewStoreQueue.scala`). That is not a latest-HEAD dynamic result.



### To Reproduce

**Reproduction**

1. Build the VS-stage table (GVA `0x0–0xfff` → GPA `0x200000–0x200fff`) and the G-stage table mapping that GPA to the data page; write `vsatp` and `hgatp`, then `hfence.vvma` / `hfence.gvma`.
2. Execute the `sw` and then the `hlv.w` at `0x800002f8`; the load replay issues a redirect with `robIdx = 75` that covers store-queue entry 11.
3. Run the ELF on KMHv3 with the store-queue assertions enabled; the simulation aborts at `double deq! index: 11`.
4. Run the same ELF on KMHv2 and compare: the same program completes with `a0 = 0`.

Reproducer ELF SHA-256: `efab33369a6ccd16461323e093cd95aaaeedfad7e8edf227e0fba25615c22283`

[xiangshan-store-queue-double-deq-upload.zip](https://github.com/user-attachments/files/34362998/xiangshan-store-queue-double-deq-upload.zip)

### Additional context

_No response_
