# Commit Log
- Issue: #3812
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3812
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3812
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3812
- Changed files: 1
- Additions: 3
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index 937213a9eab..1d5ab11ca4b 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -292,7 +292,9 @@ class InterruptFilter extends Module {
   val disableAllIntr = disableDebugIntr || !io.in.mnstatusNMIE
   val debugInterupt = ((io.in.debugIntr && !disableDebugIntr)  << CSRConst.IRQ_DEBUG).asUInt
 
-  val normalIntrVec = mIRVec | hsIRVec | vsMapHostIRVec
+  val normalIntrVec = Mux(mIRVec.orR, mIRVec,
+                        Mux(hsIRVec.orR, hsIRVec,
+                          Mux(vsMapHostIRVec.orR, vsMapHostIRVec, 0.U)))
   val intrVec = VecInit(Mux(io.in.nmi, io.in.nmiVec, normalIntrVec).asBools.map(IR => IR && !disableAllIntr)).asUInt | debugInterupt
 
   // virtual interrupt with hvictl injection
```
