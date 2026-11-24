// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L1699-L1702
    diffArchEvent.interrupt := RegEnable(interruptNO, hasTrap)
    diffArchEvent.exception := RegEnable(exceptionNO, hasTrap)
    diffArchEvent.exceptionPC := RegEnable(exceptionPC, hasTrap)
    diffArchEvent.hasNMI := RegEnable(hasNMI, hasTrap)
