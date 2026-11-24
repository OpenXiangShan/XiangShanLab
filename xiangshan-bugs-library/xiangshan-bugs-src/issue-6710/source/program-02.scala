// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/decode/isa/Extensions.scala#L156-L170
  def extensions(implicit p: Parameters): Seq[ExtBase] = Seq(
    I, M, A, F, D, Zicsr,
    System, S, Svinval,
    Za64rs, Zabha, Zacas, ZacasZabha, Zawrs,
    Zba, Zbb, Zbc, Zbs, Zbkb, Zbkc, Zbkx,
    V, H,
    Zvknha,
    Zvbb,
    XSTrap,
    Zicbo,
    Zicond,
    Zifencei, Zknd, Zkne, Zknh, Zksed, Zksh,
    // Zcb, Zcmop,
    ZfaF, ZfaD, ZfaZfh, Zfh, Zfhmin, ZfhminD,
  ) ++ Option.when(p(XSCoreParamsKey).HasMptCheck && !p(XSCoreParamsKey).HasBitmapCheck)(Smmtt).toSeq
