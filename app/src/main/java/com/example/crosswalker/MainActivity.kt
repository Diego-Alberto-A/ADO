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
import com.example.crosswalker.ui.theme.*
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
import androidx.compose.animation.core.keyframes
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
    var introIndex by mutableIntStateOf(0)
    var sound: com.example.crosswalker.audio.SoundEngine? = null
    fun enter(l: Level) { transition(ScreenMotion.FORWARD) { sound?.playSfx(R.raw.sfx_enter_level); level=l; reset(); screen="game" } }
    fun reset() { plan=Plan(level.walkers.associate { it.id to Assignment() }.toMutableMap()); selected=level.walkers.first().id; tab="skill"; simulation=null; paused=false; dialog=""; message=level.lesson; introIndex=0; frame++ }
    fun toggle(t: Tool) {
        val next=plan.copyPlan()
        val target=if(t.category=="street") next.street else next.people.getValue(selected).skills
        if(!target.remove(t.id)) target.add(t.id)
        if(next.cost(repo.tools)>level.budget) { message="No quedan fichas. Quita una acción para probar otra."; return }
        plan=next; if(!level.tutorial) message=t.description
    }
}
class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo=GameRepository(applicationContext)
        val session=ViewModelProvider(this,object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T = Session(repo) as T
        })[Session::class.java]
        setContent { MaterialTheme(typography=Typography,colorScheme=lightColorScheme(primary=Tree.base,secondary=Brick.base,background=Cream,surface=Cream,onSurface=Ink,onPrimary=CreamLight)) { Crosswalker(session) } }
    }
}
@Composable fun Crosswalker(s:Session) {
    val context=LocalContext.current
    val tone=remember { ToneGenerator(AudioManager.STREAM_MUSIC,35) }
    val sound=remember { com.example.crosswalker.audio.SoundEngine(context) { s.repo.sound } }
    LaunchedEffect(Unit) { s.sound = sound }
    fun click() { sound.playSfx(R.raw.sfx_button) }
    // La clave incluye `s.level.world` además de `s.world`: "SIGUIENTE CAMINO" puede llevar de un
    // nivel a otro de distinto mundo sin pasar por el mapa, así que `s.screen` se queda en "game" y
    // `s.world` (la pestaña del mapa) no cambia — solo `s.level.world` lo hace.
    LaunchedEffect(s.screen, s.world, s.level.world) {
        com.example.crosswalker.audio.musicForScreen(s.screen, if (s.screen=="game") s.level.world else s.world)
            ?.let { sound.playMusic(it) }
    }
    DisposableEffect(Unit) {
        val owner=context as ComponentActivity
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP && s.simulation?.phase==Phase.RUNNING) s.paused=true }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); tone.release(); sound.release() }
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
    Box(Modifier.fillMaxSize().background(Cream).systemBarsPadding()) {
        PaperGrain()
        CompositionLocalProvider(LocalVoice provides { t -> if(s.repo.sound) tone.startTone(t,18) }) {
        ScreenTransitions(s) {
            when(s.screen) {
                "home" -> Home(s, enabled=!s.transitioning) { click() }
                "map" -> WorldMap(s) { click() }
                "game" -> Game(s) { click() }
            }
        }
        }
        if(s.dialog=="settings") PaperDialog("A TU MANERA", {s.dialog=""}) {
            var sound by remember { mutableStateOf(s.repo.sound) }
            var guidance by remember { mutableStateOf(s.repo.guidance) }
            Text("Pequeños ajustes para tu aventura",style=BodyText,color=Asphalt)
            Row(verticalAlignment=Alignment.CenterVertically) { Text("Sonidos",Modifier.weight(1f),style=BodyText,color=Ink); Switch(sound,{sound=it;s.repo.sound=it}) }
            Row(verticalAlignment=Alignment.CenterVertically) { Text("Consejos en pantalla",Modifier.weight(1f),style=BodyText,color=Ink); Switch(guidance,{guidance=it;s.repo.guidance=it}) }
            PaperButton("LISTO") { s.dialog="" }
        }
        if(s.dialog=="achievements") PaperDialog("MIS LOGROS",{s.dialog=""}) {
            val completed=s.repo.levels.count { s.repo.stars(it.id)>0 }
            val earned=s.repo.levels.sumOf { s.repo.stars(it.id) }
            Text("★  $earned / 36 estrellas",style=TitleText,color=Orange.dark)
            Box(Modifier.fillMaxWidth().pixelPanelDark(corner=2.dp).padding(4.dp)) {
                PixelBar(earned/36f,Orange,Modifier.fillMaxWidth())
            }
            Text("${completed} de 12 caminos seguros",style=BodyText,color=Asphalt)
            listOf("Primer paso" to (completed>0),"Mi barrio seguro" to (completed>=4),"Gran planificador" to (completed>=8),"Guardián de la ciudad" to (completed==12)).forEach { (name,earned) -> Text("${if(earned) "★" else "○"}   $name",style=BodyText,color=if(earned) Tree.dark else Kerb) }
            PaperButton("VOLVER") { s.dialog="" }
        }
        if(s.paused && s.screen=="game" && s.dialog.isEmpty()) PaperDialog("PAUSA",{s.paused=false},accent=Taxi) {
            Text("Tu maqueta te espera.",style=BodyText,color=Asphalt)
            PaperButton("▶  CONTINUAR") {s.paused=false}
            PaperButton("↻  REINTENTAR",Brick) {s.reset()}
            PaperButton("AJUSTES",Neutral) {s.dialog="settings"}
            PaperButton("VOLVER AL MAPA",Neutral) {s.navigate("map")}
        }
    }
}
@Composable fun PaperGrain() {
    // Flat cream with a 2x2 dither so the ground has texture without noise.
    Box(Modifier.fillMaxSize().background(Cream).ditherVeil(rememberDitherShader(CreamDark), scale=4f))
}
@Composable fun Logo(letterSize: androidx.compose.ui.unit.TextUnit = 29.sp) {
    Row(horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
        val accents=listOf(Brick,Tree,Sky,Taxi)
        "CROSSWALKER".forEachIndexed { i,c ->
            val accent=accents[i%4]
            Box(Modifier.rotate(if(i%2==0)-4f else 3f)
                .pixelPanel(fill=accent.base,highlight=accent.light,shade=accent.dark,corner=2.dp)
                .padding(horizontal=4.dp,vertical=4.dp)) {
                Text(c.toString(),fontSize=letterSize,color=textOn(accent.base),fontFamily=Jersey10)
            }
        }
    }
}
@Composable fun Eyebrow(text:String) { Text(text.uppercase(),style=HudStrong,letterSpacing=2.sp,color=Asphalt) }
@Composable fun PaperButton(text:String,accent:Accent=Tree,enabled:Boolean=true,onClick:()->Unit) {
    val stop=text.startsWith("\u270B")
    PixelButton(text.removePrefix("\u270B").trim(),accent,enabled,
        leading=if(stop) ({ PixelImage(R.drawable.icon_officer,null,Modifier.size(25.dp)) }) else null,
        onClick=onClick)
}
@Composable fun WorldMap(s:Session,click:()->Unit) {
    var chosen by remember { mutableStateOf<Level?>(null) }
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { TextButton(onClick={s.navigate("home")}) {Text("‹ INICIO",style=HudStrong,color=Asphalt)}; Spacer(Modifier.weight(1f));Text("★ ${s.repo.levels.sumOf { s.repo.stars(it.id) }} / 36",style=HudStrong,color=Orange.dark) }
        Eyebrow("ELIGE TU PRÓXIMA AVENTURA")
        Text(s.repo.worlds[s.world],style=TitleText,color=Ink)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { s.repo.worlds.indices.forEach { w -> CraftChip(selected=s.world==w,onClick={s.changeWorld(w)},label={Text("MUNDO ${w+1}")}) } }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            s.repo.levels.filter { it.world==s.world }.forEachIndexed { index,l ->
                val open=s.repo.unlocked(l.id)
                Row(Modifier.fillMaxWidth().padding(start=if(index%2==0)0.dp else 35.dp,end=if(index%2==0)35.dp else 0.dp).rotate(if(index%2==0)-1f else 1f).pixelPanel(fill=if(open)Cream else KerbLight,highlight=if(open)CreamLight else Kerb,shade=CreamDark).clickable(enabled=open) { click();chosen=l }.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(60.dp).pixelSlot(),contentAlignment=Alignment.Center) { Text(if(open) "${l.id}" else "—",color=if(open)Ink else Kerb,style=TitleText) }
                    Column(Modifier.weight(1f)) {Text(l.title,style=BodyStrong,color=if(open)Ink else Asphalt);if(open) PaperStars(s.repo.stars(l.id)) else Text("Completa el nivel anterior",color=Asphalt,style=HudText) }
                    PixelImage(if(index%2==0) R.drawable.house else R.drawable.tree,null,Modifier.size(38.dp).rotate(3f))
                }
                if(index<3) Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                    val start=if(index%2==0) size.width*.27f else size.width*.72f
                    val end=if(index%2==0) size.width*.72f else size.width*.27f
                    val thread=androidx.compose.ui.graphics.Path().apply {moveTo(start,0f);cubicTo(start,size.height*.8f,end,size.height*.2f,end,size.height)}
                    drawPath(thread,Brick.base,style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx()))
                }
            }
        }
        Text("Cada buen plan abre un nuevo camino.",style=BodyText,color=Asphalt,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
    }
    chosen?.let { l -> PaperDialog("NIVEL ${l.id}",{chosen=null}) {
        Text(l.title,style=TitleText,color=Ink)
        // The tutorial's `lesson` is an in-game nudge; the card shows its opening line instead.
        Text(if(l.tutorial) l.introLines.getOrElse(1) { l.lesson } else l.lesson,style=BodyText,color=Ink)
        Text("☺ ${l.walkers.size} PEATONES    ● ${l.budget} FICHAS",style=HudStrong,color=Tree.dark)
        Text("★ Llega al otro lado\n★★ Resuelve sin ayuda de emergencia\n★★★ Usa pocas fichas",style=BodyText,color=Ink)
        if(l.newTools.isNotEmpty()) {
            Text("FICHA NUEVA",style=HudStrong,color=Orange.dark)
            l.newTools.forEach { id -> val t=s.repo.tools.first { it.id==id }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { ToolArtwork(t.id);Column { Text(t.label,style=BodyStrong,color=Ink);Text(t.description,style=BodyText,color=Ink) } }
            }
        }
        PaperButton("▶  PREPARAR EL PLAN") {chosen=null;s.enter(l)}
    } }
}
@Composable fun Game(s:Session,click:()->Unit) {
    val frame=s.frame
    val sim=s.simulation
    val planning=sim==null
    val selectedWalker=s.level.walkers.first { it.id==s.selected }
    val cost=s.plan.cost(s.repo.tools)
    // No title, no phase label: the board runs to the top edge and the only
    // chrome left floats over it — pause on one side, the budget on the other.
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Board(s.level,s.plan,sim,s.selected,planning,Modifier.fillMaxWidth().weight(1f).heightIn(min=180.dp),frame) { crossing -> val next=s.plan.copyPlan();next.people.getValue(s.selected).crossing=crossing;s.plan=next;click() }
        AnimatedContent(targetState=planning, label="planningControls",
            transitionSpec={ fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { preparing ->
        Column(Modifier.padding(horizontal=14.dp).padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        if(preparing) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { s.level.walkers.forEach { w -> CraftChip(selected=s.selected==w.id,onClick={s.selected=w.id;click()},label={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {if(s.selected==w.id) FocusCursor(Ink);WalkerArtwork(w);Text(w.name+if(s.plan.people.getValue(w.id).skills.isNotEmpty()) " ✓" else "")}}) } }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) { WalkerArtwork(selectedWalker,24.dp);Text("Cruce",style=BodyText,color=Ink);listOf("Izq.","Centro","Der.").forEachIndexed { i,label -> CraftChip(selected=s.plan.people.getValue(s.selected).crossing==i,onClick={val next=s.plan.copyPlan();next.people.getValue(s.selected).crossing=i;s.plan=next},label={Text(label)}) } }
            Row { CraftChip(selected=s.tab=="skill",onClick={s.tab="skill"},label={Row(verticalAlignment=Alignment.CenterVertically) {WalkerArtwork(selectedWalker,24.dp);Spacer(Modifier.width(6.dp));Text("HABILIDADES")}});Spacer(Modifier.width(8.dp));CraftChip(selected=s.tab=="street",onClick={s.tab="street"},label={Row(verticalAlignment=Alignment.CenterVertically) {PixelImage(R.drawable.house,null,Modifier.size(21.dp));Spacer(Modifier.width(6.dp));Text("LA CALLE")}}) }
            Crossfade(targetState=s.tab, animationSpec=tween(180), label="toolCategory") { category ->
            val toolScroll=rememberScrollState()
            Box(Modifier.fillMaxWidth()) {
            // When the cards overflow, leave a gutter on the right for the "more" arrow.
            Row(Modifier.fillMaxWidth().padding(end=if(toolScroll.maxValue>0) 16.dp else 0.dp).horizontalScroll(toolScroll),horizontalArrangement=Arrangement.spacedBy(8.dp)) { s.repo.tools.filter {it.category==category && it.id in s.level.tools}.forEach { t ->
                val active=if(t.category=="street") t.id in s.plan.street else t.id in s.plan.people.getValue(s.selected).skills
                Column(Modifier.width(84.dp).pixelPanel(fill=if(active)Taxi.base else Cream,highlight=if(active)Taxi.light else CreamLight,shade=if(active)Taxi.dark else CreamDark).clickable {s.toggle(t);click()}.padding(horizontal=4.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) { ToolArtwork(t.id);Text(t.label.uppercase(),style=HudLabel,color=Ink,minLines=2,maxLines=2,textAlign=TextAlign.Center);Text("${if(active) "✓" else "+"} ${t.cost} ●",style=HudStrong,color=if(active)Ink else Orange.dark) }
            } }
            // A fifth card can hide past the right edge: blink an arrow in the gutter until the player scrolls.
            if(toolScroll.canScrollForward) Box(Modifier.align(Alignment.CenterEnd).rotate(-90f)) { MoreTextArrow(Orange.dark,HudStrong.copy(fontSize=14.sp,lineHeight=14.sp)) }
            }
            }
            // The tutorial drives the box itself: a tap-through intro, then a
            // nudge that follows whatever the player has done so far.
            val introDone=!s.level.tutorial || s.introIndex>=s.level.introLines.size
            val displayMessage=when {
                s.level.tutorial && !introDone -> s.level.introLines[s.introIndex]
                s.level.tutorial && "look" !in s.plan.people.getValue(s.selected).skills -> s.level.lesson
                s.level.tutorial -> s.level.readyMessage
                else -> s.message
            }
            val onAdvance:(()->Unit)?=if(s.level.tutorial && !introDone) ({ s.introIndex++ }) else null
            if(s.repo.guidance) DialogueBox(displayMessage,selectedWalker.name,walkerAccent(selectedWalker.sprite),
                remember(s.level,s.repo.tools) { Lexicon.of(s.level,s.repo.tools) },
                Modifier.heightIn(min=56.dp),blip=blip(selectedWalker.id),onAdvance=onAdvance)
            PaperButton("▶   PONER EL PLAN EN MARCHA") {click();s.simulation=Simulation(s.level,s.plan)}
        } else if(sim!=null) {
            PaperButton(if(sim!!.emergencyUsed) "AYUDA UTILIZADA" else "✋  PARAR TRÁFICO · 1 USO",Brick,enabled=!sim.emergencyUsed && sim.phase==Phase.RUNNING) {sim.emergency();s.frame++;click()}
        }
        }
        }
    }
        Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.Top) {
            HudButton("Ⅱ","Pausa") { s.paused=true }
            Spacer(Modifier.weight(1f))
            TokenCounter(s.level.budget-cost,s.level.budget)
        }
    }
    if(sim?.phase==Phase.WON || sim?.phase==Phase.RETRY) {
        val won=sim.phase==Phase.WON
        PaperDialog(if(won) "¡BUEN CAMINO!" else "OTRO PLAN, OTRA OPORTUNIDAD",{},dismissible=false,accent=if(won) Tree else Brick) {
            if(won) Row { repeat(3) { i -> PixelImage(R.drawable.star,if(i<sim.stars(cost)) "Estrella obtenida" else "Estrella pendiente",Modifier.size(52.dp),alpha=if(i<sim.stars(cost)) 1f else .25f) } } else Text("↻",fontSize=52.sp,color=Taxi.dark)
            Text(if(won) "¡Todos llegaron a salvo!" else "Nos detenemos a tiempo.",style=TitleSmall,color=Ink)
            Text(if(won) "Usaste $cost de ${s.level.budget} fichas. Tus buenas decisiones hacen una ciudad más segura." else sim.reason,style=BodyText,color=Ink)
            if(won && s.level.id<s.repo.levels.size) PaperButton("SIGUIENTE CAMINO  →") {s.enter(s.repo.levels[s.level.id])}
            PaperButton(if(won) "VOLVER A INTENTAR" else "✎  MEJORAR MI PLAN",if(won) Neutral else Tree) {s.simulation=null;s.message=s.level.lesson}
            PaperButton("VOLVER AL MAPA",Neutral) {s.navigate("map")}
        }
    }
}
@Composable fun PaperDialog(title:String,onDismiss:()->Unit,dismissible:Boolean=true,accent:Accent=Sky,content:@Composable ColumnScope.()->Unit) {
    val motion=remember { Animatable(0f) }
    val scale=remember { Animatable(.9f) }
    val scope=rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        scope.launch { motion.animateTo(1f,tween(200,easing=FastOutSlowInEasing)) }
        // 0.9 -> 1.05 -> 1: the box lands with a bounce, as a paper card would.
        scale.animateTo(1f,keyframes { durationMillis=200; .9f at 0; 1.05f at 120; 1f at 200 })
    }
    val leave: (() -> Unit) -> Unit = { action ->
        if(!closing) {
            closing=true
            scope.launch {
                scope.launch { scale.animateTo(.94f,tween(140,easing=EaseOutBack)) }
                motion.animateTo(0f,tween(140,easing=FastOutSlowInEasing))
                action()
            }
        }
    }
    Dialog(onDismissRequest={if(dismissible) leave(onDismiss)}) {
        CompositionLocalProvider(LocalModalAction provides leave) {
            Box {
                Column(Modifier.fillMaxWidth().padding(top=8.dp).graphicsLayer {
                    alpha=motion.value
                    scaleX=scale.value; scaleY=scale.value
                }.then(if(closing) Modifier.clearAndSetSemantics {} else Modifier)
                    .pixelPanel(fill=Cream,innerBorder=CreamDark,corner=4.dp).padding(20.dp)
                    .verticalScroll(rememberScrollState()).animateContentSize(),
                    verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(4.dp));content()
                }
                // Name plate in the Animal Crossing manner: tilted, breaking out of the corner.
                Box(Modifier.align(Alignment.TopStart).absoluteOffset(x=12.dp).graphicsLayer { alpha=motion.value }
                    .rotate(-4f).pixelPanel(fill=accent.base,highlight=accent.light,shade=accent.dark,corner=3.dp)
                    .padding(horizontal=10.dp,vertical=3.dp)) {
                    Text(title,style=HudStrong,color=textOn(accent.base),textAlign=TextAlign.Center)
                }
                if(closing) Box(Modifier.matchParentSize().clickable(interactionSource=null,indication=null) {}.clearAndSetSemantics {})
            }
        }
    }
}
