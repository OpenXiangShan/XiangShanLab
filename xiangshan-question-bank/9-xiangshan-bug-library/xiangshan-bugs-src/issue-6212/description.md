### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

The supplied PoC triggers an instruction guest-page fault during the implicit VS root-PTE read. The faulting guest physical address is the actual root-PTE slot address `0x40000008`, so the expected `mtval2` is `0x10000002`. The tested DUT reports the page-table page base `0x10000000` instead.

Affected version: `v3.2.2-alpha-1931-g96c3f568f` (`96c3f568f943a096ffd3d712dc6f462ac4b1ba33`).

The difference is one PTE slot, not just formatting. The VS entry VA is `0x40000000`, the VS root GPA is `0x40000000`, and the root index used by this access is `1`, so the implicit PTE read is at `0x40000000 + 1 * 8 = 0x40000008`.

RISC-V Specification Requirement

RISC-V Privileged Architecture, Section 15.4.4 Machine Second Trap Value (mtval2), with guest-page-fault GPA semantics mirrored from Section 15.2.8 htval and two-stage translation behavior in Section 15.5. For guest-page faults during implicit VS-stage page-table accesses, the reported value identifies the guest physical address of the faulting implicit memory access, shifted right by two.

For this implicit VS-stage PTE access, mcause should be instruction guest page fault, mepc and mtval should identify the faulting guest virtual fetch, and mtval2 should identify the precise PTE GPA shifted right by two.

<img width="1289" height="584" alt="Image" src="https://github.com/user-attachments/assets/884d3381-f515-43c1-8d34-4522b3a987ef" />
<img width="1324" height="402" alt="Image" src="https://github.com/user-attachments/assets/f4a4ffc4-5894-45fb-813a-3c53b4954a24" />

### Expected behavior

Expected Result

mcause = 20, mepc = 0x40000000, mtval = 0x40000000, and mtval2 = 0x10000002.

Actual Result

The replay reports:

```text
mtval2 different right = 0x0000000010000002, wrong = 0x0000000010000000
```

Here `right` is the reference model and `wrong` is the XiangShan DUT. The DUT-only run also reaches the self-check path that detects `mtval2 = 0x10000000`.

Trace context before the mismatch:

<img width="1074" height="378" alt="Image" src="https://github.com/user-attachments/assets/2bdc74ab-27ba-4d25-9601-ff0e25093d91" />

Replay trace screenshot:

<img width="843" height="72" alt="Image" src="https://github.com/user-attachments/assets/2aff520b-fd6e-4f2a-b8da-21755ba7ad4a" />

DUT-only self-check screenshot:

<img width="925" height="97" alt="Image" src="https://github.com/user-attachments/assets/2b700929-8437-4240-b92a-6d94e19b5fa7" />

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
.equ GPA_VS_ROOT,      0x40000000
.equ ACTUAL_PTE_GPA,   (GPA_VS_ROOT + 8)
.equ EXPECTED_MTVAL2,  (ACTUAL_PTE_GPA >> 2)

/* Prove the precise mtval2 value is representable, then enter VS translation. */
li   t0, EXPECTED_MTVAL2
csrw mtval2, t0
csrr t1, mtval2
bne  t1, t0, mtval2_warl_inconclusive

/* First VS fetch at 0x40000000 faults during root-PTE read at GPA 0x40000008. */
mret

trap_handler:
  csrr t3, mtval2
  li   t4, EXPECTED_MTVAL2
  bne  t3, t4, wrong_mtval2
```

### Additional context

The first screenshot shows the replay mismatch on `mtval2`. The second screenshot is the DUT-only self-check reaching `HIT GOOD TRAP`, which means the test observed the imprecise page-base value directly on XiangShan. Reporting the page base loses the precise PTE slot that faulted.

The fix should propagate the precise guest physical address of the faulting implicit VS-stage PTE access into `mtval2`, without replacing the PTE-slot address used by hypervisor policy code with the page-table page base.

[XS-5.zip](https://github.com/user-attachments/files/29838089/XS-5.zip)
