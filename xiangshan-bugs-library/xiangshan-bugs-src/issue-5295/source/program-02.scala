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
