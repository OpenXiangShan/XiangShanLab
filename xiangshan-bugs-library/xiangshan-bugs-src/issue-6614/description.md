### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

[FOUND_REPRODUCED_BUG-LSU01.md](https://github.com/user-attachments/files/32458797/FOUND_REPRODUCED_BUG-LSU01.md)

### Expected behavior

| 项目 | 期望 | 实际 DUT |
| --- | --- | --- |
| 最终物理检查 | `HLVX.WU` 对最终物理地址使用 **execute** 权限（PMA/PMP X）；缺 X 必须 fault | 发往 DTLB 的 `cmd := TlbCmd.read`；PMA/PMP 只在 `isExec` 时抬 `resp.instr`；load 路径只看 `pmp.ld` |
| 异常类型 | 即使检查的是 X，仍报 **load** access fault（`mcause=5`），不是 instruction access fault | 无 trap：`trap_count=0 mcause=0` |
| 数据 | 不得写回目标字节 | DUT 读到 `hlvx=0xc33ca55a`（页内 `5a a5 3c c3` 的 LE word） |
| DIFFTEST | 本 pin NEMU 与 DUT 同路径：都不 trap、同一 GPR | `--diff` 开着，双方一致 → `HIT GOOD TRAP`。这只证明 REF 也错，**不能**当通过 |
| 负控（源码） | 页表级 HLVX 已按 `perm.x` 检查（`ldPermFail := Mux(hlvx, perm.x, …)`） | 本用例 bare translation、最终 PMA 才是触发点；不是页表 X 漏检 |

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

[BUG-LSU-01.zip](https://github.com/user-attachments/files/32458806/BUG-LSU-01.zip)

### Additional context

_No response_
