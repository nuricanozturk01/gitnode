package actions

import (
	"bytes"
	"errors"
	"io"
	"os"
	"path/filepath"
	"testing"
)

func TestParseRestoreKeys_Empty(t *testing.T) {
	keys := parseRestoreKeys("")
	if len(keys) != 0 {
		t.Errorf("expected empty slice, got %v", keys)
	}
}

func TestParseRestoreKeys_MultiLine(t *testing.T) {
	raw := "maven-\ngradle-\n"
	keys := parseRestoreKeys(raw)
	if len(keys) != 2 {
		t.Errorf("expected 2 keys, got %d: %v", len(keys), keys)
	}
	if keys[0] != "maven-" || keys[1] != "gradle-" {
		t.Errorf("unexpected keys: %v", keys)
	}
}

func TestBoolStr(t *testing.T) {
	if boolStr(true) != "true" {
		t.Error("boolStr(true) should return 'true'")
	}
	if boolStr(false) != "false" {
		t.Error("boolStr(false) should return 'false'")
	}
}

func TestTarGzUnTarGz_RoundTrip(t *testing.T) {
	src := t.TempDir()

	testFile := filepath.Join(src, "hello.txt")
	if err := os.WriteFile(testFile, []byte("hello world"), 0o644); err != nil {
		t.Fatal(err)
	}

	var buf bytes.Buffer
	if err := tarGz(src, &buf); err != nil {
		t.Fatalf("tarGz: %v", err)
	}
	if buf.Len() == 0 {
		t.Fatal("expected non-empty tar.gz output")
	}

	dest := t.TempDir()
	if err := unTarGz(bytes.NewReader(buf.Bytes()), dest); err != nil && !errors.Is(err, io.EOF) {
		t.Fatalf("unTarGz: %v", err)
	}
}
