The current condition, when there are no exceptions in the entry and the frompipe carries an exception, will go directly to the next level of determining whether it is a fof instruction. If it is a fof instruction and a non-zero element triggers an exception, the value of vl is modified directly.

> 
    when((((entryElemIdx >= selElemIdx) && entryExcp && portHasExcp(i)) || (!entryExcp && portHasExcp(i))) && pipewb.valid && !mergedByPrevPortVec(i)) { // When there are no exceptions in the entry and the frompipe carries exceptions.
      entry.uop.trigger     := selPort(0).trigger
      entry.elemIdx         := selElemIdx
      when(!entry.fof || vstart === 0.U){
        // For fof loads, if element 0 raises an exception, vl is not modified, and the trap is taken.
        entry.vstart       := vstart
        entry.exceptionVec := ExceptionNO.selectByFu(selExceptionVec, fuCfg)
        entry.vaddr        := vaddr
        entry.vaNeedExt    := selPort(0).vaNeedExt
        entry.gpaddr       := selPort(0).gpaddr
        entry.isForVSnonLeafPTE := selPort(0).isForVSnonLeafPTE
      }.otherwise{
        entry.uop.vpu.vta  := VType.tu
        entry.vl           := vstart // Set the value of vl directly.
      }
    }


The fof instruction is regarded as a unit-stride instruction. Therefore, when a fof uop is split into two access operations and both of them trigger an exception at the pipe, both of them will modify the vl value of the same entry, which may result in a situation where a smaller vl value is overwritten by a larger vl value.

Therefore, this modification makes a judgement when modifying the vl value, and only allows to write the vl value which is smaller than the current vl of the entry.

> 
    entry.vl           := Mux(entry.vl < vstart, entry.vl, vstart)
