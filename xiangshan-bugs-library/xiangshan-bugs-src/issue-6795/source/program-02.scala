writeMstatusSDT := w.wdataFields.SDT.asBool
writeSstatusSDT := Mux(this.menvcfg.DTE.asBool, wAliasSstatus.wdataFields.SDT.asBool, reg.SDT.asBool)
writeSDT        := Mux(w.wen, writeMstatusSDT, wAliasSstatus.wen && writeSstatusSDT)
// ...
when (writeSDT) {
  reg.SIE := false.B
}
