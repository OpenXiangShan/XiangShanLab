// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala#L536
  val disableAllIntrReg = RegNext(disableDebugIntr || !io.in.mnstatusNMIE, false.B)
