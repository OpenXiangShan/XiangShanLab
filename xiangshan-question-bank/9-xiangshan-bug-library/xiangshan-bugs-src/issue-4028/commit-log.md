# Commit Log
- Issue: #4028
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4028
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4028
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4028
- Changed files: 1
- Additions: 9
- Deletions: 15

## Files
- `src/main/scala/xiangshan/backend/fu/wrapper/VSet.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/VSet.scala b/src/main/scala/xiangshan/backend/fu/wrapper/VSet.scala
index ef961d4a673..7be2ed4cdf3 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/VSet.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/VSet.scala
@@ -75,7 +75,7 @@ class VSetRiWvf(cfg: FuConfig)(implicit p: Parameters) extends VSetBase(cfg) {
   val vlmax = vsetModule.io.out.vlmax
   val isVsetvl = VSETOpType.isVsetvl(in.ctrl.fuOpType)
 
-  out.res.data := vsetModule.io.out.vconfig.vl
+  out.res.data := vl
 
   if (cfg.writeVlRf) io.vtype.get.bits := vsetModule.io.out.vconfig.vtype
   if (cfg.writeVlRf) io.vtype.get.valid := io.out.valid && isVsetvl
@@ -95,28 +95,22 @@ class VSetRiWvf(cfg: FuConfig)(implicit p: Parameters) extends VSetBase(cfg) {
   * @param p [[Parameters]]
   */
 class VSetRvfWvf(cfg: FuConfig)(implicit p: Parameters) extends VSetBase(cfg) {
-  vsetModule.io.in.avl := 0.U
+  val oldVL = in.data.src(4).asTypeOf(VConfig()).vl
+  vsetModule.io.in.avl := oldVL
   vsetModule.io.in.vtype := vtype
 
-  val oldVL = in.data.src(4).asTypeOf(VConfig()).vl
-  val res = WireInit(0.U.asTypeOf(VConfig()))
+  val vl = vsetModule.io.out.vconfig.vl
   val vlmax = vsetModule.io.out.vlmax
   val isVsetvl = VSETOpType.isVsetvl(in.ctrl.fuOpType)
   val isReadVl = in.ctrl.fuOpType === VSETOpType.csrrvl
-  res.vl := Mux(vsetModule.io.out.vconfig.vtype.illegal, 0.U,
-              Mux(VSETOpType.isKeepVl(in.ctrl.fuOpType), 
-                Mux(oldVL < vlmax, oldVL, vlmax), vsetModule.io.out.vconfig.vl))
-  res.vtype := vsetModule.io.out.vconfig.vtype
 
-  out.res.data := Mux(isReadVl, oldVL,
-                    Mux(vsetModule.io.out.vconfig.vtype.illegal, 0.U,
-                      Mux(VSETOpType.isKeepVl(in.ctrl.fuOpType),
-                        Mux(oldVL < vlmax, oldVL, vlmax), vsetModule.io.out.vconfig.vl)))
+  // csrr vl instruction will use this exu to read vl
+  out.res.data := Mux(isReadVl, oldVL, vl)
 
   if (cfg.writeVlRf) io.vtype.get.bits := vsetModule.io.out.vconfig.vtype
   if (cfg.writeVlRf) io.vtype.get.valid := isVsetvl && io.out.valid
-  if (cfg.writeVlRf) io.vlIsZero.get := io.out.valid && !isReadVl && res.vl === 0.U
-  if (cfg.writeVlRf) io.vlIsVlmax.get := io.out.valid && !isReadVl && res.vl === vlmax
+  if (cfg.writeVlRf) io.vlIsZero.get := io.out.valid && !isReadVl && vl === 0.U
+  if (cfg.writeVlRf) io.vlIsVlmax.get := io.out.valid && !isReadVl && vl === vlmax
 
-  debugIO.vconfig := res
+  debugIO.vconfig := vsetModule.io.out.vconfig
 }
```
