package mcp

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

type HotRankToolConfig struct {
	JavaServiceURL string
}

type GetHotRankParams struct {
	Region string `json:"region"`
	K      int    `json:"k"`
}

type RankedItem struct {
	ContentId string  `json:"contentId"`
	Score     float64 `json:"score"`
	Rank      int     `json:"rank"`
}

func RegisterHotRankTools(server *Server, config HotRankToolConfig) {
	client := &http.Client{Timeout: 5 * time.Second}

	server.RegisterTool(Tool{
		Name:        "get_hot_rank",
		Description: "Get current Top-K hot ranked content for a region",
		Handler: func(params json.RawMessage) (any, error) {
			var p GetHotRankParams
			if err := json.Unmarshal(params, &p); err != nil {
				return nil, fmt.Errorf("invalid params: %w", err)
			}
			if p.Region == "" {
				return nil, fmt.Errorf("region is required")
			}
			if p.K <= 0 {
				p.K = 10
			}

			url := fmt.Sprintf("%s/hotrank/%s/top?k=%d", config.JavaServiceURL, p.Region, p.K)
			resp, err := client.Get(url)
			if err != nil {
				return nil, fmt.Errorf("failed to reach hotrank-service: %w", err)
			}
			defer resp.Body.Close()

			if resp.StatusCode != http.StatusOK {
				body, _ := io.ReadAll(resp.Body)
				return nil, fmt.Errorf("hotrank-service returned %d: %s", resp.StatusCode, string(body))
			}

			var items []RankedItem
			if err := json.NewDecoder(resp.Body).Decode(&items); err != nil {
				return nil, fmt.Errorf("failed to decode response: %w", err)
			}

			// Add rank positions
			for i := range items {
				items[i].Rank = i + 1
			}
			return items, nil
		},
	})
}
