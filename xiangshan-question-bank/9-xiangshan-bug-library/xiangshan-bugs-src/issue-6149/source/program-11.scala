// src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala
class SbpctlBundle extends CSRBundle {
  val RAS_ENABLE = RW(6).withReset(true.B)
    .withDescription("Enable the return-address stack predictor.")
}
