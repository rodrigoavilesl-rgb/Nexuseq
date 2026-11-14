#!/usr/bin/env python3
"""
build_muldjord.py — Arma un kit estéreo para NEXUS//DRUM a partir del Muldjord Kit
(versión DrumGizmo/SFZ de sfzinstruments, CC BY 4.0, Lars Muldjord).

El kit original trae un archivo MONO por micrófono por golpe
(Samples/<Instrumento>/<N>-<Instrumento>-<Mic>.flac). Este script:
  - mezcla overheads (L/R), ambiente y micrófono cercano en un estéreo,
  - ordena los golpes por potencia real (no confía en la numeración),
  - elige N capas de velocidad x RR variaciones,
  - recorta el silencio inicial (menor latencia) y la cola, con fundido,
  - escribe FLAC 16 bit + kit.json.

USO:
  python3 build_muldjord.py RUTA/DrumGizmo/MuldjordKit/Samples -o ../kits/muldjord
Requiere: python3, numpy, ffmpeg.
"""
import argparse, json, os, re, subprocess, sys
import numpy as np

SR = 44100
# clave app -> (carpetas fuente, mic cercano o None, pesos: close, oh, amb, dur s, fade s)
MAP = {
    'kick':       (['KdrumL', 'KdrumR'], 'SELF',        1.0, .35, .35, 1.0, .12),
    'snare':      (['Snare'],            'Snare_top',   1.0, .60, .40, .9,  .12),
    'snare:rim':  (['SnareRest'],        'Snare_top',   1.0, .60, .40, .6,  .10),
    'tom1':       (['Tom1'],             'Tom1',        1.0, .60, .40, 1.6, .25),
    'tom2':       (['Tom2'],             'Tom2',        1.0, .60, .40, 1.8, .25),
    'floor':      (['Tom4'],             'Tom4',        1.0, .60, .40, 2.2, .30),
    'hat:closed': (['HihatClosed'],      'Hihat',       .9,  .70, .30, .45, .05),
    'hat:open':   (['HihatOpen'],        'Hihat',       .9,  .70, .30, 2.4, .40),
    'crash':      (['CrashL'],           None,          0,   1.0, .55, 4.8, .70),
    'crash2':     (['CrashR'],           None,          0,   1.0, .55, 4.8, .70),
    'splash':     (['China'],            None,          0,   1.0, .55, 3.0, .50),
    'ride':       (['RideR'],            'RideR',       .45, .90, .40, 3.4, .50),
    'ride:bell':  (['RideRBell'],        'RideR',       .45, .90, .40, 2.8, .40),
}


def load(path):
    out = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'],
                         capture_output=True, check=True).stdout
    return np.frombuffer(out, dtype='<f4').copy()


def pad(a, n):
    return np.pad(a, (0, max(0, n - len(a))))[:n]


def mix_hit(d, instr, n, close, wc, woh, wamb):
    f = lambda mic: os.path.join(d, instr, f'{n}-{instr}-{mic}.flac')
    ex = lambda mic: os.path.exists(f(mic))
    ohl, ohr = load(f('OHL')), load(f('OHR'))
    N = max(len(ohl), len(ohr))
    L = woh * pad(ohl, N)
    R = woh * pad(ohr, N)
    if ex('AmbL') and ex('AmbR'):
        L += wamb * pad(load(f('AmbL')), N); R += wamb * pad(load(f('AmbR')), N)
    if close:
        cm = instr if close == 'SELF' else close
        if ex(cm):
            c = pad(load(f(cm)), N) * wc * .7
            L += c; R += c
        if instr == 'Snare' and ex('Snare_bottom'):
            b = pad(load(f('Snare_bottom')), N)
            top = pad(load(f('Snare_top')), N)
            sign = -1.0 if float(np.dot(top[:4000], b[:4000])) < 0 else 1.0   # corrige fase del micro inferior
            b = b * sign * .35 * .7
            L += b; R += b
    return np.stack([L, R], 1)


def encode(path, a):
    a = np.clip(a, -1, 1).astype('<f4')
    subprocess.run(['ffmpeg', '-y', '-v', 'error', '-f', 'f32le', '-ar', str(SR), '-ac', '2', '-i', '-',
                    '-c:a', 'flac', '-sample_fmt', 's16', path], input=a.tobytes(), check=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('samples'); ap.add_argument('-o', '--out', default='kit_out')
    ap.add_argument('--layers', type=int, default=4); ap.add_argument('--rr', type=int, default=2)
    a = ap.parse_args()
    os.makedirs(os.path.join(a.out, 'samples'), exist_ok=True)
    pieces = {}
    for key, (dirs, close, wc, woh, wamb, dur, fade) in MAP.items():
        hits = []
        for d in dirs:
            ns = sorted({int(re.match(r'(\d+)-', f)[1]) for f in os.listdir(os.path.join(a.samples, d)) if f.endswith('OHL.flac')})
            for n in ns:
                m = mix_hit(a.samples, d, n, close, wc, woh, wamb)
                hits.append(m)
        if not hits:
            print('  - sin datos:', key); continue
        # potencia de ataque y orden
        pw = [float(np.sqrt(np.mean(m[:int(.25 * SR)] ** 2))) for m in hits]
        order = np.argsort(pw)
        peak = max(float(np.abs(m).max()) for m in hits)
        gain = .89 / peak
        L = min(a.layers, len(hits))
        layers = []
        for g in range(L):
            lo, hi = round(g * len(hits) / L), round((g + 1) * len(hits) / L)
            grp = order[lo:hi]
            mid = len(grp) // 2
            pick = grp[max(0, mid - a.rr // 2): max(0, mid - a.rr // 2) + a.rr]
            files = []
            for ri, hi_idx in enumerate(pick):
                m = hits[hi_idx] * gain
                # recorta silencio inicial (pre-roll 1.5 ms) y cola con fundido
                env = np.abs(m).max(1)
                on = int(np.argmax(env > .02 * env.max()))
                m = m[max(0, on - 64):]
                nn = int(dur * SR)
                m = m[:nn]
                fn = min(len(m), int(fade * SR))
                if len(m) >= nn - 1:
                    m[-fn:] *= np.linspace(1, 0, fn)[:, None]
                name = f"{key.replace(':', '_')}_L{g+1}_{ri+1}.flac"
                encode(os.path.join(a.out, 'samples', name), m)
                files.append('samples/' + name)
            layers.append({'max': 1.0 if g == L - 1 else round((g + 1) / L, 3), 'files': files})
        pieces[key] = {'layers': layers}
        print(f'  + {key:11s} {len(hits):3d} golpes -> {len(layers)} capas x {len(layers[0]["files"])}')
    json.dump({'name': 'Muldjord Kit (CC BY 4.0, Lars Muldjord)', 'pieces': pieces},
              open(os.path.join(a.out, 'kit.json'), 'w'), indent=1)
    open(os.path.join(a.out, 'CREDITS.txt'), 'w').write(
        'Muldjord Kit - grabado por Lars Muldjord (www.muldjord.com), mapeo SFZ por kinwie.\n'
        'Licencia CC BY 4.0: https://creativecommons.org/licenses/by/4.0/\n'
        'Fuente: https://github.com/sfzinstruments/DrumGizmo.MuldjordKit\n'
        'Modificado: mezcla estéreo de micrófonos, recorte y reducción de capas para NEXUS//DRUM.\n')
    tot = sum(os.path.getsize(os.path.join(a.out, 'samples', f)) for f in os.listdir(os.path.join(a.out, 'samples')))
    print(f'\nListo: {tot/1e6:.1f} MB en {a.out}')


if __name__ == '__main__':
    main()
