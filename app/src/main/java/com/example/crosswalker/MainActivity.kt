package com.example.crosswalker

import android.os.Bundle
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.crosswalker.ui.theme.Jersey25
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.crosswalker.data.GameRepository
import com.example.crosswalker.game.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer

val Ink = Color(0xFF493E30)
val Paper = Color(0xFFF5E6C5)
val Kraft = Color(0xFFCAA574)
val Green = Color(0xFF637F57)
val Coral = Color(0xFFBA5845)
val Gold = Color(0xFFD9AC49)

class Session(val repo: GameRepository): ViewModel() {
    var transitioning by mutableStateOf(false)
    var transition: (ScreenMotion, () -> Unit) -> Unit = { _, action -> action() }
    fun navigate(destination: String) {
        if (destination != screen) transition(if (destination == "home" || (screen == "game" && destination == "map")) ScreenMotion.BACKWARD else ScreenMotion.FORWARD) {
            if (screen == "game") { simulation=null; paused=false; world=level.world }
            screen=destination
        }
    }
    fun changeWorld(next: Int) {
        if (next != world) transition(if (next < world) ScreenMotion.WORLD_BACKWARD else ScreenMotion.WORLD_FORWARD) { world=next }
    }
    var screen by mutableStateOf("home")
    var world by mutableIntStateOf(0)
    var level by mutableStateOf(repo.levels.first())
    var plan by mutableStateOf(Plan())
    var selected by mutableStateOf("ana")
    var simulation by mutableStateOf<Simulation?>(null)
    var frame by mutableIntStateOf(0)
    var paused by mutableStateOf(false)
    var dialog by mutableStateOf("")
    var tab by mutableStateOf("skill")
    var message by mutableStateOf("")
    fun enter(l: Level) { transition(ScreenMotion.FORWARD) { level=l; reset(); screen="game" } }
    fun reset() { plan=Plan(level.walkers.associate { it.id to Assignment() }.toMutableMap()); selected=level.walkers.first().id; simulation=null; paused=false; dialog=""; message=level.lesson; frame++ }
    fun toggle(t: Tool) {
        val next=plan.copyPlan()
        val target=if(t.category=="street") next.street else next.people.getValue(selected).skills
        if(!target.remove(t.id)) target.add(t.id)
        if(next.cost(repo.tools)>level.budget) { message="No quedan fichas. Quita una acción para probar otra."; return }
        plan=next; message=t.description
    }
}
class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo=GameRepository(applicationContext)
        val session=ViewModelProvider(this,object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T = Session(repo) as T
        })[Session::class.java]
        setContent { MaterialTheme(typography=com.example.crosswalker.ui.theme.Typography,colorScheme=lightColorScheme(primary=Green,secondary=Coral,background=Paper,surface=Paper,onSurface=Ink,onPrimary=Paper)) { Crosswalker(session) } }
    }
}
@Composable fun Crosswalker(s:Session) {
    val context=LocalContext.current
    val tone=remember { ToneGenerator(AudioManager.STREAM_MUSIC,35) }
    fun click() { if(s.repo.sound) tone.startTone(ToneGenerator.TONE_PROP_BEEP,65) }
    DisposableEffect(Unit) {
        val owner=context as ComponentActivity
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP && s.simulation?.phase==Phase.RUNNING) s.paused=true }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); tone.release() }
    }
    LaunchedEffect(s.simulation,s.paused,s.transitioning) {
        val sim=s.simulation
        if(sim!=null && !s.paused && !s.transitioning) while(sim.phase==Phase.RUNNING) {
            delay(50); sim.tick(); s.frame++
            if(sim.phase==Phase.WON) { s.repo.save(s.level.id,sim.stars(s.plan.cost(s.repo.tools))); if(s.repo.sound) tone.startTone(ToneGenerator.TONE_PROP_ACK,180) }
        }
    }
    BackHandler(enabled=s.screen!="home" || s.dialog.isNotEmpty()) {
        if(s.dialog.isNotEmpty()) s.dialog="" else if(s.screen=="game") s.paused=true else s.navigate("home")
    }
    Box(Modifier.fillMaxSize().background(Paper).systemBarsPadding()) {
        PaperGrain()
        ScreenTransitions(s) {
            when(s.screen) {
                "home" -> Home(s, enabled=!s.transitioning) { click() }
                "map" -> WorldMap(s) { click() }
                "game" -> Game(s) { click() }
            }
        }
        if(s.dialog=="settings") PaperDialog("A TU MANERA", {s.dialog=""}) {
            var sound by remember { mutableStateOf(s.repo.sound) }
            var guidance by remember { mutableStateOf(s.repo.guidance) }
            Text("Pequeños ajustes para tu aventura",color=Ink)
            Row(verticalAlignment=Alignment.CenterVertically) { Text("Sonidos",Modifier.weight(1f)); Switch(sound,{sound=it;s.repo.sound=it}) }
            Row(verticalAlignment=Alignment.CenterVertically) { Text("Consejos en pantalla",Modifier.weight(1f)); Switch(guidance,{guidance=it;s.repo.guidance=it}) }
            PaperButton("LISTO") { s.dialog="" }
        }
        if(s.dialog=="achievements") PaperDialog("MIS LOGROS",{s.dialog=""}) {
            val completed=s.repo.levels.count { s.repo.stars(it.id)>0 }
            Text("★  ${s.repo.levels.sumOf { s.repo.stars(it.id) }} / 36 estrellas",fontSize=28.sp,color=Green)
            Text("${completed} de 12 caminos seguros",color=Ink)
            listOf("Primer paso" to (completed>0),"Mi barrio seguro" to (completed>=4),"Gran planificador" to (completed>=8),"Guardián de la ciudad" to (completed==12)).forEach { (name,earned) -> Text("${if(earned) "★" else "○"}   $name",fontSize=18.sp,color=if(earned) Green else Ink.copy(alpha=.45f)) }
            PaperButton("VOLVER") { s.dialog="" }
        }
        if(s.paused && s.screen=="game" && s.dialog.isEmpty()) PaperDialog("PAUSA",{s.paused=false}) {
            Text("Tu maqueta te espera.",color=Ink)
            PaperButton("▶  CONTINUAR") {s.paused=false}
            PaperButton("↻  REINTENTAR",Coral) {s.reset()}
            PaperButton("AJUSTES",Kraft) {s.dialog="settings"}
            PaperButton("VOLVER AL MAPA",Kraft) {s.navigate("map")}
        }
    }
}
@Composable fun PaperGrain() {
    Image(painterResource(R.drawable.material_cream), contentDescription=null,
        modifier=Modifier.fillMaxSize(), contentScale=ContentScale.Crop)
}
@Composable fun Logo(letterSize: androidx.compose.ui.unit.TextUnit = 29.sp) {
    Row(horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
        "CROSSWALKER".forEachIndexed { i,c ->
            Text(c.toString(),fontSize=letterSize,fontWeight=FontWeight.Black,
                color=listOf(Coral,Green,Color(0xFF537C96),Ink)[i%4],
                modifier=Modifier.rotate(if(i%2==0)-4f else 3f).craftPaper().padding(horizontal=2.dp,vertical=3.dp),
                fontFamily=Jersey25)
        }
    }
}
@Composable fun Eyebrow(text:String) { Text(text,letterSpacing=2.sp,fontSize=10.sp,fontWeight=FontWeight.Bold,color=Ink.copy(alpha=.7f)) }
@Composable fun PaperButton(text:String,color:Color=Green,enabled:Boolean=true,onClick:()->Unit) {
    val modalAction=LocalModalAction.current
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(if(pressed) .97f else 1f, tween(100), label="buttonPress")
    Box(Modifier.fillMaxWidth().heightIn(min=54.dp).alpha(if(enabled)1f else .45f)
        .graphicsLayer { scaleX=buttonScale; scaleY=buttonScale }
        .craftPaper(color==Green).border(1.dp,Paper.copy(alpha=.65f),CutPaper)
        .clickable(interactionSource=interaction,indication=LocalIndication.current,enabled=enabled,role=Role.Button,onClick={modalAction(onClick)}).padding(15.dp),
        contentAlignment=Alignment.Center) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(text.startsWith("✋")) Image(painterResource(R.drawable.stop_sign),null,Modifier.size(25.dp))
            Text(text.removePrefix("✋").trim(),color=if(color==Green)Paper else if(color==Coral)Color(0xFF76352A) else Ink,
                fontWeight=FontWeight.ExtraBold,letterSpacing=1.sp,fontFamily=Jersey25)
        }
    }
}
@Composable fun WorldMap(s:Session,click:()->Unit) {
    var chosen by remember { mutableStateOf<Level?>(null) }
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { TextButton(onClick={s.navigate("home")}) {Text("‹ INICIO",color=Ink)}; Spacer(Modifier.weight(1f));Text("★ ${s.repo.levels.sumOf { s.repo.stars(it.id) }} / 36",color=Ink,fontWeight=FontWeight.Bold) }
        Eyebrow("ELIGE TU PRÓXIMA AVENTURA")
        Text(s.repo.worlds[s.world],fontFamily=Jersey25,fontSize=30.sp,fontWeight=FontWeight.Bold,color=Ink)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { s.repo.worlds.indices.forEach { w -> CraftChip(selected=s.world==w,onClick={s.changeWorld(w)},label={Text("MUNDO ${w+1}")}) } }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            s.repo.levels.filter { it.world==s.world }.forEachIndexed { index,l ->
                Row(Modifier.fillMaxWidth().padding(start=if(index%2==0)0.dp else 35.dp,end=if(index%2==0)35.dp else 0.dp).rotate(if(index%2==0)-1f else 1f).craftPaper().border(1.dp,Paper.copy(alpha=.5f),CutPaper).clickable(enabled=s.repo.unlocked(l.id)) { click();chosen=l }.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(62.dp).clip(RoundedCornerShape(32.dp)).paint(painterResource(R.drawable.material_kraft),sizeToIntrinsics=false,contentScale=ContentScale.Crop).border(3.dp,Paper,RoundedCornerShape(32.dp)),contentAlignment=Alignment.Center) { Text(if(s.repo.unlocked(l.id)) "${l.id}" else "—",color=Ink,fontWeight=FontWeight.Bold,fontSize=27.sp) }
                    Column(Modifier.weight(1f)) {Text(l.title,fontWeight=FontWeight.Bold,color=Ink,fontSize=17.sp);if(s.repo.unlocked(l.id)) PaperStars(s.repo.stars(l.id)) else Text("Completa el nivel anterior",color=Ink.copy(alpha=.65f),fontSize=12.sp) }
                    Image(painterResource(if(index%2==0) R.drawable.house else R.drawable.tree),null,Modifier.size(38.dp).rotate(3f))
                }
                if(index<3) Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                    val start=if(index%2==0) size.width*.27f else size.width*.72f
                    val end=if(index%2==0) size.width*.72f else size.width*.27f
                    val thread=androidx.compose.ui.graphics.Path().apply {moveTo(start,0f);cubicTo(start,size.height*.8f,end,size.height*.2f,end,size.height)}
                    drawPath(thread,Coral.copy(alpha=.75f),style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx()))
                }
            }
        }
        Text("Cada buen plan abre un nuevo camino.",fontFamily=Jersey25,color=Ink,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
    }
    chosen?.let { l -> PaperDialog("NIVEL ${l.id}",{chosen=null}) {
        Text(l.title,fontSize=25.sp,fontWeight=FontWeight.Bold,fontFamily=Jersey25,color=Ink)
        Text(l.lesson,color=Ink,lineHeight=23.sp)
        Text("☺ ${l.walkers.size} peatones    ● ${l.budget} fichas",color=Green,fontWeight=FontWeight.Bold)
        Text("★ Llega al otro lado\n★★ Resuelve sin ayuda de emergencia\n★★★ Usa pocas fichas",lineHeight=27.sp,color=Ink)
        PaperButton("▶  PREPARAR EL PLAN") {chosen=null;s.enter(l)}
    } }
}
@Composable fun Game(s:Session,click:()->Unit) {
    val frame=s.frame
    val sim=s.simulation
    val planning=sim==null
    val selectedWalker=s.level.walkers.first { it.id==s.selected }
    val cost=s.plan.cost(s.repo.tools)
    Column(Modifier.fillMaxSize().padding(horizontal=14.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={s.paused=true}) {Text("Ⅱ",fontSize=24.sp,color=Ink)}
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) { Eyebrow("NIVEL ${s.level.id} · MUNDO ${s.level.world+1}");Text(s.level.title,color=Ink,fontWeight=FontWeight.Bold) }
            Text("● ${s.level.budget-cost}/${s.level.budget}",color=Ink,fontWeight=FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Eyebrow(if(planning) "01 / PREPARA TU PLAN" else "02 / OBSERVA TU PLAN");Text(if(planning) "Sin límite de tiempo" else "${sim!!.people.count { it.done }} / ${sim.people.size} a salvo",fontSize=11.sp,color=Green) }
        Board(s.level,s.plan,sim,s.selected,planning,Modifier.fillMaxWidth().weight(1f).heightIn(min=180.dp),frame) { crossing -> val next=s.plan.copyPlan();next.people.getValue(s.selected).crossing=crossing;s.plan=next;click() }
        AnimatedContent(targetState=planning, label="planningControls",
            transitionSpec={ fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { preparing ->
        Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
        if(preparing) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { s.level.walkers.forEach { w -> CraftChip(selected=s.selected==w.id,onClick={s.selected=w.id;click()},label={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {WalkerArtwork(w);Text(w.name+if(s.plan.people.getValue(w.id).skills.isNotEmpty()) " ✓" else "")}}) } }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) { WalkerArtwork(selectedWalker,24.dp);Text("Cruce",fontSize=12.sp,color=Ink);listOf("Izq.","Centro","Der.").forEachIndexed { i,label -> CraftChip(selected=s.plan.people.getValue(s.selected).crossing==i,onClick={val next=s.plan.copyPlan();next.people.getValue(s.selected).crossing=i;s.plan=next},label={Text(label)}) } }
            Row { CraftChip(selected=s.tab=="skill",onClick={s.tab="skill"},label={Row(verticalAlignment=Alignment.CenterVertically) {WalkerArtwork(selectedWalker,24.dp);Spacer(Modifier.width(6.dp));Text("HABILIDADES")}});Spacer(Modifier.width(8.dp));CraftChip(selected=s.tab=="street",onClick={s.tab="street"},label={Row(verticalAlignment=Alignment.CenterVertically) {Image(painterResource(R.drawable.house),null,Modifier.size(21.dp));Spacer(Modifier.width(6.dp));Text("LA CALLE")}}) }
            Crossfade(targetState=s.tab, animationSpec=tween(180), label="toolCategory") { category ->
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { s.repo.tools.filter {it.category==category}.forEach { t ->
                val active=if(t.category=="street") t.id in s.plan.street else t.id in s.plan.people.getValue(s.selected).skills
                Column(Modifier.width(87.dp).craftPaper(active).border(1.dp,if(active)Paper else Kraft,CutPaper).clickable {s.toggle(t);click()}.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally) { ToolArtwork(t.id);Text(t.label,fontSize=11.sp,color=if(active)Paper else Ink,maxLines=1);Text("${if(active) "✓" else "+"} ${t.cost} ●",fontSize=11.sp,color=if(active)Paper else Ink) }
            } }
            }
            if(s.repo.guidance) Text(s.message,color=Ink,fontSize=12.sp,lineHeight=16.sp,modifier=Modifier.heightIn(min=32.dp),maxLines=3)
            PaperButton("▶   PONER EL PLAN EN MARCHA") {click();s.simulation=Simulation(s.level,s.plan)}
        } else if(sim!=null) {
            Text("Observa cómo cada decisión cambia el recorrido.",fontSize=12.sp,color=Ink,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
            PaperButton(if(sim!!.emergencyUsed) "AYUDA UTILIZADA" else "✋  PARAR TRÁFICO · 1 USO",Coral,enabled=!sim.emergencyUsed && sim.phase==Phase.RUNNING) {sim.emergency();s.frame++;click()}
        }
        }
        }
    }
    if(sim?.phase==Phase.WON || sim?.phase==Phase.RETRY) {
        val won=sim.phase==Phase.WON
        PaperDialog(if(won) "¡BUEN CAMINO!" else "OTRO PLAN, OTRA OPORTUNIDAD",{},dismissible=false) {
            if(won) Row { repeat(3) { i -> Image(painterResource(R.drawable.star),contentDescription=if(i<sim.stars(cost)) "Estrella obtenida" else "Estrella pendiente",modifier=Modifier.size(52.dp),alpha=if(i<sim.stars(cost)) 1f else .25f) } } else Text("↻",fontSize=52.sp,color=Gold)
            Text(if(won) "¡Todos llegaron a salvo!" else "Nos detenemos a tiempo.",fontSize=23.sp,fontFamily=Jersey25,fontWeight=FontWeight.Bold,color=Ink)
            Text(if(won) "Usaste $cost de ${s.level.budget} fichas. Tus buenas decisiones hacen una ciudad más segura." else sim.reason,color=Ink,lineHeight=23.sp)
            if(won && s.level.id<s.repo.levels.size) PaperButton("SIGUIENTE CAMINO  →") {s.enter(s.repo.levels[s.level.id])}
            PaperButton(if(won) "VOLVER A INTENTAR" else "✎  MEJORAR MI PLAN",if(won)Kraft else Green) {s.simulation=null;s.message=s.level.lesson}
            PaperButton("VOLVER AL MAPA",Kraft) {s.navigate("map")}
        }
    }
}
@Composable fun PaperDialog(title:String,onDismiss:()->Unit,dismissible:Boolean=true,content:@Composable ColumnScope.()->Unit) {
    val motion=remember { Animatable(0f) }
    val scope=rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { motion.animateTo(1f,tween(260,easing=FastOutSlowInEasing)) }
    val leave: (() -> Unit) -> Unit = { action ->
        if(!closing) {
            closing=true
            scope.launch {
                motion.animateTo(0f,tween(170,easing=FastOutSlowInEasing))
                action()
            }
        }
    }
    Dialog(onDismissRequest={if(dismissible) leave(onDismiss)}) {
        CompositionLocalProvider(LocalModalAction provides leave) {
            Box {
                Column(Modifier.fillMaxWidth().graphicsLayer {
                    alpha=motion.value
                    scaleX=.96f+.04f*motion.value; scaleY=scaleX
                    translationY=20.dp.toPx()*(1f-motion.value)
                    rotationZ=-1.2f*(1f-motion.value)
                }.then(if(closing) Modifier.clearAndSetSemantics {} else Modifier)
                    .craftPaper().border(2.dp,Paper,CutPaper).padding(20.dp)
                    .verticalScroll(rememberScrollState()).animateContentSize(),
                    verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(title,fontWeight=FontWeight.Black,letterSpacing=1.sp,color=Ink,textAlign=TextAlign.Center)
                    HorizontalDivider(color=Paper);content()
                }
                if(closing) Box(Modifier.matchParentSize().clickable(interactionSource=null,indication=null) {}.clearAndSetSemantics {})
            }
        }
    }
}
