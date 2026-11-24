// src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1617
io.lsq.ldin.bits.updateAddrValid := !s3_mis_align && (!s3_frm_mabuf || s3_in.isFinalSplit) || s3_exception
