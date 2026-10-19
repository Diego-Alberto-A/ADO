import SwiftUI

enum Palette {
    static let ink = Color(hex: 0x493E30), paper = Color(hex: 0xF5E6C5), kraft = Color(hex: 0xCAA574)
    static let green = Color(hex: 0x637F57), coral = Color(hex: 0xBA5845), gold = Color(hex: 0xD9AC49)
}
extension Color {
    init(hex: UInt32) { self.init(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1) }
}
struct CutPaper: Shape {
    func path(in r: CGRect) -> Path {
        let w = r.width, h = r.height, n: CGFloat = 2
        return Path { p in
            p.move(to: CGPoint(x: n, y: n))
            for v in [CGPoint(x:w * 0.31,y:0), CGPoint(x:w * 0.69,y:n), CGPoint(x:w-n,y:0), CGPoint(x:w,y:h * 0.37), CGPoint(x:w-n,y:h-n), CGPoint(x:w * 0.61,y:h), CGPoint(x:w * 0.29,y:h-n), CGPoint(x:0,y:h), CGPoint(x:n,y:h * 0.44)] { p.addLine(to:v) }
            p.closeSubpath()
        }
    }
}
struct PaperBackground: View {
    var body: some View { GeometryReader { g in Image("material_cream").resizable().scaledToFill().frame(width:g.size.width,height:g.size.height).clipped() } }
}
extension View {
    func craftPaper(_ felt: Bool = false) -> some View {
        background { GeometryReader { g in
            Image(felt ? "material_felt" : "material_kraft").resizable().frame(width:max(g.size.width,g.size.height),height:max(g.size.width,g.size.height))
        } }.clipShape(CutPaper())
    }
}
struct PaperButtonStyle: ButtonStyle {
    var color: Color = Palette.green
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.custom("Jersey25-Regular", size: 18)).tracking(1)
            .foregroundStyle(color == Palette.green ? Palette.paper : color == Palette.coral ? Color(hex:0x76352A) : Palette.ink)
            .padding(15).frame(maxWidth:.infinity,minHeight:54).craftPaper(color == Palette.green)
            .overlay(CutPaper().stroke(Palette.paper.opacity(0.65),lineWidth:1))
            .scaleEffect(configuration.isPressed ? 0.97 : 1).opacity(enabled ? 1 : 0.45)
            .animation(.easeInOut(duration:0.1),value:configuration.isPressed)
    }
}
struct PaperButton: View {
    let text: String
    var color: Color = Palette.green
    var enabled = true
    let action: () -> Void
    @Environment(\.modalAction) private var modalAction
    init(_ text: String, color: Color = Palette.green, enabled: Bool = true, action: @escaping () -> Void) {
        self.text=text; self.color=color; self.enabled=enabled; self.action=action
    }
    var body: some View {
        Button { modalAction(action) } label: {
            HStack(spacing:8) {
                if text.hasPrefix("✋") { Artwork("stop_sign",size:25) }
                Text(text.hasPrefix("✋") ? String(text.dropFirst()).trimmingCharacters(in:.whitespaces) : text)
            }
        }.buttonStyle(PaperButtonStyle(color:color)).disabled(!enabled)
    }
}
struct CraftChip<Label: View>: View {
    var selected: Bool
    var action: () -> Void
    @ViewBuilder var label: () -> Label
    var body: some View {
        Button(action:action) { label().font(.custom("Jersey25-Regular",size:14)).padding(.horizontal,12).padding(.vertical,10)
            .frame(minHeight:42).foregroundStyle(selected ? Palette.paper : Palette.ink).craftPaper(selected)
            .overlay(CutPaper().stroke(selected ? Palette.paper : Palette.ink.opacity(0.25),lineWidth:selected ? 1.5 : 0.5))
        }.buttonStyle(.plain).padding(.vertical,3).accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}
struct Artwork: View {
    let name: String
    var size: CGFloat = 30
    init(_ name: String, size: CGFloat = 30) { self.name=name; self.size=size }
    var body: some View { Image(name).resizable().scaledToFit().frame(width:size,height:size).accessibilityHidden(true) }
}
func toolImage(_ id: String) -> String {
    ["look":"icon_look","wait":"icon_wait","eye":"walker_blue","signal":"icon_signal","visible":"star","officer":"stop_sign","school":"house","bump":"icon_bump"][id] ?? "icon_crosswalk"
}
struct PaperStars: View {
    let count: Int
    var size: CGFloat = 18
    var body: some View { HStack(spacing:2) { ForEach(0..<3) { i in Artwork("star",size:size).opacity(i < count ? 1 : 0.23).accessibilityHidden(false).accessibilityLabel(i < count ? "Estrella obtenida" : "Estrella pendiente") } } }
}
struct Eyebrow: View {
    let text: String
    init(_ text: String) { self.text=text }
    var body: some View { Text(text).font(.custom("Jersey25-Regular",size:10)).tracking(2).foregroundStyle(Palette.ink.opacity(0.7)) }
}
struct Logo: View {
    var body: some View { HStack(spacing:0) { ForEach(Array("CROSSWALKER".enumerated()),id:\.offset) { i,c in
        Text(String(c)).font(.custom("Jersey25-Regular",size:21)).foregroundStyle([Palette.coral,Palette.green,Color(hex:0x537C96),Palette.ink][i % 4]).padding(.horizontal,2).padding(.vertical,3).craftPaper().rotationEffect(.degrees(i % 2 == 0 ? -4 : 3))
    } } }
}
