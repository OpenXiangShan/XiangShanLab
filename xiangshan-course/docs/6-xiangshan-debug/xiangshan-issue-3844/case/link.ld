OUTPUT_ARCH(riscv)
ENTRY(_start)

PHDRS
{
  text PT_LOAD FLAGS(5);
}

SECTIONS
{
  . = 0x80000000;
  .text : ALIGN(4)
  {
    *(.text.init)
    *(.text*)
  } :text

  /DISCARD/ :
  {
    *(.comment)
    *(.note*)
  }
}
