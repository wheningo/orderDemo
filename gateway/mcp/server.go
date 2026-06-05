package mcp

import (
	"encoding/json"
	"fmt"
	"net/http"
)

type Tool struct {
	Name        string
	Description string
	Handler     func(params json.RawMessage) (any, error)
}

type Server struct {
	tools map[string]Tool
}

func NewServer() *Server {
	return &Server{tools: make(map[string]Tool)}
}

func (s *Server) RegisterTool(tool Tool) {
	s.tools[tool.Name] = tool
}

func (s *Server) ListTools() []Tool {
	result := make([]Tool, 0, len(s.tools))
	for _, t := range s.tools {
		result = append(result, t)
	}
	return result
}

func (s *Server) CallTool(name string, params json.RawMessage) (any, error) {
	tool, ok := s.tools[name]
	if !ok {
		return nil, fmt.Errorf("unknown tool: %s", name)
	}
	return tool.Handler(params)
}

func (s *Server) HTTPHandler() http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /mcp/tools", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		type toolInfo struct {
			Name        string `json:"name"`
			Description string `json:"description"`
		}
		tools := make([]toolInfo, 0)
		for _, t := range s.tools {
			tools = append(tools, toolInfo{Name: t.Name, Description: t.Description})
		}
		json.NewEncoder(w).Encode(tools)
	})

	mux.HandleFunc("POST /mcp/call", func(w http.ResponseWriter, r *http.Request) {
		var req struct {
			Tool   string          `json:"tool"`
			Params json.RawMessage `json:"params"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		result, err := s.CallTool(req.Tool, req.Params)
		if err != nil {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusBadRequest)
			json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
			return
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(result)
	})

	return mux
}
