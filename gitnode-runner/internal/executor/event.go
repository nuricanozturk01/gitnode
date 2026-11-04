package executor

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
)

// writeEventFile stores the triggering event as JSON for $GITHUB_EVENT_PATH. Without a payload
// it writes "{}", as GitHub does for events without one, so scripts can always read the file.
func writeEventFile(dir string, event json.RawMessage) (string, error) {
	path := filepath.Join(dir, "event.json")
	data := []byte(event)
	if len(data) == 0 || string(data) == "null" {
		data = []byte("{}")
	}
	if err := os.WriteFile(path, data, 0o600); err != nil {
		return "", fmt.Errorf("write event file: %w", err)
	}
	return path, nil
}

// flattenEvent turns the event payload into dotted expression keys:
// {"pull_request":{"title":"x"},"commits":[{"id":"1"}]} → github.event.pull_request.title,
// github.event.commits.0.id. Scalars are stringified; objects and arrays are skipped (their
// leaves are present), except that the whole payload is kept under "github.event" as JSON so
// toJSON(github.event) and fromJSON work.
func flattenEvent(event json.RawMessage) map[string]string {
	out := map[string]string{}
	if len(event) == 0 {
		return out
	}
	var root any
	if err := json.Unmarshal(event, &root); err != nil {
		return out
	}
	out["github.event"] = string(event)
	walkEvent("github.event", root, out)
	return out
}

func walkEvent(prefix string, node any, out map[string]string) {
	switch v := node.(type) {
	case map[string]any:
		for k, child := range v {
			walkEvent(prefix+"."+k, child, out)
		}
	case []any:
		for i, child := range v {
			walkEvent(prefix+"."+strconv.Itoa(i), child, out)
		}
	case string:
		out[prefix] = v
	case float64:
		out[prefix] = strconv.FormatFloat(v, 'f', -1, 64)
	case bool:
		out[prefix] = strconv.FormatBool(v)
	case nil:
		out[prefix] = ""
	}
}
