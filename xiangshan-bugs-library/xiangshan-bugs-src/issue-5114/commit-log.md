# Commit Log
- Issue: #5114
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5114
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5114
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5114
- Changed files: 1
- Additions: 44
- Deletions: 19

## Files
- `src/main/scala/xiangshan/cache/mmu/TLBStorage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala b/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
index 53c3aaeb03f..0564ae95afa 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
@@ -191,7 +191,8 @@ class TLBFA(
   val sfenceHit_noasid = entries.map(_.hit(sfence_vpn, sfence.bits.id, ignoreAsid = true, vmid = io.csr.hgatp.vmid, hasS2xlate = io.csr.priv.virt))
   // Sfence will flush all sectors of an entry when hit
   when (sfence_valid) {
-    when (sfence.bits.rs1) { // virtual address *.rs1 <- (rs1===0.U)
+    when (sfence.bits.rs1 || io.csr.priv.virt) { // virtual address *.rs1 <- (rs1===0.U)
+      // Note: when virt=1, always flush all addr. See hfence.vvma comment.
       when (sfence.bits.rs2) { // asid, but i do not want to support asid, *.rs2 <- (rs2===0.U)
         // all addr and all asid
         v.zipWithIndex.map{ case(a, i) => a := a && !((io.csr.priv.virt === false.B && entries(i).s2xlate === noS2xlate) ||
@@ -215,28 +216,52 @@ class TLBFA(
   val hfencev_valid = sfence.valid && sfence.bits.hv
   val hfenceg_valid = sfence.valid && sfence.bits.hg
   val hfencev = io.sfence
-  val hfencev_vpn = sfence_vpn
-  val hfencevHit = entries.map(_.hit(hfencev_vpn, hfencev.bits.id, vmid = io.csr.hgatp.vmid, hasS2xlate = true.B))
-  val hfencevHit_noasid = entries.map(_.hit(hfencev_vpn, 0.U, ignoreAsid = true, vmid = io.csr.hgatp.vmid, hasS2xlate = true.B))
+  // val hfencev_vpn = sfence_vpn
+  // val hfencevHit = entries.map(_.hit(hfencev_vpn, hfencev.bits.id, vmid = io.csr.hgatp.vmid, hasS2xlate = true.B))
+  // val hfencevHit_noasid = entries.map(_.hit(hfencev_vpn, 0.U, ignoreAsid = true, vmid = io.csr.hgatp.vmid, hasS2xlate = true.B))
   when (hfencev_valid) {
-    when (hfencev.bits.rs1) {
-      when (hfencev.bits.rs2) {
-        // all addr and all asid
-        v.zipWithIndex.map { case (a, i) => a := a && !(entries(i).s2xlate =/= noS2xlate && entries(i).vmid === io.csr.hgatp.vmid)}
-      }.otherwise {
-        // all addr but specific asid
-        v.zipWithIndex.map { case (a, i) => a := a && !(!g(i) && (entries(i).s2xlate =/= noS2xlate && entries(i).asid === sfence.bits.id && entries(i).vmid === io.csr.hgatp.vmid))
-        }
-      }
+    when (hfencev.bits.rs2) {
+      // all addr and all asid
+      v.zipWithIndex.map { case (a, i) => a := a && !(entries(i).s2xlate =/= noS2xlate && entries(i).vmid === io.csr.hgatp.vmid)}
     }.otherwise {
-      when (hfencev.bits.rs2) {
-        // specific addr but all asid
-        v.zipWithIndex.map{ case (a, i) => a := a && !hfencevHit_noasid(i) }
-      }.otherwise {
-        // specific addr and specific asid
-        v.zipWithIndex.map{ case (a, i) => a := a && !(hfencevHit(i) && !g(i)) }
+      // all addr but specific asid
+      v.zipWithIndex.map { case (a, i) => a := a && !(!g(i) && (entries(i).s2xlate =/= noS2xlate && entries(i).asid === sfence.bits.id && entries(i).vmid === io.csr.hgatp.vmid))
       }
     }
+    /***
+     * Current design cannot handle addr match in L1TLB properly, when two-stage address translation is enabled.
+     * So we disable the address match function. Now hfence.vvma won't consider addr and will flush all entries.
+     *
+     * Currently, both VS-stage and G-stage are merged into a single L1TLB entry, with address matching controlled by the smaller page size.
+     * Consider the following scenario:
+     *                 VS-stage Page    G-stage Page
+     *                  Large Page       Small Page
+     *                   +--------+
+     *                   |        |
+     *                 +=|========|=====+========+=+
+     *  L1TLB Entry->  | |########|     |########| |
+     *                 +=|========|=====+========+=+
+     *                   |        |
+     *  sfence addr ---> |        |
+     *  try to flush     |        |
+     *                   +--------+
+     * 
+     * In this case, the VS-stage is a large page, while the G-stage is a small page. L1TLB stores them as a small page. 
+     * When hfence.vvma comes with an address in that VS large page but outside the small page, it should flush the VS page. 
+     * However, since L1TLB always treats this entry as a small page, it cannot match this address, thus cannot flush this entry.
+     * 
+    ***/
+    // when (hfencev.bits.rs1) {
+    //   // all addr
+    // }.otherwise {
+    //   when (hfencev.bits.rs2) {
+    //     // specific addr but all asid
+    //     v.zipWithIndex.map{ case (a, i) => a := a && !hfencevHit_noasid(i) }
+    //   }.otherwise {
+    //     // specific addr and specific asid
+    //     v.zipWithIndex.map{ case (a, i) => a := a && !(hfencevHit(i) && !g(i)) }
+    //   }
+    // }
   }
```
