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

func TestBoostToolCallsJavaAfterInterceptors(t *testing.T) {
	javaServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" || r.URL.Path != "/hotrank/boost" {
			t.Fatalf("unexpected request: %s %s", r.Method, r.URL.Path)
		}
		var body map[string]any
		json.NewDecoder(r.Body).Decode(&body)
		if body["targetContentId"] != "c-1" {
			t.Fatalf("unexpected targetContentId: %v", body["targetContentId"])
		}
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprintf(w, `{"accepted":true,"reason":null,"idempotencyKey":"%s"}`, body["idempotencyKey"])
	}))
	defer javaServer.Close()

	audit := interceptor.NewAuditInterceptor()
	chain := interceptor.NewChain(
		interceptor.NewIdempotencyInterceptor(),
		interceptor.NewRateLimitInterceptor(100, 100),
	)

	server := NewServer()
	RegisterBoostTool(server, HotRankToolConfig{JavaServiceURL: javaServer.URL}, chain, audit)

	params, _ := json.Marshal(BoostParams{
		TargetContentId: "c-1",
		Weight:          10,
		Region:          "CN",
		DecisionSource:  "agent",
	})

	result, err := server.CallTool("dispatch_boost_exposure", params)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	resp := result.(proto.BoostExposureResponse)
	if !resp.Accepted {
		t.Fatalf("expected accepted, got rejected: %s", resp.Reason)
	}
	if resp.IdempotencyKey == "" {
		t.Fatal("expected idempotency key to be set")
	}
}

func TestBoostToolRejectsWhenRateLimited(t *testing.T) {
	audit := interceptor.NewAuditInterceptor()
	chain := interceptor.NewChain(
		interceptor.NewRateLimitInterceptor(1, 0), // 0 burst = immediate limit
	)

	server := NewServer()
	RegisterBoostTool(server, HotRankToolConfig{JavaServiceURL: "http://should-not-be-called"}, chain, audit)

	params, _ := json.Marshal(BoostParams{
		TargetContentId: "c-1",
		Weight:          10,
		Region:          "CN",
		DecisionSource:  "agent",
	})

	result, err := server.CallTool("dispatch_boost_exposure", params)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	resp := result.(proto.BoostExposureResponse)
	if resp.Accepted {
		t.Fatal("expected rejected due to rate limit")
	}
	if resp.Reason == "" {
		t.Fatal("expected reason to be set")
	}
}

func TestBoostToolRejectsEmptyContentId(t *testing.T) {
	audit := interceptor.NewAuditInterceptor()
	chain := interceptor.NewChain()
	server := NewServer()
	RegisterBoostTool(server, HotRankToolConfig{JavaServiceURL: "http://unused"}, chain, audit)

	params, _ := json.Marshal(BoostParams{Region: "CN", Weight: 10})
	_, err := server.CallTool("dispatch_boost_exposure", params)
	if err == nil {
		t.Fatal("expected error for empty content id")
	}
}

func TestBoostToolRejectsEmptyRegion(t *testing.T) {
	audit := interceptor.NewAuditInterceptor()
	chain := interceptor.NewChain()
	server := NewServer()
	RegisterBoostTool(server, HotRankToolConfig{JavaServiceURL: "http://unused"}, chain, audit)

	params, _ := json.Marshal(BoostParams{TargetContentId: "c-1", Weight: 10})
	_, err := server.CallTool("dispatch_boost_exposure", params)
	if err == nil {
		t.Fatal("expected error for empty region")
	}
}

func TestBoostToolAuditsRejectedCommands(t *testing.T) {
	audit := interceptor.NewAuditInterceptor()
	chain := interceptor.NewChain(
		interceptor.NewIdempotencyInterceptor(),
		interceptor.NewRateLimitInterceptor(1, 0), // immediate reject
	)

	server := NewServer()
	RegisterBoostTool(server, HotRankToolConfig{JavaServiceURL: "http://unused"}, chain, audit)

	params, _ := json.Marshal(BoostParams{
		TargetContentId: "c-1",
		Weight:          10,
		Region:          "CN",
		DecisionSource:  "agent",
	})

	server.CallTool("dispatch_boost_exposure", params)

	entries := audit.Entries()
	if len(entries) != 1 {
		t.Fatalf("expected 1 audit entry, got %d", len(entries))
	}
	if entries[0].Result == "" {
		t.Fatal("expected audit result to be filled")
	}
	if entries[0].ContentId != "c-1" {
		t.Fatalf("expected content 'c-1', got '%s'", entries[0].ContentId)
	}
}

func TestBoostToolAuditsAcceptedCommands(t *testing.T) {
	javaServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"accepted":true,"reason":"","idempotencyKey":"k1"}`)
	}))
	defer javaServer.Close()

	audit := interceptor.NewAuditInterceptor()
	chain := interceptor.NewChain(
		interceptor.NewIdempotencyInterceptor(),
		interceptor.NewRateLimitInterceptor(100, 100),
	)

	server := NewServer()
	RegisterBoostTool(server, HotRankToolConfig{JavaServiceURL: javaServer.URL}, chain, audit)

	params, _ := json.Marshal(BoostParams{
		TargetContentId: "c-2",
		Weight:          15,
		Region:          "US",
		DecisionSource:  "agent",
	})

	server.CallTool("dispatch_boost_exposure", params)

	entries := audit.Entries()
	if len(entries) != 1 {
		t.Fatalf("expected 1 audit entry, got %d", len(entries))
	}
	if entries[0].Result != "ACCEPTED" {
		t.Fatalf("expected 'ACCEPTED', got '%s'", entries[0].Result)
	}
}