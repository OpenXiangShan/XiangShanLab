# Commit Log
- Issue: #3628
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3628
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3628
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3628
- Changed files: 1
- Additions: 5
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/FTB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/FTB.scala b/src/main/scala/xiangshan/frontend/FTB.scala
index bfbe108dff5..38444472b42 100644
--- a/src/main/scala/xiangshan/frontend/FTB.scala
+++ b/src/main/scala/xiangshan/frontend/FTB.scala
@@ -499,7 +499,11 @@ class FTB(implicit p: Parameters) extends BasePredictor with FTBParams with BPUU
     ))
     val ftb_r_entries = ftb.io.r.resp.data.map(_.entry)
 
-    val pred_rdata = HoldUnless(ftb.io.r.resp.data, RegNext(io.req_pc.valid && !io.update_access))
+    val pred_rdata = HoldUnless(
+      ftb.io.r.resp.data,
+      RegNext(io.req_pc.valid && !io.update_access),
+      init = Some(VecInit.fill(numWays)(0.U.asTypeOf(new FTBEntryWithTag)))
+    ) // rdata has ftb_entry.valid, shoud reset
     ftb.io.r.req.valid := io.req_pc.valid || io.u_req_pc.valid // io.s0_fire
     ftb.io.r.req.bits.setIdx := Mux(
       io.u_req_pc.valid,
```
