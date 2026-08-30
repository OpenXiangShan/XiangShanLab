885 val fp_b_mantissa_widen = Mux(EOP,Cat(~significand_fp_b,1.U,Fill(widenWidth,1.U)),Cat(0.U,significand_fp_b,0.U(widenWidth.W)))
