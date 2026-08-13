When there is a jalr instruction in the middle of an instruction block but
the BPU fails to predict it, the IFU should adjust the length of the
instruction block to terminate at the jalr instruction. 
However, the IFU currently does not check for this scenario, which may 
result in the unintended execution of instructions following the jalr that 
should not have been executed. This PR fixed this issue.
