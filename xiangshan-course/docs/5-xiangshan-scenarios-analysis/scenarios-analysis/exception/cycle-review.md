# 连续逐拍 review 索引

由 analyze_trap.py 从 wavekit 采样值生成。每行一拍；列出的事件只覆盖本表选定的 trap/返回接口，不代表处理器其他模块没有活动。

完整路径见 evidence/column-map.json；正文的插图⑥/⑧/⑪/⑫解释这些信号。C 是文件绝对正沿采样序号，时间单位 ps。

| C | ps | ROB 状态 | CSR 状态 | 本拍有效事件 |
| --- | --- | --- | --- | --- |
| 4250 | 8500 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4251 | 8502 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4252 | 8504 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4253 | 8506 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4254 | 8508 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4255 | 8510 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4256 | 8512 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4257 | 8514 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4258 | 8516 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4259 | 8518 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4260 | 8520 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4261 | 8522 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4262 | 8524 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4263 | 8526 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4264 | 8528 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4265 | 8530 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4266 | 8532 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4267 | 8534 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4268 | 8536 | 0 | 0 | CSR 输入 fire，ROB=20 |
| 4269 | 8538 | 0 | 2 | CSR 输出 fire，ROB=20，EX11=1，xRET redirect=0 |
| 4270 | 8540 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4271 | 8542 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4272 | 8544 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4273 | 8546 | 0 | 0 | ROB flush：ROB=20 |
| 4274 | 8548 | 0 | 0 | ROB exception.valid=1 |
| 4275 | 8550 | 1 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4276 | 8552 | 1 | 0 | CSR trap：PC=0x80000044，向量=0x800；目标更新=0x80000100 |
| 4277 | 8554 | 0 | 0 | mepc→0x80000044；mcause→0xb；mstatus→0x40a00001800 |
| 4278 | 8556 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4279 | 8558 | 0 | 0 | 前端 redirect=0x80000100，level=1 |
| 4280 | 8560 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4281 | 8562 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4282 | 8564 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4283 | 8566 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4284 | 8568 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4285 | 8570 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4286 | 8572 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4287 | 8574 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4288 | 8576 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4289 | 8578 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4290 | 8580 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4291 | 8582 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4292 | 8584 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4293 | 8586 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4294 | 8588 | 0 | 0 | CSR 输入 fire，ROB=20 |
| 4295 | 8590 | 0 | 2 | CSR 输出 fire，ROB=20，EX11=0，xRET redirect=0 |
| 4296 | 8592 | 0 | 0 | CSR 输入 fire，ROB=21 |
| 4297 | 8594 | 0 | 2 | CSR 输出 fire，ROB=21，EX11=0，xRET redirect=0 |
| 4298 | 8596 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4299 | 8598 | 0 | 0 | CSR 输入 fire，ROB=22 |
| 4300 | 8600 | 0 | 2 | CSR 输出 fire，ROB=22，EX11=0，xRET redirect=0 |
| 4301 | 8602 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4302 | 8604 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4303 | 8606 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4304 | 8608 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4305 | 8610 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4306 | 8612 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4307 | 8614 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4308 | 8616 | 0 | 0 | CSR 输入 fire，ROB=23 |
| 4309 | 8618 | 0 | 2 | CSR 输出 fire，ROB=23，EX11=0，xRET redirect=0 |
| 4310 | 8620 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4311 | 8622 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4312 | 8624 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4313 | 8626 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4314 | 8628 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4315 | 8630 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4316 | 8632 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4317 | 8634 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4318 | 8636 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4319 | 8638 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4320 | 8640 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4321 | 8642 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4322 | 8644 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4323 | 8646 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4324 | 8648 | 0 | 0 | CSR 输入 fire，ROB=41 |
| 4325 | 8650 | 0 | 2 | CSR 输出 fire，ROB=41，EX11=0，xRET redirect=0 |
| 4326 | 8652 | 0 | 0 | mepc→0x80000050 |
| 4327 | 8654 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4328 | 8656 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4329 | 8658 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4330 | 8660 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4331 | 8662 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4332 | 8664 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4333 | 8666 | 0 | 0 | 目标更新=0x80000050；CSR 输入 fire，ROB=42 |
| 4334 | 8668 | 0 | 2 | CSR 输出 fire，ROB=42，EX11=0，xRET redirect=1；mstatus→0xa00000080 |
| 4335 | 8670 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4336 | 8672 | 0 | 0 | 前端 redirect=0x80000050，level=0 |
| 4337 | 8674 | 1 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4338 | 8676 | 1 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4339 | 8678 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4340 | 8680 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4341 | 8682 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4342 | 8684 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4343 | 8686 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4344 | 8688 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4345 | 8690 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4346 | 8692 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4347 | 8694 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4348 | 8696 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4349 | 8698 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4350 | 8700 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4351 | 8702 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4352 | 8704 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4353 | 8706 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4354 | 8708 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4355 | 8710 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4356 | 8712 | 0 | 0 | 前端 redirect=0x800000a0，level=0 |
| 4357 | 8714 | 1 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4358 | 8716 | 1 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4359 | 8718 | 1 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4360 | 8720 | 1 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4361 | 8722 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4362 | 8724 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4363 | 8726 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4364 | 8728 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4365 | 8730 | 0 | 0 | ROB flush：ROB=52 |
| 4366 | 8732 | 0 | 0 | ROB exception.valid=1 |
| 4367 | 8734 | 1 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4368 | 8736 | 1 | 0 | CSR trap：PC=0x80000078，向量=0x4；目标更新=0x80000100 |
| 4369 | 8738 | 0 | 0 | mepc→0x80000078；mcause→0x2；mtval→0xffffffff；mstatus→0x40a00001800 |
| 4370 | 8740 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4371 | 8742 | 0 | 0 | 前端 redirect=0x80000100，level=1 |
| 4372 | 8744 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4373 | 8746 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4374 | 8748 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4375 | 8750 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4376 | 8752 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4377 | 8754 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4378 | 8756 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4379 | 8758 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4380 | 8760 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4381 | 8762 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4382 | 8764 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4383 | 8766 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4384 | 8768 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4385 | 8770 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4386 | 8772 | 0 | 0 | CSR 输入 fire，ROB=52 |
| 4387 | 8774 | 0 | 2 | CSR 输出 fire，ROB=52，EX11=0，xRET redirect=0 |
| 4388 | 8776 | 0 | 0 | CSR 输入 fire，ROB=53 |
| 4389 | 8778 | 0 | 2 | CSR 输出 fire，ROB=53，EX11=0，xRET redirect=0 |
| 4390 | 8780 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4391 | 8782 | 0 | 0 | CSR 输入 fire，ROB=54 |
| 4392 | 8784 | 0 | 2 | CSR 输出 fire，ROB=54，EX11=0，xRET redirect=0 |
| 4393 | 8786 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4394 | 8788 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4395 | 8790 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4396 | 8792 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4397 | 8794 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4398 | 8796 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4399 | 8798 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4400 | 8800 | 0 | 0 | CSR 输入 fire，ROB=55 |
| 4401 | 8802 | 0 | 2 | CSR 输出 fire，ROB=55，EX11=0，xRET redirect=0 |
| 4402 | 8804 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4403 | 8806 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4404 | 8808 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4405 | 8810 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4406 | 8812 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4407 | 8814 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4408 | 8816 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4409 | 8818 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4410 | 8820 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4411 | 8822 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4412 | 8824 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4413 | 8826 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4414 | 8828 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4415 | 8830 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4416 | 8832 | 0 | 0 | CSR 输入 fire，ROB=73 |
| 4417 | 8834 | 0 | 2 | CSR 输出 fire，ROB=73，EX11=0，xRET redirect=0 |
| 4418 | 8836 | 0 | 0 | mepc→0x80000084 |
| 4419 | 8838 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4420 | 8840 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4421 | 8842 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4422 | 8844 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4423 | 8846 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4424 | 8848 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4425 | 8850 | 0 | 0 | 目标更新=0x80000084；CSR 输入 fire，ROB=74 |
| 4426 | 8852 | 0 | 2 | CSR 输出 fire，ROB=74，EX11=0，xRET redirect=1；mstatus→0xa00000080 |
| 4427 | 8854 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4428 | 8856 | 0 | 0 | 前端 redirect=0x80000084，level=0 |
| 4429 | 8858 | 1 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4430 | 8860 | 1 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4431 | 8862 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4432 | 8864 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4433 | 8866 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4434 | 8868 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4435 | 8870 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4436 | 8872 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4437 | 8874 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4438 | 8876 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4439 | 8878 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4440 | 8880 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4441 | 8882 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4442 | 8884 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4443 | 8886 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
| 4444 | 8888 | 0 | 0 | 所选 trap/CSR 接口无新有效事件；其他阶段见 stage-events.csv |
