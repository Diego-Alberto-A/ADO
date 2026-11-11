"""Generate Swift parity fixtures by executing the original Kotlin engine (no reimplementation)."""
from pathlib import Path
import json, os, subprocess, shutil

IOS = Path(__file__).resolve().parents[1]
ROOT = IOS / 'Reference'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
OUT = IOS / 'CrosswalkerTests/Fixtures'
WORK = IOS / '.reference-build'
OUT.mkdir(parents=True, exist_ok=True)
WORK.mkdir(parents=True, exist_ok=True)
campaign = json.loads((ROOT / 'app/src/main/assets/campaign.json').read_text(encoding='utf-8'))
shutil.copyfile(ROOT / 'app/src/main/assets/campaign.json', OUT / 'campaign.json')

def q(value): return json.dumps(value, ensure_ascii=False)
def f(value): return str(float(value)) + 'f'
levels = []
for l in campaign['levels']:
    lanes = ','.join('Lane(' + ','.join([f(a['y']), f(a['speed']), f(a['offset']), q(a['car'])]) + ')' for a in l['lanes'])
    walkers = ','.join('Walker(' + ','.join([q(a['id']), q(a['name']), q(a['kind']), f(a['speed']), q(a['sprite'])]) + ')' for a in l['walkers'])
    levels.append(f'Level({l["id"]},{l["world"]},{q(l["title"])},{q(l["lesson"])},{l["budget"]},{str(l["night"]).lower()},listOf({lanes}),listOf({walkers}))')
tools = ','.join('Tool(' + ','.join(q(t[k]) if k != 'cost' else str(t[k]) for k in ['id','label','icon','cost','description','category']) + ')' for t in campaign['tools'])
source = r'''
import com.example.crosswalker.game.*
fun strings(values: Collection<String>) = values.joinToString(",", "[", "]") { "\"$it\"" }
fun main() {
    val levels = listOf(LEVELS)
    val tools = listOf(TOOLS)
    val results = mutableListOf<String>()
    val variants = listOf(setOf<String>(),setOf("look"),setOf("wait"),setOf("eye"),setOf("signal"),setOf("visible"),setOf("look","wait"),setOf("eye","visible"),setOf("look","signal"),setOf("look","visible"))
    for (l in levels) for (crossing in 0..2) for ((v,skills) in variants.withIndex()) {
        val street = when(v) { 6 -> mutableSetOf("school","bump"); 7 -> mutableSetOf("crosswalk"); 8 -> mutableSetOf("officer"); else -> mutableSetOf<String>() }
        val p = Plan(l.walkers.associate { it.id to Assignment(crossing,skills.toMutableSet()) }.toMutableMap(),street)
        val s = Simulation(l,p)
        val emergency = v == 9
        if(emergency) s.emergency()
        val samples = mutableListOf<String>()
        fun sample() {
            samples.add("""{"time":${s.time},"phase":"${s.phase}","y":${s.people.map { it.y }},"done":${s.people.map { it.done }},"cars":${l.lanes.map { s.carX(it) }}}""")
        }
        sample()
        var step = 0
        while(s.phase == Phase.RUNNING && step < 1802) { s.tick(); step++; if(step % 20 == 0 || s.phase != Phase.RUNNING) sample() }
        results.add("""{"level":${l.id},"crossing":$crossing,"skills":${strings(skills)},"street":${strings(street)},"emergency":$emergency,"stars":${s.stars(p.cost(tools))},"samples":[${samples.joinToString(",") }]}""")
    }
    print(results.joinToString(",","[","]"))
}
'''.replace('LEVELS', ','.join(levels)).replace('TOOLS', tools)
(WORK / 'Reference.kt').write_text(source, encoding='utf-8')

def jar(group, artifact, version):
    return next((CACHE / group / artifact / version).rglob(artifact + '-' + version + '.jar'))
jars = [jar('org.jetbrains.kotlin', a, '2.2.10') for a in ['kotlin-compiler-embeddable','kotlin-stdlib','kotlin-script-runtime','kotlin-reflect','kotlin-daemon-embeddable']]
jars += [jar('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0'), jar('org.jetbrains','annotations','13.0')]
classpath = os.pathsep.join(map(str,jars))
java = shutil.which('java') or 'C:/Program Files/Android/Android Studio/jbr/bin/java.exe'
subprocess.run([java,'-cp',classpath,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',classpath,'-d',str(WORK / 'reference.jar'),str(ROOT / 'app/src/main/java/com/example/crosswalker/game/Simulation.kt'),str(WORK / 'Reference.kt')],check=True)
result = subprocess.run([java,'-cp',str(WORK / 'reference.jar')+os.pathsep+classpath,'ReferenceKt'],capture_output=True,check=True,encoding='utf-8')
traces = json.loads(result.stdout)
assert len(traces) == 360
(OUT / 'trajectories.json').write_text(json.dumps(traces,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
print(f'Generated {len(traces)} Kotlin reference trajectories with {sum(len(t["samples"]) for t in traces)} checkpoints.')
