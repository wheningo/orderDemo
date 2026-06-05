package interceptor

import (
	"sync"
	"time"
)

type AuditEntry struct {
	Timestamp      time.Time
	IdempotencyKey string
	ContentId      string
	Region         string
	Weight         int32
	DecisionSource string
	Result         string // filled after execution
}

type AuditInterceptor struct {
	mu      sync.Mutex
	entries []AuditEntry
}

func NewAuditInterceptor() *AuditInterceptor {
	return &AuditInterceptor{}
}

func (a *AuditInterceptor) Intercept(ctx *CommandContext) error {
	a.mu.Lock()
	defer a.mu.Unlock()
	a.entries = append(a.entries, AuditEntry{
		Timestamp:      ctx.Timestamp,
		IdempotencyKey: ctx.IdempotencyKey,
		ContentId:      ctx.Request.TargetContentId,
		Region:         ctx.Request.Region,
		Weight:         ctx.Request.Weight,
		DecisionSource: ctx.Request.DecisionSource,
	})
	return nil
}

// Record adds a complete audit entry with the execution result.
func (a *AuditInterceptor) Record(ctx *CommandContext, result string) {
	a.mu.Lock()
	defer a.mu.Unlock()
	a.entries = append(a.entries, AuditEntry{
		Timestamp:      ctx.Timestamp,
		IdempotencyKey: ctx.IdempotencyKey,
		ContentId:      ctx.Request.TargetContentId,
		Region:         ctx.Request.Region,
		Weight:         ctx.Request.Weight,
		DecisionSource: ctx.Request.DecisionSource,
		Result:         result,
	})
}

func (a *AuditInterceptor) Entries() []AuditEntry {
	a.mu.Lock()
	defer a.mu.Unlock()
	result := make([]AuditEntry, len(a.entries))
	copy(result, a.entries)
	return result
}