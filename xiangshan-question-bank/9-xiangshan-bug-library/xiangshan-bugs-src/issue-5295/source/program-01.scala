def getTag(pc: PrunedAddr): UInt =
  pc(
    TagWidth + InternalBankIdxLen + SetIdxLen + FetchBlockSizeWidth - 1,
    InternalBankIdxLen + SetIdxLen + FetchBlockSizeWidth
  )
