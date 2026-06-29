package feed

import (
	"context"
	"time"

	"connectrpc.com/connect"
	"github.com/jackc/pgx/v5/pgxpool"
	"google.golang.org/protobuf/types/known/timestamppb"

	feedv1 "github.com/ventura-app/ventura/gen/go/ventura/feed/v1"
	placesv1 "github.com/ventura-app/ventura/gen/go/ventura/places/v1"
	userv1 "github.com/ventura-app/ventura/gen/go/ventura/user/v1"
)

type Handler struct {
	db *pgxpool.Pool
}

func NewHandler(db *pgxpool.Pool) *Handler {
	return &Handler{db: db}
}

const getFeedQuery = `
	SELECT
		p.id,
		p.video_url,
		COALESCE(p.thumbnail_url, ''),
		p.view_count,
		p.like_count,
		p.created_at,
		u.id,
		u.email,
		u.first_name,
		u.last_name,
		COALESCE(u.avatar_url, ''),
		pl.id,
		pl.name,
		COALESCE(pl.description, ''),
		ST_Y(pl.location::geometry) AS latitude,
		ST_X(pl.location::geometry) AS longitude,
		pl.owner_id::text
	FROM posts p
	JOIN users u  ON u.id  = p.author_id
	JOIN places pl ON pl.id = p.place_id
	ORDER BY p.created_at DESC
	LIMIT $1
`

func (h *Handler) GetFeed(
	ctx context.Context,
	req *connect.Request[feedv1.GetFeedRequest],
) (*connect.Response[feedv1.GetFeedResponse], error) {

	limit := req.Msg.Limit
	if limit == 0 {
		limit = 10
	}

	rows, err := h.db.Query(ctx, getFeedQuery, limit)
	if err != nil {
		return nil, connect.NewError(connect.CodeInternal, err)
	}
	defer rows.Close()

	var posts []*feedv1.Post

	for rows.Next() {
		var (
			postID, videoURL, thumbnailURL         string
			viewCount, likeCount                   int64
			createdAt                              time.Time
			userID, email, firstName, lastName     string
			avatarURL                              string
			placeID, placeName, placeDesc, ownerID string
			latitude, longitude                    float64
		)

		if err := rows.Scan(
			&postID, &videoURL, &thumbnailURL,
			&viewCount, &likeCount, &createdAt,
			&userID, &email, &firstName, &lastName, &avatarURL,
			&placeID, &placeName, &placeDesc,
			&latitude, &longitude, &ownerID,
		); err != nil {
			return nil, connect.NewError(connect.CodeInternal, err)
		}

		posts = append(posts, &feedv1.Post{
			Id:           postID,
			VideoUrl:     videoURL,
			ThumbnailUrl: thumbnailURL,
			ViewCount:    viewCount,
			LikeCount:    likeCount,
			CreatedAt:    timestamppb.New(createdAt),
			Author: &userv1.User{
				Id:        userID,
				Email:     email,
				FirstName: firstName,
				LastName:  lastName,
				AvatarUrl: avatarURL,
			},
			Place: &placesv1.Place{
				Id:          placeID,
				Name:        placeName,
				Description: placeDesc,
				Location: &placesv1.LatLng{
					Latitude:  latitude,
					Longitude: longitude,
				},
				OwnerId: ownerID,
			},
		})
	}

	return connect.NewResponse(&feedv1.GetFeedResponse{
		Posts:         posts,
		NextPageToken: "",
	}), nil
}
