## modifies

* support standalone train port 
* fix bug of train information pass
* fix fake RAW rolback && fake MDP update
* fix fake strict prediction
* increase clean interval, 8192 cycles --> 8192 * 16 = 131072 cycles
* delete redundant code

## Performance

[**Base**] (https://github.com/OpenXiangShan/XiangShan/actions/runs/21537333846)

[**Enable MDP**] (https://github.com/OpenXiangShan/XiangShan/actions/runs/21473249897)

SPECint2006/GHz: **+0.26/GHz**

SPECfp2006/GHz: **+0.04/GHz**

<img width="531" height="1152" alt="image" src="https://github.com/user-attachments/assets/d7b4730e-324a-46e9-9431-d956f2d01cb2" />

Co-authored-by: @happy-lx 
Co-authored-by: @xiaofeibao-xjtu
