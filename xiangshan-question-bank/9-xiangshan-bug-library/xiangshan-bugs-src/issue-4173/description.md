> we can latch mshr.io.resp.bits since they are set on req.fire or acquire.fire, and keeps unchanged during response
> however, we should not latch mshr.io.resp.valid, since io.flush/fencei may clear it at any time

Old:
```
tilelink.resp.id -> | Reg | 
                       |
                       v
| MSHR0 |     ->    | --- |
| MSHR1 |     ->    | Mux | -> io.resp
|  ...  |     ->    |     |
| MSHRn |     ->    | --- |
```

New:
```
tilelink.resp.id 
               |
               v
| MSHR0 | -> | --- |
| MSHR1 | -> | Mux | -> | Reg | -> io.resp
|  ...  | -> |     |
| MSHRn | -> | --- |
```

Timing results are good, related path: slack -44ps -> positive.
