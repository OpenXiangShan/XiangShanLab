# Commit Log
- Issue: #5295
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5295
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5295
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5295
- Changed files: 4
- Additions: 94
- Deletions: 31

## Files
- `src/main/scala/utils/AddrField.scala`
- `src/main/scala/xiangshan/frontend/bpu/Helpers.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala`

## Diff
```diff
diff --git a/src/main/scala/utils/AddrField.scala b/src/main/scala/utils/AddrField.scala
index 990f3736b2e..2fd1b9311d6 100644
--- a/src/main/scala/utils/AddrField.scala
+++ b/src/main/scala/utils/AddrField.scala
@@ -15,6 +15,9 @@
 
 package utils
 
+import chisel3._
+import xiangshan.frontend.PrunedAddr // TODO: move this to utility?
+
 /** Utility to print fields(i.e. tag, setIdx, offset) extracted from an address
  *
  * @example {{{
@@ -70,8 +73,11 @@ class AddrField(
       else
         f" $endString${"." * ((fieldLength - startString.length - endString.length) max 1)}$startString "
 
+    def formatList(indent: Int = 0): String =
+      String.format(s"%s%${maxNameLength}s: [%d:%d]", " " * indent, name, end, start)
+
     override def toString: String =
-      String.format(s"%${maxNameLength}s: [%d:%d]", name, end, start)
+      s"$name (addr[$end:$start])"
   }
 
   private var currentStart = 0
@@ -124,8 +130,65 @@ class AddrField(
   def show(indent: Int = 0): Unit =
     println(format(indent).mkString("\n"))
 
-  def showList(): Unit =
-    (fieldInstances ++ extraFieldInstances).foreach(println)
+  def formatList(indent: Int = 0): Seq[String] = {
+    (fieldInstances ++ extraFieldInstances).map(_.formatList(indent))
+  }
+
+  def showList(indent: Int = 0): Unit =
+    println(formatList(indent).mkString("\n"))
+
+  private def getField(name: String): Field =
+    (fieldInstances ++ extraFieldInstances).find(_.name == name).get
+
+  def getStart(name: String): Int =
+    getField(name).start
+
+  def getEnd(name: String): Int =
+    getField(name).end
+
+  def getWidth(name: String): Int =
+    getField(name).width
+
+  def extract(name: String, addr: UInt): UInt = {
+    val field = getField(name)
+    addr(field.end, field.start)
+  }
+
+  def extract(name: String, addr: PrunedAddr): UInt = {
+    val field = getField(name)
+    addr(field.end, field.start)
+  }
+
+  /** extract field from another field
+   *
+   * @example {{{
+   *   val fields = ...
+   *   fields.show()
+   *   // 50| 49..31 | 30..15 | 14...7 | 6.............5 | ...........4 | 3.........0 |
+   *   //   | unused |    tag | setIdx | internalBankIdx | alignBankIdx | alignOffset |
+   *   //                | 20....................................................1 |
+   *   //                |                                                  target |
+   *
+   *   val target = UInt(20.W)
+   *
+   *   val alignBankIdx = fields.extractFrom("target", "alignBankIdx", target)
+   *   // alignBankIdx = target(3, 3)
+   *   val setIdx = fields.extractFrom("target", "setIdx", target)
+   *   // setIdx = target(13, 6)
+   *
+   *   val tag = fields.extractFrom("target", "tag", target)
+   *   // Requirement Failed: cannot get tag (addr[30:15]) from target (addr[20:1])
+   * }}}
+   */
+  def extractFrom(thatName: String, name: String, that: UInt): UInt = {
+    val thatField = getField(thatName)
+    val field = getField(name)
+    require(
+      field.start >= thatField.start && field.end <= thatField.end,
+      s"cannot get $field from $thatField"
+    )
+    that(field.end - thatField.start, field.start - thatField.start)
+  }
 }
 
 object AddrField {
diff --git a/src/main/scala/xiangshan/frontend/bpu/Helpers.scala b/src/main/scala/xiangshan/frontend/bpu/Helpers.scala
index eab6e5e57f6..4303e4cdf1d 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Helpers.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Helpers.scala
@@ -74,7 +74,6 @@ trait HalfAlignHelper extends HasBpuParameters {
 
   def getFtqOffset(startVAddr: PrunedAddr, position: UInt): UInt = {
     // given a 5-bit position, calculate the ftqOffset
-    // TODO: select from two 4-bit position? (startVAddr -> mbtb & startVAddr+32 -> mbtb)
     require(
       position.getWidth == CfiPositionWidth,
       s"position width should be $CfiPositionWidth, but got ${position.getWidth}"
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
index 6aab4816047..ff66d604e3d 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
@@ -17,6 +17,7 @@ package xiangshan.frontend.bpu.mbtb
 
 import chisel3._
 import chisel3.util._
+import utils.AddrField
 import xiangshan.HasXSParameter
 import xiangshan.frontend.PrunedAddr
 import xiangshan.frontend.bpu.CrossPageHelper
@@ -25,32 +26,47 @@ import xiangshan.frontend.bpu.TargetFixHelper
 
 trait Helpers extends HasMainBtbParameters
     with HasXSParameter with TargetFixHelper with HalfAlignHelper with CrossPageHelper {
+
+  val addrFields = AddrField(
+    Seq(
+      ("alignOffset", FetchBlockAlignWidth),
+      ("alignBankIdx", AlignBankIdxLen),
+      ("internalBankIdx", InternalBankIdxLen),
+      ("setIdx", SetIdxLen),
+      ("tag", TagWidth)
+    ),
+    maxWidth = Option(VAddrBits),
+    extraFields = Seq(
+      ("replacerSetIdx", FetchBlockSizeWidth, SetIdxLen),
+      ("targetLower", instOffsetBits, TargetWidth),
+      ("position", instOffsetBits, FetchBlockAlignWidth),
+      ("cfiPosition", instOffsetBits, FetchBlockSizeWidth)
+    )
+  )
+
   def getSetIndex(pc: PrunedAddr): UInt =
-    pc(SetIdxLen + InternalBankIdxLen + FetchBlockSizeWidth - 1, InternalBankIdxLen + FetchBlockSizeWidth)
+    addrFields.extract("setIdx", pc)
 
   def getReplacerSetIndex(pc: PrunedAddr): UInt =
-    pc(SetIdxLen + FetchBlockSizeWidth - 1, FetchBlockSizeWidth)
+    addrFields.extract("replacerSetIdx", pc)
 
   def getAlignBankIndex(pc: PrunedAddr): UInt =
-    pc(FetchBlockSizeWidth - 1, FetchBlockAlignWidth)
+    addrFields.extract("alignBankIdx", pc)
 
   def getAlignBankIndexFromPosition(cfiPosition: UInt): UInt =
-    cfiPosition(CfiPositionWidth - 1, CfiPositionWidth - AlignBankIdxLen)
+    addrFields.extractFrom("cfiPosition", "alignBankIdx", cfiPosition)
 
   def getTargetUpper(pc: PrunedAddr): UInt =
-    pc(VAddrBits - 1, TargetWidth + instOffsetBits)
+    pc(pc.length - 1, addrFields.getEnd("targetLower") + 1)
 
   def getTargetLowerBits(target: PrunedAddr): UInt =
-    target(TargetWidth + instOffsetBits - 1, instOffsetBits)
+    addrFields.extract("targetLower", target)
 
   def getInternalBankIndex(pc: PrunedAddr): UInt =
-    pc(InternalBankIdxLen + FetchBlockSizeWidth - 1, FetchBlockSizeWidth)
+    addrFields.extract("internalBankIdx", pc)
 
   def getTag(pc: PrunedAddr): UInt =
-    pc(
-      TagWidth + InternalBankIdxLen + SetIdxLen + FetchBlockSizeWidth - 1,
-      InternalBankIdxLen + SetIdxLen + FetchBlockSizeWidth
-    )
+    addrFields.extract("tag", pc)
 
   // detect multi-hit, return a mask indicating which way has multi-hit
   def detectMultiHit(hitMask: IndexedSeq[Bool], position: IndexedSeq[UInt]): UInt = {
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
index dfb7a2c3cf6..ce7caddcee7 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtb.scala
@@ -20,7 +20,6 @@ import chisel3.util._
 import org.chipsalliance.cde.config.Parameters
 import utility.XSPerfAccumulate
 import utility.XSPerfHistogram
-import utils.AddrField
 import utils.VecRotate
 import xiangshan.frontend.bpu.BasePredictor
 import xiangshan.frontend.bpu.BasePredictorIO
@@ -39,21 +38,7 @@ class MainBtb(implicit p: Parameters) extends BasePredictor with HasMainBtbParam
   println(f"MainBtb:")
   println(f"  Size(set, way, align, internal): $NumSets * $NumWay * $NumAlignBanks * $NumInternalBanks = $NumEntries")
   println(f"  Address fields:")
-  AddrField(
-    Seq(
-      ("alignOffset", FetchBlockAlignWidth),
-      ("alignBankIdx", AlignBankIdxLen),
-      ("internalBankIdx", InternalBankIdxLen),
-      ("setIdx", SetIdxLen),
-      ("tag", TagWidth)
-    ),
-    maxWidth = Option(VAddrBits),
-    extraFields = Seq(
-      ("replacerSetIdx", FetchBlockAlignWidth, SetIdxLen),
-      ("targetLower", instOffsetBits, TargetWidth),
-      ("position", instOffsetBits, FetchBlockAlignWidth)
-    )
-  ).show(indent = 4)
+  addrFields.show(indent = 4)
 
   /* *** submodules *** */
   private val alignBanks = Seq.tabulate(NumAlignBanks)(alignIdx => Module(new MainBtbAlignBank(alignIdx)))
```
