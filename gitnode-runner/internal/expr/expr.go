// Package expr implements the GitHub Actions expression language
// (https://docs.github.com/actions/learn-github-actions/expressions) closely enough that
// workflows written for GitHub evaluate identically on GitNode.
//
// Supported: literals (null, bool, number, 'string'), contexts (dotted paths and ['index']),
// ( ), !, <, <=, >, >=, ==, !=, &&, || (value-returning, like GitHub), and the functions
// contains, startsWith, endsWith, format, join, toJSON, fromJSON, hashFiles plus the status
// functions success(), failure(), cancelled() and always().
package expr

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io/fs"
	"math"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"unicode"
)

// Env is everything an expression can see.
type Env struct {
	// Vars holds flattened context values keyed by dotted path, lower-case first segment is NOT
	// required: lookups are case-insensitive. Example keys: "github.sha", "env.FOO",
	// "steps.build.outputs.path", "inputs.version", "needs.test.result", "matrix.os".
	Vars map[string]string
	// Status is the running job status used by success()/failure()/cancelled():
	// "success", "failure" or "cancelled".
	Status string
	// WorkDir is the root used by hashFiles().
	WorkDir string
}

var numberRe = regexp.MustCompile(`^-?(?:0[xX][0-9a-fA-F]+|(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?)`)

var exprBlock = regexp.MustCompile(`\$\{\{\s*(.*?)\s*\}\}`)

// Interpolate replaces every ${{ expr }} in template with its string value. An expression that
// fails to parse or evaluate is left untouched and reported through the returned error (the
// first failure), so callers can log it without losing the rest of the output.
func Interpolate(template string, env Env) (string, error) {
	var firstErr error
	out := exprBlock.ReplaceAllStringFunc(template, func(match string) string {
		inner := exprBlock.FindStringSubmatch(match)[1]
		v, err := Eval(inner, env)
		if err != nil {
			if firstErr == nil {
				firstErr = fmt.Errorf("expression %q: %w", inner, err)
			}
			return match
		}
		return ToString(v)
	})
	return out, firstErr
}

// EvalCondition evaluates an if: condition. An empty condition means success(), matching
// GitHub. A condition may be a bare expression or wrapped in ${{ }}.
func EvalCondition(cond string, env Env) (bool, error) {
	cond = strings.TrimSpace(cond)
	if cond == "" {
		return env.Status == "" || env.Status == "success", nil
	}
	if m := exprBlock.FindStringSubmatch(cond); m != nil && m[0] == cond {
		cond = m[1]
	} else if strings.Contains(cond, "${{") {
		// Mixed text and ${{ }}, e.g. `${{ github.ref }} == 'refs/heads/main'`. GitHub would turn
		// this into a plain (always truthy) string. GitNode instead substitutes each block with a
		// quoted literal and evaluates the result, which is what the author meant. Text that is
		// not an expression after substitution keeps GitHub's behaviour: truthy when non-empty.
		var interpErr error
		quoted := exprBlock.ReplaceAllStringFunc(cond, func(match string) string {
			v, err := Eval(exprBlock.FindStringSubmatch(match)[1], env)
			if err != nil {
				if interpErr == nil {
					interpErr = err
				}
				return match
			}
			return "'" + strings.ReplaceAll(ToString(v), "'", "''") + "'"
		})
		if interpErr != nil {
			return false, interpErr
		}
		if strings.TrimSpace(quoted) == "" {
			return false, nil
		}
		if v, evalErr := Eval(quoted, env); evalErr == nil {
			return Truthy(v), nil
		}
		return true, nil
	}
	v, err := Eval(cond, env)
	if err != nil {
		return false, err
	}
	return Truthy(v), nil
}

// Eval parses and evaluates a bare expression (no ${{ }}).
func Eval(src string, env Env) (any, error) {
	toks, err := tokenize(src)
	if err != nil {
		return nil, err
	}
	p := &parser{toks: toks, env: env}
	v, err := p.parseOr()
	if err != nil {
		return nil, err
	}
	if p.peek().kind != tEOF {
		return nil, fmt.Errorf("unexpected %q", p.peek().text)
	}
	return v, nil
}

// Truthy implements GitHub's truthiness: false, 0, -0, "", null and NaN are falsy.
func Truthy(v any) bool {
	switch x := v.(type) {
	case nil:
		return false
	case bool:
		return x
	case float64:
		return x != 0 && !math.IsNaN(x)
	case string:
		return x != ""
	}
	return true
}

// ToString renders a value the way GitHub does when interpolating into text.
func ToString(v any) string {
	switch x := v.(type) {
	case nil:
		return ""
	case bool:
		return strconv.FormatBool(x)
	case float64:
		if x == math.Trunc(x) && math.Abs(x) < 1e15 {
			return strconv.FormatInt(int64(x), 10)
		}
		return strconv.FormatFloat(x, 'g', -1, 64)
	case string:
		return x
	}
	return fmt.Sprint(v)
}

// ── tokenizer ────────────────────────────────────────────────────────────────

type tokKind int

const (
	tEOF tokKind = iota
	tNum
	tStr
	tIdent
	tOp
	tLParen
	tRParen
	tLBrack
	tRBrack
	tComma
	tDot
)

type token struct {
	kind tokKind
	text string
}

func tokenize(s string) ([]token, error) {
	var toks []token
	rs := []rune(s)
	for i := 0; i < len(rs); {
		c := rs[i]
		switch {
		case unicode.IsSpace(c):
			i++
		case c == '(':
			toks, i = append(toks, token{tLParen, "("}), i+1
		case c == ')':
			toks, i = append(toks, token{tRParen, ")"}), i+1
		case c == '[':
			toks, i = append(toks, token{tLBrack, "["}), i+1
		case c == ']':
			toks, i = append(toks, token{tRBrack, "]"}), i+1
		case c == ',':
			toks, i = append(toks, token{tComma, ","}), i+1
		case c == '.' && (i+1 >= len(rs) || !unicode.IsDigit(rs[i+1])):
			toks, i = append(toks, token{tDot, "."}), i+1
		case c == '\'':
			var b strings.Builder
			i++
			closed := false
			for i < len(rs) {
				if rs[i] == '\'' {
					if i+1 < len(rs) && rs[i+1] == '\'' { // '' escapes a quote
						b.WriteRune('\'')
						i += 2
						continue
					}
					closed = true
					i++
					break
				}
				b.WriteRune(rs[i])
				i++
			}
			if !closed {
				return nil, fmt.Errorf("unterminated string")
			}
			toks = append(toks, token{tStr, b.String()})
		case unicode.IsDigit(c) || (c == '-' && i+1 < len(rs) && unicode.IsDigit(rs[i+1])) || c == '.':
			loc := numberRe.FindStringIndex(string(rs[i:]))
			if loc == nil || loc[0] != 0 {
				return nil, fmt.Errorf("bad number near %q", string(rs[i:]))
			}
			text := string(rs[i:])[:loc[1]]
			toks = append(toks, token{tNum, text})
			i += len([]rune(text))
		case unicode.IsLetter(c) || c == '_' || c == '*':
			j := i + 1
			for j < len(rs) && (unicode.IsLetter(rs[j]) || unicode.IsDigit(rs[j]) || rs[j] == '_' || rs[j] == '-' || rs[j] == '*') {
				j++
			}
			toks = append(toks, token{tIdent, string(rs[i:j])})
			i = j
		default:
			two := ""
			if i+1 < len(rs) {
				two = string(rs[i : i+2])
			}
			switch two {
			case "==", "!=", "<=", ">=", "&&", "||":
				toks, i = append(toks, token{tOp, two}), i+2
				continue
			}
			if c == '!' || c == '<' || c == '>' {
				toks, i = append(toks, token{tOp, string(c)}), i+1
				continue
			}
			return nil, fmt.Errorf("unexpected character %q", c)
		}
	}
	return append(toks, token{tEOF, ""}), nil
}

// ── parser / evaluator ───────────────────────────────────────────────────────

type parser struct {
	toks []token
	pos  int
	env  Env
}

func (p *parser) peek() token { return p.toks[p.pos] }
func (p *parser) next() token {
	t := p.toks[p.pos]
	if t.kind != tEOF {
		p.pos++
	}
	return t
}

func (p *parser) isOp(text string) bool {
	t := p.peek()
	return t.kind == tOp && t.text == text
}

func (p *parser) parseOr() (any, error) {
	l, err := p.parseAnd()
	if err != nil {
		return nil, err
	}
	for p.isOp("||") {
		p.next()
		r, err := p.parseAnd()
		if err != nil {
			return nil, err
		}
		if !Truthy(l) {
			l = r
		}
	}
	return l, nil
}

func (p *parser) parseAnd() (any, error) {
	l, err := p.parseEq()
	if err != nil {
		return nil, err
	}
	for p.isOp("&&") {
		p.next()
		r, err := p.parseEq()
		if err != nil {
			return nil, err
		}
		if Truthy(l) {
			l = r
		}
	}
	return l, nil
}

func (p *parser) parseEq() (any, error) {
	l, err := p.parseCmp()
	if err != nil {
		return nil, err
	}
	for p.isOp("==") || p.isOp("!=") {
		op := p.next().text
		r, err := p.parseCmp()
		if err != nil {
			return nil, err
		}
		eq := looseEqual(l, r)
		if op == "!=" {
			eq = !eq
		}
		l = eq
	}
	return l, nil
}

func (p *parser) parseCmp() (any, error) {
	l, err := p.parseUnary()
	if err != nil {
		return nil, err
	}
	for p.isOp("<") || p.isOp("<=") || p.isOp(">") || p.isOp(">=") {
		op := p.next().text
		r, err := p.parseUnary()
		if err != nil {
			return nil, err
		}
		l = compare(op, l, r)
	}
	return l, nil
}

func (p *parser) parseUnary() (any, error) {
	if p.isOp("!") {
		p.next()
		v, err := p.parseUnary()
		if err != nil {
			return nil, err
		}
		return !Truthy(v), nil
	}
	return p.parsePrimary()
}

func (p *parser) parsePrimary() (any, error) {
	t := p.next()
	switch t.kind {
	case tLParen:
		v, err := p.parseOr()
		if err != nil {
			return nil, err
		}
		if p.next().kind != tRParen {
			return nil, fmt.Errorf("expected )")
		}
		return v, nil
	case tStr:
		return t.text, nil
	case tNum:
		f, err := parseNumber(t.text)
		if err != nil {
			return nil, fmt.Errorf("bad number %q", t.text)
		}
		return f, nil
	case tIdent:
		switch strings.ToLower(t.text) {
		case "true":
			return true, nil
		case "false":
			return false, nil
		case "null":
			return nil, nil
		}
		if p.peek().kind == tLParen {
			return p.parseCall(t.text)
		}
		return p.parsePath(t.text)
	}
	return nil, fmt.Errorf("unexpected %q", t.text)
}

func (p *parser) parsePath(first string) (any, error) {
	path := first
	for {
		switch p.peek().kind {
		case tDot:
			p.next()
			seg := p.next()
			if seg.kind != tIdent && seg.kind != tNum {
				return nil, fmt.Errorf("expected property name after '.'")
			}
			path += "." + seg.text
		case tLBrack:
			p.next()
			idx, err := p.parseOr()
			if err != nil {
				return nil, err
			}
			if p.next().kind != tRBrack {
				return nil, fmt.Errorf("expected ]")
			}
			path += "." + ToString(idx)
		default:
			return p.lookup(path), nil
		}
	}
}

func (p *parser) lookup(path string) any {
	raw, ok := p.env.Vars[path]
	if !ok {
		lp := strings.ToLower(path)
		for k, v := range p.env.Vars {
			if strings.ToLower(k) == lp {
				raw, ok = v, true
				break
			}
		}
	}
	if !ok {
		return nil // unknown context values are null, like GitHub
	}
	// GitNode transports dispatch inputs as strings, while GitHub gives boolean inputs real
	// boolean values. Restore that so `if: inputs.skip`, `inputs.skip == false` and `!inputs.skip`
	// behave as they do on GitHub.
	if strings.HasPrefix(strings.ToLower(path), "inputs.") {
		switch raw {
		case "true":
			return true
		case "false":
			return false
		}
	}
	return raw
}

func (p *parser) parseCall(name string) (any, error) {
	p.next() // (
	var args []any
	for p.peek().kind != tRParen {
		a, err := p.parseOr()
		if err != nil {
			return nil, err
		}
		args = append(args, a)
		if p.peek().kind == tComma {
			p.next()
		} else if p.peek().kind != tRParen {
			return nil, fmt.Errorf("expected , or ) in call to %s", name)
		}
	}
	p.next() // )
	return p.call(strings.ToLower(name), args)
}

func (p *parser) call(name string, a []any) (any, error) {
	need := func(n int) error {
		if len(a) < n {
			return fmt.Errorf("%s() needs %d argument(s)", name, n)
		}
		return nil
	}
	switch name {
	case "success":
		return p.env.Status == "" || p.env.Status == "success", nil
	case "failure":
		return p.env.Status == "failure", nil
	case "cancelled":
		return p.env.Status == "cancelled", nil
	case "always":
		return true, nil
	case "contains":
		if err := need(2); err != nil {
			return nil, err
		}
		return strings.Contains(strings.ToLower(ToString(a[0])), strings.ToLower(ToString(a[1]))), nil
	case "startswith":
		if err := need(2); err != nil {
			return nil, err
		}
		return strings.HasPrefix(strings.ToLower(ToString(a[0])), strings.ToLower(ToString(a[1]))), nil
	case "endswith":
		if err := need(2); err != nil {
			return nil, err
		}
		return strings.HasSuffix(strings.ToLower(ToString(a[0])), strings.ToLower(ToString(a[1]))), nil
	case "format":
		if err := need(1); err != nil {
			return nil, err
		}
		return formatString(ToString(a[0]), a[1:]), nil
	case "join":
		if err := need(1); err != nil {
			return nil, err
		}
		sep := ","
		if len(a) > 1 {
			sep = ToString(a[1])
		}
		// Arrays are not first-class here; a comma/newline separated string is joined.
		parts := strings.FieldsFunc(ToString(a[0]), func(r rune) bool { return r == ',' || r == '\n' })
		for i := range parts {
			parts[i] = strings.TrimSpace(parts[i])
		}
		return strings.Join(parts, sep), nil
	case "tojson":
		if err := need(1); err != nil {
			return nil, err
		}
		b, _ := json.Marshal(a[0])
		return string(b), nil
	case "fromjson":
		if err := need(1); err != nil {
			return nil, err
		}
		var v any
		if err := json.Unmarshal([]byte(ToString(a[0])), &v); err != nil {
			return nil, fmt.Errorf("fromJSON: %w", err)
		}
		switch x := v.(type) {
		case nil, bool, float64, string:
			return x, nil
		}
		return ToString(a[0]), nil // objects/arrays stay as their JSON text
	case "hashfiles":
		var pats []string
		for _, x := range a {
			pats = append(pats, ToString(x))
		}
		return hashFiles(p.env.WorkDir, pats), nil
	}
	return nil, fmt.Errorf("unknown function %s()", name)
}

// ── helpers ──────────────────────────────────────────────────────────────────

func parseNumber(s string) (float64, error) {
	if strings.HasPrefix(s, "0x") || strings.HasPrefix(s, "0X") {
		n, err := strconv.ParseInt(s[2:], 16, 64)
		return float64(n), err
	}
	return strconv.ParseFloat(s, 64)
}

func toNumber(v any) float64 {
	switch x := v.(type) {
	case nil:
		return 0
	case bool:
		if x {
			return 1
		}
		return 0
	case float64:
		return x
	case string:
		s := strings.TrimSpace(x)
		if s == "" {
			return 0
		}
		if f, err := parseNumber(s); err == nil {
			return f
		}
	}
	return math.NaN()
}

func looseEqual(l, r any) bool {
	ls, lok := l.(string)
	rs, rok := r.(string)
	if lok && rok {
		return strings.EqualFold(ls, rs)
	}
	// Deliberate, documented deviation from GitHub: a boolean compared with the string "true" or
	// "false" compares as booleans, so GitNode-style `inputs.flag != 'true'` keeps working next to
	// GitHub-style `inputs.flag != true`.
	if lb, ok := l.(bool); ok && rok {
		if rb, isBool := boolString(rs); isBool {
			return lb == rb
		}
	}
	if rb, ok := r.(bool); ok && lok {
		if lb, isBool := boolString(ls); isBool {
			return lb == rb
		}
	}
	if l == nil && r == nil {
		return true
	}
	if l == nil || r == nil {
		// null only equals null, empty string and 0/false via number coercion
		return toNumber(l) == toNumber(r) && !math.IsNaN(toNumber(l))
	}
	ln, rn := toNumber(l), toNumber(r)
	if math.IsNaN(ln) || math.IsNaN(rn) {
		return false
	}
	return ln == rn
}

func boolString(s string) (value, ok bool) {
	switch strings.ToLower(s) {
	case "true":
		return true, true
	case "false":
		return false, true
	}
	return false, false
}

func compare(op string, l, r any) bool {
	ls, lok := l.(string)
	rs, rok := r.(string)
	if lok && rok {
		a, b := strings.ToLower(ls), strings.ToLower(rs)
		switch op {
		case "<":
			return a < b
		case "<=":
			return a <= b
		case ">":
			return a > b
		default:
			return a >= b
		}
	}
	a, b := toNumber(l), toNumber(r)
	if math.IsNaN(a) || math.IsNaN(b) {
		return false
	}
	switch op {
	case "<":
		return a < b
	case "<=":
		return a <= b
	case ">":
		return a > b
	default:
		return a >= b
	}
}

var fmtPlaceholder = regexp.MustCompile(`\{\{|\}\}|\{(\d+)\}`)

func formatString(f string, args []any) string {
	return fmtPlaceholder.ReplaceAllStringFunc(f, func(m string) string {
		switch m {
		case "{{":
			return "{"
		case "}}":
			return "}"
		}
		idx, _ := strconv.Atoi(m[1 : len(m)-1])
		if idx < len(args) {
			return ToString(args[idx])
		}
		return ""
	})
}

// hashFiles returns the SHA-256 over every file matching the glob patterns (relative to
// workDir), or "" when nothing matches — the same contract as GitHub's hashFiles().
func hashFiles(workDir string, patterns []string) string {
	if workDir == "" || len(patterns) == 0 {
		return ""
	}
	var res []*regexp.Regexp
	for _, p := range patterns {
		res = append(res, globToRegexp(p))
	}
	var files []string
	_ = filepath.WalkDir(workDir, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return nil
		}
		if d.IsDir() {
			if d.Name() == ".git" || d.Name() == "node_modules" {
				return filepath.SkipDir
			}
			return nil
		}
		rel, rerr := filepath.Rel(workDir, path)
		if rerr != nil {
			return nil
		}
		rel = filepath.ToSlash(rel)
		for _, re := range res {
			if re.MatchString(rel) {
				files = append(files, path)
				break
			}
		}
		return nil
	})
	if len(files) == 0 {
		return ""
	}
	sort.Strings(files)
	h := sha256.New()
	for _, f := range files {
		b, err := os.ReadFile(f) //nolint:gosec
		if err != nil {
			continue
		}
		sum := sha256.Sum256(b)
		h.Write(sum[:])
	}
	return hex.EncodeToString(h.Sum(nil))
}

func globToRegexp(glob string) *regexp.Regexp {
	glob = strings.TrimPrefix(filepath.ToSlash(glob), "./")
	var b strings.Builder
	b.WriteString("^")
	for i := 0; i < len(glob); i++ {
		switch c := glob[i]; c {
		case '*':
			if i+1 < len(glob) && glob[i+1] == '*' {
				i++
				if i+1 < len(glob) && glob[i+1] == '/' {
					i++
					b.WriteString("(?:.*/)?")
				} else {
					b.WriteString(".*")
				}
			} else {
				b.WriteString("[^/]*")
			}
		case '?':
			b.WriteString("[^/]")
		case '.', '+', '(', ')', '|', '^', '$', '{', '}', '[', ']', '\\':
			b.WriteByte('\\')
			b.WriteByte(c)
		default:
			b.WriteByte(c)
		}
	}
	b.WriteString("$")
	return regexp.MustCompile(b.String())
}
