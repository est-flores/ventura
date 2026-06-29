package main

import (
	"log"
	"net/http"

	"golang.org/x/net/http2"
	"golang.org/x/net/http2/h2c"

	feedv1connect "github.com/ventura-app/ventura/gen/go/ventura/feed/v1/feedv1connect"
	"github.com/ventura-app/ventura/services/consumer-api/internal/feed"
)

func main() {
	mux := http.NewServeMux()

	path, handler := feedv1connect.NewFeedServiceHandler(feed.NewHandler())
	mux.Handle(path, handler)

	log.Println("consumer-api listening on :8080")

	if err := http.ListenAndServe(":8080", h2c.NewHandler(mux, &http2.Server{})); err != nil {
		log.Fatal(err)
	}
}
