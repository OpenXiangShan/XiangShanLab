riscv64-unknown-elf-gcc -march=rv64imafdcvh_zicsr_zifencei_zicbom_zicbop_zicboz -mabi=lp64 -c -o nmi_sweep_uartlite.o nmi_sweep_uartlite.S
riscv64-unknown-elf-gcc -march=rv64imafdcvh_zicsr_zifencei_zicbom_zicbop_zicboz -mabi=lp64 -nostdlib -nostartfiles -static -Wl,--build-id=none,--no-relax -T nmi_sweep.ld -o nmi_sweep_uartlite.elf nmi_sweep_uartlite.o
