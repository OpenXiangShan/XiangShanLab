# Commit Log
- Issue: #6100
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6100
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6100
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6100
- Changed files: 5
- Additions: 83
- Deletions: 58

## Files
- `difftest`
- `ready-to-run`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/difftest b/difftest
index b3d40d3264c..5d7d90bd6dc 160000
--- a/difftest
+++ b/difftest
@@ -1 +1 @@
-Subproject commit b3d40d3264caa6c6b10cf083fad5893a8a5fc994
+Subproject commit 5d7d90bd6dcd7fede90183fc070f5e35897c6081
diff --git a/ready-to-run b/ready-to-run
index 8be711d0fe3..7e07aca9bc4 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit 8be711d0fe326d47367066150783b681a5e9f9ed
+Subproject commit 7e07aca9bc4662acc1dbd24d29b8d1cb564efc69
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala
index 858e6f37670..dad0a141123 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRAIA.scala
@@ -13,7 +13,63 @@ import xiangshan.XSBundle
 
 import scala.collection.immutable.SeqMap
 
-trait CSRAIA { self: NewCSR with HypervisorLevel =>
+trait CSRAIA extends HasSoCParameter { self: NewCSR with HypervisorLevel =>
+  private def fieldWritableMap(fields: Seq[CSREnumType]): Map[Int, Boolean] =
+    fields.map(f => f.lsb -> !f.isRO).toMap
+
+  private def validFieldWritableMap(fields: Seq[Valid[CSREnumType]]): Map[Int, Boolean] =
+    fields.map(f => f.bits.lsb -> !f.bits.isRO).toMap
+
+  private def iprioMask(base: Int, writableMaps: Seq[Map[Int, Boolean]]): UInt = {
+    val mask = (0 until 8).foldLeft(BigInt(0)) { case (m, i) =>
+      val intNo = base + i
+      val writable = writableMaps.exists(_.getOrElse(intNo, false))
+      if (writable) m | (BigInt(0xff) << (8 * i)) else m
+    }
+    mask.U(64.W)
+  }
+
+  private lazy val mieFieldWritable: Map[Int, Boolean] = {
+    val m = fieldWritableMap(new MieBundle().getFields)
+    if (soc.IMSICParams.HasTEEIMSIC) m else m.updated(InterruptNO.ASNI, false)
+  }
+
+  /*
+    For a given interrupt number, if the corresponding bit in mie is read-only zero,
+    then the interrupt’s priority number in the iprio array must be read-only zero as well.
+    static mask
+  */
+  private def miprioMask(base: Int): UInt =
+    iprioMask(base, Seq(mieFieldWritable))
+
+  private def gatedWritable(writableMap: Map[Int, Boolean], intNo: Int, gate: Bool): Bool =
+    if (writableMap.getOrElse(intNo, false)) gate else false.B
+
+  /*
+    For a given interrupt number, if the corresponding bit is not writable either in sie or,
+    if the H extension is implemented, in hie,
+    then the interrupt’s priority number in the supervisor-level iprio array must be read-only zero as well.
+    Dynamic mask
+  */
+  private def siprioMask(base: Int, midelegBits: UInt, mvienBits: UInt): UInt = {
+    val sieRegWritable = fieldWritableMap(new SieBundle().getFields)
+    val sieToMieWritable = validFieldWritableMap(new SieToMie().getAll)
+    val hieToMieWritable = validFieldWritableMap(new HieToMie().getAll)
+
+    Cat((0 until 8).reverse.map { i =>
+      val intNo = base + i
+      val delegated = midelegBits(intNo)
+      val virtualized = mvienBits(intNo)
+      val writableInSie =
+        gatedWritable(sieToMieWritable, intNo, delegated) ||
+        gatedWritable(sieRegWritable, intNo, !delegated && virtualized)
+      val writableInHie =
+        gatedWritable(hieToMieWritable, intNo, delegated)
+
+      Fill(8, writableInSie || writableInHie)
+    })
+  }
+
   val miselect = Module(new CSRModule("Miselect", new MISelectBundle) with HasISelectBundle {
     private val value = reg.ALL.asUInt
     inIMSICRange := value >= 0x70.U && value < 0x100.U
@@ -93,60 +149,36 @@ trait CSRAIA { self: NewCSR with HypervisorLevel =>
   })
     .setAddr(CSRs.vstopi)
 
-  val miprio0 = Module(new CSRModule(s"Iprio0", new Iprio0Bundle) with HasIeBundle {
-    val mask = Wire(Vec(8, UInt(8.W)))
-    for (i <- 0 until 8) {
-      mask(i) := Fill(8, mie.asUInt(i))
-    }
-    regOut := reg & mask.asUInt
+  val miprio0 = Module(new CSRModule(s"Iprio0", new Iprio0Bundle) {
+    regOut := reg & miprioMask(0)
   })
     .setAddr(0x30)
 
-  val miprio2 = Module(new CSRModule(s"Iprio2", new MIprio2Bundle) with HasIeBundle {
-    val mask = Wire(Vec(8, UInt(8.W)))
-    for (i <- 0 until 8) {
-      mask(i) := Fill(8, mie.asUInt(i+8))
-    }
-    regOut := reg & mask.asUInt
+  val miprio2 = Module(new CSRModule(s"Iprio2", new MIprio2Bundle) {
+    regOut := reg & miprioMask(8)
   })
     .setAddr(0x32)
 
   val miprios: Seq[CSRModule[_]] = (4 to (0xF, 2)).map(num =>
-    Module(new CSRModule(s"Iprio$num", new IprioBundle) with HasIeBundle {
-      val mask = Wire(Vec(8, UInt(8.W)))
-      for (i <- 0 until 8) {
-        mask(i) := Fill(8, mie.asUInt(num*4+i))
-      }
-      regOut := reg & mask.asUInt
+    Module(new CSRModule(s"Iprio$num", new IprioBundle) {
+      regOut := reg & miprioMask(num * 4)
     })
       .setAddr(0x30 + num)
   )
 
-  val siprio0 = Module(new CSRModule(s"Iprio0", new Iprio0Bundle) with HasIeBundle {
-    val mask = Wire(Vec(8, UInt(8.W)))
-    for (i <- 0 until 8) {
-      mask(i) := Fill(8, sie.asUInt(i))
-    }
-    regOut := reg & mask.asUInt
+  val siprio0 = Module(new CSRModule(s"Iprio0", new Iprio0Bundle) with HasSiprios {
+    regOut := reg & siprioMask(0, mideleg.asUInt, mvien.asUInt)
   })
     .setAddr(0x30)
 
-  val siprio2 = Module(new CSRModule(s"Iprio2", new SIprio2Bundle) with HasIeBundle {
-    val mask = Wire(Vec(8, UInt(8.W)))
-    for (i <- 0 until 8) {
-      mask(i) := Fill(8, sie.asUInt(i+8))
-    }
-    regOut := reg & mask.asUInt
+  val siprio2 = Module(new CSRModule(s"Iprio2", new SIprio2Bundle) with HasSiprios {
+    regOut := reg & siprioMask(8, mideleg.asUInt, mvien.asUInt)
   })
     .setAddr(0x32)
 
   val siprios: Seq[CSRModule[_]] = (4 to (0xF, 2)).map(num =>
-    Module(new CSRModule(s"Iprio$num", new IprioBundle) with HasIeBundle{
-      val mask = Wire(Vec(8, UInt(8.W)))
-      for (i <- 0 until 8) {
-        mask(i) := Fill(8, sie.asUInt(num*4+i))
-      }
-      regOut := reg & mask.asUInt
+    Module(new CSRModule(s"Iprio$num", new IprioBundle) with HasSiprios {
+      regOut := reg & siprioMask(num * 4, mideleg.asUInt, mvien.asUInt)
     })
     .setAddr(0x30 + num)
   )
@@ -155,8 +187,6 @@ trait CSRAIA { self: NewCSR with HypervisorLevel =>
 
   val siregiprios: Seq[CSRModule[_]] = Seq(siprio0, siprio2) ++: siprios
 
-  val iregiprios = miregiprios ++ siregiprios
-
   val aiaCSRMods = Seq(
     miselect,
     mireg,
@@ -353,7 +383,7 @@ trait HasIregSink { self: CSRModule[_] =>
   }))
 }
 
-trait HasIeBundle { self: CSRModule[_] =>
-  val mie = IO(Input(new MieBundle))
-  val sie = IO(Input(new SieBundle))
+trait HasSiprios { self: CSRModule[_] =>
+  val mideleg = IO(Input(new MidelegBundle))
+  val mvien = IO(Input(new MvienBundle))
 }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index bddea83bd9d..9fc8a5f75a2 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -427,11 +427,9 @@ trait MachineLevel { self: NewCSR =>
     with TrapEntryMNEventSinkBundle
     with MNretEventSinkBundle{
     // NMIE write 0 with no effect
-    // as opensbi not support smrnmi, we init nmie with 1,and allow software to set nmie close for testing
-    // Attension, when set nmie to zero ,do not cause double trap when nmi interrupt has triggered
-//    when(!wdata.NMIE.asBool) {
-//      reg.NMIE := reg.NMIE
-//    }
+    when(wen && !wdata.NMIE.asBool) {
+      reg.NMIE := reg.NMIE
+    }
   }).setAddr(CSRs.mnstatus)
   val mnscratch = Module(new CSRModule("Mnscratch", new ScratchBundle("Scratch register for resumable NMI handlers.")))
     .setAddr(CSRs.mnscratch)
@@ -530,7 +528,7 @@ class MstatusBundle extends CSRBundle {
   val MBE  = CSRROField     (37).withReset(0.U).withDescription("M-mode endianness selector.")
   val GVA  = CSRRWField     (38).withReset(0.U).withDescription("Indicates that trap information was derived from a guest virtual address.")
   val MPV  = VirtMode       (39).withReset(0.U).withDescription("Saved virtualization mode from before trap entry to M-mode.")
-  val MDT  = CSRRWField     (42).withReset(mdtInit.U).withDescription("M-mode disable-trap bit used by the Smdbltrp extension.")
+  val MDT  = CSRRWField     (42).withReset(1.U).withDescription("M-mode disable-trap bit used by the Smdbltrp extension.")
   val SD   = CSRROField     (63,
     (_, _) => FS === ContextStatus.Dirty || VS === ContextStatus.Dirty
   ).withDescription("Dirty summary bit for the floating-point or vector context.")
@@ -602,8 +600,7 @@ class MstatusModule(implicit override val p: Parameters) extends CSRModule("MSta
 }
 
 class MnstatusBundle extends CSRBundle {
-  // OpenSBI does not support Smrnmi yet, so NMIE resets enabled for bring-up.
-  val NMIE   = CSRRWField  (3).withReset(1.U).withDescription("Enable non-maskable interrupt handling.")
+  val NMIE   = CSRRWField  (3).withReset(0.U).withDescription("Enable non-maskable interrupt handling.")
   val MNPV   = VirtMode    (7).withReset(0.U).withDescription("Saved virtualization mode for resumable NMI handling.")
   val MNPELP = RO          (9).withReset(0.U).withDescription("Saved landing-pad state for resumable NMI handling.")
   val MNPP   = PrivMode    (12, 11).withReset(PrivMode.U).withDescription("Saved privilege level for resumable NMI handling.")
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 8ad12c6f1e0..4afb4cd27b2 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -67,8 +67,6 @@ object CSRConfig {
   final val EXT_DBLTRP = true
 
   final val PPNLength = 44
-  // TODO: as current test not support clean mdt , we set mstatus->mdt = 0 to allow exception in m-mode
-  final val mdtInit = 0
 
 }
 
@@ -554,11 +552,11 @@ class NewCSR(implicit val p: Parameters) extends Module
     mod.w.wdata := wdata
   }
 
-  iregiprios.foreach { mod =>
+  siregiprios.foreach { mod =>
     mod match {
-      case m: HasIeBundle =>
-        m.mie := mie.regOut
-        m.sie := sie.regOut
+      case m: HasSiprios =>
+        m.mideleg := mideleg.regOut
+        m.mvien   := mvien.regOut
       case _ =>
     }
   }
```
