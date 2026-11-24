// src/main/scala/xiangshan/frontend/ifu/Ifu.scala:222-227
  s1_icacheMeta.zipWithIndex.foreach { case (meta, i) =>
    meta.exception := s1_icacheMetaIn(i).exception || ExceptionType.fromEcc(
      io.fromICache.corrupt(i).reduce(_ || _), // FIXME: consider which cacheline is corrupted, and modify exceptionMask
      s1_valid
    )
  }
