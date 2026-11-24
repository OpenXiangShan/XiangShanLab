// ICacheMainPipe.scala:335-336
  // merge s1 itlb/pmp exceptions, itlb has the highest priority, pmp next, note this `||` is overloaded
  private val s1_exception = s1_exceptionInfo(0).itlbException || s1_pmpException
