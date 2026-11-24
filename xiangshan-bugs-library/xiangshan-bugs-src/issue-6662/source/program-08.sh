riscv64-unknown-elf-gcc -march=rv64i_zicsr_zifencei -mabi=lp64 -I src -c -o obj/mpt_guest.o src/mpt_guest.S
riscv64-unknown-elf-gcc -march=rv64i_zicsr_zifencei -mabi=lp64 -I src -c -o obj/reserved_bit_store_s_page0.o src/reserved_bit_store_s_page0.S
riscv64-unknown-elf-gcc -march=rv64i_zicsr_zifencei -mabi=lp64 -nostdlib -nostartfiles -static -Wl,--build-id=none,--no-relax -T src/mpt_guest.ld -o reserved_bit_store_s_page0.elf obj/mpt_guest.o obj/reserved_bit_store_s_page0.o
