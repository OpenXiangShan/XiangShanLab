// https://github.com/OpenXiangShan/XiangShan/blob/567d1da56d968a6dde8fbfe829896bc9b9ca2605/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala#L97-L106
  private val resolve = filteredResolve.map { filteredResolve =>
    val hasMispredict = filteredResolve.bits.mispredict
    val isDirect      = filteredResolve.bits.attribute.isDirect
    val shouldDrop    = !hasMispredict && (dropResolveCounter.isSaturatePositive || isDirect)

    val resolve = Wire(Valid(new Resolve))
    resolve.valid := filteredResolve.valid && !shouldDrop
    resolve.bits  := filteredResolve.bits
    resolve
  }
