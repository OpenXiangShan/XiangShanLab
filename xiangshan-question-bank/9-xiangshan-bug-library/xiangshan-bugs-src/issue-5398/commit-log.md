# Commit Log
- Issue: #5398
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5398
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5398
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5398
- Changed files: 1
- Additions: 6
- Deletions: 18

## Files
- `src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala b/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala
index ee520ec17f2..c1b65875d52 100644
--- a/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala
+++ b/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala
@@ -483,10 +483,6 @@ class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents wi
     fromRenameUpdate(i).bits.srcType(numRegSrcVf - 1) := Mux(ignoreOldVdVec(i), SrcType.no, fromRename(i).bits.srcType(numRegSrcVf - 1))
   }
   val dispatchBlock = fromRename.map(_.valid).reduce(_ || _) && !io.toRenameAllFire
-  XSPerfAccumulate(s"block_cycle", dispatchBlock)
-  XSPerfAccumulate(s"block_iq", dispatchBlock && uopBlockByIQ.asUInt.orR)
-  XSPerfAccumulate(s"block_allowDispatch", dispatchBlock && allowDispatch.asUInt.orR)
-  XSPerfAccumulate(s"block_lsqFull", dispatchBlock && lsqCanAccept)
   for (i <- 0 until RenameWidth){
     // check is drop amocas sta
     fromRenameUpdate(i).bits.isDropAmocasSta := fromRename(i).bits.isAMOCAS && fromRename(i).bits.uopIdx(0) === 0.U
@@ -876,22 +872,16 @@ class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents wi
   val renameFireCnt = PopCount(fromRename.map(_.fire))
 
   val stall_rob = hasValidInstr && !io.enqRob.canAccept
-  val stall_int_dq = hasValidInstr && io.enqRob.canAccept
-  val stall_int_dq0 = hasValidInstr && io.enqRob.canAccept
-  val stall_int_dq1 = hasValidInstr && io.enqRob.canAccept
-  val stall_fp_dq = hasValidInstr && io.enqRob.canAccept
-  val stall_ls_dq = hasValidInstr && io.enqRob.canAccept
 
   XSPerfAccumulate("in_valid_count", PopCount(fromRename.map(_.valid)))
   XSPerfAccumulate("in_fire_count", PopCount(fromRename.map(_.fire)))
   XSPerfAccumulate("in_valid_not_ready_count", PopCount(fromRename.map(x => x.valid && !x.ready)))
   XSPerfAccumulate("wait_cycle", !fromRename.head.valid && allResourceReady)
-
+  XSPerfAccumulate("stall_cycle", dispatchBlock)
   XSPerfAccumulate("stall_cycle_rob", stall_rob)
-  XSPerfAccumulate("stall_cycle_int_dq0", stall_int_dq0)
-  XSPerfAccumulate("stall_cycle_int_dq1", stall_int_dq1)
-  XSPerfAccumulate("stall_cycle_fp_dq", stall_fp_dq)
-  XSPerfAccumulate("stall_cycle_ls_dq", stall_ls_dq)
+  XSPerfAccumulate("stall_cycle_iq", dispatchBlock && uopBlockByIQ.asUInt.orR)
+  XSPerfAccumulate("stall_cycle_allowDispatch", dispatchBlock && allowDispatch.asUInt.orR)
+  XSPerfAccumulate("stall_cycle_lsqFull", dispatchBlock && lsqCanAccept)
 
   val notIssue = !io.debugTopDown.fromRob.robHeadLsIssue
   val tlbReplay = io.debugTopDown.fromCore.fromMem.robHeadTlbReplay
@@ -968,11 +958,9 @@ class NewDispatch(implicit p: Parameters) extends XSModule with HasPerfEvents wi
     ("dispatch_empty",              !hasValidInstr                                                                 ),
     ("dispatch_utili",              PopCount(fromRename.map(_.valid))                                              ),
     ("dispatch_waitinstr",          PopCount(fromRename.map(!_.valid && canAccept))                                ),
-    ("dispatch_stall_cycle_lsq",    false.B                                                                        ),
+    ("dispatch_stall_cycle_lsq",    dispatchBlock && lsqCanAccept                                                  ),
     ("dispatch_stall_cycle_rob",    stall_rob                                                                      ),
-    ("dispatch_stall_cycle_int_dq", stall_int_dq                                                                   ),
-    ("dispatch_stall_cycle_fp_dq",  stall_fp_dq                                                                    ),
-    ("dispatch_stall_cycle_ls_dq",  stall_ls_dq                                                                    )
+    ("dispatch_stall_cycle_iq",     dispatchBlock && uopBlockByIQ.asUInt.orR                                       ),
   )
   generatePerfEvent()
 }
```
