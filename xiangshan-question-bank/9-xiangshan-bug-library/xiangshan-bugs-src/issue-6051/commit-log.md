# Commit Log
- Issue: #6051
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6051
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6051
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6051
- Changed files: 5
- Additions: 5
- Deletions: 23

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMP.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/PMAEntryModule.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala
index 97888ca9d28..f6fac4a0f69 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMA.scala
@@ -30,8 +30,7 @@ trait CSRPMA { self: NewCSR =>
   val pmaaddr: Seq[CSRModule[_]] = Range(0, p(PMParameKey).NumPMAReal).map(num =>
     Module(new CSRModule(s"Pmaaddr$num") with HasPMAAddrSink {
       // read condition
-      regOut := addrRegOut(num)
-      rdata := addrRData(num)
+      regOut := addrRData(num)
     })
       .setAddr(CSRConst.PmaaddrBase + num)
   )
@@ -78,7 +77,6 @@ trait HasPMACfgRSink { self: CSRModule[_] =>
 
 trait HasPMAAddrSink { self: CSRModule[_] =>
   val addrRData = IO(Input(Vec(p(PMParameKey).NumPMAReal, UInt(64.W))))
-  val addrRegOut = IO(Input(Vec(p(PMParameKey).NumPMAReal, UInt(64.W))))
 }
 
 trait PMAInit extends HasPMParameters with PMAReadWrite {
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMP.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMP.scala
index 5f6a473e1af..58c62b0bd55 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMP.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPMP.scala
@@ -44,7 +44,7 @@ trait CSRPMP { self: NewCSR =>
     if (num < p(PMParameKey).NumPMPReal) {
       Module(new CSRModule(s"Pmpaddr$num", new PMPAddrBundle) with HasPMPAddrSink {
         // read condition
-        rdata := addrRData(num)
+        regOut := addrRData(num)
       })
         .setAddr(CSRs.pmpaddr0 + num)
     } else {
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 3bf4fcb5a2b..de9e2b3c2a1 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -446,7 +446,6 @@ class NewCSR(implicit val p: Parameters) extends Module
   // PMP
   val pmpEntryMod = Module(new PMPEntryHandleModule)
   pmpEntryMod.io.in.pmpCfg  := pmpcfgs.map(_.regOut.asInstanceOf[PMPCfgBundle])
-  pmpEntryMod.io.in.pmpAddr := pmpaddr.take(NumPMPReal).map(_.regOut.asInstanceOf[PMPAddrBundle])
   pmpEntryMod.io.in.ren   := ren
   pmpEntryMod.io.in.wen   := wenLegalReg
   pmpEntryMod.io.in.addr  := addr
@@ -568,13 +567,6 @@ class NewCSR(implicit val p: Parameters) extends Module
     mod.w.wdata := pmpEntryMod.io.out.pmpCfgWData(8*((i%8)+1)-1,8*(i%8))
   }
 
-  pmpaddr.zipWithIndex.foreach { case (mod, i) =>
-    if (i < NumPMPReal) {
-      mod.w.wen   := wenLegalReg && (addr === (CSRs.pmpaddr0 + i).U)
-      mod.w.wdata := pmpEntryMod.io.out.pmpAddrWData(i)
-    }
-  }
-
   pmacfgs.zipWithIndex.foreach { case (mod, i) =>
     mod.w.wen   := wenLegalReg && (addr === (CSRConst.PmacfgBase + i / 8 * 2).U)
     mod.w.wdata := pmaEntryMod.io.out.pmaCfgWdata(8*((i%8)+1)-1,8*(i%8))
@@ -705,7 +697,6 @@ class NewCSR(implicit val p: Parameters) extends Module
     mod match {
       case m: HasPMAAddrSink =>
         m.addrRData := pmaEntryMod.io.out.pmaAddrRData
-        m.addrRegOut := pmaEntryMod.io.out.pmaAddrRegOut
       case _ =>
     }
     mod match {
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/PMAEntryModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/PMAEntryModule.scala
index 9316ff75aa7..c80c8392c52 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/PMAEntryModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/PMAEntryModule.scala
@@ -92,7 +92,6 @@ class PMAEntryHandleModule(implicit p: Parameters) extends PMAModule with PMAIni
   }
 
   io.out.pmaAddrRData := pmaAddrR
-  io.out.pmaAddrRegOut := pmaAddr
 
 }
 
@@ -108,7 +107,6 @@ class PMAEntryHandleIOBundle(implicit p: Parameters) extends PMABundle {
   val out = Output(new Bundle {
     val pmaCfgWdata = UInt(PMXLEN.W)
     val pmaAddrRData = Vec(NumPMAReal, UInt(64.W))
-    val pmaAddrRegOut = Vec(NumPMAReal, UInt(64.W))
   })
 }
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala
index 51cb07a19ea..0ba7e1f3bea 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/PMPEntryModule.scala
@@ -19,13 +19,13 @@ class PMPEntryHandleModule(implicit p: Parameters) extends PMPModule {
   val io = IO(new PMPEntryHandleIOBundle)
 
   val pmpCfg   = io.in.pmpCfg
-  val pmpAddr  = io.in.pmpAddr
 
   val ren   = io.in.ren
   val wen   = io.in.wen
   val addr  = io.in.addr
   val wdata = io.in.wdata
 
+  val pmpAddr  = RegInit(VecInit(Seq.fill(p(PMParameKey).NumPMPReal)(0.U.asTypeOf(new PMPAddrBundle))))
   val pmpMask  = RegInit(VecInit(Seq.fill(p(PMParameKey).NumPMPReal)(0.U(PMPAddrBits.W))))
 
   val pmpEntry = Wire(Vec(p(PMParameKey).NumPMPReal, new PMPEntry))
@@ -58,22 +58,20 @@ class PMPEntryHandleModule(implicit p: Parameters) extends PMPModule {
 
   io.out.pmpCfgWData := Cat(cfgVec.map(_.asUInt).reverse)
 
-  val pmpAddrW = Wire(Vec(p(PMParameKey).NumPMPReal, UInt(64.W)))
   val pmpAddrR = Wire(Vec(p(PMParameKey).NumPMPReal, UInt(64.W)))
 
   for (i <- 0 until p(PMParameKey).NumPMPReal) {
-    pmpAddrW(i) := pmpEntry(i).addr.ADDRESS.asUInt
     pmpAddrR(i) := pmpEntry(i).addr.ADDRESS.asUInt
     // write pmpAddr
     when (wen && (addr === (CSRs.pmpaddr0 + i).U)) {
       if (i != (p(PMParameKey).NumPMPReal - 1)) {
         val addrNextLocked: Bool = PMPCfgLField.addrLocked(pmpEntry(i).cfg, pmpEntry(i + 1).cfg)
         pmpMask(i) := Mux(!addrNextLocked, pmpEntry(i).matchMask(wdata), pmpEntry(i).mask)
-        pmpAddrW(i) := Mux(!addrNextLocked, wdata, pmpEntry(i).addr.ADDRESS.asUInt)
+        pmpAddr(i) := Mux(!addrNextLocked, wdata, pmpEntry(i).addr.ADDRESS.asUInt)
       } else {
         val addrLocked: Bool = PMPCfgLField.addrLocked(pmpEntry(i).cfg)
         pmpMask(i) := Mux(!addrLocked, pmpEntry(i).matchMask(wdata), pmpEntry(i).mask)
-        pmpAddrW(i) := Mux(!addrLocked, wdata, pmpEntry(i).addr.ADDRESS.asUInt)
+        pmpAddr(i) := Mux(!addrLocked, wdata, pmpEntry(i).addr.ADDRESS.asUInt)
       }
     }
     // read pmpAddr
@@ -82,7 +80,6 @@ class PMPEntryHandleModule(implicit p: Parameters) extends PMPModule {
     }
   }
 
-  io.out.pmpAddrWData := pmpAddrW
   io.out.pmpAddrRData := pmpAddrR
 
 }
@@ -94,13 +91,11 @@ class PMPEntryHandleIOBundle(implicit p: Parameters) extends PMPBundle {
     val addr  = UInt(12.W)
     val wdata = UInt(64.W)
     val pmpCfg  = Vec(NumPMPReal, new PMPCfgBundle)
-    val pmpAddr = Vec(NumPMPReal, new PMPAddrBundle)
   })
 
   val out = Output(new Bundle {
     val pmpCfgWData  = UInt(PMXLEN.W)
     val pmpAddrRData = Vec(NumPMPReal, UInt(64.W))
-    val pmpAddrWData = Vec(NumPMPReal, UInt(64.W))
   })
 }
```
