# Commit Log
- Issue: #3714
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3714
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3714
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3714
- Changed files: 1
- Additions: 13
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/rob/ExceptionGen.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rob/ExceptionGen.scala b/src/main/scala/xiangshan/backend/rob/ExceptionGen.scala
index e220b2bded9..ece131cd4bf 100644
--- a/src/main/scala/xiangshan/backend/rob/ExceptionGen.scala
+++ b/src/main/scala/xiangshan/backend/rob/ExceptionGen.scala
@@ -128,11 +128,22 @@ class ExceptionGen(params: BackendParams)(implicit p: Parameters) extends XSModu
         current := s1_out_bits
       }.elsewhen (current.robIdx === s1_out_bits.robIdx) {
         current.exceptionVec := Mux(isVecUpdate, s1_out_bits.exceptionVec, current.exceptionVec)
+        current.hasException := Mux(isVecUpdate, s1_out_bits.hasException, current.hasException)
         current.flushPipe := (s1_out_bits.flushPipe || current.flushPipe) && !s1_out_bits.exceptionVec.asUInt.orR
         current.replayInst := s1_out_bits.replayInst || current.replayInst
         current.singleStep := s1_out_bits.singleStep || current.singleStep
-        current.trigger := (s1_out_bits.trigger | current.trigger)
-        current.vstart  := Mux(isVecUpdate, s1_out_bits.vstart, current.vstart)
+        current.trigger   := Mux(isVecUpdate, s1_out_bits.trigger,    current.trigger)
+        current.vstart    := Mux(isVecUpdate, s1_out_bits.vstart,     current.vstart)
+        current.vstartEn  := Mux(isVecUpdate, s1_out_bits.vstartEn,   current.vstartEn)
+        current.isVecLoad := Mux(isVecUpdate, s1_out_bits.isVecLoad,  current.isVecLoad)
+        current.isVlm     := Mux(isVecUpdate, s1_out_bits.isVlm,      current.isVlm)
+        current.isStrided := Mux(isVecUpdate, s1_out_bits.isStrided,  current.isStrided)
+        current.isIndexed := Mux(isVecUpdate, s1_out_bits.isIndexed,  current.isIndexed)
+        current.isWhole   := Mux(isVecUpdate, s1_out_bits.isWhole,    current.isWhole)
+        current.nf        := Mux(isVecUpdate, s1_out_bits.nf,         current.nf)
+        current.vsew      := Mux(isVecUpdate, s1_out_bits.vsew,       current.vsew)
+        current.veew      := Mux(isVecUpdate, s1_out_bits.veew,       current.veew)
+        current.vlmul     := Mux(isVecUpdate, s1_out_bits.vlmul,      current.vlmul)
       }
     }
   }.elsewhen (s1_out_valid && !s1_flush) {
```
