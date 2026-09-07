#!/usr/bin/env python3
"""Diff two JIT-verifier traces and report the first divergence.

Record layout (32 bytes, little endian):
    u64 seq, u64 rollingHash, u32 hleFuncId, u32 lr
Each record is a checkpoint every 1024 HLE calls; rollingHash accumulates EVERY call, so a
divergence anywhere shows up at the next checkpoint.
"""
import struct, sys, os

REC = struct.Struct('<QQII')

def load(path):
    n = os.path.getsize(path) // REC.size
    out = []
    with open(path, 'rb') as f:
        for _ in range(n):
            out.append(REC.unpack(f.read(REC.size)))
    return out

def main(a_path, b_path):
    a, b = load(a_path), load(b_path)
    print(f'run A ({os.path.basename(a_path)}): {len(a):,} records')
    print(f'run B ({os.path.basename(b_path)}): {len(b):,} records')

    n = min(len(a), len(b))
    first = None
    for i in range(n):
        # compare everything except seq
        if a[i][1:] != b[i][1:]:
            first = i
            break

    if first is None:
        if len(a) == len(b):
            print('\nNo divergence: traces are identical.')
        else:
            print(f'\nNo divergence in the common prefix; traces differ only in length '
                  f'(A={len(a):,} B={len(b):,}). The shorter run stopped earlier.')
        return

    print(f'\n*** FIRST DIVERGENCE at record {first:,} ***\n')

    def show(rec, tag):
        seq, h, fid, lr = rec
        print(f'  {tag}: at HLE call #{seq:,}  rollingHash=0x{h:016x} hleFuncId=0x{fid:04x} LR=0x{lr:08x}')

    show(a[first], 'A(jit)')
    show(b[first], 'B(int)')

    da = a[first]; db = b[first]
    diffs = []
    if da[2] != db[2]: diffs.append('hleFuncId (a DIFFERENT HLE function was called - control flow diverged)')
    if da[3] != db[3]: diffs.append('LR (call site differs)')
    if da[1] != db[1]: diffs.append('rollingHash (guest state differs)')
    print('\n  differing fields: ' + ', '.join(diffs))
    lo = a[first-1][0] if first > 0 else 0
    hi = da[0]
    print(f'\n  => divergence occurred somewhere in HLE calls {lo:,} .. {hi:,}')

    print('\n  --- last 8 matching checkpoints before the divergence ---')
    for i in range(max(0, first - 8), first):
        seq, h, fid, lr = a[i]
        print(f'    checkpoint@call#{seq:<10} hleFuncId=0x{fid:04x} LR=0x{lr:08x}')

if __name__ == '__main__':
    if len(sys.argv) != 3:
        print('usage: difftrace.py <traceA.bin> <traceB.bin>')
        sys.exit(1)
    main(sys.argv[1], sys.argv[2])
