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

type ScheduleDelayedCommandParams struct {
	OrderId      string `json:"order_id"`
	Reason       string `json:"reason"`
	DelayMinutes int    `json:"delay_minutes"`
}

func RegisterScheduleTool(server *Server, config HotRankToolConfig, chain *interceptor.Chain, audit *interceptor.AuditInterceptor) {
	client := &http.Client{Timeout: 5 * time.Second}

	server.RegisterTool(Tool{
		Name:        "schedule_close_order",
		Description: "Schedule an order to be closed after a delay (delayed command via RocketMQ)",
		Handler: func(params json.RawMessage) (any, error) {
			var p ScheduleDelayedCommandParams
			if err := json.Unmarshal(params, &p); err != nil {
				return nil, fmt.Errorf("invalid params: %w", err)
			}
			if p.OrderId == "" {
				return nil, fmt.Errorf("order_id is required")
			}
			if p.DelayMinutes <= 0 {
				p.DelayMinutes = 5
			}
			if p.Reason == "" {
				p.Reason = "timeout"
			}

			ctx := &interceptor.CommandContext{
				Request: proto.BoostExposureRequest{
					TargetContentId: p.OrderId,
					Weight:          int32(p.DelayMinutes),
					Region:          "schedule",
					DecisionSource:  "agent",
				},
				Timestamp: time.Now(),
			}

			if err := chain.Execute(ctx); err != nil {
				result := proto.CommandResult{Accepted: false, Reason: err.Error(), Retryable: false}
				audit.Record(ctx, "REJECTED:"+err.Error())
				return result, nil
			}

			javaReq := map[string]any{
				"orderId":        p.OrderId,
				"reason":         p.Reason,
				"delayMinutes":   p.DelayMinutes,
				"idempotencyKey": ctx.IdempotencyKey,
			}
			body, _ := json.Marshal(javaReq)

			url := config.JavaServiceURL + "/schedule/close-order"
			resp, err := client.Post(url, "application/json", bytes.NewReader(body))
			if err != nil {
				result := proto.CommandResult{Accepted: false, Reason: fmt.Sprintf("backend unreachable: %v", err), Retryable: true}
				audit.Record(ctx, "ERROR:backend_unreachable")
				return result, nil
			}
			defer resp.Body.Close()

			respBody, _ := io.ReadAll(resp.Body)
			var result proto.CommandResult
			if err := json.Unmarshal(respBody, &result); err != nil {
				result = proto.CommandResult{Accepted: false, Reason: string(respBody), Retryable: true}
				audit.Record(ctx, "ERROR:invalid_response")
				return result, nil
			}

			audit.Record(ctx, "SCHEDULED:delay="+fmt.Sprintf("%dm", p.DelayMinutes))
			return result, nil
		},
	})
}