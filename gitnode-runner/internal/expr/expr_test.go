package expr

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func env(status string, kv ...string) Env {
	vars := map[string]string{}
	for i := 0; i+1 < len(kv); i += 2 {
		vars[kv[i]] = kv[i+1]
	}
	return Env{Vars: vars, Status: status}
}

func TestEval(t *testing.T) {
	e := env("success",
		"github.ref", "refs/heads/main",
		"github.event_name", "push",
		"inputs.skip", "false",
		"inputs.flag", "true",
		"inputs.target", "vps",
		"env.CI", "true",
		"matrix.os", "Linux",
		"needs.build.result", "success",
		"steps.v.outputs.version", "1.2.3",
	)

	cases := []struct {
		src  string
		want any
	}{
		{"true", true},
		{"false", false},
		{"null", nil},
		{"1", float64(1)},
		{"-2.5", -2.5},
		{"0x10", float64(16)},
		{"1e2", float64(100)},
		{"'it''s'", "it's"},
		{"github.ref == 'refs/heads/main'", true},
		{"github.ref == 'REFS/HEADS/MAIN'", true}, // case-insensitive like GitHub
		{"github.ref != 'refs/heads/dev'", true},
		{"github.event_name == 'push' && inputs.target == 'vps'", true},
		{"github.event_name == 'pull_request' || inputs.target == 'vps'", true},
		{"github.event_name == 'pull_request' || inputs.target == 'default'", false},
		{"!(inputs.target == 'vps')", false},
		{"!false", true},
		{"inputs.skip != 'true'", true},
		{"inputs.skip == false", true}, // inputs "true"/"false" behave as booleans like on GitHub
		{"inputs.skip == 'false'", true},
		{"inputs.skip != true", true},
		{"!inputs.skip", true},
		{"inputs.skip || 'x'", "x"},
		{"inputs.flag == true", true},
		{"inputs.flag != 'true'", false},
		{"inputs.flag && 'on'", "on"},
		{"inputs.target == true", false},
		{"inputs.missing == ''", true}, // unknown context is null; null == ''
		{"inputs.missing", nil},
		{"inputs.missing || 'fallback'", "fallback"},
		{"inputs.target || 'fallback'", "vps"},
		{"inputs.target && 'yes'", "yes"},
		{"env['CI'] == 'true'", true},
		{"matrix.os == 'linux'", true},
		{"needs.build.result == 'success'", true},
		{"steps.v.outputs.version", "1.2.3"},
		{"steps.v.outputs['version']", "1.2.3"},
		{"1 < 2", true},
		{"2 <= 2", true},
		{"3 > 2", true},
		{"3 >= 4", false},
		{"'a' < 'b'", true},
		{"contains('Hello World', 'world')", true},
		{"contains(github.ref, 'feature')", false},
		{"startsWith(github.ref, 'refs/heads/')", true},
		{"endsWith(github.ref, '/main')", true},
		{"format('{0}-{1}-{0}', 'a', 'b')", "a-b-a"},
		{"format('{{literal}} {0}', 'x')", "{literal} x"},
		{"join('a, b ,c', '/')", "a/b/c"},
		{"toJSON('x')", `"x"`},
		{"fromJSON('true')", true},
		{"fromJSON('42')", float64(42)},
		{"success()", true},
		{"failure()", false},
		{"always()", true},
		{"cancelled()", false},
		{"success() && github.ref == 'refs/heads/main'", true},
		{"(1 == 1) && (2 == 2)", true},
		{"true || false && false", true}, // && binds tighter than ||
	}
	for _, tc := range cases {
		t.Run(tc.src, func(t *testing.T) {
			got, err := Eval(tc.src, e)
			require.NoError(t, err)
			assert.Equal(t, tc.want, got)
		})
	}
}

func TestEval_statusFunctions(t *testing.T) {
	for _, tc := range []struct {
		status, src string
		want        bool
	}{
		{"success", "success()", true},
		{"failure", "success()", false},
		{"failure", "failure()", true},
		{"success", "failure()", false},
		{"cancelled", "cancelled()", true},
		{"failure", "always()", true},
		{"", "success()", true},
	} {
		got, err := Eval(tc.src, env(tc.status))
		require.NoError(t, err)
		assert.Equal(t, tc.want, got, "%s with status %q", tc.src, tc.status)
	}
}

func TestEval_errors(t *testing.T) {
	for _, src := range []string{
		"'unterminated",
		"(1 == 1",
		"1 ==",
		"unknownFn(1)",
		"contains('a')",
		"github..ref",
		"1 2",
		"@",
		"inputs.x[",
	} {
		_, err := Eval(src, env("success"))
		assert.Error(t, err, src)
	}
}

func TestTruthy(t *testing.T) {
	for v, want := range map[string]bool{"": false, "0": true, "x": true} {
		assert.Equal(t, want, Truthy(v), "string %q", v)
	}
	assert.False(t, Truthy(nil))
	assert.False(t, Truthy(false))
	assert.False(t, Truthy(float64(0)))
	assert.True(t, Truthy(float64(-1)))
	assert.True(t, Truthy(true))
}

func TestInterpolate(t *testing.T) {
	e := env("success", "github.sha", "abc123", "inputs.version", "1.0.0", "inputs.n", "3")

	got, err := Interpolate("img:${{ inputs.version }}-${{github.sha}} x${{ inputs.n }}", e)
	require.NoError(t, err)
	assert.Equal(t, "img:1.0.0-abc123 x3", got)

	got, err = Interpolate("plain text, no expression", e)
	require.NoError(t, err)
	assert.Equal(t, "plain text, no expression", got)

	got, err = Interpolate("v=${{ inputs.missing }}.", e)
	require.NoError(t, err)
	assert.Equal(t, "v=.", got, "unknown context renders as empty string")

	got, err = Interpolate("${{ inputs.version == '1.0.0' && 'new' || 'old' }}", e)
	require.NoError(t, err)
	assert.Equal(t, "new", got)

	got, err = Interpolate("a ${{ ( }} b ${{ inputs.version }}", e)
	assert.Error(t, err)
	assert.Equal(t, "a ${{ ( }} b 1.0.0", got, "bad expression kept, others still resolved")
}

func TestEvalCondition(t *testing.T) {
	e := env("failure", "github.ref", "refs/heads/main", "inputs.skip", "true")

	for _, tc := range []struct {
		cond string
		want bool
	}{
		{"", false}, // empty == success(), job already failed
		{"success()", false},
		{"failure()", true},
		{"always()", true},
		{"${{ failure() }}", true},
		{"${{ always() && github.ref == 'refs/heads/main' }}", true},
		{"failure() && github.ref == 'refs/heads/dev'", false},
		{"${{ inputs.skip != 'true' }}", false},
		{"inputs.skip == 'true'", true},
		{"github.ref", true},
		{"false", false},
		{"'false'", true}, // non-empty string is truthy, like GitHub
		{"${{ github.ref }} == 'refs/heads/main'", true},
		{"${{ github.ref }} == 'refs/heads/dev'", false},
		{"${{ github.ref }} != 'refs/heads/dev'", true},
		{"${{ inputs.skip }} == 'true'", true},
		{"${{ inputs.skip }} == true", true},
		{"prefix ${{ github.ref }}", true}, // not an expression: non-empty string is truthy, like GitHub
		{"${{ inputs.missing }}", false},
	} {
		got, err := EvalCondition(tc.cond, e)
		require.NoError(t, err, tc.cond)
		assert.Equal(t, tc.want, got, tc.cond)
	}

	got, err := EvalCondition("", env("success"))
	require.NoError(t, err)
	assert.True(t, got)

	_, err = EvalCondition("${{ ( }}", e)
	assert.Error(t, err)
}

func TestHashFiles(t *testing.T) {
	dir := t.TempDir()
	require.NoError(t, os.MkdirAll(filepath.Join(dir, "sub", "deep"), 0o750))
	require.NoError(t, os.MkdirAll(filepath.Join(dir, "node_modules"), 0o750))
	require.NoError(t, os.WriteFile(filepath.Join(dir, "pnpm-lock.yaml"), []byte("a"), 0o600))
	require.NoError(t, os.WriteFile(filepath.Join(dir, "sub", "pnpm-lock.yaml"), []byte("b"), 0o600))
	require.NoError(t, os.WriteFile(filepath.Join(dir, "sub", "deep", "x.txt"), []byte("c"), 0o600))
	require.NoError(t, os.WriteFile(filepath.Join(dir, "node_modules", "pnpm-lock.yaml"), []byte("z"), 0o600))

	e := Env{Vars: map[string]string{}, Status: "success", WorkDir: dir}

	root, err := Eval("hashFiles('pnpm-lock.yaml')", e)
	require.NoError(t, err)
	all, err := Eval("hashFiles('**/pnpm-lock.yaml')", e)
	require.NoError(t, err)
	assert.NotEmpty(t, root)
	assert.NotEmpty(t, all)
	assert.NotEqual(t, root, all, "** also matches the nested lockfile")

	again, err := Eval("hashFiles('**/pnpm-lock.yaml')", e)
	require.NoError(t, err)
	assert.Equal(t, all, again, "deterministic")

	multi, err := Eval("hashFiles('**/pnpm-lock.yaml', '**/*.txt')", e)
	require.NoError(t, err)
	assert.NotEqual(t, all, multi)

	none, err := Eval("hashFiles('**/nope.lock')", e)
	require.NoError(t, err)
	assert.Equal(t, "", none)

	// node_modules is skipped, so it must not influence the hash
	require.NoError(t, os.WriteFile(filepath.Join(dir, "node_modules", "pnpm-lock.yaml"), []byte("changed"), 0o600))
	after, err := Eval("hashFiles('**/pnpm-lock.yaml')", e)
	require.NoError(t, err)
	assert.Equal(t, all, after)

	noDir, err := Eval("hashFiles('x')", env("success"))
	require.NoError(t, err)
	assert.Equal(t, "", noDir)
}

func TestToString(t *testing.T) {
	assert.Equal(t, "", ToString(nil))
	assert.Equal(t, "true", ToString(true))
	assert.Equal(t, "3", ToString(float64(3)))
	assert.Equal(t, "3.5", ToString(3.5))
	assert.Equal(t, "x", ToString("x"))
}

func TestEval_arrayFunctions(t *testing.T) {
	e := env("success", "needs.plan.outputs.list", `["a","B",3,{"k":1}]`)
	cases := map[string]any{
		`join(fromJSON('["a","b","c"]'), '+')`:           "a+b+c",
		`join(fromJSON('["a","b"]'))`:                    "a,b",
		`join(fromJSON(needs.plan.outputs.list), ' | ')`: `a | B | 3 | {"k":1}`,
		`join('x,y')`: "x,y",
		`contains(fromJSON('["alpha","beta"]'), 'BETA')`: true,
		`contains(fromJSON('["alpha","beta"]'), 'bet')`:  false, // membership, not substring
		`contains(fromJSON(needs.plan.outputs.list), 3)`: true,
		`contains('Hello World', 'world')`:               true,
		`contains(fromJSON('["a"]'), 'b')`:               false,
		`contains('[not json', 'json')`:                  true,
	}
	for src, want := range cases {
		got, err := Eval(src, e)
		require.NoError(t, err, src)
		assert.Equal(t, want, got, src)
	}
}
