package interceptor

import (
	"fmt"
	"sync"
	"time"
)

type RateLimitInterceptor struct {
	mu       sync.Mutex
	tokens   float64
	capacity float64
	rate     float64 // tokens per second
	lastTime time.Time
}

func NewRateLimitInterceptor(ratePerSecond float64, burst int) *RateLimitInterceptor {
	return &RateLimitInterceptor{
		tokens:   float64(burst),
		capacity: float64(burst),
		rate:     ratePerSecond,
		lastTime: time.Now(),
	}
}

func (r *RateLimitInterceptor) Intercept(ctx *CommandContext) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	now := time.Now()
	elapsed := now.Sub(r.lastTime).Seconds()
	r.tokens += elapsed * r.rate
	if r.tokens > r.capacity {
		r.tokens = r.capacity
	}
	r.lastTime = now

	if r.tokens < 1 {
		return fmt.Errorf("rate limit exceeded")
	}
	r.tokens--
	return nil
}