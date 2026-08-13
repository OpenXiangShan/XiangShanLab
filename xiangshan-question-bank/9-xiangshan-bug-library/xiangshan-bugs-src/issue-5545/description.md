If we do `getIncrease(-n)`, `value + step` will overflow. To fix, we need
```scala
Mux(
  cnt +& step >= SaturatePositive,
  SaturatePositive,
  Mux(
    cnt +& step <= SaturateNegative,
    SaturateNegative,
    cnt + step
  )
)
```
which is too heavy. I'd prefer do this selection outside, like
```scala
newCnt := Mux(p >= n, cnt.getIncrease(p-n), cnt.getDecrease(n-p))
```

So this PR simply disallow step < 0.
