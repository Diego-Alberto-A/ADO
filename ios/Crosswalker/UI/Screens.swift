import SwiftUI

struct ScreenContent: View {
    @ObservedObject var session: Session
    var body: some View {
        ZStack {
            PaperBackground()
            switch session.screen {
            case .home: Home(session:session)
            case .map: WorldMap(session:session)
            case .game: Game(session:session)
            }
        }.font(.custom("Jersey25-Regular",size:16)).foregroundStyle(Palette.ink).tint(Palette.green)
    }
}

struct Home: View {
    @ObservedObject var session: Session
    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing:20) {
                    Eyebrow("UN PEQUEÑO MUNDO · GRANDES DECISIONES")
                    Button { play() } label: {
                        GeometryReader { g in
                            ZStack(alignment:.top) {
                                Image("home_diorama").resizable().scaledToFit()
                                VStack(spacing:9) { Logo(); Text("El camino seguro empieza contigo.").font(.custom("Jersey25-Regular",size:11)) }
                                    .frame(width:g.size.width*0.82).padding(.top,g.size.height*0.12)
                            }
                        }.aspectRatio(0.8,contentMode:.fit)
                    }.buttonStyle(.plain).frame(maxWidth:440).accessibilityLabel("Una ciudad hecha a mano dentro de una caja de cartón").accessibilityHint("Jugar: elegir nivel")
                    VStack(spacing:8) {
                        Button(action:play) {
                            HStack(spacing:14) {
                                Text("▶").font(.custom("Jersey25-Regular",size:18)).foregroundStyle(Palette.green).frame(width:36,height:36).background(Palette.paper,in:Circle())
                                Text("JUGAR").font(.custom("Jersey25-Regular",size:30)).tracking(2)
                            }.padding(.horizontal,22).padding(.vertical,17).frame(maxWidth:.infinity).foregroundStyle(Palette.paper).craftPaper(true)
                                .overlay(CutPaper().stroke(Palette.paper.opacity(0.65),lineWidth:1)).shadow(radius:5,y:3)
                        }.buttonStyle(.plain)
                        Text("Elige un nivel y empieza a explorar").font(.custom("Jersey25-Regular",size:13)).foregroundStyle(Palette.ink.opacity(0.7))
                    }.frame(maxWidth:360)
                    HStack(spacing:12) {
                        PaperButton("⚙  AJUSTES",color:Palette.kraft) { session.beep(); session.dialog="settings" }
                        PaperButton("★  LOGROS",color:Palette.kraft) { session.beep(); session.dialog="achievements" }
                    }.frame(maxWidth:440)
                }.padding(.horizontal,24).padding(.vertical,30).frame(maxWidth:.infinity,minHeight:geometry.size.height)
            }
        }
    }
    private func play() { session.beep(); session.navigate(.map) }
}

struct WorldMap: View {
    @ObservedObject var session: Session
    var body: some View {
        VStack(alignment:.leading,spacing:14) {
            HStack { Button("‹ INICIO") { session.navigate(.home) }.padding(8); Spacer(); Text("★ \(session.totalStars) / 36") }
            Eyebrow("ELIGE TU PRÓXIMA AVENTURA")
            Text(session.repo.worlds[session.world]).font(.custom("Jersey25-Regular",size:30))
            HStack(spacing:8) {
                ForEach(session.repo.worlds.indices,id:\.self) { w in
                    CraftChip(selected:session.world == w,action:{ session.changeWorld(w) }) { Text("MUNDO \(w+1)") }
                }
            }
            ScrollView {
                VStack(spacing:18) {
                    ForEach(Array(session.repo.levels.filter { $0.world == session.world }.enumerated()),id:\.element.id) { index,level in
                        levelCard(level,index:index)
                        if index < 3 {
                            Canvas { context,size in
                                let start=size.width*(index%2 == 0 ? 0.27 : 0.72), end=size.width*(index%2 == 0 ? 0.72 : 0.27)
                                var path=Path(); path.move(to:CGPoint(x:start,y:0)); path.addCurve(to:CGPoint(x:end,y:size.height),control1:CGPoint(x:start,y:size.height*0.8),control2:CGPoint(x:end,y:size.height*0.2))
                                context.stroke(path,with:.color(Palette.coral.opacity(0.75)),lineWidth:2)
                            }.frame(height:20)
                        }
                    }
                }.padding(.vertical,4)
            }
            Text("Cada buen plan abre un nuevo camino.").frame(maxWidth:.infinity).multilineTextAlignment(.center)
        }.padding(20)
    }
    private func levelCard(_ level: Level,index:Int) -> some View {
        let unlocked=session.repo.unlocked(level.id)
        return Button { session.beep(); session.chosen=level } label: {
            HStack(spacing:16) {
                Text(unlocked ? "\(level.id)" : "—").font(.custom("Jersey25-Regular",size:27))
                    .frame(width:62,height:62).background { Image("material_kraft").resizable().scaledToFill() }.clipShape(Circle()).overlay(Circle().stroke(Palette.paper,lineWidth:3))
                VStack(alignment:.leading) {
                    Text(level.title).font(.custom("Jersey25-Regular",size:17))
                    if unlocked { PaperStars(count:session.repo.stars(level.id)) }
                    else { Text("Completa el nivel anterior").font(.custom("Jersey25-Regular",size:12)).opacity(0.65) }
                }.frame(maxWidth:.infinity,alignment:.leading)
                Artwork(index%2 == 0 ? "house" : "tree",size:38).rotationEffect(.degrees(3))
            }.padding(18).craftPaper().overlay(CutPaper().stroke(Palette.paper.opacity(0.5),lineWidth:1))
        }.buttonStyle(.plain).disabled(!unlocked)
            .rotationEffect(.degrees(index%2 == 0 ? -1 : 1)).padding(index%2 == 0 ? .trailing : .leading,35)
    }
}

struct Game: View {
    @ObservedObject var session: Session
    @State private var controlsHeight: CGFloat = 360
    private var planning: Bool { session.simulation == nil }
    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing:7) {
                    HStack {
                        Button("Ⅱ") { session.paused=true }.font(.custom("Jersey25-Regular",size:24)).frame(width:44,height:44).accessibilityLabel("Pausa")
                        VStack { Eyebrow("NIVEL \(session.level.id) · MUNDO \(session.level.world+1)"); Text(session.level.title) }.frame(maxWidth:.infinity)
                        Text("● \(session.level.budget-session.cost)/\(session.level.budget)")
                    }
                    HStack {
                        Eyebrow(planning ? "01 / PREPARA TU PLAN" : "02 / OBSERVA TU PLAN")
                        Spacer()
                        Text(planning ? "Sin límite de tiempo" : "\(session.simulation?.people.filter { $0.done }.count ?? 0) / \(session.level.walkers.count) a salvo").font(.custom("Jersey25-Regular",size:11)).foregroundStyle(Palette.green)
                    }
                    Board(session:session).frame(height:max(180,geometry.size.height-controlsHeight-93))
                    VStack(spacing:7) {
                    if planning { planningControls.transition(.opacity) }
                    else if let sim=session.simulation {
                        Text("Observa cómo cada decisión cambia el recorrido.").font(.custom("Jersey25-Regular",size:12)).multilineTextAlignment(.center)
                        PaperButton(sim.emergencyUsed ? "AYUDA UTILIZADA" : "✋  PARAR TRÁFICO · 1 USO",color:Palette.coral,enabled:!sim.emergencyUsed && sim.phase == .running) { sim.emergency(); session.frame += 1; session.beep() }
                    }
                    }.background(GeometryReader { g in Color.clear.preference(key:ControlsHeightKey.self,value:g.size.height) })
                }.padding(.horizontal,14).padding(.vertical,8)
            }.animation(.easeInOut(duration:0.22),value:planning)
                .onPreferenceChange(ControlsHeightKey.self) { controlsHeight=$0 }
        }
    }
    private var planningControls: some View {
        VStack(alignment:.leading,spacing:7) {
            ScrollView(.horizontal,showsIndicators:false) {
                HStack(spacing:6) {
                    ForEach(session.level.walkers) { w in
                        CraftChip(selected:session.selected == w.id,action:{ session.selected=w.id; session.beep() }) {
                            HStack(spacing:6) { Artwork(w.sprite); Text(w.name + ((session.plan.people[w.id]?.skills.isEmpty == false) ? " ✓" : "")) }
                        }
                    }
                }
            }
            HStack(spacing:6) {
                Artwork(session.level.walkers.first { $0.id == session.selected }!.sprite,size:24)
                Text("Cruce").font(.custom("Jersey25-Regular",size:12))
                ForEach(0..<3) { i in CraftChip(selected:session.plan.people[session.selected]?.crossing == i,action:{ session.crossing(i) }) { Text(["Izq.","Centro","Der."][i]) } }
            }
            HStack(spacing:8) {
                CraftChip(selected:session.tab == "skill",action:{ withAnimation(.easeInOut(duration:0.18)) { session.tab="skill" } }) {
                    HStack(spacing:6) { Artwork(session.level.walkers.first { $0.id == session.selected }!.sprite,size:24); Text("HABILIDADES") }
                }
                CraftChip(selected:session.tab == "street",action:{ withAnimation(.easeInOut(duration:0.18)) { session.tab="street" } }) {
                    HStack(spacing:6) { Artwork("house",size:21); Text("LA CALLE") }
                }
            }
            ScrollView(.horizontal,showsIndicators:false) {
                HStack(spacing:8) { ForEach(session.repo.tools.filter { $0.category == session.tab }) { tool in toolCard(tool) } }
            }.id(session.tab).transition(.opacity)
            if session.guidance { Text(session.message).font(.custom("Jersey25-Regular",size:12)).lineSpacing(4).lineLimit(3).frame(minHeight:32,alignment:.topLeading) }
            PaperButton("▶   PONER EL PLAN EN MARCHA") { session.beep(); session.simulation=Simulation(session.level,session.plan) }
        }
    }
    private func toolCard(_ tool: Tool) -> some View {
        let active=tool.category == "street" ? session.plan.street.contains(tool.id) : session.plan.people[session.selected]?.skills.contains(tool.id) == true
        return Button { session.toggle(tool); session.beep() } label: {
            VStack { Artwork(toolImage(tool.id)); Text(tool.label).lineLimit(1); Text("\(active ? "✓" : "+") \(tool.cost) ●") }
                .font(.custom("Jersey25-Regular",size:11)).padding(8).frame(width:87).foregroundStyle(active ? Palette.paper : Palette.ink)
                .craftPaper(active).overlay(CutPaper().stroke(active ? Palette.paper : Palette.kraft,lineWidth:1))
        }.buttonStyle(.plain).accessibilityLabel("\(tool.label), \(tool.cost) fichas").accessibilityValue(active ? "Seleccionado" : "Sin seleccionar").accessibilityHint(tool.description)
    }
}

private struct ControlsHeightKey: PreferenceKey {
    static let defaultValue: CGFloat = 360
    static func reduce(value: inout CGFloat,nextValue: () -> CGFloat) { value=nextValue() }
}
