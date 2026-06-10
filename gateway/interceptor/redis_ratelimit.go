package interceptor

import (
	"context"
	"fmt"
	"time"

	"github.com/redis/go-redis/v9"
)

// Lua script: sliding window rate limiter
// KEYS[1] = rate limit key
// ARGV[1] = window size in seconds
// ARGV[2] = max requests per window
// ARGV[3] = current timestamp
// Returns: 1 if allowed, 0 if denied
var slidingWindowScript = redis.NewScript(`
local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local clear_before = now - window

redis.call('ZREMRANGEBYSCORE', key, '-inf', clear_before)
local count = redis.call('ZCARD', key)
if count >= limit then
    return 0
end
redis.call('ZADD', key, now, now .. ':' .. math.random(1000000))
redis.call('EXPIRE', key, window + 1)
return 1
`)

// RedisRateLimitInterceptor uses a Redis-backed sliding window to enforce
// per-region rate limits. It is cluster-safe and fail-open: if Redis is
// unreachable the request is allowed (with a warning logged).
type RedisRateLimitInterceptor struct {
	client    *redis.Client
	windowSec int
	maxPerWin int
	keyPrefix string
}

func NewRedisRateLimitInterceptor(client *redis.Client, windowSec int, maxPerWindow int) *RedisRateLimitInterceptor {
	return &RedisRateLimitInterceptor{
		client:    client,
		windowSec: windowSec,
		maxPerWin: maxPerWindow,
		keyPrefix: "ratelimit:",
	}
}

func (r *RedisRateLimitInterceptor) Intercept(ctx *CommandContext) error {
	key := r.keyPrefix + ctx.Request.Region
	now := time.Now().Unix()

	result, err := slidingWindowScript.Run(
		context.Background(),
		r.client,
		[]string{key},
		r.windowSec,
		r.maxPerWin,
		now,
	).Int()

	if err != nil {
		// Fail-open: Redis down -> allow request, log warning
		fmt.Printf("[WARN] Redis rate limit unavailable, fail-open: %v\n", err)
		return nil
	}

	if result == 0 {
		return fmt.Errorf("rate limit exceeded (region=%s, limit=%d/%ds)", ctx.Request.Region, r.maxPerWin, r.windowSec)
	}
	return nil
}