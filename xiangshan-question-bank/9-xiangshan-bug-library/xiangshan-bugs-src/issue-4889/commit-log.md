# Commit Log
- Issue: #4889
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4889
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4889
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4889
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/vector/VSplit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/vector/VSplit.scala b/src/main/scala/xiangshan/mem/vector/VSplit.scala
index a9457c66586..3604e4098ea 100644
--- a/src/main/scala/xiangshan/mem/vector/VSplit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSplit.scala
@@ -371,7 +371,7 @@ abstract class VSplitBuffer(isVStore: Boolean = false)(implicit p: Parameters) e
 
   val vecActive = Mux(!issuePreIsSplit, usSplitMask.orR, (flowMask & UIntToOH(splitIdx)).orR)
   // no-unit-stride can trigger misalign
-  val addrAligned = LookupTree(issueEew, List(
+  val addrAligned = LookupTree(Mux(isIndexed(issueInstType), issueSew, issueEew), List(
     "b00".U   -> true.B,                //b
     "b01".U   -> (vaddr(0)    === 0.U), //h
     "b10".U   -> (vaddr(1, 0) === 0.U), //w
```
