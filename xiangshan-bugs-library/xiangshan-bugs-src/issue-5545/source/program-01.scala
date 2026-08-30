Mux(
  cnt +& step >= SaturatePositive,
  SaturatePositive,
  Mux(
    cnt +& step <= SaturateNegative,
    SaturateNegative,
    cnt + step
  )
)
