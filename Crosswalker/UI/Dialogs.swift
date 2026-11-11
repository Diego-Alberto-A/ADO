import SwiftUI

private struct ModalActionKey: EnvironmentKey {
    static let defaultValue: (@escaping () -> Void) -> Void = { action in action() }
}
extension EnvironmentValues {
    var modalAction: (@escaping () -> Void) -> Void {
        get { self[ModalActionKey.self] }
        set { self[ModalActionKey.self] = newValue }
    }
}
struct PaperDialog<Content: View>: View {
    let title: String
    var dismissible = true
    let dismiss: () -> Void
    @ViewBuilder let content: () -> Content
    @State private var visible = false
    @State private var closing = false
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color.black.opacity(0.32).ignoresSafeArea().onTapGesture { if dismissible { leave(dismiss) } }
                ScrollView {
                    VStack(spacing:16) {
                        Text(title).tracking(1).multilineTextAlignment(.center)
                        Rectangle().fill(Palette.paper).frame(height:1)
                        content()
                    }.padding(20).frame(maxWidth:.infinity).craftPaper().overlay(CutPaper().stroke(Palette.paper,lineWidth:2))
                }
                .frame(maxWidth:400,maxHeight:geometry.size.height-48)
                .fixedSize(horizontal:false,vertical:true)
                .padding(.horizontal,28)
                .scaleEffect(visible ? 1 : 0.96).offset(y:visible ? 0 : 20)
                .rotationEffect(.degrees(visible ? 0 : -1.2)).opacity(visible ? 1 : 0)
                .environment(\.modalAction,leave)
                .allowsHitTesting(!closing).accessibilityHidden(closing)
                .accessibilityAddTraits(.isModal)
            }.frame(maxWidth:.infinity,maxHeight:.infinity)
        }
        .onAppear { withAnimation(.timingCurve(0.4,0,0.2,1,duration:0.26)) { visible=true } }
        .accessibilityAction(.escape) { if dismissible { leave(dismiss) } }
    }
    private func leave(_ action: @escaping () -> Void) {
        guard !closing else { return }; closing=true
        withAnimation(.timingCurve(0.4,0,0.2,1,duration:0.17)) { visible=false }
        DispatchQueue.main.asyncAfter(deadline:.now()+0.17) { action() }
    }
}

struct ModalHost: View {
    @ObservedObject var session: Session
    private var key: String {
        if !session.dialog.isEmpty { return session.dialog }
        if session.paused && session.screen == .game { return "pause" }
        if let chosen=session.chosen { return "level-\(chosen.id)" }
        if let sim=session.simulation, sim.phase == .won || sim.phase == .retry { return "result" }
        return ""
    }
    var body: some View { modal.id(key) }
    @ViewBuilder private var modal: some View {
        if key == "settings" {
            PaperDialog(title:"A TU MANERA",dismiss:{ session.dialog="" }) {
                Text("Pequeños ajustes para tu aventura")
                Toggle("Sonidos",isOn:$session.sound)
                Toggle("Consejos en pantalla",isOn:$session.guidance)
                PaperButton("LISTO") { session.dialog="" }
            }
        } else if key == "achievements" {
            PaperDialog(title:"MIS LOGROS",dismiss:{ session.dialog="" }) {
                Text("★  \(session.totalStars) / 36 estrellas").font(.custom("Jersey25-Regular",size:28)).foregroundStyle(Palette.green)
                Text("\(session.completed) de 12 caminos seguros")
                ForEach(Array([("Primer paso",1),("Mi barrio seguro",4),("Gran planificador",8),("Guardián de la ciudad",12)].enumerated()),id:\.offset) { _, item in
                    let earned=session.completed >= item.1
                    Text("\(earned ? "★" : "○")   \(item.0)").font(.custom("Jersey25-Regular",size:18)).foregroundStyle(earned ? Palette.green : Palette.ink.opacity(0.45))
                }
                PaperButton("VOLVER") { session.dialog="" }
            }
        } else if key == "pause" {
            PaperDialog(title:"PAUSA",dismiss:{ session.paused=false }) {
                Text("Tu maqueta te espera.")
                PaperButton("▶  CONTINUAR") { session.paused=false }
                PaperButton("↻  REINTENTAR",color:Palette.coral) { session.reset() }
                PaperButton("AJUSTES",color:Palette.kraft) { session.dialog="settings" }
                PaperButton("VOLVER AL MAPA",color:Palette.kraft) { session.navigate(.map) }
            }
        } else if let level=session.chosen {
            PaperDialog(title:"NIVEL \(level.id)",dismiss:{ session.chosen=nil }) {
                Text(level.title).font(.custom("Jersey25-Regular",size:25))
                Text(level.lesson).lineSpacing(7)
                Text("☺ \(level.walkers.count) peatones    ● \(level.budget) fichas").foregroundStyle(Palette.green)
                Text("★ Llega al otro lado\n★★ Resuelve sin ayuda de emergencia\n★★★ Usa pocas fichas").lineSpacing(11)
                PaperButton("▶  PREPARAR EL PLAN") { session.chosen=nil; session.enter(level) }
            }
        } else if key == "result", let sim=session.simulation {
            let won=sim.phase == .won
            PaperDialog(title:won ? "¡BUEN CAMINO!" : "OTRO PLAN, OTRA OPORTUNIDAD",dismissible:false,dismiss:{}) {
                if won { PaperStars(count:sim.stars(session.cost),size:52) }
                else { Text("↻").font(.custom("Jersey25-Regular",size:52)).foregroundStyle(Palette.gold) }
                Text(won ? "¡Todos llegaron a salvo!" : "Nos detenemos a tiempo.").font(.custom("Jersey25-Regular",size:23))
                Text(won ? "Usaste \(session.cost) de \(session.level.budget) fichas. Tus buenas decisiones hacen una ciudad más segura." : sim.reason).lineSpacing(7)
                if won && session.level.id < session.repo.levels.count {
                    PaperButton("SIGUIENTE CAMINO  →") { session.enter(session.repo.levels[session.level.id]) }
                }
                PaperButton(won ? "VOLVER A INTENTAR" : "✎  MEJORAR MI PLAN",color:won ? Palette.kraft : Palette.green) { session.simulation=nil; session.message=session.level.lesson }
                PaperButton("VOLVER AL MAPA",color:Palette.kraft) { session.navigate(.map) }
            }
        }
    }
}
