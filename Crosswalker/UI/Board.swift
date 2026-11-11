import SwiftUI
import UIKit

struct Board: UIViewRepresentable {
    @ObservedObject var session: Session
    func makeUIView(context: Context) -> BoardCanvas { BoardCanvas() }
    func updateUIView(_ view: BoardCanvas, context: Context) {
        view.level=session.level; view.plan=session.plan; view.sim=session.simulation; view.selected=session.selected
        view.onCrossing = { session.crossing($0); session.beep() }
        view.accessibilityLabel = "Maqueta del nivel \(session.level.id). Usa los botones de cruce para elegir izquierda, centro o derecha."
        view.setNeedsDisplay()
    }
}

final class BoardCanvas: UIView {
    var level: Level?
    var plan = Plan()
    var sim: Simulation?
    var selected = "ana"
    var onCrossing: (Int) -> Void = { _ in }
    private var images: [String: UIImage] = [:]
    override init(frame: CGRect) {
        super.init(frame:frame); backgroundColor = .clear; isOpaque=false; isAccessibilityElement=true
        addGestureRecognizer(UITapGestureRecognizer(target:self,action:#selector(tapped(_:))))
    }
    required init?(coder: NSCoder) { fatalError("Programmatic view") }
    @objc private func tapped(_ gesture: UITapGestureRecognizer) {
        guard sim == nil else { return }
        let x = gesture.location(in:self).x / bounds.width
        onCrossing(x < 0.375 ? 0 : x < 0.625 ? 1 : 2)
    }
    private func image(_ name: String) -> UIImage? {
        if let cached=images[name] { return cached }
        let value=UIImage(named:name); images[name]=value; return value
    }
    override func draw(_ rect: CGRect) {
        guard let level, let c=UIGraphicsGetCurrentContext(), bounds.width > 0, bounds.height > 0 else { return }
        c.saveGState(); defer { c.restoreGState() }
        c.scaleBy(x:bounds.width/360,y:bounds.height/460)
        c.clip(to:CGRect(x:0,y:0,width:360,height:460))
        func color(_ hex: UInt32, _ alpha: CGFloat = 1) -> UIColor {
            UIColor(red:CGFloat((hex >> 16)&255)/255,green:CGFloat((hex >> 8)&255)/255,blue:CGFloat(hex&255)/255,alpha:alpha)
        }
        func rect(_ x:CGFloat,_ y:CGFloat,_ w:CGFloat,_ h:CGFloat,_ hex:UInt32) {
            c.setFillColor(color(hex).cgColor); c.fill(CGRect(x:x,y:y,width:w,height:h))
        }
        func line(_ x:CGFloat,_ y:CGFloat,_ x2:CGFloat,_ y2:CGFloat,_ hex:UInt32,_ width:CGFloat = 1) {
            c.setStrokeColor(color(hex).cgColor); c.setLineWidth(width); c.setLineDash(phase:0,lengths:[])
            c.move(to:CGPoint(x:x,y:y)); c.addLine(to:CGPoint(x:x2,y:y2)); c.strokePath()
        }
        func sprite(_ id:String,_ x:CGFloat,_ y:CGFloat,_ w:CGFloat,_ h:CGFloat) {
            guard let img=image(id) else { return }
            let ratio=(bounds.width/360)/(bounds.height/460), aspect=img.size.width/img.size.height
            let dh=min(h,w*ratio/aspect), dw=dh*aspect/ratio
            img.draw(in:CGRect(x:x+(w-dw)/2,y:y+h-dh,width:dw,height:dh))
        }
        func label(_ text:String,_ x:CGFloat,_ y:CGFloat,_ size:CGFloat,_ hex:UInt32) {
            let font=UIFont(name:"Jersey25-Regular",size:size) ?? UIFont.systemFont(ofSize:size)
            let attrs:[NSAttributedString.Key:Any] = [.font:font,.foregroundColor:color(hex)]
            let width=(text as NSString).size(withAttributes:attrs).width
            (text as NSString).draw(at:CGPoint(x:x-width/2,y:y-font.ascender),withAttributes:attrs)
        }
        func material(_ x:CGFloat,_ y:CGFloat,_ w:CGFloat,_ h:CGFloat,_ name:String) {
            guard let img=image("material_"+name) else { return }
            c.saveGState()
            let p=CGMutablePath(); p.move(to:CGPoint(x:x,y:y+1))
            for v in [CGPoint(x:x+w*0.3,y:y),CGPoint(x:x+w*0.7,y:y+1.1),CGPoint(x:x+w,y:y),CGPoint(x:x+w,y:y+h-1),CGPoint(x:x+w*0.65,y:y+h),CGPoint(x:x+w*0.3,y:y+h-0.8),CGPoint(x:x,y:y+h)] { p.addLine(to:v) }
            p.closeSubpath(); c.addPath(p); c.clip()
            let tw=img.size.width*0.3, th=img.size.height*0.3
            if tw > 0 && th > 0 {
                for yy in stride(from:floor(y/th)*th,to:y+h,by:th) {
                    for xx in stride(from:floor(x/tw)*tw,to:x+w,by:tw) { img.draw(in:CGRect(x:xx,y:yy,width:tw,height:th)) }
                }
            }
            c.restoreGState()
        }
        material(0,9,360,451,"kraft")
        for i in 0...60 { line(CGFloat(i)*6,448,CGFloat(i)*6,460,0xC8A36D,2) }
        material(0,0,360,447,"felt")
        sprite("house",20,0,75,63); sprite("house",230,0,65,59)
        sprite("tree",107,3,38,51); sprite("tree",312,2,34,56)
        material(0,56,360,15,"kraft")
        for i in 0...23 { rect(CGFloat(i)*16,58,8,6,i%2 == 0 ? 0xF8EACC : 0x6C6557) }
        let preview=sim ?? Simulation(level,plan)
        for (index,lane) in level.lanes.enumerated() {
            let y=CGFloat(lane.y)*410
            material(0,y-32,360,68,"road"); material(0,y-32,360,2,"cream"); material(0,y+33,360,2,"cream")
            for i in 0...12 { material(CGFloat(i)*30,y,18,2,"cream") }
            if plan.street.contains("crosswalk") {
                for x in Simulation.crossings { for i in 0...6 { material(CGFloat(x)*360-11,y-29+CGFloat(i)*9,23,5,"cream") } }
            } else if index == 0 { for i in 0...6 { material(169,y-29+CGFloat(i)*9,23,5,"cream") } }
            if plan.street.contains("bump") { for i in 0...5 { rect(275+CGFloat(i%2)*4,y-27+CGFloat(i)*10,9,5,0xD9AC49) } }
            let x=CGFloat(preview.carX(lane))*360
            c.saveGState()
            if lane.speed < 0 { c.translateBy(x:x,y:y); c.scaleBy(x:-1,y:1); c.translateBy(x:-x,y:-y) }
            sprite(lane.car,x-29,y-22,58,43); c.restoreGState()
        }
        material(0,335,360,111,"kraft")
        sprite("tree",10,181,27,41); sprite("tree",313,178,29,44)
        material(38,201,35,6,"kraft"); line(42,207,42,216,0x775E40,3); line(69,207,69,216,0x775E40,3)
        if plan.street.contains("school") { material(290,316,50,18,"cream"); label("ESCUELA",315,328,8,0x66543D) }
        if plan.street.contains("officer") { sprite("stop_sign",12,306,27,36) }
        if sim == nil {
            for (i,value) in Simulation.crossings.enumerated() {
                let x=CGFloat(value)*360, active=plan.people[selected]?.crossing == i
                c.setStrokeColor(color(active ? 0xF8EBCB : 0xAC895D).cgColor); c.setLineWidth(active ? 2.5 : 1); c.setLineDash(phase:0,lengths:[5,5])
                c.addPath(UIBezierPath(roundedRect:CGRect(x:x-26,y:349,width:52,height:65),cornerRadius:4).cgPath); c.strokePath()
                if active {
                    c.move(to:CGPoint(x:x,y:343)); c.addLine(to:CGPoint(x:x,y:72)); c.strokePath()
                    c.setFillColor(color(0xF8EBCB).cgColor); c.move(to:CGPoint(x:x,y:69)); c.addLine(to:CGPoint(x:x-5,y:79)); c.addLine(to:CGPoint(x:x+5,y:79)); c.closePath(); c.fillPath()
                }
                label(["IZQ.","CENTRO","DER."][i],x,431,9,0x645138)
            }
        }
        for (i,w) in level.walkers.enumerated() {
            let person=sim?.people.first { $0.walker.id == w.id }
            let offset=sim == nil ? (CGFloat(i)-CGFloat(level.walkers.count-1)/2)*19 : 0
            let x=CGFloat(person?.x ?? Simulation.crossings[plan.people[w.id]?.crossing ?? 1])*360+offset
            let y=CGFloat(person?.y ?? 0.91)*410
            if w.id == selected && sim == nil { c.setFillColor(color(0xF4D580).cgColor); c.fillEllipse(in:CGRect(x:x-17,y:y-8,width:34,height:14)) }
            sprite(w.sprite,x-15,y-39,30,44)
            if plan.people[w.id]?.skills.contains("visible") == true { c.setFillColor(color(0xFFDE78).cgColor); c.fillEllipse(in:CGRect(x:x-4,y:y-24,width:8,height:8)) }
            if person?.done == true { label("✓",x,y-40,15,0xF9EAC7) }
        }
        if level.night {
            c.setFillColor(color(0x22334A,45/255).cgColor); c.fill(CGRect(x:0,y:0,width:360,height:335))
            c.setFillColor(color(0xF5E6C5).cgColor); c.fillEllipse(in:CGRect(x:186,y:13,width:18,height:18))
        }
        if let sim, sim.emergencyRemaining > 0 { label("ALTO",180,330,12,0xFFF0D0) }
    }
}
