// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/ras/MicroRas.scala#L139-L142
  when(io.hasRedirect) {
    // On redirect, the main RAS state is being restored,
    // and the S1–S3 pipeline is flushed → no valid prediction.
    isCanUse := false.B
