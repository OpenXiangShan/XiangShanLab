// NEMU/src/isa/riscv64/system/priv.c:3026
static inline bool vec_permit_check(const word_t *dest_access) {
  if (is_access(vcsr) || is_access(vlenb) || is_access(vstart) || is_access(vxsat) || is_access(vxrm) || is_access(vl) || is_access(vtype)) {
    if (!require_vs()) { longjmp_exception(EX_II); }
  }
  return false;
}
