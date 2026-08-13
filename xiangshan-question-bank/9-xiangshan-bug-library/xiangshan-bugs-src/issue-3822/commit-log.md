# Commit Log
- Issue: #3822
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3822
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3822
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3822
- Changed files: 2
- Additions: 5
- Deletions: 10

## Files
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index e709d631716..47075844256 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -274,11 +274,12 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       // 2. ld 0x81001000. vaddr = 0x81001000, fullva = 0x80000ffb
       // When load 1 trigger a guest page fault, we should use offset of fullva when generate gpaddr
       // and when load 2 trigger a guest page fault, we should just use offset of vaddr(all zero).
-      // Whether cross-page will be determined in misalign buffer(situation 2) so we only need to judge situation 1 here.
-      val gpaddr_offset = Mux(isLeaf(d), get_off(req_out(i).fullva), Cat(getVpnn(get_pn(req_out(i).fullva), vpn_idx), 0.U(log2Up(XLEN/8).W)))
+      // Also, when onlyS2, if crosspage, gpaddr = vaddr(start address of a new page), else gpaddr = fullva(original vaddr)
+      val crossPageVaddr = Mux(req_out(i).fullva(12) =/= vaddr(12), vaddr, req_out(i).fullva)
+      val gpaddr_offset = Mux(isLeaf(d), get_off(crossPageVaddr), Cat(getVpnn(get_pn(crossPageVaddr), vpn_idx), 0.U(log2Up(XLEN/8).W)))
       val gpaddr = Cat(gvpn(d), gpaddr_offset)
       resp(i).bits.paddr(d) := Mux(enable, paddr, vaddr)
-      resp(i).bits.gpaddr(d) := Mux(r_s2xlate(d) === onlyStage2, vaddr, gpaddr)
+      resp(i).bits.gpaddr(d) := Mux(r_s2xlate(d) === onlyStage2, crossPageVaddr, gpaddr)
     }
 
     XSDebug(req_out_v(i), p"(${i.U}) hit:${hit} miss:${miss} ppn:${Hexadecimal(ppn(0))} perm:${perm(0)}\n")
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
index b6c15c329f6..4ab437ed34e 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
@@ -585,13 +585,7 @@ class LoadMisalignBuffer(implicit p: Parameters) extends XSModule
       splitLoadResp(curPtr).vaddr,
       splitLoadResp(curPtr).fullva),
     shouldOverwrite)
-  val overwriteGpaddr = RegEnable(
-    Mux(
-      cross16BytesBoundary && (curPtr === 1.U),
-      // when cross-page, offset should always be 0
-      Cat(get_pn(splitLoadResp(curPtr).gpaddr), get_off(0.U(splitLoadResp(curPtr).gpaddr.getWidth.W))),
-      splitLoadResp(curPtr).gpaddr),
-    shouldOverwrite)
+  val overwriteGpaddr = RegEnable(splitLoadResp(curPtr).gpaddr, shouldOverwrite)
   val overwriteIsHyper = RegEnable(splitLoadResp(curPtr).isHyper, shouldOverwrite)
   val overwriteIsForVSnonLeafPTE = RegEnable(splitLoadResp(curPtr).isForVSnonLeafPTE, shouldOverwrite)
```
