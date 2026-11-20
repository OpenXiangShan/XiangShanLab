ENTRY(_start)

SECTIONS
{
    . = 0x80000000;
    .text : {
        *(.text*)
    }

    . = 0x80002000;
    .tohost : {
        *(.tohost)
    }

    .data : {
        *(.data*)
        *(.rodata*)
        *(.sdata*)
        *(.bss*)
        *(COMMON)
    }
}
