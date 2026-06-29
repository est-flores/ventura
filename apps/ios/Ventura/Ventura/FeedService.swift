//
//  FeedService.swift
//  Ventura
//
//  Created by Esteban Flores on 28/06/26.
//
import Connect
import Foundation

final class FeedService {
    private let client: Ventura_Feed_V1_FeedServiceClient

    init() {
        let protocolClient = ProtocolClient(
            httpClient: URLSessionHTTPClient(),
            config: ProtocolClientConfig(
                host: "http://localhost:8080",
                networkProtocol: .connect,
                codec: ProtoCodec()
            )
        )
        self.client = Ventura_Feed_V1_FeedServiceClient(client: protocolClient)
    }

    func getFeed(latitude: Double, longitude: Double) async throws -> [Ventura_Feed_V1_Post] {
        var location = Ventura_Places_V1_LatLng()
        location.latitude = latitude
        location.longitude = longitude

        var request = Ventura_Feed_V1_GetFeedRequest()
        request.location = location
        request.limit = 10

        let response = await client.getFeed(request: request, headers: [:])

        guard let message = response.message else {
            throw response.error ?? ConnectError(code: .unknown, message: "Empty response")
        }

        return message.posts
    }
}
