package interceptor

import (
	"testing"
	"time"

	"github.com/whening/hotrank-agent-loop/gateway/proto"
)

func TestIdempotencyGeneratesKeyWhenEmpty(t *testing.T) {
	idem := NewIdempotencyInterceptor()
	ctx := &CommandContext{
		Request:   proto.BoostExposureRequest{TargetContentId: "c1", Weight: 10, Region: "CN"},
		Timestamp: time.Now(),
	}
	err := idem.Intercept(ctx)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if ctx.IdempotencyKey == "" {
		t.Fatal("expected idempotency key to be generated")
	}
}

func TestIdempotencyPreservesExistingKey(t *testing.T) {
	idem := NewIdempotencyInterceptor()
	ctx := &CommandContext{
		Request:   proto.BoostExposureRequest{IdempotencyKey: "my-key"},
		Timestamp: time.Now(),
	}
	err := idem.Intercept(ctx)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if ctx.IdempotencyKey != "my-key" {
		t.Fatalf("expected 'my-key', got '%s'", ctx.IdempotencyKey)
	}
}

func TestRateLimitAllowsWithinBurst(t *testing.T) {
	rl := NewRateLimitInterceptor(10, 5)
	for i := 0; i < 5; i++ {
		ctx := &CommandContext{Timestamp: time.Now()}
		if err := rl.Intercept(ctx); err != nil {
			t.Fatalf("request %d should not be rate limited: %v", i, err)
		}
	}
}

func TestRateLimitRejectsOverBurst(t *testing.T) {
	rl := NewRateLimitInterceptor(1, 2)
	ctx := &CommandContext{Timestamp: time.Now()}
	_ = rl.Intercept(ctx)
	_ = rl.Intercept(ctx)
	err := rl.Intercept(ctx)
	if err == nil {
		t.Fatal("expected rate limit error")
	}
}

func TestAuditRecordsEntry(t *testing.T) {
	audit := NewAuditInterceptor()
	ctx := &CommandContext{
		Request: proto.BoostExposureRequest{
			TargetContentId: "c-1",
			Region:          "CN",
			Weight:          10,
			DecisionSource:  "agent",
		},
		IdempotencyKey: "key-1",
		Timestamp:      time.Now(),
	}
	err := audit.Intercept(ctx)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	entries := audit.Entries()
	if len(entries) != 1 {
		t.Fatalf("expected 1 entry, got %d", len(entries))
	}
	if entries[0].ContentId != "c-1" {
		t.Fatalf("expected content 'c-1', got '%s'", entries[0].ContentId)
	}
}

func TestChainExecutesInOrder(t *testing.T) {
	idem := NewIdempotencyInterceptor()
	rl := NewRateLimitInterceptor(100, 100)
	audit := NewAuditInterceptor()
	chain := NewChain(idem, rl, audit)

	ctx := &CommandContext{
		Request: proto.BoostExposureRequest{
			TargetContentId: "c-1",
			Weight:          5,
			Region:          "US",
			DecisionSource:  "test",
		},
		Timestamp: time.Now(),
	}
	err := chain.Execute(ctx)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if ctx.IdempotencyKey == "" {
		t.Fatal("idempotency key should have been generated")
	}
	entries := audit.Entries()
	if len(entries) != 1 {
		t.Fatalf("expected 1 audit entry, got %d", len(entries))
	}
}

func TestChainStopsOnRateLimitError(t *testing.T) {
	rl := NewRateLimitInterceptor(1, 0) // 0 burst = immediately limited
	audit := NewAuditInterceptor()
	chain := NewChain(rl, audit)

	ctx := &CommandContext{Timestamp: time.Now()}
	err := chain.Execute(ctx)
	if err == nil {
		t.Fatal("expected rate limit error")
	}
	if len(audit.Entries()) != 0 {
		t.Fatal("audit should not have recorded when rate limited")
	}
}