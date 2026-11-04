package context

import "testing"

func TestAddMaskIsPartOfSecretValues(t *testing.T) {
	c := New(nil, map[string]string{"A": "secret"})
	c.AddMask("masked")
	c.AddMask("")
	got := c.SecretValues()
	if len(got) != 2 {
		t.Fatalf("got %v", got)
	}
}
