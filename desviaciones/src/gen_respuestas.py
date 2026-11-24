#!/usr/bin/env python3
"""Fase 2: con los formularios que emite el sistema (form_<ambito>.html), genera las respuestas FICTICIAS de Aliservice:
formulario HTML ya respondido (con fotos) y el .json que ese formulario entregaría."""
import re,json,io,base64,os,sys,unicodedata
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import importlib.util
spec=importlib.util.spec_from_file_location("g",os.path.join(os.path.dirname(os.path.abspath(__file__)),"gen_prueba.py"))
src=open(spec.origin,encoding="utf8").read().split("os.makedirs(OUT,exist_ok=True)")[0]   # solo definiciones (foto, ESC, jpg…)
exec(src)
N=lambda s:unicodedata.normalize("NFD",s).encode("ascii","ignore").decode().lower().strip()
def durl(im):return "data:image/jpeg;base64,"+base64.b64encode(jpg(im,62)).decode()
for amb,E in ESC.items():
    f=open(f"{TMP}/form_{amb}.html",encoding="utf8").read()
    m=re.search(r'(<script[^>]*id="dat"[^>]*>)(.*?)(</script>)',f,re.S);data=json.loads(m.group(2));em=data["emision"]
    R={};resp=[]
    for it in data["items"]:
        k=[i for i,o in enumerate(E["obs"]) if N(o[3])==N(it["det"])]
        if not k:print("sin match",it["det"]);continue
        k=k[0];r=E["resp"][k]
        if not r:continue
        est,fe,acc,tev=r;fotos=[] if tev=="conversada" else [durl(foto(E["obs"][k][3],False,k+100,640,420))]
        d={"est":est,"fe":fe,"tev":tev,"acc":acc,"com":"","fotos":fotos};R[it["id"]]=d
        resp.append({"id":it["id"],**d})
    who="Alejandra Collao (Aliservice)"
    pre=f'<script>try{{var k="titanresp_{em["id"]}";if(!localStorage.getItem(k))localStorage.setItem(k,JSON.stringify({{R:{json.dumps(R)},W:"{who}"}}))}}catch(e){{}}</script>'
    out=f[:m.end()]+pre+f[m.end():]
    open(f"{OUT}/Formulario_Respuesta_{amb.replace("ó","o")}_RESPONDIDO.html","w",encoding="utf8").write(out)
    json.dump({"sistema":"TITAN-DESV-RESP","version":1,"responde":who,"emision":em,"respuestas":resp},open(f"{OUT}/Respuesta_Aliservice_{amb.replace("ó","o")}.json","w",encoding="utf8"),ensure_ascii=False)
    print(amb,len(resp),"respuestas de",len(data["items"]))
