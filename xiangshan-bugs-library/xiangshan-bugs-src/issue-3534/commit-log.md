# Commit Log
- Issue: #3534
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3534
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3534
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3534
- Changed files: 1
- Additions: 10
- Deletions: 3

## Files
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index 97603803913..2cc402d28ff 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -429,8 +429,10 @@ object SvinvalDecode extends DecodeConstants {
   val decodeArray: Array[(BitPat, XSDecodeBase)] = Array(
     /* sinval_vma is like sfence.vma , but sinval_vma can be dispatched and issued like normal instructions while sfence.vma
      * must assure it is the ONLY instrucion executing in backend.
+     * Since software cannot promiss all sinval.vma between sfence.w.inval and sfence.inval.ir, we make sinval.vma wait
+     * forward.
      */
-    SINVAL_VMA        -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.fence, FenceOpType.sfence, SelImm.X),
+    SINVAL_VMA        -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.fence, FenceOpType.sfence, SelImm.X, noSpec = T, blockBack = T),
     /* sfecne.w.inval is the begin instrucion of a TLB flush which set *noSpecExec* and *blockBackward* signals
      * so when it comes to dispatch , it will block all instruction after itself until all instrucions ahead of it in rob commit
      * then dispatch and issue this instrucion to flush sbuffer to dcache
@@ -474,8 +476,13 @@ object HypervisorDecode extends DecodeConstants {
   override val decodeArray: Array[(BitPat, XSDecodeBase)] = Array(
     HFENCE_GVMA -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.fence, FenceOpType.hfence_g, SelImm.X, noSpec = T, blockBack = T, flushPipe = T),
     HFENCE_VVMA -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.fence, FenceOpType.hfence_v, SelImm.X, noSpec = T, blockBack = T, flushPipe = T),
-    HINVAL_GVMA -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.fence, FenceOpType.hfence_g, SelImm.X),
-    HINVAL_VVMA -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.fence, FenceOpType.hfence_v, SelImm.X),
+
+    /**
+     * Since software cannot promiss all sinval.vma between sfence.w.inval and sfence.inval.ir, we make sinval.vma wait
+     * forward.
+     */
+    HINVAL_GVMA -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.fence, FenceOpType.hfence_g, SelImm.X, noSpec = T, blockBack = T),
+    HINVAL_VVMA -> XSDecode(SrcType.reg, SrcType.reg, SrcType.X, FuType.fence, FenceOpType.hfence_v, SelImm.X, noSpec = T, blockBack = T),
     HLV_B       -> XSDecode(SrcType.reg, SrcType.X,   SrcType.X, FuType.ldu,   LSUOpType.hlvb,       SelImm.X, xWen = T),
     HLV_BU      -> XSDecode(SrcType.reg, SrcType.X,   SrcType.X, FuType.ldu,   LSUOpType.hlvbu,      SelImm.X, xWen = T),
     HLV_D       -> XSDecode(SrcType.reg, SrcType.X,   SrcType.X, FuType.ldu,   LSUOpType.hlvd,       SelImm.X, xWen = T),
```
