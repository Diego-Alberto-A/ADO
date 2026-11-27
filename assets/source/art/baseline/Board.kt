package com.example.crosswalker

import android.graphics.Paint
import android.graphics.Path
import android.graphics.DashPathEffect
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

/** Scene rendering is independent of the fixed-step simulation. */
@Composable fun Board(level:Level,plan:Plan,sim:Simulation?,selected:String,planning:Boolean,modifier:Modifier=Modifier,frame:Int=0,onCrossing:(Int)->Unit={}) {
    val resources=LocalResources.current
    val sprites=remember(resources) {
        mapOf("car_green" to R.drawable.car_green,"car_coral" to R.drawable.car_coral,"car_yellow" to R.drawable.car_yellow,"car_blue" to R.drawable.car_blue,"walker_coral" to R.drawable.walker_coral,"walker_blue" to R.drawable.walker_blue,"walker_green" to R.drawable.walker_green,"house" to R.drawable.house,"tree" to R.drawable.tree,"stop" to R.drawable.stop_sign).mapValues { resources.getDrawable(it.value, null)!! }
    }
    Canvas(modifier.semantics { contentDescription="Maqueta del nivel ${level.id}. Usa los botones de cruce para elegir izquierda, centro o derecha." }.pointerInput(planning,selected) { if(planning) detectTapGestures { onCrossing(when { it.x<size.width*.375f->0;it.x<size.width*.625f->1;else->2 }) } }) {
        @Suppress("UNUSED_VARIABLE") val redraw=frame
        val c=drawContext.canvas.nativeCanvas
        c.save();c.scale(size.width/360f,size.height/460f)
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        fun color(hex:String) {p.color=android.graphics.Color.parseColor(hex);p.style=Paint.Style.FILL;p.pathEffect=null}
        fun rect(x:Float,y:Float,w:Float,h:Float,hex:String) {color(hex);c.drawRect(x,y,x+w,y+h,p)}
        fun line(x:Float,y:Float,x2:Float,y2:Float,hex:String,width:Float=1f) {color(hex);p.strokeWidth=width;c.drawLine(x,y,x2,y2,p)}
        fun sprite(id:String,x:Float,y:Float,w:Float,h:Float) {sprites[id]?.let {it.setBounds(x.toInt(),y.toInt(),(x+w).toInt(),(y+h).toInt());it.draw(c)}}
        fun label(text:String,x:Float,y:Float,size:Float,hex:String) {color(hex);p.textSize=size;p.typeface=android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD);p.textAlign=Paint.Align.CENTER;c.drawText(text,x,y,p)}
        rect(0f,9f,360f,451f,"#947144")
        for(i in 0..60) line(i*6f,448f,i*6f,460f,"#C8A36D",2f)
        rect(0f,0f,360f,447f,if(level.night)"#566D67" else "#90A574")
        for(i in 0..95) {val x=(i*71%360).toFloat();val y=(i*43%440).toFloat();line(x,y,x+3,y+2,if(level.night)"#627B72" else "#9CAE80")}
        sprite("house",20f,0f,75f,63f);sprite("house",230f,0f,65f,59f)
        sprite("tree",107f,3f,38f,51f);sprite("tree",312f,2f,34f,56f)
        rect(0f,56f,360f,15f,"#D8C395")
        for(i in 0..23) rect(i*16f,58f,8f,6f,if(i%2==0)"#F8EACC" else "#6C6557")
        val preview=sim ?: Simulation(level,plan)
        level.lanes.forEach { lane ->
            val y=lane.y*410f
            rect(0f,y-32f,360f,68f,"#6E685D")
            rect(0f,y-32f,360f,3f,"#ECE0BF");rect(0f,y+33f,360f,3f,"#ECE0BF")
            for(i in 0..12) rect(i*30f,y,18f,2f,"#D5CDB6")
            if("crosswalk" in plan.street) Simulation.crossings.forEach { x -> for(i in 0..6) rect(x*360-11,y-29+i*9,23f,5f,"#F1E6C9") }
            else if(lane==level.lanes.first()) for(i in 0..6) rect(169f,y-29+i*9,23f,5f,"#F1E6C9")
            if("bump" in plan.street) for(i in 0..5) rect(275f+i%2*4,y-27+i*10,9f,5f,"#D9AC49")
            val x=preview.carX(lane)*360f
            c.save();if(lane.speed<0)c.scale(-1f,1f,x,y)
            sprite(lane.car,x-29,y-22,58f,43f);c.restore()
        }
        rect(0f,335f,360f,111f,"#C4A06D")
        for(i in 0..18) line(i*20f,337f,i*20f,445f,"#B89461",.65f)
        for(i in 0..5) line(0f,337f+i*20,360f,337f+i*20,"#B89461",.65f)
        sprite("tree",10f,181f,27f,41f);sprite("tree",313f,178f,29f,44f)
        rect(38f,201f,35f,6f,"#E5C99C");line(42f,207f,42f,216f,"#775E40",3f);line(69f,207f,69f,216f,"#775E40",3f)
        if("school" in plan.street) {rect(290f,316f,50f,18f,"#F4E3B7");label("ESCUELA",315f,328f,8f,"#66543D")}
        if("officer" in plan.street) sprite("stop",12f,306f,27f,36f)
        if(planning) Simulation.crossings.forEachIndexed { i,x ->
            val active=plan.people[selected]?.crossing==i
            color(if(active)"#F8EBCB" else "#AC895D");p.style=Paint.Style.STROKE;p.strokeWidth=if(active)2.5f else 1f;p.pathEffect=DashPathEffect(floatArrayOf(5f,5f),0f)
            c.drawRoundRect(x*360-26,349f,x*360+26,414f,4f,4f,p)
            if(active) {c.drawLine(x*360,343f,x*360,72f,p);p.pathEffect=null;p.style=Paint.Style.FILL;c.drawPath(Path().apply {moveTo(x*360,69f);lineTo(x*360-5,79f);lineTo(x*360+5,79f);close()},p)}
            label(listOf("IZQ.","CENTRO","DER.")[i],x*360,431f,9f,"#645138")
        }
        level.walkers.forEachIndexed { i,w ->
            val person=sim?.people?.find {it.walker.id==w.id}
            val x=(person?.x ?: Simulation.crossings[plan.people[w.id]?.crossing ?: 1])*360f + if(planning)(i-(level.walkers.size-1)/2f)*19f else 0f
            val y=(person?.y ?: .91f)*410f
            if(w.id==selected && planning) {color("#F4D580");c.drawOval(x-17,y-8,x+17,y+6,p)}
            sprite(w.sprite,x-15,y-39,30f,44f)
            if("visible" in (plan.people[w.id]?.skills ?: emptySet())) {color("#FFDE78");c.drawCircle(x,y-20,4f,p)}
            if(person?.done==true) label("✓",x,y-40,15f,"#F9EAC7")
        }
        if(level.night) {color("#22334A");p.alpha=45;c.drawRect(0f,0f,360f,335f,p);p.alpha=255;color("#F5E6C5");c.drawCircle(195f,22f,9f,p)}
        if(sim!=null && sim.emergencyRemaining>0f) label("ALTO",180f,330f,12f,"#FFF0D0")
        c.restore()
    }
}

