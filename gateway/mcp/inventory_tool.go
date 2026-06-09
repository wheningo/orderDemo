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

func RegisterInventoryTool(server *Server, config HotRankToolConfig, chain *interceptor.Chain, audit *interceptor.AuditInterceptor) {
	client := &http.Client{Timeout: 5 * time.Second}

	server.RegisterTool(Tool{
		Name:        "allocate_promo_stock",
		Description: "Allocate promotional stock for a SKU (goes through interceptor chain)",
		Handler: func(params json.RawMessage) (any, error) {
			var p proto.AllocatePromoStockParams
			if err := json.Unmarshal(params, &p); err != nil {
				return nil, fmt.Errorf("invalid params: %w", err)
			}
			if p.Sku == "" {
				return nil, fmt.Errorf("sku is required")
			}
			if p.Qty <= 0 {
				return nil, fmt.Errorf("qty must be positive")
			}

			ctx := &interceptor.CommandContext{
				Request: proto.BoostExposureRequest{
					TargetContentId: p.Sku,
					Weight:          p.Qty,
					Region:          p.Region,
					IdempotencyKey:  p.IdempotencyKey,
					DecisionSource:  "agent",
					RiskTier:        p.RiskTier,
				},
				Timestamp: time.Now(),
			}

			if err := chain.Execute(ctx); err != nil {
				result := proto.CommandResult{
					Accepted:  false,
					Reason:    err.Error(),
					Retryable: false,
				}
				audit.Record(ctx, "REJECTED:"+err.Error())
				return result, nil
			}

			// Forward to Java inventory service
			javaReq := map[string]any{
				"sku":   p.Sku,
				"qty":   p.Qty,
				"txKey": ctx.IdempotencyKey,
			}
			body, _ := json.Marshal(javaReq)

			url := config.JavaServiceURL + "/inventory/reserve"
			resp, err := client.Post(url, "application/json", bytes.NewReader(body))
			if err != nil {
				result := proto.CommandResult{
					Accepted:  false,
					Reason:    fmt.Sprintf("failed to reach backend: %v", err),
					Retryable: true,
				}
				audit.Record(ctx, "ERROR:backend_unreachable")
				return result, nil
			}
			defer resp.Body.Close()

			respBody, _ := io.ReadAll(resp.Body)
			var result proto.CommandResult
			if err := json.Unmarshal(respBody, &result); err != nil {
				result = proto.CommandResult{
					Accepted:  false,
					Reason:    fmt.Sprintf("invalid response: %s", string(respBody)),
					Retryable: true,
				}
				audit.Record(ctx, "ERROR:invalid_backend_response")
				return result, nil
			}

			if result.Accepted {
				audit.Record(ctx, "ACCEPTED")
			} else if result.Retryable {
				audit.Record(ctx, "REJECTED_RETRYABLE:"+result.Reason)
			} else {
				audit.Record(ctx, "REJECTED_BY_DOMAIN:"+result.Reason)
			}
			return result, nil
		},
	})
}