### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

Kunminghu-v2 applies instruction-fetch offsets to the GPA of an implicit VS-stage PTE read. When this read takes an instruction guest-page fault, `mtval2` or `htval` can identify the wrong address, including an address in the previous, still accessible G-stage page.

We built a program executes integer instructions near the end of a guest code page and then crosses into a page whose VS L0 PTE page is G-stage inaccessible. The guest executes no CSR, fence or privilege-changing instruction before the fault. The faulting PTE GPA is `0x8003f000`. Both supplied M- and HS-route reproducers report `0x2000fbf1`, which identifies GPA `0x8003efc4`, 60 bytes below the actual PTE. 

The page fault itself is intentional. The architectural mismatch is the nonzero fault address recorded by the CPU. It reproduces both with ELF self-checks in RTL simulation and with native Difftest, where the first reported CSR difference is `mtval2` or `htval`.

### Expected behavior

The architectural basis is **H extension 1.0 in the official 2024-04-11 privileged manual**, fixed source revision `6ee57d8997021aaa8025e208164763fe12e03c19`:                                  

- **[Section 18.2.8: htval](https://github.com/riscv/riscv-isa-manual/blob/6ee57d8997021aaa8025e208164763fe12e03c19/src/hypervisor.adoc#L688-L747)** and **[Section 18.4.4: mtval2](https://github.com/riscv/riscv-isa-manual/blob/6ee57d8997021aaa8025e208164763fe12e03c19/src/hypervisor.adoc#L1433-L1465)** permit zero. When nonzero, the value must identify the faulting GPA shifted right by two; for an implicit VS-stage access, this is the address of that implicit access.
- **Section 18.5.1** specifies G-stage translation of VS page-table accesses and reporting the original access type. **Section 18.6.3 / Table 39** specifies the corresponding RV64 implicit-read pseudoinstruction when the second address is nonzero. See the [same fixed H 1.0 specification](https://github.com/riscv/riscv-isa-manual/blob/6ee57d8997021aaa8025e208164763fe12e03c19/src/hypervisor.adoc).

This uses the existing H 1.0 rule, rather than a requirement introduced in a newer manual. The testcase accepts the architecturally permitted zero value.

In this case, the faulting VA `0x80200000` has Sv39 indices `(2, 1, 0)`. Its VS L0 table is at GPA `0x8003f000`, so the required PTE read is at `0x8003f000 + 0 * 8`. That PTE read fails G-stage translation before the final code GPA can be obtained.

For the supplied case:

| Field | Allowed/expected | Observed |
| --- | --- | --- |
| cause | 20 | 20 |
| xepc | `0x80200000` | `0x80200000` |
| xtval | zero or `0x80200000` | `0x80200000` |
| mtval2/htval | zero or `0x2000fc00` | **`0x2000fbf1`** |
| mtinst/htinst when second address is nonzero | `0x3000` | `0x3000` |

### Environment

- Code Version
  - XiangShan Kunminghu-v2 commit `1fd6f257`, KunminghuV2Config, one core.
  - Native Difftest `589f7a09`; NEMU `112f2fd1`; seed 1, squash disabled, shared default boot flash.


### To Reproduce

1. Extract `kunminghu-v2-implicit-fetch-gpa-issue-20261108-v2.zip`. Use the unmodified `IGPFOFF01.elf` for the M-route case or `IGPFOFF06.elf` for the HS-route case. The ELF, initialization, tables, fault checks and recovery are generated natively by `tests/riscv/cpu/guest_faults/implicit_fetch_pte_gpa_offset.py`.
2. For native Difftest, use the matching single-core emulator and reference:

```sh
./emu -i IGPFOFF01.elf \
  --diff=./riscv64-nemu-interpreter-so --flash=./boot.bin \
  --ram-size=256MB --max-cycles=50000 --max-instr=20000 \
  --no-squash --seed=1
```
3. The first architectural mismatch is:

```text
mtval2 different at pc = 0x008020000e, right = 0x000000002000fc00, wrong = 0x000000002000fbf1
```

For the HS route, replace the image with `IGPFOFF06.elf`; the first difference is:

```text
htval different at pc = 0x008020000e, right = 0x000000002000fc00, wrong = 0x000000002000fbf1
```

Here `right` is REF and `wrong` is DUT. Both runs terminate with a Difftest mismatch. The actual exception EPC is `0x80200000`, cause 20; the printed Difftest cursor PC `0x8020000e` is a separate diagnostic value. Raw logs and SHA-256 checksums are included in the attachment.

The ELF also contains its own fault-address check, so the CHI simulation detects the issue without Difftest. On a conforming implementation, the handler repairs the G mapping, fences, retries the guest instruction and checks completion over 16 iterations.

### Additional context

**Source-level diagnosis**

In the tested revision, [IPrefetch.scala](https://github.com/OpenXiangShan/XiangShan/blob/1fd6f257469ba99a00004595c92a3e5d482e9619/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala#L238-L253) subtracts the fetch-port offset from the ITLB GPA, then [GPAMem.scala](https://github.com/OpenXiangShan/XiangShan/blob/1fd6f257469ba99a00004595c92a3e5d482e9619/src/main/scala/xiangshan/backend/GPAMem.scala#L29-L35) adds the instruction offset. Neither operation excludes an implicit PTE GPA using `isForVSnonLeafPTE`.

Read-only model observations show a GPAMem entry with base `0x8003efc0`, `isForVSnonLeafPTE=1` and `ftqOffset=2`. For the supplied reproducers, the arithmetic matches the erroneous CSR exactly:

```text
faulting PTE GPA:                         0x8003f000
fetch-port adjustment: 0x8003f000 - 64 = 0x8003efc0
instruction adjustment:          + 2*2 = 0x8003efc4
reported second address:          >> 2 = 0x2000fbf1
```

The internal observations supplement the final architectural mismatch.

**Relationship to #6212 and control cases**
This has a different specific source-level cause from [#6212] (https://github.com/OpenXiangShan/XiangShan/issues/6212). 
The current v2 case instead faults at an L0 PTE with zero page offset and is corrupted by the port/instruction offset arithmetic.
