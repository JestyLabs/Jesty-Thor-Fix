"""Offline inspector regression; needs local pyelftools/capstone via --deps."""
import argparse
import importlib.util
from pathlib import Path
import struct
import sys
import tempfile
import unittest


def fixture():
    # Two functions have identical offsets in different sections. Relocations
    # must resolve within the selected section, never by numeric offset alone.
    names = ["", ".shstrtab", ".text", ".text.other", ".strtab", ".symtab",
             ".rela.text", ".rela.text.other"]
    shstrings = b"\0"
    name_offsets = [0]
    for name in names[1:]:
        name_offsets.append(len(shstrings))
        shstrings += name.encode() + b"\0"
    strings = b"\0first\0second\0helper_a\0helper_b\0"
    symbol = lambda name, section: struct.pack("<IBBHQQ", name, 0x12, 0, section, 0, 8 if section else 0)
    symbols = bytes(24) + symbol(1, 2) + symbol(7, 3) + symbol(14, 0) + symbol(23, 0)
    instructions = struct.pack("<II", 0x94000000, 0xd65f03c0)
    sections = [
        (0, b"", 0, 0, 0, 0, 0),
        (3, shstrings, 0, 0, 0, 1, 0),
        (1, instructions, 6, 0, 0, 4, 0),
        (1, instructions, 6, 0, 0, 4, 0),
        (3, strings, 0, 0, 0, 1, 0),
        (2, symbols, 0, 4, 1, 8, 24),
        (4, struct.pack("<QQq", 0, (3 << 32) | 283, 0), 0, 5, 2, 8, 24),
        (4, struct.pack("<QQq", 0, (4 << 32) | 283, 0), 0, 5, 3, 8, 24),
    ]
    output = bytearray(64)
    headers = []
    for index, (kind, data, flags, link, info, alignment, entsize) in enumerate(sections):
        output.extend(bytes((-len(output)) % max(alignment, 1)))
        offset = len(output) if index else 0
        output.extend(data)
        headers.append(struct.pack("<IIQQQQIIQQ", name_offsets[index], kind, flags,
                                   0, offset, len(data), link, info, alignment, entsize))
    output.extend(bytes((-len(output)) % 8))
    shoff = len(output)
    output.extend(b"".join(headers))
    output[:64] = struct.pack("<16sHHIQQQIHHHHHH", b"\x7fELF\x02\x01\x01" + bytes(9),
                              1, 183, 1, 0, 0, shoff, 0, 64, 0, 0, 64, len(sections), 1)
    return output


class ModuleInspectionTest(unittest.TestCase):
    def test_section_specific_function_and_relocations(self):
        with tempfile.TemporaryDirectory(prefix="thor-refresh-elf-test-") as temp:
            path = Path(temp) / "fixture.ko"
            path.write_bytes(fixture())
            first = inspector.disassemble_function(path, "first")
            second = inspector.disassemble_function(path, "second")
            self.assertIn("section=.text; section_index=2; section_offset=0x0", first)
            self.assertIn("section=.text.other; section_index=3; section_offset=0x0", second)
            self.assertIn("R_AARCH64_CALL26 helper_a", first)
            self.assertNotIn("helper_b", first)
            self.assertIn("R_AARCH64_CALL26 helper_b", second)
            self.assertNotIn("helper_a", second)
            self.assertNotEqual(first.split("file_offset=")[1].splitlines()[0],
                                second.split("file_offset=")[1].splitlines()[0])
            with self.assertRaisesRegex(ValueError, "Expected exactly one"):
                inspector.disassemble_function(path, "missing")
            with self.assertRaisesRegex(ValueError, "section offsets"):
                inspector.disassemble(path, 0, 8)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--deps", type=Path)
    args = parser.parse_args()
    if args.deps:
        sys.path.insert(0, str(args.deps.resolve()))
    spec = importlib.util.spec_from_file_location("inspector", Path(__file__).with_name("inspect-thor-refresh-elf.py"))
    inspector = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(inspector)
    unittest.main(argv=[sys.argv[0]])
