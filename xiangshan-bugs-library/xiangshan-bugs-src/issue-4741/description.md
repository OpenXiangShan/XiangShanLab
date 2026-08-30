Bug description:
Address `0x15002cf00` has a valid cacheline at setidx`0x3c`, then there is an sbuffer write request, setidx=`0xfc`, it will miss and enters the `MissQueue`, then wait for refill. There is another refill request sent by `MissQueue` with setidx=`0xfc`, it will replace this cacheline, but the tag (`0x15002cf00`) is already in the `MissQueue`, so it will blocks

How to fix:
Add alias bit comparison to the blocking logic of replace and probe
