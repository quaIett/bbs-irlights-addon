"""Generate anchored shader patches from pristine/working trees (no third-party file replacement)."""
from pathlib import Path
import argparse
import difflib
import re

REPO = Path(__file__).resolve().parent.parent
PACKS = {'Solas': 'solas', 'BSL': 'bsl', 'RethinkingVoxels': 'rethinkingvoxels',
         'Bliss': 'bliss', 'IterationRP': 'iterationrp', 'Photon': 'photon'}
TEXT = {'.glsl', '.fsh', '.vsh', '.gsh', '.csh', '.properties', '.lang', '.txt', '.json'}


def body(text):
    return '<<<\n' + text + '\n>>>\n'


def quoted(text):
    return '"' + text.replace('\\', '\\\\').replace('"', '\\"').replace('\t', '\\t').replace('\n', '\\n') + '"'


def generate(pack):
    original = REPO / 'Shadres/Original' / pack
    modified = REPO / 'Shadres/Modification' / pack
    output = REPO / 'patches' / (PACKS[pack] + '.irlights')
    old_patch = output.read_text(encoding='utf-8-sig')
    header = '\n'.join(re.findall(r'^@(?:name|target|packversion|irlite|marker)\s+.*', old_patch, re.M))
    parts = ['# IRLights: surface, per-light profiles, independent Lit/Outline Replays.\n', header + '\n\n']
    count = 0
    for path in sorted((modified / 'shaders').rglob('*')):
        if not path.is_file():
            continue
        relative = path.relative_to(modified)
        source = original / relative
        if source.exists() and source.read_bytes() == path.read_bytes():
            continue
        if path.suffix not in TEXT:
            raise ValueError(f'Unsupported binary difference: {relative}')
        new = path.read_text(encoding='utf-8')
        if not source.exists():
            parts += ['+file ' + relative.as_posix() + '\n', body(new), '\n']
            count += 1
            continue
        old = source.read_text(encoding='utf-8')
        if old == new:
            continue
        matcher = difflib.SequenceMatcher(None, old.splitlines(keepends=True), new.splitlines(keepends=True), autojunk=False)
        old_lines, new_lines = old.splitlines(keepends=True), new.splitlines(keepends=True)
        context = 1
        while True:
            edits = []
            for group in matcher.get_grouped_opcodes(context):
                first, last = group[0], group[-1]
                anchor = ''.join(old_lines[first[1]:last[2]])
                replacement = ''.join(new_lines[first[3]:last[4]])
                edits.append((anchor, replacement))
            if all(anchor and old.count(anchor) == 1 for anchor, _ in edits):
                break
            context *= 2
            if context > len(old_lines) + 1:
                raise ValueError(f'Cannot find unique anchors: {relative}')
        parts.append('@file ' + relative.as_posix() + '\n')
        check = old
        for anchor, replacement in reversed(edits):
            if check.count(anchor) != 1:
                raise ValueError(f'Sequential anchor ambiguity: {relative}')
            check = check.replace(anchor, replacement, 1)
            parts += ['replace ' + quoted(anchor) + '\n', body(replacement)]
            count += 1
        if check != new:
            raise ValueError(f'Round-trip mismatch: {relative}')
        parts.append('\n')
    output.write_text(''.join(parts), encoding='utf-8', newline='\n')
    print(f'{pack}: {count} operations -> {output.name}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('packs', nargs='*', choices=list(PACKS))
    args = parser.parse_args()
    for pack in args.packs or PACKS:
        generate(pack)
