// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CrosswalkerCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "CrosswalkerCore", targets: ["CrosswalkerCore"])],
    targets: [
        .target(name: "CrosswalkerCore", path: "Crosswalker/Core"),
        .testTarget(name: "CrosswalkerCoreTests", dependencies: ["CrosswalkerCore"], path: "CrosswalkerTests", resources: [.copy("Fixtures")])
    ]
)
