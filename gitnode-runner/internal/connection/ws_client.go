package connection

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"go.uber.org/zap"

	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

var ErrNotConnected = errors.New("not connected")

// Handler processes inbound messages. Called in its own goroutine.
type Handler func(msg protocol.InboundMessage)

// Client manages a WebSocket connection to the GitNode server with
// automatic exponential-backoff reconnect.
type Client struct {
	serverURL string
	runnerID  string
	token     string
	onMessage Handler
	log       *zap.Logger

	wmu  sync.Mutex // serialises writes
	cmu  sync.Mutex // protects conn field
	conn *websocket.Conn
}

func New(serverURL, runnerID, token string, onMessage Handler, log *zap.Logger) *Client {
	return &Client{
		serverURL: serverURL,
		runnerID:  runnerID,
		token:     token,
		onMessage: onMessage,
		log:       log,
	}
}

// Run connects and reconnects until ctx is cancelled.
func (c *Client) Run(ctx context.Context) {
	backoff := time.Second
	const maxBackoff = 60 * time.Second

	for ctx.Err() == nil {
		start := time.Now()
		err := c.connect(ctx)
		if ctx.Err() != nil {
			return
		}
		// Reset backoff if the connection was stable for >30 s.
		if time.Since(start) > 30*time.Second {
			backoff = time.Second
		}
		if err != nil {
			c.log.Warn("disconnected, reconnecting", zap.Error(err), zap.Duration("in", backoff))
		}
		select {
		case <-time.After(backoff):
		case <-ctx.Done():
			return
		}
		if backoff*2 > maxBackoff {
			backoff = maxBackoff
		} else {
			backoff *= 2
		}
	}
}

// Send transmits a message on the active connection. Safe for concurrent use.
func (c *Client) Send(msg protocol.OutboundMessage) error {
	c.cmu.Lock()
	conn := c.conn
	c.cmu.Unlock()
	if conn == nil {
		return ErrNotConnected
	}
	return c.write(conn, msg)
}

func (c *Client) connect(ctx context.Context) error {
	wsURL := toWS(c.serverURL) + "/ws/runner/" + c.runnerID
	hdr := http.Header{"Authorization": {"Bearer " + c.token}}

	conn, resp, err := websocket.DefaultDialer.DialContext(ctx, wsURL, hdr)
	if resp != nil {
		_ = resp.Body.Close()
	}
	if err != nil {
		return fmt.Errorf("dial %s: %w", wsURL, err)
	}

	c.cmu.Lock()
	c.conn = conn
	c.cmu.Unlock()

	defer func() {
		_ = conn.Close()
		c.cmu.Lock()
		c.conn = nil
		c.cmu.Unlock()
	}()

	go func() {
		<-ctx.Done()
		_ = conn.Close()
	}()

	c.log.Info("connected", zap.String("url", wsURL))

	if err := c.write(conn, protocol.OutboundMessage{
		Type: protocol.TypeRegister,
		Data: mustJSON(protocol.RegisterPayload{RunnerID: c.runnerID, Token: c.token}),
	}); err != nil {
		return fmt.Errorf("send register: %w", err)
	}

	hbCtx, hbCancel := context.WithCancel(ctx)
	defer hbCancel()
	go c.heartbeat(hbCtx, conn)

	for {
		_, raw, err := conn.ReadMessage()
		if err != nil {
			return fmt.Errorf("read: %w", err)
		}
		var msg protocol.InboundMessage
		if err := json.Unmarshal(raw, &msg); err != nil {
			c.log.Warn("unparseable message", zap.Error(err))
			continue
		}
		go c.onMessage(msg)
	}
}

func (c *Client) heartbeat(ctx context.Context, conn *websocket.Conn) {
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if err := c.write(conn, protocol.OutboundMessage{Type: protocol.TypeHeartbeat}); err != nil {
				c.log.Warn("heartbeat error", zap.Error(err))
				return
			}
		}
	}
}

func (c *Client) write(conn *websocket.Conn, msg protocol.OutboundMessage) error {
	c.wmu.Lock()
	defer c.wmu.Unlock()
	return conn.WriteJSON(msg)
}

func toWS(u string) string {
	switch {
	case strings.HasPrefix(u, "https://"):
		return "wss://" + u[8:]
	case strings.HasPrefix(u, "http://"):
		return "ws://" + u[7:]
	default:
		return u
	}
}

func mustJSON(v any) json.RawMessage {
	b, _ := json.Marshal(v)
	return b
}
