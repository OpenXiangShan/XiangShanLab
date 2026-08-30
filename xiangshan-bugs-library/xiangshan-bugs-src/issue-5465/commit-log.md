# Commit Log
- Issue: #5465
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5465
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5465
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5465
- Changed files: 2
- Additions: 4
- Deletions: 4

## Files
- `src/main/scala/xiangshan/backend/Region.scala`
- `src/main/scala/xiangshan/backend/datapath/DataPath.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Region.scala b/src/main/scala/xiangshan/backend/Region.scala
index 3431c03187f..5a9d3aebe71 100644
--- a/src/main/scala/xiangshan/backend/Region.scala
+++ b/src/main/scala/xiangshan/backend/Region.scala
@@ -784,8 +784,8 @@ class RegionIO(val params: SchdBlockParams)(implicit p: Parameters) extends XSBu
   val flush = Flipped(ValidIO(new Redirect))
   val ldCancel = Vec(backendParams.LduCnt, Flipped(new LoadCancelIO))
   val fromPcTargetMem = Option.when(params.isIntSchd)(Flipped(new PcToDataPathIO(backendParams)))
-  val diffVlRat = Option.when(params.isVecSchd)(Input(Vec(1, UInt(log2Up(VlPhyRegs).W))))
-  val diffVl = Option.when(params.isVecSchd)(Output(UInt(VlData().dataWidth.W)))
+  val diffVlRat = Option.when(backendParams.basicDebugEn && params.isVecSchd)(Input(Vec(1, UInt(log2Up(VlPhyRegs).W))))
+  val diffVl = Option.when(backendParams.basicDebugEn && params.isVecSchd)(Output(UInt(VlData().dataWidth.W)))
   val vlWriteBackInfoIn = new Bundle {
     val vlFromIntIsZero = Input(Bool())
     val vlFromIntIsVlmax = Input(Bool())
diff --git a/src/main/scala/xiangshan/backend/datapath/DataPath.scala b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
index 97ac0945b3c..3a084c0ef9c 100644
--- a/src/main/scala/xiangshan/backend/datapath/DataPath.scala
+++ b/src/main/scala/xiangshan/backend/datapath/DataPath.scala
@@ -873,8 +873,8 @@ class DataPathIO()(implicit p: Parameters, params: BackendParams, param: SchdBlo
     Output(UInt(RegCacheIdxWidth.W))
   )
 
-  val diffVlRat  = if (params.basicDebugEn && param.isVecSchd) Some(Input(Vec(1, UInt(log2Up(VlPhyRegs).W)))) else None
-  val diffVl     = if (params.basicDebugEn && param.isVecSchd) Some(Output(UInt(VlData().dataWidth.W))) else None
+  val diffVlRat = Option.when(params.basicDebugEn && param.isVecSchd)(Input(Vec(1, UInt(log2Up(VlPhyRegs).W))))
+  val diffVl = Option.when(params.basicDebugEn && param.isVecSchd)(Output(UInt(VlData().dataWidth.W)))
 
   val uopTopDown = new UopTopDown
 }
```
