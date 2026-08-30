.equ LOW_ALIAS_VA, 0x0000000040001000
.equ HIGH_BAD_TAG, 0x0000010000000000
.equ BAD_FETCH_VA, (HIGH_BAD_TAG | LOW_ALIAS_VA)

li   t0, BAD_FETCH_VA
jalr zero, 0(t0)           /* must trap as non-canonical IFETCH */

s_alias_page:
  ecall                    /* bad if low alias is executed */
