updateUsefulCnt(provider) := Mux(
  !t1_meta.altDiffers,
  t1_meta.providerUsefulCnt,
  (t1_meta.providerTarget === updateRealTarget).asTypeOf(UsefulCounter())
)
