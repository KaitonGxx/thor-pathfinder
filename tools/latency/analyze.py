"""Button latency from atrace dumps: kernel event to the app, per press and release.

For every key event inject.sh writes, measures from the moment the kernel
event wakes InputReader to the moment the app starts deliverInputEvent. When
Pathfinder's pid is given, also splits out its hop: system_server handing the
key to Pathfinder, Pathfinder's main thread waking, its onKeyEvent, and the
rest of the way to the app after its answer.

usage: python3 analyze.py <atrace dump>[:<pathfinder pid>] ...
env:   APP=<pid>  count only deliveries in this process (the game)
       SRC=<hex>  input source to count (default 0x501, a gamepad key)
"""
import os
import re
import statistics
import sys
import zlib

APP = os.environ.get('APP')
SRC = os.environ.get('SRC', '0x501')
LINE = re.compile(r'^\s*(.{16})-(\d+)\s+\(\s*([\d-]+)\)\s+\[\d+\]\s+\S+\s+([\d.]+):\s+(\w+):\s+(.*)$')


def load(path):
    data = open(path, 'rb').read()
    if b'TRACE:\n' in data[:200]:
        data = data[data.index(b'TRACE:\n') + 7:]
        try:
            data = zlib.decompress(data)
        except zlib.error:
            pass
    return data.decode(errors='replace')


def events(path, pf):
    senders, presses, cur = set(), [], None
    for line in load(path).splitlines():
        m = LINE.match(line)
        if not m:
            continue
        _, tid, pid, ts, ev, rest = m.groups()
        ts = float(ts) * 1000
        if ev == 'task_rename' and 'newcomm=sendevent' in rest:
            senders.add(re.search(r'pid=(\d+)', rest).group(1))
        elif ev == 'sched_waking' and 'comm=InputReader' in rest and tid in senders:
            # inject.sh writes down, up, down, up...
            cur = {'t0': ts, 'up': len(presses) % 2 == 1}
            presses.append(cur)
        elif cur is None:
            continue
        elif ev == 'binder_transaction' and pf:
            if f'dest_proc={pf} ' in rest and 'code=0x6' in rest and 'reply=0' in rest and 'pf_in' not in cur:
                cur['pf_in'] = ts
            elif pid == pf and tid == pf and 'pf_in' in cur and 'flags=0x11' in rest and 'code=0xf' in rest:
                cur.setdefault('pf_out', ts)
        elif ev == 'sched_switch' and pf and f'next_pid={pf} ' in rest and 'pf_in' in cur:
            cur.setdefault('pf_run', ts)
        elif (ev == 'tracing_mark_write' and f'|deliverInputEvent src={SRC}' in rest
              and 'app' not in cur and (not APP or pid == APP)):
            cur['app'] = ts
    return presses


def stats(name, xs):
    if not xs:
        return
    xs = sorted(xs)
    q = lambda f: xs[min(len(xs) - 1, int(f * len(xs)))]
    print(f'{name:36s} n={len(xs):4d}  median={statistics.median(xs):6.2f}  '
          f'p90={q(.9):6.2f}  p99={q(.99):6.2f}  max={xs[-1]:6.2f} ms')


presses = []
for arg in sys.argv[1:]:
    path, _, pf = arg.partition(':')
    presses += events(path, pf)
ok = [p for p in presses if 'app' in p and p['app'] - p['t0'] < 200]
print(f'events seen {len(presses)}, delivered {len(ok)}')
stats('kernel -> app', [p['app'] - p['t0'] for p in ok])
stats('  presses', [p['app'] - p['t0'] for p in ok if not p['up']])
stats('  releases', [p['app'] - p['t0'] for p in ok if p['up']])
hop = [p for p in ok if 'pf_in' in p and 'pf_run' in p and 'pf_out' in p]
if hop:
    stats('  kernel -> sent to Pathfinder', [p['pf_in'] - p['t0'] for p in hop])
    stats('  Pathfinder main thread wake-up', [p['pf_run'] - p['pf_in'] for p in hop])
    stats('  Pathfinder onKeyEvent', [p['pf_out'] - p['pf_run'] for p in hop])
    stats('  answer -> app', [p['app'] - p['pf_out'] for p in hop])
