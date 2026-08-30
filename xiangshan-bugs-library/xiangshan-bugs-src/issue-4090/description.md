In PTW/LLPTW/HPTW, mem.resp.fire is set in first cycle, but mem.resp.data comes in second cycle. The signal full_gvpn depends on mem data, but it is incorrectly updated in first cycle, retrieving the old value. This leads to an in correct GPF.

This patch tries to fix this problem. It splits full_gvpn into wire and reg, and introduces a new signal to control the update of reg and the selection between wire and reg.
