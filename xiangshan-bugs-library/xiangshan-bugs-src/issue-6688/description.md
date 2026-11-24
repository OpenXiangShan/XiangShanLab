### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

`ColdDownThreshold` is a parameter that sets the cold-down threshold of the load replay logic, and it can be optimized through `constantin` for performance:

```scala
// src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:654-663
val ColdDownCycles = 16
val coldCounter = RegInit(VecInit(List.fill(LoadPipelineWidth)(0.U(log2Up(ColdDownCycles).W))))
val ColdDownThreshold = Wire(UInt(log2Up(ColdDownCycles).W))
ColdDownThreshold := Constantin.createRecord(s"ColdDownThreshold_${p(XSCoreParamsKey).HartId}", initValue = 12)
assert(ColdDownCycles.U > ColdDownThreshold, "ColdDownCycles must great than ColdDownThreshold!")

def replayCanFire(i: Int) = coldCounter(i) >= 0.U && coldCounter(i) < ColdDownThreshold
def coldDownNow(i: Int)   = coldCounter(i) >= ColdDownThreshold
```
However, setting this parameter to `0` makes `replayCanFire` always false, so a load that needs to be
re-executed is retired without being replayed and commits the data of its first attempt.

The existing assertion `assert(ColdDownCycles.U > ColdDownThreshold)` does not forbid this value.

### Expected behavior

`ColdDownThreshold = 0` is not a legal value and should be forbidden by an assertion to avoid a
failure when simulation.

### Environment

- Repo
  - XiangShan commit id: `09453ff8`
  - NEMU commit id: the `ready-to-run` interpreter pinned by the repo (submodule `4cf99835`, NEMU `513786c4`)


### To Reproduce

Set `ColdDownThreshold` to 0 and run the following trigger. The core loop of the trigger around the failing
load is:

```asm
    la      t5, test_memory   # zero initialized region inside the loaded image (t5 = 0x80000ab0)
    fld     f0, 0(t5)            # pc 0x80000078: identical load, retires correctly
    fld     f1, 8(t5)
    fld     f2, 16(t5)
    fmaxm.d f2, f0, f3
    li      s1, 28        
1:
    la      t5, test_memory
    fld     f0, 0(t5)            # pc 0x80000092: <- difftest mismatch here
    ...
    bnez s1, 1b
```
The same `fld ft0, 0(t5)` also executes once before this loop, at pc 0x80000078, and retires correctly. The
mismatch appears on the first iteration of the loop, whose body also performs a store.

```
[INFO] constant updated: ColdDownThreshold_0 = 0
    ft0 different at pc = 0x0080000092, right = 0x0000000000000000, wrong = 0x15602b939093dce5
Core 0: ABORT at pc = 0x80000092
Core-0 instrCnt = 88, cycleCnt = 24079, IPC = 0.003655
```

### Additional context

_No response_
