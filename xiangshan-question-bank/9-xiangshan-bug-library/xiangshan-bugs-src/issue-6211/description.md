### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

The supplied reproducer enters S-mode at the end of a mapped Sv39 page so that a 32-bit instruction crosses into an unmapped next page. The correct first trap is an instruction page fault with `mepc` at the instruction start and `mtval` at the inaccessible second-page portion. The tested DUT reports `mepc` advanced to the second page and `mtval` advanced by two bytes.

The important point is not the exception class: both sides report an instruction page fault. The bug is that the architectural recovery metadata points at the wrong part of the instruction stream.

RISC-V Specification Requirement

RISC-V Privileged Architecture, Section 3.1.14 Machine Exception Program Counter (mepc) and Section 3.1.16 Machine Trap Value (mtval). For variable-length instructions, mepc identifies the beginning of the instruction, while mtval may identify the portion of the instruction that faulted.

For the cross-page instruction fault, mcause should be instruction page fault, mepc should remain 0x40000ffe, and mtval should identify the inaccessible portion at 0x40001000.

<img width="1304" height="591" alt="Image" src="https://github.com/user-attachments/assets/39252cee-3b21-4bda-a5ce-5535fc1ee012" />
<img width="1290" height="269" alt="Image" src="https://github.com/user-attachments/assets/392ef6f3-3069-4997-ad0c-aa34fba9f0c3" />
<img width="1280" height="78" alt="Image" src="https://github.com/user-attachments/assets/1c1990fa-bebb-4013-bac9-5b93cfd5bb65" />
<img width="1280" height="83" alt="Image" src="https://github.com/user-attachments/assets/174c70b3-ce5e-4b29-b7c6-08a63b3af86c" />


### Expected behavior

Expected Result

mcause = 12, mepc = 0x40000ffe, and mtval = 0x40001000.

Actual Result

The replay comparison reports:

```text
mepc different  right = 0x0000000040000ffe, wrong = 0x0000000040001000
mtval different right = 0x0000000040001000, wrong = 0x0000000040001002
```

Here `right` is the reference model and `wrong` is the XiangShan DUT. The DUT has moved both recovery fields forward into the second page.

Trace context before the mismatch:

<img width="1092" height="328" alt="Image" src="https://github.com/user-attachments/assets/1bc70140-9b84-481d-b855-e107f4a5d39f" />

Difftest trace screenshot:

<img width="1001" height="107" alt="Image" src="https://github.com/user-attachments/assets/0c54fcc2-d2df-4e67-b517-93b57e8ae231" />

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
.equ CROSS_VA, 0x0000000040000ffe

li   t0, CROSS_VA
csrw mepc, t0
mret

/* 32-bit instruction starts at 0x40000ffe and crosses into the next page. */
cross_page_insn:
  .4byte 0x00000013
```

### Additional context

The log shows `mret` entering `0x40000ffe`, followed by an instruction page fault on bytes that cross into `0x40001000`. The reference keeps `mepc` at the beginning of the instruction and uses `mtval` for the faulting portion. XiangShan instead reports `mepc = 0x40001000` and `mtval = 0x40001002`, which would mislead any handler that uses `mepc` to resume after mapping the missing page.

The fix should report cross-page instruction faults with `mepc` set to the original instruction start and `mtval` set to the actual faulting portion, without letting internal second-page fetch bookkeeping become the architectural recovery PC.

[XS-3.zip](https://github.com/user-attachments/files/29838075/XS-3.zip)
