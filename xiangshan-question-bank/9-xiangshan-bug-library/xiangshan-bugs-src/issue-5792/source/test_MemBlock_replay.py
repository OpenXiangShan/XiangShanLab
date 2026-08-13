# coding=utf-8
"""
MemBlock 真实 DUT replay 场景测试。

当前覆盖三类首批稳定 replay：
  1. `FF`: store 地址已知、数据未就绪时，同地址 younger load 触发 forward-fail replay
  2. `DM`: cold cacheable load 触发 dcache miss replay
  3. `NC`: IO/uncache load 走 nc/outer 路径并出现 replay/nc_out 观测
  4. `RAW`: older store 地址长期未就绪，挤满 LQRAW 后 younger loads 触发 raw replay
  5. `RAR`: 更老 load 因精确 load-wait 暂停，更年轻同地址 load 完成后在 probe release 下触发 ld-ld violation
"""

from request_apis import LoadTxn, StoreTxn, ptr_inc, send_load
from sequences import (
    FlushStoreBuffersSequence,
    MmuSv39AddressSpaceConfig,
    MmuSv39AddressSpaceInstallSequence,
    ResetEnvSequence,
    ScalarCacheMissReplaySequence,
    ScalarForwardFailReplaySequence,
    ScalarNcReplaySequence,
    ScalarRarViolationSequence,
    ScalarRawReplaySequence,
    ScalarSqDataInvalidMatchInvalidTriggerSequence,
    SequenceState,
    Sv39GigapageMapping,
)


FORWARD_FAIL_STORE_ADDR = 0x80000120
FORWARD_FAIL_STORE_DATA = 0xCAFEBABE11223344
CACHE_MISS_ADDR = 0x80000280
NC_REPLAY_ADDR = 0x2000
NC_REPLAY_DATA = 0x5566778899AABBCC
RAW_REPLAY_STORE_ADDR = 0x80000600
RAW_REPLAY_STORE_DATA = 0x0BADF00D11223344
RAW_REPLAY_LOAD_BASE = 0x80000400
RAW_REPLAY_LOAD_COUNT = 36
RAR_OLDER_STORE_ADDR = 0x80000700
RAR_OLDER_STORE_DATA = 0x2233445566778899
RAR_ADDR = 0x80000880
RAR_OLD_DATA = 0x1020304050607080
RAR_NEW_DATA = 0x8877665544332211
MATCH_INVALID_ROOT_A = 0x88000000
MATCH_INVALID_ROOT_B = 0x88001000
MATCH_INVALID_MAIN_VA = 0x40001000
MATCH_INVALID_TLB_PRIME_VA = 0x40002000
MATCH_INVALID_PA_BASE_A = 0x80000000
MATCH_INVALID_PA_BASE_B = 0xC0000000
MATCH_INVALID_WARMUP_DATA = 0x13579BDF2468ACE0
MATCH_INVALID_TLB_PRIME_DATA = 0x0F0E0D0C0B0A0908
MATCH_INVALID_STORE_DATA = 0x1122334455667788


def _reset_env_and_state(env) -> SequenceState:
    return ResetEnvSequence(
        require_issue_lanes=(0, 3, 5),
        require_lq_ready=True,
        require_sq_ready=True,
    ).run(env)


def test_api_MemBlock_scalar_forward_fail_replay_smoke(env):
    """
    通过 `STA` 先于 `STD` 的真实 DUT 时序触发一次 `C_FF` replay。
    """

    initial_state = _reset_env_and_state(env)
    result = ScalarForwardFailReplaySequence(
        StoreTxn(
            req_id=0,
            sq_ptr=initial_state.sq_ptr,
            addr=FORWARD_FAIL_STORE_ADDR,
            data=FORWARD_FAIL_STORE_DATA,
        ),
        initial_lq_ptr=initial_state.next_lq_ptr,
        load_req_id=1,
        assert_no_outstanding=True,
    ).run(env)

    assert result.replay_event["cause"] == "FF", "FF replay 原因不匹配"
    assert result.replay_event["source"] in {"replay_queue", "replay_lane", "ldu"}, "FF replay 观测来源异常"
    assert result.replay_event["rob_idx_value"] == 1, "FF replay 的 load robIdx 不匹配"
    assert result.committed_store_view.committed, "FF replay 场景中的 store 未进入 committed"
    assert result.committed_store_view.addr == FORWARD_FAIL_STORE_ADDR, "FF replay store 地址不匹配"
    assert (int(result.committed_store_view.data) & ((1 << 64) - 1)) == FORWARD_FAIL_STORE_DATA

    drain_summary = FlushStoreBuffersSequence().run(env)
    assert drain_summary["drain_event_count"] > 0, "FF replay 场景未能在收尾阶段 drain store"
    env.assert_no_outstanding()


def test_api_MemBlock_scalar_cache_miss_replay_smoke(env):
    """
    单条 cold cacheable load 应先出现 `DM` replay，再完成正常写回。
    """

    initial_state = _reset_env_and_state(env)
    result = ScalarCacheMissReplaySequence(
        LoadTxn(
            req_id=0,
            addr=CACHE_MISS_ADDR,
            lq_ptr=initial_state.next_lq_ptr,
            sq_ptr=initial_state.sq_ptr,
        ),
        expected_completed_loads=1,
        assert_no_outstanding=True,
    ).run(env)

    assert result.replay_event["cause"] == "DM", "cache miss replay 原因不匹配"
    assert result.replay_event["source"] in {"replay_queue", "replay_lane", "ldu"}, "DM replay 观测来源异常"
    assert result.transport_stats_after["dcache_a_request_count"] > result.transport_stats_before["dcache_a_request_count"]
    assert result.transport_stats_after["dcache_d_response_count"] > result.transport_stats_before["dcache_d_response_count"]
    assert result.transport_stats_after["dcache_e_request_count"] > result.transport_stats_before["dcache_e_request_count"]
    assert result.transport_stats_after["outer_request_count"] == result.transport_stats_before["outer_request_count"]
    env.assert_no_outstanding()


def test_api_MemBlock_scalar_nc_replay_smoke(env):
    """
    单条 IO/uncache load 应观测到 `nc_out` 或 NC replay，并经 outer 路径完成。
    """

    initial_state = _reset_env_and_state(env)
    env.preload_u64(NC_REPLAY_ADDR, NC_REPLAY_DATA)
    result = ScalarNcReplaySequence(
        LoadTxn(
            req_id=0,
            addr=NC_REPLAY_ADDR,
            lq_ptr=initial_state.next_lq_ptr,
            sq_ptr=initial_state.sq_ptr,
        ),
        expected_completed_loads=1,
        assert_no_outstanding=True,
    ).run(env)

    assert result.replay_event["source"] in {"replay_queue", "replay_lane", "ldu", "nc_out"}, "NC replay 未出现在 NC 相关观测口"
    assert result.replay_event["cause"] == "NC", "NC replay 原因不匹配"
    assert result.transport_stats_after["outer_request_count"] > result.transport_stats_before["outer_request_count"]
    assert result.transport_stats_after["dcache_a_request_count"] == result.transport_stats_before["dcache_a_request_count"]
    env.assert_no_outstanding()


def test_api_MemBlock_scalar_raw_replay_smoke(env):
    """
    older store 长时间缺少 STA/STD，填满 LQRAW 后应观测到 RAW replay 与 rawNukeQuery backpressure。
    """

    initial_state = _reset_env_and_state(env)
    load_addresses = [RAW_REPLAY_LOAD_BASE + ((idx % 8) * 8) for idx in range(RAW_REPLAY_LOAD_COUNT)]
    for idx, addr in enumerate(load_addresses):
        env.preload_u64(addr, 0x1000_0000_0000_0000 + idx)

    result = ScalarRawReplaySequence(
        StoreTxn(
            req_id=0,
            sq_ptr=initial_state.sq_ptr,
            addr=RAW_REPLAY_STORE_ADDR,
            data=RAW_REPLAY_STORE_DATA,
        ),
        initial_lq_ptr=initial_state.next_lq_ptr,
        load_addresses=load_addresses,
        first_load_req_id=1,
        assert_no_outstanding=True,
    ).run(env)

    assert result.nuke_query_event["kind"] == "RAW", "RAW nuke query 类型不匹配"
    assert result.nuke_query_event["valid"] == 1 and result.nuke_query_event["ready"] == 0, "未观测到 rawNukeQuery backpressure"
    assert result.replay_event["cause"] == "RAW", "RAW replay 原因不匹配"
    assert result.replay_event["source"] in {"replay_queue", "replay_lane", "ldu"}, "RAW replay 观测来源异常"
    assert result.replay_event["sq_idx_value"] == 1, "RAW replay 对应的 younger sqIdx 不匹配"
    assert result.committed_store_view.committed, "RAW replay 场景中的 older store 未进入 committed"
    assert result.completed_load_count == RAW_REPLAY_LOAD_COUNT, "RAW replay 场景并未完成全部 younger loads"

    drain_summary = FlushStoreBuffersSequence().run(env)
    assert drain_summary["drain_event_count"] > 0, "RAW replay 场景未能在收尾阶段 drain older store"
    env.assert_no_outstanding()


def test_api_MemBlock_scalar_rar_violation_smoke(env):
    """
    更老 load 因精确 load-wait 暂停时，更年轻同地址 load 完成后若收到 probe release，应触发真实的 ld-ld violation。
    """

    initial_state = _reset_env_and_state(env)
    env.preload_u64(RAR_ADDR, RAR_OLD_DATA)
    younger_sq_ptr = ptr_inc(initial_state.sq_ptr, env.config.sequence.store_queue_size)
    younger_lq_ptr = ptr_inc(initial_state.next_lq_ptr, env.config.sequence.load_queue_size)

    result = ScalarRarViolationSequence(
        fake_store_txn=StoreTxn(
            req_id=0,
            sq_ptr=initial_state.sq_ptr,
            addr=RAR_OLDER_STORE_ADDR,
            data=RAR_OLDER_STORE_DATA,
        ),
        older_load_txn=LoadTxn(
            req_id=1,
            addr=RAR_ADDR,
            lq_ptr=initial_state.next_lq_ptr,
            sq_ptr=younger_sq_ptr,
            load_wait_bit=1,
            load_wait_strict=0,
            store_set_hit=1,
            wait_for_rob_idx_flag=0,
            wait_for_rob_idx_value=0,
        ),
        younger_load_txn=LoadTxn(
            req_id=2,
            addr=RAR_ADDR,
            lq_ptr=younger_lq_ptr,
            sq_ptr=younger_sq_ptr,
        ),
        release_new_value=RAR_NEW_DATA,
        assert_no_outstanding=True,
    ).run(env)

    assert result.release_event["valid"] == 1, "RAR 场景未观测到 cacheline release"
    assert (int(result.release_event["paddr"]) & ~0x3F) == (RAR_ADDR & ~0x3F), "RAR release cacheline 不匹配"
    assert result.younger_writeback["rob_idx_value"] == 2, "RAR 场景 younger load 未先完成首次写回"
    assert result.younger_writeback["data"] == RAR_OLD_DATA, "RAR 场景 younger load 首次写回未读到旧值"
    assert result.rar_nuke_response["resp_valid"] == 1 and result.rar_nuke_response["nuke"] == 1, "RAR nuke response 未命中"
    assert result.rar_nuke_response["rob_idx_value"] == 1, "RAR nuke response 未对应 older load"
    assert result.older_writeback["rob_idx_value"] == 1, "RAR 场景 older load 未在 release 后写回"
    assert result.older_writeback["data"] == RAR_NEW_DATA, "RAR 场景 older load 未读到 probe 后的新值"
    if result.violation_event is not None:
        assert result.violation_event["source"] == "memory_violation", "RAR violation 事件来源异常"
        assert result.violation_event["rob_idx_value"] == 1, "RAR violation 未对应 older load"
    assert result.fake_store_view.committed, "RAR 场景中的依赖 store 未进入 committed"
    assert result.completed_load_count == 1, "RAR 场景应仅完成 older load 的 commit-boundary compare"

    drain_summary = FlushStoreBuffersSequence().run(env)
    assert drain_summary["drain_event_count"] > 0, "RAR 场景收尾未成功 drain 辅助 store"
    env.assert_no_outstanding()


def test_api_MemBlock_scalar_sq_datainvalid_matchinvalid_nuke_smoke(env):
    """
    同一虚拟地址在 satp 切换前后映射到不同物理页。

    目标组合：
      - dcache hit 且无 cache error
      - SQ forward resp 同时给出 `dataInvalid=1` 与 `matchInvalid=1`
      - younger load 在 mem_to_ooo 侧触发 redirect/nuke（`memoryViolation`）
    """

    initial_state = _reset_env_and_state(env)
    root_a = MmuSv39AddressSpaceInstallSequence(
        MmuSv39AddressSpaceConfig(
            root_pt_addr=MATCH_INVALID_ROOT_A,
            mappings=(Sv39GigapageMapping(va=MATCH_INVALID_MAIN_VA, pa_base=MATCH_INVALID_PA_BASE_A),),
        )
    ).run(env)
    root_b = MmuSv39AddressSpaceInstallSequence(
        MmuSv39AddressSpaceConfig(
            root_pt_addr=MATCH_INVALID_ROOT_B,
            mappings=(
                Sv39GigapageMapping(va=MATCH_INVALID_MAIN_VA, pa_base=MATCH_INVALID_PA_BASE_B),
                Sv39GigapageMapping(va=MATCH_INVALID_TLB_PRIME_VA, pa_base=MATCH_INVALID_PA_BASE_B),
            ),
        )
    ).run(env)
    del root_a

    main_pa_b = root_b.translated_pa_for(MATCH_INVALID_MAIN_VA)
    tlb_prime_pa_b = root_b.translated_pa_for(MATCH_INVALID_TLB_PRIME_VA)
    env.preload_u64(main_pa_b, MATCH_INVALID_WARMUP_DATA)
    env.preload_u64(tlb_prime_pa_b, MATCH_INVALID_TLB_PRIME_DATA)
    env.preload_u64(MATCH_INVALID_MAIN_VA, MATCH_INVALID_STORE_DATA)
    env.mmu.allow_all_smode_access()

    warmup_load = LoadTxn(
        req_id=0,
        addr=main_pa_b,
        lq_ptr=initial_state.next_lq_ptr,
        sq_ptr=initial_state.sq_ptr,
    )
    env.expect_scalar_load(
        req_id=warmup_load.req_id,
        pdest=warmup_load.resolved_pdest,
        addr=main_pa_b,
        size=warmup_load.size,
        mask=warmup_load.mask,
    )
    send_load(env, warmup_load)
    warmup_writeback = env.wait_load_writeback_observed(
        rob_idx_flag=warmup_load.rob_idx_flag,
        rob_idx_value=warmup_load.rob_idx_value,
        data=MATCH_INVALID_WARMUP_DATA,
        max_cycles=512,
    )
    for _ in range(64):
        if env.get_completed_load_count() >= 1:
            break
        env.Step(1)
    assert env.get_completed_load_count() == 1, "物理预热 load 未完成 compare"

    with env.mmu.ptw_responder():
        trigger = ScalarSqDataInvalidMatchInvalidTriggerSequence(
            main_va=MATCH_INVALID_MAIN_VA,
            main_pa=main_pa_b,
            store_txn=StoreTxn(
                req_id=1,
                sq_ptr=initial_state.sq_ptr,
                addr=MATCH_INVALID_MAIN_VA,
                data=MATCH_INVALID_STORE_DATA,
            ),
            main_load_req_id=3,
            initial_state=SequenceState(
                next_lq_ptr=ptr_inc(initial_state.next_lq_ptr, env.config.sequence.load_queue_size),
                sq_ptr=initial_state.sq_ptr,
            ),
            activate_root_pt_addr=MATCH_INVALID_ROOT_B,
            tlb_prime_req_id=2,
            tlb_prime_va=MATCH_INVALID_TLB_PRIME_VA,
            tlb_prime_pa=tlb_prime_pa_b,
            tlb_prime_data=MATCH_INVALID_TLB_PRIME_DATA,
        ).run(env)

    env.mmu.disable_translation()

    assert warmup_writeback["data"] == MATCH_INVALID_WARMUP_DATA, "物理预热 load 未读到目标 cacheline 数据"
    assert trigger.tlb_prime_writeback["data"] == MATCH_INVALID_TLB_PRIME_DATA, "satp 切回 root-B 后的 TLB 预热 load 未完成"
    assert trigger.sq_forward_event["data_invalid_valid"] == 1, "SQ forward 未返回 dataInvalid=1"
    assert trigger.sq_forward_event["match_invalid"] == 1, "SQ forward 未返回 matchInvalid=1"
    assert trigger.sq_forward_event["forward_invalid"] == 0, "本场景不应退化为 forwardInvalid"
    assert trigger.sq_forward_event["data_invalid_value"] == trigger.store_sq_ptr.value, "dataInvalid 未指向 older store"
    assert trigger.memory_violation["valid"] == 1, "未观测到 younger load 的 memoryViolation/nuke"
    assert trigger.memory_violation["rob_idx_value"] == trigger.main_load.rob_idx_value, "memoryViolation 未对应 younger load"
    assert trigger.memory_violation["level"] == 1, "matchInvalid 应触发 flush 级 redirect"
    assert trigger.dcache_error_valid == 0, "场景不应出现 dcache error"
    if trigger.dcache_miss_signal is not None:
        assert trigger.dcache_miss_signal == 0, "主 load 应在 dcache hit 条件下命中"

    forbidden_replays = {
        event["cause"]
        for event in trigger.replay_events
        if event.get("source") in {"replay_queue", "replay_lane", "ldu", "nc_out"}
    }
    assert forbidden_replays.isdisjoint({"DM", "DR", "TM", "NC"}), f"主 load 不应走 miss/error 路径: {forbidden_replays}"
    assert (
        trigger.transport_stats_after_main["outer_request_count"]
        == trigger.transport_stats_before_main["outer_request_count"]
    ), "主 load 不应走 outer/uncache 路径"
    assert (
        trigger.transport_stats_after_main["dcache_a_request_count"]
        == trigger.transport_stats_before_main["dcache_a_request_count"]
    ), "命中场景不应额外触发 dcache refill 请求"
    assert (
        trigger.transport_stats_after_main["dcache_d_response_count"]
        == trigger.transport_stats_before_main["dcache_d_response_count"]
    ), "命中场景不应额外等待 dcache D 响应"
    assert trigger.committed_store_view.committed, "older store 未在收尾阶段进入 committed"

    drain_summary = FlushStoreBuffersSequence().run(env)
    assert drain_summary["drain_event_count"] > 0, "matchInvalid/nuke 场景收尾未成功 drain older store"
    env.assert_no_outstanding()


def test_api_MemBlock_small_replay_mix_smoke(env):
    """
    以固定模板串联 FF / DM / NC 三类 replay，验证真实 DUT 的小规模 replay 混合流。
    """

    state = _reset_env_and_state(env)

    ff_result = ScalarForwardFailReplaySequence(
        StoreTxn(
            req_id=0,
            sq_ptr=state.sq_ptr,
            addr=FORWARD_FAIL_STORE_ADDR,
            data=FORWARD_FAIL_STORE_DATA,
        ),
        initial_lq_ptr=state.next_lq_ptr,
        load_req_id=1,
    ).run(env)
    state = ff_result.final_state
    ff_drain_summary = FlushStoreBuffersSequence().run(env)
    assert ff_drain_summary["drain_event_count"] > 0, "mixed replay 中 FF 阶段的 store 未成功 drain"

    dm_result = ScalarCacheMissReplaySequence(
        LoadTxn(
            req_id=2,
            addr=CACHE_MISS_ADDR,
            lq_ptr=state.next_lq_ptr,
            sq_ptr=state.sq_ptr,
        ),
        expected_completed_loads=2,
    ).run(env)
    state = _reset_env_and_state(env)
    env.preload_u64(NC_REPLAY_ADDR, NC_REPLAY_DATA)
    nc_result = ScalarNcReplaySequence(
        LoadTxn(
            req_id=0,
            addr=NC_REPLAY_ADDR,
            lq_ptr=state.next_lq_ptr,
            sq_ptr=state.sq_ptr,
        ),
    ).run(env)

    causes = {ff_result.replay_event["cause"], dm_result.replay_event["cause"], nc_result.replay_event["cause"]}
    assert causes == {"FF", "DM", "NC"}, f"mixed replay 覆盖不完整: {causes}"
    assert nc_result.load_result.completed_load_count >= 1, "mixed replay 中 NC 阶段未完成 load compare"

    env.assert_no_outstanding()
