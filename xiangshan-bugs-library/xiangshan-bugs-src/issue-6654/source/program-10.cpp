// https://github.com/OpenXiangShan/difftest/blob/4c64fe86b17e0564232b52bb3800d9b15c45a3a0/src/test/csrc/difftest/checkers/traps.cpp#L83-L85
  if (probe.exception == EX_IPF || probe.exception == EX_LPF || probe.exception == EX_SPF ||
      probe.exception == EX_IGPF || probe.exception == EX_LGPF || probe.exception == EX_SGPF ||
      probe.exception == EX_HWE) {
