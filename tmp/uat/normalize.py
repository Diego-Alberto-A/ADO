from zipfile import ZipFile,ZIP_DEFLATED
import xml.etree.ElementTree as E
ns={'m':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
# Normalize only the sample's rich-text strings before import: the renderer retains old runs after a values edit.
with ZipFile(r'C:\Users\toast\Downloads\Template para Pruebas de Aceptación.xlsx') as src, ZipFile('tmp/uat/template_plain.xlsx','w',ZIP_DEFLATED) as dst:
 for info in src.infolist():
  data=src.read(info.filename)
  if info.filename=='xl/sharedStrings.xml':
   root=E.fromstring(data)
   for si in root:
    if si.find('m:r',ns) is not None:
     text=''.join(si.itertext())
     for child in list(si): si.remove(child)
     E.SubElement(si,'{'+ns['m']+'}t').text=text
   data=E.tostring(root,encoding='utf-8',xml_declaration=True)
  dst.writestr(info,data)
