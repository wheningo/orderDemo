package interceptor

import (
	"database/sql"
	"fmt"
	"sync"
	"time"
)

// DBAuditInterceptor extends the in-memory audit with async batch persistence
// to a SQL database. It maintains the same in-memory entries list for
// compatibility, and additionally drains entries to the DB in the background.
type DBAuditInterceptor struct {
	mu      sync.Mutex
	entries []AuditEntry
	db      *sql.DB
	queue   chan AuditEntry
}

func NewDBAuditInterceptor(db *sql.DB) *DBAuditInterceptor {
	a := &DBAuditInterceptor{
		db:    db,
		queue: make(chan AuditEntry, 1000),
	}
	go a.drainLoop()
	return a
}

func (a *DBAuditInterceptor) Intercept(ctx *CommandContext) error {
	a.mu.Lock()
	defer a.mu.Unlock()
	a.entries = append(a.entries, AuditEntry{
		Timestamp:      ctx.Timestamp,
		IdempotencyKey: ctx.IdempotencyKey,
		ContentId:      ctx.Request.TargetContentId,
		Region:         ctx.Request.Region,
		Weight:         ctx.Request.Weight,
		DecisionSource: ctx.Request.DecisionSource,
		RiskTier:       ctx.Request.RiskTier,
	})
	return nil
}

func (a *DBAuditInterceptor) Record(ctx *CommandContext, result string) {
	entry := AuditEntry{
		Timestamp:      ctx.Timestamp,
		IdempotencyKey: ctx.IdempotencyKey,
		ContentId:      ctx.Request.TargetContentId,
		Region:         ctx.Request.Region,
		Weight:         ctx.Request.Weight,
		DecisionSource: ctx.Request.DecisionSource,
		RiskTier:       ctx.Request.RiskTier,
		Result:         result,
	}
	a.mu.Lock()
	a.entries = append(a.entries, entry)
	a.mu.Unlock()

	// Non-blocking send to persistence queue
	select {
	case a.queue <- entry:
	default:
		fmt.Println("[WARN] Audit queue full, dropping persistence for:", entry.IdempotencyKey)
	}
}

func (a *DBAuditInterceptor) Entries() []AuditEntry {
	a.mu.Lock()
	defer a.mu.Unlock()
	result := make([]AuditEntry, len(a.entries))
	copy(result, a.entries)
	return result
}

func (a *DBAuditInterceptor) drainLoop() {
	batch := make([]AuditEntry, 0, 50)
	ticker := time.NewTicker(2 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case entry := <-a.queue:
			batch = append(batch, entry)
			if len(batch) >= 50 {
				a.flush(batch)
				batch = batch[:0]
			}
		case <-ticker.C:
			if len(batch) > 0 {
				a.flush(batch)
				batch = batch[:0]
			}
		}
	}
}

func (a *DBAuditInterceptor) flush(batch []AuditEntry) {
	if a.db == nil {
		return
	}
	tx, err := a.db.Begin()
	if err != nil {
		fmt.Printf("[ERROR] Audit flush begin tx: %v\n", err)
		return
	}
	stmt, err := tx.Prepare("INSERT INTO audit_log (timestamp, idempotency_key, content_id, region, weight, decision_source, risk_tier, result) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
	if err != nil {
		tx.Rollback()
		fmt.Printf("[ERROR] Audit flush prepare: %v\n", err)
		return
	}
	defer stmt.Close()

	for _, e := range batch {
		_, err := stmt.Exec(e.Timestamp.Unix(), e.IdempotencyKey, e.ContentId, e.Region, e.Weight, e.DecisionSource, e.RiskTier, e.Result)
		if err != nil {
			fmt.Printf("[ERROR] Audit flush exec: %v\n", err)
		}
	}
	tx.Commit()
}