### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

With an `action=0` execute trigger configured, changing MIE/SIE immediately before the target instruction causes missed or spurious breakpoints. We reproduced both directions in M and HS modes; the HS cases use `medeleg[3]=1`.

| Case | Update before target | Expected | Observed on v2 and v3 |
|---|---|---|---|
| A-n0 | MIE 0→1 | Breakpoint | Target commits; `mcause=0` |
| B-n0 | MIE 1→0 | No breakpoint | `mcause=3`, `mepc=target`, `MPIE=0` |
| SA-n0 | SIE 0→1 | Breakpoint | Target commits; `scause=0` |
| SB-n0 | SIE 1→0 | No breakpoint | `scause=3`, `sepc=target`, `SPIE=0` |

We completed these experiments on October 18, 2026, covering both XS v2 and XS v3. The attachment includes four minimal reproducers, five controls, sources, ELF/images and raw logs from both versions.

The controls cover constant MIE, non-delegated HS breakpoints, and load/store triggers. Their trigger outcomes match the reference; the triggering controls retain a handler-step comparison offset, documented in the README.

### Expected behavior

The execute trigger should follow the MIE/SIE value established by preceding instructions. This is the native-trigger gating scheme used by the design; see [Debug Spec 1.0, §5.4](https://docs.riscv.org/reference/debug/_attachments/riscv-debug-specification.pdf#page=67).

### Environment

Kunminghu v2 (`2acbf327cf`) and v3 (`96c3f568f9`), Verilator + XiangShan Spike, seed 0. Full environment records and binary hashes are included in `environment.json`.

### To Reproduce

Extract the attachment and run:

```bash
XS_EMU=/path/to/v2/emu XS_REF=/path/to/riscv64-spike-so bash replay.sh
```

This runs the four main cases using the supplied images. Append `C0 HS0 LDA LDB STA` to run the controls. Each run preserves its logs and return codes.

### Additional context

The suspected cause is that the frontend applies `triggerCanRaiseBpExp` before the status write takes effect, while MIE/SIE-only writes do not cause a refetch. The sampling cycle has not been verified by waveform.

[sdtrig-reproducer.zip](https://github.com/user-attachments/files/33761015/sdtrig-reproducer.zip)
