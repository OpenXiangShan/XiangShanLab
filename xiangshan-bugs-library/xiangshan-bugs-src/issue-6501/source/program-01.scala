// MachineLevel.scala, minstret (tested build 2acbf327c and v2 HEAD 5d393413: L353-360; v3 HEAD 14af5521 L366-375)
when(w.wen) {
  reg := w.wdata
}.elsewhen(!this.mcountinhibit.IR && robCommit.instNum.valid) {
  reg := reg.ALL.asUInt + robCommit.instNum.bits
}
