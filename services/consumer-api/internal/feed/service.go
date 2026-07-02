package feed

import (
	"context"

	feedv1 "github.com/ventura-app/ventura/gen/go/ventura/feed/v1"
)

type Service struct {
	repo *Repository
}

func NewService(repo *Repository) *Service {
	return &Service{repo: repo}
}

// GetFeed will eventually apply GPS filtering and ranking before returning
// results; for now it delegates directly to the repository.
func (s *Service) GetFeed(ctx context.Context, limit int32) ([]*feedv1.Post, error) {
	return s.repo.GetFeed(ctx, limit)
}
