from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from pathlib import Path
import json
d=json.loads(Path('tmp/uat/data.json').read_text(encoding='utf-8'))
styles=getSampleStyleSheet()
styles.add(ParagraphStyle(name='BodyES',fontName='Helvetica',fontSize=10,leading=14,spaceAfter=8,textColor=colors.HexColor('#243247')))
styles.add(ParagraphStyle(name='SmallES',parent=styles['BodyES'],fontSize=8.5,leading=11))
styles['Title'].textColor=colors.HexColor('#203B60');styles['Title'].alignment=TA_LEFT
story=[]
def p(t,style='BodyES'): story.append(Paragraph(t,styles[style]))
def h(t): p(t,'Heading2')
def table(rows,widths):
 t=Table([[Paragraph(str(x),styles['SmallES']) for x in row] for row in rows],colWidths=widths,repeatRows=1,hAlign='LEFT')
 t.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#E7EDF5')),('VALIGN',(0,0),(-1,-1),'TOP'),('BOTTOMPADDING',(0,0),(-1,-1),7),('TOPPADDING',(0,0),(-1,-1),7),('LINEBELOW',(0,0),(-1,0),.7,colors.HexColor('#203B60')),('LINEBELOW',(0,1),(-1,-1),.3,colors.HexColor('#D9E0E8'))]))
 story.append(t);story.append(Spacer(1,10))
p('Crosswalker','Title');p('Reporte gerencial de pruebas de aceptación','Heading1');p('Diseño al 1 de octubre de 2026 · Base: Etapa 1. Requerimientos-1.pdf')
p('Se diseñaron <b>32 casos de prueba</b>: 24 cubren los 12 requerimientos de usuario y ocho verifican aspectos técnicos complementarios. Cada requerimiento tiene dos criterios de aceptación y dos casos asociados. Los 14 campos de la plantilla están completos.')
table([['Indicador','Resultado del diseño'],['Cobertura de requerimientos de usuario','12 de 12 (100%)'],['Criterios de aceptación de RU','24, con trazabilidad al caso correspondiente'],['Casos principales / complementarios','24 / 8'],['Prioridad alta / media','26 / 6'],['Estado inicial','32 pendientes de ejecución; 0 ejecutados']], [245,255])
p('<b>La cobertura indica que se diseñaron pruebas, no que el producto esté aprobado.</b> No se ejecutaron las aplicaciones, no se midió su desempeño y no se generaron evidencias de cumplimiento. El ejemplo de login de la plantilla se sustituyó porque el proyecto excluye cuentas y autenticación.')
h('Alcance y enfoque')
p('Las pruebas cubren navegación para niños que aún no leen, planeación sin tiempo límite, sesiones breves, comprensión del objetivo, fallos amigables, reintentos con pistas, instalación, uso en aula, validación pedagógica, niveles JSON y operación offline. Los casos adicionales verifican rendimiento, tamaño, tiempos de carga, progresión, fichas y arquetipos, paleta de emergencia, límites del plan e interfaz.')
p('Se combinan recorridos exitosos, fallos provocados, repetición determinista, valores límite, persistencia entre sesiones, observación con usuarios e inspección de paquetes y contenido. La matriz complementaria no sustituye una auditoría exhaustiva de todos los requisitos funcionales, legales o documentales de Etapa 1.')
h('Prioridad de ejecución')
p('Ejecutar primero instalación y arranque, ausencia de red y datos personales, seguridad del contenido, planeación/RUN, reintentos y persistencia. Continuar con paridad de los siete niveles y desempeño. Completar usabilidad, presentación y revisión formal con ADO. Un defecto crítico o alto impide recomendar la aceptación hasta corregirlo y repetir sus pruebas.')
story.append(PageBreak())
h('Cobertura por requerimiento')
table([['RU','Enfoque','Casos'],*[[r['id'],r['text'],'01 y 02'] for r in d['requirements']]], [48,377,75])
p('Identificación: UAT-RU-01-01 y UAT-RU-01-02 corresponden a RU-01; el mismo patrón aplica hasta RU-12. Los complementarios se identifican de UAT-TEC-01 a UAT-TEC-08. La hoja «Criterios y trazabilidad» relaciona cada criterio con su caso y páginas de origen.','SmallES')
h('Recursos y responsabilidades propuestas')
p('<b>Dispositivos:</b> tres Android de gama baja (A1 con Android 7.0/API 24, A2 y A3 con versiones compatibles) y un iPhone con iOS 14 (I1). Los modelos deben confirmarse antes de ejecutar. Esta matriz contempla S-03 y el mínimo de compatibilidad de C-01. Registrar un resultado separado por dispositivo aplicable.')
p('<b>Responsables:</b> Arturo y Shalom para mecánicas, datos y paridad; Juan Pablo para instalación y actividades de uso; Diego para usabilidad y contenido visual/sonoro; equipo QA para mediciones. Son asignaciones propuestas, no compromisos confirmados. El enlace de Fundación ADO aprueba los resultados de aceptación y el contenido pedagógico.')
story.append(PageBreak())
h('Decisiones pendientes y riesgos')
table([['Asunto','Implicación y acción requerida'],['Cantidad de niveles','O-04/REQ-03 exigen mínimo 7 y L-06 máximo 7. Se diseña para exactamente 7 en 3 mundos; confirmar la línea base.'],['Referencia de instalación','US-07 cita REQ-11, pero ese requisito define JSON. La trazabilidad de RU-09 usa sección 1.5 y CU-07. Corregir referencia en la base.'],['Revisión al 70%','O-06/M3 indican 23 de octubre; actividad 4.2 indica 26-30 de octubre. Acordar fecha y distinguir revisión pedagógica de prueba de usabilidad.'],['Criterios incompletos','Definir tutorial «largo», muestra/tasa de sesiones breves, efectos de fichas, reglas de estrellas y umbrales de calentamiento/batería. C-10 aparece incompleto en p. 21; complementar su redacción usando O-05.'],['Instalación iOS','Confirmar método, firma, perfiles, equipo y confianza previa. No confundir app offline con instalación offline universal.'],['Disponibilidad y evidencia','Confirmar dispositivos, macOS, grupo de al menos 8 usuarios y enlace ADO. La revisión al 30% reportada en la base no se considera verificada sin su minuta.']], [125,375])
h('Calendario y control de ejecución')
p('La base programa pruebas de dispositivos y usabilidad en S11 (26-30 de octubre), correcciones y regresión en S12 (2-6 de noviembre), y manuales/empaquetado en S13-S14, con entrega el 20 de noviembre. Los casos de manuales y firma deben repetirse sobre entregables finales antes de M5. No se estima esfuerzo adicional sin validar disponibilidad y matriz real.')
p('Para cada ejecución registrar ID del caso, versión/build, dispositivo y OS, fecha, ejecutor, resultado observado, evidencia y defecto asociado. Usar estados Pendiente, En ejecución, Aprobado, Fallido o Bloqueado en el campo combinado de la plantilla. Conservar las observaciones. Los indicadores de prioridad y total del Excel se calculan desde los casos; el dato de pendientes al cierre del diseño es una fotografía inicial.')
h('Recomendación de aceptación')
p('Antes de ejecutar, acordar los criterios pendientes y preparar paquetes, JSON, soluciones de referencia y material pedagógico. Para recomendar aceptación, completar la matriz aplicable, documentar resultados, resolver defectos críticos/altos, repetir fallidos y obtener conformidad de ADO. Verificar además C-01 a C-10 y la entrega de todos los productos de la sección 1.5. El cumplimiento de estos casos por sí solo no acredita el cierre integral del proyecto.')
p('<b>Fuentes:</b> Etapa 1, tabla 10 (pp. 12-13), requisitos (pp. 14-19), aprobación (pp. 20-21), casos e historias (pp. 28-30), riesgos y calendario (pp. 31-35); Template para Pruebas de Aceptación.xlsx, estructura de 14 campos. Los datos sintéticos, protocolos y responsables propuestos están identificados en el Excel.','SmallES')
def footer(c,doc):
 c.setFont('Helvetica',8);c.setFillColor(colors.HexColor('#65758B'));c.drawString(47,28,'Crosswalker · Diseño de aceptación · 01/10/2026');c.drawRightString(548,28,f'{doc.page}')
Path('output/pdf').mkdir(parents=True,exist_ok=True)
SimpleDocTemplate('output/pdf/Reporte_gerencial_Crosswalker.pdf',pagesize=(595,842),rightMargin=47,leftMargin=47,topMargin=38,bottomMargin=45).build(story,onFirstPage=footer,onLaterPages=footer)
