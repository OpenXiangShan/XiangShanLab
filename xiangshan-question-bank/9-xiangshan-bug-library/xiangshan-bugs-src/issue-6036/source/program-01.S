vsetivli zero, 4, e64, m1, ta, ma
csrr t0, vlenb
srli t0, t0, 3
csrw vstart, t0
vmv1r.v v8, v10
