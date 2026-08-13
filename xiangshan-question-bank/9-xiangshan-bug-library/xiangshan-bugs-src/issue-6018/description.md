### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

### Bug Location
`XSCache/src/main/scala/coupledL2/prefetch/TemporalPrefetch.scala`,  Line 316

### Summary
When the `trainOnVaddr` is true, the Temporal Prefetcher silently
truncates the upper 8 bits of the virtual trigger tag before storing it into
`tpMetaTable`. Because the tag comparison on the read path zero-extends the stored
value back to 40 bits, any trigger whose virtual address has a non-zero bit in
[49:42] will never produce a tag match again. Effectively, the prefetcher trains
but cannot reuse any pattern collected from those addresses.

### Root cause
The `tpMetaEntry.triggerTag` field is sized assuming a *physical* address split:

```scala
class tpMetaEntry(implicit p: Parameters) extends TPBundle {
  val valid      = Bool()
  val triggerTag = UInt((fullAddressBits - blockOffBits - tpTableSetBits).W)
}
```

For the typical configuration (fullAddressBits = 48, blockOffBits = 6, tpTableSetBits = 10), this gives 32 bits.

However, parseVaddr does not mirror parsePaddr. It only strips the set bits, not the cacheline offset:

```scala
def parseVaddr(x: UInt): (UInt, UInt) = {
  (x(x.getWidth - 1, tpTableSetBits),         // tag  = 50 - 10 = 40 bits
   x(tpTableSetBits - 1, 0))                  // set  = 10 bits
}
def parsePaddr(x: UInt): (UInt, UInt) = {
  (x(x.getWidth - 1, tpTableSetBits + blockOffBits),  // tag = 48 - 16 = 32 bits
   x(tpTableSetBits + blockOffBits - 1, blockOffBits))
}
```

So with vaddrBits = 50 (Sv48 + H-extension, sv48x4 GPA), vtag is 40 bits while the storage field is 32 bits. The write side performs an implicit truncation of the upper 8 bits:

```scala
tpMeta_w_bits.triggerTag := Mux(trainOnVaddr.orR, write_record_vtag,    // 40 bits
```

### Expected behavior

`parseVaddr` should preserve the same semantics as `parsePaddr` and must not truncate the high-order bits.



### Environment

Detected by a static program analysis tool.

### To Reproduce

Detected by a static program analysis tool.

### Additional context

_No response_
