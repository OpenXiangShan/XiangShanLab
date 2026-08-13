* if hvictl.VTI = 0:
* the highest-priority pending-and-enabled major interrupt indicated
* by vsip and vsie other than a supervisor external interrupt(code 9),
* using the priority numbers assigned by hviprio1 and hviprio2. 
* 
* A hypervisor can choose to employ registers hviprio1 and hviprio2
* when emulating the (virtual) supervisor-level iprio array accessed
* indirectly through siselect and sireg (really vsiselect and vsireg)
* for a virtual hart. For interrupts not in the subset supported by
* hviprio1 and hviprio2, the priority number bytes in the emulated
* iprio array can be read-only zeros.
