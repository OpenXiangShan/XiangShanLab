  io.bpuTrain.bits.branches.foreach { branch =>
    branch.bits.debug_realCfiPc.foreach { pc =>
      when(io.bpuTrain.fire && branch.valid &&
        (pc === "h80000100".U || pc === "h80000180".U || pc === "h800001c0".U)) {
        printf(p"P4FEG4_INTS_QUEUE_ACCEPT pc=0x${Hexadecimal(pc)} ftq_idx=${io.bpuTrain.bits.ftqIdx.value} target=0x${Hexadecimal(branch.bits.target.toUInt)} taken=${branch.bits.taken} mispredict=${branch.bits.mispredict} branch_type=${branch.bits.attribute.branchType} ras_action=${branch.bits.attribute.rasAction}\n")
      }
    }
  }
