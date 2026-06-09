package registration

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"runtime"
	"time"

	"github.com/nuricanozturk/gitnode-runner/internal/config"
)

const version = "0.1.0"

type regRequest struct {
	Token        string   `json:"token"`
	Name         string   `json:"name"`
	Labels       []string `json:"labels"`
	OS           string   `json:"os"`
	Arch         string   `json:"arch"`
	Version      string   `json:"version"`
	ExecutorType string   `json:"executorType"`
}

type regResponse struct {
	RunnerID string   `json:"runnerId"`
	Token    string   `json:"token"`
	Labels   []string `json:"labels"`
}

// Register calls POST /api/actions/runners/register and writes runnerId +
// runnerToken into cfg. Caller is responsible for persisting cfg afterward.
func Register(cfg *config.Config) error {
	body, err := json.Marshal(regRequest{
		Token:        cfg.Token,
		Name:         cfg.Name,
		Labels:       cfg.Labels,
		OS:           runtime.GOOS,
		Arch:         runtime.GOARCH,
		Version:      version,
		ExecutorType: cfg.Executor,
	})
	if err != nil {
		return fmt.Errorf("marshal: %w", err)
	}

	req, err := http.NewRequestWithContext(
		context.Background(),
		http.MethodPost,
		cfg.ServerURL+"/api/actions/runners/register",
		bytes.NewReader(body),
	)
	if err != nil {
		return fmt.Errorf("build request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	httpClient := &http.Client{Timeout: 15 * time.Second}
	resp, err := httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("http: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusCreated {
		return fmt.Errorf("registration failed: HTTP %d", resp.StatusCode)
	}

	var result regResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return fmt.Errorf("decode: %w", err)
	}

	cfg.RunnerID = result.RunnerID
	cfg.RunnerToken = result.Token
	return nil
}
