### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

An unaligned scalar load is split into a head and a tail access. The head access is on a mapped Sv39 page and the tail access enters an unmapped page. The reference behavior is to keep forward progress by retiring a precise load-page-fault trap for the original load instruction, with mtval identifying the faulting tail page address. On the tested XiangShan revision, the DUT stops committing before that trap is retired, leaving mcause, mepc, and mtval stale.

The reproducer first executes a same-page misaligned load as a calibration case. The failing instruction is the following split load at `0x40000ffc`: the low bytes are in the mapped page and the high bytes start at the unmapped page `0x40001000`.

RISC-V Privileged Architecture, Section 3.1.15 Machine Cause (mcause) synchronous exception priority table, and Section 3.1.16 Machine Trap Value (mtval). The priority between misaligned and page-fault paths may be implementation-defined, but the selected exception path still has to retire precise architectural trap state.

If the implementation chooses the page-fault path for this split load, the trap must be made architecturally visible. mcause should report load page fault, mepc should identify the faulting load instruction, and mtval should identify the inaccessible tail page address.

<img width="1160" height="765" alt="Image" src="https://github.com/user-attachments/assets/d100f18d-4d33-42d0-92dc-6d7a5ba17c3f" />
<img width="1281" height="499" alt="Image" src="https://github.com/user-attachments/assets/09b46674-c2c7-4637-8253-fc61e1ba540a" />


### Expected behavior

Expected Result

The hart should retire a precise exception. For the page-fault classification, mcause should be 13, mepc should identify fault_ld, and mtval should be 0x40001000.

Actual Result

The replay comparison shows the reference taking the load page fault, while the DUT keeps stale trap state:

```text
mepc different   right = 0x0000000040000008, wrong = 0x0000000040000000
mtval different  right = 0x0000000040001000, wrong = 0x0000000000000000
mcause different right = 0x000000000000000d, wrong = 0x0000000000000000
```

Here `right` is the reference model and `wrong` is the XiangShan DUT. The mismatch means the split-load tail page fault was not made visible as architectural trap state.

Specific replay CSR differences:

<img width="1048" height="278" alt="Image" src="https://github.com/user-attachments/assets/f22eabde-0df4-496c-87b7-0639995e8847" />

Trace context before the mismatch:

<img width="1110" height="353" alt="Image" src="https://github.com/user-attachments/assets/8a3cf924-7556-4bd9-9190-f30ac7ecc434" />

Difftest timeout screenshot:

<img width="613" height="50" alt="Image" src="https://github.com/user-attachments/assets/7374331e-8d47-432a-8bdc-a94097afd853" />

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
/* Sv39 maps the first supervisor page; the next page is intentionally unmapped. */
li   t0, (ROOT_PPN | SATP_MODE_SV39)
csrw satp, t0
sfence.vma

la   t0, s_page
csrw mepc, t0
mret

s_page:
  li   a0, 0x40000100
calib_ld:
  ld   t0, 4(a0)          /* calibration: same-page misaligned load */
  li   a0, 0x40000ffc
fault_ld:
  ld   t0, 0(a0)          /* split load: tail enters unmapped page */
```

### Additional context

The CSR-difference screenshot highlights the `mepc`, `mtval`, and `mcause` differences from `logs/program.latest.xiangshan.replay.log`. The timeout screenshot shows the no-forward-commit symptom before the reference is advanced for comparison. This is not just a different exception-priority choice: once the page-fault path is taken by the reference comparison, the DUT should retire a precise trap with the faulting load PC and tail-page address instead of leaving all three trap CSRs stale.

The fix should ensure that split-load tail faults are merged into the load-unit exception path as retireable architectural exceptions, without allowing internal split-access bookkeeping to leave the hart in a no-commit state.

[XS-1.zip](https://github.com/user-attachments/files/29837390/XS-1.zip)
