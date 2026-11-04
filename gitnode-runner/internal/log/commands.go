package log

import (
	"fmt"
	"strings"
)

// command is a parsed workflow command: ::name key=value,key2=value2::message
type command struct {
	name    string
	props   map[string]string
	message string
}

// parseCommand recognises GitHub's workflow command syntax. Anything that does not look like a
// command (including "::" inside a line) is not one.
func parseCommand(line string) (command, bool) {
	if !strings.HasPrefix(line, "::") {
		return command{}, false
	}
	rest := line[2:]
	end := strings.Index(rest, "::")
	if end < 0 {
		return command{}, false
	}
	head, message := rest[:end], rest[end+2:]

	name, propText, _ := strings.Cut(head, " ")
	if name == "" || strings.ContainsAny(name, " \t") {
		return command{}, false
	}

	props := map[string]string{}
	for _, pair := range strings.Split(propText, ",") {
		pair = strings.TrimSpace(pair)
		if pair == "" {
			continue
		}
		key, value, found := strings.Cut(pair, "=")
		if !found {
			continue
		}
		props[key] = unescapeProperty(value)
	}
	return command{name: strings.ToLower(name), props: props, message: unescapeData(message)}, true
}

// unescapeData reverses the escaping GitHub applies to a command's message.
func unescapeData(s string) string {
	return strings.NewReplacer("%0D", "\r", "%0d", "\r", "%0A", "\n", "%0a", "\n", "%25", "%").Replace(s)
}

// unescapeProperty reverses the escaping of a property value (message escapes plus : and ,).
func unescapeProperty(s string) string {
	return strings.NewReplacer(
		"%0D", "\r", "%0d", "\r", "%0A", "\n", "%0a", "\n",
		"%3A", ":", "%3a", ":", "%2C", ",", "%2c", ",", "%25", "%",
	).Replace(s)
}

// annotationText is how ::error/::warning/::notice show up in the log: the message, prefixed
// with file/line when the command carries them (GitHub's annotation location).
func annotationText(c command) string {
	file := c.props["file"]
	if file == "" {
		if title := c.props["title"]; title != "" {
			return title + ": " + c.message
		}
		return c.message
	}
	loc := file
	if line := c.props["line"]; line != "" {
		loc += fmt.Sprintf(":%s", line)
		if col := c.props["col"]; col != "" {
			loc += fmt.Sprintf(":%s", col)
		}
	}
	msg := c.message
	if title := c.props["title"]; title != "" {
		msg = title + ": " + msg
	}
	return loc + ": " + msg
}
