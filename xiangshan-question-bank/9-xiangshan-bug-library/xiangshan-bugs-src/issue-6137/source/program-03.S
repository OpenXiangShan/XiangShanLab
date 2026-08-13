gadget:
    ld      s1, 0(s0)          # attack: secret_data
    andi    s1, s1, 0xff
    slli    s1, s1, 6
    la      s2, probe_array
    add     s2, s2, s1
    ld      s3, 0(s2)          # DCache address encodes secret byte
    j       fallthrough
