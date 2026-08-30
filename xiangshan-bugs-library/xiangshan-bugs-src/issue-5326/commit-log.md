# Commit Log
- Issue: #5326
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5326
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5326
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5326
- Changed files: 1
- Additions: 3
- Deletions: 2

## Files
- `src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala b/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
index 24477c5101a..68efea9ce6c 100644
--- a/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/ubtb/MicroBtb.scala
@@ -130,7 +130,7 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
   private val t1_allocate     = Wire(Bool())
 
   // if t0_tag === t1_tag, t1 must be updating the entry, so we can see it as a hit, and use t1_updateIdx as hitIdx
-  private val t0_hitT1Update = t1_valid && t0_tag === t1_tag
+  private val t0_hitT1Update = Wire(Bool())
   // if t0 hits but t1 is replacing it, we should see it as not hit
   private val t0_hitT1Victim = t1_valid && t0_realHitIdx === replacer.io.victim && t1_allocate
 
@@ -169,7 +169,8 @@ class MicroBtb(implicit p: Parameters) extends BasePredictor with HasMicroBtbPar
   private val t1_hitPositionSame  = RegEnable(t0_hitPositionSame, t0_valid)
   private val t1_hitAttributeSame = RegEnable(t0_hitAttributeSame, t0_valid)
   private val t1_hitTargetSame    = RegEnable(t0_hitTargetSame, t0_valid)
-
+  // only when t1 is updating/allocating can t0 hit it
+  t0_hitT1Update := t1_valid && t0_tag === t1_tag && (t1_hit || t1_allocate)
   // init a new entry
   private def initEntryIfNotUseful(notUseful: Bool): Unit =
     when(notUseful) {
```
