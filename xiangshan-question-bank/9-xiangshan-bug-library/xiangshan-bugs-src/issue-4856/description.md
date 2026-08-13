Bugs description:
* there are two  miss requests: 1) MissA; 2) MissB
* MissA request write to sram sucessfully (chose a invalid entry, it means that the data is `x`), 
* MissB need replay (will not go to s3), hence MissB read `x` from sram (it's data which MissA read)

How to fix:
* add a checker for a miss wether go to s3
