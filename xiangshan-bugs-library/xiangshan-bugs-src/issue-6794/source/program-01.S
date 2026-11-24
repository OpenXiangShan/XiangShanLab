  csrsi  menvcfg, 1      # FIOM = 1
  csrr   s4, menvcfg     # expected bit 0 = 1
  csrsi  senvcfg, 1
  csrr   s2, senvcfg     # expected bit 0 = 1
  csrsi  henvcfg, 1
  csrr   s5, henvcfg     # expected bit 0 = 1
