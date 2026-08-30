# Commit Log
- Issue: #5181
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5181
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5181
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5181
- Changed files: 1
- Additions: 3
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
index bf257d7fa4e..318d14355b3 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala
@@ -16,6 +16,7 @@
 package xiangshan.frontend.bpu.mbtb
 
 import chisel3._
+import chisel3.util._
 import xiangshan.HasXSParameter
 import xiangshan.frontend.PrunedAddr
 import xiangshan.frontend.bpu.CrossPageHelper
@@ -52,6 +53,7 @@ trait Helpers extends HasMainBtbParameters
       InternalBankIdxLen + SetIdxLen + FetchBlockSizeWidth
     )
 
+  // detect multi-hit, return a mask indicating which way has multi-hit
   def detectMultiHit(hitMask: IndexedSeq[Bool], position: IndexedSeq[UInt]): UInt = {
     require(hitMask.length == position.length)
     require(hitMask.length >= 2)
@@ -66,6 +68,6 @@ trait Helpers extends HasMainBtbParameters
         multiHitMask(i) := true.B
       }
     }
-    multiHitMask.asUInt
+    PriorityEncoderOH(multiHitMask.asUInt)
   }
 }
```
