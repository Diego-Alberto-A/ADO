package com.example.crosswalker

import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.DashPathEffect
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
            "phone" to R.drawable.phone,
            "house" to R.drawable.house,"tree" to R.drawable.tree,"tree_autumn" to R.drawable.tree_autumn,
            "stop" to R.drawable.stop_sign,"signal" to R.drawable.traffic_light)
            .mapValues { resources.getDrawable(it.value, null)!!.apply { setFilterBitmap(false) } }
    }
    // Ground textures tile at 16 board units, matching one source tile of the pack.
    val grounds=remember(resources) {
        mapOf("grass" to R.drawable.ground_grass,"road" to R.drawable.ground_road,"walk" to R.drawable.ground_walk,
            "crosswalk" to R.drawable.ground_crosswalk).mapValues { (_,id) ->
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
        fun sprite(id:String,x:Float,y:Float,w:Float,h:Float) {sprites[id]?.let {
            val scaleRatio=(size.width/360f)/(size.height/460f)
            val aspect=it.intrinsicWidth.toFloat()/it.intrinsicHeight
            val drawH=minOf(h,w*scaleRatio/aspect)
            val drawW=drawH*aspect/scaleRatio
            val left=x+(w-drawW)/2
            it.setBounds(left.toInt(),(y+h-drawH).toInt(),(left+drawW).toInt(),(y+h).toInt());it.draw(c)
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

        ground(0f,0f,360f,460f,"grass")

        // Far side of the street: houses, trees and the kerb the walkers aim for.
        sprite("house",18f,0f,78f,64f);sprite("house",232f,2f,68f,60f)
        sprite("tree",107f,4f,36f,52f);sprite("tree_autumn",310f,2f,34f,56f)
        ground(0f,56f,360f,16f,"walk")
        rect(0f,56f,360f,PIXEL*2,ASPHALT)

        val preview=sim ?: Simulation(level,plan)
        level.lanes.forEach { lane ->
            val y=lane.y*410f
            ground(0f,y-32f,360f,68f,"road")
            // Kerb edges, then the dashed centre line of the lane.
            rect(0f,y-32f,360f,PIXEL*2,MARKING);rect(0f,y+34f,360f,PIXEL*2,MARKING)
            for(i in 0..11) rect(i*32f+6f,y,18f,PIXEL*2,MARKING)
            if("crosswalk" in plan.street) Simulation.crossings.forEach { x -> ground(x*360-16,y-30f,32f,64f,"crosswalk") }
            else if(lane==level.lanes.first()) ground(164f,y-30f,32f,64f,"crosswalk")
            if("bump" in plan.street) for(i in 0..5) rect(272f+i%2*5,y-26+i*10,11f,6f,"#F5A94C")
            val x=preview.carX(lane)*360f
            c.save();if(lane.speed<0)c.scale(-1f,1f,x,y)
            sprite(lane.car,x-26,y-19,52f,38f);c.restore()
        }

        // Near side: the pavement the pedestrians wait on.
        ground(0f,335f,360f,125f,"walk")
        rect(0f,335f,360f,PIXEL*2,ASPHALT)
        // Kerbside trees sit on the near pavement, clear of the crossing zones.
        sprite("tree",4f,386f,28f,44f);sprite("tree_autumn",328f,386f,28f,44f)
        if("school" in plan.street) {rect(286f,314f,56f,20f,"#C6BC9F");rect(286f,314f,56f,PIXEL*2,"#8B8AB1");label("ESCUELA",314f,328f,9f,WORLD_TEXT)}
        if("officer" in plan.street) sprite("stop",12f,300f,30f,42f)
        // "Semáforo" is a per-pedestrian skill: show the pole once anyone relies on it.
        if(plan.people.values.any {"signal" in it.skills}) sprite("signal",320f,294f,32f,48f)
        if(planning) Simulation.crossings.forEachIndexed { i,x ->
            val active=plan.people[selected]?.crossing==i
            color(if(active)"#FFFFFF" else "#8B8AB1");p.style=Paint.Style.STROKE;p.strokeWidth=if(active)2.5f else 1f;p.pathEffect=DashPathEffect(floatArrayOf(5f,5f),0f)
            c.drawRoundRect(x*360-26,349f,x*360+26,414f,4f,4f,p)
            if(active) {c.drawLine(x*360,343f,x*360,72f,p);p.pathEffect=null;p.style=Paint.Style.FILL;c.drawPath(Path().apply {moveTo(x*360,69f);lineTo(x*360-5,79f);lineTo(x*360+5,79f);close()},p)}
            label(listOf("IZQ.","CENTRO","DER.")[i],x*360,431f,9f,WORLD_TEXT)
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
        level.walkers.forEachIndexed { i,w ->
            val person=sim?.people?.find {it.walker.id==w.id}
            val x=(person?.x ?: Simulation.crossings[plan.people[w.id]?.crossing ?: 1])*360f + if(planning)(i-(level.walkers.size-1)/2f)*19f else 0f
            val y=(person?.y ?: .91f)*410f
            if(w.id==selected && planning) {color("#FFE07A");c.drawOval(x-16,y-7,x+16,y+5,p)}
            // Walking away from the camera: the stride advances with distance
            // covered, so a pedestrian who stops to wait stops stepping too.
            val collapsed=sim?.phase==Phase.RETRY && sim.collapsedWalkerId==w.id
            val art=if(person==null || collapsed) w.sprite else "${w.sprite}_w${((person.y*32f).toInt().mod(3))}"
            sprite(art,x-15,y-38,30f,42f)
            val skills=plan.people[w.id]?.skills ?: emptySet()
            if("visible" in skills) {color("#FFE07A");c.drawCircle(x,y-19,4f,p)}
            // The phone floats over its owner until "Guarda el celular" puts it away.
            if(w.kind=="phone" && "phone_away" !in skills) sprite("phone",x-6,maxOf(y-60f,4f),12f,19f)
            // Clamped: a walker who just reached the far kerb sits against the
            // top of the board, and the bubble would be drawn off-canvas.
            val bubbleY=maxOf(y-40f,26f)
            if(person?.done==true) bubble(x+14,bubbleY,"♥","#D9483F")
            else if(sim!=null && sim.emergencyRemaining>0f) bubble(x+14,bubbleY,"!","#D98A1C")
        }
        // Choque: oscurecer todo el tablero excepto al peatón golpeado y su carril.
        if(sim?.phase==Phase.RETRY && sim.collapsedWalkerId!=null) {
            val hitWalker=level.walkers.first { it.id==sim.collapsedWalkerId }
            val hitPerson=sim.people.first { it.walker.id==hitWalker.id }
            val hx=hitPerson.x*360f; val hy=hitPerson.y*410f
            val laneY=sim.collapsedLane!!.y*410f
            val layerId=c.saveLayer(0f,0f,360f,460f,null)
            color("#000000");p.alpha=140;c.drawRect(0f,0f,360f,460f,p);p.alpha=255
            p.xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            c.drawRect(hx-22f,hy-45f,hx+22f,hy+10f,p)   // agujero 1: el peatón golpeado
            c.drawRect(0f,laneY-34f,360f,laneY+34f,p)   // agujero 2: todo el carril implicado
            p.xfermode=null
            c.restoreToCount(layerId)
        }
        // Victoria: oscurecer todo excepto a los peatones y sus corazones (ya dibujados arriba).
        if(sim?.phase==Phase.WON) {
            val layerId=c.saveLayer(0f,0f,360f,460f,null)
            color("#000000");p.alpha=160;c.drawRect(0f,0f,360f,460f,p);p.alpha=255
            p.xfermode=PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            level.walkers.forEach { w ->
                val person=sim.people.first { it.walker.id==w.id }
                val hx=person.x*360f; val hy=person.y*410f
                c.drawRect(hx-22f,hy-45f,hx+22f,hy+14f,p)   // cubre sprite + burbuja "♥"
            }
            p.xfermode=null
            c.restoreToCount(layerId)
        }
        // Night: the street goes dark, the near pavement keeps a streetlamp's wash
        // so the crossing zones and their labels stay readable.
        if(level.night) {
            color("#1B2440");p.alpha=95;c.drawRect(0f,0f,360f,335f,p)
            p.alpha=38;c.drawRect(0f,335f,360f,125f,p)
            p.alpha=255;color("#F5E6C5");c.drawCircle(195f,22f,9f,p)
        }
        if(sim!=null && sim.emergencyRemaining>0f) label("ALTO",180f,330f,13f,"#F27A6B")
        c.restore()
    }
}
