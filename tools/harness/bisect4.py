#!/usr/bin/env python3
"""Bisect ALL 278 dumped pixel shaders to find the one painting the blown foliage.

Why this supersedes the earlier category-based attempts: forcing all 278 red turned the whole
frame red INCLUDING the tree, so the culprit is definitely in this set. The category subsets
(multi-output / geometry / full-screen) only ever got 133 of 138 shaders applied between them,
and the culprit was in the gap.

Marker is RED on every output, injected after the LAST write to ANY output (writes to
passPixelColor1..3 come after colour0 and would otherwise overwrite the marker).
"""
import os, re, glob, shutil, sys

S = os.path.dirname(os.path.abspath(__file__))
SC = f'{S}/scene_shaders'

def candidates():
    return sorted(os.path.basename(p) for p in glob.glob(f'{SC}/*_ps.txt'))

def build(names, tag='NFS_B4'):
    d = f'{S}/graphicPacks/{tag}'
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    open(f'{d}/rules.txt', 'w').write(
        '[Definition]\ntitleIds = 0005000010128800\nname = "B4"\n'
        'path = "Need for Speed Most Wanted U/Graphics/B4"\n'
        'description = foliage bisect\nversion = 6\n')
    n = 0
    for b in names:
        src = open(f'{SC}/{b}', errors='replace').read()
        outs = sorted(set(re.findall(r'out vec4 passPixelColor(\d)', src)))
        m = list(re.finditer(r'passPixelColor\d\s*=\s*vec4\([^;]*\);', src))
        if not m or not outs:
            continue
        last = m[-1]
        mk = os.environ.get('MARKER','red')
        if mk == 'clamp':
            inject = last.group(0) + '\n' + ''.join(
                f'passPixelColor{o}.rgb = min(passPixelColor{o}.rgb, vec3(0.6));\n' for o in outs)
        else:
            inject = last.group(0) + '\n' + ''.join(
                f'passPixelColor{o} = vec4(4.0,0.0,0.0,1.0);\n' for o in outs)
        open(f'{d}/{b}', 'w').write(src[:last.start()] + inject + src[last.end():])
        n += 1
    return n

if __name__ == '__main__':
    c = candidates()
    if sys.argv[1] == '--list':
        print(f'{len(c)} pixel shaders')
        for i, b in enumerate(c):
            print(f'{i:4d} {b}')
        sys.exit(0)
    lo, hi = int(sys.argv[1]), int(sys.argv[2])
    ex = set(int(x) for x in os.environ.get('EXCLUDE','').split(',') if x.strip())
    sel = [b for i,b in enumerate(c) if lo <= i < hi and i not in ex]
    print(f'total={len(c)} slice=[{lo}:{hi}] excluded={sorted(ex)} patched={build(sel)}')
