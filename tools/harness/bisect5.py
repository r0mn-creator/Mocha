#!/usr/bin/env python3
"""Red-bisect ONLY the shaders that actually run in this scene (138 of 278), excluding the
scene/post passes already identified as full-screen floods.

Signal is the DIFFERENTIAL: a tree-painting shader makes the canopy red while the road stays
normal. A flood makes both red, which reads as ~0 differential and is useless - hence the
exclusions.
"""
import os, re, shutil, sys
S = os.path.dirname(os.path.abspath(__file__))
SC = f'{S}/scene_shaders'

# known scene / post / flood passes (verified: zeroing them blacks the frame, or red floods all)
EXCL = {
 '0e0deb6416f308c9_00000000003ca249',  # LUT grade
 '99dcf5f90060b88b_00000000003c9449',  # DOF+overlay+LUT sibling
 '4321153fba71b1a6_0000000000000079',  # 9-tap 2D blur
 '3fd981bc68e8a9ae_00000000000003c9', '3e0ecdbe62f5b0e8_00000000000003c9',
 '570a59bc2be49c1a_0000000000000079', 'd673db20846940b5_0000000000000079',
 '48bb0d729dae4b7e_0000000000000079', '5a7f0b66e9ec5689_0000000000000079',
 'daaad9a8661ed757_0000000000000079',
 '0ddb2648868961b9_000000000000001f', '10000004a2293c4d_000000000000000f',
 '054e33cd8fa5d3d6_c000000000000003', '15a5e3fc50dde9a9_000000000000000f',
 '466d12c0383bcbce_000000000000007b', 'ba9907a6e8f95531_000000000000007b',  # cubemap convolution
 '39fc1961106f4ba9_0000000000001e49',  # full-screen flood
 '3c4b5718e0e71f65_000000000000000f',  # full-screen flood (aux 0xf, same family as the others)
}

def candidates():
    used = [l.strip() for l in open(f'{S}/used_shaders.txt') if l.strip()]
    return [u for u in used if u not in EXCL]

def build(names):
    d = f'{S}/graphicPacks/NFS_B5'
    shutil.rmtree(d, ignore_errors=True); os.makedirs(d)
    open(f'{d}/rules.txt','w').write('[Definition]\ntitleIds = 0005000010128800\nname = "B5"\n'
        'path = "Need for Speed Most Wanted U/Graphics/B5"\ndescription = tree hunt\nversion = 6\n')
    n = 0
    for b in names:
        p = f'{SC}/{b}_ps.txt'
        if not os.path.exists(p): continue
        src = open(p, errors='replace').read()
        outs = sorted(set(re.findall(r'out vec4 passPixelColor(\d)', src)))
        m = list(re.finditer(r'passPixelColor\d\s*=\s*vec4\([^;]*\);', src))
        if not m or not outs: continue
        last = m[-1]
        inject = last.group(0) + '\n' + ''.join(
            f'passPixelColor{o} = vec4(4.0,0.0,0.0,1.0);\n' for o in outs)
        open(f'{d}/{b}_ps.txt','w').write(src[:last.start()] + inject + src[last.end():])
        n += 1
    return n

if __name__ == '__main__':
    c = candidates()
    if sys.argv[1] == '--list':
        print(f'{len(c)} used, non-excluded shaders')
        for i,b in enumerate(c): print(f'{i:4d} {b}')
        sys.exit(0)
    lo, hi = int(sys.argv[1]), int(sys.argv[2])
    print(f'total={len(c)} slice=[{lo}:{hi}] patched={build(c[lo:hi])}')
