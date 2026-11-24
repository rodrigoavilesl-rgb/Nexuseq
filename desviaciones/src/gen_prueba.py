#!/usr/bin/env python3
"""Genera el escenario FICTICIO de prueba: 2 OT (.xls con fotos) + 2 respuestas de Aliservice (.json)."""
import io,json,base64,os,subprocess,datetime as D,random
from PIL import Image,ImageDraw,ImageFont
import openpyxl
from openpyxl.drawing.image import Image as XI
OUT=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),"prueba")
TMP="/tmp/claude-0/gen";os.makedirs(TMP,exist_ok=True)
def foto(txt,antes,seed,w=640,h=420):
    random.seed(seed);im=Image.new("RGB",(w,h),(70,55,50) if antes else (225,235,228));d=ImageDraw.Draw(im)
    for _ in range(40):
        x,y=random.randint(0,w),random.randint(0,h);r=random.randint(10,60)
        c=(random.randint(90,150),random.randint(60,100),random.randint(40,80)) if antes else (random.randint(190,235),random.randint(215,245),random.randint(200,235))
        d.ellipse([x-r,y-r,x+r,y+r],fill=c)
    d.rectangle([0,h-70,w,h],fill=(0,0,0))
    try:f=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",20);g=ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",15)
    except:f=g=ImageFont.load_default()
    d.text((12,h-64),("ANTES · " if antes else "DESPUÉS · ")+"FOTO FICTICIA DE PRUEBA",fill=(255,179,0) if antes else (182,255,60),font=f)
    d.text((12,h-34),txt[:70],fill=(255,255,255),font=g);return im
def jpg(im,q=70):
    b=io.BytesIO();im.save(b,"JPEG",quality=q);return b.getvalue()
HOY=D.date(2026,11,24)
ESC={
 "Alimentación":dict(fecha=D.date(2026,11,9),insp="CRISTIÁN PIZARRO",para="ALEJANDRA COLLAO - ALEJANDRA FREDES",ref="CASINO ANTIGUO - CASINO NUEVO",archivo="OT-09.11.26_Alim_PRUEBA.xls",
  obs=[("ALIMENTACIÓN","CASINO ANTIGUO","CUARTO FRÍO","PRODUCTO SIN ROTULAR NI FECHA DE ELABORACIÓN (LÁCTEOS)","ROTULAR / ELIMINAR",3,"DOCUMENTACIÓN, CONTRATO Y PROCEDIMIENTOS DEL SERVICIO","CUMPLIMIENTO NORMATIVO",3),
   ("ALIMENTACIÓN","CASINO ANTIGUO","CUARTO CALIENTE","ELEMENTO EXTRAÑO (CABELLO) EN PREPARACIÓN DE ENSALADA","RETIRAR / REINSTRUIR AL PERSONAL",5,"DS. Nº 977, DS. Nº 594","CUMPLIMIENTO NORMATIVO",3),
   ("ALIMENTACIÓN","CASINO ANTIGUO","LÍNEA DE SERVICIO","BANDEJA DE PLATO DE FONDO BAJO 65 °C (58 °C)","CORREGIR / CAPACITAR",4,"DS. Nº 977, DS. Nº 594","CUMPLIMIENTO NORMATIVO",2),
   ("ALIMENTACIÓN","CASINO ANTIGUO","BODEGA ABARROTES","CAJAS EN CONTACTO DIRECTO CON EL PISO","ORDENAR / REPONER PALLETS",3,"INSTALACION E INFRAESTRUCTURA","CUMPLIMIENTO NORMATIVO",3),
   ("CONDICIONES DE SEGURIDAD","CASINO NUEVO","ÁREA LIMPIA","LONCHEROS EN MAL ESTADO (BORDES FILOSOS)","ELIMINAR / REPONER",4,"CONDICIONES SUBESTANDARES","CONDICIONES DE SEGURIDAD",3),
   ("ALIMENTACIÓN","CASINO NUEVO","LAVADO DE OLLAS","PISO CON ACUMULACIÓN DE AGUA Y GRASA","LIMPIEZA / REPARAR DESAGÜE",2,"LIMPIEZA Y SANITIZACION","CUMPLIMIENTO NORMATIVO",4)],
 # (estado, fecha ejecución, acción, tipo evidencia, comentario) por índice; None = sin respuesta
 resp=[("Cerrado","2026-11-11","Se rotuló todo el producto con fecha y se eliminó lo vencido","mejora"),
  ("Cerrado","2026-11-10","Se retiró la preparación, se reinstruyó a la manipuladora y se cambió el tocado","difusion"),
  ("En proceso","2026-11-20","Se compró nuevo baño María; llega el 27-11","mejora"),
  ("Cerrado","2026-11-13","Se instalaron pallets plásticos y se reordenó la bodega","mejora"),
  ("Cerrado","2026-11-12","Se eliminaron 40 loncheros y se repusieron nuevos","mejora"),
  None]),
 "Campamento":dict(fecha=D.date(2026,11,11),insp="CRISTIÁN PIZARRO",para="ALEJANDRA COLLAO - VICTORIA ARAVENA",ref="CAMPAMENTO NUEVO",archivo="OT-11.11.26_Camp_PRUEBA.xls",
  obs=[("CAMPAMENTO","MÓDULO QUILLAY","HABITACIONES","LUZ DE HABITACIÓN CON INTERMITENCIA (HAB 112)","REVISAR / REEMPLAZAR",1,"REPARACION Y MANTENCION MISCELANEA","SERVICIOS GENERALES",3),
   ("CAMPAMENTO","MÓDULO QUILLAY","HABITACIONES","HECHURA DE CAMA DEFICIENTE (HAB 118)","ORDENAR / REINSTRUIR AL PERSONAL",3,"HOTELERIA","SERVICIOS GENERALES",3),
   ("CAMPAMENTO","MÓDULO LINGUE","PASILLO INTERIOR","LUMINARIAS FUERA DE SERVICIO (CAJA ESCALA)","REVISAR / REPARAR",1,"REPARACION Y MANTENCION MISCELANEA","SERVICIOS GENERALES",5),
   ("CAMPAMENTO","MÓDULO LINGUE","BAÑOS","FILTRACIÓN EN LAVAMANOS (BAÑO 2)","REPARAR",3,"REPARACION Y MANTENCION MISCELANEA","SERVICIOS GENERALES",3),
   ("CONDICIONES DE SEGURIDAD","ÁREAS COMUNES-RECREACIÓN","DESCONCHE","MESA DE PING-PONG CON PATA INESTABLE","REPARAR / RETIRAR",4,"CONDICIONES SUBESTANDARES","CONDICIONES DE SEGURIDAD",3),
   ("CAMPAMENTO","MÓDULO QUILLAY","HABITACIONES","VENTANA SIN MOSQUITERO (HAB 121)","REPONER MATERIAL",3,"HOTELERIA","SERVICIOS GENERALES",5)],
 resp=[("Cerrado","2026-11-13","Se cambió luminaria LED","mejora"),
  ("Cerrado","2026-11-12","Se reinstruyó al personal de aseo y se supervisó la habitación","difusion"),
  ("En proceso","2026-11-22","Repuesto en bodega central; se instala esta semana","mejora"),
  ("Cerrado","2026-11-18","Se reemplazó el sello del lavamanos","mejora"),
  ("Cerrado","2026-11-16","Se reparó y niveló la pata de la mesa","mejora"),
  None])}
os.makedirs(OUT,exist_ok=True)
H=["EVIDENCIA FOTOGRÁFICA","AREA","DEPENDENCIA","SECTOR","OBSERVACION DE INCUMPLIMIENTO","ACCION PROPUESTA","FECHA CIERRE","RESPONSABLE","FACTOR","INDICADOR","NIVEL DE IMPORTANCIA","ESTADO"]
ids_out={}
for amb,E in ESC.items():
    wb=openpyxl.Workbook();ws=wb.active;ws.title="OT INSPECCIÓN"
    ws["D1"]="ORDEN DE TRABAJO";ws["B3"]="A:";ws["C3"]=E["para"];ws["B4"]="NOMBRE INSPECTOR:";ws["C4"]=E["insp"];ws["B5"]="FECHA:";ws["C5"]=E["fecha"];ws["B6"]="REFERENCIA AREA";ws["C6"]=E["ref"]
    for i,h in enumerate(H):ws.cell(8,2+i,h)
    for k,(area,dep,sec,ob,acc,niv,fac,ind,pl) in enumerate(E["obs"]):
        r=9+k;ws.row_dimensions[r].height=80
        for c,v in enumerate([None,area,dep,sec,ob,acc,E["fecha"]+D.timedelta(days=pl),E["para"].split(" - ")[0],fac,ind,niv,"ABIERTO"]):
            if v is not None:ws.cell(r,2+c,v)
        p=f"{TMP}/{amb}_a{k}.jpg";open(p,"wb").write(jpg(foto(ob,True,k+(10 if amb=="Campamento" else 0),420,280),65))
        im=XI(p);im.width,im.height=105,70;ws.add_image(im,f"B{r}")
    for col,w in zip("BCDEFGHIJKLM",[16,20,24,22,50,28,14,22,34,26,10,10]):ws.column_dimensions[col].width=w
    x=f"{TMP}/{E['archivo'].replace('.xls','.xlsx')}";wb.save(x)
    subprocess.run(["soffice","--headless","--convert-to","xls:MS Excel 97","--outdir",OUT,x],capture_output=True,timeout=180)
    print("OT",amb,os.path.exists(f"{OUT}/{E['archivo']}"))
json.dump({k:{"fecha":str(v["fecha"])} for k,v in ESC.items()},open(TMP+"/esc.json","w"))
