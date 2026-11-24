// https://github.com/OpenXiangShan/XiangShan/blob/567d1da56d968a6dde8fbfe829896bc9b9ca2605/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala#L68-L74
  private val filteredResolve = io.backendResolve.map { backendResolve =>
    val filteredResolve = Wire(Valid(new Resolve))
    filteredResolve.valid := backendResolve.valid &&
      !(backendRedirect.reduce(_ || _) && backendResolve.bits.ftqIdx > backendRedirectPtr)
    filteredResolve.bits := backendResolve.bits
    filteredResolve
  }
