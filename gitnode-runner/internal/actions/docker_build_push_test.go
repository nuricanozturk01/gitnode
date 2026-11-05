package actions

import (
	"reflect"
	"testing"
)

func TestSplitNewlinesKeepsCommas(t *testing.T) {
	got := splitNewlines("type=gha,mode=max\n\ntype=registry,ref=x/y")
	want := []string{"type=gha,mode=max", "type=registry,ref=x/y"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v want %v", got, want)
	}
}
