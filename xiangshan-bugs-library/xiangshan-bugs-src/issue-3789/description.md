* NEMU commit: 066cb1f1c61feb21153399c26ca393dfb3a560d7
* NEMU configs:
  * riscv64-xs-ref_defconfig
  * riscv64-dual-xs-ref_defconfig

Including:
  * fix(format): adjust code format and add one config (OpenXiangShan/NEMU#603)
  * fix(vfredusum): set xstatus.fs and xstatus.vs dirty (OpenXiangShan/NEMU#605)
  * fix(vf): do not set dirtyFs for some instructions (OpenXiangShan/NEMU#606)
  * feat(trigger): add trigger support for rva.
  * configs(xs): open Sm/sdbltrp extension and add MDT_INIT config (OpenXiangShan/NEMU#604)

---

* spike commit: c0b18d3913d8ceac83743a053a7dbd2fb8716c83
* spike config: CPU=XIANGSHAN

Including:
* fix(rva, trigger): For rva instr, raise BP from trigger prior to misaligned.
* fix(Makefile): Increase maxdepth for finding .h files.
* fix(tdata1): CPU_XIANGSHAN do not implement hit bit in tdata1.
* fix(icount): place the read before the return of the detect_icount_match.
