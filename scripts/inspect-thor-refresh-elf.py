"""Read local ELFs only. Never executes, patches, uploads or collects a binary.

Requires locally installed pyelftools; --disassemble additionally uses capstone.
--deps can point at an existing offline dependency directory. Output stays local.
--disassemble uses ELF virtual addresses, with file offsets independently.
--function reads a named AArch64 ET_REL module function using section offsets
and relocation annotations, without confusing equal offsets across sections.
"""

import argparse
import hashlib
import io
import json
import lzma
from pathlib import Path
import sys


def symbols(elf):
    result = {}
    sources = [(elf, "elf")]
    mini = elf.get_section_by_name(".gnu_debugdata")
    if mini:
        from elftools.elf.elffile import ELFFile
        sources.append((ELFFile(io.BytesIO(lzma.decompress(mini.data()))), "gnu_debugdata"))
    for source, provenance in sources:
        for section in source.iter_sections():
            if section["sh_type"] not in ("SHT_DYNSYM", "SHT_SYMTAB"):
                continue
            for symbol in section.iter_symbols():
                if symbol.name and symbol["st_value"]:
                    key = (symbol["st_value"], symbol.name)
                    result[key] = {"address": symbol["st_value"], "size": symbol["st_size"],
                                   "name": symbol.name, "source": provenance}
    return sorted(result.values(), key=lambda x: (x["address"], x["name"]))


def identity(path, relative=None, expected=None):
    from elftools.elf.elffile import ELFFile
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    with path.open("rb") as stream:
        elf = ELFFile(stream)
        ids = [note["n_desc"] for section in elf.iter_sections()
               if section["sh_type"] == "SHT_NOTE" for note in section.iter_notes()
               if note["n_type"] == "NT_GNU_BUILD_ID"]
        return {"path": relative or str(path), "bytes": path.stat().st_size,
                "sha256": digest, "buildIds": ids, "elfClass": elf.elfclass,
                "machine": elf["e_machine"], "hasMiniDebug": bool(elf.get_section_by_name(".gnu_debugdata")),
                "manifestMatch": None if expected is None else digest == expected.lower()}


def inventory(root, manifest):
    expected = {}
    if manifest:
        for line in manifest.read_text(encoding="utf-8-sig").splitlines():
            fields = line.split(None, 1)
            if len(fields) == 2 and len(fields[0]) == 64:
                expected[fields[1].strip().lstrip("*")] = fields[0]
    rows, ignored = [], []
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        with path.open("rb") as stream:
            magic = stream.read(4)
        relative = "/" + path.relative_to(root).as_posix()
        if magic != b"\x7fELF":
            ignored.append(relative)
            continue
        rows.append(identity(path, relative, expected.get(relative)))
    present = {row["path"] for row in rows}
    return {"schema": "THOR_REFRESH_ELF_INVENTORY_V1", "files": rows,
            "missingManifestPaths": sorted(set(expected) - present), "ignoredNonElf": ignored,
            "firmwareBinding": "saved manifest only; ELF does not independently authenticate firmware"}


def disassemble(path, start, end):
    from elftools.elf.elffile import ELFFile
    from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN
    with path.open("rb") as stream:
        elf = ELFFile(stream)
        if elf.elfclass != 64 or elf["e_machine"] != "EM_AARCH64" or not elf.little_endian:
            raise ValueError("Disassembly requires a little-endian AArch64 ELF")
        if elf["e_type"] == "ET_REL":
            raise ValueError("ET_REL has section offsets, not linked VAs; use --function NAME")
        segment = next(s for s in elf.iter_segments() if s["p_type"] == "PT_LOAD"
                       and s["p_vaddr"] <= start < end <= s["p_vaddr"] + s["p_filesz"])
        offset = segment["p_offset"] + start - segment["p_vaddr"]
        stream.seek(offset)
        data = stream.read(end - start)
        names = {}
        for symbol in symbols(elf):
            names.setdefault(symbol["address"], []).append(symbol["name"])
        relocations = {}
        section = elf.get_section_by_name(".rela.plt")
        if section:
            dynsym = elf.get_section(section["sh_link"])
            relocations = {r["r_offset"]: dynsym.get_symbol(r["r_info_sym"]).name
                           for r in section.iter_relocations()}
        plt = elf.get_section_by_name(".plt")
        if plt:
            import re
            entry, page = None, None
            for ins in Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN).disasm(plt.data(), plt["sh_addr"]):
                if ins.mnemonic == "adrp" and ins.op_str.startswith("x16, #0x"):
                    entry, page = ins.address, int(ins.op_str.split("#")[1], 16)
                match = re.fullmatch(r"x17, \[x16(?:, #(0x[0-9a-f]+))?\]", ins.op_str)
                if ins.mnemonic == "ldr" and match and page is not None:
                    name = relocations.get(page + int(match[1] or "0", 16))
                    if name:
                        names[entry] = [name + "@plt"]
        lines = [json.dumps(identity(path)), f"VA {start:#x}..{end:#x}; file offset {offset:#x}"]
        for ins in Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN).disasm(data, start):
            annotation = ""
            if ins.mnemonic in ("bl", "b") and ins.op_str.startswith("#0x"):
                annotation = " ; " + " / ".join(names.get(int(ins.op_str[1:], 16), []))
                if annotation == " ; ":
                    annotation = ""
            lines.append(f"{ins.address:08x} file={offset + ins.address - start:08x} "
                         f"{ins.bytes.hex()} {ins.mnemonic:7} {ins.op_str}{annotation}")
        return "\n".join(lines)


def disassemble_function(path, name):
    """Resolve one module function and its relocations within its own section."""
    from elftools.elf.elffile import ELFFile
    from elftools.elf.descriptions import describe_reloc_type
    from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN
    with path.open("rb") as stream:
        elf = ELFFile(stream)
        if (elf["e_type"] != "ET_REL" or elf["e_machine"] != "EM_AARCH64"
                or elf.elfclass != 64 or not elf.little_endian):
            raise ValueError("--function requires a little-endian AArch64 ET_REL module")
        table = elf.get_section_by_name(".symtab")
        candidates = [] if table is None else [s for s in table.get_symbol_by_name(name) or []
            if s["st_info"]["type"] == "STT_FUNC" and isinstance(s["st_shndx"], int)]
        if len(candidates) != 1:
            raise ValueError("Expected exactly one defined function: " + name)
        symbol = candidates[0]
        index, start, size = symbol["st_shndx"], symbol["st_value"], symbol["st_size"]
        section = elf.get_section(index)
        if size <= 0 or start + size > section["sh_size"]:
            raise ValueError("Function has no valid bounded section range")
        annotations = {}
        for reloc_section in elf.iter_sections():
            if reloc_section["sh_type"] != "SHT_RELA" or reloc_section["sh_info"] != index:
                continue
            reloc_symbols = elf.get_section(reloc_section["sh_link"])
            for reloc in reloc_section.iter_relocations():
                offset = reloc["r_offset"]
                if not start <= offset < start + size:
                    continue
                target = reloc_symbols.get_symbol(reloc["r_info_sym"])
                target_section = elf.get_section(target["st_shndx"]) if isinstance(target["st_shndx"], int) else None
                target_name = target.name or (target_section.name if target_section else str(target["st_shndx"]))
                addend = reloc["r_addend"]
                description = (f'{describe_reloc_type(reloc["r_info_type"], elf)} '
                               f'{target_name} addend={addend:#x}')
                if target_section and target_section.name.startswith(".rodata"):
                    position = target["st_value"] + addend
                    if 0 <= position < target_section["sh_size"]:
                        preview = target_section.data()[position:position + 160].split(b"\0", 1)[0]
                        description += " " + repr(preview)
                annotations.setdefault(offset, []).append(description)
        lines = [json.dumps(identity(path)),
                 f"FUNCTION {name}; section={section.name}; section_index={index}; "
                 f"section_offset={start:#x}; size={size:#x}; file_offset={section['sh_offset'] + start:#x}",
                 "Addresses below are section offsets, not runtime addresses or linked VAs."]
        for ins in Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN).disasm(section.data()[start:start + size], start):
            annotation = " ; ".join(annotations.get(ins.address, []))
            lines.append(f"{ins.address:08x} file={section['sh_offset'] + ins.address:08x} "
                         f"{ins.bytes.hex()} {ins.mnemonic:7} {ins.op_str} ; {annotation}")
        return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", type=Path)
    parser.add_argument("--deps", type=Path)
    parser.add_argument("--manifest", type=Path)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--symbols", help="substring; includes embedded .gnu_debugdata symbols")
    mode.add_argument("--disassemble", nargs=2, metavar=("START_VA", "END_VA"))
    mode.add_argument("--function", help="exact function name in an AArch64 ET_REL module")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.deps:
        sys.path.insert(0, str(args.deps.resolve()))
    if args.path.is_dir():
        result = inventory(args.path, args.manifest)
        text = json.dumps(result, indent=2)
        failed = bool(args.manifest) and (bool(result["missingManifestPaths"]) or any(
            row["manifestMatch"] is not True for row in result["files"]))
    elif args.function:
        text, failed = disassemble_function(args.path, args.function), False
    elif args.disassemble:
        text = disassemble(args.path, *(int(x, 0) for x in args.disassemble))
        failed = False
    elif args.symbols:
        from elftools.elf.elffile import ELFFile
        with args.path.open("rb") as stream:
            result = [s for s in symbols(ELFFile(stream)) if args.symbols in s["name"]]
        text, failed = json.dumps(result, indent=2), False
    else:
        text, failed = json.dumps(identity(args.path), indent=2), False
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text + "\n", encoding="utf-8")
    else:
        print(text)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
