import Foundation

struct Lane: Codable, Hashable { let y: Float; let speed: Float; let offset: Float; let car: String }
struct Walker: Codable, Identifiable { let id: String; let name: String; let kind: String; let speed: Float; let sprite: String }
struct Level: Codable, Identifiable { let id: Int; let world: Int; let title: String; let lesson: String; let budget: Int; let night: Bool; let lanes: [Lane]; let walkers: [Walker] }
struct Tool: Codable, Identifiable { let id: String; let label: String; let icon: String; let cost: Int; let description: String; let category: String }
struct Campaign: Codable { let worlds: [String]; let tools: [Tool]; let levels: [Level] }
struct Assignment { var crossing = 1; var skills: Set<String> = [] }
struct Plan {
    var people: [String: Assignment] = [:]
    var street: Set<String> = []
    func cost(_ tools: [Tool]) -> Int {
        tools.reduce(0) { total, t in total + t.cost * (t.category == "street" ? (street.contains(t.id) ? 1 : 0) : people.values.filter { $0.skills.contains(t.id) }.count) }
    }
}
enum Phase: Equatable { case planning, running, won, retry }
struct PersonState { let walker: Walker; let x: Float; var y: Float = 0.91; var done = false }

// Float and the update order intentionally match the Android engine.
final class Simulation {
    static let step: Float = 0.05
    static let crossings: [Float] = [0.25, 0.5, 0.75]
    let level: Level
    let plan: Plan
    private(set) var phase: Phase = .running
    private(set) var time: Float = 0
    private(set) var emergencyUsed = false
    private var stopUntil: Float = 0
    private var trafficClocks: [Lane: Float]
    private(set) var reason = ""
    private(set) var people: [PersonState]
    var emergencyRemaining: Float { max(0, stopUntil - time) }
    init(_ level: Level, _ source: Plan) {
        self.level = level; plan = source
        trafficClocks = Dictionary(level.lanes.map { ($0, Float(0)) }, uniquingKeysWith: { a, _ in a })
        people = level.walkers.map { PersonState(walker: $0, x: Self.crossings[source.people[$0.id]?.crossing ?? 1]) }
    }
    func carX(_ lane: Lane, ahead: Float = 0) -> Float {
        let multiplier: Float = (plan.street.contains("school") ? 0.7 : 1) * (plan.street.contains("bump") ? 0.65 : 1)
        let t = trafficClocks[lane, default: 0] + (time < stopUntil ? 0 : ahead)
        let raw = lane.offset + lane.speed * multiplier * t
        return (raw.truncatingRemainder(dividingBy: 1.4) + 1.4).truncatingRemainder(dividingBy: 1.4) - 0.2
    }
    func emergency() {
        if phase == .running && !emergencyUsed { emergencyUsed = true; stopUntil = time + 3 }
    }
    func tick(_ dt: Float = Simulation.step) {
        guard phase == .running else { return }
        time += dt
        let signalled = people.contains { p in
            (plan.people[p.walker.id]?.skills.contains("signal") ?? false) && abs(p.x - 0.5) < 0.05 && !p.done
        }
        let officer = plan.street.contains("officer") && time < 6
        if time >= stopUntil && !officer && !signalled {
            for lane in level.lanes {
                let yielding = people.contains { p in
                    let actions = plan.people[p.walker.id]?.skills ?? []
                    let visible = actions.contains(level.night ? "visible" : "eye")
                    let distance = (p.x - carX(lane)) * (lane.speed > 0 ? Float(1) : -1)
                    return visible && !p.done && p.y > lane.y - 0.09 && p.y < lane.y + 0.22 && (Float(0.10)...Float(0.25)).contains(distance)
                }
                if !yielding { trafficClocks[lane, default: 0] += dt }
            }
        }
        for i in people.indices {
            var p = people[i]
            if p.done { continue }
            let skills = plan.people[p.walker.id]?.skills ?? []
            if skills.contains("wait") && time < 3 { continue }
            var velocity = p.walker.speed * (plan.street.contains("crosswalk") ? Float(1.2) : 1)
            if p.walker.kind == "distracted" && !skills.contains("look") { velocity *= 0.75 }
            if let lane = level.lanes.first(where: { p.y > $0.y + 0.065 && p.y - $0.y < 0.15 }), skills.contains("look") {
                let duration = (p.y - lane.y + 0.075) / velocity
                let hazard = (0...30).contains { abs(carX(lane, ahead: duration * Float($0) / 30) - p.x) < 0.135 }
                if hazard && !officer && !signalled && time >= stopUntil { continue }
            }
            p.y -= velocity * dt
            people[i] = p
            for lane in level.lanes {
                let near = abs(p.y - lane.y) < 0.066 && abs(p.x - carX(lane)) < 0.115
                let yielding = (skills.contains("eye") && !level.night) || (skills.contains("visible") && level.night)
                if near && !yielding && !officer && !signalled && time >= stopUntil {
                    phase = .retry
                    reason = level.night ? "De noche, usa el reflector para que puedan verte." : "Un coche estaba muy cerca. Prueba mirar antes de cruzar o cambia el punto de cruce."
                    return
                }
            }
            if p.y <= 0.08 { people[i].done = true }
        }
        if people.allSatisfy({ $0.done }) { phase = .won }
        if time > 90 && phase == .running { phase = .retry; reason = "Probemos otro plan para encontrar un momento seguro." }
    }
    func stars(_ cost: Int) -> Int {
        phase != .won ? 0 : emergencyUsed ? 1 : Float(cost) <= Float(level.budget) * 0.65 ? 3 : 2
    }
}
