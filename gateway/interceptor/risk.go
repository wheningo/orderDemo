package interceptor

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

type RiskDecision struct {
	Decision string `json:"decision"`
	Reason   string `json:"reason"`
	RuleId   string `json:"ruleId"`
}

type RiskInterceptor struct {
	client     *http.Client
	serviceURL string
}

func NewRiskInterceptor(riskServiceURL string) *RiskInterceptor {
	return &RiskInterceptor{
		client:     &http.Client{Timeout: 3 * time.Second},
		serviceURL: riskServiceURL,
	}
}

func (r *RiskInterceptor) Intercept(ctx *CommandContext) error {
	reqBody := map[string]any{
		"commandType":         ctx.Request.TargetContentId,
		"targetId":            ctx.Request.TargetContentId,
		"region":              ctx.Request.Region,
		"agentId":             ctx.Request.DecisionSource,
		"riskTier":            ctx.Request.RiskTier,
		"amount":              ctx.Request.Weight,
		"frequencyLastMinute": 0,
		"idempotencyKey":      ctx.IdempotencyKey,
	}
	body, _ := json.Marshal(reqBody)

	resp, err := r.client.Post(r.serviceURL+"/risk/evaluate", "application/json", bytes.NewReader(body))
	if err != nil {
		// Fail-open: risk service down -> allow (log warning)
		fmt.Printf("[WARN] Risk service unavailable, fail-open: %v\n", err)
		return nil
	}
	defer resp.Body.Close()

	respBody, _ := io.ReadAll(resp.Body)
	var decision RiskDecision
	if err := json.Unmarshal(respBody, &decision); err != nil {
		fmt.Printf("[WARN] Risk service invalid response, fail-open: %s\n", string(respBody))
		return nil
	}

	switch decision.Decision {
	case "REJECT":
		return fmt.Errorf("risk rejected: %s (rule: %s)", decision.Reason, decision.RuleId)
	case "PENDING_REVIEW":
		return fmt.Errorf("pending review: %s (rule: %s)", decision.Reason, decision.RuleId)
	default:
		return nil
	}
}