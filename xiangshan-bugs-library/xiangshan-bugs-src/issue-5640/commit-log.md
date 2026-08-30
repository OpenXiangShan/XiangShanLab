# Commit Log
- Issue: #5640
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5640
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5640
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5640
- Changed files: 11
- Additions: 52
- Deletions: 23

## Files
- `.github/workflows/emu.yml`
- `scripts/xiangshan.py`
- `src/main/scala/xiangshan/XSCore.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`
- `src/main/scala/xiangshan/backend/rob/RobBundles.scala`
- `src/main/scala/xiangshan/mem/MemBlock.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala`
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala`
- `src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala`

## Diff
```diff
diff --git a/.github/workflows/emu.yml b/.github/workflows/emu.yml
index 081927264f1..1d3ffed098b 100644
--- a/.github/workflows/emu.yml
+++ b/.github/workflows/emu.yml
@@ -401,7 +401,8 @@ jobs:
           python3 $GITHUB_WORKSPACE/scripts/xiangshan.py --build \
             --num-cores 2 --emu-optimize "" \
             --dramsim3 /nfs/home/share/ci-workloads/DRAMsim3 --with-dramsim3 --threads 16 \
-            --pgo /nfs/home/share/ci-workloads/linux-hello-smp-new/bbl.bin --llvm-profdata llvm-profdata --trace-fst
+            --pgo /nfs/home/share/ci-workloads/linux-hello-smp-new/bbl.bin --llvm-profdata llvm-profdata \
+            --trace-fst --trace-all
       - name: MC Test
         run: |
           python3 $GITHUB_WORKSPACE/scripts/xiangshan.py --wave-dump $WAVE_HOME --threads 16 --numa --diff ./ready-to-run/riscv64-nemu-interpreter-dual-so --ci mc-tests 2> /dev/zero
diff --git a/scripts/xiangshan.py b/scripts/xiangshan.py
index 4289dcb6b81..0083a94940e 100644
--- a/scripts/xiangshan.py
+++ b/scripts/xiangshan.py
@@ -100,6 +100,7 @@ def __init__(self, args):
         self.pgo_max_cycle = args.pgo_max_cycle
         self.pgo_emu_args = args.pgo_emu_args
         self.llvm_profdata = args.llvm_profdata
+        self.emu_trace_all = 1 if args.trace_all else None
         # wave dump path
         if args.wave_dump is not None:
             self.set_wave_home(args.wave_dump)
@@ -145,6 +146,7 @@ def get_makefile_args(self):
             (self.pgo_emu_args,  "PGO_EMU_ARGS"),
             (self.llvm_profdata, "LLVM_PROFDATA"),
             (self.issue,         "ISSUE"),
+            (self.emu_trace_all, "EMU_TRACE_ALL"),
         ]
         args = filter(lambda arg: arg[0] is not None, makefile_args)
         args = [(shlex.quote(str(arg[0])), arg[1]) for arg in args] # shell escape
@@ -680,6 +682,7 @@ def get_free_cores(n):
     parser.add_argument('--make-threads', nargs='?', type=int, help='number of make threads', default=200)
     parser.add_argument('--trace', action='store_true', help='enable vcd waveform')
     parser.add_argument('--trace-fst', action='store_true', help='enable fst waveform')
+    parser.add_argument('--trace-all', action='store_true', help='enable EMU_TRACE_ALL for makefile')
     parser.add_argument('--config', nargs='?', type=str, help='config')
     parser.add_argument('--yaml-config', nargs='?', type=str, help='yaml config')
     parser.add_argument('--emu-optimize', nargs='?', type=str, help='verilator optimization letter')
diff --git a/src/main/scala/xiangshan/XSCore.scala b/src/main/scala/xiangshan/XSCore.scala
index aea38f8af5c..349715703b4 100644
--- a/src/main/scala/xiangshan/XSCore.scala
+++ b/src/main/scala/xiangshan/XSCore.scala
@@ -171,8 +171,10 @@ class XSCoreImp(outer: XSCoreBase) extends LazyModuleImp(outer)
   backend.io.mem.writebackHyuSta <> memBlock.io.mem_to_ooo.writebackHyuSta
   backend.io.mem.writebackStd <> memBlock.io.mem_to_ooo.writebackStd
   backend.io.mem.writebackVldu <> memBlock.io.mem_to_ooo.writebackVldu
-  backend.io.mem.robLsqIO.mmio := memBlock.io.mem_to_ooo.lsqio.mmio
-  backend.io.mem.robLsqIO.uop := memBlock.io.mem_to_ooo.lsqio.uop
+  backend.io.mem.robLsqIO.loadMmio := memBlock.io.mem_to_ooo.lsqio.loadMmio
+  backend.io.mem.robLsqIO.loadMmioUop := memBlock.io.mem_to_ooo.lsqio.loadMmioUop
+  backend.io.mem.robLsqIO.storeMmio := memBlock.io.mem_to_ooo.lsqio.storeMmio
+  backend.io.mem.robLsqIO.storeMmioUop := memBlock.io.mem_to_ooo.lsqio.storeMmioUop
 
   // memblock error exception writeback, 1 cycle after normal writeback
   backend.io.mem.s3_delayed_load_error := memBlock.io.mem_to_ooo.s3_delayed_load_error
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index 703d4333981..a42ffc5b4c5 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -554,11 +554,14 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   val writebackNum = PopCount(exuWBs.map(_.valid))
   XSInfo(writebackNum =/= 0.U, "writebacked %d insts\n", writebackNum)
 
-  for (i <- 0 until LoadPipelineWidth) {
-    when(RegNext(io.lsq.mmio(i))) {
-      robEntries(RegEnable(io.lsq.uop(i).robIdx, io.lsq.mmio(i)).value).mmio := true.B
+  for (i <- 0 until io.lsq.loadMmio.getWidth) {
+    when(RegNext(io.lsq.loadMmio(i))) {
+      robEntries(RegEnable(io.lsq.loadMmioUop(i).robIdx, io.lsq.loadMmio(i)).value).mmio := true.B
     }
   }
+  when(RegNext(io.lsq.storeMmio)) {
+    robEntries(RegEnable(io.lsq.storeMmioUop.robIdx, io.lsq.storeMmio).value).mmio := true.B
+  }
 
 
   /**
diff --git a/src/main/scala/xiangshan/backend/rob/RobBundles.scala b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
index 33dc6666c83..18316396766 100644
--- a/src/main/scala/xiangshan/backend/rob/RobBundles.scala
+++ b/src/main/scala/xiangshan/backend/rob/RobBundles.scala
@@ -245,9 +245,11 @@ class RobLsqIO(implicit p: Parameters) extends XSBundle {
   val pendingPtr = Output(new RobPtr)
   val pendingPtrNext = Output(new RobPtr)
 
-  val mmio = Input(Vec(LoadPipelineWidth, Bool()))
-  // Todo: what's this?
-  val uop = Input(Vec(LoadPipelineWidth, new DynInst))
+  // lsq to rob for mmio flag
+  val loadMmio = Input(Vec(LoadPipelineWidth, Bool()))
+  val loadMmioUop = Input(Vec(LoadPipelineWidth, new DynInst))
+  val storeMmio = Input(Bool())
+  val storeMmioUop = Input(new DynInst)
 }
 
 class RobEnqIO(implicit p: Parameters) extends XSBundle {
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index 3276309bf3a..9c7f055ce9d 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -149,8 +149,10 @@ class mem_to_ooo(implicit p: Parameters) extends MemBlockBundle {
     val vl = Output(UInt((log2Up(VLEN) + 1).W))
     val gpaddr = Output(UInt(XLEN.W))
     val isForVSnonLeafPTE = Output(Bool())
-    val mmio = Output(Vec(LoadPipelineWidth, Bool()))
-    val uop = Output(Vec(LoadPipelineWidth, new DynInst))
+    val loadMmio = Output(Vec(LoadPipelineWidth, Bool()))
+    val loadMmioUop = Output(Vec(LoadPipelineWidth, new DynInst))
+    val storeMmio = Output(Bool())
+    val storeMmioUop = Output(new DynInst)
     val lqCanAccept = Output(Bool())
     val sqCanAccept = Output(Bool())
   }
@@ -1404,8 +1406,11 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   lsq.io.uncacheOutstanding := io.ooo_to_mem.csrCtrl.uncache_write_outstanding_enable
 
   // Lsq
-  io.mem_to_ooo.lsqio.mmio       := lsq.io.rob.mmio
-  io.mem_to_ooo.lsqio.uop        := lsq.io.rob.uop
+  io.mem_to_ooo.lsqio.loadMmio     := lsq.io.rob.loadMmio
+  io.mem_to_ooo.lsqio.loadMmioUop  := lsq.io.rob.loadMmioUop
+  io.mem_to_ooo.lsqio.storeMmio    := lsq.io.rob.storeMmio
+  io.mem_to_ooo.lsqio.storeMmioUop := lsq.io.rob.storeMmioUop
+
   lsq.io.rob.lcommit             := io.ooo_to_mem.lsqio.lcommit
   lsq.io.rob.scommit             := io.ooo_to_mem.lsqio.scommit
   lsq.io.rob.pendingMMIOld       := io.ooo_to_mem.lsqio.pendingMMIOld
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
index d97f9f408b2..f272ca8bea0 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala
@@ -254,6 +254,11 @@ class LsqWrapper(implicit p: Parameters) extends XSModule with HasDCacheParamete
   io.exceptionAddr.gpaddr := Mux(RegNext(io.exceptionAddr.isStore), storeQueue.io.exceptionAddr.gpaddr, loadQueue.io.exceptionAddr.gpaddr)
   io.exceptionAddr.isForVSnonLeafPTE:= Mux(RegNext(io.exceptionAddr.isStore), storeQueue.io.exceptionAddr.isForVSnonLeafPTE, loadQueue.io.exceptionAddr.isForVSnonLeafPTE)
   io.issuePtrExt := storeQueue.io.stAddrReadySqPtr
+  io.rob.loadMmio := loadQueue.io.rob.loadMmio
+  io.rob.loadMmioUop := loadQueue.io.rob.loadMmioUop
+  io.rob.storeMmio := storeQueue.io.rob.storeMmio
+  io.rob.storeMmioUop := storeQueue.io.rob.storeMmioUop
+
 
   // naive uncache arbiter
   val s_idle :: s_load :: s_store :: Nil = Enum(3)
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
index f6c6af25b11..2e42c292247 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala
@@ -135,8 +135,10 @@ class LoadMisalignBuffer(implicit p: Parameters) extends XSModule
     val loadMisalignFull = Output(Bool())
   })
 
-  io.rob.mmio := 0.U.asTypeOf(Vec(LoadPipelineWidth, Bool()))
-  io.rob.uop  := 0.U.asTypeOf(Vec(LoadPipelineWidth, new DynInst))
+  io.rob.loadMmio := DontCare
+  io.rob.loadMmioUop  := DontCare
+  io.rob.storeMmio := DontCare
+  io.rob.storeMmioUop  := DontCare
 
   val req_valid = RegInit(false.B)
   val req = Reg(new LqWriteBundle)
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
index bdf1ac9a16e..056318110bb 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala
@@ -162,8 +162,10 @@ class UncacheEntry(entryIndex: Int)(implicit p: Parameters) extends XSModule
 
   /* control */
   io.flush := flush
-  io.rob.mmio := DontCare
-  io.rob.uop := DontCare
+  io.rob.loadMmio := DontCare
+  io.rob.loadMmioUop := DontCare
+  io.rob.storeMmio := DontCare
+  io.rob.storeMmioUop := DontCare
   io.mmioSelect := (uncacheState =/= s_idle) && req.mmio
   io.slaveId.valid := slaveAccept
   io.slaveId.bits := slaveId
@@ -498,8 +500,8 @@ class LoadQueueUncache(implicit p: Parameters) extends XSModule
 
   // rob
   for (i <- 0 until LoadPipelineWidth) {
-    io.rob.mmio(i) := RegNext(s1_valid(i) && s1_req(i).mmio)
-    io.rob.uop(i) := RegEnable(s1_req(i).uop, s1_valid(i))
+    io.rob.loadMmio(i) := RegNext(s1_valid(i) && s1_req(i).mmio)
+    io.rob.loadMmioUop(i) := RegEnable(s1_req(i).uop, s1_valid(i))
   }
 
 
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
index 1da04a0786e..49cc82d0b17 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala
@@ -121,8 +121,10 @@ class StoreMisalignBuffer(implicit p: Parameters) extends XSModule
     val toVecSplit = Output(new MisBuffertoVecSplitIO) // robIdx in misalignedBuffer
   })
 
-  io.rob.mmio := 0.U.asTypeOf(Vec(LoadPipelineWidth, Bool()))
-  io.rob.uop  := 0.U.asTypeOf(Vec(LoadPipelineWidth, new DynInst))
+  io.rob.loadMmio := DontCare
+  io.rob.loadMmioUop  := DontCare
+  io.rob.storeMmio := DontCare
+  io.rob.storeMmioUop  := DontCare
 
   class StoreMisalignBufferEntry(implicit p: Parameters) extends LsPipelineBundle {
     val portIndex = UInt(log2Up(enqPortNum).W)
diff --git a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
index 1910325f80d..d522343e17d 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala
@@ -307,8 +307,8 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   val ncPtr = Mux(io.uncacheOutstanding, ncSlaveAckMid, ncWaitRespPtrReg)
 
   // store can be committed by ROB
-  io.rob.mmio := DontCare
-  io.rob.uop := DontCare
+  io.rob.loadMmio := DontCare
+  io.rob.loadMmioUop := DontCare
 
   // Read dataModule
   assert(EnsbufferWidth <= 2)
@@ -899,6 +899,8 @@ class StoreQueue(implicit p: Parameters) extends XSModule
   mmioReq.bits.nc := false.B
   mmioReq.bits.id := rdataPtrExt(0).value
 
+  io.rob.storeMmio := mmioReq.valid
+  io.rob.storeMmioUop  := uncacheUop
   /**
     * NC Store
     * (1) req: when it has been commited, it can be sent to lower level.
```
