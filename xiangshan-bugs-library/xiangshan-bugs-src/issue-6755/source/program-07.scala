// https://github.com/OpenXiangShan/XiangShan/blob/e516245046234ba958fd69cf95241e77278a1c43/src/main/scala/device/SYSCNT.scala#L144-L146
    when(time_req_rtc_ris) {
      time := time_sw
    }.elsewhen(stop_sync) {
