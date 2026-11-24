}.elsewhen(robEntries(i).valid) {
  // update by writing back
  robEntries(i).uopNum := robEntries(i).uopNum - wbCnt
  assert(!(robEntries(i).uopNum - wbCnt > robEntries(i).uopNum), s"robEntries $i uopNum is overflow!")
}
