So we don't have to manually calculate each range like `addr(a+b+c+d-1, a+b+c)`:

```scala
def getTag(pc: PrunedAddr): UInt =
  pc(
    TagWidth + InternalBankIdxLen + SetIdxLen + FetchBlockSizeWidth - 1,
    InternalBankIdxLen + SetIdxLen + FetchBlockSizeWidth
  )
```

will become
```scala
val addrFields = AddrField(
  Seq(
    ("alignOffset", FetchBlockAlignWidth),
    ("alignBankIdx", AlignBankIdxLen),
    ("internalBankIdx", InternalBankIdxLen),
    ("setIdx", SetIdxLen),
    ("tag", TagWidth)
  )
)

def getTag(pc: PrunedAddr): UInt =
  addrFields.extract("tag", pc)
```

This PR also use these methods in mbtb as demo, if acceptable, I can apply this to all predictors

See https://github.com/OpenXiangShan/XiangShan/pull/5274#issuecomment-3595829992
