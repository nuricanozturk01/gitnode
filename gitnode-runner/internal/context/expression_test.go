package context

import (
	"testing"
)

func TestEvaluate_SingleExpression(t *testing.T) {
	ctx := map[string]string{"github.sha": "abc123"}
	got := Evaluate("commit: ${{ github.sha }}", ctx)
	if got != "commit: abc123" {
		t.Errorf("got %q", got)
	}
}

func TestEvaluate_MultipleExpressions(t *testing.T) {
	ctx := map[string]string{
		"github.actor": "octocat",
		"github.sha":   "def456",
	}
	got := Evaluate("${{ github.actor }} pushed ${{ github.sha }}", ctx)
	want := "octocat pushed def456"
	if got != want {
		t.Errorf("got %q, want %q", got, want)
	}
}

func TestEvaluate_UnknownKept(t *testing.T) {
	ctx := map[string]string{}
	expr := "${{ unknown.var }}"
	got := Evaluate(expr, ctx)
	if got != expr {
		t.Errorf("expected unchanged, got %q", got)
	}
}

func TestEvaluate_NoExpression(t *testing.T) {
	got := Evaluate("plain string", nil)
	if got != "plain string" {
		t.Errorf("got %q", got)
	}
}

func TestEvaluateCondition_Always(t *testing.T) {
	for _, status := range []string{"success", "failure", "cancelled"} {
		if !EvaluateCondition("always()", nil, status) {
			t.Errorf("always() should be true for status=%s", status)
		}
	}
}

func TestEvaluateCondition_Failure(t *testing.T) {
	if !EvaluateCondition("failure()", nil, "failure") {
		t.Error("failure() should be true when status=failure")
	}
	if EvaluateCondition("failure()", nil, "success") {
		t.Error("failure() should be false when status=success")
	}
}

func TestEvaluateCondition_Equality(t *testing.T) {
	ctx := map[string]string{"github.ref": "refs/heads/main"}
	if !EvaluateCondition("${{ github.ref }} == 'refs/heads/main'", ctx, "success") {
		t.Error("equality condition should be true")
	}
	if EvaluateCondition("${{ github.ref }} == 'refs/heads/dev'", ctx, "success") {
		t.Error("equality condition should be false")
	}
}

func TestEvaluateCondition_Inequality(t *testing.T) {
	ctx := map[string]string{"github.ref": "refs/heads/main"}
	if !EvaluateCondition("${{ github.ref }} != 'refs/heads/dev'", ctx, "success") {
		t.Error("inequality condition should be true")
	}
}

func TestEvaluateCondition_FalseString(t *testing.T) {
	if EvaluateCondition("false", nil, "success") {
		t.Error("'false' string should be falsy")
	}
}

func TestEvaluateCondition_EmptyString(t *testing.T) {
	if EvaluateCondition("", nil, "success") {
		t.Error("empty string should be falsy")
	}
}
