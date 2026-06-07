package actions_test

import (
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/nuricanozturk/originhub-runner/internal/actions"
)

func TestSetupPythonAction_Name(t *testing.T) {
	a := actions.NewSetupPythonAction()
	assert.Equal(t, "actions/setup-python", a.Name())
}

func TestSetupPythonAction_Versions(t *testing.T) {
	a := actions.NewSetupPythonAction()
	assert.Contains(t, a.Versions(), "v1")
}

func TestSetupPythonAction_NotEmpty(t *testing.T) {
	a := actions.NewSetupPythonAction()
	assert.NotEmpty(t, a.Name())
	assert.NotEmpty(t, a.Versions())
}

func TestSetupPythonAction_RegisteredInRegistry(t *testing.T) {
	reg := actions.NewRegistry("", "")
	handler, err := reg.Resolve("actions/setup-python@v1")
	assert.NoError(t, err)
	assert.NotNil(t, handler)
}
