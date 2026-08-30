#!/usr/bin/env python3
"""Focused regression tests for the deterministic case analyzer."""

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "analyze_case.py"
SPEC = importlib.util.spec_from_file_location("difftest_analyze_case", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
ANALYZER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ANALYZER)


class ParserTests(unittest.TestCase):
    def parse(self, text: str):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "run.log"
            path.write_text(text, encoding="utf-8")
            return ANALYZER.parse_log(path)[0]

    def test_commit_ring_writer_is_separate_from_reporter_pc(self):
        parsed = self.parse(
            "\n".join(
                (
                    "a4 different at pc = 0x0080000c8c, right = 0x77, wrong = 0x0",
                    "[c=8979][26] commit pc 0000000080000c8c inst 06f71b63 wen 0 dst 22 data 1122334455667788 idx 130",
                    "[c=8979][28] commit pc 0000000080000c94 inst ce074703 wen 1 dst 14 data 0000000000000000 idx 132",
                )
            )
        )
        correlation = ANALYZER.correlate_commit_writers([parsed])[0]
        self.assertEqual(correlation["mismatching_register"], "x14")
        self.assertEqual(correlation["reporter_pc_candidates"], ["0x80000c8c"])
        producer = correlation["producer_candidates"][0]
        self.assertEqual(producer["pc"], "0x80000c94")
        self.assertEqual(producer["write_data_matches_side_labels"], ["wrong"])
        self.assertEqual(producer["cycle_candidates"], [8979])
        self.assertEqual(producer["commit_sequence_candidates"], [28])

    def test_summary_word_different_is_not_a_mismatch(self):
        parsed = self.parse(
            "Seed=1 Guest cycle spent: 8983 (this will be different from cycleCnt)\n"
        )
        self.assertEqual(parsed["events"], [])

    def test_signal_name_mismatch_is_not_an_architectural_event(self):
        parsed = self.parse(
            "[PERF ][time=8348] frontend.ras_top_mismatch_s1fall, 0\n"
        )
        self.assertEqual(parsed["events"], [])

    def test_nemu_memory_event_keeps_address_data_and_length(self):
        parsed = self.parse(
            "[NEMU] paddr write addr:0x8000996f, data:11223344, len:8, mode:3\n"
        )
        event = parsed["events"][0]
        self.assertEqual(event["types"], ["memory-write"])
        self.assertEqual(event["commit_fields"], [])
        self.assertEqual(
            event["store_fields"],
            [
                {"field": "addr", "value": "0x8000996f"},
                {"field": "data", "value": "11223344"},
                {"field": "len", "value": "8"},
                {"field": "mode", "value": "3"},
            ],
        )

    def test_log_instruction_word_is_normalized_to_file_byte_order(self):
        self.assertEqual(
            ANALYZER.decode_log_instruction_bits("6f71b63", expected_length=4),
            "631bf706",
        )

    def test_readelf_spaced_execute_flag_is_parsed(self):
        summary = ANALYZER.parse_readelf(
            "  LOAD           0x001000 0x0000000080000000 0x0000000080000000 "
            "0x0000cc 0x0000cc R E 0x1000\n"
            "  Tag_RISCV_arch: \"rv64i2p1_m2p0\"\n"
        )
        self.assertEqual(summary["load_segments"][0]["flags"], "RE")
        self.assertEqual(summary["attributes"]["Tag_RISCV_arch"], "rv64i2p1_m2p0")

    def test_padded_decimal_commit_destination_is_normalized(self):
        self.assertEqual(
            ANALYZER.commit_destination_register(
                {"commit_fields": [{"field": "dst", "value": "05"}]}
            ),
            "x5",
        )

    def test_log_values_drop_trailing_prose_punctuation(self):
        parsed = self.parse(
            "a0 different at pc = 0x80000000, right = 0x1), wrong = 0x2).\n"
        )
        self.assertEqual(
            [(item["label"], item["value"]) for item in parsed["events"][0]["side_values"]],
            [("right", "0x1"), ("wrong", "0x2")],
        )

    def test_vector_segment_store_has_no_false_register_definition(self):
        result = ANALYZER.instruction_def_use("vsuxseg3ei8.v", "v9,(s3),v25")
        self.assertEqual(result["definitions"], [])
        self.assertEqual(result["uses"], ["v9", "x19", "v25"])

    def test_store_side_line_keeps_owner_and_memory_fields(self):
        parsed = self.parse(
            "REF commits addr 0x8000996f, data 0x1122, mask 0x00ff, pc 0x80000042\n"
        )
        event = parsed["events"][0]
        self.assertEqual(event["store_side"], "ref")
        self.assertEqual(
            {item["field"] for item in event["store_fields"]},
            {"addr", "data", "mask"},
        )

    def test_commit_group_identifier_is_decimal(self):
        parsed = self.parse("commit group [15]: pc 0080000c8c cmtcnt 6\n")
        self.assertEqual(parsed["events"][0]["commit_group"]["group"], 15)

    def test_commit_sha_banner_is_not_a_commit_event(self):
        parsed = self.parse("Core 0's Commit SHA is: 87d03b2cc0, dirty: 0\n")
        self.assertEqual(parsed["events"], [])

    def test_explicit_pc_replaces_automatic_mismatch_anchor(self):
        parsed = self.parse(
            "a4 different at pc = 0x80000c8c, right = 0x77, wrong = 0\n"
            "[c=9][2] commit pc 0000000080000c94 inst ce074703 wen 1 dst 14 data 0 idx 3\n"
        )
        anchors = ANALYZER.build_anchors([0x80000C94], [parsed], [], 2, 2)
        self.assertEqual([anchor["pc"] for anchor in anchors], ["0x80000c94"])

    def test_memory_taint_matches_overlapping_byte(self):
        parsed = self.parse(
            "[NEMU] paddr write addr:0x8000996f, data:1122334455667788, len:8, mode:3\n"
        )
        observations, total = ANALYZER.memory_taint_observations(
            [parsed], "0x80009970"
        )
        self.assertEqual(total, 1)
        self.assertEqual(observations[0]["target_byte_offset"], 1)

    def test_control_taint_ignores_ordinary_commit_noise(self):
        parsed = self.parse(
            "[1] commit pc 0000000080000000 inst 00000013 wen 0 dst 0 data 0 idx 1\n"
            "commit group [15]: pc 0080000004 cmtcnt 2 <--\n"
            "a0 different at pc = 0x80000008, right = 1, wrong = 0\n"
        )
        observations, total = ANALYZER.control_taint_observations([parsed], "pc")
        self.assertEqual(total, 2)
        self.assertEqual(
            [observation["raw_line_number"] for observation in observations], [2, 3]
        )

    def test_run_metadata_is_kept_out_of_event_stream(self):
        parsed = self.parse(
            "Using seed = 20260421\n"
            "Core  0's Commit SHA is: 656c9e55cd, dirty: 1\n"
            "privilegeMode: 3\n"
            "Core-0 instrCnt = 315, cycleCnt = 8979, IPC = 0.035082\n"
        )
        self.assertEqual(parsed["events"], [])
        self.assertEqual(parsed["metadata"]["seeds"], [20260421])
        self.assertTrue(parsed["metadata"]["core_revisions"][0]["dirty"])
        self.assertEqual(parsed["metadata"]["privilege_modes"], [3])
        self.assertEqual(parsed["metadata"]["run_summaries"][0]["cycle_count"], 8979)

    def test_duplicate_run_metadata_is_deduplicated_by_value(self):
        revision = "Core  0's Commit SHA is: 656c9e55cd, dirty: 1\n"
        summary = "Core-0 instrCnt = 315, cycleCnt = 8979, IPC = 0.035082\n"
        parsed = self.parse(revision + summary + revision + summary)
        self.assertEqual(len(parsed["metadata"]["core_revisions"]), 1)
        self.assertEqual(len(parsed["metadata"]["run_summaries"]), 1)
        self.assertEqual(
            parsed["metadata"]["run_summaries"][0]["raw_line_number"], 2
        )

    def test_vector_and_csr_names_do_not_become_gprs(self):
        parsed = self.parse(
            "v8_high different at pc = 0x80000010, right = 1, wrong = 0\n"
            "mcause different at pc = 0x80000014, right = 5, wrong = 4\n"
        )
        self.assertEqual(parsed["events"][0]["register_candidates"], ["v8"])
        self.assertEqual(parsed["events"][1]["register_candidates"], [])
        self.assertEqual(ANALYZER.correlate_commit_writers([parsed]), [])

    def test_compressed_instruction_length_controls_fallthrough(self):
        parsed = ANALYZER.parse_disassembly(
            """\
Disassembly of section .text:

0000000080000000 <start>:
    80000000:\t4505                \tli\ta0,1
    80000002:\t00150513            \taddi\ta0,a0,1
""",
            "fixture",
        )
        instructions = parsed["instructions"]
        self.assertEqual(instructions[0]["length"], 2)
        flow = ANALYZER.control_flow_candidates(
            instructions, 0, ANALYZER.index_by_pc(instructions)
        )
        self.assertEqual(
            flow["successor_candidates"][0]["instruction"]["pc"], "0x80000002"
        )

    def test_slice_does_not_make_anchor_its_own_producer(self):
        parsed = ANALYZER.parse_disassembly(
            """\
Disassembly of section .text:

0000000080000000 <start>:
    80000000:\t00000517            \tauipc\ta0,0x0
    80000004:\t00054503            \tlbu\ta0,0(a0)
""",
            "fixture",
        )
        instructions = parsed["instructions"]
        result = ANALYZER.backward_register_slice(instructions, 1, ["x10"], 4)
        self.assertTrue(result["edges"])
        self.assertNotEqual(result["edges"][0]["producer_candidate"]["pc"], "0x80000004")


if __name__ == "__main__":
    unittest.main()
