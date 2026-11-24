// src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1374
val s2_safe_wakeup = !s2_out.rep_info.need_rep && !s2_mmio && (!s2_in.nc || s2_nc_with_data) && !s2_mis_align && !s2_real_exception
// src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1582
val s3_safe_writeback = RegEnable(s2_safe_writeback, s2_fire) || s3_hw_err
// src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1643
s3_out.valid                := s3_valid && s3_safe_writeback && !toMisalignBufferValid
