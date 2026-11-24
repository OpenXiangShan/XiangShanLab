// src/main/scala/xiangshan/frontend/ifu/Ifu.scala:273-277 @170961f4cdc59034c52f34263a144447a6907e31
  private val s1_icacheExceptionIdx = Mux(
    s1_icacheMeta(0).exception.hasException,
    s1_alignShiftNum,
    s1_alignShiftNum +& s1_firstInstrCount
  )
