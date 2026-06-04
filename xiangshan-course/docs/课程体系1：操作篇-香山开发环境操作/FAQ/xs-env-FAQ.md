# 简介
+ 这里是香山在配置和运行的可能出现问题的总结，
+ 如果出现文件缺失的问题可以先尝试删除原始文件之后重新下载
+ 其中部分命令的运行时间可能会很久，这里附上参考时间，便于读者预估自己的运行时间是否合理（不同的设备配置会有差异）

时间比较长的命令主要集中在香山的编译中：

## 编译和执行时间估算
```plain
# Common aliases for both bash & zsh
alias dve='dve -full64 &'
alias make_sim_verilog='make sim-verilog -j32'
alias make_sim_verilog_bosc='make sim-verilog XSTOP_PREFIX=bosc_ -j32'
alias make_verilog='make verilog -j32'
alias make_vcs='make simv CONSIDER_FSDB=1 -j32'
alias make_kmhv2_emu='make emu CONFIG=KunminghuV2Config EMU_TRACE=1 EMU_THREADS=32 -j32'
alias make_emu='make emu EMU_TRACE=1 EMU_THREADS=16 -j32'
alias make_emu_debug='make emu EMU_TRACE=1 EMU_THREADS=32 WITH_CHISELDB=1 -j32'
alias module_vcs='module load synopsys/vcs/Q-2020.03-SP2 synopsys/verdi/R-2020.12-SP1 license'
```

https://github.com/OpenXiangShan/xs-env

```plain
git clone https://github.com/OpenXiangShan/xs-env.git
cd xs-env
source setup.sh
source update-submodule.sh 
cd XiangShan
//git fetch origin
make init //or git submodule update --init
git branch // master 
// make emu CONFIG=MinimalConfig EMU_TRACE=1 -j200
// make emu CONFIG=KunminghuV2Config EMU_TRACE=1 EMU_THREADS=32 -j32

make emu EMU_TRACE=1 EMU_THREADS=16 -j32
//MFC  
make sim-verilog MFC=1 NUM_CORES=1 WITH_CONSTANTIN=0
//NoC
make verilog MFC=1 NUM_CORES=1 WITH_CONSTANTIN=0 CONFIG=XSNoCTopConfig

//add top-prefix
make sim-verilog -j32 XSTOP_PREFIX=bosc_
//default waveform formatm'k =cd  vcd
make emu EMU_THREADS=16 EMU_TRACE=1 -j32
//用PerfCCT
make emu EMU_THREADS=16 EMU_TRACE=1 WITH_CHISELDB=1 -j32 
//default waveform format = fst
make emu EMU_THREADS=16 EMU_TRACE=fst -j32

///./build/emu -i $AM_HOME/apps/hello/build/hello-riscv --enable-fork

./build/emu -i bin_path  --diff ready-to-run/riscv64-nemu-interpreter-so --enable-fork
```

Make verilog 生成 verilog 的时间 -j32 EMU_THREADS=16 的 配置下，需要643s

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/33538855/1773370350380-2711c306-0846-49f2-b94a-475291898a7c.png)

Verilog -> verilation simluation 生成`emu`

10:53:36 -> 11:28:23

verilator.cpp 这一步执行会非常的慢

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/33538855/1773370350611-e1946e95-b4fa-4bd3-b79d-4e97237379b9.png)

Building emu ...

11:28 - 11:43 emu 生成

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/33538855/1773370350495-7a196ad3-0484-4239-a743-2341b05afe48.png)

直接跑 hello-xiangshan, 执行时间 Host time spent: 17,892ms

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/33538855/1773370350491-3c8a1328-aee8-4ecb-8b03-6f1b5d893423.png)

# 环境问题
## 服务器上 GitHub 无法链接
1. 连接不上GitHub

```plain
liruoshi@open01:~$ git clone https://github.com/OpenXiangShan/xs-env
Cloning into 'xs-env'...
fatal: unable to access 'https://github.com/OpenXiangShan/xs-env/': Failed to connect to github.com port 443 after 134748 ms: Connection timed out
```

修改config文件之后，使用ssh命令，但是还是不行

```plain
liruoshi@open01:~$ git clone git@github.com:OpenXiangShan/xs-env.git
Cloning into 'xs-env'...
ssh: connect to host github.com port 22: Connection timed out
fatal: Could not read from remote repository.

Please make sure you have the correct access rights
and the repository exists.
```

这里修改服务器上面的config文件（注意更新地址 实在服务器上面）

```markdown
Host github.com
  Hostname ssh.github.com
  Port 443
  ProxyCommand nc -X 5 -x 172.38.10.247:8970 %h %p
```

2. 或者是配置 HTTPS 转换为 ssh 

```shell
[url "git@github.com:"]	insteadOf = https://github.com/[http "https://github.com"]	proxy = socks5h://172.38.10.247:8970
```

3. 没有权限

```shell
Already on 'master'
Your branch is up to date with 'origin/master'.
git@ssh.github.com: Permission denied (publickey).
fatal: Could not read from remote repository.
```

这里需要配置自己的 GitHub 账号里面的 SSH

## 环境变量设置
[<font style="color:rgb(64, 81, 181);">AM</font>](https://github.com/OpenXiangShan/nexus-am)<font style="color:rgba(0, 0, 0, 0.87);"> 是一个裸机运行时环境，用户可以使用 AM 来编译在香山裸机上运行的程序。使用 AM 编译程序的示例如下：</font>

<font style="color:rgba(0, 0, 0, 0.87);">进入实例的文件夹：运行命令 </font>`<font style="color:rgb(54, 70, 78);background-color:rgb(245, 245, 245);">make ARCH=riscv64-xs -j8</font>`

运行时**报错**：

```shell
liruoshi@open01:~/xs-env/nexus-am/apps/coremark$ make ARCH=riscv64-xs -j8
Makefile:8: /Makefile.app: No such file or directory
make: *** No rule to make target '/Makefile.app'.  Stop.
liruoshi@open01:~/xs-env$ source env.sh
SET XS_PROJECT_ROOT: /nfs/home/liruoshi/xs-env
SET NOOP_HOME (XiangShan RTL Home): /nfs/home/liruoshi/xs-env/XiangShan
SET NEMU_HOME: /nfs/home/liruoshi/xs-env/NEMU
SET AM_HOME: /nfs/home/liruoshi/xs-env/nexus-am
SET DRAMSIM3_HOME: /nfs/home/liruoshi/xs-env/DRAMsim3
liruoshi@open01:~/xs-env$ cd ..
liruoshi@open01:~$ cd xs-env/nexus-am/apps/coremark
liruoshi@open01:~/xs-env/nexus-am/apps/coremark$ make ARCH=riscv64-xs -j8
# Building coremark [riscv64-xs] with AM_HOME {/nfs/home/liruoshi/xs-env/nexus-am}
+ CC src/core_portme.c
make: riscv64-unknown-linux-gnu-gcc: No such file or directory
make: *** [/nfs/home/liruoshi/xs-env/nexus-am/Makefile.compile:29: /nfs/home/liruoshi/xs-env/nexus-am/apps/coremark/build/riscv64-xs//./src/core_portme.o] Error 127
```

解决：缺少 `$AM_HOME`环境变量和 gcc 编译器的路径变量

前者要运行 `env.sh`:实现环境变量的设置

后者是加入环境变量：`<font style="color:rgb(15, 17, 21);background-color:rgb(237, 243, 254);">export PATH=$PATH:安装 gcc 的位置 </font>`

<font style="color:rgba(0, 0, 0, 0.87);">生成的</font>`<font style="color:rgb(54, 70, 78);background-color:rgb(245, 245, 245);">coremark-riscv64-xs.bin</font>`<font style="color:rgba(0, 0, 0, 0.87);">可以作为仿真中的程序输入。要使用 AM 生成自定义的 workload</font>

## NEMU 编译 
NEMU是difftest机制中用于参考的golden模型。

<font style="color:rgba(0, 0, 0, 0.87);">在使用 NEMU 模拟器运行 workload 时，我们需要将模拟器的</font><font style="color:#2F4BDA;">虚拟外设与香山的外设地址空间对齐</font><font style="color:rgba(0, 0, 0, 0.87);">。进入 </font>`<font style="color:rgb(54, 70, 78);background-color:rgb(245, 245, 245);">/xs-env/NEMU</font>`<font style="color:rgba(0, 0, 0, 0.87);"> 目录，运行以下命令：</font>

```shell
cd $NEMU_HOME 
make clean 
make riscv64-xs_defconfig 
//将 NEMU 编译成裸机，从而可以运行之前步骤的 Coremark 
make -j 

//与香山核协同仿真的 NEMU 模拟器配置与独立运行时略有不同。我们使用以下的命令编译仿真中使用的 NEMU：
make clean-softfloat / make clean-all
make riscv64-xs-ref_defconfig 
//如果不想要 make clean-softfloat  或 make clean-all，并且有需求编译 -so
//可以先编译 make riscv64-xs-ref_defconfig 
```

选择执行后者，<font style="color:rgba(0, 0, 0, 0.87);">这个命令会将 NEMU 模拟器编译成动态链接库，将会在 </font>`<font style="color:rgb(54, 70, 78);background-color:rgb(245, 245, 245);">build</font>`<font style="color:rgba(0, 0, 0, 0.87);"> 目录下生成文件 </font>`<font style="color:rgb(54, 70, 78);background-color:rgb(245, 245, 245);">riscv64-nemu-interpreter-so</font>`<font style="color:rgba(0, 0, 0, 0.87);">，从而接入到香山仿真差分测试中。</font>[新NEMU临时使用指南](https://github.com/OpenXiangShan/NEMU/wiki/%E6%96%B0NEMU%E4%B8%B4%E6%97%B6%E4%BD%BF%E7%94%A8%E6%8C%87%E5%8D%97)

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/33538855/1768527485760-21279457-5f14-4234-a54a-0e3c7f07cc36.png)

:::info
<font style="color:rgba(0, 0, 0, 0.87);">后者编译的时候会报错：</font>

:::

```shell
+ ccache g++ /nfs/home/liruoshi/xs-env/NEMU/build/riscv64-nemu-interpreter-so
/usr/bin/ld: resource/softfloat/build/softfloat.a(s_mulAddF64.o): warning: relocation against `softfloat_roundingMode' in read-only section `.text'
/usr/bin/ld: resource/softfloat/build/softfloat.a(f16_roundToInt.o): relocation R_X86_64_PC32 against symbol `softfloat_exceptionFlags' can not be used when making a shared object; recompile with -fPIC
/usr/bin/ld: final link failed: bad value
collect2: error: ld returned 1 exit status
make: *** [/nfs/home/liruoshi/xs-env/NEMU/scripts/build.mk:81: /nfs/home/liruoshi/xs-env/NEMU/build/riscv64-nemu-interpreter-so] Error 1
```

**解决办法**：在ready-to-run文件夹下有编译好的nemu动态链接库，可以直接用，后面在使用的时候加入其路径

```shell
liruoshi@open01:~/xs-env/NEMU$ cd ready-to-run
liruoshi@open01:~/xs-env/NEMU/ready-to-run$ ls
auto_bump.sh             copy_and_run.bin          microbench.bin                          riscv64-nemu-interpreter-so
bump_all_from_docker.sh  coremark-2-iteration.bin  README.md                               riscv64-nutshell-spike-so
bump-nemu.sh             Dockerfile                riscv64-nemu-interpreter-debug-so       riscv64-spike-so
bump-spike-nutshell.sh   flash_recursion_test.bin  riscv64-nemu-interpreter-dual-debug-so
bump-spike.sh            linux.bin                 riscv64-nemu-interpreter-dual-so
```

<font style="color:rgba(0, 0, 0, 0.87);">使用 NEMU 作为香山的测试程序：</font>

## make simv 的环境变量
<font style="color:rgba(0, 0, 0, 0.87);">进入香山根目录，执行，进行编译：</font>

```bash
-bash-4.2$ make simv RELEASE=1 CONSIDER_FSDB=1 -j16
make -C ./difftest simv NUM_CORES=1 RTL_SUFFIX=sv
make[1]: Entering directory `/nfs/home/liruoshi/xs-env/XiangShan/difftest'
vcs.mk:51: *** VERDI_HOME is not set. Try whereis verdi, abandon /bin/verdi and set VERID_HOME manually.  Stop.
make[1]: Leaving directory `/nfs/home/liruoshi/xs-env/XiangShan/difftest'
make: *** [simv] Error 2
```

缺少环境变量。运行` source ~/.bashrc`

## make  verilog 编译问题
make_verilog 编译出现问题，一般重新走一遍上述流程就可以走通

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/33538855/1773383742683-c7a0e769-21e6-4ee5-8793-03e0be92a064.png)

可能原因： make init 这一步 git submodule update 可能失败

NOTE 执行 make_verilog 一定要 export NOOP_HOME 环境变量

## make emu error
在 make_emu 过程中 ， 小机房的 网络 断连之后，即使 make clean, 再重新 make emu 也可能出现 make error

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/33538855/1773383742651-129e6740-6a3e-4e81-8aa3-2aaa4210db09.png)

Chisel 升级版本 7 之后 导致 chisel -> firrtl 变快， firrtl -> verilog 变慢， 整体 编译速度变慢

# 命令配置问题
生成波形的时候报错

```bash
liruoshi@open01:~/xs-env/XiangShan$ ./build/simv +workload=./ready-to-run/microbench.bin  +diff=./ready-to-run/riscv64-nemu-in
terpreter-so +dump-wave=fsdb
./build/simv: /lib/x86_64-linux-gnu/libpthread.so.0: version `GLIBC_PRIVATE' not found (required by ./build/simv)
```

这里需要在 eda01 服务器上面，不能在 open01 上





进程数不能设置的太大，本质应该是 verilator 问题

EMU_THREADS=xx 和 -jxxx, 进程数 会报 

make emu EMU_THREADS=64 EMU_TRACE=1 -j128

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/33538855/1773383070228-59cb3ca2-7987-47d1-b27d-78f1e075d4f0.png)



# JDK 版本问题
动机：切换到 2024-04-14 旧的版本，复现bug

遇到的问题：



<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/28590141/1779795677325-e285fc80-db76-46da-a557-e36368c51ea6.png)



问题猜测：

jdk 或者 mill 版本问题

可以跑通的版本

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/28590141/1779795763820-12e9bee8-c359-49fa-a7a4-4837c777513b.png)

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/28590141/1779795893350-3d562595-001b-45a5-9c22-3e3cba396cdd.png)



服务器的版本

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/28590141/1779795983783-26283eb9-8c78-4ec2-b24c-acf46df0933d.png)



1. mill --version需要在 make init 之后执行，否则会出现

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/28590141/1779795799739-3a64849b-acb1-49bc-b14a-b110dfa4a9e7.png)

2. mill 版本 会跟着分支走，不同分支的 mill --verison 跑出来的结果也不一样，排除mill --version 问题
3. 对jdk 进行降级处理，设置环境变量



<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/28590141/1779796106079-1a9673e7-d5a2-4b77-929c-07ecae101733.png)



4. 重新查看 jdk 和 mill 版本号

查看过程中，可能遇到的问题

<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/28590141/1779796273871-7cefd161-6844-4cbc-9fb1-8719151a6d3c.png)

解决方法， kill java 相关的进程，或者重启terminal



<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/28590141/1779796427697-0088e2aa-fa7b-4313-88ac-9d422351ae59.png)



重新查看版本



<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/28590141/1779796392562-96bb89d0-9857-4029-ba65-78e05902a569.png)



开始编译

NOTE !!! 编译前 注意设置环境变量，问题已解决



<!-- 这是一张图片，ocr 内容为： -->
![](https://cdn.nlark.com/yuque/0/2026/png/28590141/1779796458987-c00c9795-4316-4fea-9c02-89a0944bb36d.png)

# 参考样式
[DeepEP](https://www.deepep.org/?f_link_type=f_linkinlinenote&flow_extra=eyJpbmxpbmVfZGlzcGxheV9wb3NpdGlvbiI6MCwiZG9jX3Bvc2l0aW9uIjowLCJkb2NfaWQiOiIwN2RjODRmODk4OGFiNzc2LWRmN2JhNGYzMDlkMDBiMzUifQ%3D%3D)

