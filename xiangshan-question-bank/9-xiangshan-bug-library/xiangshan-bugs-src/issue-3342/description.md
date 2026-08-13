When the resp is allstage and level == 0, PTW find pte and then gpf happens in the last s2xlate before resp to l1tlb. We can't give fake pte to stage1 because the pte that mem resp is valid in PTW.
