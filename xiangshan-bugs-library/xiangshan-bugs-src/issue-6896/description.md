## Before start
- [x] I have read the RISC-V ISA Manual and XiangShan Documents, and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs and did not find anything relevant.
- [x] I have reproduced the issue using the latest commit on the default branch.
- [x] If this report was generated with AI assistance, I have verified the correctness of its content.

## Branch
kunminghu-v3

## Describe the bug

With DefaultConfig, the DCache has two memory channels (`WithNKBL1D(64, ways = 4, numMemChannels = 2)`, `channelSelByAddr = true`). Each channel is a separate TileLink client whose visibility covers only its half of the block addresses ([DCacheWrapper.scala#L1017-L1035](https://github.com/OpenXiangShan/XiangShan/blob/a253c1163982ae0b485b3a2ed9b61253d980d837/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L1017-L1035)).

`CMOUnit` selects the channel like this:

https://github.com/OpenXiangShan/XiangShan/blob/a253c1163982ae0b485b3a2ed9b61253d980d837/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L415

```scala
io.channel_sel := Mux(RegNext(io.req.fire), selectChannel(req.address), 0.U)
```

`RegNext(io.req.fire)` is high only in the first `s_sreq` cycle. If the A request is not accepted in that cycle, the unit stays in `s_sreq` (with `io.req_chanA.valid` still high) but `channel_sel` drops to 0. `demuxByChannel` ([MissQueue.scala#L2023](https://github.com/OpenXiangShan/XiangShan/blob/a253c1163982ae0b485b3a2ed9b61253d980d837/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L2023)) then moves the request to channel 0. The A request is not accepted in its first cycle in two cases:
- the fabric input or the target slice's A queue is not ready (other clients, a full queue, or the SpecMiss round-robin at each DCache input, [CustomL1L2Fabric.scala#L159](https://github.com/OpenXiangShan/XiangShan/blob/a253c1163982ae0b485b3a2ed9b61253d980d837/src/main/scala/xiangshan/CustomL1L2Fabric.scala#L159));
- `io.wfi.wfiReq` is high.

The fabric routes A by address, using only the slices that the source client can see ([CustomL1L2Fabric.scala#L184](https://github.com/OpenXiangShan/XiangShan/blob/a253c1163982ae0b485b3a2ed9b61253d980d837/src/main/scala/xiangshan/CustomL1L2Fabric.scala#L184), `TLXbar.circuit`). Channel 0 can only reach the even slices, so a CBO for an odd block is delivered to an even L2 slice. In simulation (below), the real `DCache` and the real `CustomL1L2Fabric` deliver `cbo.flush 0x80001040` (slice 1) to slice 0, and `0x800010c0` (slice 3) to slice 2. The CBOAck from that slice completes the instruction normally.

What happens next is from reading the code; I did not simulate the L2:
- CoupledL2 drops the bank bits when it parses the address ([CoupledL2.scala#L228-L233](https://github.com/OpenXiangShan/XSCache/blob/a041f0750e8aa2a831298977e3fac3fbdde7c93b/src/main/scala/coupledL2/CoupledL2.scala#L228-L233)). The wrong slice therefore operates on a different line, which here is the adjacent line with block bits [7:6] = 00/10.
- The line the CBO names is never cleaned, flushed or invalidated in L2.
- For `cbo.inval`, the aliased line is invalidated with an `Evict` (MSHR.scala L556), which can discard that unrelated line's dirty data.

## Expected behavior

The CBO is sent on the channel selected by its address until it is accepted, whatever the back-pressure.

## Environment
- XiangShan `a253c1163982ae0b485b3a2ed9b61253d980d837`, `DefaultConfig`. The current kunminghu-v3 HEAD `1e05481a4` adds two commits (FTQ/RAS and MMU) that do not touch the DCache or the fabric; `MissQueue.scala` L415 is unchanged.
- Chisel 7.13.0 / firtool 1.149.0 (XiangShan build), Verilator 5.052, OpenJDK 21, macOS arm64.

## To Reproduce

The files are below (collapsed). `CmoRepro.scala` elaborates the real `DCache` LazyModule and the real `CustomL1L2Fabric`, built with the same arguments as `L2Top.scala:79-84`. They are connected to four TileLink manager ports with `BankBinder(4, 64)` address sets, which stand in for the L2 slices, plus one Get-only client that stands in for L1I/PTW. The testbench issues a CBO through `io.lsu.cmoOpReq` and records which slice port receives it. In the TEST cases, the target slice is busy (`a.ready = 0`) and its fabric A queue is already full, so the CBO is not accepted in its first cycle.

`run.sh` has two modes: Mode B (prebuilt XiangShan classes plus scalac; this is what I ran) and Mode A (`XS_HOME=/path/to/XiangShan ./run.sh`, building through mill in a checkout; written but not run by me). Each elaboration takes about 30 minutes with a 16 GB JVM heap. `gen_dpi.py` generates stubs for the Constantin DPI readers and `split.py` splits firtool's output.

ORIG:
```
CTRL1 odd block, no backpressure            cbo addr=0x80001040 (-> slice 1)  received by slice 1 addr=0x80001040 src=17  CBOAck->LSU resp=1  [ok]
CTRL2 even block, slice busy 4 cycles       cbo addr=0x80001080 (-> slice 2)  received by slice 2 addr=0x80001080 src=81  CBOAck->LSU resp=1  [ok]
TEST1 odd block (slice 1), slice busy 4 cyc cbo addr=0x80001040 (-> slice 1)  received by slice 0 addr=0x80001040 src=81  CBOAck->LSU resp=1  [FAIL]
    A-valid/ready at the two DCache client ports: [c0 ch0 v0 r1 | ch1 v1 r0] [c1 ch0 v1 r1 | ch1 v0 r0] ...
TEST2 odd block (slice 3), slice busy 4 cyc cbo addr=0x800010c0 (-> slice 3)  received by slice 2 addr=0x800010c0 src=81  CBOAck->LSU resp=1  [FAIL]
TEST3 odd block (slice 1), slice busy 1 cyc cbo addr=0x80001040 (-> slice 1)  received by slice 0 addr=0x80001040 src=81  CBOAck->LSU resp=1  [FAIL]
errors=3
```
FIX: all five cases arrive at the address-selected slice, `errors=0`.


**Reproducer files**

<details><summary><code>CmoRepro.scala</code></summary>

```scala
package xiangshan.cache
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import xiangshan._

// Real DCache LazyModule (DefaultConfig: numMemChannels=2, channelSelByAddr=true, enMissTrack=true)
// -> real CustomL1L2Fabric (constructed exactly as in L2Top.scala:79-84)
// -> 4 TL manager ports with the address sets BankBinder(4, 64) produces for 4 L2 slices.
// A third "icache" Get client stands in for the other fabric clients (L1I/PTW).
class CmoLM(implicit p: Parameters) extends LazyModule with HasDCacheParameters {
  val dcache = LazyModule(new DCache())
  val fabric = LazyModule(new CustomL1L2Fabric(
    specChannels = numMemChannels, specOwners = LoadPipelineWidth,
    specIdBits = log2Up(cfg.nMissEntries), specBlockBytes = cfg.blockBytes))
  val other = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(name = "icache", sourceId = IdRange(0, 4))))))
  dcache.clientNodes.foreach(c => fabric.node := c)
  fabric.node := other
  fabric.specSink.get := dcache.specFabricSource.get
  val ctrlClient = dcache.cacheCtrlOpt.map { u =>
    val c = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(name = "ctrl")))))
    u.node := TLFragmenter(8, 64) := TLWidthWidget(32) := c; c }
  val nBanks = 4
  val slices = Seq.tabulate(nBanks) { i =>
    val addr = AddressSet(0x80000000L, 0x7fffffffL).intersect(AddressSet(i * 64, ~BigInt((nBanks - 1) * 64))).toSeq
    TLManagerNode(Seq(TLSlavePortParameters.v1(Seq(TLSlaveParameters.v1(
      address = addr, regionType = RegionType.CACHED, executable = true,
      supportsAcquireT = TransferSizes(1, 64), supportsAcquireB = TransferSizes(1, 64),
      supportsGet = TransferSizes(1, 64), supportsPutFull = TransferSizes(1, 64), supportsPutPartial = TransferSizes(1, 64),
      fifoId = None)), beatBytes = 32, endSinkId = 16)))
  }
  slices.foreach(_ := fabric.node)

  lazy val module = new LazyModuleImp(this) {
    val io = IO(new Bundle {
      val cmoValid = Input(Bool()); val cmoReady = Output(Bool())
      val cmoOp = Input(UInt(3.W)); val cmoAddr = Input(UInt(64.W))
      val cmoRespValid = Output(Bool()); val cmoRespAddr = Output(UInt(64.W))
      val sliceReady = Input(Vec(nBanks, Bool()))
      val sliceAValid = Output(Vec(nBanks, Bool())); val sliceAOp = Output(Vec(nBanks, UInt(3.W)))
      val sliceAAddr = Output(Vec(nBanks, UInt(48.W))); val sliceASrc = Output(Vec(nBanks, UInt(16.W)))
      val dValid = Input(Vec(nBanks, Bool())); val dSrc = Input(Vec(nBanks, UInt(16.W)))
      val otherValid = Input(Bool()); val otherReady = Output(Bool()); val otherAddr = Input(UInt(48.W)); val otherSrc = Input(UInt(2.W))
      val dcAValid = Output(Vec(numMemChannels, Bool())); val dcAReady = Output(Vec(numMemChannels, Bool()))
      val dcAAddr = Output(Vec(numMemChannels, UInt(48.W)))
    })
    def tieIn(x: Data): Unit = x match {
      case r: Record => r.elements.foreach { case (_, e) => tieIn(e) }
      case v: Vec[_] => v.foreach(e => tieIn(e))
      case e => if (chisel3.reflect.DataMirror.directionOf(e) == ActualDirection.Input) e := 0.U.asTypeOf(e)
    }
    val dio = dcache.module.io
    chisel3.reflect.DataMirror.modulePorts(dcache.module).foreach { case (n, x) => if (n == "io") tieIn(x) }
    dio.cmoOpReq.valid := io.cmoValid; dio.cmoOpReq.bits.opcode := io.cmoOp; dio.cmoOpReq.bits.address := io.cmoAddr
    io.cmoReady := dio.cmoOpReq.ready
    dio.cmoOpResp.ready := true.B
    io.cmoRespValid := dio.cmoOpResp.valid; io.cmoRespAddr := dio.cmoOpResp.bits.address
    for (i <- 0 until numMemChannels) {
      val b = fabric.node.in(i)._1
      io.dcAValid(i) := chisel3.util.experimental.BoringUtils.tapAndRead(b.a.valid)
      io.dcAReady(i) := chisel3.util.experimental.BoringUtils.tapAndRead(b.a.ready)
      io.dcAAddr(i) := chisel3.util.experimental.BoringUtils.tapAndRead(b.a.bits.address)
    }
    val (ob, oe) = other.out.head
    ob.a.valid := io.otherValid; io.otherReady := ob.a.ready
    ob.a.bits := oe.Get(io.otherSrc, io.otherAddr, 6.U)._2
    ob.d.ready := true.B; ob.b.ready := true.B; ob.c.valid := false.B; ob.c.bits := DontCare; ob.e.valid := false.B; ob.e.bits := DontCare
    ctrlClient.foreach { c => val (cb, _) = c.out.head
      cb.a.valid := false.B; cb.a.bits := DontCare; cb.d.ready := true.B
      cb.b.ready := true.B; cb.c.valid := false.B; cb.c.bits := DontCare; cb.e.valid := false.B; cb.e.bits := DontCare }
    slices.zipWithIndex.foreach { case (s, i) =>
      val (sb, se) = s.in.head
      sb.a.ready := io.sliceReady(i)
      io.sliceAValid(i) := sb.a.valid; io.sliceAOp(i) := sb.a.bits.opcode
      io.sliceAAddr(i) := sb.a.bits.address; io.sliceASrc(i) := sb.a.bits.source
      sb.d.valid := io.dValid(i)
      sb.d.bits := 0.U.asTypeOf(sb.d.bits)
      sb.d.bits.opcode := TLMessages.CBOAck
      sb.d.bits.size := 6.U
      sb.d.bits.source := io.dSrc(i)
      sb.b.valid := false.B; sb.b.bits := DontCare
      sb.c.ready := true.B; sb.e.ready := true.B
    }
  }
}
object CmoMain extends App {
  implicit val p: Parameters = audit.AuditCfg()
  val lm = LazyModule(new CmoLM)
  print(_root_.circt.stage.ChiselStage.emitSystemVerilog(lm.module, firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")))
}
```
</details>

<details><summary><code>AuditCfg.scala</code></summary>

```scala
package audit
import org.chipsalliance.cde.config.Parameters
import xiangshan._
import top.DefaultConfig
import utility._
object AuditCfg {
  def apply(): Parameters = {
    val dc = new DefaultConfig
    dc.alter((site, here, up) => {
      case XSCoreParamsKey => up(XSTileKey).head
      case DFTOptionsKey => up(DFTOptionsKey).copy(EnableMbist = false, EnableSramCtl = false)
      case LogUtilsOptionsKey => LogUtilsOptions(false, false, false, false)
      case PerfCounterOptionsKey => PerfCounterOptions(false, false, XSPerfLevel.withName("VERBOSE"), 0)
    })
  }
}
```
</details>

<details><summary><code>tb.cpp</code></summary>

```cpp
// Real DCache + real CustomL1L2Fabric + 4 L2-slice manager ports (address sets as BankBinder(4,64)).
// A CBO is issued through DCache.io.lsu.cmoOpReq; we record which slice port receives it.
#include "VCmoLM.h"
#include "verilated.h"
#include <cstdio>
#include <cstdint>
#include <cstdlib>
VCmoLM* t; uint64_t cyc = 0;
void tick() { t->clock = 0; t->eval(); t->clock = 1; t->eval(); cyc++; }
#define SV(i) (i==0?t->io_sliceAValid_0:i==1?t->io_sliceAValid_1:i==2?t->io_sliceAValid_2:t->io_sliceAValid_3)
#define SO(i) (i==0?t->io_sliceAOp_0:i==1?t->io_sliceAOp_1:i==2?t->io_sliceAOp_2:t->io_sliceAOp_3)
#define SA(i) (i==0?t->io_sliceAAddr_0:i==1?t->io_sliceAAddr_1:i==2?t->io_sliceAAddr_2:t->io_sliceAAddr_3)
#define SS(i) (i==0?t->io_sliceASrc_0:i==1?t->io_sliceASrc_1:i==2?t->io_sliceASrc_2:t->io_sliceASrc_3)
bool ready[4] = {1, 1, 1, 1};
void setReady() { t->io_sliceReady_0 = ready[0]; t->io_sliceReady_1 = ready[1]; t->io_sliceReady_2 = ready[2]; t->io_sliceReady_3 = ready[3]; }
void setD(int i, int v, int src) {
  if (i == 0) { t->io_dValid_0 = v; t->io_dSrc_0 = src; } if (i == 1) { t->io_dValid_1 = v; t->io_dSrc_1 = src; }
  if (i == 2) { t->io_dValid_2 = v; t->io_dSrc_2 = src; } if (i == 3) { t->io_dValid_3 = v; t->io_dSrc_3 = src; } }
const int OP_CBOFLUSH_TRUNC = 13 & 7, OP_CBOINVAL_TRUNC = 14 & 7, OP_GET = 4; // io.sliceAOp is the low 3 bits of the 4-bit opcode
int errs = 0;
// returns slice index that accepted the CBO, fills addr
void run(const char* name, uint64_t addr, int cmoOp, int stallSlice, int stallCycles) {
  int expSlice = (addr >> 6) & 3;
  for (int i = 0; i < 4; i++) ready[i] = 1;
  t->io_otherValid = 0; t->io_cmoValid = 0; setReady();
  for (int i = 0; i < 20; i++) tick();
  if (stallSlice >= 0) {
    // other fabric clients (stand-in for L1I/PTW) fill the target slice's A queue while that slice is busy
    ready[stallSlice] = 0; setReady();
    int sent = 0;
    for (int c = 0; c < 20; c++) {
      t->io_otherValid = 1; t->io_otherSrc = sent & 3; t->io_otherAddr = 0x80010000ULL + (uint64_t)stallSlice * 64 + (uint64_t)sent * 256; t->eval();
      bool f = t->io_otherReady; tick(); if (f) sent++; else break;
    }
    t->io_otherValid = 0; t->eval();
  }
  // issue the CBO
  t->io_cmoValid = 1; t->io_cmoOp = cmoOp; t->io_cmoAddr = addr; t->eval();
  while (!t->io_cmoReady) { tick(); t->eval(); }
  tick(); t->io_cmoValid = 0;
  int got = -1; uint64_t gotAddr = 0; int gotSrc = 0; char trace[256]; int tn = 0; int respSeen = 0; int dSlice = -1, dCyc = -1;
  for (int c = 0; c < 200; c++) {
    if (stallSlice >= 0 && c == stallCycles) { ready[stallSlice] = 1; }
    setReady(); setD(0, 0, 0); setD(1, 0, 0); setD(2, 0, 0); setD(3, 0, 0);
    if (dSlice >= 0 && c == dCyc) setD(dSlice, 1, gotSrc);
    t->eval();
    if (c < 8) tn += snprintf(trace + tn, 256 - tn, "[c%d ch0 v%d r%d | ch1 v%d r%d] ", c, t->io_dcAValid_0, t->io_dcAReady_0, t->io_dcAValid_1, t->io_dcAReady_1);
    for (int i = 0; i < 4; i++) if (SV(i) && ready[i] && (SO(i) == OP_CBOFLUSH_TRUNC || SO(i) == OP_CBOINVAL_TRUNC) && got < 0) {
      got = i; gotAddr = SA(i); gotSrc = SS(i); dSlice = i; dCyc = c + 3; }
    if (t->io_cmoRespValid) respSeen = 1;
    tick();
    if (respSeen) break;
  }
  bool ok = (got == expSlice);
  printf("%-46s cbo addr=0x%llx (bits[7:6]=%d -> slice %d)  received by slice %d addr=0x%llx src=%d  CBOAck->LSU resp=%d  %s\n",
         name, (unsigned long long)addr, expSlice, expSlice, got, (unsigned long long)gotAddr, gotSrc, respSeen, ok ? "[ok]" : "[FAIL]");
  printf("    A-valid/ready at the two DCache client ports after the CBO is accepted: %s\n", trace);
  if (!ok) errs++;
}
int main(int argc, char** argv) {
  Verilated::commandArgs(argc, argv);
  t = new VCmoLM;
  t->reset = 1; for (int i = 0; i < 4; i++) ready[i] = 1; setReady(); for (int i = 0; i < 20; i++) tick(); t->reset = 0;
  for (int i = 0; i < 3000; i++) tick();   // let DCache SRAM init / reset sequences finish
  run("CTRL1 odd block, no backpressure",            0x80001040ULL, 1, -1, 0);
  run("CTRL2 even block, slice busy 4 cycles",       0x80001080ULL, 1,  2, 4);
  run("TEST1 odd block (slice 1), slice busy 4 cyc", 0x80001040ULL, 1,  1, 4);
  run("TEST2 odd block (slice 3), slice busy 4 cyc", 0x800010c0ULL, 2,  3, 4);
  run("TEST3 odd block (slice 1), slice busy 1 cyc", 0x80001040ULL, 1,  1, 1);
  printf("errors=%d\n", errs);
  return 0;
}
```
</details>

<details><summary><code>run.sh</code></summary>

```bash
#!/bin/bash
# Mode A: XS_HOME=<XiangShan checkout of a253c1163 after `make init`> ./run.sh   (uses mill; not executed by me)
# Mode B: XS_CP=<runtime classpath of compiled a253c1163> SCALAC_CP=<scala 2.13.17 compiler jars>
#         CHISEL_PLUGIN=<chisel-plugin 7.13.0 jar> XS_SRC=<source tree> ./run.sh            (what I ran)
# SKIP_ELAB=1 reuses $WORK/ORIG.sv and $WORK/FIX.sv.
# Elaborating the DCache takes about 30 min and up to 16 GB of JVM heap per variant.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
W="${WORK:-/tmp/xs_cmo_repro}"; mkdir -p "$W"; cp "$HERE/tb.cpp" "$W/tb.cpp"
SRC=xiangshan/cache/dcache/mainpipe/MissQueue.scala
scalac_cmd() { java -cp "$SCALAC_CP" scala.tools.nsc.Main -Xplugin:"$CHISEL_PLUGIN" -language:reflectiveCalls -Ymacro-annotations -Ytasty-reader -nowarn "$@"; }
if [ -z "$SKIP_ELAB" ]; then
if [ -z "$XS_CP" ]; then
  : "${XS_HOME:?set XS_HOME (mode A) or XS_CP (mode B)}"
  mkdir -p "$XS_HOME/src/main/scala/repro_cmo"; cp "$HERE/AuditCfg.scala" "$HERE/CmoRepro.scala" "$XS_HOME/src/main/scala/repro_cmo/"
  trap 'rm -rf "$XS_HOME/src/main/scala/repro_cmo"' EXIT
  (cd "$XS_HOME" && ./mill -i xiangshan.runMain xiangshan.cache.CmoMain) > "$W/ORIG.sv"
  (cd "$XS_HOME" && git apply "$HERE/fix.diff")
  (cd "$XS_HOME" && ./mill -i xiangshan.runMain xiangshan.cache.CmoMain) > "$W/FIX.sv" || true
  (cd "$XS_HOME" && git apply -R "$HERE/fix.diff")
else
  : "${SCALAC_CP:?}" "${CHISEL_PLUGIN:?}" "${XS_SRC:?}"
  mkdir -p "$W/cls" "$W/cls_fix" "$W/fixsrc"
  scalac_cmd -d "$W/cls" -classpath "$XS_CP" "$HERE/AuditCfg.scala" "$HERE/CmoRepro.scala"
  java -Xmx16g -Xss64m -cp "$W/cls:$XS_CP" xiangshan.cache.CmoMain > "$W/ORIG.sv"
  cp "$XS_SRC/src/main/scala/$SRC" "$W/fixsrc/"; (cd "$W/fixsrc" && patch -p8 -s < "$HERE/fix.diff")
  scalac_cmd -d "$W/cls_fix" -classpath "$W/cls:$XS_CP" "$W/fixsrc/MissQueue.scala"
  java -Xmx16g -Xss64m -cp "$W/cls_fix:$W/cls:$XS_CP" xiangshan.cache.CmoMain > "$W/FIX.sv"
fi
fi
for V in ORIG FIX; do
  python3 "$HERE/split.py" "$W/$V.sv" "$W/$V" > /dev/null
  python3 "$HERE/gen_dpi.py" "$W/$V" "$W/$V/dpi_stubs.cpp" > /dev/null   # Constantin DPI readers -> elaborated init values
  # The bound TLMonitor layers are not used: the generic monitor rejects XiangShan's custom CBO opcodes (>= 8).
  (cd "$W/$V" && verilator --cc --exe --build -j 10 --top-module CmoLM -Wno-fatal -Wno-lint -Wno-style -Wno-MULTIDRIVEN \
     main.sv $(ls *.v) ClockGate.sv ../tb.cpp dpi_stubs.cpp -o sim -Mdir obj > build.log 2>&1)
done
{ for V in ORIG FIX; do echo "== $V"; "$W/$V/obj/sim"; done; } | tee "$W/run_all.log"
```
</details>

<details><summary><code>gen_dpi.py</code></summary>

```python
# Emit C stubs for the Constantin DPI readers, returning the elaborated init value
import re,glob,sys
out=[]
for f in glob.glob(sys.argv[1]+'/*_constantinReader.v'):
    s=open(f).read()
    fn=re.search(r'function longint (\w+)\(\)',s).group(1)
    v=re.search(r'initial value = (\d+);',s).group(1)
    out.append('extern "C" long long %s(){ return %s; }'%(fn,v))
open(sys.argv[2],'w').write('\n'.join(out)+'\n')
print(len(out))
```
</details>

<details><summary><code>split.py</code></summary>

```python
import sys,re,os
src,outdir=sys.argv[1],sys.argv[2]
os.makedirs(outdir,exist_ok=True)
lines=open(src).read().split('\n')
start=next(i for i,l in enumerate(lines) if l.startswith('// Generated by CIRCT'))
cur='main.sv'; buf={cur:[]}
for l in lines[start:]:
    m=re.match(r'// ----- 8< ----- FILE "(.*)" ----- 8< -----',l)
    if m: cur=m.group(1); buf.setdefault(cur,[]); continue
    buf[cur].append(l)
for k,v in buf.items():
    p=os.path.join(outdir,k); os.makedirs(os.path.dirname(p),exist_ok=True); open(p,'w').write('\n'.join(v)+'\n')
print(len(buf))
```
</details>

<details><summary><code>fix.diff</code></summary>

```diff
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
@@ -412,7 +412,7 @@
       0.U(memChannelBits.W)
     }
   }
-  io.channel_sel := Mux(RegNext(io.req.fire), selectChannel(req.address), 0.U)
+  io.channel_sel := Mux(state === s_sreq, selectChannel(req.address), 0.U)
 
   io.req.ready := state === s_idle
```
</details>

## Additional context

Suggested fix (tested above):
```diff
-  io.channel_sel := Mux(RegNext(io.req.fire), selectChannel(req.address), 0.U)
+  io.channel_sel := Mux(state === s_sreq, selectChannel(req.address), 0.U)
```

Scope / limitations:
- The design under test is the real DCache plus the real L1-L2 fabric. The L2 slices are TileLink manager stubs, and the full core / CoupledL2 were not elaborated (not enough memory on my machine). The back-pressure is produced by another fabric client filling the target slice's A queue while that slice is not ready. In the full system the same first-cycle refusal can also come from the SpecMiss round-robin or from `wfiReq`; I did not show those paths dynamically.
- The L2-side consequences above (wrong line in the wrong slice, target line untouched, `cbo.inval` on the aliased line) come from reading CoupledL2, not from simulation.
- The generic TLMonitor flags XiangShan's custom CBO opcodes (>= 8) as invalid, so the monitor layers were left out of this build.

This report was prepared with AI assistance (Claude); the commands and logs above were run and checked.
