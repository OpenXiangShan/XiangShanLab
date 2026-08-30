### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

In XiangShan 2-core RTL simulation, an S-mode store to a PMP-protected region does not raise the expected Store/AMO Access Fault when the matched PMP entry is configured with `L=1` and `W=0`.

The issue has been reproduced in both address-matching variants:

- B7: NAPOT, `L=1`, `R=1 W=0 X=0`
- B7b: TOR, `L=1`, `R=1 W=0 X=0`

Observed behavior:

- XiangShan 1-core takes the expected fault path.
- XiangShan 2-core Core 1 takes the expected fault path.
- XiangShan 2-core Core 0 takes a non-fault path.
- NEMU-dual DiffTest reports zero divergence.
- Spike returns `mcause=7`, matching the expected Store/AMO Access Fault.

Therefore, the issue currently appears to be specific to the XiangShan 2-core Core 0 path under `L=1` locked PMP write protection.

## Spec reference

For S-mode, if the matched PMP entry has `W=0`, a store to that region should be denied and should raise a Store/AMO Access Fault (`mcause=7`). This rule should not be relaxed by setting `L=1`.

The `L=1` bit additionally locks the PMP entry and makes the entry enforced for M-mode as well. Therefore, `L=1` must not allow an S-mode store to bypass the `W=0` restriction.

Relevant RISC-V Privileged Spec text, §3.7.1:

> If PMP entry i is locked, writes to `pmpicfg` and `pmpaddrj` are ignored.
> Additionally, if `PMP_L` is set, the entry is also enforced in machine mode.

## Key observation

The same PoC behaves correctly on XiangShan 1-core, Spike, Rocket, and BOOM, but fails on XiangShan 2-core Core 0.

The stable bug signature is the exit path:

- Expected: Store/AMO Access Fault path, `mcause=7`
- Observed on XiangShan 2-core Core 0: non-fault path followed by successful exit
- NEMU-dual DiffTest: zero divergence

The GOODTRAP PC of Core 0 is not a stable signature, because it changes across runs due to multi-core scheduling.

### Expected behavior

An S-mode store to a PMP region whose matched PMP entry has `W=0` should raise a Store/AMO Access Fault:

- `mcause = 7`
- control should transfer to the trap handler
- the store should not reach the following non-fault `ecall` path

Expected behavior for the attached PoCs:

| Platform | Expected result |
|---|---|
| Spike | `mcause=7`, Store/AMO Access Fault |
| XiangShan 1-core | fault path |
| XiangShan 2-core Core 0 | fault path |
| XiangShan 2-core Core 1 | fault path |
| NEMU-dual DiffTest | should report divergence if RTL takes a non-fault path while the reference takes the fault path |

### Environment

Please see the attached archive

[bug-report.tar.gz](https://github.com/user-attachments/files/29705349/bug-report.tar.gz)

### To Reproduce

Please see the attached archive 

[xs2_locked_pmp_poc.zip](https://github.com/user-attachments/files/29704933/xs2_locked_pmp_poc.zip)

, which contains:

- `poc_b7_napot_L1_secret_write.S`
- `poc_b7b_tor_opensbi.S`
- `link_poc.ld`
- `pmp_common.inc`
- prebuilt images: `poc_b7.img`, `poc_b7b.img`
- raw logs: `xs2_b7_nemu_dual.log`, `xs2_b7b_nemu_dual.log`, `xs1_b7_control.log`, `spike_b7.log`, `spike_b7b.log`
- `result.json`

./build_2core/emu -i poc_b7.img \
    --diff ready-to-run/riscv64-nemu-interpreter-dual-so \
    -I 50000

./build/emu -i poc_b7.img \
    --diff ready-to-run/riscv64-nemu-interpreter-so \
    -I 50000

spike --isa=rv64gc poc_b7.elf

### Additional context

### Relation to #6139

I noticed #6139, but this report appears to describe a different PMP/store issue.

Issue #6139 reports a `mtval/tval` mismatch where both XiangShan and NEMU raise `mcause=7`. In contrast, this issue reports that XiangShan 2-core Core 0 does not raise the expected Store/AMO Access Fault at all for an S-mode store to an `L=1, W=0` locked PMP region. NEMU-dual DiffTest reports zero divergence for this case.

Therefore, #6139 is about fault-address reporting after a store access fault, while this issue is about missing PMP fault generation in the 2-core locked-PMP path.

## Possible root-cause direction

The issue appears to be specific to the 2-core `L=1` PMP enforcement path. The single-core configuration and the `L=0` control case behave correctly.

A possible direction to inspect is whether the effective privilege and PMP permission context used by the store path is correctly selected for Core 0 in the 2-core configuration.

This is only a preliminary direction, not a confirmed root cause.

## AI assistance

This report was prepared with AI assistance after the experiments were completed. All reported results are from real RTL simulation logs and attached PoC files.

Discovered by an ISA-contract test that checks PMP postconditions against Spike.
