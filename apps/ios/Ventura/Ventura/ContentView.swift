//
//  ContentView.swift
//  Ventura
//
//  Created by Esteban Flores on 28/06/26.
//
import SwiftUI
import AVKit
import VenturaGenSwift

struct ContentView: View {
    @State private var player: AVPlayer?
    @State private var isLoading = true
    @State private var errorMessage: String?

    private let feedService = FeedService()

    var body: some View {
        Group {
            if let player {
                VideoPlayer(player: player)
                    .ignoresSafeArea()
                    .onAppear { player.play() }
            } else if isLoading {
                ProgressView("Loading feed...")
            } else if let errorMessage {
                Text("Error: \(errorMessage)")
                    .foregroundStyle(.red)
                    .padding()
            }
        }
        .task {
            await loadFeed()
        }
    }

    private func loadFeed() async {
        do {
            let posts = try await feedService.getFeed(
                latitude: 14.6349,
                longitude: -90.5069
            )
            if let first = posts.first, let url = URL(string: first.videoURL) {
                player = AVPlayer(url: url)
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}
