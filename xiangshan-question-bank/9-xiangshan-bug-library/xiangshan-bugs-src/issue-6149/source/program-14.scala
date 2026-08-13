s1_ubtbPrediction.target := Mux(
  ubtb.io.prediction.bits.attribute.isReturn && uras.io.specOut.isCanUse,
  uras.io.specOut.retTarget,
  ubtb.io.prediction.bits.target
)

s1_abtbResult.target := Mux(
  s1_abtbFirstTakenBr.attribute.isReturn && uras.io.specOut.isCanUse,
  uras.io.specOut.retTarget,
  s1_abtbFirstTakenBr.target
)
