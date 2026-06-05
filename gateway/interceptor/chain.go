package interceptor

import (
	"time"

	"github.com/whening/hotrank-agent-loop/gateway/proto"
)

type CommandContext struct {
	Request        proto.BoostExposureRequest
	IdempotencyKey string
	Timestamp      time.Time
}

type Interceptor interface {
	Intercept(ctx *CommandContext) error
}

type Chain struct {
	interceptors []Interceptor
}

func NewChain(interceptors ...Interceptor) *Chain {
	return &Chain{interceptors: interceptors}
}

func (c *Chain) Execute(ctx *CommandContext) error {
	for _, i := range c.interceptors {
		if err := i.Intercept(ctx); err != nil {
			return err
		}
	}
	return nil
}