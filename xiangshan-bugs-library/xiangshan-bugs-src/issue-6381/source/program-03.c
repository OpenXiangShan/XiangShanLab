write_sdt = new_val.sdt;
if (menvcfg->dte == 0 ) {
  sstatus_wmask &= ~SSTATUS_SDT;
  write_sdt = mstatus->sdt;
}
mstatus->val = mask_bitset(mstatus->val, sstatus_wmask, new_val.val);
if (write_sdt) { mstatus->sie = 0; }
