package interceptor

import (
	"context"
	"testing"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/whening/hotrank-agent-loop/gateway/proto"
)

func TestRedisRateLimitAllowsWithinWindow(t *testing.T) {
	client := redis.NewClient(&redis.Options{Addr: "localhost:6379"})
	defer client.Close()

	// Skip if Redis not available
	if err := client.Ping(context.Background()).Err(); err != nil {
		t.Skip("Redis not available, skipping integration test")
	}

	// Clean up test key
	client.Del(context.Background(), "ratelimit:TEST")

	rl := NewRedisRateLimitInterceptor(client, 10, 5)

	for i := 0; i < 5; i++ {
		ctx := &CommandContext{
			Request:   proto.BoostExposureRequest{Region: "TEST"},
			Timestamp: time.Now(),
		}
		if err := rl.Intercept(ctx); err != nil {
			t.Fatalf("request %d should be allowed: %v", i, err)
		}
	}

	// 6th should be rejected
	ctx := &CommandContext{
		Request:   proto.BoostExposureRequest{Region: "TEST"},
		Timestamp: time.Now(),
	}
	if err := rl.Intercept(ctx); err == nil {
		t.Fatal("6th request should be rate limited")
	}

	// Cleanup
	client.Del(context.Background(), "ratelimit:TEST")
}

func TestRedisRateLimitFailOpen(t *testing.T) {
	// Connect to non-existent Redis -> should fail-open
	client := redis.NewClient(&redis.Options{Addr: "localhost:19999"})
	defer client.Close()

	rl := NewRedisRateLimitInterceptor(client, 10, 5)
	ctx := &CommandContext{
		Request:   proto.BoostExposureRequest{Region: "FAIL"},
		Timestamp: time.Now(),
	}
	// Should NOT return error - fail-open
	if err := rl.Intercept(ctx); err != nil {
		t.Fatalf("fail-open should allow request, got: %v", err)
	}
}