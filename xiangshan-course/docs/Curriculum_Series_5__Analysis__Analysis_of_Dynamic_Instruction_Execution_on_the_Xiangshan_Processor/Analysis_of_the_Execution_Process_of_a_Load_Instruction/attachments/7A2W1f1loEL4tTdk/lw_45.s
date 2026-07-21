
/nfs/home/liruoshi/arch-fuzz/single.out/lw_45/lw_45.elf:     file format elf64-littleriscv


Disassembly of section .text.init:

0000000080000000 <_start>:
    80000000:	00201117          	auipc	sp,0x201
    80000004:	00010113          	mv	sp,sp
    80000008:	0e00006f          	j	800000e8 <main_c_entry>
    8000000c:	0001                	nop

Disassembly of section .text.trap:

0000000080000010 <trap_handler>:
    80000010:	342022f3          	csrr	t0,mcause
    80000014:	34102373          	csrr	t1,mepc
    80000018:	00035383          	lhu	t2,0(t1)
    8000001c:	0033f393          	andi	t2,t2,3
    80000020:	4e0d                	li	t3,3
    80000022:	01c38463          	beq	t2,t3,8000002a <not_compressed>
    80000026:	0309                	addi	t1,t1,2
    80000028:	a011                	j	8000002c <update_mepc>

000000008000002a <not_compressed>:
    8000002a:	0311                	addi	t1,t1,4

000000008000002c <update_mepc>:
    8000002c:	34131073          	csrw	mepc,t1
    80000030:	30200073          	mret
    80000034:	0001                	nop

Disassembly of section .text:

0000000080000036 <init_float_env>:
    80000036:	1141                	addi	sp,sp,-16 # 80200ff0 <stack+0xff0>
    80000038:	e406                	sd	ra,8(sp)
    8000003a:	e022                	sd	s0,0(sp)
    8000003c:	0800                	addi	s0,sp,16
    8000003e:	4781                	li	a5,0
    80000040:	00379073          	fscsr	a5
    80000044:	0001                	nop
    80000046:	60a2                	ld	ra,8(sp)
    80000048:	6402                	ld	s0,0(sp)
    8000004a:	0141                	addi	sp,sp,16
    8000004c:	8082                	ret

000000008000004e <init_vector_env>:
    8000004e:	1141                	addi	sp,sp,-16
    80000050:	e406                	sd	ra,8(sp)
    80000052:	e022                	sd	s0,0(sp)
    80000054:	0800                	addi	s0,sp,16
    80000056:	cd00f057          	vsetivli	zero,1,e32,m1,ta,ma
    8000005a:	0001                	nop
    8000005c:	60a2                	ld	ra,8(sp)
    8000005e:	6402                	ld	s0,0(sp)
    80000060:	0141                	addi	sp,sp,16
    80000062:	8082                	ret

0000000080000064 <apply_rva23_config>:
    80000064:	1101                	addi	sp,sp,-32
    80000066:	ec06                	sd	ra,24(sp)
    80000068:	e822                	sd	s0,16(sp)
    8000006a:	1000                	addi	s0,sp,32
    8000006c:	fea43423          	sd	a0,-24(s0)
    80000070:	fe843783          	ld	a5,-24(s0)
    80000074:	679c                	ld	a5,8(a5)
    80000076:	30179073          	csrw	misa,a5
    8000007a:	fe843783          	ld	a5,-24(s0)
    8000007e:	639c                	ld	a5,0(a5)
    80000080:	30079073          	csrw	mstatus,a5
    80000084:	57fd                	li	a5,-1
    80000086:	3b079073          	csrw	pmpaddr0,a5
    8000008a:	fe843783          	ld	a5,-24(s0)
    8000008e:	739c                	ld	a5,32(a5)
    80000090:	3a079073          	csrw	pmpcfg0,a5
    80000094:	fe843783          	ld	a5,-24(s0)
    80000098:	6b9c                	ld	a5,16(a5)
    8000009a:	30a79073          	csrw	menvcfg,a5
    8000009e:	f99ff0ef          	jal	80000036 <init_float_env>
    800000a2:	fadff0ef          	jal	8000004e <init_vector_env>
    800000a6:	6785                	lui	a5,0x1
    800000a8:	b007879b          	addiw	a5,a5,-1280 # b00 <_start-0x7ffff500>
    800000ac:	30279073          	csrw	medeleg,a5
    800000b0:	22200793          	li	a5,546
    800000b4:	30379073          	csrw	mideleg,a5
    800000b8:	0001                	nop
    800000ba:	60e2                	ld	ra,24(sp)
    800000bc:	6442                	ld	s0,16(sp)
    800000be:	6105                	addi	sp,sp,32
    800000c0:	8082                	ret

00000000800000c2 <disable_multicore>:
    800000c2:	1101                	addi	sp,sp,-32
    800000c4:	ec06                	sd	ra,24(sp)
    800000c6:	e822                	sd	s0,16(sp)
    800000c8:	1000                	addi	s0,sp,32
    800000ca:	f14027f3          	csrr	a5,mhartid
    800000ce:	fef43423          	sd	a5,-24(s0)
    800000d2:	fe843783          	ld	a5,-24(s0)
    800000d6:	c781                	beqz	a5,800000de <disable_multicore+0x1c>
    800000d8:	10500073          	wfi
    800000dc:	bff5                	j	800000d8 <disable_multicore+0x16>
    800000de:	0001                	nop
    800000e0:	60e2                	ld	ra,24(sp)
    800000e2:	6442                	ld	s0,16(sp)
    800000e4:	6105                	addi	sp,sp,32
    800000e6:	8082                	ret

00000000800000e8 <main_c_entry>:
    800000e8:	1141                	addi	sp,sp,-16
    800000ea:	e406                	sd	ra,8(sp)
    800000ec:	e022                	sd	s0,0(sp)
    800000ee:	0800                	addi	s0,sp,16
    800000f0:	fd3ff0ef          	jal	800000c2 <disable_multicore>
    800000f4:	00000517          	auipc	a0,0x0
    800000f8:	21450513          	addi	a0,a0,532 # 80000308 <rva23_full_config>
    800000fc:	f69ff0ef          	jal	80000064 <apply_rva23_config>
    80000100:	00000797          	auipc	a5,0x0
    80000104:	f1078793          	addi	a5,a5,-240 # 80000010 <trap_handler>
    80000108:	30579073          	csrw	mtvec,a5
    8000010c:	4081                	li	ra,0
    8000010e:	4101                	li	sp,0
    80000110:	4181                	li	gp,0
    80000112:	4201                	li	tp,0
    80000114:	4281                	li	t0,0
    80000116:	4301                	li	t1,0
    80000118:	4381                	li	t2,0
    8000011a:	4401                	li	s0,0
    8000011c:	4481                	li	s1,0
    8000011e:	4501                	li	a0,0
    80000120:	4581                	li	a1,0
    80000122:	4601                	li	a2,0
    80000124:	4681                	li	a3,0
    80000126:	4701                	li	a4,0
    80000128:	4781                	li	a5,0
    8000012a:	4801                	li	a6,0
    8000012c:	4881                	li	a7,0
    8000012e:	4901                	li	s2,0
    80000130:	4981                	li	s3,0
    80000132:	4a01                	li	s4,0
    80000134:	4a81                	li	s5,0
    80000136:	4b01                	li	s6,0
    80000138:	4b81                	li	s7,0
    8000013a:	4c01                	li	s8,0
    8000013c:	4c81                	li	s9,0
    8000013e:	4d01                	li	s10,0
    80000140:	4d81                	li	s11,0
    80000142:	4e01                	li	t3,0
    80000144:	4e81                	li	t4,0
    80000146:	4f01                	li	t5,0
    80000148:	4f81                	li	t6,0
    8000014a:	f2000053          	fmv.d.x	ft0,zero
    8000014e:	f20000d3          	fmv.d.x	ft1,zero
    80000152:	f2000153          	fmv.d.x	ft2,zero
    80000156:	f20001d3          	fmv.d.x	ft3,zero
    8000015a:	f2000253          	fmv.d.x	ft4,zero
    8000015e:	f20002d3          	fmv.d.x	ft5,zero
    80000162:	f2000353          	fmv.d.x	ft6,zero
    80000166:	f20003d3          	fmv.d.x	ft7,zero
    8000016a:	f2000453          	fmv.d.x	fs0,zero
    8000016e:	f20004d3          	fmv.d.x	fs1,zero
    80000172:	f2000553          	fmv.d.x	fa0,zero
    80000176:	f20005d3          	fmv.d.x	fa1,zero
    8000017a:	f2000653          	fmv.d.x	fa2,zero
    8000017e:	f20006d3          	fmv.d.x	fa3,zero
    80000182:	f2000753          	fmv.d.x	fa4,zero
    80000186:	f20007d3          	fmv.d.x	fa5,zero
    8000018a:	f2000853          	fmv.d.x	fa6,zero
    8000018e:	f20008d3          	fmv.d.x	fa7,zero
    80000192:	f2000953          	fmv.d.x	fs2,zero
    80000196:	f20009d3          	fmv.d.x	fs3,zero
    8000019a:	f2000a53          	fmv.d.x	fs4,zero
    8000019e:	f2000ad3          	fmv.d.x	fs5,zero
    800001a2:	f2000b53          	fmv.d.x	fs6,zero
    800001a6:	f2000bd3          	fmv.d.x	fs7,zero
    800001aa:	f2000c53          	fmv.d.x	fs8,zero
    800001ae:	f2000cd3          	fmv.d.x	fs9,zero
    800001b2:	f2000d53          	fmv.d.x	fs10,zero
    800001b6:	f2000dd3          	fmv.d.x	fs11,zero
    800001ba:	f2000e53          	fmv.d.x	ft8,zero
    800001be:	f2000ed3          	fmv.d.x	ft9,zero
    800001c2:	f2000f53          	fmv.d.x	ft10,zero
    800001c6:	f2000fd3          	fmv.d.x	ft11,zero
    800001ca:	4281                	li	t0,0
    800001cc:	5e02c057          	vmv.v.x	v0,t0
    800001d0:	5e02c0d7          	vmv.v.x	v1,t0
    800001d4:	5e02c157          	vmv.v.x	v2,t0
    800001d8:	5e02c1d7          	vmv.v.x	v3,t0
    800001dc:	5e02c257          	vmv.v.x	v4,t0
    800001e0:	5e02c2d7          	vmv.v.x	v5,t0
    800001e4:	5e02c357          	vmv.v.x	v6,t0
    800001e8:	5e02c3d7          	vmv.v.x	v7,t0
    800001ec:	5e02c457          	vmv.v.x	v8,t0
    800001f0:	5e02c4d7          	vmv.v.x	v9,t0
    800001f4:	5e02c557          	vmv.v.x	v10,t0
    800001f8:	5e02c5d7          	vmv.v.x	v11,t0
    800001fc:	5e02c657          	vmv.v.x	v12,t0
    80000200:	5e02c6d7          	vmv.v.x	v13,t0
    80000204:	5e02c757          	vmv.v.x	v14,t0
    80000208:	5e02c7d7          	vmv.v.x	v15,t0
    8000020c:	5e02c857          	vmv.v.x	v16,t0
    80000210:	5e02c8d7          	vmv.v.x	v17,t0
    80000214:	5e02c957          	vmv.v.x	v18,t0
    80000218:	5e02c9d7          	vmv.v.x	v19,t0
    8000021c:	5e02ca57          	vmv.v.x	v20,t0
    80000220:	5e02cad7          	vmv.v.x	v21,t0
    80000224:	5e02cb57          	vmv.v.x	v22,t0
    80000228:	5e02cbd7          	vmv.v.x	v23,t0
    8000022c:	5e02cc57          	vmv.v.x	v24,t0
    80000230:	5e02ccd7          	vmv.v.x	v25,t0
    80000234:	5e02cd57          	vmv.v.x	v26,t0
    80000238:	5e02cdd7          	vmv.v.x	v27,t0
    8000023c:	5e02ce57          	vmv.v.x	v28,t0
    80000240:	5e02ced7          	vmv.v.x	v29,t0
    80000244:	5e02cf57          	vmv.v.x	v30,t0
    80000248:	5e02cfd7          	vmv.v.x	v31,t0

000000008000024c <_l0>:
    8000024c:	800bad03          	lw	s10,-2048(s7)
    80000250:	8014ab03          	lw	s6,-2047(s1)
    80000254:	c01f2c83          	lw	s9,-1023(t5)
    80000258:	00002e03          	lw	t3,0(zero) # 0 <_start-0x80000000>
    8000025c:	001f2303          	lw	t1,1(t5)
    80000260:	00262a03          	lw	s4,2(a2)
    80000264:	00332503          	lw	a0,3(t1)
    80000268:	004da783          	lw	a5,4(s11)
    8000026c:	00502983          	lw	s3,5(zero) # 5 <_start-0x7ffffffb>
    80000270:	00692583          	lw	a1,6(s2)
    80000274:	007c2503          	lw	a0,7(s8)
    80000278:	0084a183          	lw	gp,8(s1)
    8000027c:	009f2103          	lw	sp,9(t5)
    80000280:	00a32e83          	lw	t4,10(t1)
    80000284:	00f4a883          	lw	a7,15(s1)
    80000288:	010e2683          	lw	a3,16(t3)
    8000028c:	01182883          	lw	a7,17(a6)
    80000290:	015f2c03          	lw	s8,21(t5)
    80000294:	01fca183          	lw	gp,31(s9)
    80000298:	020eab83          	lw	s7,32(t4)
    8000029c:	02102403          	lw	s0,33(zero) # 21 <_start-0x7fffffdf>
    800002a0:	03f6aa03          	lw	s4,63(a3)
    800002a4:	040e2203          	lw	tp,64(t3)
    800002a8:	04182e83          	lw	t4,65(a6)
    800002ac:	07f92983          	lw	s3,127(s2)
    800002b0:	0801ad83          	lw	s11,128(gp)
    800002b4:	0817a683          	lw	a3,129(a5)
    800002b8:	0ff22a83          	lw	s5,255(tp) # ff <_start-0x7fffff01>
    800002bc:	100f2a83          	lw	s5,256(t5)
    800002c0:	1011a203          	lw	tp,257(gp)
    800002c4:	1ff6a003          	lw	zero,511(a3)
    800002c8:	200da083          	lw	ra,512(s11)
    800002cc:	20152583          	lw	a1,513(a0)
    800002d0:	3ffda283          	lw	t0,1023(s11)
    800002d4:	4006a203          	lw	tp,1024(a3)
    800002d8:	4017a703          	lw	a4,1025(a5)
    800002dc:	7fe22803          	lw	a6,2046(tp) # 7fe <_start-0x7ffff802>
    800002e0:	7ff0ae83          	lw	t4,2047(ra)
    800002e4:	0001                	nop

00000000800002e6 <_good_exit>:
    800002e6:	4501                	li	a0,0
    800002e8:	0005006b          	.insn	4, 0x0005006b
    800002ec:	4505                	li	a0,1
    800002ee:	00202297          	auipc	t0,0x202
    800002f2:	d1228293          	addi	t0,t0,-750 # 80202000 <tohost>
    800002f6:	00a2b023          	sd	a0,0(t0)
    800002fa:	a001                	j	800002fa <_good_exit+0x14>
    800002fc:	0001                	nop
    800002fe:	0001                	nop
    80000300:	60a2                	ld	ra,8(sp)
    80000302:	6402                	ld	s0,0(sp)
    80000304:	0141                	addi	sp,sp,16
    80000306:	8082                	ret

Disassembly of section .rodata:

0000000080000308 <rva23_full_config>:
    80000308:	7e00                	ld	s0,56(a2)
    8000030a:	0046                	c.slli	zero,0x11
    8000030c:	0000                	unimp
    8000030e:	0000                	unimp
    80000310:	001411bf 40000000 	.insn	8, 0x40000000001411bf
    80000318:	0000003f 00000000 	.insn	8, 0x003f
    80000320:	0000003f 00000000 	.insn	8, 0x003f
    80000328:	0000000f          	fence	unknown,unknown
    8000032c:	0000                	unimp
	...

Disassembly of section .tohost:

0000000080202000 <tohost>:
	...

0000000080202008 <fromhost>:
	...
