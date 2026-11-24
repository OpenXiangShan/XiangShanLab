// src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1647-1651
s3_out.bits.uop.exceptionVec(hardwareError) := (s3_in.uop.exceptionVec(hardwareError) || s3_hw_err ||
                                               io.dcache.resp.bits.tl_error_delayed.tl_corrupt && !io.dcache.resp.bits.tl_error_delayed.tl_denied) && s3_vecActive && !s3_in.isPrefetch
when (s3_nc_with_data) {
  s3_out.bits.uop.exceptionVec := s3_in.uop.exceptionVec
}
