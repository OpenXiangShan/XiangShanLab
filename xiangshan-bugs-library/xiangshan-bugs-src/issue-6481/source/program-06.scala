// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala#L121-L125
  private val stage1Fault = VecInit.tabulate(IBufferEnqueueWidth)(i =>
    jalFaultVec(i) || jalrFaultVec(i) || retFaultVec(i) || notCfiTaken(i) || invalidTaken(i)
  )
  dontTouch(stage1Fault.asUInt)
  mispredIdx.valid := ParallelOR(stage1Fault)
