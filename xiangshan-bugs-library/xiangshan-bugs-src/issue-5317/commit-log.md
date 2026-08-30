# Commit Log
- Issue: #5317
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5317
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5317
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5317
- Changed files: 6
- Additions: 7
- Deletions: 14

## Files
- `src/main/scala/xiangshan/frontend/bpu/Bpu.scala`
- `src/main/scala/xiangshan/frontend/bpu/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala`
- `src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala`
- `src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala`
- `src/main/scala/xiangshan/frontend/ftq/Ftq.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
index 129bffec8a0..592c5ae3913 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bpu.scala
@@ -200,8 +200,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   // ras
   ras.io.redirect.valid          := redirect.valid
   ras.io.redirect.bits.attribute := redirect.bits.attribute
-  ras.io.redirect.bits.brPc      := redirect.bits.startVAddr
-  ras.io.redirect.bits.isRvc     := redirect.bits.isRvc
+  ras.io.redirect.bits.brPc      := redirect.bits.cfiPc
   ras.io.redirect.bits.meta      := redirect.bits.speculationMeta.rasMeta
   ras.io.redirect.bits.level     := 0.U(1.W)
   ras.io.commit.valid            := commitUpdate.valid
@@ -209,7 +208,6 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   ras.io.commit.bits.meta        := commitUpdate.bits.rasMeta
   ras.io.specIn.valid            := s3_fire
   ras.io.specIn.bits.startPc     := s3_pc.toUInt
-  ras.io.specIn.bits.isRvc       := false.B
   ras.io.specIn.bits.attribute   := s3_prediction.attribute
   ras.io.specIn.bits.cfiPosition := s3_prediction.cfiPosition
 
@@ -425,7 +423,7 @@ class Bpu(implicit p: Parameters) extends BpuModule with HalfAlignHelper {
   ghr.io.update.position     := VecInit(s3_mbtbResult.map(_.bits.cfiPosition))
   ghr.io.update.hitMask      := VecInit(s3_mbtbResult.map(_.valid))
   ghr.io.redirect.valid      := redirect.valid
-  ghr.io.redirect.startVAddr := redirect.bits.startVAddr
+  ghr.io.redirect.startVAddr := redirect.bits.cfiPc
   ghr.io.redirect.taken      := redirect.bits.taken
   ghr.io.redirect.meta       := redirect.bits.speculationMeta.ghrMeta
   private val s0_ghist = ghr.io.s0_ghist
diff --git a/src/main/scala/xiangshan/frontend/bpu/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
index 31e185c7674..c962a12318e 100644
--- a/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/Bundles.scala
@@ -201,9 +201,8 @@ class BpuPrediction(implicit p: Parameters) extends BpuBundle with HalfAlignHelp
 
 // Backend & Ftq -> Bpu
 class BpuRedirect(implicit p: Parameters) extends BpuBundle {
-  val startVAddr:      PrunedAddr         = PrunedAddr(VAddrBits)
+  val cfiPc:           PrunedAddr         = PrunedAddr(VAddrBits)
   val target:          PrunedAddr         = PrunedAddr(VAddrBits)
-  val isRvc:           Bool               = Bool()
   val taken:           Bool               = Bool()
   val speculationMeta: BpuSpeculationMeta = new BpuSpeculationMeta
   val attribute:       BranchAttribute    = new BranchAttribute
diff --git a/src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala b/src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala
index a9eacfe98cb..cec42e482ae 100644
--- a/src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala
@@ -93,7 +93,7 @@ class Phr(implicit p: Parameters) extends PhrModule with HasPhrParameters with H
 
   redirectData.valid   := io.train.redirect.valid
   redirectData.taken   := io.train.redirect.bits.taken
-  redirectData.pc      := io.train.redirect.bits.startVAddr
+  redirectData.pc      := io.train.redirect.bits.cfiPc
   redirectData.target  := io.train.redirect.bits.target
   redirectData.phrMeta := io.train.redirect.bits.speculationMeta.phrMeta
 
diff --git a/src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala b/src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala
index d94cd3428cc..43bbf04755e 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ras/Bundles.scala
@@ -104,7 +104,6 @@ class RasSpecInfo(implicit p: Parameters) extends RasBundle {
   val attribute:   BranchAttribute = new BranchAttribute
   val cfiPosition: UInt            = UInt(CfiPositionWidth.W)
   val startPc:     UInt            = UInt(VAddrBits.W)
-  val isRvc:       Bool            = Bool()
 }
 
 class RasCommitInfo(implicit p: Parameters) extends RasBundle {
@@ -115,7 +114,6 @@ class RasCommitInfo(implicit p: Parameters) extends RasBundle {
 class RasRedirectInfo(implicit p: Parameters) extends RasBundle {
   val attribute: BranchAttribute = new BranchAttribute
   val brPc:      PrunedAddr      = PrunedAddr(VAddrBits)
-  val isRvc:     Bool            = Bool()
   val meta:      RasInternalMeta = new RasInternalMeta
   val level:     UInt            = RedirectLevel()
 }
diff --git a/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala b/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
index 980fc331bd2..60744b4ef05 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ras/Ras.scala
@@ -64,7 +64,7 @@ class Ras(implicit p: Parameters) extends BasePredictor with HasRasParameters wi
 
   private val specIn       = io.specIn.bits
   private val specAlignPc  = specIn.startPc & alignMask
-  private val specPushAddr = specAlignPc + specIn.cfiPosition + Mux(specIn.isRvc, 2.U, 4.U)
+  private val specPushAddr = specAlignPc + specIn.cfiPosition + 2.U
   stack.spec.pushValid := specPush && !stackNearOverflow
   stack.spec.popValid  := specPop && !stackNearOverflow
 
diff --git a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
index 34eeac26373..cf5c977978a 100644
--- a/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
+++ b/src/main/scala/xiangshan/frontend/ftq/Ftq.scala
@@ -302,11 +302,9 @@ class Ftq(implicit p: Parameters) extends FtqModule
   // TODO: only valid should be needed
   io.toIfu.redirect.bits := DontCare
 
-  io.toBpu.redirect.valid := redirect.valid
-  // FIXME: Modify BPU
-  io.toBpu.redirect.bits.startVAddr      := redirect.bits.pc
+  io.toBpu.redirect.valid                := redirect.valid
+  io.toBpu.redirect.bits.cfiPc           := redirect.bits.pc + (redirect.bits.ftqOffset << 1).asUInt
   io.toBpu.redirect.bits.target          := redirect.bits.target
-  io.toBpu.redirect.bits.isRvc           := redirect.bits.isRVC
   io.toBpu.redirect.bits.taken           := redirect.bits.taken
   io.toBpu.redirect.bits.attribute       := redirect.bits.attribute
   io.toBpu.redirect.bits.speculationMeta := speculationQueue(redirect.bits.ftqIdx.value)
```
