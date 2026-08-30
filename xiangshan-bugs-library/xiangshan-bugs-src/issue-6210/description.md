### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

The supplied reproducer redirects S-mode instruction fetch to the non-canonical Sv39 virtual address `0x0000010040001000`. A conforming implementation should raise an instruction page fault before fetching any low-address alias. The tested DUT instead fetches the mapped low alias at `0x0000000040001000` and reaches the S-mode alias-page `ecall`.

The low alias page is intentionally mapped only as a witness. If execution reaches its `ecall`, the non-canonical fetch target has already been consumed incorrectly.

RISC-V Specification Requirement：

RISC-V Privileged Architecture, Section 12.4.1 Sv39 Addressing and Memory Protection. Bits 63 through 39 of a 64-bit Sv39 virtual address must all equal bit 38; otherwise, instruction fetch or data access raises a page-fault exception.

A non-canonical Sv39 instruction-fetch target must fault before translation can fetch or execute bytes from a low-address alias.

<img width="1305" height="574" alt="Image" src="https://github.com/user-attachments/assets/5652248b-3f9f-4a97-ac06-5394de62b583" />


### Expected behavior

Expected Result

The target `0x0000010040001000` should raise an instruction page fault before any low-alias instruction is fetched. In the captured reference run, the trap state is `mcause = 12`, `mepc = 0x0000010040001000`, and `mtval = 0x0000010040001000`.

Actual Result

The replay comparison shows the DUT reaching the low alias and reporting an S-mode `ecall` instead of the expected instruction page fault:

```text
mepc different   right = 0x0000010040001000, wrong = 0x0000000040001000
mtval different  right = 0x0000010040001000, wrong = 0x0000000000000000
mcause different right = 0x000000000000000c, wrong = 0x0000000000000009
```

Here `right` is the reference model and `wrong` is the XiangShan DUT. `mcause = 0x9` is the S-mode `ecall` from the alias page, which should not have been fetched.

Trace context before the mismatch:

<img width="1074" height="403" alt="Image" src="https://github.com/user-attachments/assets/82bbfd7b-e222-49e1-beca-c6a26b794971" />

Difftest trace screenshot:

<img width="839" height="109" alt="Image" src="https://github.com/user-attachments/assets/cd921ba1-095a-46e3-a8e5-31451459d850" />

### Environment

XiangShan branch: Kunminghu-v3.
XiangShan commit: `96c3f568f943a096ffd3d712dc6f462ac4b1ba33` (`v3.2.2-alpha-1931-g96c3f568f`).
Observed under XiangShan replay/difftest co-simulation with the SPIKE reference model; Kunminghu-v2 was tested and did not reproduce this behavior.

### To Reproduce

1. Use the supplied `poc/program.elf` artifact in the XiangShan replay environment.
2. Run the built image with XiangShan as DUT using the same replay/no-diff mode represented by `logs/`.
3. Compare the architecture-visible trap or CSR state around the highlighted instruction sequence.

Minimal source excerpt:

```asm
.equ LOW_ALIAS_VA, 0x0000000040001000
.equ HIGH_BAD_TAG, 0x0000010000000000
.equ BAD_FETCH_VA, (HIGH_BAD_TAG | LOW_ALIAS_VA)

li   t0, BAD_FETCH_VA
jalr zero, 0(t0)           /* must trap as non-canonical IFETCH */

s_alias_page:
  ecall                    /* bad if low alias is executed */
```

### Additional context

The log first shows the branch target register holding `0x0000010040001000`, then shows an exception at `pc 0x0000000040001000` with `cause 9`. That sequence is the important symptom: the high non-canonical target was effectively treated as its low canonical alias.

The fix should perform Sv39 canonicality checks for instruction-fetch targets before any pruned, truncated, or low-alias address is used in the ITLB/fetch path.

[XS-2.zip](https://github.com/user-attachments/files/29838068/XS-2.zip)
