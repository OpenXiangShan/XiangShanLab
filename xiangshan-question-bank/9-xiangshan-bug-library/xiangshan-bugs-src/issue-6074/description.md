When executing the `mret` instruction, the control logic of `vsstatus` was not connected, resulting in the inability to clear `vsstatus.SDT` when `mret` enters `VU` mode.
