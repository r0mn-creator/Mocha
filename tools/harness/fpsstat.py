#!/usr/bin/env python3
"""Summarise a MOCHAFPS logcat capture as a distribution, not a single number.

usage: fpsstat.py <capture.txt> [<capture2.txt> ...]
Each capture is logcat output containing lines like:
    MOCHAFPS fps=14.93 draws=1204 fastdraws=0
"""
import re, sys, statistics as st

PAT = re.compile(r'MOCHAFPS fps=([\d.]+) draws=(\d+) fastdraws=(\d+)')

def load(path):
    fps, draws = [], []
    for line in open(path, errors='replace'):
        m = PAT.search(line)
        if m:
            fps.append(float(m.group(1)))
            draws.append(int(m.group(2)))
    return fps, draws

def summarise(tag, fps, draws):
    if not fps:
        print(f'{tag}: NO SAMPLES - was debug.mocha.fpslog set to 1?')
        return None
    fps_s = sorted(fps)
    n = len(fps_s)
    def pct(p):
        return fps_s[min(n - 1, int(n * p / 100))]
    print(f'{tag}')
    print(f'  samples : {n}')
    print(f'  mean    : {st.mean(fps):6.2f}   median {st.median(fps):6.2f}   stdev {(st.pstdev(fps)):5.2f}')
    print(f'  p1/p5   : {pct(1):6.2f} / {pct(5):6.2f}      (worst frames - what stutter feels like)')
    print(f'  p50/p95 : {pct(50):6.2f} / {pct(95):6.2f}')
    print(f'  min/max : {min(fps):6.2f} / {max(fps):6.2f}')
    print(f'  >=30fps : {sum(1 for f in fps if f >= 30.0) / n * 100:5.1f}% of samples')
    print(f'  >=25fps : {sum(1 for f in fps if f >= 25.0) / n * 100:5.1f}% of samples')
    if draws:
        print(f'  draws   : mean {st.mean(draws):.0f}  median {st.median(draws):.0f}')
    return st.mean(fps)

means = []
for p in sys.argv[1:]:
    m = summarise(p, *load(p))
    means.append((p, m))
    print()

ok = [(p, m) for p, m in means if m]
if len(ok) == 2:
    (pa, a), (pb, b) = ok
    print(f'CHANGE: {pa} -> {pb}: {a:.2f} -> {b:.2f} fps  ({(b - a) / a * 100:+.1f}%)')
