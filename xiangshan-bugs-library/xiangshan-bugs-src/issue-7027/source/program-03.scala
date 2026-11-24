// src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:364
s2_enqueue(w) := s2_valid(w) && !s2_has_exception(w) && !s2_need_replay(w) && (s2_req(w).mmio || s2_req(w).nc)

### Expected behavior

The load either completes through LoadQueueUncache (this pass does not use DCache data) or reports a precise hardware error. It does not hang.

### Environment

- XiangShan: kunminghu-v2 `9c53db086a`, KunminghuV2Config, 1 core, Verilator emu, difftest disabled

### To Reproduce

[nc-first-pass-err.zip](https://github.com/user-attachments/files/35227466/nc-first-pass-err.zip)

Attachment: `nc-first-pass-err.zip`. It contains `nc-err.S`, `io-err.S`, the images `nc-err.bin` and `io-err.bin`, the controls `nc-control.bin` and `io-control.bin`, `build.sh` (rebuilds all four with LLVM and checks `SHA256SUMS`), and a short README.

The program makes a cacheable line Y dirty in L1, arms a one-shot L1 data pseudo-ECC error through CtrlUnit (0x38022000), evicts Y (ReleaseData carries corrupt) and reloads it (Grant carries corrupt, the L1 line is flagged; this cacheable reload traps with cause 19, which the handler counts and skips). Then, in S mode, it loads Y through its PBMT=NC alias (`io-err`: PBMT=IO alias). The controls skip the CtrlUnit write.

```bash
./build/emu -i nc-err.bin     --no-diff -C 200000
./build/emu -i nc-control.bin --no-diff -C 200000
./build/emu -i io-err.bin     --no-diff -C 200000
./build/emu -i io-control.bin --no-diff -C 200000
