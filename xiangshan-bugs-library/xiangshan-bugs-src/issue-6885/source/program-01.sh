./emu -i IGPFOFF01.elf \
  --diff=./riscv64-nemu-interpreter-so --flash=./boot.bin \
  --ram-size=256MB --max-cycles=50000 --max-instr=20000 \
  --no-squash --seed=1
