# Mocha dev tooling

Rescued out of a session scratchpad — these were the instruments behind every
measured result in this fork. Kept here so they survive.

## graphicPacks/

Copy onto the device's graphic-pack folder, then enable in Settings.

| pack | what it does |
| --- | --- |
| **`NFS_MemFix`** | **Ships.** Adds the RAM mapping NFS MW U expects but Cemu never maps (`0x00010000-0x00200000`). Took boot success from ~60-70% to **10/10**. The crash address `0x0012a9d0` sat in an unmapped gap, not in corrupted heap. |
| **`NFS_Tonemap_W1`** | **Ships.** Hue-preserving Reinhard tone-map applied *before* the colour LUT lookup in `0e0deb6416f308c9`. `treeBlown 63.3% → 0.00%`, canopy greenness `+1.34 → +5.97`. Beats stock desktop Cemu, which blows out identically. |
| `NFS_Tonemap_W15` / `W2` | Same fix, higher white points. W2 keeps more contrast, less green. |
| `NFS_Foliage_Knee`, `Clamp06/08` | **Superseded.** Clamp *after* the LUT — removes blowout but leaves the canopy grey, because hue is already destroyed by then. Kept only as reference. |

⚠️ Tone-map channel order is scrambled: `x←R123f.y, y←R123f.x, z←R123f.w`.

## harness/

| script | use |
| --- | --- |
| `launchtest.sh <sthmode> <attempts> [wait]` | Launch-reliability harness via `am start` intent. Counts OK/CRASH/NO-BOOT, pulls `log.txt` each run (it truncates every launch). ~35s/attempt. |
| `taptest.sh` | Same, but through the **real user path** (open app, tap the game tile). Both paths measured equivalent. |
| `packtest.sh` | Apply a graphic pack and capture the result at the save point. |
| `fpsrun.sh` + `fpsstat.py` | `MOCHAFPS` trace capture and statistics. |
| `bisect4.py` / `b4run.sh` | Full-set shader bisection with `MARKER=clamp\|red`, `EXCLUDE=`. |
| `bisect5.py` / `b5run.sh` | Bisect only shaders actually used in-scene. |
| `difftrace.py` | Diff two JIT-vs-interpreter traces from the differential verifier. |

### Gotchas that cost real time

- **Don't pass `--grant-read-uri-permission`** to `am start` — adb shell (uid 2000)
  doesn't own the URI, throws SecurityException. Without it the app resolves via
  its own persisted SAF grant. Game URI lives in `files/title_list_cache.xml`.
- **`log.txt` truncates on every launch** — pull it before relaunching.
- **RED marking is useless on this deferred renderer** — a full-screen pass painted
  red floods the frame and masks the signal. Use CLAMP.
- **Bisect the full set; don't pre-filter by category** — category subsets missed
  the culprit by leaving a 5-shader gap.
- **Contrast/frame-mean are confounded by time-of-day drift.** Only `treeBlown` and
  the channel-difference greenness metric are trustworthy run-to-run.

## ../device_backup/

Controller profile, `settings.xml`, and the NFS save points — including the parked
Downtown save every foliage measurement was calibrated against. `restore_input.sh`
puts the input config back. Restore these before trying to reproduce any prior number.
