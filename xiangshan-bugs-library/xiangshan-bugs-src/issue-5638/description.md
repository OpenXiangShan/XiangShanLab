No functional change, just compile-time log mismatching with actual behavior. Detail:

Log:
```
[749] MainBtb:
[749]   Size(set, way, align, internal): 256 * 4 * 2 * 4 = 8192
[749]   Address fields:
[749]     50| 49..32 | 31..16 | 15...8 | 7.............6 | ...........5 | 4.........0 |
[749]       | unused |    tag | setIdx | internalBankIdx | alignBankIdx | alignOffset |
[749]                           | 13...................6 |
[749]                           |         replacerSetIdx |
[749]                     | 20....................................................1 |
[749]                     |                                             targetLower |
[749]                                                                | 5..........1 |
[749]                                                                |     position |
[749]                                                 | 6.........................1 |
[749]                                                 |                 cfiPosition |
```

Actual: 
- position = pc[4:1]
- cfiPosition = pc[5:1]

The only use case is `getAlignBankIndexFromPosition(...) = addrFields.extractFrom("cfiPosition", "alignBankIdx", ...)`, it uses `(field cfiPosition).start` but this typo only affects `....end`, so no functional impact.
