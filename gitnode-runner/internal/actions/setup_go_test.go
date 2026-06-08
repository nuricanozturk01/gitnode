package actions_test

import (
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/nuricanozturk/gitnode-runner/internal/actions"
)

func TestSetupGoAction_Name(t *testing.T) {
	a := actions.NewSetupGoAction()
	assert.Equal(t, "actions/setup-go", a.Name())
}

func TestSetupGoAction_Versions(t *testing.T) {
	a := actions.NewSetupGoAction()
	assert.Contains(t, a.Versions(), "v1")
}

func TestSetupGoAction_RegisteredInRegistry(t *testing.T) {
	reg := actions.NewRegistry("", "")
	handler, err := reg.Resolve("actions/setup-go@v1")
	assert.NoError(t, err)
	assert.NotNil(t, handler)
}
