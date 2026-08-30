# Commit Log
- Issue: #3324
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3324
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3324
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3324
- Changed files: 1
- Additions: 6
- Deletions: 4

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index e895f8d07f2..0fd20f48ad4 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -50,6 +50,8 @@ class InterruptFilter extends Module {
   val sieFields = sie.asTypeOf(new SieBundle)
   val hipFields = hip.asTypeOf(new HipBundle)
   val hieFields = hie.asTypeOf(new HieBundle)
+  val vsipFields = vsip.asTypeOf(new VSipBundle)
+  val vsieFields = vsie.asTypeOf(new VSieBundle)
   val hidelegFields = hideleg.asTypeOf(new HidelegBundle)
 
   private val hsip = hip.asUInt | sip.asUInt
@@ -180,10 +182,10 @@ class InterruptFilter extends Module {
   )
 
   // refactor this code & has some problem
-  val Candidate1: Bool = hidelegFields.VSEI && hipFields.VSEIP && hieFields.VSEIE.asBool && (hstatus.VGEIN.asUInt =/= 0.U) && (vstopei.asUInt =/= 0.U)
-  val Candidate2: Bool = hidelegFields.VSEI && hipFields.VSEIP && hieFields.VSEIE.asBool && (hstatus.VGEIN.asUInt === 0.U) && (hvictl.IID.asUInt === 9.U) && (hvictl.IPRIO.asUInt =/= 0.U)
-  val Candidate3: Bool = hidelegFields.VSEI && hipFields.VSEIP && hieFields.VSEIE.asBool && !Candidate1 && !Candidate2
-  val Candidate4: Bool = hvictl.VTI.asUInt === 0.U
+  val Candidate1: Bool = vsipFields.VSEIP.asBool && vsieFields.VSEIE.asBool && (hstatus.VGEIN.asUInt =/= 0.U) && (vstopei.asUInt =/= 0.U)
+  val Candidate2: Bool = vsipFields.VSEIP.asBool && vsieFields.VSEIE.asBool && (hstatus.VGEIN.asUInt === 0.U) && (hvictl.IID.asUInt === 9.U) && (hvictl.IPRIO.asUInt =/= 0.U)
+  val Candidate3: Bool = vsipFields.VSEIP.asBool && vsieFields.VSEIE.asBool && !Candidate1 && !Candidate2
+  val Candidate4: Bool = (hvictl.VTI.asUInt === 0.U) && (vsie & vsip & "hfffffffffffffdff".U).orR
   val Candidate5: Bool = (hvictl.VTI.asUInt === 1.U) && (hvictl.IID.asUInt =/= 9.U)
   val CandidateNoValid: Bool = !Candidate1 && !Candidate2 && !Candidate3 && !Candidate4 && !Candidate5
```
