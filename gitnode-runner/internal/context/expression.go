package context

import (
	"regexp"
	"strings"
)

var exprPattern = regexp.MustCompile(`\$\{\{\s*(.+?)\s*\}\}`)

// Evaluate interpolates all ${{ expr }} expressions in template using ctx.
// Unresolved expressions are kept as-is.
func Evaluate(template string, ctx map[string]string) string {
	return exprPattern.ReplaceAllStringFunc(template, func(match string) string {
		sub := exprPattern.FindStringSubmatch(match)
		if len(sub) < 2 {
			return match
		}
		key := strings.TrimSpace(sub[1])
		if v, ok := ctx[key]; ok {
			return v
		}
		return match
	})
}

// EvaluateCondition evaluates an if: condition string to bool.
// Supports always(), failure(), success(), == and != comparisons.
func EvaluateCondition(condition string, ctx map[string]string, jobStatus string) bool {
	interpolated := strings.TrimSpace(Evaluate(condition, ctx))

	switch strings.ToLower(interpolated) {
	case "always()":
		return true
	case "failure()":
		return strings.EqualFold(jobStatus, "failure")
	case "success()":
		return strings.EqualFold(jobStatus, "success")
	case "cancelled()":
		return strings.EqualFold(jobStatus, "cancelled")
	case "false", "":
		return false
	}

	if idx := strings.Index(interpolated, "=="); idx != -1 {
		lhs := normalize(interpolated[:idx])
		rhs := normalize(interpolated[idx+2:])
		return lhs == rhs
	}
	if idx := strings.Index(interpolated, "!="); idx != -1 {
		lhs := normalize(interpolated[:idx])
		rhs := normalize(interpolated[idx+2:])
		return lhs != rhs
	}

	return true
}

func normalize(s string) string {
	s = strings.TrimSpace(s)
	s = strings.Trim(s, `'"`)
	return s
}
