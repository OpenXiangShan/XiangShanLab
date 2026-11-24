const reg_t mDTE = (state->menvcfg->read() & MENVCFG_DTE);
const reg_t adj_write_mask = sstatus_write_mask & ~(mDTE ? 0 : SSTATUS_SDT);
reg_t new_mstatus = (mstatus->read() & ~adj_write_mask) | (val & adj_write_mask);
new_mstatus = (new_mstatus & SSTATUS_SDT) ? (new_mstatus & ~SSTATUS_SIE) : new_mstatus;
