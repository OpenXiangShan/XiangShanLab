### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

Two adjacent `csrr t6, tdata2` instructions cause the first `csrr` to commit `t6 = 0x3f8891a339acd78f` instead of the `tdata2` state `0`, and the hart aborts at a non-program PC. One `csrr t6, tdata2` reaches `HIT GOOD TRAP`, and the same shape with `tdata1` also reaches `HIT GOOD TRAP`.

It looks introduced by https://github.com/OpenXiangShan/XiangShan/commit/429980ea228c481384134abcd99a02f1ceae7440, which marks these CSRR reads as out of order. The CSR wrapper delays the result by three cycles and separately delays `pdest` and `robIdx` captured at `io.in.fire`, so the first read's result can be paired with another read's destination:

https://github.com/OpenXiangShan/XiangShan/blob/429980ea228c481384134abcd99a02f1ceae7440/src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannel/PseudoDecodeChannel.scala#L160-L174

https://github.com/OpenXiangShan/XiangShan/blob/429980ea228c481384134abcd99a02f1ceae7440/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala#L330-L365

### Expected behavior

The program should reach `HIT GOOD TRAP` at `0x80000134` with `t6 = 0`.


### Environment

- DefaultConfig
- Commit `429980ea228c481384134abcd99a02f1ceae7440`.


### To Reproduce

```asm
#include "encoding.h"

.global do_init
do_init:
  csrr t6, tdata2
  csrr t6, tdata2
  li a0, 0
  .word 0x0000006b

failure:
  li a0, 1
  .word 0x0000006b
```

### Additional context

_No response_
