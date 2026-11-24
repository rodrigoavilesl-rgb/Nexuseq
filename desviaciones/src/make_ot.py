#!/usr/bin/env python3
"""Genera Desviaciones_Simple_OT.html: motor validado del sistema original + guía de 5 pasos.
Uso: python3 make_ot.py <TITAN_Consolidado_Desviaciones_CAREN.html original>"""
import sys,os
src=open(sys.argv[1],encoding="utf8").read()
def rep(a,b,cnt=1):
    global src
    assert src.count(a)==cnt,(a[:60],src.count(a))
    src=src.replace(a,b)
# 1. navegación: 5 pasos + herramientas avanzadas
i=src.index('<nav class="nonrep" id="nav">');j=src.index('</nav>',i)+6
src=src[:i]+'<nav class="nonrep" id="nav"><button data-t="ini" class="on">Guía</button><button data-t="car">1</button><button data-t="env">2</button><button data-t="vis">3</button><button data-t="inf">4</button><button data-t="pen">Pendientes</button><button data-t="des">Desviaciones</button><button data-t="his">Histórico</button><button data-t="cfg">Config</button></nav>'+src[j:]
rep('{ini:"Inicio",car:"Cargar",pen:"Pendientes",des:"Desviaciones",inf:"Informes",his:"Histórico",cfg:"Configuración y respaldo"}',
    '{ini:"◆ Guía",car:"1 · Cargar",env:"2 · Enviar a Aliservice",vis:"3 · Visar",inf:"4 · Informes",pen:"Pendientes",des:"Desviaciones y trazabilidad",his:"Histórico",cfg:"Configuración y respaldo"}')
rep('"ini"!==t.dataset.t&&t.classList.add("adv")','["pen","des","his","cfg"].includes(t.dataset.t)&&t.classList.add("adv")')
# 2. paneles nuevos
PANELS='''<section id="t-env" hidden><div id="envx"></div></section>
<section id="t-vis" hidden><div id="visx"></div></section>
'''
rep('<section id="t-pen" hidden>',PANELS+'<section id="t-pen" hidden>')
rep('<div class="drop" id="drop">','<div class="alerts ok" style="margin-bottom:8px"><b>Un solo lugar para cargar todo</b> (en cualquier orden): la <b>OT del inspector</b> (.xls), la <b>respuesta de Aliservice</b> (.json del formulario o Excel) y los <b>respaldos</b> (.json). El sistema reconoce cada archivo solo.</div><div class="drop" id="drop">')
# 3. estilos + lógica de la guía
EXTRA=open(os.path.join(os.path.dirname(os.path.abspath(__file__)),"ot_extra.html"),encoding="utf8").read()
k=src.rindex('</body>');src=src[:k]+EXTRA+src[k:]
out=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),"Desviaciones_Simple_OT.html")
open(out,"w",encoding="utf8").write(src);print("OK",out,len(src)//1024,"KB")
