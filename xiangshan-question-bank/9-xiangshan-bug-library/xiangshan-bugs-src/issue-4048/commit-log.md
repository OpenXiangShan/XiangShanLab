# Commit Log
- Issue: #4048
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4048
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4048
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4048
- Changed files: 1
- Additions: 5
- Deletions: 4

## Files
- `src/main/scala/xiangshan/frontend/newRAS.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/newRAS.scala b/src/main/scala/xiangshan/frontend/newRAS.scala
index 799d45d3303..e5d65de51bd 100644
--- a/src/main/scala/xiangshan/frontend/newRAS.scala
+++ b/src/main/scala/xiangshan/frontend/newRAS.scala
@@ -732,10 +732,11 @@ class RAS(implicit p: Parameters) extends BasePredictor {
   // so io.update.bits.pc is used directly here
   val update_pc = io.update.bits.pc
 
-  // To improve Clock Gating Efficiency
-  update.meta := RegEnable(io.update.bits.meta, io.update.valid && (io.update.bits.is_call || io.update.bits.is_ret))
-
-  val updateMeta = update.meta.asTypeOf(new RASMeta)
+  // full core power down sequence need 'val updateMeta = RegEnable(io.update.bits.meta.asTypeOf(new RASMeta), io.update.valid)' to be
+  // 'val updateMeta = RegEnable(io.update.bits.meta.asTypeOf(new RASMeta), io.update.valid && (io.update.bits.is_call || io.update.bits.is_ret))',
+  // but the fault-tolerance mechanism of the return stack needs to be updated in time. Using an unexpected old value on reset will cause errors.
+  // Only 9 registers have clock gate efficiency affected, so we relaxed the control signals.
+  val updateMeta = RegEnable(io.update.bits.meta.asTypeOf(new RASMeta), io.update.valid)
 
   stack.commit_valid      := updateValid
   stack.commit_push_valid := updateValid && update.is_call_taken
```
