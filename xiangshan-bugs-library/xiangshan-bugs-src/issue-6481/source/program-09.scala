// https://github.com/OpenXiangShan/XiangShan/blob/24ed326a5786e43c9d2ce4f43821c19810f16c95/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala#L86-L95
  private val backendFilteredResolve = io.backendResolve.map { backendResolve =>
    val filteredResolve = Wire(Valid(new ResolveWithSource))
    filteredResolve.valid := backendResolve.valid && !(backendResolve.bits.attribute.isDirect || backendResolve.bits.attribute.isReturn) &&
      !(backendRedirect.reduce(_ || _) && backendResolve.bits.ftqIdx > backendRedirectPtr)
    filteredResolve.bits.fromResolve(ResolveSource.Backend, backendResolve.bits)
    filteredResolve
  }
  private val ifuFilteredResolve = Wire(Valid(new ResolveWithSource))
  ifuFilteredResolve.valid := io.ifuResolve.valid && !backendRedirect.reduce(_ || _)
  ifuFilteredResolve.bits.fromResolve(ResolveSource.Ifu, io.ifuResolve.bits)
