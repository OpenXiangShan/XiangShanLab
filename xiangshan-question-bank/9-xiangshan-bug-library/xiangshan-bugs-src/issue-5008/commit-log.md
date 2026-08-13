# Commit Log
- Issue: #5008
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5008
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5008
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5008
- Changed files: 1
- Additions: 9
- Deletions: 9

## Files
- `src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala b/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
index 9ee8634807e..5ca4aafc1ac 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
@@ -59,8 +59,7 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
   private val s1_tag        = getTag(s1_startVAddr)
 
   private val s1_hitOH = VecInit(entries.map(e => e.valid && e.tag === s1_tag)).asUInt
-  // FIXME: Currently this assertion fails. Fix or reconsider it in the future.
-//  assert(PopCount(s1_hitOH) <= 1.U, "MicroBtb s1_hitOH should be one-hot")
+  assert(PopCount(s1_hitOH) <= 1.U, "MicroBtb s1_hitOH should be one-hot")
   private val s1_hit      = s1_hitOH.orR
   private val s1_hitIdx   = OHToUInt(s1_hitOH)
   private val s1_hitEntry = entries(s1_hitIdx)
@@ -104,19 +103,19 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
   // or, the first may replace the entry, causing a false "hit" in the second train, causing wrong update.
   // So, we define some of the t1 signals in advance, and use them to check if the contiguous trains are hit.
   private val t1_valid        = Wire(Bool())
-  private val t1_hit          = Wire(Bool())
   private val t1_tag          = Wire(UInt(TagWidth.W))
   private val t1_updateIdx    = Wire(UInt(log2Up(NumEntries).W))
   private val t1_hitEntry     = Wire(new MicroBtbEntry)
   private val t1_updatedEntry = WireDefault(t1_hitEntry) // will be updated in t1, then write back to entries
+  private val t1_allocate     = Wire(Bool())
 
   // if t0_tag === t1_tag, t1 must be updating the entry, so we can see it as a hit, and use t1_updateIdx as hitIdx
   private val t0_hitT1Update = t1_valid && t0_tag === t1_tag
   // if t0 hits but t1 is replacing it, we should see it as not hit
-  private val t0_hitT1Victim = t1_valid && t0_realHitIdx === replacer.io.victim && t0_realHit && !t1_hit
+  private val t0_hitT1Victim = t1_valid && t0_realHitIdx === replacer.io.victim && t1_allocate
 
   // fix final hit
-  private val t0_hit = (t0_realHit || t0_hitT1Update) && !t0_hitT1Victim
+  private val t0_hit = t0_realHit && !t0_hitT1Victim || t0_hitT1Update
   // select hit entry: use t1_updatedEntry if t0_hitT1Update, otherwise use real hit entry
   private val t0_hitIdx   = Mux(t0_hitT1Update, t1_updateIdx, t0_realHitIdx)
   private val t0_hitEntry = Mux(t0_hitT1Update, t1_updatedEntry, entries(t0_realHitIdx))
@@ -144,7 +143,7 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
   private val t1_attribute   = RegEnable(t0_attribute, t0_valid)
   private val t1_targetCarry = t0_targetCarry.map(w => RegEnable(w, t0_valid)) // if (EnableTargetFix)
 
-  t1_hit := RegEnable(t0_hit, t0_valid)
+  private val t1_hit    = RegEnable(t0_hit, t0_valid)
   private val t1_hitIdx = RegEnable(t0_hitIdx, t0_valid)
   t1_hitEntry := RegEnable(t0_hitEntry, t0_valid)
 
@@ -240,9 +239,10 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
   }
 
   // select the entry: if hit, use the hit entry, otherwise use the victim from replacer (first not useful, or Plru)
+  t1_allocate  := !t1_hit && t1_actualTaken
   t1_updateIdx := Mux(t1_hit, t1_hitIdx, replacer.io.victim)
   // and write back the updated entry
-  when(t1_valid && (t1_hit || t1_actualTaken)) { // update entry if hit, or alloc entry only for taken branches
+  when(t1_valid && (t1_hit || t1_allocate)) { // update entry if hit, or alloc entry only for taken branches
     entries(t1_updateIdx) := t1_updatedEntry
   }
 
@@ -255,8 +255,8 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
   XSPerfAccumulate("trainHitT1Update", t0_valid && t0_hitT1Update)
   XSPerfAccumulate("trainHitT1Victim", t0_valid && t0_hitT1Victim)
 
-  XSPerfAccumulate("allocateNotUseful", t1_valid && !t1_hit && replacer.io.perf.replaceNotUseful)
-  XSPerfAccumulate("allocatePlru", t1_valid && !t1_hit && !replacer.io.perf.replaceNotUseful)
+  XSPerfAccumulate("allocateNotUseful", t1_valid && t1_allocate && replacer.io.perf.replaceNotUseful)
+  XSPerfAccumulate("allocatePlru", t1_valid && t1_allocate && !replacer.io.perf.replaceNotUseful)
   XSPerfAccumulate(
     "replace",
     t1_valid && t1_hit && (
```
