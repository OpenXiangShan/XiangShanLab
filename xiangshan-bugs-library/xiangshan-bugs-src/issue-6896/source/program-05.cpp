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
