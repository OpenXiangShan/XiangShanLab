// https://github.com/OpenXiangShan/XiangShan/blob/e516245046234ba958fd69cf95241e77278a1c43/src/main/scala/device/TIMER.scala#L86-L87
    val time = RegInit(0.U(timeWidth.W))
    when(io.time.valid)(time := io.time.bits)
