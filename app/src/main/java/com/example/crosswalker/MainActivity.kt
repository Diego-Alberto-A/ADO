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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer

class Session(val repo: GameRepository): ViewModel() {
    var transitioning by mutableStateOf(false)
    var transition: (ScreenMotion, () -> Unit) -> Unit = { _, action -> action() }
    fun navigate(destination: String) {
        if (destination == screen) return
        val returningFromCredits = screen == "credits" && destination == "home"
        val motion = when {
            destination == "credits" || returningFromCredits -> ScreenMotion.FADE
            destination == "home" || (screen == "game" && destination == "map") -> ScreenMotion.BACKWARD
            else -> ScreenMotion.FORWARD
        }
        transition(motion) {
            if (screen == "game") { sound?.stopOneShots(); simulation=null; paused=false; world=level.world }
            // Volver al menú (inicio o mapa) es lo único que vuelve a permitir música de fondo
            // después de una victoria.
            musicSuspended=false
            screen=destination
            if (returningFromCredits) dialog = "campaignComplete"
        }
    }
    // Suspend background music only while a result is being presented.
    var musicSuspended by mutableStateOf(false)
    fun changeWorld(next: Int) {
        if (!transitioning) world = next
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
    var planningPanelOpen by mutableStateOf(false)
    var introIndex by mutableIntStateOf(0)
    val mechanicGuide: Tool?
        get() {
            if (level.tutorial) return null
            val id = level.guideTools.getOrNull(introIndex) ?: return null
            return repo.tools.firstOrNull { it.id == id }
        }

    var sound: com.example.crosswalker.audio.SoundEngine? = null
    var showResultDialog by mutableStateOf(false)
    val tutorialController = TutorialController()
    val tutorialActive: Boolean get() = level.tutorial && !repo.tutorialDone
    fun enter(l: Level) {
        if (!repo.isLevelUnlocked(l.id)) return
        transition(ScreenMotion.FORWARD) {
            sound?.playSfx(R.raw.sfx_enter_level)
            level = l
            reset()
            screen = "game"
        }
    }
    var attempt by mutableIntStateOf(0)
    fun resumePlanning() { sound?.stopOneShots(); musicSuspended=false; showResultDialog=false; simulation=null; planningPanelOpen=false; attempt++ }
    fun reset() { resumePlanning(); plan=initialPlan(); selected=level.walkers.first().id; tab="skill"; simulation=null; paused=false; dialog=""; introIndex=0; showResultDialog=false; frame++ }
    private fun initialPlan(): Plan {
        if (level.initialCrossings.isNotEmpty()) {
            return Plan(level.walkers.zip(level.initialCrossings).associate { (walker, crossing) ->
                walker.id to Assignment(crossing)
            }.toMutableMap())
        }
        val available = listOf(1, 0, 2).filter { it != level.blockedCrossing }.toMutableList()
        val assignments = level.walkers.sortedBy { it.kind != "wheelchair" }.associate { walker ->
            val accessible = if (walker.kind == "wheelchair") available.firstOrNull { it in level.accessible } else null
            val crossing = accessible ?: available.first()
            available.remove(crossing)
            walker.id to Assignment(crossing)
        }
        return Plan(assignments.toMutableMap())
    }

    fun selectWalker(id: String) {
        if (id == selected) return
        selected = id
        tab = "skill"
    }

    fun chooseCrossing(crossing: Int) {
        if (crossing == level.blockedCrossing) return
        val next = plan.copyPlan()
        next.moveWalker(selected, crossing)
        plan = next
    }

    fun toggle(t: Tool) {
        val next=plan.copyPlan()
        val target=if(t.category=="street") next.street else next.people.getValue(selected).skills
        if(!target.remove(t.id)) target.add(t.id)
        if (next.cost(repo.tools) > level.budget) {
            dialog = "budget"
            return
        }
        plan = next
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
    val musicTrack = if (s.musicSuspended) null else
        com.example.crosswalker.audio.musicForScreen(s.screen, if (s.screen == "game") s.level.world else s.world)
    val gameplayAttempt = if (s.screen == "game") s.attempt else null
    LaunchedEffect(musicTrack, gameplayAttempt) {
        if (musicTrack != null) sound.playMusic(musicTrack, loop = musicTrack != R.raw.end_song) else sound.stopMusic()
    }
    // Rain ambience: its own loop, independent of the music track, only while actually
    // on a rainy level's board — stops the instant you leave it or pause.
    LaunchedEffect(s.screen, s.level.weather, s.musicSuspended) {
        if (s.screen == "game" && s.level.weather == "rain" && !s.musicSuspended) sound.loopAmbient(R.raw.rain)
        else sound.stopAmbient()
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
            if (sim.phase == Phase.WON && s.repo.sound) {
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 180)
            }
        }
    }
    BackHandler(enabled=s.screen!="home" || s.dialog.isNotEmpty()) {
        if(s.dialog.isNotEmpty()) s.dialog="" else if(s.screen=="game") s.paused=true else s.navigate("home")
    }
    val home = s.screen == "home"
    val screenBackdrop = if (home) InkShadow else Cream
    Box(Modifier.fillMaxSize().background(screenBackdrop).systemBarsPadding()) {
        if (!home) PaperGrain()
        CompositionLocalProvider(LocalVoice provides { t -> if(s.repo.sound) tone.startTone(t,18) }) {
        ScreenTransitions(s) {
            when(s.screen) {
                "home" -> Home(s, enabled=!s.transitioning) { click() }
                "map" -> WorldMap(s) { click() }
                "game" -> Game(s) { click() }
                "credits" -> CampaignCredits { click(); s.navigate("home") }
            }
        }
        }
        if (s.screen=="game" && s.tutorialActive) TutorialOverlay(s.tutorialController)
        if (s.dialog == "campaignComplete" && !s.transitioning) {
            PaperDialog("¡GRACIAS POR COMPLETAR EL JUEGO!", { s.dialog = "" }, accent = Taxi) {
                PaperButton("SEGUIR JUGANDO") { s.dialog = "" }
            }
        }
        if(s.dialog=="resetProgress") PaperDialog("¿EMPEZAR DE NUEVO?", {s.dialog=""}, accent=Taxi) {
            Text("Un nuevo camino te espera", style=TitleSmall, color=Ink)
            Text("Se borrarán tus estrellas y logros. Solo quedará abierto el primer nivel y volverás a ver el tutorial. Esta acción no se puede deshacer.", style=BodyText, color=Ink)
            Text("Tus ajustes de sonido y consejos se conservarán.", style=BodyText, color=Asphalt)
            PaperButton("CONSERVAR MI PROGRESO", Tree) { s.dialog="" }
            PaperButton("↻  SÍ, REINICIAR", Brick) {
                s.repo.resetProgress()
                s.world=0
                s.level=s.repo.levels.first()
                s.reset()
                s.dialog="progressReset"
            }
        }
        if(s.dialog=="progressReset") PaperDialog("¡UN NUEVO COMIENZO!", {s.dialog=""}, accent=Tree) {
            Text("Todo listo para tu primer cruce. Tus estrellas y logros vuelven a empezar.", style=BodyText, color=Ink)
            PaperButton("▶  JUGAR DESDE EL INICIO") { s.dialog=""; s.enter(s.repo.levels.first()) }
            PaperButton("VOLVER AL MENÚ", Neutral) { s.dialog="" }
        }
        if (s.dialog == "budget") {
            val remaining = s.level.budget - s.plan.cost(s.repo.tools)
            val title = if (remaining == 0) "NO TE QUEDAN FICHAS" else "NO TE ALCANZAN LAS FICHAS"
            PaperDialog(title, { s.dialog = "" }, accent = Taxi) {
                Text("Fichas disponibles: $remaining de ${s.level.budget}", style = HudStrong, color = Ink)
                Text("Quita alguna habilidad o mejora de la calle para recuperar fichas e intenta de nuevo.", style = BodyText, color = Ink)
                PaperButton("VOLVER AL PLAN") { s.dialog = "" }
            }
        }
        if(s.dialog.startsWith("tool:")) s.repo.tools.firstOrNull { it.id==s.dialog.substringAfter(":") }?.let { tool ->
            PaperDialog(tool.label.uppercase(), {s.dialog=""}, accent=Sky) {
                ToolArtwork(tool.id)
                Text("${tool.cost} fichas · ${if(tool.category=="street") "Mejora compartida de la calle" else "Habilidad para un peatón"}", style=HudStrong, color=Tree.dark)
                Text(tool.explanation(s.level), style=BodyText, color=Ink)
                PaperButton("ENTENDIDO") {s.dialog=""}
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
@Composable fun PaperButton(text:String,accent:Accent=Tree,enabled:Boolean=true,onClick:()->Unit) {
    PixelButton(text,accent,enabled,onClick=onClick)
}
@Composable fun WorldMap(s:Session,click:()->Unit) {
    var chosen by remember { mutableStateOf<Level?>(null) }
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        val completed = s.repo.levels.count { s.repo.stars(it.id) > 0 }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PixelButton(
                text = "‹ INICIO",
                accent = Neutral,
                modifier = Modifier.weight(1f),
                onClickLabel = "Volver al inicio"
            ) {
                click()
                s.navigate("home")
            }
            Column(
                Modifier.weight(1.4f).pixelPanelDark(corner = 4.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("$completed / ${s.repo.levels.size}", style = TitleSmall, color = Taxi.light)
                Text("Niveles completados", style = BodyStrong, color = CreamLight,
                    textAlign = TextAlign.Center)
            }
        }
        Text(s.repo.worlds[s.world],style=TitleText,color=Ink)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { s.repo.worlds.indices.forEach { w -> CraftChip(selected=s.world==w,onClick={s.changeWorld(w)},label={Text("MUNDO ${w+1}")}) } }
        AnimatedContent(
            targetState = s.world,
            modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { direction * it / 6 } +
                    fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -direction * it / 6 } +
                        fadeOut(tween(180)))
            },
            label = "worldLevels"
        ) { world ->
            val active = world == s.world
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = active)
                    .then(if (active) Modifier else Modifier.clearAndSetSemantics {}),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                s.repo.levels.filter { it.world == world }.forEachIndexed { index,l ->
                    val unlocked = s.repo.isLevelUnlocked(l.id)
                    val card = if (unlocked) {
                        Modifier.pixelPanel().clickable(enabled = active) { click(); chosen = l }
                    } else {
                        Modifier.pixelPanelDark().clearAndSetSemantics {
                            contentDescription = "Nivel ${l.id}, ${l.title}, no disponible"
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(start = if (index % 2 == 0) 0.dp else 35.dp, end = if (index % 2 == 0) 35.dp else 0.dp)
                            .rotate(if (index % 2 == 0) -1f else 1f)
                            .then(card)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val slot = if (unlocked) Modifier.pixelSlot() else Modifier.background(Ink)
                        Box(Modifier.size(60.dp).then(slot), contentAlignment = Alignment.Center) {
                            Text("${l.id}", color = if (unlocked) Ink else KerbLight, style = TitleText)
                        }
                        if (unlocked) {
                            Column(Modifier.weight(1f)) {
                                Text(l.title, style = BodyStrong, color = Ink)
                                PaperStars(s.repo.stars(l.id))
                            }
                            val sceneryIcon = when (l.world) {
                                1 -> R.drawable.decor_building_campus
                                else -> if (index % 2 == 0) R.drawable.house else R.drawable.tree
                            }
                            PixelImage(sceneryIcon, null, Modifier.size(38.dp).rotate(3f))
                        } else {
                            Text(l.title, modifier = Modifier.weight(1f), style = BodyStrong, color = KerbLight)
                            LevelLock()
                        }
                    }
                    if(index<3) Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                        val start=if(index%2==0) size.width*.27f else size.width*.72f
                        val end=if(index%2==0) size.width*.72f else size.width*.27f
                        val thread=androidx.compose.ui.graphics.Path().apply {moveTo(start,0f);cubicTo(start,size.height*.8f,end,size.height*.2f,end,size.height)}
                        drawPath(thread,Brick.base,style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx()))
                    }
                }
            }
        }
    }
    chosen?.let { l -> PaperDialog("NIVEL ${l.id}",{chosen=null}) {
        Text(l.title,style=TitleText,color=Ink)
        Text("★ Llega al otro lado\n★★ Resuelve sin choques\n★★★ Usa pocas fichas",style=BodyText,color=Ink)
        PaperButton("▶  PREPARAR EL PLAN") {chosen=null;s.enter(l)}
    } }
}

@Composable
private fun LevelLock() {
    Canvas(Modifier.size(38.dp)) {
        val unit = size.width / 19f
        drawRect(KerbLight, Offset(6f * unit, 2f * unit), Size(7f * unit, 2f * unit))
        drawRect(KerbLight, Offset(5f * unit, 4f * unit), Size(2f * unit, 5f * unit))
        drawRect(KerbLight, Offset(12f * unit, 4f * unit), Size(2f * unit, 5f * unit))
        drawRect(Kerb, Offset(3f * unit, 8f * unit), Size(13f * unit, 9f * unit))
        drawRect(Ink, Offset(9f * unit, 11f * unit), Size(unit, 3f * unit))
    }
}

@Composable fun Game(s:Session,click:()->Unit) {
    val frame=s.frame
    val sim=s.simulation
    val planning=sim==null
    val selectedWalker=s.level.walkers.first { it.id==s.selected }
    val cost=s.plan.cost(s.repo.tools)
    val lookChosen="look" in s.plan.people.getValue(s.selected).skills
    s.tutorialController.step = if (planning && s.tutorialActive)
        tutorialStepFor(s.introIndex, s.level.introLines.size, lookChosen, s.planningPanelOpen) else null
    LaunchedEffect(sim, sim?.phase) {
        val engine = s.sound
        when (sim?.phase) {
            Phase.RETRY -> {
                s.musicSuspended = true
                engine?.stopMusic()
                if (sim.collapsedWalkerId != null) engine?.playSfx(R.raw.sfx_collapse)
                engine?.playOneShot(R.raw.music_gameover)
                // Show the collapse first; keep the result timing independent of audio settings.
                delay(900)
                s.showResultDialog = true
            }
            Phase.WON -> {
                s.repo.save(sim.level.id, sim.stars(sim.plan.cost(s.repo.tools)))
                s.musicSuspended = true
                engine?.stopMusic()
                engine?.playOneShot(R.raw.music_win)
                delay(900)
                if (s.level.id == 12) s.navigate("credits") else s.showResultDialog = true
            }
            else -> {}
        }
    }
    Box(Modifier.fillMaxSize()) {
        Board(s.level, s.plan, sim, s.selected, planning,
            Modifier.matchParentSize().tutorialTarget("board", s.tutorialController) {}, frame) { crossing ->
            s.chooseCrossing(crossing)
            click()
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.tutorialTarget("pause", s.tutorialController) { s.paused = true }) {
                    HudButton("Ⅱ", "Pausa") { s.paused = true }
                }
                Spacer(Modifier.weight(1f))
                TokenCounter(s.level.budget - cost, s.level.budget, Modifier.tutorialTarget("tokens", s.tutorialController) {})
            }
            if (planning) {
                PlanningGuide(s, selectedWalker)
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val showingMap = s.tutorialActive && s.introIndex == MAP_INTRO_INDEX
                    val togglePanel = {
                        s.planningPanelOpen = !s.planningPanelOpen
                        if (s.planningPanelOpen && showingMap) s.introIndex++
                        click()
                    }
                    PlanningTab(s.planningPanelOpen, s.mechanicGuide != null, s.tutorialController, togglePanel)
                    AnimatedVisibility(
                        visible = s.planningPanelOpen,
                        modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                        enter = expandVertically(tween(240), expandFrom = Alignment.Bottom) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Bottom) + fadeOut(tween(140))
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            Box(
                                Modifier.matchParentSize().graphicsLayer { alpha = .72f }
                                    .pixelPanel(fill = Ink, highlight = AsphaltEdge, shade = InkShadow, corner = 8.dp)
                            )
                            Column(
                                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PlanningControls(s, click)
                            }
                        }
                    }
                    val launch = {
                        click()
                        if (s.tutorialActive) s.repo.tutorialDone = true
                        s.simulation = Simulation(s.level, s.plan)
                    }
                    val canLaunch = !s.tutorialActive || s.tutorialController.step?.tapId == "launch"
                    PixelButton(
                        text = "PONER EL PLAN EN MARCHA",
                        enabled = canLaunch,
                        modifier = Modifier.widthIn(max = 304.dp).padding(top = 6.dp)
                            .tutorialTarget("launch", s.tutorialController, launch),
                        leading = {
                            Box(
                                Modifier.size(28.dp).background(Ink.copy(alpha = .22f), RoundedCornerShape(5.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("▶", style = HudStrong, color = if (canLaunch) CreamLight else Ink)
                            }
                        },
                        onClick = launch
                    )
                }
            }
        }
        if (sim != null && !s.level.tutorial) {
            Text(
                sim.feedback.ifEmpty { "Observa cada carril: los coches necesitan tiempo y espacio para detenerse." },
                style = BodyText,
                color = Ink,
                modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp)
                    .pixelPanel(fill = Cream.copy(alpha = .9f), corner = 4.dp).padding(8.dp)
            )
        }
    }
    if((sim?.phase==Phase.RETRY || sim?.phase==Phase.WON) && s.showResultDialog) {
        val won=sim.phase==Phase.WON
        PaperDialog(if(won) "¡BUEN CAMINO!" else "OTRO PLAN, OTRA OPORTUNIDAD",{},dismissible=false,accent=if(won) Tree else Brick) {
            if(won) Row { repeat(3) { i -> PixelImage(R.drawable.star,if(i<sim.stars(cost)) "Estrella obtenida" else "Estrella pendiente",Modifier.size(52.dp),alpha=if(i<sim.stars(cost)) 1f else .25f) } } else Text("↻",fontSize=52.sp,color=Taxi.dark)
            Text(if(won) "¡Todos llegaron a salvo!" else if(sim.collapsedWalkerId!=null) "¡Cuidado! Hubo un choque." else "Necesitamos un plan más seguro.",style=TitleSmall,color=Ink)
            Text(if(won) "Usaste $cost de ${s.level.budget} fichas. Tus buenas decisiones hacen una ciudad más segura." else sim.reason,style=BodyText,color=Ink)
            if (won) s.repo.nextLevel(s.level.id)?.let { next ->
                PaperButton("SIGUIENTE CAMINO  →") { s.enter(next) }
            }
            PaperButton(if(won) "VOLVER A INTENTAR" else "✎  MEJORAR MI PLAN",if(won) Neutral else Tree) {s.resumePlanning()}
            PaperButton("VOLVER AL MAPA",Neutral) {s.navigate("map")}
        }
    }
}
@Composable
private fun PlanningTab(open: Boolean, guideActive: Boolean, tutorial: TutorialController, toggle: () -> Unit) {
    Box(Modifier.mechanicHint(guideActive && !open).tutorialTarget("planning_panel", tutorial, toggle)) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val arrow by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "planningArrow")
        Row(
            Modifier.heightIn(min = 48.dp)
                .graphicsLayer { translationY = if (pressed) 2.dp.toPx() else 0f }
                .pixelPanel(fill = if (open) Asphalt else Taxi.base,
                    highlight = if (open) AsphaltEdge else Taxi.light,
                    shade = if (open) InkShadow else Taxi.dark, corner = 6.dp)
                .semantics { stateDescription = if (open) "Abierto" else "Cerrado" }
                .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = toggle)
                .padding(start = 12.dp, end = if (open) 16.dp else 46.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(26.dp).background(Taxi.light, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("▲", style = HudStrong, color = Ink, modifier = Modifier.rotate(arrow))
            }
            Text(if (open) "CERRAR PLAN" else "ABRIR PLAN", style = HudStrong, color = if (open) CreamLight else Ink)
        }
        if (!open && tutorial.step == null) {
            val motion = rememberClickMotion()
            val hand = ImageBitmap.imageResource(R.drawable.pointer_hand)
            Canvas(Modifier.matchParentSize()) {
                drawClickHand(hand, Offset(size.width - 37.dp.toPx(), size.height * .60f), 28.dp.toPx(), motion.value)
            }
        }
    }
}

@Composable
private fun PlanningControls(s: Session, click: () -> Unit) {
    val assignment = s.plan.people.getValue(s.selected)
    Row(
        Modifier.fillMaxWidth().tutorialTarget("walkers", s.tutorialController) {}
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        s.level.walkers.forEach { walker ->
            val selected = s.selected == walker.id
            val hasSkills = s.plan.people.getValue(walker.id).skills.isNotEmpty()
            Row(
                Modifier.heightIn(min = 44.dp)
                    .pixelPanel(fill = if (selected) Cream else Asphalt, corner = 5.dp)
                    .selectable(selected = selected, role = Role.Tab, onClick = { s.selectWalker(walker.id); click() })
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                WalkerArtwork(walker, 28.dp, s.plan.people.getValue(walker.id).skills, child = s.level.showsChildBadge(walker))
                Text(walker.name + if (hasSkills) " ✓" else "", style = HudStrong,
                    color = if (selected) Ink else CreamLight)
            }
        }
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Cruce", style = HudStrong, color = CreamLight, modifier = Modifier.padding(end = 4.dp))
        listOf("Izq.", "Centro", "Der.").forEachIndexed { index, label ->
            val choose = {
                s.chooseCrossing(index)
                if (s.tutorialActive) s.introIndex++
                click()
            }
            val occupant = s.level.walkers.firstOrNull {
                it.id != s.selected && s.plan.people[it.id]?.crossing == index
            }
            val crossingStatus = if (s.level.night && s.level.pedestrianCrossings.isNotEmpty() && index != s.level.blockedCrossing) {
                if (s.level.hasMarkedCrossing(index)) "Con paso" else "Sin paso"
            } else null
            PlanningSegment(
                label = if (index == s.level.blockedCrossing) "Cerrado" else label,
                subtitle = crossingStatus,
                selected = assignment.crossing == index,
                enabled = index != s.level.blockedCrossing,
                modifier = Modifier.weight(1f).tutorialTarget("crossing", s.tutorialController, choose)
                    .semantics {
                        val descriptions = listOfNotNull(
                            if (occupant != null) "Ocupado por ${occupant.name}. Toca para intercambiar." else null,
                            crossingStatus?.let { "$it peatonal" }
                        )
                        if (descriptions.isNotEmpty()) stateDescription = descriptions.joinToString(". ")
                    },
                onClick = choose
            )
        }
    }

    val guide = s.mechanicGuide
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("skill" to "HABILIDADES", "street" to "LA CALLE").forEach { (category, label) ->
            val choose = { s.tab = category; click() }
            PlanningSegment(
                label = label,
                selected = s.tab == category,
                modifier = Modifier.weight(1f)
                    .tutorialTarget("tab_$category", s.tutorialController, choose)
                    .mechanicHint(guide?.category == category && s.tab != category),
                leading = {
                    if (category == "street") PixelImage(R.drawable.house, null, Modifier.size(18.dp))
                },
                onClick = choose
            )
        }
    }

    Crossfade(targetState = s.tab, animationSpec = tween(160), label = "toolCategory") { category ->
        val scroll = rememberScrollState()
        val tools = s.repo.tools.filter { it.category == category && it.id in s.level.tools }
        Box {
            Row(
                Modifier.fillMaxWidth().padding(end = if (scroll.canScrollForward) 14.dp else 0.dp)
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tools.forEach { tool ->
                    val active = tool.id in if (category == "street") s.plan.street else assignment.skills
                    PlanningCard(s, tool, active, guide?.id == tool.id, click)
                }
            }
            if (scroll.canScrollForward) {
                Box(Modifier.align(Alignment.CenterEnd).rotate(-90f)) { MoreTextArrow(Taxi.light) }
            }
        }
    }
}

@Composable
private fun PlanningSegment(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    leading: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier.heightIn(min = 44.dp)
            .pixelPanel(fill = if (selected) Taxi.light else Asphalt, corner = 4.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.CenterHorizontally) {
            val color = if (selected) Ink else CreamLight
            Text(label, style = HudStrong, color = color, textAlign = TextAlign.Center)
            if (subtitle != null) Text(subtitle, style = HudLabel, color = color, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PlanningCard(s: Session, tool: Tool, active: Boolean, guided: Boolean, click: () -> Unit) {
    val choose = { s.toggle(tool); click() }
    val explain = { s.dialog = "tool:${tool.id}"; click() }
    Column(
        Modifier.width(124.dp)
            .mechanicHint(guided)
            .pixelPanel(fill = if (active) Tree.light else CreamLight, shade = if (active) Tree.dark else CreamDark, corner = 6.dp)
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier.fillMaxWidth()
                .tutorialTarget(if (tool.id == "look") "tool_look" else "", s.tutorialController, choose)
                .selectable(selected = active, role = Role.Checkbox, onClick = choose),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolArtwork(tool.id)
                Text(
                    if (active) "✓ ${tool.cost}" else "${tool.cost} ●",
                    style = HudStrong, color = Ink,
                    modifier = Modifier.background(if (active) Tree.base else Taxi.light, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            Text(tool.label.uppercase(), style = HudLabel, color = Ink, minLines = 2, textAlign = TextAlign.Center)
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .tutorialTarget("tool_help", s.tutorialController, explain)
                .pixelPanel(fill = Sky.light, corner = 4.dp)
                .clickable(role = Role.Button, onClickLabel = "Explicar ${tool.label}", onClick = explain)
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("¿Para qué sirve?", style = HudLabel, color = Ink, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PlanningGuide(s: Session, selectedWalker: Walker) {
    val waitingForPanel = s.tutorialActive && !s.planningPanelOpen
    val level = s.level
    val page = level.introLines.getOrNull(s.introIndex)
    val guideWalker = level.walkers.firstOrNull().takeUnless { level.tutorial } ?: selectedWalker
    val displayMessage = when {
        waitingForPanel && s.introIndex == MAP_INTRO_INDEX -> level.introLines[MAP_INTRO_INDEX]
        waitingForPanel -> "Toca la pestaña con la mano para abrir el plan. Vuelve a tocarla cuando quieras cerrar el panel."
        page != null -> page
        !level.tutorial || "look" !in s.plan.people.getValue(s.selected).skills -> level.lesson
        else -> level.readyMessage
    }
    val advanceDialogue = {
        if (s.introIndex < level.introLines.lastIndex) {
            s.introIndex++
            if (s.tutorialActive && s.introIndex == MAP_INTRO_INDEX) s.planningPanelOpen = false
        }
    }
    val onAdvance = advanceDialogue.takeIf { !waitingForPanel && s.introIndex < level.introLines.lastIndex }
    if (s.repo.guidance || s.tutorialActive || (!level.tutorial && page != null)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                .tutorialTarget("dialogue", s.tutorialController, advanceDialogue),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WalkerArtwork(guideWalker, 48.dp, s.plan.people.getValue(guideWalker.id).skills, child = level.showsChildBadge(guideWalker))
            DialogueBox(
                displayMessage, guideWalker.name, walkerAccent(guideWalker.sprite),
                remember(s.level, s.repo.tools) { Lexicon.of(s.level, s.repo.tools) },
                modifier = Modifier.weight(1f),
                blip = blip(guideWalker.id), onAdvance = onAdvance, voice = s.sound,
                showTapHint = !s.tutorialActive
            )
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

