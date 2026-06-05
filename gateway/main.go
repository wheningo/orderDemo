package main

import (
	"encoding/json"
	"log"
	"net/http"
	"os"

	"github.com/whening/hotrank-agent-loop/gateway/interceptor"
	"github.com/whening/hotrank-agent-loop/gateway/mcp"
)

func main() {
	javaURL := os.Getenv("HOTRANK_SERVICE_URL")
	if javaURL == "" {
		javaURL = "http://localhost:8080"
	}

	// Build interceptor chain
	chain := interceptor.NewChain(
		interceptor.NewIdempotencyInterceptor(),
		interceptor.NewRateLimitInterceptor(10, 20),
		interceptor.NewAuditInterceptor(),
	)

	// Build MCP server with tools
	mcpServer := mcp.NewServer()
	config := mcp.HotRankToolConfig{JavaServiceURL: javaURL}
	mcp.RegisterHotRankTools(mcpServer, config)
	mcp.RegisterBoostTool(mcpServer, config, chain)

	mux := http.NewServeMux()
	mux.HandleFunc("/health", healthHandler)
	mux.Handle("/mcp/", mcpServer.HTTPHandler())

	log.Printf("gateway listening on :8081 (java backend: %s)", javaURL)
	log.Fatal(http.ListenAndServe(":8081", mux))
}

func healthHandler(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "UP"})
}