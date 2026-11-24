// ICacheMainPipe.scala:390-391
  // merge all exceptions, itlb/pmp has the highest priority, then l2/ecc
  private val s1_exceptionOut = s1_exception || s1_tlException
