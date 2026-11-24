### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A younger load that overlaps only the lower 16-byte part of an older misaligned store crossing a 16-byte boundary
can return the value of an even older store to the same bytes. This happens when the store's address is ready
early and its data late.

In the program below (`buf` is 1 KiB aligned and zero), each iteration stores `t3`, produced by six divides from
`a3`, with `sd t3, 473(s6)`, which writes bytes 473..480 and crosses 480. It then loads `lw a4, 470(s6)`, which reads
bytes 470..473; byte 473 comes from the store. In the second iteration the load returns `0x11000000`, the first
iteration's byte, instead of `0x12000000`.

DiffTest output on `df993b2527` (excerpt):

```text
[19] commit pc 000000008000004c inst 1dcb3ca3 wen 0 dst 25 data 0000000011000000 idx 00e (01)
[20] commit pc 0000000080000050 inst 1d6b2703 wen 1 dst 14 data 0000000011000000 idx 00f (01)
     a4 different at pc = 0x2e07e5a88f45, right = 0x0000000012000000, wrong = 0x0000000011000000
     a7 different at pc = 0x2e07e5a88f45, right = 0x0000000023000000, wrong = 0x0000000022000000
Core 0: ABORT at pc = 0x2e07e5a88f45
```

The pc in the last two lines is not a program address; the commit trace shows the load (`lw a4, 470(s6)`) at
`0x80000050`.

### Expected behavior


Each `lw a4, 470(s6)` returns the byte written by the `sd` of the same iteration, `(a3 & 0xff) << 24`. The sum
over the 20 iterations is `0x212000000`, and the program ends at the `li a0, 0` trap, as on NEMU.


### Environment

- Software
  - Operating system: Ubuntu 24.04.5 LTS
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
  - java version: openjdk version "17.0.20.1" 2026-08-18
  - mill version: 0.12.17
  - verilator version: 5.052
  - RISC-V toolchain for the workload: riscv-none-elf-gcc (xPack GNU RISC-V Embedded GCC) 15.2.0
- Repo
  - XiangShan commit id: `df993b2527` (kunminghu-v3 HEAD); also reproduced on `09453ff8f`
  - NEMU commit id (if difftest failed with NEMU): prebuilt `riscv64-nemu-interpreter-so` from the `ready-to-run` submodule (`4cf9983`)
  - SPIKE commit id (if difftest failed with SPIKE): n/a

### To Reproduce

1. Build the workload:
   `riscv64-unknown-elf-gcc -march=rv64gc_zicsr -mabi=lp64d -nostdlib -nostartfiles -static -T link.ld -o fwd_split.elf fwd_split.S`
   and `riscv64-unknown-elf-objcopy -O binary fwd_split.elf fwd_split.bin`. `link.ld` places `.text.init` at
   `0x80000000` and `.data` at the next 4 KiB boundary.
2. Run it with DiffTest against NEMU (command above). DiffTest stops at the load in the second iteration with the
   mismatch shown above. Without DiffTest the program ends at the trap after label `fail` (a0 = 3).

[fwd_split.bin.zip](https://github.com/user-attachments/files/33300695/fwd_split.bin.zip)

### Additional context


Bisect:
- The same-page case passes on `f46464944` (2026-05-26) and fails from `7016efa04` (2026-05-27) on. The change
  between them that touches the memory pipeline is #5984 ("make split unalign store that cross 16B bank", the fix for
  #5861), which splits every cross-16B store into a head and a tail request; before #5984 only cross-page stores were
  split.
- The cross-page variant fails from `87d03b2cc` (2026-03-24) on and passes on `4c05eeae3` (2026-03-17).
- kunminghu-v2 (`e7bab53e6`) passes both.

Cause (our reading of `NewStoreUnit.scala` and `NewStoreQueue.scala`): the head and the tail request each go to the
StoreQueue, to the store-load nuke check and to LoadQueueRAW with their own 16-byte `paddr` and `mask`.
- The head writes `vaddr`, `paddrHigh`, `byteMask` and `cross16Byte` of the StoreQueue entry, but `addrValid` is set
  only by the last request (`addrValidSet = isLastRequest && !tlbMiss`).
- Forwarding requires `addrValid` (`s1VaddrMatchVec`), so between the two requests the store is invisible to a
  younger load. The load then forwards from an older store or reads the sbuffer/DCache.
- When the head's check runs, the load has not executed yet. The tail's check compares only the next 16 bytes. So a
  load that overlaps only the lower part is never flushed.

A load that overlaps the upper part is caught by the tail's check, and a load that executes before the head is
caught by the head's check.

Possible fix: let forwarding see the head. Add an entry bit `unalignHeadValid`, set by a head request that hits the
TLB (`!isLastRequest && cross16Byte && !tlbMiss`) and cleared by the next request, dequeue and cancel. Use
`addrValid || unalignHeadValid` instead of `addrValid` in `s1VaddrMatchVec`, `s2CanForward` and the "forward select a
invalid entry" check. The load then forwards from the store, or waits for its data as for any store whose data is not
ready. An alternative is to make the tail's LoadQueueRAW/nuke check also cover the lower 16 bytes of the store.
With the patch below alone (on `09453ff8f`, DiffTest on), the program above and the cross-page variant pass. So do
the other failing pairs listed under "Variants", and the pairs whose store stays inside 16 bytes still pass. With the
patch applied together with PR #6312 and the fix for a rotation-wrap problem in the same forward stage (reported in a
separate issue), our other load/store tests give the same results as before.

<details>
<summary>Patch (on 09453ff8f)</summary>

```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
index f386d8c6b..88bd53bb6 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
@@ -150,6 +150,8 @@ class SQCtrlEntryBundle(implicit p: Parameters) extends MemBlockBundle {
   // vecInactive indicate storage a inactive vector element, it will not write to Sbuffer. written when vector split.
   val vecInactive        = Bool()
   val cross16Byte        = Bool()
+  // The head of a split cross-16B store has written vaddr, paddr and byteMask; addrValid waits for the tail.
+  val unalignHeadValid   = Bool()
   val hasException       = Bool()
   val committed          = Bool()
   val handleFinish       = Bool() // this signal is for deqPtr move, true.B indicate NC/MMIO/cbo request can deq
@@ -345,6 +347,8 @@ abstract class PhysicalStoreQueueBase(implicit p: Parameters) extends LSQModule
       //     2. Generate final forwarded data and mask
 
       val addrValidVec = WireInit(VecInit((0 until StoreQueuePhysicalSize).map(j => io.ctrlEntriesIn(j).addrValid)))
+      val fwdAddrValidVec = WireInit(VecInit((0 until StoreQueuePhysicalSize).map(j =>
+        io.ctrlEntriesIn(j).addrValid || io.ctrlEntriesIn(j).unalignHeadValid)))
       // if is cbo zero, it can forward; other cbo type's data is invalid.
       val dataValidVec = WireInit(VecInit((0 until StoreQueuePhysicalSize).map(j =>
         io.ctrlEntriesIn(j).dataValid && (isCboZero(io.dataEntriesIn(j).cboType) || !io.ctrlEntriesIn(j).isCbo))))
@@ -473,7 +477,7 @@ abstract class PhysicalStoreQueueBase(implicit p: Parameters) extends LSQModule
       val s1VaddrMatchVec  = VecInit(io.dataEntriesIn.zip(io.ctrlEntriesIn).zipWithIndex.map { case ((dataEntry, ctrlEntry), j) =>
         val storeIsCbo = ctrlEntry.isCbo
 
-        (s1Same16BMatchVec(j) || s1Next16BMatchVec(j) || (storeIsCbo && s1SameLineMatchVec(j))) && ctrlEntry.addrValid
+        (s1Same16BMatchVec(j) || s1Next16BMatchVec(j) || (storeIsCbo && s1SameLineMatchVec(j))) && fwdAddrValidVec(j)
       }).asUInt
 
       // An UnalignQueue hit identifies the second page of an older cross-page store.
@@ -554,7 +558,7 @@ abstract class PhysicalStoreQueueBase(implicit p: Parameters) extends LSQModule
       val s2SelectCtrlEntry   = RegEnable(s1SelectCtrlEntry, s1Valid)
       val s2DataInValid       = RegEnable(s1DataInvalid, s1Valid)
       val s2StrictMdpWait     = RegEnable(s1StrictMdpWait, s1Valid)
-      val s2CanForward       = RegEnable((s1AgeMaskLow | s1AgeMaskHigh) & s1OverlapMask & addrValidVec.asUInt, s1Valid)
+      val s2CanForward       = RegEnable((s1AgeMaskLow | s1AgeMaskHigh) & s1OverlapMask & fwdAddrValidVec.asUInt, s1Valid)
       val s2SelectOH         = RegEnable(s1SelectOH, s1Valid)
       val s2LoadMaskEnd      = RegEnable(UIntToMask(MemorySize.CalculateSelectMask(s1LoadStart, s1LoadEnd), VLENB), s1Valid)
       val s2DataInvalidSqIdx = RegEnable(s1DataInvalidSqIdx, s1Valid)
@@ -579,7 +583,8 @@ abstract class PhysicalStoreQueueBase(implicit p: Parameters) extends LSQModule
       val s2PreciseMdpWait     = s2MdpHitInPhysical && !io.ctrlEntriesIn(s2WaitSqIdx.value).addrValid
       val s2NeedPreciseMdpWait = s2PreciseMdpWait || s2MdpHitOutOfRange
       // debug
-      XSError(s1SelectOH.orR && !s1SelectCtrlEntry.addrValid && s1Valid, "forward select a invalid entry!\n")
+      XSError(s1SelectOH.orR && !s1SelectCtrlEntry.addrValid && !s1SelectCtrlEntry.unalignHeadValid && s1Valid,
+        "forward select a invalid entry!\n")
       /*================================================== Stage 2 ===================================================*/
 
       // Data Generation Process:
@@ -1682,6 +1687,9 @@ class PhysicalStoreQueue(implicit p: Parameters) extends PhysicalStoreQueueBase
     val cross16ByteSet = io.fromStoreUnit.storeAddrIn.zipWithIndex.map { case (port, j) =>
       port.bits.isUnalign && port.bits.cross16Byte && staValidSetVec(j)
     }.reduce(_ || _)
+    val unalignHeadSet = io.fromStoreUnit.storeAddrIn.zipWithIndex.map { case (port, j) =>
+      !port.bits.isLastRequest && port.bits.cross16Byte && !port.bits.tlbMiss && staValidSetVec(j)
+    }.reduce(_ || _)
     val cboSetVec = io.fromStoreUnit.storeAddrIn.zipWithIndex.map { case (port, j) =>
       LSUOpType.isCboAll(port.bits.uop.fuOpType) && staValidSetVec(j)
     }
@@ -1696,6 +1704,12 @@ class PhysicalStoreQueue(implicit p: Parameters) extends PhysicalStoreQueueBase
       ctrlEntries(i).addrValid    := false.B
     }
 
+    when(staSetValid) {
+      ctrlEntries(i).unalignHeadValid := unalignHeadSet
+    }.elsewhen(deqCancel || needCancel(i)) {
+      ctrlEntries(i).unalignHeadValid := false.B
+    }
+
     when(staSetValid) { // no need to clean when deq or cancel, because it will be set when set addrValid
       ctrlEntries(i).cross16Byte  := cross16ByteSet
       ctrlEntries(i).isCbo        := isCboSet
```

</details>
