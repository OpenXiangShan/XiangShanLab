# Bug Description

In multi-core case that st1 is access the memory between ld1 and ld2.

cpu0:

```
ld1
ld2
```

cpu1:

```
st1
```


If ld2 completes first but triggers a RAW hazard with the store pipeline (s2_nuke), it will issue a fast replay (the only reason for fast replay). During this fast replay, if ld1 enters the load pipeline and writes back normally, the system fails to detect the RAR violation where:
* Ld1 (issued earlier) accesses new data
* Ld2 (issued later) accesses old data

# Root Cause

Fast replay does not re-fetch data, so ordering checks miss the RAR conflict.

# Fix

If fast replay occurs, it must re-access the data.
