### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

In the CHI MSHR (`coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala`), the error-accumulation registers `denied`, `corrupt`, and the `state.w_replResp` flag are updated with the `r := r || x` pattern inside **three parallel, non-mutually-exclusive `when` blocks** (`c_resp` / `rxdat` / `rxrsp`).

Location:
File: `coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala`
Register declarations:
- `val denied = RegInit(false.B)` — line 114
- `val corrupt = RegInit(false.B)` — line 115

`denied := denied || ...` across three sibling `when` blocks (no `.elsewhen`, no merging wire):

| Line | Enclosing `when` | CHI path |
|------|------------------|----------|
| 1117 | `when (c_resp.valid)` | report (`denied := denied \|\| c_resp.bits.denied`) |
| 1160 | `when (rxdat.valid)`  | DataSepResp |
| 1178 | `when (rxdat.valid)`  | CompData |
| 1195 | `when (rxrsp.valid)`  | RespSepData |
| 1210 | `when (rxrsp.valid)`  | Comp |

The three `when (c_resp.valid)` / `when (rxdat.valid)` / `when (rxrsp.valid)` blocks are siblings at the same scope. They are **not** mutually exclusive and there is **no combinational wire** that ORs the per-channel contributions before the register update.

FIRRTL/Chisel resolves multiple `reg := ...` connections under different conditions into a single priority mux, where **each RHS reads the register's stable (old) value**.

When both `rxrsp_valid` and `rxdat_valid` are high, only the `rxrsp` branch is taken; `io_rxdat_nderr` does not participate at all. This is a priority mux, not the intended accumulation.


### Expected behavior

The `denied`, `corrupt`, and `state.w_replResp` flags are meant to be **sticky accumulators**: once any response channel reports an error for a transaction, the flag should latch to `true` and stay `true`, regardless of what the other channels report in the same or later cycles.



### Environment

detected by a static analysis tool.

### To Reproduce

detected by a static analysis tool.

### Additional context

_No response_
