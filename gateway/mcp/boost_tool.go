package mcp

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/whening/hotrank-agent-loop/gateway/interceptor"
	"github.com/whening/hotrank-agent-loop/gateway/proto"
)

type BoostParams struct {
	TargetContentId string `json:"target_content_id"`
	Weight          int32  `json:"weight"`
	Region          string `json:"region"`
	IdempotencyKey  string `json:"idempotency_key,omitempty"`
	DecisionSource  string `json:"decision_source"`
}

func RegisterBoostTool(server *Server, config HotRankToolConfig, chain *interceptor.Chain) {
	client := &http.Client{Timeout: 5 * time.Second}

	server.RegisterTool(Tool{
		Name:        "dispatch_boost_exposure",
		Description: "Boost exposure for a content item (goes through interceptor chain)",
		Handler: func(params json.RawMessage) (any, error) {
			var p BoostParams
			if err := json.Unmarshal(params, &p); err != nil {
				return nil, fmt.Errorf("invalid params: %w", err)
			}
			if p.TargetContentId == "" {
				return nil, fmt.Errorf("target_content_id is required")
			}
			if p.Region == "" {
				return nil, fmt.Errorf("region is required")
			}

			ctx := &interceptor.CommandContext{
				Request: proto.BoostExposureRequest{
					TargetContentId: p.TargetContentId,
					Weight:          p.Weight,
					Region:          p.Region,
					IdempotencyKey:  p.IdempotencyKey,
					DecisionSource:  p.DecisionSource,
				},
				Timestamp: time.Now(),
			}

			if err := chain.Execute(ctx); err != nil {
				return proto.BoostExposureResponse{
					Accepted:       false,
					Reason:         err.Error(),
					IdempotencyKey: ctx.IdempotencyKey,
				}, nil
			}

			// Forward to Java
			javaReq := map[string]any{
				"targetContentId": p.TargetContentId,
				"weight":          ctx.Request.Weight,
				"region":          p.Region,
				"idempotencyKey":  ctx.IdempotencyKey,
				"decisionSource":  p.DecisionSource,
			}
			body, _ := json.Marshal(javaReq)

			url := config.JavaServiceURL + "/hotrank/boost"
			resp, err := client.Post(url, "application/json", bytes.NewReader(body))
			if err != nil {
				return proto.BoostExposureResponse{
					Accepted:       false,
					Reason:         fmt.Sprintf("failed to reach backend: %v", err),
					IdempotencyKey: ctx.IdempotencyKey,
				}, nil
			}
			defer resp.Body.Close()

			respBody, _ := io.ReadAll(resp.Body)
			var result proto.BoostExposureResponse
			if err := json.Unmarshal(respBody, &result); err != nil {
				return proto.BoostExposureResponse{
					Accepted:       false,
					Reason:         fmt.Sprintf("invalid response from backend: %s", string(respBody)),
					IdempotencyKey: ctx.IdempotencyKey,
				}, nil
			}
			result.IdempotencyKey = ctx.IdempotencyKey
			return result, nil
		},
	})
}