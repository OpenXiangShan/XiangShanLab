* menvcfg.DTE only control Smode dbltrp. Thus mstatus.sdt will not
    control by DTE.
  * as sstatus is alias of mstatus, when menvcfg.DTE close write
    sstatus.sdt cannot lead to shadow write of mstatus.sdt. As a result,
    we add wmask of sdt, when write source is from alias write.
    While vsstatus is not alias of any other CSR fields, so origin logic
    is correct.

  * align spike/nemu logic to xiangshan. see spike pr[60](https://github.com/OpenXiangShan/riscv-isa-sim/pull/60), NEMU pr[688](https://github.com/OpenXiangShan/NEMU/pull/688) for detail.
