arbiterIn.valid := in.valid && in.bits.toIntRf.map(_.valid).getOrElse(false.B)
