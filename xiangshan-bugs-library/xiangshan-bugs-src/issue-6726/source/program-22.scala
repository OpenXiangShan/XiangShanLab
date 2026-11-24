// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala#L86
  private val dbltrpToMN = m_EX_DT && mnstatus.NMIE.asBool // NMI not allow double trap
