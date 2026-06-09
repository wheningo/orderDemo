package proto

type BoostExposureRequest struct {
	TargetContentId string
	Weight          int32
	Region          string
	IdempotencyKey  string
	DecisionSource  string
	RiskTier        string
}

type BoostExposureResponse struct {
	Accepted       bool
	Reason         string
	IdempotencyKey string
}

type GetTopKRequest struct {
	Region string
	K      int32
}

type GetTopKResponse struct {
	Items []RankedItem
}

type RankedItem struct {
	ContentId string
	Score     float64
	Rank      int32
}

type AllocatePromoStockParams struct {
	Sku            string `json:"sku"`
	Qty            int32  `json:"qty"`
	Region         string `json:"region"`
	IdempotencyKey string `json:"idempotency_key,omitempty"`
	RiskTier       string `json:"risk_tier,omitempty"`
}

type CommandResult struct {
	Accepted  bool   `json:"accepted"`
	Reason    string `json:"reason"`
	Retryable bool   `json:"retryable"`
}