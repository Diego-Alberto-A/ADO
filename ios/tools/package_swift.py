"""Rebuild the checked-in Xcode project and native resources using Python and FFmpeg."""
from pathlib import Path
import hashlib, json, math, plistlib, shutil, struct, subprocess, wave

IOS=Path(__file__).resolve().parents[1]
ROOT=IOS/'Reference'
RES=IOS/'Crosswalker/Resources'
RES.mkdir(parents=True,exist_ok=True)
for source in (ROOT/'app/src/main/res/drawable-nodpi').glob('*.webp'):
    subprocess.run(['ffmpeg','-v','error','-y','-i',str(source),str(RES/(source.stem+'.png'))],check=True)
for source,name in [('app/src/main/assets/campaign.json','campaign.json'),('app/src/main/res/font/jersey_25.ttf','jersey_25.ttf'),('app/src/main/assets/licenses/Jersey25-OFL.txt','Jersey25-OFL.txt')]:
    shutil.copyfile(ROOT/source,RES/name)

# AOSP ToneGenerator: 400+1200 Hz beep and 1200 Hz acknowledgement.
# The original calls cap the acknowledgement at 180 ms (100 ms tone + 80 ms silence).
# https://android.googlesource.com/platform/frameworks/base/+/4dfbec2/media/libmedia/ToneGenerator.cpp
for name,duration,on,frequencies in [('click',0.04,0.04,[400,1200]),('ack',0.18,0.10,[1200])]:
    with wave.open(str(RES/(name+'.wav')),'wb') as output:
        output.setparams((1,2,44100,0,'NONE','not compressed'))
        samples=[]
        for i in range(round(duration*44100)):
            t=i/44100
            envelope=min(1,t/0.002,max(0,(on-t)/0.002)) if t < on else 0
            value=envelope*sum(math.sin(2*math.pi*f*t) for f in frequencies)/len(frequencies)
            samples.append(struct.pack('<h',round(value*24000)))
        output.writeframes(b''.join(samples))

catalog=IOS/'Crosswalker/Assets.xcassets'
icon=catalog/'AppIcon.appiconset'
icon.mkdir(parents=True,exist_ok=True)
(catalog/'Contents.json').write_text(json.dumps({'info':{'author':'xcode','version':1}},indent=2))
subprocess.run(['ffmpeg','-v','error','-y','-i',str(ROOT/'app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp'),'-vf','scale=1024:1024,format=rgb24',str(icon/'AppIcon.png')],check=True)
(icon/'Contents.json').write_text(json.dumps({'images':[{'filename':'AppIcon.png','idiom':'universal','platform':'ios','size':'1024x1024'}],'info':{'author':'xcode','version':1}},indent=2))
info={'CFBundleDevelopmentRegion':'es','CFBundleDisplayName':'Crosswalker','CFBundleExecutable':'$(EXECUTABLE_NAME)','CFBundleIdentifier':'$(PRODUCT_BUNDLE_IDENTIFIER)','CFBundleInfoDictionaryVersion':'6.0','CFBundleName':'$(PRODUCT_NAME)','CFBundlePackageType':'APPL','CFBundleShortVersionString':'1.0','CFBundleVersion':'1','LSRequiresIPhoneOS':True,'UIAppFonts':['jersey_25.ttf'],'UIApplicationSceneManifest':{'UIApplicationSupportsMultipleScenes':False},'UILaunchScreen':{},'UISupportedInterfaceOrientations':['UIInterfaceOrientationPortrait','UIInterfaceOrientationLandscapeLeft','UIInterfaceOrientationLandscapeRight'],'UISupportedInterfaceOrientations~ipad':['UIInterfaceOrientationPortrait','UIInterfaceOrientationPortraitUpsideDown','UIInterfaceOrientationLandscapeLeft','UIInterfaceOrientationLandscapeRight']}
info['UISupportedInterfaceOrientations']=['UIInterfaceOrientationPortrait']
info['UISupportedInterfaceOrientations~ipad']=['UIInterfaceOrientationPortrait']
info['UIRequiresFullScreen']=True  # Match the original Android portrait-only activity.
(IOS/'Crosswalker/Info.plist').write_bytes(plistlib.dumps(info))
privacy={'NSPrivacyTracking':False,'NSPrivacyTrackingDomains':[],'NSPrivacyCollectedDataTypes':[],'NSPrivacyAccessedAPITypes':[{'NSPrivacyAccessedAPIType':'NSPrivacyAccessedAPICategoryUserDefaults','NSPrivacyAccessedAPITypeReasons':['CA92.1']}]}
(RES/'PrivacyInfo.xcprivacy').write_bytes(plistlib.dumps(privacy))

def uid(s): return hashlib.sha256(s.encode()).hexdigest()[:24].upper()
def q(s): return json.dumps(str(s))
objects={}
def add(name,text):
    identifier=uid(name); objects[identifier]=text; return identifier
def array(values): return '('+','.join(values)+',)' if values else '()'
files={}
def ref(path,kind=None):
    if path in files: return files[path]
    kind=kind or ('sourcecode.swift' if path.endswith('.swift') else 'folder')
    files[path]=add('file:'+path,f'isa = PBXFileReference; lastKnownFileType = {q(kind)}; path = {q(path)}; sourceTree = "<group>";')
    return files[path]
def build(path): return add('build:'+path,f'isa = PBXBuildFile; fileRef = {files[path]};')
def phase(name,kind,paths):
    return add(name,f'isa = {kind}; buildActionMask = 2147483647; files = {array([build(p) for p in paths])}; runOnlyForDeploymentPostprocessing = 0;')
def configs(name,settings):
    ids=[]
    for mode in ['Debug','Release']:
        values=dict(settings)
        values.update({'SWIFT_OPTIMIZATION_LEVEL':'-Onone' if mode == 'Debug' else '-O','DEBUG_INFORMATION_FORMAT':'dwarf' if mode == 'Debug' else 'dwarf-with-dsym'})
        if mode == 'Debug': values.update({'ENABLE_TESTABILITY':'YES','SWIFT_ACTIVE_COMPILATION_CONDITIONS':'DEBUG'})
        config=add(name+mode,'isa = XCBuildConfiguration; buildSettings = {'+''.join(f'{key} = {q(value)};' for key,value in values.items())+'}; name = '+mode+';')
        ids.append(config)
    return add(name+'configs',f'isa = XCConfigurationList; buildConfigurations = {array(ids)}; defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;')

app_sources=[p.relative_to(IOS).as_posix() for p in sorted((IOS/'Crosswalker').rglob('*.swift'))]
tests=[p.relative_to(IOS).as_posix() for p in sorted((IOS/'CrosswalkerTests').glob('*.swift'))]
ui_tests=[p.relative_to(IOS).as_posix() for p in sorted((IOS/'CrosswalkerUITests').glob('*.swift'))]
for path in app_sources+tests+ui_tests: ref(path)
resources=[]
for p in sorted(RES.iterdir()):
    path=p.relative_to(IOS).as_posix()
    kind={'.png':'image.png','.json':'text.json','.ttf':'file','.txt':'text','.wav':'audio.wav','.xcprivacy':'text.xml'}.get(p.suffix,'file')
    ref(path,kind); resources.append(path)
ref('Crosswalker/Assets.xcassets','folder.assetcatalog'); resources.append('Crosswalker/Assets.xcassets')
ref('Crosswalker/Info.plist','text.plist.xml')
ref('CrosswalkerTests/Fixtures','folder')
product_ids=[]
target_ids=[]
project_id=uid('project')
for name,sources,resource_paths,product_type in [('Crosswalker',app_sources,resources,'application'),('CrosswalkerTests',tests,['CrosswalkerTests/Fixtures'],'bundle.unit-test'),('CrosswalkerUITests',ui_tests,[],'bundle.ui-testing')]:
    is_app=product_type == 'application'
    product=add(name+'product',f'isa = PBXFileReference; explicitFileType = {q("wrapper.application" if is_app else "wrapper.cfbundle")}; includeInIndex = 0; path = {q(name+(".app" if is_app else ".xctest"))}; sourceTree = BUILT_PRODUCTS_DIR;')
    product_ids.append(product)
    settings={'PRODUCT_NAME':'$(TARGET_NAME)','PRODUCT_BUNDLE_IDENTIFIER':'com.example.crosswalker'+('' if is_app else '.'+name),'SWIFT_VERSION':'5.0','IPHONEOS_DEPLOYMENT_TARGET':'16.0','TARGETED_DEVICE_FAMILY':'1,2','CODE_SIGN_STYLE':'Automatic','SDKROOT':'iphoneos','SUPPORTED_PLATFORMS':'iphoneos iphonesimulator','SUPPORTS_MACCATALYST':'NO','GENERATE_INFOPLIST_FILE':'NO' if is_app else 'YES','LD_RUNPATH_SEARCH_PATHS':'$(inherited) @executable_path/Frameworks @loader_path/Frameworks'}
    if is_app: settings.update({'INFOPLIST_FILE':'Crosswalker/Info.plist','ASSETCATALOG_COMPILER_APPICON_NAME':'AppIcon'})
    elif product_type == 'bundle.unit-test': settings.update({'TEST_HOST':'$(BUILT_PRODUCTS_DIR)/Crosswalker.app/Crosswalker','BUNDLE_LOADER':'$(TEST_HOST)'})
    else: settings['TEST_TARGET_NAME']='Crosswalker'
    phases=[phase(name+'sources','PBXSourcesBuildPhase',sources),phase(name+'frameworks','PBXFrameworksBuildPhase',[]),phase(name+'resources','PBXResourcesBuildPhase',resource_paths)]
    deps=[]
    if not is_app:
        proxy=add(name+'proxy',f'isa = PBXContainerItemProxy; containerPortal = {project_id}; proxyType = 1; remoteGlobalIDString = {uid("Crosswalkertarget")}; remoteInfo = Crosswalker;')
        deps.append(add(name+'dependency',f'isa = PBXTargetDependency; target = {uid("Crosswalkertarget")}; targetProxy = {proxy};'))
    target_ids.append(add(name+'target',f'isa = PBXNativeTarget; buildConfigurationList = {configs(name,settings)}; buildPhases = {array(phases)}; buildRules = (); dependencies = {array(deps)}; name = {name}; productName = {name}; productReference = {product}; productType = {q("com.apple.product-type."+product_type)};'))
products=add('products',f'isa = PBXGroup; children = {array(product_ids)}; name = Products; sourceTree = "<group>";')
main=add('main',f'isa = PBXGroup; children = {array(list(files.values())+[products])}; sourceTree = "<group>";')
project_config=configs('project',{'CLANG_ENABLE_MODULES':'YES','CLANG_ENABLE_OBJC_ARC':'YES','SWIFT_VERSION':'5.0','IPHONEOS_DEPLOYMENT_TARGET':'16.0','SDKROOT':'iphoneos'})
add('project',f'isa = PBXProject; attributes = {{ BuildIndependentTargetsInParallel = YES; LastUpgradeCheck = 1600; }}; buildConfigurationList = {project_config}; compatibilityVersion = "Xcode 14.0"; developmentRegion = es; hasScannedForEncodings = 0; knownRegions = (es,en,Base); mainGroup = {main}; productRefGroup = {products}; projectDirPath = ""; projectRoot = ""; targets = {array(target_ids)};')
project=IOS/'Crosswalker.xcodeproj'
project.mkdir(exist_ok=True)
(project/'project.pbxproj').write_text('// !$*UTF8*$!\n{archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n'+ '\n'.join(f'{key} = {{{value}}};' for key,value in objects.items())+'\n}; rootObject = '+project_id+'; }\n',encoding='utf-8')
def reference(name,ext): return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{uid(name+"target")}" BuildableName="{name}.{ext}" BlueprintName="{name}" ReferencedContainer="container:Crosswalker.xcodeproj"/>'
scheme=f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{reference('Crosswalker','app')}</BuildActionEntry></BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO">{reference('CrosswalkerTests','xctest')}</TestableReference><TestableReference skipped="NO">{reference('CrosswalkerUITests','xctest')}</TestableReference></Testables></TestAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{reference('Crosswalker','app')}</BuildableProductRunnable></LaunchAction>
<ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{reference('Crosswalker','app')}</BuildableProductRunnable></ProfileAction>
<AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>'''
schemes=project/'xcshareddata/xcschemes'; schemes.mkdir(parents=True,exist_ok=True)
(schemes/'Crosswalker.xcscheme').write_text(scheme,encoding='utf-8')
print(f'Packaged {len(app_sources)} Swift source files, {len(resources)} resources, and {len(tests)+len(ui_tests)} test files.')
