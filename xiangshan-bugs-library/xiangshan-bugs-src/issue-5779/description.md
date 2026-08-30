### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

When executing `HFENCE.GVMA zero, rs2` with rs2 ≠ x0, the RISC-V privileged specification (Section 5.3.2) requires:

> "When rs2 ≠ x0, bits XLEN-1:VMIDMAX of the value held in rs2 are reserved for future standard use. Until their use is defined by a standard extension, they should be zeroed by software and **ignored by current implementations**."

For XiangShan, the VMID length is 14 (`VmidLength` / `MMUVmidLen`), so bits 63:14 of rs2 should be ignored, and only bits 13:0 should participate in the fence operation.

**However**, `SfenceBundle.id` is defined as 16 bits (`AsidLength = 16`) to be shared between ASID and VMID use cases. In the `HFENCE.GVMA` path, `Fence.scala` forwards rs2[15:0] into this field without masking it down to the effective VMID width. Meanwhile, XiangShan's MMU-side VMID state uses 14-bit effective semantics (`VmidLength = MMUVmidLen = 14`). As a result, the reserved bits rs2[15:14] incorrectly participate in HFENCE.GVMA VMID match / hash logic instead of being ignored.

1. **SfenceBundle.id is 16 bits (sized for ASID, not VMID):**

```scala
// src/main/scala/xiangshan/Bundle.scala
class SfenceBundle(implicit p: Parameters) extends XSBundle {
  val bits = new Bundle {
    val id = UInt((AsidLength).W) // asid or vmid   ← AsidLength = 16
  }
}

// src/main/scala/xiangshan/Parameters.scala
AsidLength: Int = 16,
VmidLength: Int = 14,   // ← VMID is only 14 bits
```

**2. Fence.scala assigns full rs2 to 16-bit id (bits 63:16 truncated, but bits 15:14 preserved):**

```scala
// src/main/scala/xiangshan/backend/fu/Fence.scala:74
sfence.bits.id := RegEnable(io.in.bits.data.src(1), io.in.fire)
// src(1) is 64-bit → Chisel truncates to 16 bits → id = rs2[15:0]
```

`HFENCE.GVMA` therefore preserves `rs2[15:14]` inside `sfence.bits.id`, even though those bits are reserved and should be ignored by the implementation.

**3. TLBStorage: 14-bit vmid compared with 16-bit id:**

```scala
// src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:274
// HFENCE.GVMA with specific VMID (rs2 ≠ x0):
v.zipWithIndex.map { case (a, i) =>
  a := a && !(entries(i).s2xlate =/= noS2xlate && entries(i).vmid === sfence.bits.id)
}
```

**4. PageTableCache: same issue in both XORFold hash and direct comparison:**

```scala
// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:1203-1208
val l0hashVmid = XORFold(sfence_dup(0).bits.id, l2tlbParams.hashAsidWidth) // 16-bit input
val l2vmidhit = VecInit(l2vmids.map(_.getOrElse(0.U) === sfence_dup(2).bits.id)).asUInt
```

### Expected behavior

`HFENCE.GVMA zero, rs2` should flush TLB and PageTableCache entries matching the VMID in rs2[13:0], **regardless of the values in rs2[15:14]** (and rs2[63:16], which are already correctly truncated).
In normal software usage, `rs2[15:14]` will usually be zero. However, the specification still requires current implementations to ignore these bits, so correctness must not depend on software always keeping them zero. The current implementation violates this by allowing these bits to affect correctness.

### Environment

  - XiangShan branch: master(kunminghu-v2)


### To Reproduce

This issue was identified through a code audit. I'm not sure whether it is actually a bug, We would appreciate it if you could evaluate its significance and take it into consideration.

### Additional context

_No response_
