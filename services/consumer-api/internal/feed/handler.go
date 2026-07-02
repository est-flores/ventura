package feed

import (
	"context"

	"connectrpc.com/connect"

	feedv1 "github.com/ventura-app/ventura/gen/go/ventura/feed/v1"
)

type Handler struct {
	svc *Service
}

func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

func (h *Handler) GetFeed(
	ctx context.Context,
	req *connect.Request[feedv1.GetFeedRequest],
) (*connect.Response[feedv1.GetFeedResponse], error) {

	limit := req.Msg.Limit
	if limit == 0 {
		limit = 10
	}

	posts, err := h.svc.GetFeed(ctx, limit)
	if err != nil {
		return nil, connect.NewError(connect.CodeInternal, err)
	}

	return connect.NewResponse(&feedv1.GetFeedResponse{
		Posts:         posts,
		NextPageToken: "",
	}), nil
}
