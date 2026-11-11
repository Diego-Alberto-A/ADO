#if !SWIFT_PACKAGE
import XCTest
import SwiftUI
import UIKit
@testable import Crosswalker

@MainActor final class AssetTests: XCTestCase {
    func testAllArtworkLoadsFromBuiltApplication() throws {
        // Use the running app's bundle, not the test fixture bundle. Files on disk
        // alone don't establish that Xcode compiled and shipped the catalog.
        let names = [
            "material_cream", "material_kraft", "material_felt", "material_road",
            "home_diorama", "car_blue", "car_coral", "car_green", "car_yellow",
            "house", "tree", "star", "stop_sign", "walker_blue", "walker_coral",
            "walker_green", "icon_bump", "icon_crosswalk", "icon_look",
            "icon_signal", "icon_wait"
        ]
        for name in names {
            let image = try XCTUnwrap(UIImage(named: name, in: .main, compatibleWith: nil), name)
            XCTAssertGreaterThan(image.size.width, 0, name)
            XCTAssertGreaterThan(image.size.height, 0, name)
            XCTAssertNotNil(image.cgImage, name)
            XCTAssertNotEqual(image.renderingMode, .alwaysTemplate, name)
        }
        XCTAssertNotNil(UIFont(name: "Jersey25-Regular", size: 16))
        for name in ["click", "ack"] {
            XCTAssertNotNil(Bundle.main.url(forResource: name, withExtension: "wav"), name)
        }
        XCTAssertEqual(try GameRepository().levels.count, 12)
    }

    func testSwiftUIMaterialsRenderAtTheirContainerSize() throws {
        let background = ImageRenderer(content: PaperBackground().frame(width: 120, height: 80))
        background.scale = 1
        let image = try XCTUnwrap(background.uiImage)
        XCTAssertEqual(image.size, CGSize(width: 120, height: 80))
        try assertVisible(image)
        for felt in [false, true] {
            let renderer = ImageRenderer(content: Color.clear.frame(width: 120, height: 80).craftPaper(felt))
            renderer.scale = 1
            try assertVisible(XCTUnwrap(renderer.uiImage))
        }
    }

    private func assertVisible(_ image: UIImage) throws {
        let cgImage = try XCTUnwrap(image.cgImage)
        let bytesPerRow = cgImage.width * 4
        var pixels = [UInt8](repeating: 0, count: bytesPerRow * cgImage.height)
        try pixels.withUnsafeMutableBytes { bytes in
            let context = try XCTUnwrap(CGContext(
                data: bytes.baseAddress, width: cgImage.width, height: cgImage.height,
                bitsPerComponent: 8, bytesPerRow: bytesPerRow,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ))
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: CGFloat(cgImage.width), height: CGFloat(cgImage.height)))
        }
        let visiblePixels = stride(from: 3, to: pixels.count, by: 4).filter { pixels[$0] > 0 }.count
        XCTAssertGreaterThan(visiblePixels, cgImage.width * cgImage.height / 2, "Material is blank")
    }
}
#endif
