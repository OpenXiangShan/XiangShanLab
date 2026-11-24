// ICacheMainPipe.scala:380-388
  // also raise af if l2 corrupt is detected
  private val s1_tlException =
    (s1_tlCorrupt.flatten zip s1_tlDenied.flatten).zipWithIndex.map { case ((corrupt, denied), i) =>
      val portValid   = s1_portValid(i)
      val realCorrupt = corrupt && portValid
      val realDenied  = denied && portValid
      val canAssert   = s1_valid && portValid
      ExceptionType.fromTileLink(realCorrupt, realDenied, canAssert)
    }.reduce(_ || _)
