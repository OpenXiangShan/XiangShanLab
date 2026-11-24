// src/main/scala/xiangshan/frontend/ifu/Ifu.scala:543-550 @170961f4cdc59034c52f34263a144447a6907e31
  io.toIBuffer.bits.exceptionType := MuxCase(
    s2_rvcException,
    Seq(
      s2_icacheMeta(0).exception.hasException -> s2_icacheMeta(0).exception,
      s2_firstHasRvcIll                       -> s2_rvcException,
      s2_icacheMeta(1).exception.hasException -> s2_icacheMeta(1).exception
    )
  )
