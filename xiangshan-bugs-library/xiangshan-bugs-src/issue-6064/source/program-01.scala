  313    val srcType1 =  Mux1H(Seq(
  314      isvrgatherei16                     -> "b0001".U,
  315      isvcompress                        -> "b1111".U,
  316      !(isvrgatherei16|isvrgatherei16)   -> Cat(isFp ,isFp,  sew(1,0)),
  317    ))
