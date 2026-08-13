In a previous change, `get_off(vpn)` was mistakenly used, but it should have been `get_off(vaddr)` since vpn has already truncated the lower bits of vaddr
