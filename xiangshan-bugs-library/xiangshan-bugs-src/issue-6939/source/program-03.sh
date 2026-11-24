SAIL=./build/c_emulator/sail_riscv_sim
"$SAIL" --print-default-config > sail-default.jsonc
# Copy to sail-v128.json and set extensions.V.vlen_exp = 7 (VLEN=128)
"$SAIL" --config sail-v128.json --trace-instr --trace-exception \
  vnclip_src_eew_overlap.elf
# expect: illegal-instruction on vnclip.wv, tval=0xBEC68D57
