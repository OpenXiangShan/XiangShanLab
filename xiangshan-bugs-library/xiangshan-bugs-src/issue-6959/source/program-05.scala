// Fence.scala:82-87
when (state === s_idle && io.in.valid) { state := s_wait }
when (state === s_wait && func === FenceOpType.fencei && sbEmpty) { state := s_icache }
when (state === s_wait && ((func === FenceOpType.sfence || func === FenceOpType.hfence_g || func === FenceOpType.hfence_v || (if (HasMptCheck) (func === FenceOpType.mfence) else false.B)) && sbEmpty)) { state := s_tlb }
when (state === s_wait && func === FenceOpType.fence  && sbEmpty) { state := s_fence }
when (state === s_wait && func === FenceOpType.nofence  && sbEmpty) { state := s_nofence }
when (state =/= s_idle && state =/= s_wait) { state := s_idle }
