* Although EX_II will be raised when access these CSRs in some illegal ways(e.g. writing pmpcfg in S mode), the regs in these CSRs will always be changed by wdata. The reason for the mistake is that the wen of these CSRs is assigned directly to wen of NewCSR instead of wenLegal which only assert when writing CSR in some legal ways.
* Fixed CSRs are pmpcfgs, pmpaddrs, miregs, siregs and vsiregs.
* Todo: all wen and wdata of CSRModule assigned in the same for loop
