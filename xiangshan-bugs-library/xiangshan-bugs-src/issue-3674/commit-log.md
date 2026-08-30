# Commit Log
- Issue: #3674
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3674
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3674
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3674
- Changed files: 1
- Additions: 3
- Deletions: 0

## Files
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 5b83bde5f6e..15eeade3a71 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -368,6 +368,9 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       resp(idx).bits.excp(nDups).af.instr := false.B
 
       resp(idx).bits.excp(nDups).vaNeedExt := false.B
+      // overwrite miss & gpaddr when exception related to high address truncation happens
+      resp(idx).bits.miss := false.B
+      resp(idx).bits.gpaddr(nDups) := RegNext(req(idx).bits.fullva)
     } .otherwise {
       // isForVSnonLeafPTE is used only when gpf happens and it caused by a G-stage translation which supports VS-stage translation
       // it will be sent to CSR in order to modify the m/htinst.
```
