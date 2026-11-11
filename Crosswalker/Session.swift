import SwiftUI
import AVFoundation

enum Screen: Equatable { case home, map, game }
enum ScreenMotion: Equatable { case forward, backward, worldForward, worldBackward
    var backwards: Bool { self == .backward || self == .worldBackward }
    var sliding: Bool { self == .worldForward || self == .worldBackward }
}

@MainActor final class Session: ObservableObject {
    let repo: GameRepository
    @Published var screen: Screen = .home
    @Published var world = 0
    @Published var level: Level
    @Published var plan = Plan()
    @Published var selected = "ana"
    @Published var simulation: Simulation?
    @Published var frame = 0
    @Published var paused = false
    @Published var dialog = ""
    @Published var chosen: Level?
    @Published var tab = "skill"
    @Published var message = ""
    @Published var transitioning = false
    @Published var sound: Bool { didSet { repo.sound = sound } }
    @Published var guidance: Bool { didSet { repo.guidance = guidance } }
    var transition: (ScreenMotion, @escaping () -> Void) -> Void = { _, action in action() }
    private var player: AVAudioPlayer?
    init(repo: GameRepository) {
        self.repo = repo; level = repo.levels[0]; sound = repo.sound; guidance = repo.guidance
        // Android used STREAM_MUSIC; retain media-volume behavior and coexist with other audio.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: .mixWithOthers)
    }
    var cost: Int { plan.cost(repo.tools) }
    var totalStars: Int { repo.levels.reduce(0) { $0 + repo.stars($1.id) } }
    var completed: Int { repo.levels.filter { repo.stars($0.id) > 0 }.count }
    func navigate(_ destination: Screen) {
        guard destination != screen, !transitioning else { return }
        transition(destination == .home || (screen == .game && destination == .map) ? .backward : .forward) {
            if self.screen == .game { self.simulation = nil; self.paused = false; self.world = self.level.world }
            self.screen = destination
        }
    }
    func changeWorld(_ next: Int) {
        guard next != world, !transitioning else { return }
        transition(next < world ? .worldBackward : .worldForward) { self.world = next }
    }
    func enter(_ next: Level) {
        guard !transitioning else { return }
        transition(.forward) { self.level = next; self.reset(); self.screen = .game }
    }
    func reset() {
        plan = Plan(people: Dictionary(uniqueKeysWithValues: level.walkers.map { ($0.id, Assignment()) }))
        selected = level.walkers[0].id; simulation = nil; paused = false; dialog = ""; message = level.lesson; frame += 1
    }
    func crossing(_ value: Int) { guard simulation == nil else { return }; plan.people[selected, default: Assignment()].crossing = value }
    func toggle(_ tool: Tool) {
        guard simulation == nil else { return }
        var next = plan
        if tool.category == "street" {
            if !next.street.insert(tool.id).inserted { next.street.remove(tool.id) }
        } else {
            var assignment = next.people[selected, default: Assignment()]
            if !assignment.skills.insert(tool.id).inserted { assignment.skills.remove(tool.id) }
            next.people[selected] = assignment
        }
        guard next.cost(repo.tools) <= level.budget else { message = "No quedan fichas. Quita una acción para probar otra."; return }
        plan = next; message = tool.description
    }
    func tick() {
        guard !paused, !transitioning, let sim = simulation, sim.phase == .running else { return }
        sim.tick(); frame += 1
        if sim.phase == .won { repo.save(level.id, stars: sim.stars(cost)); beep(acknowledge: true) }
    }
    func beep(acknowledge: Bool = false) {
        guard sound, let url = Bundle.main.url(forResource: acknowledge ? "ack" : "click", withExtension: "wav") else { return }
        do { player = try AVAudioPlayer(contentsOf: url); player?.volume = 0.35; player?.play() } catch { /* Sound is optional; gameplay continues. */ }
    }
}
