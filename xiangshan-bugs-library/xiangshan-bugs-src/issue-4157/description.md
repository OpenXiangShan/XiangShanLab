* The STIP signal is updated when:
    * time.valid of clint
    *   stimecmp CSR is written
    *   menvcfg CSR is written

* The VSTIP signal is updated when:
    *   time.valid of clint
   *   vstimecmp CSR is written
   *   htimedelta CSR is written
   *   menvcfg CSR is written
   *   henvcfg CSR is written

Co-authored-by: Xuan Hu <39661208+huxuan0307@users.noreply.github.com>
