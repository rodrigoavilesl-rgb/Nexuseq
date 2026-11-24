#!/usr/bin/env python3
"""Arma Desviaciones_Simple.html (archivo único, funciona offline) desde src/."""
import os
d=os.path.dirname(os.path.abspath(__file__))+"/src/"
t=open(d+"app.html",encoding="utf8").read()
t=t.replace("/*__XLSX__*/",open(d+"xlsx.inline.js",encoding="utf8").read().replace("</script","<\\/script"))
t=t.replace("__LOGO__",open(d+"logo.txt").read().strip())
open(os.path.dirname(d[:-1])+"/Desviaciones_Simple.html","w",encoding="utf8").write(t)
print("OK",len(t)//1024,"KB")
