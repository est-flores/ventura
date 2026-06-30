// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "VenturaGenSwift",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "VenturaGenSwift", targets: ["VenturaGenSwift"])
    ],
    dependencies: [
        .package(url: "https://github.com/connectrpc/connect-swift", from: "1.2.3"),
        .package(url: "https://github.com/apple/swift-protobuf", from: "1.38.1")
    ],
    targets: [
        .target(
            name: "VenturaGenSwift",
            dependencies: [
                .product(name: "Connect", package: "connect-swift"),
                .product(name: "SwiftProtobuf", package: "swift-protobuf")
            ],
            path: "ventura"
        )
    ]
)