main:
  li   s3, 0x99        # 80001020: 09900993
  csrw pmpcfg0, s3     # 80001024: 3a099073
  nop                  # 80001028: 00000013  <-- this fetch must fault
