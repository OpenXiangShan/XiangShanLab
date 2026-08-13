optimise redundant signals to reduce MemBlock area.
1. optimise 'exceptionVec', selected by LduCfg or StaCfg;
2. optimise 'fuType',  reassign the value when writeback Rob in pipeline, so no longer saved in LSQ.
3. optimise 'uop.imm',  vaddr is computed in StoreMisalignBuffer and there is no need to store the uop.imm.
