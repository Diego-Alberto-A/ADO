import fs from 'node:fs/promises';
import {FileBlob,SpreadsheetFile} from '@oai/artifact-tool';
const w=await SpreadsheetFile.importXlsx(await FileBlob.load('C:/Users/toast/Downloads/Template para Pruebas de Aceptación.xlsx'));
console.log((await w.inspect({kind:'workbook,sheet,table',maxChars:1500,tableMaxRows:2,tableMaxCols:5})).ndjson);
await fs.writeFile('tmp/uat/template.png',new Uint8Array(await (await w.render({sheetName:'Sheet1',range:'C2:H3',scale:1})).arrayBuffer()));
