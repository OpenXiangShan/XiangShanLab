// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala#L85-L87
  jalrFaultVec := VecInit(pdInfoVec.zipWithIndex.map { case (pd, i) =>
    pd.brAttribute.isIndirect && !pd.brAttribute.hasPop && instrValid(i) && !isPredTaken(i)
  })
