Bug descriptions:
* `extra_meta_resp` will be overrided when requset is blocked at `s1` in `MainPipe`.

How to fix:
* add a skid buffer
