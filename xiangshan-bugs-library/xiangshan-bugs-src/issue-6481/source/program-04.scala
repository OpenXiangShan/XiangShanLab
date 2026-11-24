// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala#L88-L90
  retFaultVec := VecInit(pdInfoVec.zipWithIndex.map { case (pd, i) =>
    pd.brAttribute.hasPop && instrValid(i) && !isPredTaken(i)
  })
