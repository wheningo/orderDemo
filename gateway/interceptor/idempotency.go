package interceptor

import (
	"fmt"

	"github.com/google/uuid"
)

type IdempotencyInterceptor struct{}

func NewIdempotencyInterceptor() *IdempotencyInterceptor {
	return &IdempotencyInterceptor{}
}

func (i *IdempotencyInterceptor) Intercept(ctx *CommandContext) error {
	if ctx.Request.IdempotencyKey == "" {
		ctx.Request.IdempotencyKey = uuid.New().String()
	}
	ctx.IdempotencyKey = ctx.Request.IdempotencyKey
	if len(ctx.IdempotencyKey) > 128 {
		return fmt.Errorf("idempotency key too long: %d chars", len(ctx.IdempotencyKey))
	}
	return nil
}