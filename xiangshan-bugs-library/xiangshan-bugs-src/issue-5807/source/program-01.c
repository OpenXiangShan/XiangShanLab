      isa_mmio_misalign_data_addr_check(addr, vaddr, len, MEM_TYPE_READ, cross_page_load);
#ifdef CONFIG_ENABLE_CONFIG_MMIO_SPACE
      if (!mmio_is_real_device(addr)) {
        raise_read_access_fault(trap_type, vaddr);
        return 0;
      }
#endif // CONFIG_ENABLE_CONFIG_MMIO_SPACE
