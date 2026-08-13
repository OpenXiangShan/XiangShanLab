// src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala
filteredResolve.valid := backendResolve.valid &&
  !(backendRedirect.reduce(_ || _) && backendResolve.bits.ftqIdx > backendRedirectPtr)
