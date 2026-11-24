// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala#L108-L109
  io.out.causeNO.Interrupt := hasIR
  io.out.causeNO.ExceptionCode := causeNO
