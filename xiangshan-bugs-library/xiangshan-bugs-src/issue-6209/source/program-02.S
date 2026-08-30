/* Sv39 maps the first supervisor page; the next page is intentionally unmapped. */
li   t0, (ROOT_PPN | SATP_MODE_SV39)
csrw satp, t0
sfence.vma

la   t0, s_page
csrw mepc, t0
mret

s_page:
  li   a0, 0x40000100
calib_ld:
  ld   t0, 4(a0)          /* calibration: same-page misaligned load */
  li   a0, 0x40000ffc
fault_ld:
  ld   t0, 0(a0)          /* split load: tail enters unmapped page */
