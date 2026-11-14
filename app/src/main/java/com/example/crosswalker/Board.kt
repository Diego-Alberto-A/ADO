package com.example.crosswalker

import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.DashPathEffect
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.crosswalker.game.*

/** Asphalt, kerb and markings, sampled from the Kenney tiles the sprites come from. */
private const val ASPHALT = "#5C6278"
private const val MARKING = "#A09CCA"
private const val INK = "#2B2540"
private const val WORLD_TEXT = "#FFF8E6"

/** One source pixel of the tileset, in board units. */
private const val PIXEL = 1f

/** Scene rendering is independent of the fixed-step simulation. */
@Composable fun Board(level:Level,plan:Plan,sim:Simulation?,selected:String,planning:Boolean,modifier:Modifier=Modifier,frame:Int=0,onCrossing:(Int)->Unit={}) {
    val wearingHeadphones = level.walkers.any { it.hasHeadphonesOn(plan.people[it.id]?.skills.orEmpty()) }
    val musicPhase = if (wearingHeadphones && (planning || sim?.phase == Phase.RUNNING)) {
        rememberInfiniteTransition(label = "headphoneNotes").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2100, easing = LinearEasing)),
            label = "noteRise"
        )
    } else null
    // Rain must fall even while planning or paused, when `frame` (driven by the running
    // simulation's tick loop) never changes — its own always-on clock, independent of
    // game state, only ticking at all when there's actually rain to animate.
    var rainTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(level.weather) {
        if(level.weather=="rain") while(true) { delay(50); rainTick++ }
    }
    // A short, self-paced clock for the result "impact" animation: starts over every
    // time a fresh Simulation lands on WON/RETRY, independent of `frame` — which stops
    // ticking the instant the phase leaves RUNNING, so it alone couldn't drive this.
    var impactTick by remember { mutableIntStateOf(30) }
    LaunchedEffect(sim, sim?.phase) {
        if(sim?.phase==Phase.WON || sim?.phase==Phase.RETRY) {
            impactTick=0
            while(impactTick<30) { delay(16); impactTick++ }
        }
    }
    val impactT=minOf(1f,impactTick/30f)
    val scenery = remember(level.world, level.id) { boardScenery(level.world, level.id) }
    val context=androidx.compose.ui.platform.LocalContext.current
    val typeface=remember(context) { androidx.core.content.res.ResourcesCompat.getFont(context,R.font.silkscreen) }
    val resources=LocalResources.current
    val sprites=remember(resources) {
        mapOf("car_green" to R.drawable.car_green,"car_coral" to R.drawable.car_coral,"car_yellow" to R.drawable.car_yellow,"car_blue" to R.drawable.car_blue,
            "walker_coral" to R.drawable.walker_coral,"walker_blue" to R.drawable.walker_blue,"walker_green" to R.drawable.walker_green,
            "walker_coral_w0" to R.drawable.walker_coral_w0,"walker_coral_w1" to R.drawable.walker_coral_w1,"walker_coral_w2" to R.drawable.walker_coral_w2,
            "walker_blue_w0" to R.drawable.walker_blue_w0,"walker_blue_w1" to R.drawable.walker_blue_w1,"walker_blue_w2" to R.drawable.walker_blue_w2,
            "walker_green_w0" to R.drawable.walker_green_w0,"walker_green_w1" to R.drawable.walker_green_w1,"walker_green_w2" to R.drawable.walker_green_w2,
            "walker_chair" to R.drawable.walker_chair,"walker_chair_w0" to R.drawable.walker_chair_w0,"walker_chair_w1" to R.drawable.walker_chair_w1,"walker_chair_w2" to R.drawable.walker_chair_w2,
            "walker_slate" to R.drawable.walker_slate,"walker_slate_w0" to R.drawable.walker_slate_w0,"walker_slate_w1" to R.drawable.walker_slate_w1,"walker_slate_w2" to R.drawable.walker_slate_w2,
            "walker_rust" to R.drawable.walker_rust,"walker_rust_w0" to R.drawable.walker_rust_w0,"walker_rust_w1" to R.drawable.walker_rust_w1,"walker_rust_w2" to R.drawable.walker_rust_w2,
            "phone" to R.drawable.phone,"headphones" to R.drawable.headphones,
            "tree" to R.drawable.tree,"tree_autumn" to R.drawable.tree_autumn,
            "stop" to R.drawable.stop_sign,
            "decor_fence_barrio" to R.drawable.decor_fence_barrio,"decor_mailbox_barrio" to R.drawable.decor_mailbox_barrio,
            "decor_bikerack_campus" to R.drawable.decor_bikerack_campus,
            "obstacle_barricade_barrio" to R.drawable.obstacle_barricade_barrio,"obstacle_cone_campus" to R.drawable.obstacle_cone_campus,
            "ramp" to R.drawable.ramp)
            .mapValues { resources.getDrawable(it.value, null)!!.apply { setFilterBitmap(false) } }
    }
    // Ground textures tile at 16 board units, matching one source tile of the pack.
    val grounds=remember(resources) {
        mapOf("grass" to R.drawable.ground_grass,"road" to R.drawable.ground_road,"walk" to R.drawable.ground_walk,
        ).mapValues { (_,id) ->
            val bitmap=BitmapFactory.decodeResource(resources,id)
            BitmapShader(bitmap,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT).apply {
                setLocalMatrix(Matrix().apply { setScale(16f/bitmap.width,16f/bitmap.height) })
            }
        }
    }
    Canvas(modifier.semantics { contentDescription="${level.title}. Nivel ${level.id}, mundo ${level.world+1}. Usa los botones de cruce para elegir izquierda, centro o derecha." }.pointerInput(planning,selected) { if(planning) detectTapGestures { onCrossing(when { it.x<size.width*.375f->0;it.x<size.width*.625f->1;else->2 }) } }) {
        @Suppress("UNUSED_VARIABLE") val redraw=frame
        val c=drawContext.canvas.nativeCanvas
        c.save();c.scale(size.width/360f,size.height/460f);c.clipRect(0f,0f,360f,460f)
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        // Pixel art must never be smoothed; every upscale stays nearest-neighbour.
        p.isFilterBitmap=false
        fun color(hex:String) {p.color=android.graphics.Color.parseColor(hex);p.style=Paint.Style.FILL;p.pathEffect=null;p.shader=null}
        fun rect(x:Float,y:Float,w:Float,h:Float,hex:String) {color(hex);c.drawRect(x,y,x+w,y+h,p)}
        fun sprite(id:String,x:Float,y:Float,w:Float,h:Float, fitted: ((RectF) -> Unit)? = null) {sprites[id]?.let {
            val scaleRatio=(size.width/360f)/(size.height/460f)
            val aspect=it.intrinsicWidth.toFloat()/it.intrinsicHeight
            val drawH=minOf(h,w*scaleRatio/aspect)
            val drawW=drawH*aspect/scaleRatio
            val left=x+(w-drawW)/2
            it.setBounds(left.toInt(),(y+h-drawH).toInt(),(left+drawW).toInt(),(y+h).toInt());it.draw(c)
            if (fitted != null) fitted(RectF(it.bounds))
        }}
        /** World text carries a 1 px ink outline so it reads over grass or asphalt. */
        fun label(text:String,x:Float,y:Float,size:Float,hex:String) {
            p.textSize=size;p.typeface=typeface;p.textAlign=Paint.Align.CENTER
            color(INK);p.style=Paint.Style.STROKE;p.strokeWidth=2f;c.drawText(text,x,y,p)
            color(hex);c.drawText(text,x,y,p)
        }
        /** Flat tiled ground: pixel tiles stay square, so no cut-paper silhouette here. */
        fun ground(x:Float,y:Float,w:Float,h:Float,name:String) {
            color("#FFFFFF");p.shader=grounds.getValue(name)
            c.drawRect(x,y,x+w,y+h,p);p.shader=null
        }
        fun zebraCrossing(centerX: Float, laneY: Float) {
            val left = centerX - 19f
            // Remove the dashed lane divider inside the crosswalk so the paint reads as one pattern.
            ground(left, laneY - 30f, 38f, 64f, "road")
            repeat(6) { stripe ->
                val top = laneY - 26f + stripe * 10f
                rect(left, top, 38f, 6f, "#FFF8E6")
                rect(left, top + 5f, 38f, 1f, "#D6D7E0")
            }
        }

        /** Cuts a precise, alpha-matched hole in the current dark overlay layer around one
         * sprite — the PNG's own transparency outlines the hole, so the edge reads as a
         * deliberate spotlight, never a cropped rectangle — plus a tinted, oversized copy of
         * the same silhouette drawn underneath as a soft glow ring that marks who/what is
         * being called out. Takes the exact same (x,y,w,h) box `sprite()` was drawn with
         * above, and replicates its placement formula bit-for-bit: `sprite()` only centres
         * the art horizontally within that box, and anchors it to the box's *bottom* edge
         * (never vertically centred) whenever the box is wider than the art's own aspect
         * ratio allows — a mismatch here is exactly what made earlier cutouts drift off the
         * actual sprite, worst on wide ones like cars. */
        fun revealSprite(id:String,x:Float,y:Float,w:Float,h:Float,flip:Boolean,glowColor:String,glowAlpha:Int,glowScale:Float) {
            val bmp=(sprites[id] as? BitmapDrawable)?.bitmap ?: return
            val scaleRatio=(size.width/360f)/(size.height/460f)
            val aspect=bmp.width.toFloat()/bmp.height
            val drawH=minOf(h,w*scaleRatio/aspect); val drawW=drawH*aspect/scaleRatio
            val left=x+(w-drawW)/2; val top=y+h-drawH
            val cx=x+w/2; val cy=top+drawH/2
            c.save();if(flip) c.scale(-1f,1f,cx,0f)
            p.colorFilter=PorterDuffColorFilter(android.graphics.Color.parseColor(glowColor),PorterDuff.Mode.SRC_IN)
            p.alpha=glowAlpha
            val gw=drawW*glowScale; val gh=drawH*glowScale
            c.drawBitmap(bmp,null,RectF(cx-gw/2,cy-gh/2,cx+gw/2,cy+gh/2),p)
            p.colorFilter=null;p.alpha=255
            p.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            c.drawBitmap(bmp,null,RectF(left,top,left+drawW,top+drawH),p)
            p.xfermode=null
            c.restore()
        }
        /** A ring of small pixel-square sparks launched outward from (cx,cy), the way the
         * rest of this board's art stays blocky rather than smooth — `eased` 0..1 drives
         * both how far they've travelled and how much they've faded. */
        fun burst(cx:Float,cy:Float,eased:Float,colors:List<String>) {
            if(eased>=1f) return
            val n=8
            val a=((1f-eased)*220).toInt().coerceIn(0,255)
            for(i in 0 until n) {
                val angle=(i/n.toFloat())*(Math.PI*2).toFloat()+.3f
                val radius=55f*eased
                val px=cx+cos(angle)*radius; val py=cy+sin(angle)*radius*.55f
                color(colors[i%colors.size]);p.alpha=a
                c.drawRect(px-2.5f,py-2.5f,px+2.5f,py+2.5f,p)
            }
            p.alpha=255
        }

        ground(0f,0f,360f,460f,"grass")

        fun drawScenery(near: Boolean) {
            scenery.pixels.forEach { detail ->
                if ((detail.y >= 335f) == near) {
                    rect(detail.x, detail.y, detail.width, detail.height, detail.color)
                }
            }
            scenery.sprites.forEach { prop ->
                if ((prop.y >= 335f) == near) {
                    sprite(prop.id, prop.x, prop.y, prop.width, prop.height)
                }
            }
        }

        drawScenery(near = false)
        scenery.signs.forEach { sign ->
            rect(sign.x, sign.y, sign.width, 10f, INK)
            rect(sign.x + 1f, sign.y + 1f, sign.width - 2f, 8f, "#FFE0A0")
            label(sign.text, sign.x + sign.width / 2f, sign.y + 7.5f, 7f, WORLD_TEXT)
        }
        ground(0f, 56f, 360f, 16f, "walk")
        rect(0f, 56f, 360f, PIXEL * 2, ASPHALT)
        rect(0f, 70f, 360f, PIXEL, "#6D7186")
        for (x in 16..352 step 24) {
            rect(x.toFloat(), 59f, 1f, 10f, "#8B879F")
        }

        // Connect the existing crossings across the green strips between roads.
        // Keep the paths flat so they never read as a new obstacle or a traffic signal.
        val roadCenters = level.lanes.map { it.y * 410f }.sorted()
        val greenStrips = (listOf(72f) + roadCenters.map { it + 36f })
            .zip(roadCenters.map { it - 32f } + listOf(335f))
        val paths = Simulation.crossings.filterIndexed { index, _ ->
            if (level.night && level.pedestrianCrossings.isNotEmpty()) level.hasMarkedCrossing(index)
            else index != level.blockedCrossing
        }
        for ((top, bottom) in greenStrips) {
            val height = bottom - top
            if (height < 9f) continue
            for (crossing in paths) {
                val left = crossing * 360f - 13f
                rect(left - 2f, top, 30f, height, "#318981")
                ground(left, top, 26f, height, "walk")
            }
            // Set small planting beds away from the crossing lines and from the road edges.
            if (height >= 32f) {
                val y = top + (height - 12f) / 2f
                val bloom = when (level.world) {
                    1 -> "#E1B17C"
                    2 -> "#7D9CB2"
                    else -> "#D4A47D"
                }
                for (x in listOf(25f, 335f)) {
                    rect(x - 15f, y + 2f, 30f, 11f, "#2A746A")
                    rect(x - 15f, y, 28f, 10f, "#437C61")
                    rect(x - 12f, y + 1f, 23f, 6f, "#5CA181")
                    for (offset in listOf(-9f, -1f, 7f)) rect(x + offset, y + 2f, 3f, 2f, bloom)
                }
            }
        }

        val preview=sim ?: Simulation(level,plan)
        // Captured below, inside the lane loop, the moment we draw the exact car that
        // caused a collision — so the result screen can spotlight that one vehicle instead
        // of its whole lane.
        var hitCarSprite=""; var hitCarPx=0f; var hitCarPy=0f; var hitCarW=0f; var hitCarH=0f; var hitCarFlip=false
        // Every light source queues its spot here instead of painting a flat tinted
        // circle immediately: at night, these get burned through the dark overlay as a
        // soft gradient hole (plus a warm tint on top) once that overlay exists, so they
        // read as light pushing back the dark rather than one more translucent shape
        // layered on top of it. (x, y, radiusX, radiusY) in board units.
        val nightGlows = mutableListOf<FloatArray>()
        val headlightsOn = level.night && "car_lights" in plan.street
        fun queueHeadlights(x: Float, y: Float, width: Float, height: Float, direction: Float) {
            if (!headlightsOn) return
            // Keep the radial falloff ahead of the bumper, with two overlapping pools of light.
            val radius = 48f
            val center = x + direction * (width / 2f + radius - 3f)
            val separation = height * .15f
            nightGlows.add(floatArrayOf(center, y - separation, radius, 28f))
            nightGlows.add(floatArrayOf(center, y + separation, radius, 28f))
        }
        level.lanes.forEach { lane ->
            val y=lane.y*410f
            ground(0f,y-32f,360f,68f,"road")
            // Kerb edges, then the dashed centre line of the lane.
            rect(0f,y-32f,360f,PIXEL*2,MARKING);rect(0f,y+34f,360f,PIXEL*2,MARKING)
            for(i in 0..11) rect(i*32f+6f,y,18f,PIXEL*2,MARKING)
            if (level.night && level.pedestrianCrossings.isNotEmpty()) {
                Simulation.crossings.forEachIndexed { index, x ->
                    if (level.hasMarkedCrossing(index)) zebraCrossing(x * 360f, y)
                }
            } else if ("crosswalk" in plan.street) {
                Simulation.crossings.forEachIndexed { index, x ->
                    if (level.tutorial || index in level.accessible) zebraCrossing(x * 360f, y)
                }
            } else if (lane == level.lanes.first()) {
                zebraCrossing(180f, y)
            }
            if("speed_zone" in plan.street) for(i in 0..5) rect(272f+i%2*5,y-26+i*10,11f,6f,"#F5A94C")
            // A twoWay lane is really two sub-lanes sharing the strip: each direction
            // gets its own row, centred in its half of the road (between the kerb edge
            // at y-32/y+34 and the centre line at y), so the cars read as two separate
            // lanes instead of both hugging the centre line and reading as one.
            // A blind-spot lane is a large vehicle (bus/truck): rendered bigger so its
            // size — not just its behaviour — tells the player why its driver reacts late.
            val carW=if(lane.blindSpot) 64f else 52f; val carH=if(lane.blindSpot) 44f else 38f
            val x=preview.carX(lane)*360f; val carY=if(lane.twoWay) y-16f else y
            val direction = if (lane.speed < 0) -1f else 1f
            queueHeadlights(x, carY, carW, carH, direction)
            c.save();if(lane.speed<0)c.scale(-1f,1f,x,carY)
            sprite(lane.car,x-carW/2,carY-carH/2,carW,carH);c.restore()
            // A red brake light at the rear, not just text — a child sees the car itself
            // is reacting, not just a label floating above it.
            if(!level.tutorial && sim?.isBraking(lane) == true) {
                val tailX=x+(if(lane.speed<0) carW/2-4f else -carW/2+4f)
                color("#F27A6B");c.drawCircle(tailX,carY,4f,p)
            }
            // The oncoming car drives its own row continuously, same as any other
            // car on the board — it never teleports into view mid-street; only an
            // inattentive walker gets ambushed by it (see Simulation's reckless-car
            // intercept), always launched from off-screen, never conjured nearby.
            if(lane.twoWay) {
                val ox=preview.oncomingCarX(lane)*360f; val oy=y+17f
                queueHeadlights(ox, oy, carW, carH, -direction)
                c.save();if(lane.speed>=0)c.scale(-1f,1f,ox,oy)
                sprite(lane.car,ox-carW/2,oy-carH/2,carW,carH);c.restore()
            }
            // Whoever crossed without looking gets a brand-new car aimed at them, launched
            // from off-screen — never the lane's regular traffic suddenly relocated.
            val ambushY=if(lane.twoWay) y+17f else carY
            preview.ambushCarsFor(lane).forEach { (ax,adir) ->
                val axPx=ax*360f
                queueHeadlights(axPx, ambushY, carW, carH, adir)
                c.save();if(adir<0f)c.scale(-1f,1f,axPx,ambushY)
                sprite(lane.car,axPx-carW/2,ambushY-carH/2,carW,carH);c.restore()
            }
            // Remember exactly where (and which) car caused a collision, in its own pixel
            // position/size/orientation, so the result overlay below can spotlight that
            // precise vehicle instead of darkening its whole lane.
            if(sim?.phase==Phase.RETRY && lane==sim.collapsedLane) when(sim.collapsedCarRole) {
                HitCarRole.MAIN -> { hitCarSprite=lane.car;hitCarPx=x;hitCarPy=carY;hitCarW=carW;hitCarH=carH;hitCarFlip=lane.speed<0 }
                HitCarRole.ONCOMING -> {
                    val ox=preview.oncomingCarX(lane)*360f
                    hitCarSprite=lane.car;hitCarPx=ox;hitCarPy=y+17f;hitCarW=carW;hitCarH=carH;hitCarFlip=lane.speed>=0
                }
                HitCarRole.AMBUSH -> preview.ambushCarsFor(lane).minByOrNull { (ax,_) -> abs(ax-(sim.collapsedCarX ?: ax)) }?.let { (ax,adir) ->
                    hitCarSprite=lane.car;hitCarPx=ax*360f;hitCarPy=ambushY;hitCarW=carW;hitCarH=carH;hitCarFlip=adir<0f
                }
                null -> {}
            }
            if(!level.tutorial && sim != null) {
                if(sim.carSpeed(lane)<.002f) label("ALTO",x,carY-24f,8f,"#FFE07A")
                else if(sim.isBraking(lane)) label("FRENA",x,carY-24f,8f,"#F27A6B")
            }
        }
        // Near side: the pavement the pedestrians wait on.
        ground(0f,335f,360f,125f,"walk")
        rect(0f,335f,360f,PIXEL*2,ASPHALT)
        rect(0f,338f,360f,1f,"#817D95")
        drawScenery(near = true)
        // A fixed obstacle (roadwork) blocks foot access to one crossing entirely — it
        // sits on the sidewalk, not the road, so traffic drives by untouched. Same
        // caution orange as "ZONA LENTA"/the speed-zone cones, with its own label and an
        // X mark, so it reads as "this path is closed" and never gets mistaken for the
        // parked car that just blocks a sightline (that one's red, not orange).
        level.blockedCrossing?.let { bc ->
            val obstacle=when(level.world) { 1->"obstacle_cone_campus"; else->"obstacle_barricade_barrio" }
            val bx=Simulation.crossings[bc]*360f
            sprite(obstacle,bx-20,297f,40f,40f)
            rect(bx-32f,336f,64f,13f,"#F5A94C")
            color(INK);p.style=Paint.Style.STROKE;p.strokeWidth=1.3f
            c.drawLine(bx-27f,339f,bx-21f,345f,p);c.drawLine(bx-21f,339f,bx-27f,345f,p)
            label("CERRADO: OBRA",bx+3,345f,6f,INK)
        }
        if(!level.tutorial) {
            if("speed_zone" in plan.street) { sprite("stop",12f,300f,24f,32f);label("ZONA LENTA",62f,326f,8f,WORLD_TEXT) }
            Simulation.crossings.forEachIndexed { i,x ->
                if("crosswalk" in plan.street && i in level.accessible) {
                    sprite("ramp",x*360-18,311f,36f,30f)
                    label("RAMPA",x*360,353f,7f,WORLD_TEXT)
                }
            }
            if("lighting" in plan.street) {
                nightGlows.add(floatArrayOf(180f,210f,90f,90f))
                label("LUZ",180f,88f,8f,"#FFE07A")
            }
            label(when(level.weather) { "rain" -> "LLUVIA · FRENADO LARGO"; "fog" -> "NIEBLA · VISTA REDUCIDA"; else -> if(level.night) "NOCHE" else "DÍA" },180f,452f,9f,WORLD_TEXT)
            if(level.weather=="rain") {
                color("#1B2440");p.alpha=35;c.drawRect(0f,0f,360f,330f,p);p.alpha=255
                color("#CFE0ED");p.alpha=160
                // Each drop falls steadily and wraps back to the top — `frame` only ticks
                // while the simulation is actually running, so rain animates during the
                // walk, the same moments the player is watching the board most closely.
                val fall=(rainTick*7)%340
                for(i in 0..70) {
                    val x=(i*37%370-5).toFloat()
                    val y=((i*53%340+fall)%340).toFloat()
                    c.drawLine(x,y,x-5,y+16,p)
                }
                p.alpha=255
            }
            if(level.weather=="fog") {color("#DDE2E9");p.alpha=110;c.drawRect(0f,56f,360f,330f,p);p.alpha=255}
        }
        if(planning) Simulation.crossings.forEachIndexed { i,x ->
            val active=plan.people[selected]?.crossing==i
            val blocked=level.blockedCrossing==i
            // The exact button the obra disables gets the same caution orange as its
            // sign above, right where the player actually taps — not just a sprite
            // somewhere else on the board they have to connect to this choice themselves.
            if(blocked) {
                color("#F5A94C");p.alpha=70;p.style=Paint.Style.FILL
                c.drawRoundRect(x*360-26,349f,x*360+26,414f,4f,4f,p);p.alpha=255
            }
            color(if(blocked) "#F5A94C" else if(active)"#FFFFFF" else "#8B8AB1")
            p.style=Paint.Style.STROKE;p.strokeWidth=if(active||blocked)2.5f else 1f;p.pathEffect=DashPathEffect(floatArrayOf(5f,5f),0f)
            c.drawRoundRect(x*360-26,349f,x*360+26,414f,4f,4f,p)
            if(active) {c.drawLine(x*360,343f,x*360,72f,p);p.pathEffect=null;p.style=Paint.Style.FILL;c.drawPath(Path().apply {moveTo(x*360,69f);lineTo(x*360-5,79f);lineTo(x*360+5,79f);close()},p)}
            p.pathEffect=null
            label(if(blocked) "CERRADO" else listOf("IZQ.","CENTRO","DER.")[i],x*360,431f,9f,if(blocked) "#F5A94C" else WORLD_TEXT)
            if (level.night && !blocked && level.pedestrianCrossings.isNotEmpty()) {
                val marked = level.hasMarkedCrossing(i)
                label(if (marked) "PASO PEATONAL" else "SIN PASO", x * 360f, 443f, 6f,
                    if (marked) "#FFE07A" else "#F5A94C")
            }
        }
        /** A small cream bubble with a 4 px tail, the way a character reacts. */
        fun bubble(x:Float,y:Float,glyph:String,hex:String) {
            val w=22f; val h=17f; val left=x-w/2; val top=y-h
            color(INK);c.drawRect(left-1,top-1,left+w+1,top+h+1,p)
            c.drawPath(Path().apply {moveTo(x-5,top+h);lineTo(x+1,top+h+5);lineTo(x+3,top+h);close()},p)
            color("#F4EAD0");c.drawRect(left,top,left+w,top+h,p)
            c.drawPath(Path().apply {moveTo(x-4,top+h);lineTo(x,top+h+3);lineTo(x+2,top+h);close()},p)
            p.textSize=12f;p.typeface=typeface;p.textAlign=Paint.Align.CENTER
            color(hex);c.drawText(glyph,x,top+h-4,p)
        }
        /** Same cream bubble as above, but holding the phone icon instead of a
         * glyph — the tail points down at whoever's holding it, so it reads as
         * a comic marker instead of the icon floating loose over their head. */
        fun phoneBubble(x:Float,y:Float) {
            val w=22f; val h=17f; val left=x-w/2; val top=y-h
            color(INK);c.drawRect(left-1,top-1,left+w+1,top+h+1,p)
            c.drawPath(Path().apply {moveTo(x-5,top+h);lineTo(x+1,top+h+5);lineTo(x+3,top+h);close()},p)
            color("#F4EAD0");c.drawRect(left,top,left+w,top+h,p)
            c.drawPath(Path().apply {moveTo(x-4,top+h);lineTo(x,top+h+3);lineTo(x+2,top+h);close()},p)
            sprite("phone",x-4,top+2,8f,13f)
        }
        fun drawMusicNotes(headX: Float, headY: Float, phase: Float) {
            repeat(3) { index ->
                val age = (phase + index / 3f) % 1f
                val direction = if (index == 1) -1f else 1f
                val x = headX + direction * (4f + age * 14f) + sin(age * 5f) * 3f
                val y = headY - 3f - age * 40f
                val opacity = minOf(age * 12f, 1f - age).coerceIn(0f, 1f)
                color(INK)
                p.alpha = (245 * opacity).toInt()
                c.drawRect(x - 4f, y + 1f, x + 3f, y + 7f, p)
                c.drawRect(x, y - 8f, x + 4f, y + 4f, p)
                c.drawRect(x + 3f, y - 8f, x + 8f, y - 3f, p)
                color(if (index == 1) "#7CC6F5" else "#FFE07A")
                p.alpha = (255 * opacity).toInt()
                c.drawRect(x - 3f, y + 2f, x + 2f, y + 6f, p)
                c.drawRect(x + 1f, y - 7f, x + 3f, y + 4f, p)
                c.drawRect(x + 3f, y - 7f, x + 7f, y - 4f, p)
            }
            p.alpha = 255
        }
        val schoolSigns = mutableListOf<androidx.compose.ui.geometry.Offset>()
        level.walkers.forEach { w ->
            val person=sim?.people?.find {it.walker.id==w.id}
            val x=(person?.x ?: Simulation.crossings[plan.people[w.id]?.crossing ?: 1])*360f
            val y=(person?.y ?: .91f)*410f
            // How far this walker can actually see from the crossing they're assigned,
            // drawn before they ever press play — a filled flashlight-style band, not a
            // thin line, so a short/red band reads as a risk without needing to read text.
            if(planning) {
                val range=preview.sightRange(w.id)*360f
                val crossX=Simulation.crossings[plan.people[w.id]?.crossing ?: 1]*360f
                val laneY=(level.lanes.maxByOrNull { it.y }?.y ?: .5f)*410f
                val short=preview.sightRange(w.id)<.5f
                color(if(short) "#F27A6B" else "#FFE07A");p.style=Paint.Style.FILL;p.alpha=55
                c.drawRect(crossX-range,laneY-10f,crossX+range,laneY+10f,p)
                p.alpha=255
                if(short) label("¡OJO!",crossX,laneY-16f,8f,"#F27A6B")
            }
            if(w.id==selected && planning) {color("#FFE07A");c.drawOval(x-16,y-7,x+16,y+5,p)}
            val skills=plan.people[w.id]?.skills ?: emptySet()
            // A reflector catches and scatters nearby light — at night it reads as a real
            // glow around its wearer, the same visual language as a streetlamp or a car's
            // headlights, not just a tiny dot nobody would notice. Queued, not drawn here:
            // it has to land after the night overlay to actually push back the dark instead
            // of sitting dimmed underneath it like everything else.
            if("visible" in skills && level.night) {
                nightGlows.add(floatArrayOf(x,y-19,30f,30f))
            }
            // Walking away from the camera: the stride advances with distance
            // covered, so a pedestrian who stops to wait stops stepping too.
            val collapsed=sim?.phase==Phase.RETRY && sim.collapsedWalkerId==w.id
            val art=if(person==null || collapsed) w.sprite else "${w.sprite}_w${((person.y*32f).toInt().mod(3))}"
            if (w.hasHeadphonesOn(skills)) {
                sprite(art, x - 15, y - 38, 30f, 42f) { bounds ->
                    val head = headphonesFit(bounds.width(), bounds.height(), walking = art != w.sprite)
                    sprites.getValue("headphones").apply {
                        setBounds(
                            (bounds.left + head.left).toInt(), (bounds.top + head.top).toInt(),
                            (bounds.left + head.right).toInt(), (bounds.top + head.bottom).toInt()
                        )
                        draw(c)
                    }
                    if (!collapsed && person?.done != true) {
                        musicPhase?.let { drawMusicNotes(bounds.centerX(), bounds.top + head.top, it.value) }
                    }
                }
            } else if (level.showsChildBadge(w)) {
                val width = 30f * CHILD_ART_SCALE
                val height = 42f * CHILD_ART_SCALE
                sprite(art, x - width / 2f, y + 4f - height, width, height) { bounds ->
                    drawSchoolBackpack(c, p, bounds, fromBack = art != w.sprite)
                }
                if (person?.done != true && (planning || sim?.phase == Phase.RUNNING)) {
                    schoolSigns += androidx.compose.ui.geometry.Offset(x - 12f, maxOf(4f, y - height - 24f))
                }
            } else {
                sprite(art, x - 15, y - 38, 30f, 42f)
            }
            // A comic marker points down at whoever's holding the phone, until "Guarda el celular" puts it away.
            if(w.kind=="phone" && "phone_away" !in skills) phoneBubble(x,maxOf(y-60f,4f)+17f)
            // Clamped: a walker who just reached the far kerb sits against the
            // top of the board, and the bubble would be drawn off-canvas.
            val bubbleY=maxOf(y-40f,26f)
            if(person?.done==true) bubble(x+14,bubbleY,"♥","#D9483F")
        }
        // Choque: oscurecer el tablero y abrir una ventana recortada exactamente a la
        // silueta del peatón golpeado y, por separado, al coche concreto que lo golpeó —
        // nunca todo el carril — con un aro de brillo y una ráfaga de chispas de píxel.
        if(sim?.phase==Phase.RETRY && sim.collapsedWalkerId!=null) {
            val hitWalker=level.walkers.first { it.id==sim.collapsedWalkerId }
            val hitPerson=sim.people.first { it.walker.id==hitWalker.id }
            val hx=hitPerson.x*360f; val hy=hitPerson.y*410f
            val eased=1f-(1f-impactT)*(1f-impactT)
            val glowAlpha=(70+110*(1f-eased)).toInt().coerceIn(0,255)
            val glowScale=1.5f-.35f*eased
            val layerId=c.saveLayer(0f,0f,360f,460f,null)
            color("#000000");p.alpha=(150*minOf(1f,impactT*3)).toInt();c.drawRect(0f,0f,360f,460f,p);p.alpha=255
            revealSprite(hitWalker.sprite,hx-15f,hy-38f,30f,42f,false,"#F27A6B",glowAlpha,glowScale)
            if(hitCarSprite.isNotEmpty()) revealSprite(hitCarSprite,hitCarPx-hitCarW/2,hitCarPy-hitCarH/2,hitCarW,hitCarH,hitCarFlip,"#F27A6B",glowAlpha,glowScale)
            c.restoreToCount(layerId)
            burst(hx,hy-17f,eased,listOf("#F27A6B","#FFE07A"))
        }
        // Victoria: oscurecer el tablero y abrir una ventana recortada a la silueta de cada
        // peatón (más su burbuja "♥"), con el mismo aro de brillo —ahora dorado— y chispas.
        if(sim?.phase==Phase.WON) {
            val eased=1f-(1f-impactT)*(1f-impactT)
            val glowAlpha=(70+110*(1f-eased)).toInt().coerceIn(0,255)
            val glowScale=1.5f-.35f*eased
            val layerId=c.saveLayer(0f,0f,360f,460f,null)
            color("#000000");p.alpha=(160*minOf(1f,impactT*3)).toInt();c.drawRect(0f,0f,360f,460f,p);p.alpha=255
            level.walkers.forEach { w ->
                val person=sim.people.first { it.walker.id==w.id }
                val hx=person.x*360f; val hy=person.y*410f
                val art="${w.sprite}_w${(person.y*32f).toInt().mod(3)}"
                revealSprite(art,hx-15f,hy-38f,30f,42f,false,"#FFE07A",glowAlpha,glowScale)
                val bubbleX=hx+14f; val bubbleY=maxOf(hy-40f,26f)
                p.xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                c.drawRect(bubbleX-13f,bubbleY-19f,bubbleX+13f,bubbleY+6f,p)   // la burbuja ya es rectangular
                p.xfermode=null
            }
            c.restoreToCount(layerId)
            level.walkers.forEach { w ->
                val person=sim.people.first { it.walker.id==w.id }
                burst(person.x*360f,person.y*410f-17f,eased,listOf("#FFE07A","#88D498"))
            }
        }
        // Night: the whole board goes dark, kerb included — there's no real reason the
        // spot where people wait would be any brighter than the street two steps away.
        if(level.night) {
            // The dark wash lives in its own layer so every queued light source can burn a
            // soft hole straight through it (DST_OUT with a radial-gradient alpha mask,
            // the same recipe `revealSprite` above uses to spotlight a sprite's silhouette)
            // instead of just floating a tinted shape on top of a flat tint — once this
            // layer is composited back, the scene's own already-lit colours show through
            // exactly where a light actually reaches, fading out instead of stopping at a
            // hard edge.
            val nightLayer=c.saveLayer(0f,0f,360f,460f,null)
            color("#1B2440");p.alpha=155;c.drawRect(0f,0f,360f,460f,p);p.alpha=255
            for(g in nightGlows) {
                val gx=g[0]; val gy=g[1]; val rx=g[2]; val ry=g[3]
                val maxR=maxOf(rx,ry)
                c.save();c.translate(gx,gy);if(rx!=ry) c.scale(rx/maxR,ry/maxR)
                p.shader=android.graphics.RadialGradient(0f,0f,maxR,
                    intArrayOf(android.graphics.Color.argb(255,255,255,255),
                        android.graphics.Color.argb(130,255,255,255),
                        android.graphics.Color.argb(0,255,255,255)),
                    floatArrayOf(0f,.55f,1f),Shader.TileMode.CLAMP)
                p.xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                c.drawCircle(0f,0f,maxR,p)
                p.xfermode=null;p.shader=null
                c.restore()
            }
            c.restoreToCount(nightLayer)
            // A warm tint on top of the now-revealed centre, so it reads as the light's own
            // colour radiating out — not just "the dark has a hole here".
            for(g in nightGlows) {
                val gx=g[0]; val gy=g[1]; val rx=g[2]; val ry=g[3]
                val maxR=maxOf(rx,ry)
                c.save();c.translate(gx,gy);if(rx!=ry) c.scale(rx/maxR,ry/maxR)
                p.shader=android.graphics.RadialGradient(0f,0f,maxR,
                    intArrayOf(android.graphics.Color.argb(130,0xFF,0xE0,0x7A),
                        android.graphics.Color.argb(55,0xFF,0xE0,0x7A),
                        android.graphics.Color.argb(0,0xFF,0xE0,0x7A)),
                    floatArrayOf(0f,.6f,1f),Shader.TileMode.CLAMP)
                c.drawCircle(0f,0f,maxR,p)
                p.shader=null
                c.restore()
            }
            scenery.accentLights.forEach { light ->
                color(light.color)
                p.alpha = 210
                c.drawRect(light.x, light.y, light.x + light.width, light.y + light.height, p)
            }
            p.alpha = 255
        }
        // Keep the school symbol readable over the night wash.
        if (schoolSigns.isNotEmpty()) {
            color(INK)
            schoolSigns.forEach { drawSchoolCrossingBadge(c, p, it.x, it.y, 24f) }
        }
        c.restore()
    }
}
