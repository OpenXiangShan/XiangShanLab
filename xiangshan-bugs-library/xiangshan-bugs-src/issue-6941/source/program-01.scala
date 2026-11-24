when(io.memGrant.fire && edge.hasData(io.memGrant.bits)) {
  corruptReg := corruptReg || io.memGrant.bits.corrupt
  deniedReg  := deniedReg || io.memGrant.bits.denied
}.elsewhen(lastFireNext) {
  corruptReg := false.B
  deniedReg  := false.B
}
