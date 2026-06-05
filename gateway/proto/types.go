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