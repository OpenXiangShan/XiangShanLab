// VirtualSupervisorLevel.scala
writeSDT := Mux(this.menvcfg.DTE && this.henvcfg.DTE, w.wdataFields.SDT.asBool, 0.U)
