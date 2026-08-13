# Commit Log
- Issue: #3423
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3423
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3423
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3423
- Changed files: 1
- Additions: 4
- Deletions: 0

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index bd6674ef229..68029de74a4 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -186,6 +186,10 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   fake_pte.perm.r := true.B
   fake_pte.perm.w := true.B
   fake_pte.perm.x := true.B
+  fake_pte.perm.a := true.B
+  fake_pte.perm.d := true.B
+  fake_pte.ppn := ppn(ppnLen - 1, 0)
+  fake_pte.ppn_high := ppn(ptePPNLen - 1, ppnLen)
 
   io.req.ready := idle
   val ptw_resp = Wire(new PtwMergeResp)
```
