In the existing design, ICache assumes that once a gpf occurs, it works on the wrong path until a flush (redirect) arrives, so it can discard redundant gpf/gpaddr data to reduce power/area.

As shown below, the 2nd(orange) and 3rd(blue) gpaddr write to wayLookup is discarded.
![241011-wave-old](https://github.com/user-attachments/assets/878a0894-9d97-437d-aaa3-486d380da74f)

This assumption is mostly true, except:
1. Consider a 34B fetch block in which the first 32B have no exceptions and consist entirely of RVC instructions, and the last 2B cross a page boundary and a gpf occurs.
2. The IFU sends at most 16 instructions to the ibuffer, and therefore discards the last 2B. This way, none of the instructions received by the backend have exceptions and no flush (redirect) is generated.
3. The next fetch block again has a gpf, which ICache (wayLookup) considers redundant and discards the gpaddr data.
4. When the instruction with gpf is sent to the backend, the backend does not get the correct gpaddr and caused an error.

Fix: block writes when there is gpf/gpaddr data in wayLookup that is not read by mainPipe (i.e. is pending).

As shown below, the 1st(yellow) gpaddr write is bypassed to read port, the 2nd is stored in gpf entry, and the 3rd is stalled until the 2nd is read. So all 3 gpaddr data are sent to backend(gpaMem).
![241011-wave-new](https://github.com/user-attachments/assets/d856a08c-4a89-49f0-90da-81d140aee3b1)
