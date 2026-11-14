#!/usr/bin/env python3
"""
sfz2nexus.py — Convierte un kit de batería SFZ (ej. Muldjord Kit de FreePats /
sfzinstruments) al formato kit.json de NEXUS//DRUM, recortando y optimizando
los WAV para que pesen poco en el celular.

USO
  1) Inspeccionar el kit (no escribe nada):
       python3 sfz2nexus.py RUTA/MuldjordKit.sfz --inspect
  2) Generar:
       python3 sfz2nexus.py RUTA/MuldjordKit.sfz -o ../kits/muldjord
  3) Servir la app y abrirla:
       cd nexus-drum && python3 -m http.server 8000   ->  http://localhost:8000

OPCIONES
  --layers N    capas de velocidad por pieza (def. 4)
  --rr N        variaciones (round-robin) por capa (def. 2)
  --mic REGEX   conservar solo samples cuya ruta coincida (ej. 'amb|overhead')
  --exclude RE  descartar samples cuya ruta coincida
  --no-trim     copiar los archivos tal cual (sin recortar)

Requiere Python 3.8+. ffmpeg es opcional pero recomendado (acepta FLAC, 24 bit
extensible, etc.). Sin ffmpeg solo procesa WAV PCM con la librería estándar.
"""
import argparse, json, os, re, shutil, subprocess, sys, wave
from array import array
from collections import defaultdict

# ---------- mapeo GM -> clave de NEXUS//DRUM ----------
# claves con ':' son articulaciones; la app cae a la clave base si no existen
PREF = {
    'kick': [36, 35], 'snare': [38, 40], 'snare:rim': [37],
    'hat:closed': [42], 'hat:pedal': [44], 'hat:open': [46],
    'tom1': [50, 48], 'tom2': [47, 45], 'floor': [43, 41],
    'crash': [49], 'crash2': [57, 52], 'ride': [51, 59], 'ride:bell': [53], 'splash': [55],
}
DUR = {  # segundos máximos por pieza (recorte con fundido)
    'kick': 1.0, 'snare': .9, 'snare:rim': .5, 'tom1': 1.6, 'tom2': 1.8, 'floor': 2.2,
    'hat:closed': .4, 'hat:pedal': .3, 'hat:open': 2.4, 'crash': 4.5, 'crash2': 4.5,
    'ride': 3.2, 'ride:bell': 2.6, 'splash': 2.2,
}
NOTE_BASE = {'c': 0, 'd': 2, 'e': 4, 'f': 5, 'g': 7, 'a': 9, 'b': 11}


def note(v):
    v = str(v).strip().lower()
    if re.fullmatch(r'-?\d+', v):
        return int(v)
    m = re.fullmatch(r'([a-g])(#|b)?(-?\d+)', v)
    if not m:
        return None
    n = NOTE_BASE[m[1]] + (1 if m[2] == '#' else -1 if m[2] == 'b' else 0)
    return (int(m[3]) + 1) * 12 + n


# ---------- parser SFZ ----------
def load_text(path):
    txt = open(path, encoding='utf-8', errors='replace').read()
    txt = re.sub(r'/\*.*?\*/', '', txt, flags=re.S)
    txt = re.sub(r'(?<!:)//[^\n]*', '', txt)

    def inc(m):
        p = os.path.join(os.path.dirname(path), m[1].replace('\\', '/'))
        return load_text(p) if os.path.exists(p) else ''
    txt = re.sub(r'#include\s+"([^"]+)"', inc, txt)
    defs = {}

    def d(m):
        defs[m[1]] = m[2].strip()
        return ''
    txt = re.sub(r'#define\s+(\$\w+)\s+([^\n]*)', d, txt)
    for k in sorted(defs, key=len, reverse=True):
        txt = txt.replace(k, defs[k])
    return txt


HDR = re.compile(r'<(\w+)>')
OP = re.compile(r'([A-Za-z_]\w*)=(.*?)(?=\s+[A-Za-z_]\w*=|\s*<|\s*$)', re.S)


def parse(txt):
    parts = HDR.split(txt)
    sc = {'control': {}, 'global': {}, 'master': {}, 'group': {}}
    regions = []
    for i in range(1, len(parts), 2):
        h, ops = parts[i], {k: v.strip() for k, v in OP.findall(parts[i + 1])}
        if h == 'control':
            sc['control'].update(ops)
        elif h == 'global':
            sc['global'], sc['master'], sc['group'] = ops, {}, {}
        elif h == 'master':
            sc['master'], sc['group'] = ops, {}
        elif h == 'group':
            sc['group'] = ops
        elif h == 'region':
            r = {}
            for s in ('global', 'master', 'group'):
                r.update(sc[s])
            r.update(ops)
            r['_dp'] = sc['control'].get('default_path', '')
            regions.append(r)
    return regions


class Resolver:
    def __init__(self, root):
        self.root, self.idx = root, None

    def __call__(self, r):
        p = os.path.normpath(os.path.join(self.root, r['_dp'].replace('\\', '/'), r['sample'].replace('\\', '/')))
        if os.path.exists(p):
            return p
        if self.idx is None:
            self.idx = {}
            for dp, _, fs in os.walk(self.root):
                for f in fs:
                    full = os.path.join(dp, f)
                    self.idx[os.path.relpath(full, self.root).lower()] = full
        return self.idx.get(os.path.relpath(p, self.root).lower())


def collect(regions):
    """nota GM -> lista de regiones (una región cuenta en la primera nota útil de su rango)"""
    useful = {n for v in PREF.values() for n in v}
    out = defaultdict(list)
    for r in regions:
        if 'sample' not in r or r['sample'].startswith('*'):
            continue
        k = note(r['key']) if 'key' in r else None
        lo = k if k is not None else note(r.get('lokey', ''))
        hi = k if k is not None else note(r.get('hikey', ''))
        if lo is None or hi is None:
            continue
        for n in range(lo, hi + 1):
            if n in useful:
                out[n].append(r)
                break
    return out


def inspect(notes, resolve, regions):
    print(f'{len(regions)} regiones; {len(notes)} notas GM relevantes\n')
    cc = sum(1 for r in regions if any(k.startswith(('locc', 'hicc')) for k in r))
    if cc:
        print(f'AVISO: {cc} regiones tienen condiciones CC (ej. apertura del hi-hat). Se ignoran las condiciones.\n')
    for n in sorted(notes):
        regs = notes[n]
        vel = sorted({(int(r.get('lovel', 0)), int(r.get('hivel', 127))) for r in regs})
        keys = [k for k, v in PREF.items() if n in v]
        ex = resolve(regs[0])
        print(f'nota {n:3d} -> {",".join(keys):22s} regiones={len(regs):4d} capas_vel={len(vel):3d}  ej: {os.path.relpath(ex) if ex else "(NO ENCONTRADO) " + regs[0]["sample"]}')
    print('\nSi un mismo golpe aparece con varios micrófonos (Amb, Overhead, Room, In/Out...), usa --mic para quedarte con uno o una mezcla.')


# ---------- selección de capas ----------
def build(regs, resolve, nlayers, nrr, mic, exc):
    byvel = defaultdict(list)
    for i, r in enumerate(regs):
        p = resolve(r)
        if not p or (mic and not re.search(mic, p, re.I)) or (exc and re.search(exc, p, re.I)):
            continue
        key = (int(r.get('lovel', 0)), int(r.get('hivel', 127)))
        byvel[key].append((int(r.get('seq_position', 0)), float(r.get('lorand', 0) or 0), i, p))
    vs = sorted(byvel, key=lambda k: (k[1], k[0]))
    if not vs:
        return []
    n = min(nlayers, len(vs))
    layers = []
    for g in range(n):
        grp = vs[round(g * len(vs) / n):round((g + 1) * len(vs) / n)]
        mid = grp[len(grp) // 2]
        files = []
        for _, _, _, p in sorted(byvel[mid]):
            if p not in files:
                files.append(p)
        if len(files) > nrr:
            files = [files[round(i * (len(files) - 1) / max(nrr - 1, 1))] for i in range(nrr)]
        layers.append({'max': 1.0 if g == n - 1 else round(grp[-1][1] / 127, 3), 'src': files})
    return layers


# ---------- conversión de audio ----------
def convert(src, dst, dur, fade, no_trim, ffmpeg):
    if no_trim:
        shutil.copyfile(src, dst)
        return
    if ffmpeg:
        st = max(0, dur - fade)
        subprocess.run([ffmpeg, '-y', '-v', 'error', '-i', src, '-t', f'{dur}', '-af',
                        f'afade=t=out:st={st}:d={fade}', '-ar', '44100', '-c:a', 'pcm_s16le', dst], check=True)
        return
    with wave.open(src, 'rb') as w:
        ch, sw, sr, n = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
        raw = w.readframes(min(n, int(dur * w.getframerate())))
    if sw == 3:
        b = bytearray(len(raw) // 3 * 2); b[0::2] = raw[1::3]; b[1::2] = raw[2::3]; raw = bytes(b)
    elif sw == 4:
        b = bytearray(len(raw) // 4 * 2); b[0::2] = raw[2::4]; b[1::2] = raw[3::4]; raw = bytes(b)
    elif sw != 2:
        raise RuntimeError(f'WAV de {sw*8} bits no soportado sin ffmpeg')
    a = array('h'); a.frombytes(raw)
    if sys.byteorder == 'big':
        a.byteswap()
    nf = len(a) // ch
    if nf >= int(dur * sr) - 1:   # solo si realmente se cortó el audio
        fn = min(nf, int(fade * sr))
        for i in range(fn):
            g = 1 - i / fn
            for c in range(ch):
                a[(nf - fn + i) * ch + c] = int(a[(nf - fn + i) * ch + c] * g)
    if sys.byteorder == 'big':
        a.byteswap()
    with wave.open(dst, 'wb') as o:
        o.setnchannels(ch); o.setsampwidth(2); o.setframerate(sr); o.writeframes(a.tobytes())


def main():
    ap = argparse.ArgumentParser(description='SFZ -> NEXUS//DRUM kit.json')
    ap.add_argument('sfz'); ap.add_argument('-o', '--out', default='kit_out')
    ap.add_argument('--layers', type=int, default=4); ap.add_argument('--rr', type=int, default=2)
    ap.add_argument('--mic'); ap.add_argument('--exclude'); ap.add_argument('--no-trim', action='store_true')
    ap.add_argument('--inspect', action='store_true'); ap.add_argument('--name', default='Muldjord Kit (CC BY 4.0, Lars Muldjord)')
    a = ap.parse_args()

    root = os.path.dirname(os.path.abspath(a.sfz))
    regions = parse(load_text(a.sfz))
    resolve = Resolver(root)
    notes = collect(regions)
    if not notes:
        sys.exit('No se encontraron regiones con notas GM de batería. Ejecuta con --inspect o revisa el archivo .sfz.')
    if a.inspect:
        return inspect(notes, resolve, regions)

    ffmpeg = shutil.which('ffmpeg')
    print('ffmpeg:', ffmpeg or 'no encontrado (solo WAV PCM, recorte con la librería estándar)')
    os.makedirs(os.path.join(a.out, 'samples'), exist_ok=True)
    pieces, total = {}, 0
    for key, prefs in PREF.items():
        n = next((x for x in prefs if x in notes), None)
        if n is None:
            print(f'  - {key}: sin samples (la app usará síntesis o la clave base)')
            continue
        layers = build(notes[n], resolve, a.layers, a.rr, a.mic, a.exclude)
        if not layers:
            print(f'  - {key}: todos los samples quedaron filtrados / no existen en disco')
            continue
        out_layers = []
        for li, L in enumerate(layers):
            files = []
            for ri, src in enumerate(L['src']):
                name = f"{key.replace(':', '_')}_L{li+1}_{ri+1}.wav"
                convert(src, os.path.join(a.out, 'samples', name), DUR[key], .03 if key.startswith('hat:c') else .08, a.no_trim, ffmpeg)
                files.append('samples/' + name); total += 1
            out_layers.append({'max': L['max'], 'files': files})
        pieces[key] = {'layers': out_layers}
        print(f'  + {key:11s} nota {n}: {len(out_layers)} capas x {max(len(l["files"]) for l in out_layers)} variaciones')
    if 'crash' in pieces and 'crash2' not in pieces:
        pieces['crash2'] = pieces['crash']
    json.dump({'name': a.name, 'pieces': pieces}, open(os.path.join(a.out, 'kit.json'), 'w'), indent=1)
    open(os.path.join(a.out, 'CREDITS.txt'), 'w').write(
        'Muldjord Kit — grabado por Lars Muldjord (DrumGizmo), licencia CC BY 4.0.\n'
        'https://creativecommons.org/licenses/by/4.0/\nArchivos recortados/convertidos para NEXUS//DRUM.\n')
    mb = sum(os.path.getsize(os.path.join(a.out, 'samples', f)) for f in os.listdir(os.path.join(a.out, 'samples'))) / 1e6
    print(f'\nListo: {total} archivos, {mb:.1f} MB en {a.out}')


if __name__ == '__main__':
    main()
