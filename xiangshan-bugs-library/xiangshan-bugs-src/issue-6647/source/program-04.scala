out.uop.bits := Mux1H(outFuUopEx.map(x => x.valid -> x.bits))
