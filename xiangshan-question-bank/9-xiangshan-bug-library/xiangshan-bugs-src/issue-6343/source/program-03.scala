def resetFSM(): Unit = {
  state := s_invalid
  out_valid := false.B
  data_valid := false.B
  stdCnt := 0.U
  pdest1Valid := false.B
  pdest2Valid := false.B
}
