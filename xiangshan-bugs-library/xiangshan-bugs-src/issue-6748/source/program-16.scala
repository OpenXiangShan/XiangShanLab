// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala#L92-L95
  private val xtvec = MuxCase(io.in.mtvec, Seq(
    traptoVS -> io.in.vstvec,
    trapToHS -> io.in.stvec
  ))
