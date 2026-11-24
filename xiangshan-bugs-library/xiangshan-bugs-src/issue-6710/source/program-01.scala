// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/Parameters.scala#L305-L318
  def ISAExtensions = Seq(
    // single letter extensions, in canonical order
    "i", "m", "a", "f", "d", "c", "b", "v", "h",
    // multi-letter extensions, sorted alphanumerically
    "sdext", "sdtrig", "sha", "shcounterenw", "shgatpa", "shlcofideleg", "shtvala", "shvsatpa", "shvstvala",
    "shvstvecd", "smaia", "smcdeleg", "smcntrpmf", "smcsrind", "smdbltrp", "smmpm", "smnpm", "smrnmi", "smstateen",
    "ss1p13", "ssaia", "ssccfg", "ssccptr", "sscofpmf", "sscounterenw", "sscsrind", "ssdbltrp", "ssnpm",
    "sspm", "ssstateen", "ssstrict", "sstc", "sstvala", "sstvecd", "ssu64xl", "supm", "sv39",
    "sv48", "svade", "svbare", "svinval", "svnapot", "svpbmt", "za64rs", "zabha", "zacas", "zama16b", "zawrs", "zba",
    "zbb", "zbc", "zbkb", "zbkc", "zbkx", "zbs", "zcb", "zcmop", "zfa", "zfh", "zfhmin", "zic64b",
    "zicbom", "zicbop", "zicboz", "ziccamoa", "ziccamoc", "ziccif", "zicclsm", "ziccrse", "zicntr", "zicond",
    "zicsr", "zifencei", "zihintntl", "zihintpause", "zihpm", "zimop", "zkn", "zknd", "zkne", "zknh",
    "zksed", "zksh", "zkt", "zvbb", "zvfh", "zvfhmin", "zvkt", "zvl128b", "zvl32b", "zvl64b"
  )
