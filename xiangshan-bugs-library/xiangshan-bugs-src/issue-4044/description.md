This PR is part of *RAS(Reliability, Accessibility, Serviceability)* error recovery features.

- Add a series of mmio-mapped CSR to control ICache ECC check & ECC inject features
- Implement ICache ECC injection
  - M-state software can write `eccctrl` to trigger error injection to meta/dataArray, next read can trigger auto-recovery (implemented in #3899)
- Remove custom CSR `Sfetchctl`

~May cause merge conflict with #4009, should be merged after that PR.~ Update: resolved

# Details
## CSR
The base address of the added mmio-mapped CSR is `0x38022080` and the registers is defined as below:
```
              64     10        7         4         2        1        0
0x00 eccctrl   | WARL | ierror | istatus | itarget | inject | enable |

              64 PAddrBits-1               0
0x08 ecciaddr  | WARL |       paddr        |
```
| CSR | field | desp |
| --- | --- | --- |
| eccctrl | enable | ECC check enable |
| eccctrl | inject | ECC inject enable (write 1 to trigger injection, read always 0) |
| eccctrl | itarget | ECC inject target<br>0: metaArray<br>1: rsvd<br>2: dataArray<br>3: rsvd |
| eccctrl | istatus | ECC inject status (read-only)<br>0: idle: inject controller idle, goes to working when received a inject request (i.e. write 1 to eccctrl.inject)<br>1: working: inject controller working, goes to injected when finished / error when failed<br>2: injected, goes to idle after read<br>3: rsvd<br>4: rsvd<br>5: rsvd<br>6: rsvd<br>7: error: inject failed (check eccctl.ierror for reason), goes to idle after read |
| eccctrl | ierror  | ECC error reason (read-only, valid only if `eccctrl.istatus==error`)<br>0: ECC check is not enabled (i.e. `!eccctrl.enable`)<br>1: inject target invalid (i.e. `eccctrl.itarget==rsvd`)<br>2: inject addr (i.e. `ecciaddr.paddr`) not in ICache<br>3: rsvd<br>4: rsvd<br>5: rsvd<br>6: rsvd<br>7: rsvd |
| ecciaddr | paddr | Physical address of the inject target |

## Inject method
```asm
$INJECT_ADDR:
  # maybe do something else
  ret

test:
  la t0, $BASE_ADDR     # load icache control base addr
  la t1, $INJECT_ADDR   # load inject addr
  jalr ra, 0(t1)        # jump to injected addr to load it i
  sd t1, 8(t0)          # set inject addr
  la t2, (target << 2 | 1 << 1 | 1 << 0)  # load inject target & inject enable & ecc enable
  sd t1, 0(t0)          # set inject enable & ecc enable
loop:
  ld t1, 0(t0)          # get ecc control state
  andi t1, t1, (0b11 << (4+1)) # get high bits of inject state
  beqz t1, loop         # if is idle, or working, loop

  addi t1, t1, -1       # t1 = inject_state[2:1] - 1
  bnez t1, error        # if is not injected, error or rsvd

  jalr ra, 0(t1)        # jump to injected addr to trigger error
  j    finish

error:
  # handle error
finish:
  # finish
```
Or, checkout https://github.com/OpenXiangShan/nexus-am/pull/48
