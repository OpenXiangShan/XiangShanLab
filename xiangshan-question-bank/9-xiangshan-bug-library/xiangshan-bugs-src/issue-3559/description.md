* CSR
  * When reset, xenvcfg.CBZE = 1, xenvcfg.CBCFE = 1, xenvcfg.CBIE = 0b11, while x in {m, s, h}.
  * Support xenvcfg.CBIE = Flush(0b01)
* Decode
  * Use the illegalInst and virtualInst conditions from CSR to assert EX_II or EX_VI.
  * Convert CBO.INVAL to CBO.FLUSH when envcfg.CBIE === EnvCBIE.Flush.
