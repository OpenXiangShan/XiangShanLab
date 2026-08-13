### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

## Describe the bug
In [xiangshan/backend/datapath/DataPath.scala 334], the integer reg-cache maintains a tag history buffer:

- `int_regcache_tag`: `Vec(48, UInt(...))`
- `int_regcache_enqPtr`: `UInt(log2Up(48).W)` → 6-bit pointer (range **0..63**)

The enqueue pointer is advanced with natural unsigned overflow:

```scala
val int_regcache_size = 48
val int_regcache_tag = RegInit(VecInit(Seq.fill(int_regcache_size)(0.U(intSchdParams.pregIdxWidth.W))))
val int_regcache_enqPtr = RegInit(0.U(log2Up(int_regcache_size).W))

int_regcache_enqPtr := int_regcache_enqPtr + PopCount(intRfWen)
for (i <- intRfWen.indices) {
  when (intRfWen(i)) {
    int_regcache_tag(int_regcache_enqPtr + PopCount(intRfWen.take(i))) := intRfWaddr(i)
  }
}
```

### What’s wrong
Because `log2Up(48)=6`, `int_regcache_enqPtr` **wraps at 64**, not 48. If  `int_regcache_enqPtr` (`int_regcache_enqPtr + offset`) can enter 48..63, the code may perform **out-of-range writes** to `int_regcache_tag(...)` whose valid indices are only 0..47.

This suspected bug has some similarities to patch commit #4642（https://github.com/OpenXiangShan/XiangShan/pull/4642/files）.

### Expected behavior

Out-of-range dynamic index writes to `Vec(48)` should not occur.

### To Reproduce

N/A —sorry for no minimal reproducer yet. The issue was found via code inspection.

### Environment

- XiangShan branch:master
- XiangShan commit id:b08943245a92c9d8fe2a191aecec23a93cfdf1f0
- XiangShan config:DefaultConfig


### Additional context

_No response_
