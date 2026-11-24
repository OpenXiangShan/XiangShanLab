### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A Verilator run reproduces an incorrect exception PC for an Sv39 instruction fetch with `PBMT.NC` (`PBMT=1`) and execute permission disabled (`X=0`). For VA `0x80200f20`, the PTW request and response complete correctly, and the expected result is an `instruction_page_fault`. At cycle 576, the DUT reports the correct exception type through `cfVec`, but the exception slot contains `pc=0` instead of the faulting fetch VA `0x80200f20`.

Because `X=0`, the fault is raised before a normal `ICache` or `InstrUncache` fetch request is issued. However, the frontend still treats the fetch as an uncache request and overwrites the faulting PC with `uncachePc`, which remains zero because no InstrUncache request occurred. The expected behavior is to preserve and propagate the original faulting VA `0x80200f20` with the instruction page fault; `PBMT.NC` must not cause the exception path to use an invalid uncache PC.

waveform: [test_translation_constrained_random_stream_dut.fst.zip](https://github.com/user-attachments/files/31288498/test_translation_constrained_random_stream_dut.fst.zip)

### Expected behavior

The exception cfVec entry should preserve the faulting fetch PC `0x80200f20`.


### Environment

- Hardware
  - CPU: AMD EPYC 9684X
- Software
  - Operating system: AMD EPYC 9684X
  - gcc version: <!-- run `gcc --version 2>&1 | head -n 1` to get the version -->13.3.0
  - clang version: <!-- run `clang --version 2>&1 | head -n 1` to get the version, only needed when you use clang -->19.1.1
  - java version: <!-- run `java -version 2>&1 | head -n 1` to get the version -->21.0.11
  - mill version: <!-- run `mill -i --version 2>&1 | head -n 1` to get the version -->0.12.17
- Repo
  - XiangShan commit id: `89a02bdb6e58a60ae42d94861891aed917007912`

### To Reproduce

Please reproduce this issue on the `frontend-bt` branch.
build:
```
make frontend -j16
```
run:
```
TB_ENABLE_DUT_TESTS=1 \
TB_FRONTEND_SIM=verilator \
TB_TRANSLATION_RANDOM_SEED=0x5a390001 \
TB_TRANSLATION_RANDOM_COUNT=1 \
TB_TRANSLATION_RANDOM_START_ORDINAL=1 \
src/test/python/Frontend/scripts/run_pytest_with_log.sh -v \
src/test/python/Frontend/tests/py/zhaoxinran/test_translation_random_regression_dut.py::test_translation_constrained_random_stream_dut
```

### Additional context

_No response_
