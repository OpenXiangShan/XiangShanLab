ppnLen: PaddrBits - Offset = 48 - 12 = 36
gvpnLen: GVaddrBits - Offset = 50 (Sv48x4) - 12 = 38

When hypervisor extension implemented, PPN length should be gvpnLen rather than ppnLen
