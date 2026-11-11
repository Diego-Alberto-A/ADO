import Foundation

final class GameRepository {
    let campaign: Campaign
    private let defaults: UserDefaults
    var worlds: [String] { campaign.worlds }
    var levels: [Level] { campaign.levels }
    var tools: [Tool] { campaign.tools }
    convenience init(bundle: Bundle = .main, defaults: UserDefaults = .standard) throws {
        guard let url = bundle.url(forResource: "campaign", withExtension: "json") else { throw CocoaError(.fileNoSuchFile) }
        let campaign = try JSONDecoder().decode(Campaign.self, from: Data(contentsOf: url))
        self.init(campaign: campaign, defaults: defaults)
    }
    init(campaign: Campaign, defaults: UserDefaults = .standard) {
        self.campaign = campaign
        self.defaults = defaults
        defaults.register(defaults: ["sound": true, "guidance": true])
    }
    func stars(_ id: Int) -> Int { defaults.integer(forKey: "stars_\(id)") }
    func unlocked(_ id: Int) -> Bool { id == 1 || stars(id - 1) > 0 }
    func save(_ id: Int, stars value: Int) { defaults.set(max(value, stars(id)), forKey: "stars_\(id)") }
    var sound: Bool { get { defaults.bool(forKey: "sound") } set { defaults.set(newValue, forKey: "sound") } }
    var guidance: Bool { get { defaults.bool(forKey: "guidance") } set { defaults.set(newValue, forKey: "guidance") } }
}
