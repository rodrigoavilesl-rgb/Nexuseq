#!/usr/bin/env python3
"""Correo HTML (Codelco futurista) para colegas: guía secuencial con capturas. Genera .html (imágenes embebidas) y .eml (imágenes cid)."""
import os,io,base64,html
from PIL import Image
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.mime.image import MIMEImage
R=os.path.dirname(os.path.dirname(os.path.abspath(__file__)));S="/tmp/claude-0/shots/";OUT=R+"/correo/"
def prep(name,w=1180,crop=None,q=80):
    im=Image.open(S+name).convert("RGB")
    if crop:im=im.crop(crop)
    im.thumbnail((w,4000));b=io.BytesIO();im.save(b,"JPEG",quality=q,optimize=True);return b.getvalue()
IMG={ # clave: (archivo, crop)
 "hud":("06_guia_hud.png",(0,0,2160,1100)),"car":("01_cargar_ot.png",(0,0,2160,1700)),"env":("02_enviar.png",(0,0,2160,640)),
 "form":("12_formulario_aliservice.png",None),"resp":("03_cargar_respuestas.png",(0,0,2160,1700)),"vis":("05_visar_modal.png",(100,0,2060,900)),
 "inf":("10_lamina_resumen.png",None),"cat":("08_lamina_catering.png",None),"ad":("09_lamina_antes_despues.png",None)}
data={k:prep(v[0],crop=v[1]) for k,v in IMG.items()}
logo=base64.b64decode(open(R+"/src/logo.txt").read().split(",")[1])
O,Y,C,RD,G="#ff6a1a","#ffc21a","#14e0f0","#ff3b47","#47f08a"
BG,PN,LN,TX,MU="#05080c","#0a1118","#1b2836","#e6eef7","#8fa3b8"
MONO="'Courier New',Consolas,monospace"
def im(k,cid,alt):
    src=f"cid:{cid}" if cid else "data:image/jpeg;base64,"+base64.b64encode(data[k]).decode()
    return f'<img src="{src}" width="596" alt="{alt}" style="display:block;width:100%;max-width:596px;height:auto;border:1px solid {LN};">'
def step(n,col,titulo,hace,key,obtiene,cid):
    li="".join(f'<tr><td valign="top" style="padding:3px 8px 3px 0;color:{col};font:bold 13px {MONO};">▸</td><td style="padding:3px 0;color:{TX};font:14px/20px Arial,Helvetica,sans-serif;">{t}</td></tr>' for t in hace)
    return f'''<tr><td style="padding:26px 22px 0 22px;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:{PN};border:1px solid {LN};border-top:3px solid {col};">
<tr><td style="padding:16px 18px 6px 18px;"><table role="presentation" cellpadding="0" cellspacing="0"><tr>
<td style="background:{col};color:#000;font:bold 22px {MONO};padding:4px 12px;">{n}</td>
<td style="padding-left:12px;color:#fff;font:bold 17px Arial,Helvetica,sans-serif;letter-spacing:.3px;">{titulo}</td></tr></table></td></tr>
<tr><td style="padding:6px 18px 4px 18px;color:{MU};font:bold 11px {MONO};letter-spacing:2px;">QUÉ HACES</td></tr>
<tr><td style="padding:0 18px;"><table role="presentation" cellpadding="0" cellspacing="0">{li}</table></td></tr>
<tr><td style="padding:12px 18px 6px 18px;">{im(key,cid,titulo)}</td></tr>
<tr><td style="padding:6px 18px 18px 18px;"><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td style="border-left:3px solid {G};background:#0c1a12;padding:9px 12px;color:#cfe9d9;font:13px/19px Arial,Helvetica,sans-serif;"><b style="color:{G};font-family:{MONO};letter-spacing:1px;">QUÉ OBTIENES ›</b> {obtiene}</td></tr></table></td></tr>
</table></td></tr>'''
def chip(n,t,col):return f'<td align="center" style="padding:0 3px;"><table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="border:1px solid {LN};border-top:2px solid {col};background:{PN};"><tr><td align="center" style="padding:8px 2px;"><div style="color:{col};font:bold 20px {MONO};">{n}</div><div style="color:{TX};font:bold 11px Arial,sans-serif;letter-spacing:.5px;">{t}</div></td></tr></table></td>'
def build(cid):
    L=(lambda k:k) if cid else (lambda k:None)
    c=lambda k:(k if cid else None)
    steps=[
     step("1",O,"Carga la OT del inspector",["Abre <b>1 · Cargar</b> y arrastra el archivo de la OT (.xls). Puedes soltar Alimentación y Campamento juntos.","Revisa el resumen: cuántas son nuevas, actualizadas o sin cambio.","Presiona <b>Confirmar carga</b>."],"car","las observaciones ordenadas por casino, con la foto de cada una rescatada sola desde el Excel. Si subes la misma OT dos veces no se duplica.",c("car")),
     step("2",Y,"Envía el formulario a Aliservice",["Abre <b>2 · Enviar a Aliservice</b>, elige Alimentación o Campamento.","Descarga el <b>formulario HTML</b> (lleva la foto de cada observación abierta) y envíaselo por correo.","Aliservice lo abre en su computador o celular, responde cada observación y adjunta fotos."],"env","un solo archivo para Aliservice, sin planillas ni instalaciones. Cada observación queda registrada como «enviada».",c("env")),
     step("2b",Y,"Así lo ve Aliservice",["Elige el estado (Cerrado, En proceso o No corresponde) y la fecha.","Describe qué hizo y adjunta hasta tres fotos de evidencia.","Al terminar pulsa <b>Descargar respuesta</b> y te devuelve un archivo .json."],"form","un formulario guiado: no deja avanzar si faltan datos y marca cada tarjeta como «Lista» o «Falta».",c("form")),
     step("3",C,"Carga la respuesta de Aliservice",["Vuelve a <b>1 · Cargar</b> y arrastra el archivo que te devolvieron.","El sistema lo enlaza solo con cada observación. Si llega antes que la OT, queda en espera y se enlaza al cargarla."],"resp","la respuesta de Aliservice anotada en la bitácora de cada observación. Todavía no se cierra nada: falta tu visación.",c("resp")),
     step("4",O,"Visa: compara el antes y el después",["Abre <b>3 · Visar</b> y presiona <b>Visar ahora</b>.","Mira la foto de la inspección (antes) junto a la evidencia de Aliservice (después).","Atajos: <b>V</b> verifica y cierra · <b>R</b> rechaza con motivo · <b>S</b> salta."],"vis","si verificas, la observación queda cerrada con su evidencia. Si rechazas, vuelve a Aliservice con tu motivo y queda trazada.",c("vis")),
    ]
    inf=f'''<tr><td style="padding:26px 22px 0 22px;"><table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:{PN};border:1px solid {LN};border-top:3px solid {G};">
<tr><td style="padding:16px 18px 6px 18px;"><table role="presentation" cellpadding="0" cellspacing="0"><tr><td style="background:{G};color:#000;font:bold 22px {MONO};padding:4px 12px;">5</td><td style="padding-left:12px;color:#fff;font:bold 17px Arial,Helvetica,sans-serif;">Genera el informe</td></tr></table></td></tr>
<tr><td style="padding:6px 18px 4px 18px;color:{MU};font:bold 11px {MONO};letter-spacing:2px;">QUÉ HACES</td></tr>
<tr><td style="padding:0 18px 8px 18px;color:{TX};font:14px/20px Arial,Helvetica,sans-serif;">Abre <b>4 · Informes</b>, elige tipo (mensual, semanal o traspaso de turno), casino y período. Usa el borrador automático de conclusiones, edítalo y presiona <b>Generar</b>. Luego <b>Imprimir / guardar PDF</b> (A4 horizontal).</td></tr>
<tr><td style="padding:6px 18px;color:{MU};font:bold 11px {MONO};letter-spacing:2px;">QUÉ OBTIENES — LÁMINAS DEL INFORME</td></tr>
<tr><td style="padding:6px 18px;">{im("inf",c("inf"),"Informe mensual")}<div style="color:{MU};font:12px Arial,sans-serif;padding:4px 0 10px;">① Resumen con KPIs y comparativo contra el período anterior.</div></td></tr>
<tr><td style="padding:0 18px;">{im("cat",c("cat"),"Respuesta del catering")}<div style="color:{MU};font:12px Arial,sans-serif;padding:4px 0 10px;">② Respuesta del catering y verificación de cierres: porcentaje de respuesta, cierres verificados, rechazos y casos a gestionar.</div></td></tr>
<tr><td style="padding:0 18px 18px 18px;">{im("ad",c("ad"),"Antes y después")}<div style="color:{MU};font:12px Arial,sans-serif;padding:4px 0 0;">③ Evidencia de cierres visados, antes / después. Además: gestión de cierre del período anterior, criticidad Alta y elementos extraños, y abiertas al término del período.</div></td></tr>
</table></td></tr>'''
    hud=f'''<tr><td style="padding:26px 22px 0 22px;"><table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:{PN};border:1px solid {LN};border-top:3px solid {C};">
<tr><td style="padding:16px 18px 4px 18px;color:#fff;font:bold 17px Arial,Helvetica,sans-serif;"><span style="color:{C};font-family:{MONO};">◆</span> Tu tablero de control (pestaña Guía)</td></tr>
<tr><td style="padding:4px 18px 10px 18px;color:{TX};font:14px/20px Arial,Helvetica,sans-serif;">En cuanto cargas datos, la <b>Guía</b> muestra un tablero por ámbito: porcentaje de cierre, observadas, abiertas, criticidad Alta, plazos vencidos y respuestas por visar. El estado cambia solo: <b style="color:{G};">NOMINAL</b>, <b style="color:{Y};">ALERTA</b> o <b style="color:{RD};">CRÍTICO</b>. Debajo aparece la <b>cola de atención</b> con lo más urgente primero.</td></tr>
<tr><td style="padding:0 18px 18px 18px;">{im("hud",c("hud"),"Tablero de control")}</td></tr></table></td></tr>'''
    TIPS=[
"<b>Cómo abrirlo:</b> guarda el archivo adjunto <b>Desviaciones_Simple_OT.html</b> y ábrelo con doble clic en Chrome o Edge. No necesita internet ni instalación.",
"<b>Tus datos quedan en ese computador.</b> Al cerrar cada turno entra a <i>Configuración y respaldo</i> y descarga el respaldo (.json). Para unir datos de dos personas, cada uno carga el respaldo del otro en el paso 1: no duplica.",
"<b>Un solo lugar para cargar todo:</b> OT, respuestas de Aliservice y respaldos se arrastran a la misma zona y el sistema reconoce cada archivo.",
"<b>Trazabilidad:</b> en <i>Desviaciones y trazabilidad</i> ves la historia completa de cada observación: quién cargó qué, cuándo respondió Aliservice y quién visó.",
"<b>Para practicar</b> hay un escenario de prueba (adjunto, con datos inventados): 2 OT, las 2 respuestas y los informes de ejemplo."]
    TIPLI="".join('<tr><td valign="top" style="padding:4px 8px 4px 0;color:'+Y+';font:bold 13px '+MONO+';">▸</td><td style="padding:4px 0;color:'+TX+';font:14px/20px Arial,Helvetica,sans-serif;">'+t+'</td></tr>' for t in TIPS)
    tips=f'''<tr><td style="padding:26px 22px 0 22px;"><table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:{PN};border:1px solid {LN};border-top:3px solid {Y};">
<tr><td style="padding:16px 18px 6px 18px;color:#fff;font:bold 17px Arial,Helvetica,sans-serif;"><span style="color:{Y};font-family:{MONO};">!</span> Antes de empezar</td></tr>
<tr><td style="padding:0 18px 16px 18px;"><table role="presentation" cellpadding="0" cellspacing="0">
{TIPLI}
</table></td></tr></table></td></tr>'''
    return f'''<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="dark"><title>Cómo usar TITAN · Desviaciones Carén</title></head>
<body style="margin:0;padding:0;background:{BG};">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:{BG};"><tr><td align="center" style="padding:0;">
<table role="presentation" width="640" cellpadding="0" cellspacing="0" style="width:100%;max-width:640px;background:{BG};">
<tr><td><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td width="66%" height="5" style="background:{O};font-size:0;line-height:0;">&nbsp;</td><td width="34%" height="5" style="background:{Y};font-size:0;line-height:0;">&nbsp;</td></tr></table></td></tr>
<tr><td style="background:#0a0f16;padding:18px 22px;border-bottom:1px solid {LN};"><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr>
<td width="120" valign="middle"><div style="background:#fff;padding:6px 10px;border-left:3px solid {O};width:100px;"><img src="{"cid:logo" if cid else "data:image/png;base64,"+base64.b64encode(logo).decode()}" width="100" alt="Workmate" style="display:block;width:100px;height:auto;"></div></td>
<td valign="middle" style="padding-left:14px;"><div style="color:{MU};font:11px {MONO};letter-spacing:2px;"><span style="color:{O};">◆</span> TITAN-NEXUS · CENTRO DE CONTROL · CARÉN</div><div style="color:#fff;font:bold 22px/27px Arial,Helvetica,sans-serif;padding-top:6px;">Desviaciones de inspección:<br>de la OT al informe, <span style="color:{O};">paso a paso</span></div></td></tr></table></td></tr>
<tr><td style="padding:22px 22px 0 22px;color:{TX};font:15px/23px Arial,Helvetica,sans-serif;">Hola equipo:<br><br>Les presento la herramienta que usaremos para <b>gestionar las observaciones de las OT de inspección</b> (Alimentación y Campamento): ordena lo que levanta el inspector, lo envía a Aliservice, controla lo que responden, verifica la evidencia y entrega los informes ya validados. <b>Sin planillas manuales y con trazabilidad completa.</b><br><br>Es simple: son <b>cinco acciones, siempre en el mismo orden</b>. Aquí van con capturas reales:</td></tr>
<tr><td style="padding:18px 19px 0 19px;"><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr>{chip("1","CARGAR OT",O)}{chip("2","ENVIAR",Y)}{chip("3","CARGAR RESP.",C)}{chip("4","VISAR",O)}{chip("5","INFORME",G)}</tr></table></td></tr>
{"".join(steps)}{inf}{hud}{tips}
<tr><td style="padding:26px 22px 8px 22px;"><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td style="border:1px solid {O};background:#1a0f08;padding:14px 16px;color:{TX};font:14px/21px Arial,Helvetica,sans-serif;"><b style="color:{Y};font-family:{MONO};letter-spacing:1px;">¿DUDAS?</b> Respóndeme este correo o búscame en terreno. La primera vez conviene practicar con el escenario de prueba antes de usar datos reales.</td></tr></table></td></tr>
<tr><td style="padding:16px 22px 26px 22px;border-top:1px solid {LN};color:{MU};font:11px/17px {MONO};">Rodrigo Avilés Lazcano · Workmate<br>TITAN-NEXUS · © 2026 Rodrigo Avilés Lazcano. Software de autoría propia, entregado con licencia de uso a la operación. Las capturas usan datos ficticios de prueba.</td></tr>
</table></td></tr></table></body></html>'''
os.makedirs(OUT,exist_ok=True)
open(OUT+"Correo_Colegas_Desviaciones.html","w",encoding="utf8").write(build(False))
# .eml con imágenes incrustadas (cid) y los adjuntos
msg=MIMEMultipart("mixed");msg["Subject"]="Cómo usar TITAN · Desviaciones Carén: de la OT al informe, paso a paso";msg["X-Unsent"]="1"
rel=MIMEMultipart("related");rel.attach(MIMEText(build(True),"html","utf-8"))
for k,b in list(data.items())+[("logo",logo)]:
    p=MIMEImage(b,_subtype="png" if k=="logo" else "jpeg");p.add_header("Content-ID",f"<{k}>");p.add_header("Content-Disposition","inline",filename=k+(".png" if k=="logo" else ".jpg"));rel.attach(p)
msg.attach(rel)
for f in [R+"/Desviaciones_Simple_OT.html",R+"/prueba/Escenario_Prueba_OT_Aliservice.zip"]:
    from email.mime.base import MIMEBase;from email import encoders
    a=MIMEBase("application","octet-stream");a.set_payload(open(f,"rb").read());encoders.encode_base64(a);a.add_header("Content-Disposition","attachment",filename=os.path.basename(f));msg.attach(a)
open(OUT+"Correo_Colegas_Desviaciones.eml","wb").write(msg.as_bytes())
print({k:len(v)//1024 for k,v in data.items()})
