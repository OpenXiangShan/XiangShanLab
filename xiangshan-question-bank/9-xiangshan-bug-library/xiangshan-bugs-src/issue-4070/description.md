For bits of mideleg that are zero, the corresponding bits in hideleg, hip, and hie are read-only zeros.

The VSSIP, VSTIP, VSEIP in mideleg are read-only ones when the H extension is implemented.

When the hypervisor extension is implemented, if a bit is zero in the same position in both mideleg
and mvien, then that bit is read-only zero in hideleg (in addition to being read-only zero in sip, sie,
hip, and hie). But if a bit for one of interrupts 13-63 is a one in either mideleg or mvien, then the same
bit in hideleg may be writable or may be read-only zero, depending on the implementation. No bits in
hideleg are ever read-only ones. The RISC-V Privileged Architecture further constrains bits 12:0 of
hideleg.
