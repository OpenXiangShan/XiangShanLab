### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

## 问题
- 向量 load 异常路径下，`isForVSnonLeafPTE` 在 LSQ 异常注入点丢失。
- 该位会进一步影响 `mtinst/htinst` 是否生成 `0x3000`。

## Chisel 链路
- 上游产生：`src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala:143`
- 丢失点：`src/main/scala/xiangshan/mem/lsqueue/LoadQueue.scala:274`
- 下游消费：`src/main/scala/xiangshan/mem/lsqueue/LoadExceptionBuffer.scala:100`
- 后端使用：`src/main/scala/xiangshan/mem/MemBlock.scala:1670`
- Trap 生成：`src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/TrapEntryMEvent.scala:122`

## RTL 结构证据
- `build/rtl/LqExceptionBuffer.sv:101` 的 `io_req_3/io_req_4` 没有 `isForVSnonLeafPTE` 输入。
- `build/rtl/LqExceptionBuffer.sv:145` 仍输出 `io_exceptionAddr_isForVSnonLeafPTE`。

## UT 复现
- 工程：`ut_risk_verilator/a2_lq_exception_ut`
- 结果：标量路径 PASS，向量路径 metadata loss YES。

## IT 复现
- 用例：`ut_risk_verilator/workloads/a2_it_vec_guest_fault_mprv.S`
- 运行：`bash ut_risk_verilator/scripts/run_case.sh a2_it_vec_guest_fault_mprv 500`
- 期望：guest fault 时 `mtinst=0x3000`
- 实际：difftest 日志显示 `mtinst right = 0x3000, wrong = 0x0`

## 影响
- 不是性能问题，而是 trap 语义错误。
- 软件可见结果是异常上下文被改写，虚拟化场景下会误导异常处理。

## 为什么无兜底
- 上游已提供该位；
- 中间结构级丢失；
- 下游无法恢复，只能消费错误值。


### Expected behavior


## 结论
- UT 最适合定位根因；
- IT 最适合证明业务可见错误；
- 本次两者都做。

## UT
- 模块：`build/rtl/LqExceptionBuffer.sv`
- 对比：标量异常路径 vs 向量异常路径
- 目标：证明字段在向量注入端口物理缺失

## IT
- 用例：`ut_risk_verilator/workloads/a2_it_vec_guest_fault_mprv.S`
- 技术路线：`MPRV + MPV + MPP=S`，让 M 模式下的数据访问走 VS+G 两级翻译
- 触发指令：`vsetvli` + `vle32.v`
- 结果判据：`cause=0x15` 且 `mtinst right=0x3000, wrong=0x0`

## 说服力
- UT 说明根因成立；
- IT 说明软件可见错误真实发生；
- 因此 A2 属于无兜底功能 bug。


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

Ut 小机房 /nfs/home/zhangyuxin/uc_agent_V0.1/XiangShan/ut_risk_verilator

### Additional context

_No response_
