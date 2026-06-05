package mcp

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestServerRegistersAndCallsTool(t *testing.T) {
	server := NewServer()
	server.RegisterTool(Tool{
		Name:        "echo",
		Description: "Echo back params",
		Handler: func(params json.RawMessage) (any, error) {
			return map[string]string{"echo": string(params)}, nil
		},
	})

	result, err := server.CallTool("echo", json.RawMessage(`"hello"`))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	m := result.(map[string]string)
	if m["echo"] != `"hello"` {
		t.Fatalf("expected echo of hello, got %v", m)
	}
}

func TestServerReturnsErrorForUnknownTool(t *testing.T) {
	server := NewServer()
	_, err := server.CallTool("nonexistent", nil)
	if err == nil {
		t.Fatal("expected error for unknown tool")
	}
}

func TestListToolsReturnsAll(t *testing.T) {
	server := NewServer()
	server.RegisterTool(Tool{Name: "a", Description: "tool a", Handler: func(p json.RawMessage) (any, error) { return nil, nil }})
	server.RegisterTool(Tool{Name: "b", Description: "tool b", Handler: func(p json.RawMessage) (any, error) { return nil, nil }})
	tools := server.ListTools()
	if len(tools) != 2 {
		t.Fatalf("expected 2 tools, got %d", len(tools))
	}
}

func TestGetHotRankProxiesJava(t *testing.T) {
	// Mock Java service
	javaServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/hotrank/CN/top" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if r.URL.Query().Get("k") != "5" {
			t.Fatalf("unexpected k: %s", r.URL.Query().Get("k"))
		}
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `[{"contentId":"c-1","score":100},{"contentId":"c-2","score":80}]`)
	}))
	defer javaServer.Close()

	server := NewServer()
	RegisterHotRankTools(server, HotRankToolConfig{JavaServiceURL: javaServer.URL})

	params, _ := json.Marshal(GetHotRankParams{Region: "CN", K: 5})
	result, err := server.CallTool("get_hot_rank", params)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	items := result.([]RankedItem)
	if len(items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(items))
	}
	if items[0].ContentId != "c-1" || items[0].Rank != 1 {
		t.Fatalf("unexpected first item: %+v", items[0])
	}
}

func TestGetHotRankRejectsEmptyRegion(t *testing.T) {
	server := NewServer()
	RegisterHotRankTools(server, HotRankToolConfig{JavaServiceURL: "http://localhost:9999"})

	params, _ := json.Marshal(GetHotRankParams{Region: "", K: 5})
	_, err := server.CallTool("get_hot_rank", params)
	if err == nil {
		t.Fatal("expected error for empty region")
	}
}

func TestHTTPHandlerCallTool(t *testing.T) {
	server := NewServer()
	server.RegisterTool(Tool{
		Name:        "ping",
		Description: "Returns pong",
		Handler: func(params json.RawMessage) (any, error) {
			return map[string]string{"result": "pong"}, nil
		},
	})

	handler := server.HTTPHandler()
	req := httptest.NewRequest("POST", "/mcp/call", strings.NewReader(`{"tool":"ping","params":null}`))
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Code)
	}
}