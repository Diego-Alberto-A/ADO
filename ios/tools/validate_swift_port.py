"""Static/resource validation; intentionally does not claim to compile Swift or run XCTest."""
from pathlib import Path
import hashlib, json, plistlib, re, struct, subprocess, xml.etree.ElementTree as ET

IOS=Path(__file__).resolve().parents[1]
ROOT=IOS/'Reference'
RES=IOS/'Crosswalker/Resources'
def check(condition,message):
    if not condition: raise AssertionError(message)

for original,name in [('app/src/main/assets/campaign.json','campaign.json'),('app/src/main/res/font/jersey_25.ttf','jersey_25.ttf'),('app/src/main/assets/licenses/Jersey25-OFL.txt','Jersey25-OFL.txt')]:
    check((ROOT/original).read_bytes()==(RES/name).read_bytes(),f'Changed original: {name}')
check((RES/'campaign.json').read_bytes()==(IOS/'CrosswalkerTests/Fixtures/campaign.json').read_bytes(),'Stale test campaign')
campaign=json.loads((RES/'campaign.json').read_text(encoding='utf-8'))
check(len(campaign['levels'])==12 and len(campaign['tools'])==9 and len(campaign['worlds'])==3,'Campaign inventory')

def pixels(path):
    result=subprocess.run(['ffmpeg','-v','error','-i',str(path),'-f','rawvideo','-pix_fmt','rgba','-'],capture_output=True,check=True)
    return hashlib.sha256(result.stdout).hexdigest()
images=list((ROOT/'app/src/main/res/drawable-nodpi').glob('*.webp'))
for source in images:
    check(pixels(source)==pixels(RES/(source.stem+'.png')),f'Image pixels changed: {source.name}')
for level in campaign['levels']:
    for name in [l['car'] for l in level['lanes']]+[w['sprite'] for w in level['walkers']]:
        check((RES/(name+'.png')).exists(),f'Missing sprite {name}')
for source in (IOS/'Crosswalker').rglob('*.swift'):
    for name in re.findall(r'(?:Image|Artwork)\("([a-z_]+)"',source.read_text(encoding='utf-8')):
        check((RES/(name+'.png')).exists(),f'Missing UI asset {name}')

font=(RES/'jersey_25.ttf').read_bytes()
tables=struct.unpack_from('>H',font,4)[0]
names=[]
for i in range(tables):
    tag,_,offset,_=struct.unpack_from('>4sIII',font,12+i*16)
    if tag==b'name':
        _,count,strings=struct.unpack_from('>HHH',font,offset)
        for j in range(count):
            platform,_,_,name_id,length,start=struct.unpack_from('>HHHHHH',font,offset+6+j*12)
            if name_id==6:
                raw=font[offset+strings+start:offset+strings+start+length]
                names.append(raw.decode('utf-16-be' if platform in [0,3] else 'mac_roman'))
check('Jersey25-Regular' in names,'Wrong custom font PostScript name')

info=plistlib.loads((IOS/'Crosswalker/Info.plist').read_bytes())
check(info['UIAppFonts']==['jersey_25.ttf'],'Font not registered')
plistlib.loads((RES/'PrivacyInfo.xcprivacy').read_bytes())
ET.parse(IOS/'Crosswalker.xcodeproj/xcshareddata/xcschemes/Crosswalker.xcscheme')
project=(IOS/'Crosswalker.xcodeproj/project.pbxproj').read_text(encoding='utf-8')
definitions=re.findall(r'^([A-F0-9]{24}) = ',project,re.M)
check(len(definitions)==len(set(definitions)),'Duplicate project object')
check(set(re.findall(r'\b[A-F0-9]{24}\b',project))==set(definitions),'Dangling project object')
for path in re.findall(r'path = "([^"]+)";',project):
    if not path.endswith(('.app','.xctest')): check((IOS/path).exists(),f'Project file missing: {path}')
for source in (IOS/'Crosswalker').rglob('*.swift'):
    check(source.relative_to(IOS).as_posix() in project,f'Source excluded from project: {source}')
traces=json.loads((IOS/'CrosswalkerTests/Fixtures/trajectories.json').read_text())
check(len(traces)==360,'Incomplete reference traces')
check({t['level'] for t in traces}==set(range(1,13)),'Missing trace level')
check(all(t['samples'][-1]['phase']!='RUNNING' for t in traces),'Unfinished reference simulation')
print(f'PASS: {len(images)} images match original decoded pixels; campaign, font and license match byte-for-byte.')
print(f'PASS: asset references, font name, plists, scheme, {len(definitions)} project objects and 360 Kotlin traces are valid.')
print('NOT RUN: Swift compilation, XCTest, simulator/device and visual parity checks (requires Apple toolchain).')
