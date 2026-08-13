# Commit Log
- Issue: #5302
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5302
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5302
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5302
- Changed files: 4
- Additions: 28
- Deletions: 26

## Files
- `src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala`
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala
index 6c2d4229759..aed4eac09d1 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/Bundles.scala
@@ -56,10 +56,7 @@ class MainBtbMetaEntry(implicit p: Parameters) extends MainBtbBundle {
   val position:  UInt            = UInt(CfiPositionWidth.W)
   val attribute: BranchAttribute = new BranchAttribute
 
-  def hit(branch: BranchInfo): Bool =
-    rawHit &&
-      position === branch.cfiPosition &&
-      attribute === branch.attribute
+  def hit(branch: BranchInfo): Bool = rawHit && position === branch.cfiPosition
 }
 
 class MainBtbMeta(implicit p: Parameters) extends MainBtbBundle {
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
index 6aab4816047..ae701af5ac9 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
@@ -64,7 +64,7 @@ trait Helpers extends HasMainBtbParameters
       val bothHit      = hitMask(i) && hitMask(j)
       val samePosition = position(i) === position(j)
       when(bothHit && samePosition) {
-        multiHitMask(i) := true.B
+        multiHitMask(j) := true.B
       }
     }
     PriorityEncoderOH(multiHitMask.asUInt)
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
index 71fd0819b1d..dfcec5f1c9e 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala
@@ -177,16 +177,21 @@ class MainBtbAlignBank(
   private val t1_internalBankMask = UIntToOH(t1_internalBankIdx, NumInternalBanks)
   private val t1_alignBankIdx     = getAlignBankIndex(t1_startVAddr)
 
-  // Decide wayMask:
-  // If not hit, use mbtb replacer's victim way
-  // If hit, use hit wayMask, but do write only if:
-  //   1. it's an OtherIndirect-type branch (to update target and play the role of Ittage's base table)
-  //   No other cases for now
-  private val t1_hitMask = VecInit(io.write.req.bits.meta.map(_.hit(t1_branchInfo)))
-  private val t1_hit     = t1_hitMask.reduce(_ || _)
-
-  private val t1_needWrite = !t1_hit || t1_branchInfo.attribute.isOtherIndirect
-  private val t1_wayMask   = Mux(t1_hit, t1_hitMask.asUInt, replacer.io.victim.wayMask)
+  // NOTE: the original rawHit result can be multi-hit (i.e. multiple rawHit && position match), so PriorityEncoderOH
+  private val t1_hitMask = PriorityEncoderOH(VecInit(io.write.req.bits.meta.map(_.hit(t1_branchInfo))).asUInt)
+  private val t1_hit     = t1_hitMask.orR
+
+  // Decide whether to write and which wayMask to write:
+  private val t1_needWrite =
+    // If not hit, always write a new entry, use mbtb replacer's victim way
+    !t1_hit ||
+      // If hit, but do write only if:
+      //   1. it's an OtherIndirect-type branch (to update target and play the role of Ittage's base table)
+      t1_branchInfo.attribute.isOtherIndirect ||
+      //   2. attribute changed, probably indicating a software self-modification
+      !(t1_branchInfo.attribute === Mux1H(t1_hitMask, io.write.req.bits.meta.map(_.attribute)))
+  // Use hit wayMask if hit, else use replacer's victim way
+  private val t1_wayMask = Mux(t1_hit, t1_hitMask, replacer.io.victim.wayMask)
 
   private val t1_entry = Wire(new MainBtbEntry)
   t1_entry.valid           := true.B
@@ -207,8 +212,9 @@ class MainBtbAlignBank(
   }
 
   // update replacer
-  replacer.io.trainTouch.valid       := t1_valid && t1_needWrite
-  replacer.io.trainTouch.bits.setIdx := getReplacerSetIndex(t1_startVAddr)
+  replacer.io.trainTouch.valid        := t1_valid && t1_needWrite
+  replacer.io.trainTouch.bits.setIdx  := getReplacerSetIndex(t1_startVAddr)
+  replacer.io.trainTouch.bits.wayMask := t1_wayMask
 
   /* *** multi-hit detection & flush *** */
   private val s2_multiHitMask = detectMultiHit(s2_hitMask, VecInit(s2_rawEntries.map(_.position)))
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala
index ba9e376229c..04233b5f086 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala
@@ -23,22 +23,20 @@ import xiangshan.frontend.bpu.ReplacerState
 
 class MainBtbReplacer(implicit p: Parameters) extends MainBtbModule {
   class MainBtbReplacerIO extends Bundle {
-    class PredictTouch extends Bundle {
+    class Touch extends Bundle {
       val setIdx:  UInt = UInt(SetIdxLen.W)
       val wayMask: UInt = UInt(NumWay.W)
     }
 
-    class TrainTouch extends Bundle {
-      val setIdx: UInt = UInt(SetIdxLen.W)
-    }
-
     class Victim extends Bundle {
       val wayMask: UInt = UInt(NumWay.W)
     }
 
-    val predictTouch: Valid[PredictTouch] = Flipped(Valid(new PredictTouch))
-    val trainTouch:   Valid[TrainTouch]   = Flipped(Valid(new TrainTouch))
-    val victim:       Victim              = Output(new Victim)
+    val victim: Victim            = Output(new Victim)
+    val touch:  Vec[Valid[Touch]] = Vec(2, Flipped(Valid(new Touch))) // magic number 2: predict and train
+
+    def predictTouch: Valid[Touch] = touch(0)
+    def trainTouch:   Valid[Touch] = touch(1)
   }
 
   val io: MainBtbReplacerIO = IO(new MainBtbReplacerIO)
@@ -78,7 +76,8 @@ class MainBtbReplacer(implicit p: Parameters) extends MainBtbModule {
   // compose touch way vec
   private val trainTouchWay = Wire(Valid(UInt(log2Up(NumWay).W)))
   trainTouchWay.valid := io.trainTouch.valid
-  trainTouchWay.bits  := trainStateGen.io.replaceWay
+  trainTouchWay.bits  := OHToUInt(io.victim.wayMask) // MainBtbAlignBank ensures this is one-hot
+  assert(!io.trainTouch.valid || PopCount(io.victim.wayMask) <= 1.U, "victim wayMask should be at-most-one-hot")
 
   // generate next state
   trainStateGen.io.stateIn   := trainState
```
