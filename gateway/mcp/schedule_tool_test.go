package mcp

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/whening/hotrank-agent-loop/gateway/interceptor"
	"github.com/whening/hotrank-agent-loop/gateway/proto"
)

func TestScheduleCloseOrderAccepted(t *testing.T) {
	javaServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/schedule/close-order" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"accepted":true,"reason":"","retryable":false}`)
	}))
	defer javaServer.Close()

	audit := interceptor.NewAuditInterceptor()
	chain := interceptor.NewChain(
		interceptor.NewIdempotencyInterceptor(),
		interceptor.NewRateLimitInterceptor(100, 100),
	)

	server := NewServer()
	RegisterScheduleTool(server, HotRankToolConfig{JavaServiceURL: javaServer.URL}, chain, audit)

	params, _ := json.Marshal(ScheduleDelayedCommandParams{OrderId: "123", Reason: "timeout", DelayMinutes: 5})
	result, err := server.CallTool("schedule_close_order", params)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	cr := result.(proto.CommandResult)
	if !cr.Accepted {
		t.Fatalf("expected accepted, got: %s", cr.Reason)
	}

	entries := audit.Entries()
	if len(entries) != 1 {
		t.Fatalf("expected 1 audit entry, got %d", len(entries))
	}
}

func TestScheduleCloseOrderRejectsEmptyOrderId(t *testing.T) {
	server := NewServer()
	chain := interceptor.NewChain()
	audit := interceptor.NewAuditInterceptor()
	RegisterScheduleTool(server, HotRankToolConfig{}, chain, audit)

	params, _ := json.Marshal(ScheduleDelayedCommandParams{DelayMinutes: 5})
	_, err := server.CallTool("schedule_close_order", params)
	if err == nil {
		t.Fatal("expected error for empty order_id")
	}
}