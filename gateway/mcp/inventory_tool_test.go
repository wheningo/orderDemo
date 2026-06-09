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

func TestAllocatePromoStockAccepted(t *testing.T) {
	javaServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
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
	RegisterInventoryTool(server, HotRankToolConfig{JavaServiceURL: javaServer.URL}, chain, audit)

	params, _ := json.Marshal(proto.AllocatePromoStockParams{Sku: "SKU-1", Qty: 30, Region: "CN"})
	result, err := server.CallTool("allocate_promo_stock", params)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	cr := result.(proto.CommandResult)
	if !cr.Accepted {
		t.Fatalf("expected accepted, got: %s", cr.Reason)
	}

	entries := audit.Entries()
	if len(entries) != 1 || entries[0].Result != "ACCEPTED" {
		t.Fatalf("expected ACCEPTED audit, got: %v", entries)
	}
}

func TestAllocatePromoStockOversellRejected(t *testing.T) {
	javaServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"accepted":false,"reason":"Oversell rejected: sku=SKU-1, requested=300, available=100","retryable":false}`)
	}))
	defer javaServer.Close()

	audit := interceptor.NewAuditInterceptor()
	chain := interceptor.NewChain(interceptor.NewIdempotencyInterceptor())

	server := NewServer()
	RegisterInventoryTool(server, HotRankToolConfig{JavaServiceURL: javaServer.URL}, chain, audit)

	params, _ := json.Marshal(proto.AllocatePromoStockParams{Sku: "SKU-1", Qty: 300, Region: "CN"})
	result, _ := server.CallTool("allocate_promo_stock", params)

	cr := result.(proto.CommandResult)
	if cr.Accepted || cr.Retryable {
		t.Fatalf("expected non-retryable rejection, got: %+v", cr)
	}

	entries := audit.Entries()
	if len(entries) != 1 || entries[0].Result == "" {
		t.Fatalf("expected audit with REJECTED_BY_DOMAIN, got: %v", entries)
	}
}

func TestAllocatePromoStockRateLimited(t *testing.T) {
	audit := interceptor.NewAuditInterceptor()
	chain := interceptor.NewChain(interceptor.NewRateLimitInterceptor(1, 0))

	server := NewServer()
	RegisterInventoryTool(server, HotRankToolConfig{JavaServiceURL: "http://unused"}, chain, audit)

	params, _ := json.Marshal(proto.AllocatePromoStockParams{Sku: "SKU-1", Qty: 10, Region: "CN"})
	result, _ := server.CallTool("allocate_promo_stock", params)

	cr := result.(proto.CommandResult)
	if cr.Accepted {
		t.Fatal("expected rate limit rejection")
	}

	entries := audit.Entries()
	if len(entries) != 1 {
		t.Fatalf("expected 1 audit entry, got %d", len(entries))
	}
}

func TestAllocatePromoStockRejectsEmptySku(t *testing.T) {
	server := NewServer()
	chain := interceptor.NewChain()
	audit := interceptor.NewAuditInterceptor()
	RegisterInventoryTool(server, HotRankToolConfig{}, chain, audit)

	params, _ := json.Marshal(proto.AllocatePromoStockParams{Qty: 10, Region: "CN"})
	_, err := server.CallTool("allocate_promo_stock", params)
	if err == nil {
		t.Fatal("expected error for empty sku")
	}
}