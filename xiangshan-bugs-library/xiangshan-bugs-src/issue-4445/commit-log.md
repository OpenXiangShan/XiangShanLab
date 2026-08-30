# Commit Log
- Issue: #4445
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4445
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4445
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4445
- Changed files: 5
- Additions: 26
- Deletions: 88

## Files
- `build.sc`
- `src/main/scala/xiangshan/frontend/SC.scala`
- `src/main/scala/xiangshan/frontend/Tage.scala`
- `src/main/scala/xiangshan/frontend/WrBypass.scala`
- `utility`

## Diff
```diff
diff --git a/build.sc b/build.sc
index d6797e2ae1b..6cf97a708f6 100644
--- a/build.sc
+++ b/build.sc
@@ -121,6 +121,9 @@ object utility extends HasChisel {
     ivy"com.lihaoyi::sourcecode:0.4.2",
   )
 
+  object test extends SbtTests with TestModule.ScalaTest {
+    override def ivyDeps = Agg(ivy"org.scalatest::scalatest:3.2.7")
+  }
 }
 
 object yunsuan extends HasChisel {
diff --git a/src/main/scala/xiangshan/frontend/SC.scala b/src/main/scala/xiangshan/frontend/SC.scala
index 288b7c1e2c6..9225c4210fd 100644
--- a/src/main/scala/xiangshan/frontend/SC.scala
+++ b/src/main/scala/xiangshan/frontend/SC.scala
@@ -34,6 +34,7 @@ import scala.{Tuple2 => &}
 import scala.math.min
 import utility._
 import utility.mbist.MbistPipeline
+import utility.sram.SRAMConflictBehavior
 import utility.sram.SRAMTemplate
 import xiangshan._
 
@@ -81,7 +82,7 @@ class SCTable(val nRows: Int, val ctrBits: Int, val histLen: Int)(implicit p: Pa
     shouldReset = true,
     holdRead = true,
     singlePort = false,
-    bypassWrite = true,
+    conflictBehavior = SRAMConflictBehavior.BufferWriteLossy,
     withClockGate = true,
     hasMbist = hasMbist
   ))
@@ -114,6 +115,16 @@ class SCTable(val nRows: Int, val ctrBits: Int, val histLen: Int)(implicit p: Pa
   table.io.r.req.valid       := io.req.valid
   table.io.r.req.bits.setIdx := s0_idx
 
+  val per_br_ctrs_unshuffled = table.io.r.resp.data.sliding(2, 2).toSeq.map(VecInit(_))
+  val per_br_ctrs = VecInit((0 until numBr).map(i =>
+    Mux1H(
+      UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr),
+      per_br_ctrs_unshuffled
+    )
+  ))
+
+  io.resp.ctrs := per_br_ctrs
+
   val update_wdata        = Wire(Vec(numBr, SInt(ctrBits.W))) // correspond to physical bridx
   val update_wdata_packed = VecInit(update_wdata.map(Seq.fill(2)(_)).reduce(_ ++ _))
   val updateWayMask       = Wire(Vec(2 * numBr, Bool()))      // correspond to physical bridx
@@ -134,52 +145,11 @@ class SCTable(val nRows: Int, val ctrBits: Int, val histLen: Int)(implicit p: Pa
   }
   val update_idx = getIdx(io.update.pc, update_folded_hist)
 
-  // SCTable dual port SRAM reads and writes to the same address processing
-  val conflict_buffer_valid   = RegInit(false.B)
-  val conflict_buffer_data    = RegInit(0.U.asTypeOf(update_wdata_packed))
-  val conflict_buffer_idx     = RegInit(0.U.asTypeOf(update_idx))
-  val conflict_buffer_waymask = RegInit(0.U.asTypeOf(updateWayMask))
-
-  val write_conflict = update_idx === s0_idx && io.update.mask.reduce(_ || _) && io.req.valid
-  val can_write      = (conflict_buffer_idx =/= s0_idx || !io.req.valid) && conflict_buffer_valid
-
-  when(write_conflict) {
-    conflict_buffer_valid   := true.B
-    conflict_buffer_data    := update_wdata_packed
-    conflict_buffer_idx     := update_idx
-    conflict_buffer_waymask := updateWayMask
-  }
-  when(can_write) {
-    conflict_buffer_valid := false.B
-  }
-
-  // Using buffer data for prediction
-  val use_conflict_data = conflict_buffer_valid && conflict_buffer_idx === s1_idx
-  val conflict_data_bypass = conflict_buffer_data.zip(conflict_buffer_waymask).map { case (data, mask) =>
-    Mux(mask, data, 0.U.asTypeOf(data))
-  }
-  val conflict_prediction_data = conflict_data_bypass.sliding(2, 2).toSeq.map(VecInit(_))
-  val per_br_ctrs_unshuffled   = table.io.r.resp.data.sliding(2, 2).toSeq.map(VecInit(_))
-  val per_br_ctrs = VecInit((0 until numBr).map(i =>
-    Mux1H(
-      UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr),
-      per_br_ctrs_unshuffled
-    )
-  ))
-  val conflict_br_ctrs = VecInit((0 until numBr).map(i =>
-    Mux1H(
-      UIntToOH(get_phy_br_idx(s1_unhashed_idx, i), numBr),
-      conflict_prediction_data
-    )
-  ))
-
-  io.resp.ctrs := Mux(use_conflict_data, conflict_br_ctrs, per_br_ctrs)
-
   table.io.w.apply(
-    valid = (io.update.mask.reduce(_ || _) && !write_conflict) || can_write,
-    data = Mux(can_write, conflict_buffer_data, update_wdata_packed),
-    setIdx = Mux(can_write, conflict_buffer_idx, update_idx),
-    waymask = Mux(can_write, conflict_buffer_waymask.asUInt, updateWayMask.asUInt)
+    valid = io.update.mask.reduce(_ || _),
+    data = update_wdata_packed,
+    setIdx = update_idx,
+    waymask = updateWayMask.asUInt
   )
 
   val wrBypassEntries = 16
diff --git a/src/main/scala/xiangshan/frontend/Tage.scala b/src/main/scala/xiangshan/frontend/Tage.scala
index c99af6456fd..5e309bd8872 100644
--- a/src/main/scala/xiangshan/frontend/Tage.scala
+++ b/src/main/scala/xiangshan/frontend/Tage.scala
@@ -36,6 +36,7 @@ import scala.math.min
 import utility._
 import utility.mbist.MbistPipeline
 import utility.sram.FoldedSRAMTemplate
+import utility.sram.SRAMConflictBehavior
 import xiangshan._
 
 trait TageParams extends HasBPUConst with HasXSParameter {
@@ -167,9 +168,8 @@ class TageBTable(implicit p: Parameters) extends XSModule with TBTParams {
       way = numBr,
       shouldReset = false,
       holdRead = true,
-      bypassWrite = true,
+      conflictBehavior = SRAMConflictBehavior.BufferWriteLossy,
       withClockGate = true,
-      avoidSameAddr = true,
       hasMbist = hasMbist
     )
   )
diff --git a/src/main/scala/xiangshan/frontend/WrBypass.scala b/src/main/scala/xiangshan/frontend/WrBypass.scala
index c6723bf7db2..367fdd2ec77 100644
--- a/src/main/scala/xiangshan/frontend/WrBypass.scala
+++ b/src/main/scala/xiangshan/frontend/WrBypass.scala
@@ -26,8 +26,7 @@ class WrBypass[T <: Data](
     val numEntries: Int,
     val idxWidth:   Int,
     val numWays:    Int = 1,
-    val tagWidth:   Int = 0,
-    val extraPort:  Option[Boolean] = None
+    val tagWidth:   Int = 0
 )(implicit p: Parameters) extends XSModule {
   require(numEntries >= 0)
   require(idxWidth > 0)
@@ -42,18 +41,8 @@ class WrBypass[T <: Data](
     val write_data     = Input(Vec(numWays, gen))
     val write_way_mask = if (multipleWays) Some(Input(Vec(numWays, Bool()))) else None
 
-    val conflict_valid      = if (extraPort.isDefined) Some(Input(Bool())) else None
-    val conflict_write_data = if (extraPort.isDefined) Some(Input(Vec(numWays, gen))) else None
-    val conflict_way_mask   = if (extraPort.isDefined) Some(Input(UInt(numBr.W))) else None
-
-    val hit             = Output(Bool())
-    val hit_data        = Vec(numWays, Valid(gen))
-    val has_conflict    = if (extraPort.isDefined) Some(Output(Bool())) else None
-    val update_idx      = if (extraPort.isDefined) Some(Output(UInt(idxWidth.W))) else None
-    val update_data     = if (extraPort.isDefined) Some(Output(Vec(numWays, gen))) else None
-    val update_way_mask = if (extraPort.isDefined) Some(Output(UInt(numBr.W))) else None
-
-    val conflict_clean = if (extraPort.isDefined) Some(Input(Bool())) else None
+    val hit      = Output(Bool())
+    val hit_data = Vec(numWays, Valid(gen))
   })
 
   class Idx_Tag extends Bundle {
@@ -65,7 +54,7 @@ class WrBypass[T <: Data](
     }
   }
 
-  val idx_tag_cam = Module(new IndexableCAMTemplate(new Idx_Tag, numEntries, 1, isIndexable = extraPort.isDefined))
+  val idx_tag_cam = Module(new IndexableCAMTemplate(new Idx_Tag, numEntries, 1))
   val data_mem    = Mem(numEntries, Vec(numWays, gen))
 
   val valids       = RegInit(0.U.asTypeOf(Vec(numEntries, Vec(numWays, Bool()))))
@@ -123,30 +112,6 @@ class WrBypass[T <: Data](
   idx_tag_cam.io.w.bits.index := enq_idx
   idx_tag_cam.io.w.bits.data(io.write_idx, io.write_tag.getOrElse(0.U))
 
-  // Extra ports are used to handle dual port read/write conflicts
-  if (extraPort.isDefined) {
-    val conflict_flags    = RegInit(0.U.asTypeOf(Vec(numEntries, Bool())))
-    val conflict_way_mask = RegInit(0.U.asTypeOf(io.conflict_way_mask.get))
-    val conflict_data     = RegInit(VecInit(Seq.tabulate(numWays)(i => 0.U.asTypeOf(gen))))
-    val conflict_idx      = OHToUInt(conflict_flags)
-
-    idx_tag_cam.io.ridx.get := conflict_idx
-
-    when(io.wen && io.conflict_valid.getOrElse(false.B)) {
-      conflict_flags(Mux(hit, hit_idx, enq_idx)) := true.B
-      conflict_way_mask                          := io.conflict_way_mask.get
-      conflict_data                              := io.conflict_write_data.get
-    }
-    when(io.conflict_clean.getOrElse(false.B)) {
-      conflict_flags(conflict_idx) := false.B
-    }
-    // for update the cached data
-    io.has_conflict.get    := conflict_flags.reduce(_ || _)
-    io.update_idx.get      := idx_tag_cam.io.rdata.get.idx
-    io.update_way_mask.get := conflict_way_mask
-    io.update_data.foreach(_ := conflict_data)
-  } else None
-
   XSPerfAccumulate("wrbypass_hit", io.wen && hit)
   XSPerfAccumulate("wrbypass_miss", io.wen && !hit)
 
diff --git a/utility b/utility
index f07fc52c6d2..d28afa34449 160000
--- a/utility
+++ b/utility
@@ -1 +1 @@
-Subproject commit f07fc52c6d23768e445b3c948528a57190f092ca
+Subproject commit d28afa344498b00ed238f75db2de15eaa787a1b7
```
