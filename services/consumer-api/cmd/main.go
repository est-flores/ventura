package main

import (
	"context"
	"log"
	"net/http"
	"os"

	"github.com/joho/godotenv"
	"golang.org/x/net/http2"
	"golang.org/x/net/http2/h2c"

	feedv1connect "github.com/ventura-app/ventura/gen/go/ventura/feed/v1/feedv1connect"
	"github.com/ventura-app/ventura/services/consumer-api/internal/db"
	"github.com/ventura-app/ventura/services/consumer-api/internal/feed"
)

func main() {
	if err := godotenv.Load(); err != nil {
		log.Println("no .env file, reading from environment")
	}

	ctx := context.Background()

	pool, err := db.NewPool(ctx)
	if err != nil {
		log.Fatalf("failed to connect to database: %v", err)
	}
	defer pool.Close()

	mux := http.NewServeMux()
	path, handler := feedv1connect.NewFeedServiceHandler(feed.NewHandler(pool))
	mux.Handle(path, handler)

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	log.Printf("consumer-api listening on :%s", port)

	if err := http.ListenAndServe(":"+port, h2c.NewHandler(mux, &http2.Server{})); err != nil {
		log.Fatal(err)
	}
}
