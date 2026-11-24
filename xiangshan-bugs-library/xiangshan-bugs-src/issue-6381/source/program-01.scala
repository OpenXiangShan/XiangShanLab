// when DTE is zero, SDT field is read-only zero(write any, read zero, side effect of write 1 is block)
writeMstatusSDT := w.wdataFields.SDT.asBool
writeSstatusSDT := Mux(this.menvcfg.DTE.asBool, wAliasSstatus.wdataFields.SDT.asBool, reg.SDT.asBool)
//                                              BUG: DTE=0 branch uses the stored SDT, should be false.B
writeSDT        := Mux(w.wen, writeMstatusSDT, wAliasSstatus.wen && writeSstatusSDT)

when (writeSDT) {
  reg.SIE := false.B          // overrides the SIE value written by the same sstatus write
}
