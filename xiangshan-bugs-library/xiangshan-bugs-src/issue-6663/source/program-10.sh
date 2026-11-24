riscv64-unknown-elf-gcc -march=rv64i_zicsr_zifencei -mabi=lp64 -I src -c -o obj/mpt_guest.o src/mpt_guest.S
riscv64-unknown-elf-gcc -march=rv64i_zicsr_zifencei -mabi=lp64 -I src -c -o obj/execute_only_load_s_page0.o src/execute_only_load_s_page0.S
riscv64-unknown-elf-gcc -march=rv64i_zicsr_zifencei -mabi=lp64 -nostdlib -nostartfiles -static -Wl,--build-id=none,--no-relax -T src/mpt_guest.ld -o execute_only_load_s_page0.elf obj/mpt_guest.o obj/execute_only_load_s_page0.o
