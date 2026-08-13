# Commit Log
- Issue: #3991
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3991
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3991
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3991
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/rob/Rob.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index 8555f27a7b9..5616379ab72 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -1110,7 +1110,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
       // However, we cannot determine whether a load/store instruction is MMIO.
       // Thus, we don't allow load/store instructions to trigger an interrupt.
       // TODO: support non-MMIO load-store instructions to trigger interrupts
-      val allow_interrupts = !CommitType.isLoadStore(io.enq.req(i).bits.commitType) && !FuType.isFence(io.enq.req(i).bits.fuType) && !FuType.isCsr(io.enq.req(i).bits.fuType)
+      val allow_interrupts = !CommitType.isLoadStore(io.enq.req(i).bits.commitType) && !FuType.isFence(io.enq.req(i).bits.fuType) && !FuType.isCsr(io.enq.req(i).bits.fuType) && !FuType.isVset(io.enq.req(i).bits.fuType)
       robEntries(allocatePtrVec(i).value).interrupt_safe := allow_interrupts
     }
   }
```
