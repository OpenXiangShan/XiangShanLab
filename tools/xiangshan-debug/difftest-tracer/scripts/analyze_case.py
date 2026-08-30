#!/usr/bin/env python3
"""Create a deterministic, conservative first-pass XiangShan difftest report.

The helper deliberately performs only static analysis.  It never executes a
supplied workload and it never modifies an input artifact.  Its control-flow
and data-flow results are candidate sets which must be checked against a
same-run commit trace or waveform before they are treated as dynamic facts.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import sys
from collections import deque
from pathlib import Path
from typing import Any, Callable, Iterable, Optional, Sequence


SCHEMA_VERSION = 1
TOOL_TIMEOUT_SECONDS = 120
MAX_CONTEXT_LINES = 10_000
MAX_SLICE_DEPTH = 64
MAX_LOG_CONTEXTS_PER_ANCHOR = 6
MAX_RENDERED_EVENTS = 120
MAX_NAMED_TAINT_MATCHES = 20


class AnalysisError(Exception):
    """An expected, user-actionable analysis error."""


def parse_integer(value: str, option: str) -> int:
    text = value.strip()
    try:
        if text.lower().startswith(("0x", "+0x", "-0x")):
            return int(text, 16)
        return int(text, 0)
    except ValueError as exc:
        raise argparse.ArgumentTypeError(
            f"{option} expects an integer such as 0x80000000; got {value!r}"
        ) from exc


def nonnegative_count(value: str, option: str, maximum: int) -> int:
    result = parse_integer(value, option)
    if result < 0 or result > maximum:
        raise argparse.ArgumentTypeError(
            f"{option} must be between 0 and {maximum}; got {result}"
        )
    return result


def hex_address(value: int) -> str:
    return f"0x{value:x}"


def unique_in_order(values: Iterable[Any]) -> list[Any]:
    result: list[Any] = []
    seen: set[Any] = set()
    for value in values:
        if value not in seen:
            seen.add(value)
            result.append(value)
    return result


def absolute_input_path(raw_path: str, role: str) -> Path:
    candidate = Path(raw_path).expanduser()
    try:
        path = candidate.resolve(strict=True)
    except FileNotFoundError as exc:
        raise AnalysisError(f"{role} input does not exist: {candidate.absolute()}") from exc
    except OSError as exc:
        raise AnalysisError(f"cannot resolve {role} input {candidate}: {exc}") from exc
    try:
        mode = path.stat().st_mode
    except OSError as exc:
        raise AnalysisError(f"cannot stat {role} input {path}: {exc}") from exc
    if not stat.S_ISREG(mode):
        raise AnalysisError(f"{role} input must be a regular file: {path}")
    if not os.access(path, os.R_OK):
        raise AnalysisError(f"{role} input is not readable: {path}")
    return path


def classify_magic(sample: bytes) -> str:
    if sample.startswith(b"\x7fELF"):
        return "ELF"
    if sample.startswith(b"PK\x03\x04"):
        return "ZIP"
    if sample.startswith(b"\x1f\x8b"):
        return "gzip"
    if sample.startswith(b"MZ"):
        return "PE"
    if not sample:
        return "empty"
    printable = sum(byte in b"\t\n\r" or 0x20 <= byte <= 0x7E for byte in sample)
    return "text-like" if printable / len(sample) >= 0.85 else "binary"


def artifact_identity(path: Path, role: str) -> dict[str, Any]:
    digest = hashlib.sha256()
    sample = bytearray()
    size = 0
    try:
        with path.open("rb") as stream:
            while True:
                chunk = stream.read(1024 * 1024)
                if not chunk:
                    break
                if len(sample) < 4096:
                    sample.extend(chunk[: 4096 - len(sample)])
                digest.update(chunk)
                size += len(chunk)
    except OSError as exc:
        raise AnalysisError(f"cannot read {role} input {path}: {exc}") from exc
    magic = bytes(sample[:16])
    return {
        "role": role,
        "path": str(path),
        "size": size,
        "sha256": digest.hexdigest(),
        "magic": {
            "hex": magic.hex(),
            "ascii": "".join(chr(byte) if 0x20 <= byte <= 0x7E else "." for byte in magic),
            "kind": classify_magic(bytes(sample)),
        },
    }


def read_text(path: Path, role: str) -> str:
    try:
        return path.read_bytes().decode("utf-8", errors="replace")
    except OSError as exc:
        raise AnalysisError(f"cannot read {role} input {path}: {exc}") from exc


def tool_paths(environment_name: str, names: Sequence[str]) -> list[str]:
    candidates: list[str] = []
    configured = os.environ.get(environment_name)
    if configured:
        configured_path = shutil.which(configured) if os.path.sep not in configured else configured
        if configured_path and os.path.isfile(configured_path) and os.access(configured_path, os.X_OK):
            candidates.append(os.path.abspath(configured_path))
    for name in names:
        found = shutil.which(name)
        if found:
            candidates.append(os.path.abspath(found))
    return unique_in_order(candidates)


def command_failure_summary(stderr: str, stdout: str) -> str:
    useful = stderr.strip() or stdout.strip() or "no diagnostic output"
    useful = " ".join(useful.split())
    return useful[:500] + ("..." if len(useful) > 500 else "")


def run_inspection_tool(
    purpose: str,
    tools: Sequence[str],
    argument_variants: Sequence[Callable[[str], list[str]]],
) -> dict[str, Any]:
    if not tools:
        raise AnalysisError(
            f"no compatible {purpose} tool was found in PATH; install RISC-V binutils "
            f"or supply a matching --disasm file"
        )
    failures: list[str] = []
    for executable in tools:
        for make_argv in argument_variants:
            argv = [executable, *make_argv(executable)]
            try:
                completed = subprocess.run(
                    argv,
                    stdin=subprocess.DEVNULL,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    text=True,
                    encoding="utf-8",
                    errors="replace",
                    timeout=TOOL_TIMEOUT_SECONDS,
                    check=False,
                    shell=False,
                )
            except (OSError, subprocess.SubprocessError) as exc:
                failures.append(f"{executable}: {exc}")
                continue
            if completed.returncode == 0 and completed.stdout.strip():
                return {
                    "tool": executable,
                    "argv": argv,
                    "returncode": completed.returncode,
                    "stdout": completed.stdout,
                    "stderr": completed.stderr,
                }
            failures.append(
                f"{' '.join(argv[:3])}: exit {completed.returncode}: "
                f"{command_failure_summary(completed.stderr, completed.stdout)}"
            )
    details = "; ".join(failures[:6])
    raise AnalysisError(f"all {purpose} commands failed ({details})")


READELF_NAMES = (
    "riscv64-unknown-elf-readelf",
    "riscv64-linux-gnu-readelf",
    "llvm-readelf",
    "readelf",
)

OBJDUMP_NAMES = (
    "riscv64-unknown-elf-objdump",
    "riscv64-linux-gnu-objdump",
    "llvm-objdump",
    "objdump",
)


def inspect_elf(path: Path) -> dict[str, Any]:
    try:
        with path.open("rb") as stream:
            magic = stream.read(4)
    except OSError as exc:
        raise AnalysisError(f"cannot inspect ELF input {path}: {exc}") from exc
    if magic != b"\x7fELF":
        raise AnalysisError(f"--elf does not have ELF magic (7f454c46): {path}")
    tools = tool_paths("READELF", READELF_NAMES)
    result = run_inspection_tool(
        "readelf inspection",
        tools,
        (
            lambda _tool: ["--wide", "-h", "-l", "-S", "-s", "-A", str(path)],
            lambda _tool: ["-h", "-l", "-S", "-s", str(path)],
        ),
    )
    summary = parse_readelf(result["stdout"])
    return {
        "tool": result["tool"],
        "argv": result["argv"],
        "stderr": result["stderr"],
        "summary": summary,
        "text": result["stdout"],
    }


def parse_readelf(text: str) -> dict[str, Any]:
    header: dict[str, str] = {}
    attributes: dict[str, str] = {}
    sections: list[dict[str, Any]] = []
    load_segments: list[dict[str, Any]] = []
    in_header = False
    section_pattern = re.compile(
        r"^\s*\[\s*(\d+)\]\s+(\S+)\s+(\S+)\s+"
        r"([0-9a-fA-F]+)\s+([0-9a-fA-F]+)\s+([0-9a-fA-F]+)(?:\s+|$)"
    )
    program_header_pattern = re.compile(
        r"^\s*(LOAD)\s+"
        r"(0x[0-9a-fA-F]+)\s+(0x[0-9a-fA-F]+)\s+(0x[0-9a-fA-F]+)\s+"
        r"(0x[0-9a-fA-F]+)\s+(0x[0-9a-fA-F]+)\s+"
        r"([RWE](?:\s*[RWE])*)\s+(0x[0-9a-fA-F]+)\s*$"
    )
    split_load_first_pattern = re.compile(
        r"^\s*(LOAD)\s+(0x[0-9a-fA-F]+)\s+(0x[0-9a-fA-F]+)\s+(0x[0-9a-fA-F]+)\s*$"
    )
    split_load_second_pattern = re.compile(
        r"^\s*(0x[0-9a-fA-F]+)\s+(0x[0-9a-fA-F]+)\s+"
        r"([RWE](?:\s*[RWE])*)\s+(0x[0-9a-fA-F]+)\s*$"
    )

    def append_load_segment(values: Sequence[str]) -> None:
        kind, offset, vaddr, paddr, filesz, memsz, flags, align = values
        load_segments.append(
            {
                "type": kind,
                "offset": hex_address(int(offset, 16)),
                "virt_addr": hex_address(int(vaddr, 16)),
                "phys_addr": hex_address(int(paddr, 16)),
                "file_size": hex_address(int(filesz, 16)),
                "memory_size": hex_address(int(memsz, 16)),
                "flags": re.sub(r"\s+", "", flags),
                "align": hex_address(int(align, 16)),
            }
        )

    pending_split_load: Optional[tuple[str, ...]] = None
    for line in text.splitlines():
        if pending_split_load is not None:
            split_second = split_load_second_pattern.match(line)
            if split_second:
                append_load_segment((*pending_split_load, *split_second.groups()))
                pending_split_load = None
                continue
            pending_split_load = None
        if line.strip() == "ELF Header:":
            in_header = True
            continue
        if in_header:
            if not line.strip():
                in_header = False
            else:
                match = re.match(r"^\s*([^:]+):\s*(.*)$", line)
                if match:
                    key = re.sub(r"[^a-z0-9]+", "_", match.group(1).strip().lower()).strip("_")
                    header[key] = match.group(2).strip()
        attribute_match = re.match(r"^\s*(Tag_RISCV_[A-Za-z0-9_]+):\s*(.*)$", line)
        if attribute_match:
            attributes[attribute_match.group(1)] = attribute_match.group(2).strip().strip('"')
        section_match = section_pattern.match(line)
        if section_match:
            index, name, section_type, address, offset, size = section_match.groups()
            sections.append(
                {
                    "index": int(index),
                    "name": name,
                    "type": section_type,
                    "address": hex_address(int(address, 16)),
                    "offset": hex_address(int(offset, 16)),
                    "size": hex_address(int(size, 16)),
                }
            )
        program_match = program_header_pattern.match(line)
        if program_match:
            append_load_segment(program_match.groups())
        else:
            split_first = split_load_first_pattern.match(line)
            if split_first:
                pending_split_load = split_first.groups()
    return {
        "header": header,
        "attributes": attributes,
        "sections": sections,
        "load_segments": load_segments,
    }


def generate_disassembly(elf: Optional[Path], binary: Optional[Path], base: Optional[int]) -> dict[str, Any]:
    tools = tool_paths("OBJDUMP", OBJDUMP_NAMES)
    if elf is not None:
        variants = (
            lambda _tool: ["-d", "-w", str(elf)],
            lambda _tool: ["-d", str(elf)],
        )
        source = {"kind": "generated-from-elf", "path": str(elf)}
    elif binary is not None:
        if base is None:
            raise AnalysisError("a raw --bin requires --base, for example --base 0x80000000")
        variants = (
            lambda _tool: [
                "-D",
                "-b",
                "binary",
                "-m",
                "riscv:rv64",
                f"--adjust-vma={hex_address(base)}",
                str(binary),
            ],
            lambda _tool: [
                "-D",
                "-b",
                "binary",
                "-m",
                "riscv",
                f"--adjust-vma={hex_address(base)}",
                str(binary),
            ],
        )
        source = {
            "kind": "generated-from-raw-bin",
            "path": str(binary),
            "base": hex_address(base),
            "assumed_architecture": "RISC-V; tool-selected/default XLEN (verify against the run)",
        }
    else:
        raise AnalysisError("internal error: no input from which to generate disassembly")
    result = run_inspection_tool("objdump disassembly", tools, variants)
    return {
        "tool": result["tool"],
        "argv": result["argv"],
        "stderr": result["stderr"],
        "source": source,
        "text": result["stdout"],
    }


GPR_ALIASES = {
    "zero": "x0",
    "ra": "x1",
    "sp": "x2",
    "gp": "x3",
    "tp": "x4",
    "t0": "x5",
    "t1": "x6",
    "t2": "x7",
    "s0": "x8",
    "fp": "x8",
    "s1": "x9",
    "a0": "x10",
    "a1": "x11",
    "a2": "x12",
    "a3": "x13",
    "a4": "x14",
    "a5": "x15",
    "a6": "x16",
    "a7": "x17",
    "s2": "x18",
    "s3": "x19",
    "s4": "x20",
    "s5": "x21",
    "s6": "x22",
    "s7": "x23",
    "s8": "x24",
    "s9": "x25",
    "s10": "x26",
    "s11": "x27",
    "t3": "x28",
    "t4": "x29",
    "t5": "x30",
    "t6": "x31",
}

FPR_ALIASES: dict[str, str] = {}
for _number, _alias in enumerate(("ft0", "ft1", "ft2", "ft3", "ft4", "ft5", "ft6", "ft7")):
    FPR_ALIASES[_alias] = f"f{_number}"
FPR_ALIASES.update({"fs0": "f8", "fs1": "f9"})
for _offset in range(8):
    FPR_ALIASES[f"fa{_offset}"] = f"f{10 + _offset}"
for _offset in range(10):
    FPR_ALIASES[f"fs{2 + _offset}"] = f"f{18 + _offset}"
for _offset in range(4):
    FPR_ALIASES[f"ft{8 + _offset}"] = f"f{28 + _offset}"

REGISTER_NAMES = set(GPR_ALIASES) | set(FPR_ALIASES)
REGISTER_NAMES.update(f"x{number}" for number in range(32))
REGISTER_NAMES.update(f"f{number}" for number in range(32))
REGISTER_NAMES.update(f"v{number}" for number in range(32))
REGISTER_PATTERN = re.compile(
    r"(?<![A-Za-z0-9_.])(" + "|".join(sorted(map(re.escape, REGISTER_NAMES), key=len, reverse=True)) + r")(?![A-Za-z0-9_.])",
    re.IGNORECASE,
)
VECTOR_PART_PATTERN = re.compile(
    r"(?<![A-Za-z0-9_.])(v(?:[0-9]|[12][0-9]|3[01]))_(?:low|high)(?![A-Za-z0-9_.])",
    re.IGNORECASE,
)


def normalize_register_name(name: str) -> Optional[str]:
    lowered = name.strip().lower()
    if lowered in GPR_ALIASES:
        return GPR_ALIASES[lowered]
    if lowered in FPR_ALIASES:
        return FPR_ALIASES[lowered]
    if re.fullmatch(r"x(?:[0-9]|[12][0-9]|3[01])", lowered):
        return lowered
    if re.fullmatch(r"f(?:[0-9]|[12][0-9]|3[01])", lowered):
        return lowered
    if re.fullmatch(r"v(?:[0-9]|[12][0-9]|3[01])", lowered):
        return lowered
    return None


def extract_registers(text: str) -> list[str]:
    without_symbols = re.sub(r"<[^>]*>", "", text)
    values = []
    for match in REGISTER_PATTERN.finditer(without_symbols):
        normalized = normalize_register_name(match.group(1))
        if normalized is not None and normalized != "x0":
            values.append(normalized)
    return unique_in_order(values)


def extract_register_mentions(text: str) -> list[dict[str, str]]:
    without_symbols = re.sub(r"<[^>]*>", "", text)
    mentions: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()
    for match in REGISTER_PATTERN.finditer(without_symbols):
        spelling = match.group(1)
        normalized = normalize_register_name(spelling)
        if normalized is None:
            continue
        key = (spelling.lower(), normalized)
        if key not in seen:
            seen.add(key)
            mentions.append({"spelling": spelling, "normalized": normalized})
    for match in VECTOR_PART_PATTERN.finditer(without_symbols):
        spelling = match.group(0)
        normalized = match.group(1).lower()
        key = (spelling.lower(), normalized)
        if key not in seen:
            seen.add(key)
            mentions.append({"spelling": spelling, "normalized": normalized})
    return mentions


def split_operands(operands: str) -> list[str]:
    # Parenthesized RISC-V address operands contain no commas in normal objdump
    # output.  Keep a small balanced splitter for custom/vector forms anyway.
    result: list[str] = []
    current: list[str] = []
    depth = 0
    for char in operands:
        if char in "([":
            depth += 1
        elif char in ")]" and depth:
            depth -= 1
        if char == "," and depth == 0:
            result.append("".join(current).strip())
            current = []
        else:
            current.append(char)
    if current or operands.strip():
        result.append("".join(current).strip())
    return result


def operand_register(operand: str) -> Optional[str]:
    match = REGISTER_PATTERN.search(re.sub(r"<[^>]*>", "", operand))
    return normalize_register_name(match.group(1)) if match else None


CONDITIONAL_BRANCHES = {
    "beq",
    "bne",
    "blt",
    "bge",
    "bltu",
    "bgeu",
    "bgt",
    "ble",
    "bgtu",
    "bleu",
    "beqz",
    "bnez",
    "blez",
    "bgez",
    "bltz",
    "bgtz",
    "c.beqz",
    "c.bnez",
}

DIRECT_JUMPS = {"j", "c.j", "jal", "c.jal", "call", "tail"}
INDIRECT_JUMPS = {"jalr", "c.jalr", "jr", "c.jr", "ret"}
RETURNS = {"ret", "mret", "sret", "uret", "dret"}
SYSTEM_CONTROL = {"ecall", "ebreak", "c.ebreak", "mret", "sret", "uret", "dret", "wfi"}


def vector_load(mnemonic: str) -> bool:
    # Covers unit/strided/indexed, fault-only-first, whole-register, and
    # segment forms (for example vl1re64.v and vlseg4e8.v).
    return mnemonic.startswith("vl") and mnemonic.endswith(".v")


def vector_store(mnemonic: str) -> bool:
    # Covers unit/strided/indexed, whole-register, and segment stores.
    return mnemonic.startswith("vs") and mnemonic.endswith(".v")


def instruction_def_use(mnemonic: str, operands: str) -> dict[str, Any]:
    mnem = mnemonic.lower()
    ops = split_operands(operands)
    op_registers = [extract_registers(op) for op in ops]
    all_registers = unique_in_order(reg for registers in op_registers for reg in registers)
    definitions: list[str] = []
    uses: list[str] = []
    effects: list[str] = []
    notes: list[str] = []
    confidence = "mnemonic heuristic"

    scalar_stores = {"sb", "sh", "sw", "sd", "sq", "fsw", "fsd", "fsq", "c.sw", "c.sd", "c.swsp", "c.sdsp", "c.fsw", "c.fsd"}
    scalar_loads = {
        "lb", "lbu", "lh", "lhu", "lw", "lwu", "ld", "lq", "flh", "flw", "fld", "flq",
        "c.lw", "c.ld", "c.lwsp", "c.ldsp", "c.flw", "c.fld",
    }

    if mnem in CONDITIONAL_BRANCHES:
        uses = all_registers
        effects.append("control predicate/target")
    elif mnem in {"j", "c.j", "tail"}:
        effects.append("control target")
    elif mnem in {"call", "c.jal"}:
        definitions = ["x1"]
        effects.extend(("control target", "link register"))
    elif mnem == "jal":
        explicit_destination = operand_register(ops[0]) if ops else None
        if explicit_destination is not None:
            definitions = [] if explicit_destination == "x0" else [explicit_destination]
        else:
            definitions = ["x1"]
        effects.extend(("control target", "link register"))
    elif mnem in {"jr", "c.jr"}:
        uses = all_registers
        effects.append("indirect control target")
    elif mnem in {"ret"}:
        uses = ["x1"]
        effects.append("indirect control target")
    elif mnem in {"jalr", "c.jalr"}:
        explicit_destination = operand_register(ops[0]) if ops else None
        if len(ops) >= 2 and explicit_destination is not None:
            definitions = [] if explicit_destination == "x0" else [explicit_destination]
            uses = unique_in_order(reg for registers in op_registers[1:] for reg in registers)
        else:
            definitions = ["x1"]
            uses = all_registers
        effects.extend(("indirect control target", "link register"))
    elif mnem in SYSTEM_CONTROL:
        effects.append("implicit control/privilege state")
        notes.append("implicit CSR, trap, interrupt, or environment dependencies are not closed")
    elif mnem in scalar_stores or vector_store(mnem):
        uses = all_registers
        effects.append("memory write: address, data, mask, ordering, and visibility unresolved")
    elif mnem in scalar_loads or vector_load(mnem):
        if op_registers and op_registers[0]:
            definitions = op_registers[0][:1]
        uses = unique_in_order(reg for registers in op_registers[1:] for reg in registers)
        effects.append("memory read: address and dynamic memory version unresolved")
    elif mnem.startswith("lr."):
        if op_registers and op_registers[0]:
            definitions = op_registers[0][:1]
        uses = unique_in_order(reg for registers in op_registers[1:] for reg in registers)
        effects.append("reservation and memory read")
    elif mnem.startswith("sc.") or mnem.startswith("amo"):
        if op_registers and op_registers[0]:
            definitions = op_registers[0][:1]
        uses = unique_in_order(reg for registers in op_registers[1:] for reg in registers)
        effects.append("atomic memory read/write and ordering")
    elif mnem.startswith("csrr"):
        if op_registers and op_registers[0]:
            definitions = op_registers[0][:1]
        uses = unique_in_order(reg for registers in op_registers[1:] for reg in registers)
        effects.append("CSR read/modify/write state")
    elif mnem in {"csrw", "csrs", "csrc", "csrwi", "csrsi", "csrci"}:
        uses = all_registers
        effects.append("CSR write state")
    elif mnem.startswith("sfence") or mnem.startswith("hfence"):
        uses = all_registers
        effects.append("translation/order state")
    elif mnem.startswith("fence") or mnem.startswith("cbo."):
        uses = all_registers
        effects.append("memory/cache ordering state")
    elif ops and op_registers and op_registers[0]:
        definitions = op_registers[0][:1]
        uses = unique_in_order(reg for registers in op_registers[1:] for reg in registers)
        if mnem.startswith("v") and definitions:
            # Undisturbed masked/tail elements can retain the old destination.
            uses = unique_in_order([*uses, *definitions])
            notes.append("vector old-destination dependence is conservatively included")
        notes.append("unknown/custom and pseudo-instruction forms may need manual def/use correction")
    else:
        uses = all_registers
        if all_registers:
            notes.append("register roles are unresolved for this mnemonic")

    definitions = [reg for reg in unique_in_order(definitions) if reg != "x0"]
    uses = [reg for reg in unique_in_order(uses) if reg != "x0"]
    return {
        "definitions": definitions,
        "uses": uses,
        "implicit_or_nonregister_effects": effects,
        "confidence": confidence,
        "notes": notes,
    }


def decode_objdump_encoding(display: str) -> Optional[str]:
    tokens = display.strip().split()
    if not tokens:
        return None
    if all(re.fullmatch(r"[0-9a-fA-F]{2}", token) for token in tokens):
        return "".join(token.lower() for token in tokens)
    compact = "".join(tokens)
    if not re.fullmatch(r"[0-9a-fA-F]+", compact) or len(compact) not in {4, 8, 12, 16}:
        return None
    byte_tokens = [compact[index : index + 2] for index in range(0, len(compact), 2)]
    return "".join(reversed(byte_tokens)).lower()


def decode_log_instruction_bits(raw: str, expected_length: Optional[int] = None) -> Optional[str]:
    """Decode the integer-style instruction spelling used by commit logs.

    GNU objdump and XiangShan commit logs usually print a little-endian
    instruction as a most-significant-byte-first hexadecimal word.  NEMU
    sometimes omits leading zeroes, so normalize the width before reversing
    the bytes.  The result is deliberately marked as an assumed orientation
    in the report; a dynamic fetch trace remains authoritative.
    """
    value = raw.strip().lower().removeprefix("0x")
    if not re.fullmatch(r"[0-9a-f]+", value):
        return None
    if len(value) % 2:
        value = "0" + value
    if expected_length is not None:
        width = expected_length * 2
        if width <= 0:
            return None
        if len(value) > width:
            # Zero-extended NEMU words can be narrowed safely.  Preserve a
            # genuinely wider/non-zero encoding so an ELF/disassembly length
            # mismatch is visible instead of silently comparing only its low
            # half.
            prefix = value[:-width]
            if set(prefix) <= {"0"}:
                value = value[-width:]
        else:
            value = value.zfill(width)
    if len(value) not in {4, 8, 12, 16}:
        return None
    return "".join(
        value[index : index + 2]
        for index in range(len(value) - 2, -1, -2)
    )


def parse_instruction_line(line: str) -> Optional[tuple[int, str, str]]:
    address_match = re.match(r"^\s*([0-9a-fA-F]+):\s*(.*)$", line)
    if not address_match:
        return None
    address = int(address_match.group(1), 16)
    remainder = address_match.group(2)
    encoding = ""
    assembly = ""
    if "\t" in remainder:
        fields = [field.strip() for field in remainder.split("\t") if field.strip()]
        if len(fields) >= 2:
            encoding = fields[0]
            assembly = " ".join(fields[1:])
    if not assembly:
        spaced = re.match(
            r"^\s*((?:[0-9a-fA-F]{2}\s+){1,15}[0-9a-fA-F]{2}|[0-9a-fA-F]{4,16})\s{2,}(\S.*)$",
            remainder,
        )
        if spaced:
            encoding, assembly = spaced.groups()
    if not assembly or decode_objdump_encoding(encoding) is None:
        return None
    return address, encoding.strip(), assembly.strip()


def parse_disassembly(text: str, source: str) -> dict[str, Any]:
    section_pattern = re.compile(r"^Disassembly of section\s+(.+?):\s*$")
    function_pattern = re.compile(r"^\s*([0-9a-fA-F]+)\s+<([^>]+)>:\s*$")
    current_section: Optional[str] = None
    current_function: Optional[str] = None
    instructions: list[dict[str, Any]] = []
    functions: list[dict[str, Any]] = []
    sections: list[str] = []
    for line_number, line in enumerate(text.splitlines(), start=1):
        section_match = section_pattern.match(line)
        if section_match:
            current_section = section_match.group(1)
            current_function = None
            if current_section not in sections:
                sections.append(current_section)
            continue
        function_match = function_pattern.match(line)
        if function_match:
            current_function = function_match.group(2)
            functions.append(
                {
                    "address": hex_address(int(function_match.group(1), 16)),
                    "name": current_function,
                    "section": current_section,
                    "raw_line_number": line_number,
                }
            )
            continue
        parsed = parse_instruction_line(line)
        if parsed is None:
            continue
        address, encoding_display, assembly = parsed
        fields = assembly.split(None, 1)
        mnemonic = fields[0].lower()
        operands = fields[1].strip() if len(fields) > 1 else ""
        byte_string = decode_objdump_encoding(encoding_display)
        assert byte_string is not None
        instructions.append(
            {
                "address": address,
                "pc": hex_address(address),
                "encoding_display": encoding_display,
                "bytes_file_order": byte_string,
                "length": len(byte_string) // 2,
                "mnemonic": mnemonic,
                "operands": operands,
                "assembly": assembly,
                "section": current_section,
                "function": current_function,
                "raw_line_number": line_number,
                "raw_line": line,
                "def_use": instruction_def_use(mnemonic, operands),
            }
        )
    if text.strip() and not instructions:
        raise AnalysisError(
            f"no RISC-V objdump instruction lines were recognized in disassembly source {source}; "
            "supply standard `objdump -d` text"
        )
    return {
        "source": source,
        "line_count": len(text.splitlines()),
        "sections": sections,
        "functions": functions,
        "instructions": instructions,
    }


PC_PATTERN = re.compile(
    r"(?<![A-Za-z0-9_])pc\s*(?:(?:=|:)\s*|is\s+)?(?:0x)?([0-9a-fA-F]{4,32})\b",
    re.IGNORECASE,
)
CYCLE_PATTERN = re.compile(
    r"\b(?:cycle|cyclecnt)\s*(?:=|:)?\s*((?:0x[0-9a-fA-F]+)|(?:\d+))\b",
    re.IGNORECASE,
)
BRACKET_CYCLE_PATTERN = re.compile(r"\[\s*c\s*=\s*((?:0x)?[0-9a-fA-F]+)\s*\]", re.IGNORECASE)
BRACKET_SEQUENCE_PATTERN = re.compile(
    r"\[\s*c\s*=\s*(?:0x)?[0-9a-fA-F]+\s*\]\s*\[\s*(?:lane\s*=\s*)?(\d+)\s*\]",
    re.IGNORECASE,
)
HART_PATTERN = re.compile(r"\b(?:hart|core)\s*(?:=|:)?\s*(\d+)\b", re.IGNORECASE)
INSTRUCTION_BITS_PATTERN = re.compile(
    r"\b(?:instr(?:uction)?|inst)\s*(?:=|:)?\s*(?:0x)?([0-9a-fA-F]{4,16})\b",
    re.IGNORECASE,
)
MISMATCH_PATTERN = re.compile(
    r"\b(?:mismatch(?:ed)?|different|differs|diverg(?:ed|ence)|not\s+equal|inconsistent)\b"
    r"|\bdifftest.{0,24}(?:fail|error)\b"
    r"|\bcomparison.{0,24}(?:fail|error)\b",
    re.IGNORECASE,
)
SIDE_VALUE_PATTERN = re.compile(
    r"\b(right|wrong|dut|ref(?:erence)?|golden|expected|actual)\b\s*"
    r"(?:value\s*)?(?:(?:=|:)\s*|\s+(?=[-+]?(?:0x[0-9a-fA-F]+|[0-9]+)\b))"
    r"([^\s,;]+)",
    re.IGNORECASE,
)
STORE_FIELD_PATTERN = re.compile(
    r"\b(paddr|vaddr|addr(?:ess)?|data|mask|len|size|type|mode)\s*"
    r"(?:(?:=|:)\s*|\s+)((?:0x)?[0-9a-fA-F]+)",
    re.IGNORECASE,
)
COMMIT_FIELD_PATTERN = re.compile(
    r"\b(wen|dst|data|idx|robidx|rob)\b\s*(?:=|:)?\s*([^\s,;\]]+)",
    re.IGNORECASE,
)
COMMIT_GROUP_PATTERN = re.compile(
    r"\bcommit\s+group\s*\[\s*(\d+)\s*\]\s*:?\s*"
    r"pc\s+(?:0x)?([0-9a-fA-F]{4,32})\s+cmtcnt\s+(\d+)",
    re.IGNORECASE,
)
EXCEPTION_PATTERN = re.compile(
    r"\bexception\s+pc\s+(?:0x)?([0-9a-fA-F]{4,32})\s+"
    r"inst\s+(?:0x)?([0-9a-fA-F]{4,16})\s+"
    r"cause\s+(?:0x)?([0-9a-fA-F]+)",
    re.IGNORECASE,
)
MEMORY_EVENT_PATTERN = re.compile(
    r"\bpaddr\s+(read|write)\b",
    re.IGNORECASE,
)
STORE_SIDE_PATTERN = re.compile(
    r"\b(ref(?:erence)?|dut|golden)\s+commits?\b",
    re.IGNORECASE,
)


def parsed_number(text: str) -> Optional[int]:
    try:
        lowered = text.lower()
        if lowered.startswith("0x"):
            return int(lowered, 16)
        return int(lowered, 10)
    except ValueError:
        return None


def clean_log_value(text: str) -> str:
    """Remove punctuation attached to a scalar value in human log prose."""
    return text.strip().rstrip(",;)]}>.")


def parsed_machine_value(text: str) -> Optional[int]:
    stripped = text.strip().lower()
    try:
        if stripped.startswith("0x") or re.search(r"[a-f]", stripped):
            return int(stripped.removeprefix("0x"), 16)
        # Fixed-width register dumps are conventionally hexadecimal even when
        # the value happens to contain only decimal digits.
        if len(stripped) >= 8 and stripped.startswith("0"):
            return int(stripped, 16)
        return int(stripped, 10)
    except ValueError:
        return None


def parse_log_metadata(lines: list[str]) -> dict[str, Any]:
    simulator_builds: list[str] = []
    seeds: list[int] = []
    images: list[str] = []
    reference_models: list[str] = []
    core_revisions: list[dict[str, Any]] = []
    privilege_modes: list[int] = []
    run_summaries: list[dict[str, Any]] = []
    for line_number, raw_line in enumerate(lines, start=1):
        line = re.sub(r"\x1b\[[0-9;]*m", "", raw_line)
        build_match = re.search(r"\bemu\s+compiled\s+at\s+(.+)$", line, re.IGNORECASE)
        if build_match:
            simulator_builds.append(build_match.group(1).strip())
        seed_match = re.search(r"\b(?:using\s+)?seed\s*=\s*([0-9]+)\b", line, re.IGNORECASE)
        if seed_match:
            seeds.append(int(seed_match.group(1)))
        image_match = re.match(r"^\s*The image is\s+(.+?)\s*$", line, re.IGNORECASE)
        if image_match:
            images.append(image_match.group(1))
        reference_match = re.match(
            r"^\s*The reference model is\s+(.+?)\s*$", line, re.IGNORECASE
        )
        if reference_match:
            reference_models.append(reference_match.group(1))
        revision_match = re.search(
            r"\bCore\s+(\d+)'s\s+Commit\s+SHA\s+is:\s*([0-9a-fA-F]+)\s*,\s*dirty:\s*(\d+)",
            line,
            re.IGNORECASE,
        )
        if revision_match:
            core_revisions.append(
                {
                    "core": int(revision_match.group(1)),
                    "commit_sha": revision_match.group(2).lower(),
                    "dirty": int(revision_match.group(3)) != 0,
                    "raw_line_number": line_number,
                }
            )
        privilege_match = re.search(r"\bprivilegeMode\s*:\s*(\d+)\b", line)
        if privilege_match:
            privilege_modes.append(int(privilege_match.group(1)))
        summary_match = re.search(
            r"\bCore-(\d+)\s+instrCnt\s*=\s*([0-9,]+)\s*,\s*"
            r"cycleCnt\s*=\s*([0-9,]+)\s*,\s*IPC\s*=\s*([0-9.]+)",
            line,
            re.IGNORECASE,
        )
        if summary_match:
            run_summaries.append(
                {
                    "core": int(summary_match.group(1)),
                    "instruction_count": int(summary_match.group(2).replace(",", "")),
                    "cycle_count": int(summary_match.group(3).replace(",", "")),
                    "ipc": summary_match.group(4),
                    "raw_line_number": line_number,
                }
            )
    def unique_records(records: list[dict[str, Any]], keys: Sequence[str]) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = []
        seen: set[tuple[Any, ...]] = set()
        for record in records:
            identity = tuple(record[key] for key in keys)
            if identity not in seen:
                seen.add(identity)
                result.append(record)
        return result

    return {
        "simulator_builds": unique_in_order(simulator_builds),
        "seeds": unique_in_order(seeds),
        "images": unique_in_order(images),
        "reference_models": unique_in_order(reference_models),
        "core_revisions": unique_records(
            core_revisions, ("core", "commit_sha", "dirty")
        ),
        "privilege_modes": unique_in_order(privilege_modes),
        "run_summaries": unique_records(
            run_summaries, ("core", "instruction_count", "cycle_count", "ipc")
        ),
    }


def parse_log(path: Path) -> tuple[dict[str, Any], list[str]]:
    text = read_text(path, "log")
    lines = text.splitlines()
    events: list[dict[str, Any]] = []
    for line_number, line in enumerate(lines, start=1):
        event_types: list[str] = []
        lower = line.lower()
        pc_values = unique_in_order(int(match.group(1), 16) for match in PC_PATTERN.finditer(line))
        cycles = [parsed_number(match.group(1)) for match in CYCLE_PATTERN.finditer(line)]
        cycles.extend(parsed_number(match.group(1)) for match in BRACKET_CYCLE_PATTERN.finditer(line))
        cycles = [value for value in cycles if value is not None]
        harts = [int(match.group(1)) for match in HART_PATTERN.finditer(line)]
        commit_sequences = [
            int(match.group(1)) for match in BRACKET_SEQUENCE_PATTERN.finditer(line)
        ]
        instruction_bits = unique_in_order(match.group(1).lower() for match in INSTRUCTION_BITS_PATTERN.finditer(line))
        side_values = [
            {"label": match.group(1).lower(), "value": clean_log_value(match.group(2))}
            for match in SIDE_VALUE_PATTERN.finditer(line)
        ]
        commit_group_match = COMMIT_GROUP_PATTERN.search(line)
        exception_match = EXCEPTION_PATTERN.search(line)
        memory_event_match = MEMORY_EVENT_PATTERN.search(line)
        store_side_match = STORE_SIDE_PATTERN.search(line)
        memory_fields = (
            [
                {"field": match.group(1).lower(), "value": clean_log_value(match.group(2))}
                for match in STORE_FIELD_PATTERN.finditer(line)
            ]
            if (
                "store" in lower
                or memory_event_match is not None
                or re.search(r"\b(?:paddr|vaddr|mask)\b", lower)
            )
            else []
        )
        commit_fields = (
            [
                {"field": match.group(1).lower(), "value": clean_log_value(match.group(2))}
                for match in COMMIT_FIELD_PATTERN.finditer(line)
            ]
            if "commit" in lower
            or "retire" in lower
            or re.search(r"\b(?:wen|dst|idx|robidx|rob)\b", lower)
            else []
        )
        mismatch = bool(MISMATCH_PATTERN.search(line))
        # Signal/performance names such as ``ras_top_mismatch`` are not
        # architectural comparison records.  Keep a mismatch only when the
        # line also has a comparison-shaped context (PC/side values, a commit,
        # memory/CSR/exception marker, or an explicit difftest failure).
        comparison_context = bool(
            pc_values
            or side_values
            or cycles
            or re.search(
                r"\b(?:difftest|store|commit|retire|exception|csr|register|gpr|memory|"
                r"dut|ref(?:erence)?|right|wrong|comparison|fail(?:ure)?|error)\b",
                lower,
            )
        )
        summary_only = bool(re.search(r"different\s+from\s+cycle\s*cnt", lower))
        if mismatch and (not comparison_context or summary_only):
            mismatch = False
        store_commit = bool(
            re.search(r"\bstore\s+commits?\b|\bstore\s+commit\b", lower)
            or store_side_match
        )
        commit = bool(re.search(r"\b(?:commit|retire(?:d)?)\s+pc\b", lower))
        if pc_values:
            event_types.append("pc")
        if mismatch:
            event_types.append("mismatch")
        if side_values:
            event_types.append("side-values")
        if store_commit:
            event_types.append("store-commit")
        elif commit:
            event_types.append("commit")
        if commit_group_match:
            event_types.append("commit-group")
        if exception_match:
            event_types.append("exception")
        if memory_event_match:
            event_types.append(
                "memory-read" if memory_event_match.group(1).lower() == "read" else "memory-write"
            )
        if store_side_match:
            event_types.append("store-side")
        if not event_types:
            continue
        register_mentions = extract_register_mentions(line) if mismatch else []
        register_candidates = unique_in_order(
            mention["normalized"] for mention in register_mentions if mention["normalized"] != "x0"
        )
        events.append(
            {
                "raw_line_number": line_number,
                "raw_line": line,
                "types": unique_in_order(event_types),
                "pcs": [hex_address(value) for value in pc_values],
                "cycles": unique_in_order(cycles),
                "harts": unique_in_order(harts),
                "commit_sequences": unique_in_order(commit_sequences),
                "instruction_bits": instruction_bits,
                "side_values": side_values,
                "store_fields": memory_fields,
                "store_side": store_side_match.group(1).lower() if store_side_match else None,
                "commit_fields": commit_fields,
                "register_candidates": register_candidates,
                "register_mentions": register_mentions,
                "commit_group": (
                    {
                        "group": int(commit_group_match.group(1), 10),
                        "pc": hex_address(int(commit_group_match.group(2), 16)),
                        "commit_count": int(commit_group_match.group(3)),
                    }
                    if commit_group_match
                    else None
                ),
                "exception": (
                    {
                        "pc": hex_address(int(exception_match.group(1), 16)),
                        "instruction_bits": exception_match.group(2).lower(),
                        "cause": hex_address(int(exception_match.group(3), 16)),
                    }
                    if exception_match
                    else None
                ),
                "memory_event": (
                    {"access": memory_event_match.group(1).lower()}
                    if memory_event_match
                    else None
                ),
            }
        )
    return (
        {
            "path": str(path),
            "line_count": len(lines),
            "event_count": len(events),
            "metadata": parse_log_metadata(lines),
            "events": events,
        },
        lines,
    )


def commit_field(event: dict[str, Any], *names: str) -> Optional[str]:
    wanted = set(names)
    for field in event.get("commit_fields", []):
        if field["field"] in wanted:
            return field["value"]
    return None


def commit_write_enabled(event: dict[str, Any]) -> bool:
    raw = commit_field(event, "wen")
    if raw is None:
        return False
    lowered = raw.strip().lower()
    if lowered in {"true", "yes"}:
        return True
    if lowered in {"false", "no"}:
        return False
    try:
        return int(lowered, 0) != 0
    except ValueError:
        try:
            return int(lowered, 10) != 0
        except ValueError:
            return False


def commit_destination_register(event: dict[str, Any]) -> Optional[str]:
    raw = commit_field(event, "dst")
    if raw is None:
        return None
    normalized = normalize_register_name(raw)
    if normalized is not None:
        return normalized
    try:
        number = int(raw, 0)
    except ValueError:
        try:
            number = int(raw, 10)
        except ValueError:
            number = None
    if number is not None and 0 <= number <= 31:
        return f"x{number}"
    return None


def correlate_commit_writers(parsed_logs: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Associate a GPR mismatch with its latest visible committed writer.

    This is deliberately a candidate relation.  It is useful for XiangShan
    commit windows, but the harness may compare pre-state or post-state and a
    truncated window may omit the true producer.
    """
    correlations: list[dict[str, Any]] = []
    for log in parsed_logs:
        events = log["events"]
        commits = [event for event in events if "commit" in event["types"]]
        mismatches = [event for event in events if "mismatch" in event["types"]]
        for mismatch_index, mismatch in enumerate(mismatches):
            previous_mismatch_line = (
                mismatches[mismatch_index - 1]["raw_line_number"] if mismatch_index else 0
            )
            next_mismatch_line = (
                mismatches[mismatch_index + 1]["raw_line_number"]
                if mismatch_index + 1 < len(mismatches)
                else 1 << 62
            )
            window_start = max(previous_mismatch_line + 1, mismatch["raw_line_number"] - 512)
            window_end = min(next_mismatch_line - 1, mismatch["raw_line_number"] + 512)
            gpr_targets = [
                register for register in mismatch["register_candidates"] if register.startswith("x")
            ]
            if not gpr_targets:
                continue
            nearby_side_values = [
                {
                    "raw_line_number": event["raw_line_number"],
                    "label": value["label"],
                    "value": value["value"],
                }
                for event in events
                if abs(event["raw_line_number"] - mismatch["raw_line_number"]) <= 6
                for value in event["side_values"]
            ]
            for register in unique_in_order(gpr_targets):
                # Checkpoints commonly dump their commit ring *after* the
                # mismatch line.  Prefer a bounded local region around the
                # mismatch, then choose the writer that matches a logged side
                # value before falling back to raw-line proximity/order.
                eligible = [
                    event
                    for event in commits
                    if window_start <= event["raw_line_number"] <= window_end
                    and commit_write_enabled(event)
                    and commit_destination_register(event) == register
                ]
                side_numbers = {
                    parsed_machine_value(side["value"])
                    for side in nearby_side_values
                    if parsed_machine_value(side["value"]) is not None
                }
                value_matching = [
                    event
                    for event in eligible
                    if (lambda raw: raw is not None and parsed_machine_value(raw) in side_numbers)(
                        commit_field(event, "data")
                    )
                ]
                pool = value_matching or eligible
                events_with_cycles = [event for event in pool if event["cycles"]]
                selection_basis = "nearest raw line (no commit cycle was available)"
                if events_with_cycles:
                    latest_cycle = max(max(event["cycles"]) for event in events_with_cycles)
                    latest = [event for event in events_with_cycles if latest_cycle in event["cycles"]]
                    selection_basis = f"largest parsed commit cycle {latest_cycle}"
                    events_with_sequences = [
                        event for event in latest if event["commit_sequences"]
                    ]
                    if events_with_sequences:
                        latest_sequence = max(
                            max(event["commit_sequences"]) for event in events_with_sequences
                        )
                        latest = [
                            event
                            for event in events_with_sequences
                            if latest_sequence in event["commit_sequences"]
                        ]
                        selection_basis += f", then largest commit sequence {latest_sequence}"
                else:
                    minimum_distance = min(
                        (abs(event["raw_line_number"] - mismatch["raw_line_number"]) for event in pool),
                        default=None,
                    )
                    latest = [
                        event
                        for event in pool
                        if abs(event["raw_line_number"] - mismatch["raw_line_number"]) == minimum_distance
                    ]
                producer_candidates = []
                for event in latest:
                    write_data = commit_field(event, "data")
                    write_data_number = parsed_machine_value(write_data) if write_data is not None else None
                    matching_side_labels = [
                        side["label"]
                        for side in nearby_side_values
                        if write_data_number is not None and parsed_machine_value(side["value"]) == write_data_number
                    ]
                    producer_candidates.append(
                        {
                            "pc": event["pcs"][0] if len(event["pcs"]) == 1 else None,
                            "pc_candidates": event["pcs"],
                            "instruction_bits": event["instruction_bits"],
                            "write_data": write_data,
                            "write_data_matches_side_labels": matching_side_labels,
                            "destination": commit_destination_register(event),
                            "cycle_candidates": event["cycles"],
                            "commit_sequence_candidates": event["commit_sequences"],
                            "identity": commit_field(event, "idx", "robidx", "rob"),
                            "raw_line_number": event["raw_line_number"],
                            "raw_line": event["raw_line"],
                            "evidence": (
                                "same-window parsed commit with wen != 0 and matching dst; "
                                f"matching logged side value preferred; selected by {selection_basis}"
                            ),
                            "confidence": "Candidate",
                        }
                    )
                correlations.append(
                    {
                        "log": log["path"],
                        "mismatch_raw_line_number": mismatch["raw_line_number"],
                        "mismatch_raw_line": mismatch["raw_line"],
                        "mismatching_register": register,
                        "mismatch_register_spellings": unique_in_order(
                            mention["spelling"]
                            for mention in mismatch["register_mentions"]
                            if mention["normalized"] == register
                        ),
                        "mismatch_side_values": nearby_side_values,
                        "reporter_pc_candidates": mismatch["pcs"],
                        "producer_candidates": producer_candidates,
                        "other_matching_writers_in_512_line_window": max(0, len(eligible) - len(latest)),
                        "ambiguities": [
                            "the association uses a bounded 512-line region between adjacent parsed mismatches; a checkpoint may print its ring before or after the mismatch",
                            "comparison phase and multi-lane retirement order must be verified in the harness",
                            "the latest architectural writer can be a propagation point rather than the root-cause instruction",
                        ],
                    }
                )
    return correlations


def parse_taint(value: str) -> dict[str, Any]:
    if ":" not in value:
        raise argparse.ArgumentTypeError(
            f"--taint expects kind:value, such as reg:a0 or control:pc; got {value!r}"
        )
    kind, raw_target = value.split(":", 1)
    kind = kind.strip().lower()
    target = raw_target.strip()
    if not target:
        raise argparse.ArgumentTypeError(f"--taint has an empty target: {value!r}")
    if kind == "reg":
        normalized = normalize_register_name(target)
        if normalized is None:
            raise argparse.ArgumentTypeError(f"unknown RISC-V register in --taint {value!r}")
        return {"kind": kind, "target": normalized, "input": value}
    if kind == "mem":
        address = parse_integer(target, "--taint mem")
        if address < 0:
            raise argparse.ArgumentTypeError(f"memory taint address cannot be negative: {value!r}")
        return {"kind": kind, "target": hex_address(address), "input": value}
    if kind in {"csr", "control"}:
        return {"kind": kind, "target": target.lower(), "input": value}
    raise argparse.ArgumentTypeError(
        f"unsupported --taint kind {kind!r}; use reg, csr, mem, or control"
    )


def log_address_value(raw: str) -> Optional[int]:
    text = clean_log_value(raw).lower().removeprefix("0x")
    try:
        return int(text, 16)
    except ValueError:
        return None


def event_field(event: dict[str, Any], *names: str) -> Optional[str]:
    wanted = set(names)
    for field in event.get("store_fields", []):
        if field["field"] in wanted:
            return field["value"]
    return None


def memory_taint_observations(
    parsed_logs: list[dict[str, Any]], target_text: str
) -> tuple[list[dict[str, Any]], int]:
    target = int(target_text, 16)
    observations: list[dict[str, Any]] = []
    total = 0
    for log in parsed_logs:
        for event in log["events"]:
            raw_address = event_field(event, "paddr", "vaddr", "addr", "address")
            if raw_address is None:
                continue
            address = log_address_value(raw_address)
            if address is None:
                continue
            raw_length = event_field(event, "len", "size")
            length = parsed_number(raw_length) if raw_length is not None else None
            if length is None or length <= 0:
                length = 1
            if not address <= target < address + length:
                continue
            total += 1
            if len(observations) >= MAX_NAMED_TAINT_MATCHES:
                continue
            observations.append(
                {
                    "log": log["path"],
                    "raw_line_number": event["raw_line_number"],
                    "event_types": event["types"],
                    "store_side": event.get("store_side"),
                    "address": hex_address(address),
                    "length": length,
                    "target_byte_offset": target - address,
                    "data": event_field(event, "data"),
                    "mask": event_field(event, "mask"),
                    "pcs": event["pcs"],
                    "cycles": event["cycles"],
                    "raw_line": event["raw_line"],
                    "confidence": "E1 OBSERVED overlap in one parsed log event",
                }
            )
    return observations, total


def named_taint_observations(
    raw_log_lines: dict[str, list[str]], target: str
) -> tuple[list[dict[str, Any]], int]:
    pattern = re.compile(rf"(?<![A-Za-z0-9_.]){re.escape(target)}(?![A-Za-z0-9_.])", re.IGNORECASE)
    observations: list[dict[str, Any]] = []
    total = 0
    for path, lines in raw_log_lines.items():
        for line_number, line in enumerate(lines, start=1):
            if not pattern.search(line):
                continue
            total += 1
            if len(observations) < MAX_NAMED_TAINT_MATCHES:
                observations.append(
                    {
                        "log": path,
                        "raw_line_number": line_number,
                        "raw_line": line,
                        "confidence": "E1 OBSERVED name match only; semantic role unresolved",
                    }
                )
    return observations, total


def control_taint_observations(
    parsed_logs: list[dict[str, Any]], target: str
) -> tuple[list[dict[str, Any]], int]:
    observations: list[dict[str, Any]] = []
    total = 0
    target_pattern = re.compile(
        rf"(?<![A-Za-z0-9_.]){re.escape(target)}(?![A-Za-z0-9_.])", re.IGNORECASE
    )
    for log in parsed_logs:
        for event in log["events"]:
            lower = event["raw_line"].lower()
            high_signal = bool(
                {"mismatch", "exception"}.intersection(event["types"])
                or "<--" in event["raw_line"]
                or re.search(r"\b(?:redirect|flush|trap|interrupt|mret|sret|uret)\b", lower)
            )
            if not high_signal or not target_pattern.search(event["raw_line"]):
                continue
            total += 1
            if len(observations) < MAX_NAMED_TAINT_MATCHES:
                observations.append(
                    {
                        "log": log["path"],
                        "raw_line_number": event["raw_line_number"],
                        "raw_line": event["raw_line"],
                        "event_types": event["types"],
                        "pcs": event["pcs"],
                        "cycles": event["cycles"],
                        "confidence": "E1 OBSERVED high-signal control text; causal edge unresolved",
                    }
                )
    return observations, total


def analyze_nonregister_taints(
    roots: list[dict[str, Any]],
    parsed_logs: list[dict[str, Any]],
    raw_log_lines: dict[str, list[str]],
) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    for root in roots:
        if root["kind"] == "mem":
            observations, total = memory_taint_observations(parsed_logs, root["target"])
            interpretation = (
                "logged accesses whose byte interval overlaps the tainted address; physical/virtual "
                "namespace, global order, forwarding, and visibility still require dynamic proof"
            )
        elif root["kind"] == "control":
            observations, total = control_taint_observations(parsed_logs, root["target"])
            interpretation = (
                "high-signal logged control observations plus static CFG candidates at each anchor; "
                "executed predecessor/successor, redirect, trap, and flush still require dynamic proof"
            )
        else:
            observations, total = named_taint_observations(raw_log_lines, root["target"])
            interpretation = (
                "textual log observations only; static CFG candidates are attached to each anchor "
                "for control roots, while CSR/control state transitions require a dynamic trace"
            )
        results.append(
            {
                **root,
                "observation_count": total,
                "observations": observations,
                "observations_truncated": total > len(observations),
                "interpretation": interpretation,
                "status": "candidate observations; dynamic taint relation unresolved",
            }
        )
    return results


def compact_instruction(instruction: dict[str, Any]) -> dict[str, Any]:
    return {
        "pc": instruction["pc"],
        "bytes_file_order": instruction["bytes_file_order"],
        "assembly": instruction["assembly"],
        "section": instruction["section"],
        "function": instruction["function"],
        "raw_line_number": instruction["raw_line_number"],
    }


def direct_target(instruction: dict[str, Any]) -> Optional[int]:
    mnemonic = instruction["mnemonic"]
    if mnemonic not in CONDITIONAL_BRANCHES and mnemonic not in DIRECT_JUMPS:
        return None
    operands = re.sub(r"<[^>]*>", "", instruction["operands"]).strip()
    match = re.search(r"(?:^|,)\s*(?:0x)?([0-9a-fA-F]+)\s*$", operands)
    if not match:
        return None
    return int(match.group(1), 16)


def instruction_has_immediate_fallthrough(instruction: dict[str, Any]) -> bool:
    mnemonic = instruction["mnemonic"]
    if mnemonic in CONDITIONAL_BRANCHES:
        return True
    if mnemonic in DIRECT_JUMPS or mnemonic in INDIRECT_JUMPS or mnemonic in SYSTEM_CONTROL:
        return False
    return True


def index_by_pc(instructions: list[dict[str, Any]]) -> dict[int, list[int]]:
    result: dict[int, list[int]] = {}
    for index, instruction in enumerate(instructions):
        result.setdefault(instruction["address"], []).append(index)
    return result


def next_contiguous_instruction(
    instructions: list[dict[str, Any]], index: int, by_pc: dict[int, list[int]]
) -> list[dict[str, Any]]:
    instruction = instructions[index]
    next_address = instruction["address"] + instruction["length"]
    return [
        instructions[candidate]
        for candidate in by_pc.get(next_address, [])
        if instructions[candidate]["section"] == instruction["section"]
    ]


def control_flow_candidates(
    instructions: list[dict[str, Any]], anchor_index: int, by_pc: dict[int, list[int]]
) -> dict[str, Any]:
    anchor = instructions[anchor_index]
    predecessors: list[dict[str, Any]] = []
    successors: list[dict[str, Any]] = []
    ambiguities: list[str] = []
    for index, instruction in enumerate(instructions):
        if index == anchor_index or instruction["section"] != anchor["section"]:
            continue
        target = direct_target(instruction)
        if target == anchor["address"]:
            predecessors.append(
                {"kind": "direct branch/jump target candidate", "instruction": compact_instruction(instruction)}
            )
        if instruction_has_immediate_fallthrough(instruction):
            if instruction["address"] + instruction["length"] == anchor["address"]:
                predecessors.append(
                    {"kind": "linear fallthrough candidate", "instruction": compact_instruction(instruction)}
                )
    mnemonic = anchor["mnemonic"]
    target = direct_target(anchor)
    if target is not None:
        target_matches = by_pc.get(target, [])
        if target_matches:
            for candidate in target_matches:
                successors.append(
                    {
                        "kind": "direct target candidate",
                        "instruction": compact_instruction(instructions[candidate]),
                    }
                )
        else:
            successors.append({"kind": "direct target outside parsed disassembly", "pc": hex_address(target)})
    fallthroughs = next_contiguous_instruction(instructions, anchor_index, by_pc)
    if mnemonic in CONDITIONAL_BRANCHES or instruction_has_immediate_fallthrough(anchor):
        for candidate in fallthroughs:
            successors.append({"kind": "fallthrough candidate", "instruction": compact_instruction(candidate)})
    elif mnemonic in {"call", "c.jal"} or (mnemonic == "jal" and "x1" in anchor["def_use"]["definitions"]):
        for candidate in fallthroughs:
            successors.append(
                {"kind": "possible return-site candidate, not immediate successor", "instruction": compact_instruction(candidate)}
            )
    if mnemonic in INDIRECT_JUMPS or mnemonic in RETURNS or mnemonic in SYSTEM_CONTROL:
        ambiguities.append("indirect/trap/return target requires dynamic architectural state")
    ambiguities.append(
        "static predecessor/successor candidates do not prove the executed path; loops, calls, redirects, traps, and interrupts remain unresolved"
    )
    return {
        "anchor": compact_instruction(anchor),
        "predecessor_candidates": predecessors,
        "successor_candidates": successors,
        "ambiguities": unique_in_order(ambiguities),
    }


def build_anchors(
    explicit_pcs: list[int],
    parsed_logs: list[dict[str, Any]],
    commit_correlations: list[dict[str, Any]],
    before: int,
    after: int,
) -> list[dict[str, Any]]:
    anchors: dict[int, dict[str, Any]] = {}

    def add(pc: int, source: dict[str, Any], ambiguity: Optional[str] = None) -> None:
        anchor = anchors.setdefault(
            pc,
            {"pc_value": pc, "pc": hex_address(pc), "sources": [], "ambiguities": []},
        )
        if source not in anchor["sources"]:
            anchor["sources"].append(source)
        if ambiguity and ambiguity not in anchor["ambiguities"]:
            anchor["ambiguities"].append(ambiguity)

    for pc in explicit_pcs:
        add(pc, {"kind": "explicit --pc"})

    if explicit_pcs:
        for log in parsed_logs:
            for event in log["events"]:
                if not event["instruction_bits"]:
                    continue
                for pc_text in event["pcs"]:
                    pc_value = int(pc_text, 16)
                    if pc_value in anchors:
                        add(
                            pc_value,
                            {
                                "kind": "instruction bits at an explicitly selected logged PC",
                                "log": log["path"],
                                "raw_line_number": event["raw_line_number"],
                                "instruction_bits": event["instruction_bits"],
                            },
                        )
        return [anchors[pc] for pc in sorted(anchors)]

    for log in parsed_logs:
        pc_events = [event for event in log["events"] if event["pcs"]]
        mismatch_events = [event for event in log["events"] if "mismatch" in event["types"]]
        for mismatch in mismatch_events:
            if mismatch["pcs"]:
                for pc_text in mismatch["pcs"]:
                    add(
                        int(pc_text, 16),
                        {
                            "kind": "PC printed on mismatch line",
                            "log": log["path"],
                            "raw_line_number": mismatch["raw_line_number"],
                            "instruction_bits": mismatch["instruction_bits"],
                        },
                    )
                continue
            if pc_events:
                distance = min(abs(event["raw_line_number"] - mismatch["raw_line_number"]) for event in pc_events)
                if distance <= max(32, before + after + 4):
                    nearest = [
                        event
                        for event in pc_events
                        if abs(event["raw_line_number"] - mismatch["raw_line_number"]) == distance
                    ]
                    for event in nearest:
                        for pc_text in event["pcs"]:
                            add(
                                int(pc_text, 16),
                                {
                                    "kind": "nearest logged PC to mismatch",
                                    "log": log["path"],
                                    "raw_line_number": event["raw_line_number"],
                                    "mismatch_raw_line_number": mismatch["raw_line_number"],
                                    "line_distance": distance,
                                    "instruction_bits": event["instruction_bits"],
                                },
                                "PC was associated by line proximity and is not proven to be the reporter or divergent instruction",
                            )
        for event in log["events"]:
            if "side-values" in event["types"] and event["side_values"] and event["pcs"]:
                for pc_text in event["pcs"]:
                    add(
                        int(pc_text, 16),
                        {
                            "kind": "PC printed with DUT/reference/right/wrong values",
                            "log": log["path"],
                            "raw_line_number": event["raw_line_number"],
                            "instruction_bits": event["instruction_bits"],
                        },
                        "side labels are preserved verbatim; comparison phase and ownership are unverified",
                    )
        for event in log["events"]:
            if "exception" in event["types"] and event.get("exception"):
                exception = event["exception"]
                add(
                    int(exception["pc"], 16),
                    {
                        "kind": "faulting PC from exception event",
                        "log": log["path"],
                        "raw_line_number": event["raw_line_number"],
                        "cause": exception["cause"],
                        "instruction_bits": exception["instruction_bits"],
                    },
                    "exception event is architecturally meaningful, but its relation to a later CSR mismatch still requires comparison-phase evidence",
                )

    for correlation in commit_correlations:
        for producer in correlation["producer_candidates"]:
            if producer["pc"] is None:
                continue
            add(
                int(producer["pc"], 16),
                {
                    "kind": "commit-window GPR writer candidate",
                    "role": "producer-candidate-separate-from-reporter-pc",
                    "log": correlation["log"],
                    "raw_line_number": producer["raw_line_number"],
                    "mismatch_raw_line_number": correlation["mismatch_raw_line_number"],
                    "mismatching_register": correlation["mismatching_register"],
                    "write_data": producer["write_data"],
                    "instruction_bits": producer["instruction_bits"],
                    "cycle_candidates": producer["cycle_candidates"],
                    "commit_sequence_candidates": producer["commit_sequence_candidates"],
                    "identity": producer["identity"],
                },
                "this is the latest visible committed writer of the mismatching GPR, not a proven root-cause or reporter instruction",
            )

    # A separate NEMU/trace line may carry the instruction word for a PC that
    # was reported on a mismatch line without an ``inst`` field.  Attach it to
    # an already-selected anchor so byte identity can be checked without
    # turning every ordinary execution line into a new anchor.
    for log in parsed_logs:
        for event in log["events"]:
            if not event["instruction_bits"] or not event["pcs"]:
                continue
            for pc_text in event["pcs"]:
                pc_value = int(pc_text, 16)
                if pc_value in anchors:
                    add(
                        pc_value,
                        {
                            "kind": "instruction bits at an already-selected logged PC",
                            "log": log["path"],
                            "raw_line_number": event["raw_line_number"],
                            "instruction_bits": event["instruction_bits"],
                        },
                    )

    if not anchors:
        all_pc_events = [
            (log, event)
            for log in parsed_logs
            for event in log["events"]
            if event["pcs"]
        ]
        if all_pc_events:
            log, event = all_pc_events[-1]
            for pc_text in event["pcs"]:
                add(
                    int(pc_text, 16),
                    {
                        "kind": "fallback last parsed PC",
                        "log": log["path"],
                        "raw_line_number": event["raw_line_number"],
                        "instruction_bits": event["instruction_bits"],
                    },
                    "no mismatch-associated or explicit PC was available; this fallback is only an observation candidate",
                )
    return [anchors[pc] for pc in sorted(anchors)]


def attach_log_contexts(
    anchor: dict[str, Any], parsed_logs: list[dict[str, Any]], raw_log_lines: dict[str, list[str]], before: int, after: int
) -> None:
    locations: list[tuple[str, int, str]] = []
    for source in anchor["sources"]:
        if source.get("log") and source.get("raw_line_number"):
            locations.append((source["log"], source["raw_line_number"], source["kind"]))
        if source.get("log") and source.get("mismatch_raw_line_number"):
            locations.append(
                (source["log"], source["mismatch_raw_line_number"], "associated GPR mismatch")
            )
    for log in parsed_logs:
        for event in log["events"]:
            if anchor["pc"] in event["pcs"]:
                locations.append((log["path"], event["raw_line_number"], "exact PC occurrence"))
    locations = unique_in_order(locations)
    truncated = len(locations) > MAX_LOG_CONTEXTS_PER_ANCHOR
    contexts: list[dict[str, Any]] = []
    for path, line_number, reason in locations[:MAX_LOG_CONTEXTS_PER_ANCHOR]:
        lines = raw_log_lines[path]
        start = max(1, line_number - before)
        end = min(len(lines), line_number + after)
        contexts.append(
            {
                "log": path,
                "anchor_raw_line_number": line_number,
                "reason": reason,
                "start_raw_line_number": start,
                "end_raw_line_number": end,
                "lines": [
                    {"raw_line_number": number, "raw_line": lines[number - 1]}
                    for number in range(start, end + 1)
                ],
            }
        )
    anchor["log_contexts"] = contexts
    if truncated:
        anchor["ambiguities"].append(
            f"log context occurrences were capped at {MAX_LOG_CONTEXTS_PER_ANCHOR}; all parsed events remain in case.json"
        )


def attach_disassembly_context(
    anchor: dict[str, Any], instructions: list[dict[str, Any]], before: int, after: int
) -> list[int]:
    matches = [index for index, instruction in enumerate(instructions) if instruction["address"] == anchor["pc_value"]]
    contexts: list[dict[str, Any]] = []
    for index in matches:
        low = max(0, index - before)
        high = min(len(instructions), index + after + 1)
        contexts.append(
            {
                "match": compact_instruction(instructions[index]),
                "instructions": [
                    {**compact_instruction(instruction), "def_use": instruction["def_use"]}
                    for instruction in instructions[low:high]
                ],
            }
        )
    anchor["disassembly_contexts"] = contexts
    if len(matches) > 1:
        anchor["ambiguities"].append(
            "the PC occurs more than once in the parsed disassembly; section/image identity must be resolved"
        )
    if not matches:
        lower = [instruction for instruction in instructions if instruction["address"] < anchor["pc_value"]]
        upper = [instruction for instruction in instructions if instruction["address"] > anchor["pc_value"]]
        nearby = []
        if lower:
            nearby.append({"kind": "nearest lower address", "instruction": compact_instruction(max(lower, key=lambda item: item["address"]))})
        if upper:
            nearby.append({"kind": "nearest higher address", "instruction": compact_instruction(min(upper, key=lambda item: item["address"]))})
        anchor["nearby_disassembly_candidates"] = nearby
        anchor["ambiguities"].append("no exact instruction address matched this PC in the parsed disassembly")
    return matches


def mismatch_registers_for_anchor(anchor: dict[str, Any], parsed_logs: list[dict[str, Any]]) -> list[str]:
    source_lines = {
        (source.get("log"), source.get("raw_line_number"))
        for source in anchor["sources"]
        if source.get("log") and source.get("raw_line_number")
    }
    candidates: list[str] = []
    for log in parsed_logs:
        for event in log["events"]:
            near_source = any(
                path == log["path"] and line is not None and abs(event["raw_line_number"] - line) <= 3
                for path, line in source_lines
            )
            if anchor["pc"] in event["pcs"] or near_source:
                candidates.extend(event["register_candidates"])
    return unique_in_order(candidates)


def backward_register_slice(
    instructions: list[dict[str, Any]],
    anchor_index: int,
    seed_registers: list[str],
    depth_limit: int,
) -> dict[str, Any]:
    anchor = instructions[anchor_index]
    queue: deque[tuple[int, str, int, dict[str, Any]]] = deque()
    for register in seed_registers:
        # A mismatch register is commonly the destination of the anchor
        # instruction (for example `lbu a4` or `csrr t4,vl`).  Start before
        # that instruction so the slice seeks its inputs rather than treating
        # the sink as its own producer.  A user-supplied source register may
        # legitimately be consumed by the anchor, so retain the anchor in
        # that case.
        start_index = anchor_index - 1 if register in anchor["def_use"]["definitions"] else anchor_index
        queue.append(
            (
                start_index,
                register,
                0,
                {"kind": "taint sink at anchor", "pc": anchor["pc"], "assembly": anchor["assembly"]},
            )
        )
    edges: list[dict[str, Any]] = []
    unresolved: list[dict[str, Any]] = []
    visited: set[tuple[int, str]] = set()
    while queue:
        start_index, register, depth, consumer = queue.popleft()
        state = (start_index, register)
        if state in visited:
            continue
        visited.add(state)
        if depth >= depth_limit:
            unresolved.append(
                {
                    "register": register,
                    "consumer": consumer,
                    "reason": f"slice depth limit {depth_limit} reached",
                }
            )
            continue
        producer_index: Optional[int] = None
        for index in range(start_index, -1, -1):
            candidate = instructions[index]
            if candidate["section"] != anchor["section"]:
                continue
            if register in candidate["def_use"]["definitions"]:
                producer_index = index
                break
        if producer_index is None:
            unresolved.append(
                {
                    "register": register,
                    "consumer": consumer,
                    "reason": "no earlier textual definition in the same disassembly section; input/caller/path definition unresolved",
                }
            )
            continue
        producer = instructions[producer_index]
        producer_compact = compact_instruction(producer)
        edges.append(
            {
                "depth": depth + 1,
                "register": register,
                "producer_candidate": producer_compact,
                "consumer": consumer,
                "producer_uses": producer["def_use"]["uses"],
                "evidence": "latest earlier textual definition in the parsed disassembly section",
                "confidence": "Candidate",
                "unresolved_conditions": [
                    "executed CFG path and dynamic producer identity are unproven",
                    "memory/CSR/control/exception and call effects are not closed by this register-only slice",
                ],
            }
        )
        for used_register in producer["def_use"]["uses"]:
            queue.append((producer_index - 1, used_register, depth + 1, producer_compact))
    return {
        "seed_registers": seed_registers,
        "depth_limit": depth_limit,
        "edges": edges,
        "unresolved": unresolved,
        "status": "static candidate only; not a dynamic taint proof",
    }


def read_bytes_at(path: Path, offset: int, length: int) -> Optional[str]:
    if offset < 0:
        return None
    try:
        size = path.stat().st_size
        if offset >= size:
            return None
        with path.open("rb") as stream:
            stream.seek(offset)
            data = stream.read(length)
    except OSError as exc:
        raise AnalysisError(f"cannot read bytes from {path}: {exc}") from exc
    return data.hex() if data else None


def anchor_byte_checks(
    anchor: dict[str, Any],
    instructions: list[dict[str, Any]],
    match_indexes: list[int],
    binary: Optional[Path],
    base: Optional[int],
    elf: Optional[Path],
    elf_summary: Optional[dict[str, Any]],
) -> dict[str, Any]:
    sources: list[dict[str, Any]] = []
    lengths = unique_in_order(instructions[index]["length"] for index in match_indexes)
    expected_length = lengths[0] if len(lengths) == 1 else None
    length = expected_length or 4
    for source in anchor["sources"]:
        for raw_bits in source.get("instruction_bits", []):
            decoded = decode_log_instruction_bits(raw_bits, expected_length)
            decoded_length = len(decoded) // 2 if decoded else None
            interpretation = "normalized from commit/NEMU integer spelling; verify against a fetch trace"
            if expected_length is not None and decoded_length not in {None, expected_length}:
                interpretation += (
                    f"; logged encoding length {decoded_length} differs from parsed disassembly length "
                    f"{expected_length}"
                )
            sources.append(
                {
                    "kind": "log instruction bits (byte-reversed assumption)",
                    "path": source.get("log"),
                    "raw_line_number": source.get("raw_line_number"),
                    "raw_instruction_bits": raw_bits,
                    "bytes": decoded,
                    "length": decoded_length,
                    "interpretation": interpretation,
                }
            )
    for index in match_indexes:
        instruction = instructions[index]
        sources.append(
            {
                "kind": "disassembly display decoded to file byte order",
                "path": None,
                "bytes": instruction["bytes_file_order"],
                "length": instruction["length"],
                "raw_line_number": instruction["raw_line_number"],
            }
        )
    if binary is not None and base is not None:
        offset = anchor["pc_value"] - base
        data = read_bytes_at(binary, offset, length)
        sources.append(
            {
                "kind": "raw BIN at PC - base",
                "path": str(binary),
                "base": hex_address(base),
                "offset": hex_address(offset) if offset >= 0 else str(offset),
                "bytes": data,
                "length_requested": length,
                "in_bounds": data is not None,
            }
        )
    if elf is not None and elf_summary is not None:
        section_matches = []
        for section in elf_summary.get("sections", []):
            address = int(section["address"], 16)
            size = int(section["size"], 16)
            if section["type"] != "NOBITS" and address <= anchor["pc_value"] < address + size:
                offset = int(section["offset"], 16) + anchor["pc_value"] - address
                section_matches.append(
                    {
                        "kind": "ELF section file mapping",
                        "path": str(elf),
                        "section": section["name"],
                        "offset": hex_address(offset),
                        "bytes": read_bytes_at(elf, offset, length),
                        "length_requested": length,
                    }
                )
        sources.extend(section_matches)
    executable_mapping: Optional[dict[str, Any]] = None
    if elf_summary is not None:
        segments = elf_summary.get("load_segments", [])
        matches = []
        for segment in segments:
            start = int(segment["virt_addr"], 16)
            memory_size = int(segment["memory_size"], 16)
            if start <= anchor["pc_value"] < start + memory_size:
                matches.append(segment)
        executable_matches = [segment for segment in matches if "E" in segment.get("flags", "")]
        executable_mapping = {
            "in_load_segment": bool(matches),
            "in_executable_segment": bool(executable_matches),
            "matching_segments": matches,
            "interpretation": (
                "PC is inside an ELF executable LOAD segment"
                if executable_matches
                else "PC is not inside an ELF executable LOAD segment; do not map it to a nearby instruction"
            ),
        }
    comparable = [source["bytes"] for source in sources if source.get("bytes")]
    consistency: Optional[bool]
    if len(comparable) >= 2:
        # Compare only the common prefix because an out-of-bounds artifact can
        # return fewer bytes than the requested instruction length.
        common_length = min(len(value) for value in comparable)
        consistency = len({value[:common_length].lower() for value in comparable}) == 1
    else:
        consistency = None
    return {
        "sources": sources,
        "executable_mapping": executable_mapping,
        "consistent_common_prefix": consistency,
        "interpretation": (
            "true means the available static byte sources share a common prefix; it does not prove that these bytes were fetched in the failing run"
        ),
    }


def markdown_cell(value: Any) -> str:
    if value is None:
        return "-"
    if isinstance(value, (list, tuple)):
        text = ", ".join(str(item) for item in value)
    else:
        text = str(value)
    return text.replace("|", "\\|").replace("\n", "<br>")


def display_raw(text: str) -> str:
    output = []
    for char in text:
        code = ord(char)
        if char == "\t" or code >= 0x20:
            output.append(char)
        else:
            output.append(f"\\x{code:02x}")
    return "".join(output)


def fenced_block(lines: Sequence[str]) -> str:
    content = "\n".join(display_raw(line) for line in lines)
    longest = max((len(match.group(0)) for match in re.finditer(r"`+", content)), default=0)
    fence = "`" * max(3, longest + 1)
    return f"{fence}text\n{content}\n{fence}"


def render_markdown(case: dict[str, Any]) -> str:
    output: list[str] = [
        "# XiangShan Difftest Static Case Report",
        "",
        "> **Status: Unresolved.** This automated report provides static candidates only. "
        "It does not prove the executed instruction path, dynamic control flow, dynamic data producer, "
        "comparison phase, or DUT/reference ownership.",
        "",
        "## Artifact identity",
        "",
        "| Role | Absolute path | Size | SHA-256 | Magic |",
        "| --- | --- | ---: | --- | --- |",
    ]
    for artifact in case["artifacts"]:
        output.append(
            "| "
            + " | ".join(
                markdown_cell(value)
                for value in (
                    artifact["role"],
                    artifact["path"],
                    artifact["size"],
                    artifact["sha256"],
                    f"{artifact['magic']['kind']} / {artifact['magic']['hex']}",
                )
            )
            + " |"
        )
    output.extend(["", "Inputs were opened read-only; the supplied workload was not executed.", ""])

    output.extend(
        [
            "## Parsed run metadata",
            "",
            "| Log | Seed | Core revision / dirty | Privilege mode | Final counters | Image / reference |",
            "| --- | --- | --- | --- | --- | --- |",
        ]
    )
    for log in case["logs"]:
        metadata = log["metadata"]
        revisions = [
            f"core={item['core']} sha={item['commit_sha']} dirty={item['dirty']}"
            for item in metadata["core_revisions"]
        ]
        summaries = [
            f"core={item['core']} instr={item['instruction_count']} c={item['cycle_count']} ipc={item['ipc']}"
            for item in metadata["run_summaries"]
        ]
        images_and_refs = [
            *(f"image={value}" for value in metadata["images"]),
            *(f"ref={value}" for value in metadata["reference_models"]),
        ]
        output.append(
            "| "
            + " | ".join(
                markdown_cell(value)
                for value in (
                    log["path"],
                    metadata["seeds"],
                    revisions,
                    metadata["privilege_modes"],
                    summaries,
                    images_and_refs,
                )
            )
            + " |"
        )
    output.extend(
        [
            "",
            "These fields are best-effort log parsing. Missing values remain unknown; paths printed by the run are not assumed to be current local artifacts.",
            "",
        ]
    )

    if case.get("elf_inspection"):
        inspection = case["elf_inspection"]
        header = inspection["summary"].get("header", {})
        attributes = inspection["summary"].get("attributes", {})
        output.extend(
            [
                "## ELF inspection",
                "",
                f"`readelf` tool: `{inspection['tool']}`",
                "",
                f"Machine: `{header.get('machine', 'unknown')}`; entry: `{header.get('entry_point_address', 'unknown')}`; "
                f"class: `{header.get('class', 'unknown')}`.",
                "",
                f"ISA attributes: `{attributes.get('Tag_RISCV_arch', 'unknown')}`; "
                f"privileged spec: `{attributes.get('Tag_RISCV_priv_spec', 'unknown')}."
                f"{attributes.get('Tag_RISCV_priv_spec_minor', 'unknown')}`.",
                "",
            ]
        )
    output.extend(["## Parsed failure events", ""])
    all_events = [(log, event) for log in case["logs"] for event in log["events"]]
    if all_events:
        priority_types = {"mismatch", "exception", "store-commit", "store-side"}
        priority_indexes = [
            index
            for index, (_log, event) in enumerate(all_events)
            if priority_types.intersection(event["types"])
        ][:MAX_RENDERED_EVENTS]
        selected_indexes = set(priority_indexes)
        for index in range(len(all_events)):
            if len(selected_indexes) >= MAX_RENDERED_EVENTS:
                break
            selected_indexes.add(index)
        rendered_events = [all_events[index] for index in sorted(selected_indexes)]
        output.extend(
            [
                "| Log | Raw line | Types | Cycle | Hart/commit sequence | PC | Parsed fields | Raw text |",
                "| --- | ---: | --- | --- | --- | --- | --- | --- |",
            ]
        )
        for log, event in rendered_events:
            details = [f"{item['label']}={item['value']}" for item in event["side_values"]]
            if event.get("store_side"):
                details.append(f"store_side={event['store_side']}")
            details.extend(f"{item['field']}={item['value']}" for item in event["store_fields"])
            if event.get("exception"):
                details.append(
                    f"exception cause={event['exception']['cause']} inst={event['exception']['instruction_bits']}"
                )
            if event.get("commit_group"):
                group = event["commit_group"]
                details.append(f"group={group['group']} cmtcnt={group['commit_count']}")
            if event.get("memory_event"):
                details.append(f"memory={event['memory_event']['access']}")
            details.extend(f"{item['field']}={item['value']}" for item in event["commit_fields"])
            output.append(
                "| "
                + " | ".join(
                    markdown_cell(value)
                    for value in (
                        log["path"],
                        event["raw_line_number"],
                        event["types"],
                        event["cycles"],
                        f"hart={event['harts']}, commit_sequence={event['commit_sequences']}",
                        event["pcs"],
                        details,
                        display_raw(event["raw_line"]),
                    )
                )
                + " |"
            )
        if len(all_events) > MAX_RENDERED_EVENTS:
            output.append(
                f"\nA selected set of {len(rendered_events)} early and high-signal events is rendered "
                f"from {len(all_events)} parsed events; "
                "case.json contains all parsed events."
            )
    else:
        output.append("No common PC/cycle/mismatch/right/wrong/commit pattern was recognized. Inspect the raw logs manually.")
    output.append("")

    output.extend(["## Commit-window GPR writer correlation", ""])
    if case["commit_window_correlations"]:
        output.extend(
            [
                "These candidates are kept separate from the reporter PC. They correlate a mismatch with the latest visible `wen != 0, dst=<register>` commit writer.",
                "",
                        "| Log:mismatch line | GPR / logged spelling | Reporter PC candidate | Latest writer PC:line | Writer data / matching side | Cycle/commit sequence/identity |",
                "| --- | --- | --- | --- | --- | --- |",
            ]
        )
        for correlation in case["commit_window_correlations"]:
            if correlation["producer_candidates"]:
                for producer in correlation["producer_candidates"]:
                    ordering = (
                        f"c={producer['cycle_candidates']}, commit_sequence={producer['commit_sequence_candidates']}, "
                        f"idx={producer['identity']}"
                    )
                    output.append(
                        "| "
                        + " | ".join(
                            markdown_cell(value)
                            for value in (
                                f"{correlation['log']}:{correlation['mismatch_raw_line_number']}",
                                f"{correlation['mismatching_register']} / {correlation['mismatch_register_spellings']}",
                                correlation["reporter_pc_candidates"],
                                f"{producer['pc_candidates']}:{producer['raw_line_number']}",
                                f"{producer['write_data']} / {producer['write_data_matches_side_labels']}",
                                ordering,
                            )
                        )
                        + " |"
                    )
            else:
                output.append(
                    "| "
                    + " | ".join(
                        markdown_cell(value)
                        for value in (
                            f"{correlation['log']}:{correlation['mismatch_raw_line_number']}",
                            f"{correlation['mismatching_register']} / {correlation['mismatch_register_spellings']}",
                            correlation["reporter_pc_candidates"],
                            "none in preceding 512 lines",
                            None,
                            "truncated/missing commit window",
                        )
                    )
                    + " |"
                )
        output.extend(
            [
                "",
                "A latest architectural writer can be only a propagation point. Verify comparison phase and multi-lane retirement order before promoting it beyond **Candidate**.",
                "",
            ]
        )
    else:
        output.extend(["No GPR mismatch-to-commit-writer correlation was available.", ""])

    output.extend(["## Global ambiguities", ""])
    for ambiguity in case["global_ambiguities"]:
        output.append(f"- {ambiguity}")
    output.extend(["", "## Non-register taint observations", ""])
    if case["nonregister_taint_results"]:
        for result in case["nonregister_taint_results"]:
            output.append(f"### `{result['kind']}:{result['target']}`")
            output.append("")
            output.append(result["interpretation"] + ".")
            output.append("")
            if result["observations"]:
                output.extend(
                    [
                        "| Log:line | Types/address | PC/cycle | Data/mask | Raw text |",
                        "| --- | --- | --- | --- | --- |",
                    ]
                )
                for observation in result["observations"]:
                    address_detail = (
                        f"{observation.get('event_types', [])}; "
                        f"addr={observation.get('address')}, len={observation.get('length')}, "
                        f"byte_offset={observation.get('target_byte_offset')}"
                        if "address" in observation
                        else (
                            str(observation["event_types"])
                            if observation.get("event_types")
                            else "textual name match"
                        )
                    )
                    output.append(
                        "| "
                        + " | ".join(
                            markdown_cell(value)
                            for value in (
                                f"{observation['log']}:{observation['raw_line_number']}",
                                address_detail,
                                f"pc={observation.get('pcs', [])}, c={observation.get('cycles', [])}",
                                f"data={observation.get('data')}, mask={observation.get('mask')}",
                                display_raw(observation["raw_line"]),
                            )
                        )
                        + " |"
                    )
                output.append("")
                if result["observations_truncated"]:
                    output.append(
                        f"Only {len(result['observations'])} of {result['observation_count']} name/overlap matches are rendered; case.json retains the capped observation list."
                    )
                    output.append("")
            else:
                output.extend(["No matching log observation was found.", ""])
    else:
        output.extend(["No `mem:`, `csr:`, or `control:` taint root was supplied.", ""])

    output.extend(["## Anchors and instruction flow", ""])
    if not case["anchors"]:
        output.extend(
            [
                "No anchor PC was selected. Add `--pc 0x...` or provide a log containing a recognizable PC near the mismatch.",
                "",
            ]
        )
    for anchor_number, anchor in enumerate(case["anchors"], start=1):
        output.extend([f"### Anchor {anchor_number}: `{anchor['pc']}`", ""])
        source_descriptions = []
        for source in anchor["sources"]:
            detail = source["kind"]
            if source.get("log"):
                detail += f" at {source['log']}:{source.get('raw_line_number')}"
            source_descriptions.append(detail)
        output.append("Sources: " + ("; ".join(source_descriptions) if source_descriptions else "none"))
        output.append("")
        if anchor["byte_checks"]["sources"]:
            output.extend(
                [
                    "Static byte identity checks:",
                    "",
                    "| Source | Bytes | Location |",
                    "| --- | --- | --- |",
                ]
            )
            for source in anchor["byte_checks"]["sources"]:
                location = source.get("path") or "supplied/generated disassembly"
                if source.get("offset") is not None:
                    location += f" offset {source['offset']}"
                output.append(
                    f"| {markdown_cell(source['kind'])} | `{markdown_cell(source.get('bytes'))}` | {markdown_cell(location)} |"
                )
            output.append("")
        mapping = anchor["byte_checks"].get("executable_mapping")
        if mapping is not None:
            mapping_status = "yes" if mapping["in_executable_segment"] else "no"
            output.append(
                f"ELF executable-segment check: **{mapping_status}**. {mapping['interpretation']}."
            )
            output.append("")
        for context in anchor["log_contexts"]:
            output.extend(
                [
                    f"Log context `{context['log']}:{context['anchor_raw_line_number']}` ({context['reason']}):",
                    "",
                    fenced_block(
                        [f"{line['raw_line_number']:>8}: {line['raw_line']}" for line in context["lines"]]
                    ),
                    "",
                ]
            )
        if anchor["disassembly_contexts"]:
            for context in anchor["disassembly_contexts"]:
                output.extend(
                    [
                        "Static disassembly context (address adjacency is not dynamic instruction flow):",
                        "",
                        "| PC | Bytes | Instruction | Function | Defs | Uses |",
                        "| --- | --- | --- | --- | --- | --- |",
                    ]
                )
                for instruction in context["instructions"]:
                    marker = "**anchor** " if instruction["pc"] == anchor["pc"] else ""
                    output.append(
                        "| "
                        + " | ".join(
                            markdown_cell(value)
                            for value in (
                                marker + instruction["pc"],
                                instruction["bytes_file_order"],
                                instruction["assembly"],
                                instruction["function"],
                                instruction["def_use"]["definitions"],
                                instruction["def_use"]["uses"],
                            )
                        )
                        + " |"
                    )
                output.append("")
        else:
            output.extend(["No exact disassembly instruction matched this anchor.", ""])

        output.extend(["#### Control-flow candidates", ""])
        if anchor["control_flow_candidates"]:
            for flow in anchor["control_flow_candidates"]:
                output.append(f"For `{flow['anchor']['pc']} {flow['anchor']['assembly']}`:")
                output.append("")
                output.extend(["| Direction | Candidate kind | PC/instruction |", "| --- | --- | --- |"])
                for candidate in flow["predecessor_candidates"]:
                    instruction = candidate["instruction"]
                    output.append(
                        f"| predecessor | {markdown_cell(candidate['kind'])} | `{instruction['pc']} {markdown_cell(instruction['assembly'])}` |"
                    )
                for candidate in flow["successor_candidates"]:
                    instruction = candidate.get("instruction")
                    value = f"{instruction['pc']} {instruction['assembly']}" if instruction else candidate.get("pc")
                    output.append(f"| successor | {markdown_cell(candidate['kind'])} | `{markdown_cell(value)}` |")
                if not flow["predecessor_candidates"] and not flow["successor_candidates"]:
                    output.append("| - | none resolved | dynamic trace required |")
                output.append("")
        else:
            output.extend(["No exact instruction was available for static CFG enumeration.", ""])

        output.extend(["#### Data-flow / backward register slice", ""])
        if anchor["slices"]:
            for slice_result in anchor["slices"]:
                output.append(
                    f"Seed registers: `{', '.join(slice_result['seed_registers']) or 'none'}`; "
                    f"depth limit: {slice_result['depth_limit']}."
                )
                output.append("")
                if slice_result["edges"]:
                    output.extend(
                        [
                            "| Depth | Register | Producer candidate | Consumer | Evidence | Confidence |",
                            "| ---: | --- | --- | --- | --- | --- |",
                        ]
                    )
                    for edge in slice_result["edges"]:
                        producer = edge["producer_candidate"]
                        consumer = edge["consumer"]
                        consumer_text = f"{consumer.get('pc', '')} {consumer.get('assembly', consumer.get('kind', ''))}".strip()
                        output.append(
                            "| "
                            + " | ".join(
                                markdown_cell(value)
                                for value in (
                                    edge["depth"],
                                    edge["register"],
                                    f"{producer['pc']} {producer['assembly']}",
                                    consumer_text,
                                    edge["evidence"],
                                    edge["confidence"],
                                )
                            )
                            + " |"
                        )
                    output.append("")
                if slice_result["unresolved"]:
                    output.append("Unresolved slice leaves:")
                    output.append("")
                    for leaf in slice_result["unresolved"]:
                        output.append(f"- `{leaf['register']}`: {leaf['reason']}")
                    output.append("")
        else:
            output.extend(["No register slice could be built for this anchor.", ""])
        if anchor["ambiguities"]:
            output.append("Anchor ambiguities:")
            output.append("")
            for ambiguity in unique_in_order(anchor["ambiguities"]):
                output.append(f"- {ambiguity}")
            output.append("")

    output.extend(
        [
            "## Evidence gaps and next capture",
            "",
            "- Verify the harness comparison phase and whether each printed PC is pre-state, post-state, next PC, or only a reporter PC.",
            "- Capture the last matching commit through the first mismatch with hart, cycle/order, instruction bytes, ROB identity, destination, and DUT/reference values.",
            "- Confirm the executed predecessor/successor and every reaching definition; this report only follows static address order.",
            "- For memory, CSR, exception, interrupt, call, replay, or self-modifying-code cases, capture the corresponding dynamic state and producer identity.",
            "- A pipeline instruction-flow claim needs Decode-through-Commit valid/ready/fire, redirect/flush/replay, writeback, and commit evidence tied by stable identity.",
            "",
            "The first bad architectural instruction and repair boundary remain **unresolved** until those dynamic observations close the candidate slice.",
            "",
        ]
    )
    return "\n".join(output)


def public_instruction(instruction: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in instruction.items() if key != "address"}


def parse_arguments(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Parse XiangShan difftest logs and matching RISC-V artifacts into a deterministic "
            "static candidate report. Inputs are never modified or executed."
        )
    )
    parser.add_argument("--log", action="append", required=True, metavar="PATH", help="difftest/simulator log; repeatable")
    parser.add_argument("--elf", metavar="PATH", help="matching RISC-V ELF (inspected with readelf)")
    parser.add_argument("--bin", dest="binary", metavar="PATH", help="matching raw program image; requires --base")
    parser.add_argument("--disasm", metavar="PATH", help="matching RISC-V objdump text; preferred when supplied")
    parser.add_argument("--base", type=lambda value: parse_integer(value, "--base"), help="raw BIN load base")
    parser.add_argument(
        "--pc",
        action="append",
        default=[],
        type=lambda value: parse_integer(value, "--pc"),
        help="explicit anchor PC; repeatable; when present, replaces automatic anchor selection",
    )
    parser.add_argument("--taint", action="append", default=[], type=parse_taint, help="taint root, e.g. reg:a0, csr:mcause, mem:0x..., control:pc")
    parser.add_argument("--before", type=lambda value: nonnegative_count(value, "--before", MAX_CONTEXT_LINES), default=8, help="context lines/instructions before an anchor (default: 8)")
    parser.add_argument("--after", type=lambda value: nonnegative_count(value, "--after", MAX_CONTEXT_LINES), default=8, help="context lines/instructions after an anchor (default: 8)")
    parser.add_argument("--slice-depth", type=lambda value: nonnegative_count(value, "--slice-depth", MAX_SLICE_DEPTH), default=8, help="maximum static backward register-slice depth (default: 8)")
    parser.add_argument("--out", required=True, metavar="DIR", help="new output directory; existing paths are never overwritten")
    args = parser.parse_args(argv)
    if args.base is not None and args.base < 0:
        parser.error("--base cannot be negative")
    if any(pc < 0 for pc in args.pc):
        parser.error("--pc cannot be negative")
    if args.binary and args.base is None:
        parser.error("a raw --bin requires --base, for example --base 0x80000000")
    return args


def write_exclusive(path: Path, content: str) -> None:
    try:
        with path.open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(content)
    except FileExistsError as exc:
        raise AnalysisError(f"refusing to overwrite existing output file: {path}") from exc
    except OSError as exc:
        raise AnalysisError(f"cannot write output file {path}: {exc}") from exc


def create_output_directory(output_path: Path) -> None:
    """Create parents one-by-one without following symlink path components."""
    missing: list[Path] = []
    cursor = output_path.parent
    while not os.path.lexists(cursor):
        missing.append(cursor)
        if cursor.parent == cursor:
            break
        cursor = cursor.parent
    if os.path.lexists(cursor):
        try:
            mode = os.lstat(cursor).st_mode
        except OSError as exc:
            raise AnalysisError(f"cannot inspect output parent {cursor}: {exc}") from exc
        if stat.S_ISLNK(mode):
            raise AnalysisError(f"refusing symlink output parent: {cursor}")
        if not stat.S_ISDIR(mode):
            raise AnalysisError(f"output parent is not a directory: {cursor}")
    for directory in reversed(missing):
        try:
            directory.mkdir(mode=0o755)
        except FileExistsError as exc:
            raise AnalysisError(f"output parent appeared during analysis: {directory}") from exc
        except OSError as exc:
            raise AnalysisError(f"cannot create output parent {directory}: {exc}") from exc
    try:
        output_path.mkdir(mode=0o755, exist_ok=False)
    except FileExistsError as exc:
        raise AnalysisError(f"refusing to overwrite existing output path: {output_path}") from exc
    except OSError as exc:
        raise AnalysisError(f"cannot create output directory {output_path}: {exc}") from exc


def analyze(args: argparse.Namespace) -> tuple[dict[str, Any], dict[str, str]]:
    log_paths = [absolute_input_path(path, f"log[{index}]") for index, path in enumerate(args.log)]
    elf_path = absolute_input_path(args.elf, "ELF") if args.elf else None
    bin_path = absolute_input_path(args.binary, "BIN") if args.binary else None
    disasm_path = absolute_input_path(args.disasm, "disassembly") if args.disasm else None

    artifacts: list[dict[str, Any]] = []
    for index, path in enumerate(log_paths):
        artifacts.append(artifact_identity(path, f"log[{index}]"))
    if elf_path:
        artifacts.append(artifact_identity(elf_path, "elf"))
    if bin_path:
        artifacts.append(artifact_identity(bin_path, "bin"))
    if disasm_path:
        artifacts.append(artifact_identity(disasm_path, "disasm"))

    elf_inspection_internal = inspect_elf(elf_path) if elf_path else None
    generated_disassembly: Optional[dict[str, Any]] = None
    if disasm_path:
        disassembly_text = read_text(disasm_path, "disassembly")
        disassembly_source = str(disasm_path)
    elif elf_path or bin_path:
        generated_disassembly = generate_disassembly(elf_path, bin_path, args.base)
        disassembly_text = generated_disassembly["text"]
        disassembly_source = generated_disassembly["source"]["kind"] + ":" + generated_disassembly["source"]["path"]
    else:
        disassembly_text = ""
        disassembly_source = "none"
    disassembly = parse_disassembly(disassembly_text, disassembly_source)
    instructions = disassembly["instructions"]
    by_pc = index_by_pc(instructions)

    parsed_logs: list[dict[str, Any]] = []
    raw_log_lines: dict[str, list[str]] = {}
    for path in log_paths:
        parsed, raw_lines = parse_log(path)
        parsed_logs.append(parsed)
        raw_log_lines[str(path)] = raw_lines

    commit_correlations = correlate_commit_writers(parsed_logs)
    anchors = build_anchors(args.pc, parsed_logs, commit_correlations, args.before, args.after)
    user_register_taints = unique_in_order(root["target"] for root in args.taint if root["kind"] == "reg")
    nonregister_taints = [root for root in args.taint if root["kind"] != "reg"]
    nonregister_taint_results = analyze_nonregister_taints(
        nonregister_taints, parsed_logs, raw_log_lines
    )
    for anchor in anchors:
        attach_log_contexts(anchor, parsed_logs, raw_log_lines, args.before, args.after)
        match_indexes = attach_disassembly_context(anchor, instructions, args.before, args.after)
        anchor["control_flow_candidates"] = [
            control_flow_candidates(instructions, index, by_pc) for index in match_indexes
        ]
        auto_registers = unique_in_order(
            [
                source["mismatching_register"]
                for source in anchor["sources"]
                if source.get("mismatching_register")
            ]
            + mismatch_registers_for_anchor(anchor, parsed_logs)
        )
        slices = []
        for index in match_indexes:
            seed_registers = user_register_taints or auto_registers
            if not seed_registers and not args.taint:
                seed_registers = instructions[index]["def_use"]["definitions"]
                if seed_registers:
                    anchor["ambiguities"].append(
                        "no register taint root was supplied or parsed; anchor destination registers were used as fallback seeds"
                    )
            if seed_registers:
                slices.append(backward_register_slice(instructions, index, seed_registers, args.slice_depth))
        anchor["slices"] = slices
        anchor["nonregister_taints"] = nonregister_taints
        anchor["byte_checks"] = anchor_byte_checks(
            anchor,
            instructions,
            match_indexes,
            bin_path,
            args.base,
            elf_path,
            elf_inspection_internal["summary"] if elf_inspection_internal else None,
        )
        if anchor["byte_checks"]["consistent_common_prefix"] is False:
            anchor["ambiguities"].append(
                "available ELF/BIN/disassembly bytes disagree at this PC; resolve image identity before causal analysis"
            )
        mapping = anchor["byte_checks"].get("executable_mapping")
        if mapping is not None and not mapping["in_executable_segment"]:
            anchor["ambiguities"].append(
                "this PC is outside the ELF executable LOAD segment; it may be a reporter/harness/relocation artifact rather than an instruction address"
            )
        anchor["ambiguities"] = unique_in_order(anchor["ambiguities"])
        del anchor["pc_value"]

    elf_inspection_public = None
    if elf_inspection_internal:
        elf_inspection_public = {
            "tool": elf_inspection_internal["tool"],
            "argv": elf_inspection_internal["argv"],
            "stderr": elf_inspection_internal["stderr"],
            "summary": elf_inspection_internal["summary"],
            "full_output_file": "elf-readelf.txt",
        }
    disassembly_public = {
        "source": disassembly["source"],
        "line_count": disassembly["line_count"],
        "sections": disassembly["sections"],
        "functions": disassembly["functions"],
        "instructions": [public_instruction(instruction) for instruction in instructions],
        "generated": generated_disassembly is not None,
    }
    if generated_disassembly:
        disassembly_public["generation"] = {
            "tool": generated_disassembly["tool"],
            "argv": generated_disassembly["argv"],
            "stderr": generated_disassembly["stderr"],
            "source": generated_disassembly["source"],
            "full_output_file": "generated-disassembly.txt",
        }
    global_ambiguities = [
        "a logged reporter PC is not automatically the first divergent or root-cause instruction",
        "right/wrong/DUT/reference labels are preserved without assuming which side owns the bug",
        "static address adjacency and reaching definitions do not establish dynamic instruction, control, or data flow",
        "memory aliases/versions, calls, traps, interrupts, privilege state, replay, flush, and self-modifying code require dynamic evidence",
    ]
    if elf_inspection_internal:
        machine = elf_inspection_internal["summary"].get("header", {}).get("machine", "")
        if machine and "risc-v" not in machine.lower():
            global_ambiguities.append(
                f"ELF machine is {machine!r}, not RISC-V; RISC-V def/use conclusions are not applicable until artifact identity is corrected"
            )
    case = {
        "schema_version": SCHEMA_VERSION,
        "status": "Unresolved",
        "analysis_scope": "static candidate enumeration only; no supplied workload was executed",
        "parameters": {
            "explicit_pcs": [hex_address(pc) for pc in args.pc],
            "taint_roots": args.taint,
            "base": hex_address(args.base) if args.base is not None else None,
            "before": args.before,
            "after": args.after,
            "slice_depth": args.slice_depth,
        },
        "artifacts": artifacts,
        "elf_inspection": elf_inspection_public,
        "logs": parsed_logs,
        "commit_window_correlations": commit_correlations,
        "nonregister_taint_results": nonregister_taint_results,
        "disassembly": disassembly_public,
        "anchors": anchors,
        "global_ambiguities": global_ambiguities,
    }
    auxiliary: dict[str, str] = {}
    if elf_inspection_internal:
        auxiliary["elf-readelf.txt"] = elf_inspection_internal["text"]
    if generated_disassembly:
        auxiliary["generated-disassembly.txt"] = generated_disassembly["text"]
    return case, auxiliary


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_arguments(argv)
    output_path = Path(os.path.abspath(os.path.expanduser(args.out)))
    if os.path.lexists(output_path):
        print(f"error: refusing to overwrite existing output path: {output_path}", file=sys.stderr)
        return 2
    try:
        case, auxiliary = analyze(args)
        # Re-hash every supplied artifact immediately before publishing.  This
        # detects inputs that changed while readelf/objdump/parsing was running.
        for recorded in case["artifacts"]:
            current = artifact_identity(Path(recorded["path"]), recorded["role"])
            if current != recorded:
                raise AnalysisError(
                    f"input changed during analysis; refusing to publish mixed evidence: {recorded['path']}"
                )
        case_json = json.dumps(case, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
        case_markdown = render_markdown(case)
        create_output_directory(output_path)
        write_exclusive(output_path / "case.json", case_json)
        write_exclusive(output_path / "case.md", case_markdown)
        for name in sorted(auxiliary):
            write_exclusive(output_path / name, auxiliary[name])
    except AnalysisError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    print(f"wrote static candidate report: {output_path / 'case.md'}")
    print(f"wrote machine-readable evidence: {output_path / 'case.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
