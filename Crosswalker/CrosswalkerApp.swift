import SwiftUI

@main struct CrosswalkerApp: App {
    private let repository = Result { try GameRepository() }
    var body: some Scene {
        WindowGroup {
            switch repository {
            case .success(let repo): GameRoot(repo: repo)
            case .failure(let error): VStack { Text("No se pudo abrir la aventura."); Text(error.localizedDescription) }.padding()
            }
        }
    }
}

@MainActor struct GameRoot: View {
    @StateObject private var session: Session
    @Environment(\.scenePhase) private var scenePhase
    init(repo: GameRepository) { _session = StateObject(wrappedValue: Session(repo: repo)) }
    var body: some View {
        ZStack {
            PaperBackground().ignoresSafeArea()
            ScreenTransitions(session: session)
                .accessibilityHidden(!session.dialog.isEmpty || session.paused || session.chosen != nil || session.simulation?.phase == .won || session.simulation?.phase == .retry)
            ModalHost(session: session)
        }
        .font(.custom("Jersey25-Regular", size: 16))
        .foregroundStyle(Palette.ink)
        .tint(Palette.green)
        .preferredColorScheme(.light)
        .onChange(of: scenePhase) { phase in
            if phase != .active && session.simulation?.phase == .running { session.paused = true }
        }
        .task {
            while !Task.isCancelled {
                do { try await Task.sleep(nanoseconds: 50_000_000) } catch { return }
                if scenePhase == .active { session.tick() }
            }
        }
    }
}
