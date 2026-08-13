In previous design, we always use `ptes(index).getPPN()` to generate PPN for last G-stage translate. However, when VS-Stage is napot, we should use the low 4 bits of vpn for generating ppn.
