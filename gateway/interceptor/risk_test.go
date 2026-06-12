package interceptor

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/whening/hotrank-agent-loop/gateway/proto"
)

func TestRiskInterceptorPass(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(RiskDecision{Decision: "PASS"})
	}))
	defer server.Close()

	ri := NewRiskInterceptor(server.URL)
	ctx := &CommandContext{
		Request:   proto.BoostExposureRequest{TargetContentId: "c-1", Region: "CN", Weight: 10},
		Timestamp: time.Now(),
	}
	err := ri.Intercept(ctx)
	if err != nil {
		t.Fatalf("expected pass, got: %v", err)
	}
}

func TestRiskInterceptorReject(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(RiskDecision{Decision: "REJECT", Reason: "too fast", RuleId: "FREQ"})
	}))
	defer server.Close()

	ri := NewRiskInterceptor(server.URL)
	ctx := &CommandContext{
		Request:   proto.BoostExposureRequest{TargetContentId: "c-1", Region: "CN", Weight: 10},
		Timestamp: time.Now(),
	}
	err := ri.Intercept(ctx)
	if err == nil {
		t.Fatal("expected rejection")
	}
}

func TestRiskInterceptorFailOpen(t *testing.T) {
	ri := NewRiskInterceptor("http://localhost:19999") // non-existent
	ctx := &CommandContext{
		Request:   proto.BoostExposureRequest{TargetContentId: "c-1", Region: "CN"},
		Timestamp: time.Now(),
	}
	err := ri.Intercept(ctx)
	if err != nil {
		t.Fatalf("fail-open should allow, got: %v", err)
	}
}