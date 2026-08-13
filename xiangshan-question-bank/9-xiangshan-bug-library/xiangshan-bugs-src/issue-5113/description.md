Tested on test-bpu branch with BpTrace, direct branches mispredicts 1045 -> 111

Typical pattern before change:
```csv
1556,143,0x40000e4b,p1,0,15,-,None,None,0x40000e58
1556,143,0x40000e4b,p3,0,15,-,None,None,0x40000e58
1568,143,0x40000e4b,t0,1,3,1,Direct,None,0x40000e6b
1603,167,0x40000e4b,p1,1,3,-,Direct,None,0x40000e6b
1603,167,0x40000e4b,p3,0,15,-,None,None,0x40000e58
```

after change:
```csv
1556,143,0x40000e4b,p1,0,15,-,None,None,0x40000e58
1556,143,0x40000e4b,p3,0,15,-,None,None,0x40000e58
1568,143,0x40000e4b,t0,1,3,1,Direct,None,0x40000e6b
1603,167,0x40000e4b,p1,1,3,-,Direct,None,0x40000e6b
1603,167,0x40000e4b,p3,1,3,-,Direct,None,0x40000e6b
```

where `0x40000e4b` is a fetch block containing only one `c.j` instrction.

`position = 3 > getAlignedOffset(11) = 3` does not holds so it's filtered-out, causing misprediction.
