This PR fix trap inst update.
Because of CSRR inst is out of order insts, trap inst should select the oldest trap inst when CSRR inst raise trap.
