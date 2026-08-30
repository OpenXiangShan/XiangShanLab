# Commit Log
- Issue: #5543
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5543
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5543
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5543
- Changed files: 2
- Additions: 11
- Deletions: 10

## Files
- `src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala
index 0578017af3e..f650d9d1575 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala
@@ -79,6 +79,14 @@ class MainBtbMeta(implicit p: Parameters) extends MainBtbBundle {
   val entries: Vec[Vec[MainBtbMetaEntry]] = Vec(NumAlignBanks, Vec(NumWay, new MainBtbMetaEntry))
 }
 
+class MainBtbAlignBankTrace(implicit p: Parameters) extends MainBtbBundle {
+  val needWrite: Bool         = Bool()
+  val setIdx:    UInt         = UInt(SetIdxLen.W)
+  val bankIdx:   UInt         = UInt(log2Ceil(NumInternalBanks).W)
+  val wayIdx:    UInt         = UInt(log2Ceil(NumWay).W)
+  val entry:     MainBtbEntry = new MainBtbEntry
+}
+
 class MainBtbTrace(implicit p: Parameters) extends MainBtbBundle {
 
   val startPc:     PrunedAddr      = PrunedAddr(VAddrBits)
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
index fabd3fdfda3..dfb070a2369 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
@@ -62,20 +62,13 @@ class MainBtbAlignBank(
 
       val req: Valid[Req] = Flipped(Valid(new Req))
     }
-    class Trace extends Bundle {
-      val needWrite: Bool         = Bool()
-      val setIdx:    UInt         = UInt(SetIdxLen.W)
-      val bankIdx:   UInt         = UInt(log2Ceil(NumInternalBanks).W)
-      val wayIdx:    UInt         = UInt(log2Ceil(NumWay).W)
-      val entry:     MainBtbEntry = new MainBtbEntry
-    }
 
     val resetDone: Bool      = Output(Bool())
     val stageCtrl: StageCtrl = Input(new StageCtrl)
 
-    val read:  Read  = new Read
-    val write: Write = new Write
-    val trace: Trace = Output(new Trace)
+    val read:  Read                  = new Read
+    val write: Write                 = new Write
+    val trace: MainBtbAlignBankTrace = Output(new MainBtbAlignBankTrace)
 
     // final s3_takenMask (mbtb + tage + sc), used to touch replacer accurately
     val s3_takenMask: Vec[Bool] = Input(Vec(NumWay, Bool()))
```
