for (i <- 0 until StaCnt) when(st_atomics(i)) {
  issueSta(i).ready := atomicsUnit.io.in.ready
}
atomicsUnit.io.in.valid := st_atomics.reduce(_ || _)
atomicsUnit.io.in.bits := Mux1H(st_atomics, issueSta.map(_.bits))
