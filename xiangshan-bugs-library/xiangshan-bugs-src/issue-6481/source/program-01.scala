// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala#L86-L92
  private val backendFilteredResolve = io.backendResolve.map { backendResolve =>
    val filteredResolve = Wire(Valid(new ResolveWithSource))
    filteredResolve.valid := backendResolve.valid &&
      !(backendResolve.bits.attribute.isDirect || backendResolve.bits.attribute.isReturn)
    filteredResolve.bits.fromResolve(ResolveSource.Backend, backendResolve.bits)
    filteredResolve
  }
