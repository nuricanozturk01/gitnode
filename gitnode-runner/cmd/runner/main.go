package main

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/signal"
	"runtime"
	"strings"
	"syscall"

	"github.com/spf13/cobra"
	"go.uber.org/zap"

	"github.com/nuricanozturk/gitnode-runner/internal/actions"
	"github.com/nuricanozturk/gitnode-runner/internal/config"
	"github.com/nuricanozturk/gitnode-runner/internal/connection"
	"github.com/nuricanozturk/gitnode-runner/internal/executor"
	"github.com/nuricanozturk/gitnode-runner/internal/registration"
	"github.com/nuricanozturk/gitnode-runner/internal/workspace"
	"github.com/nuricanozturk/gitnode-runner/pkg/protocol"
)

var version = "dev"

func main() {
	if err := rootCmd().Execute(); err != nil {
		os.Exit(1)
	}
}

func rootCmd() *cobra.Command {
	root := &cobra.Command{
		Use:     "gitnode-runner",
		Short:   "GitNode CI/CD runner agent",
		Version: version,
	}
	root.AddCommand(startCmd())
	return root
}

func startCmd() *cobra.Command {
	var (
		configFile     string
		serverURL      string
		token          string
		name           string
		labels         string
		executorType   string
		workDir        string
		concurrentJobs int
	)

	cmd := &cobra.Command{
		Use:   "start",
		Short: "Register (if needed) and start the runner",
		RunE: func(_ *cobra.Command, _ []string) error {
			log, err := zap.NewProduction()
			if err != nil {
				return err
			}
			defer log.Sync() //nolint:errcheck

			cfg, err := buildConfig(configFile, serverURL, token, name, labels, executorType, workDir, concurrentJobs)
			if err != nil {
				return fmt.Errorf("config: %w", err)
			}

			if !cfg.Registered() {
				log.Info("registering runner", zap.String("name", cfg.Name), zap.String("server", cfg.ServerURL))
				if err := registration.Register(cfg); err != nil {
					return fmt.Errorf("registration: %w", err)
				}
				log.Info("registered", zap.String("runnerID", cfg.RunnerID))
				if configFile != "" {
					if err := cfg.Save(configFile); err != nil {
						log.Warn("failed to persist config", zap.Error(err))
					}
				}
			}

			ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
			defer stop()

			log.Info("starting",
				zap.String("runnerID", cfg.RunnerID),
				zap.String("server", cfg.ServerURL),
				zap.Strings("labels", cfg.Labels),
				zap.String("executor", cfg.Executor),
			)

			// Build executor components.
			wsMgr := workspace.New(cfg.WorkDir)
			actionReg := actions.NewRegistry(cfg.ServerURL, cfg.RunnerToken)

			// wsClient is created before the message handler so we can close it in handler.
			var wsClient *connection.Client

			jobExec := executor.New(
				wsMgr,
				actionReg,
				func(msg protocol.OutboundMessage) {
					if wsClient != nil {
						if err := wsClient.Send(msg); err != nil {
							log.Warn("failed to send WS message", zap.Error(err))
						}
					}
				},
				cfg.Executor,
				log,
			)

			wsClient = connection.New(
				cfg.ServerURL,
				cfg.RunnerID,
				cfg.RunnerToken,
				buildMessageHandler(jobExec, log, ctx),
				log,
			)
			wsClient.Run(ctx)

			log.Info("runner stopped")
			return nil
		},
	}

	cmd.Flags().StringVar(&configFile, "config", "", "path to YAML config file")
	cmd.Flags().StringVar(&serverURL, "server-url", "", "GitNode server URL (e.g. http://localhost:8080)")
	cmd.Flags().StringVar(&token, "token", "", "one-time registration token (ghrt_...)")
	cmd.Flags().StringVar(&name, "name", defaultName(), "runner name")
	cmd.Flags().StringVar(&labels, "labels", "self-hosted", "comma-separated runner labels")
	cmd.Flags().StringVar(&executorType, "executor", "shell", "executor type: shell or docker")
	cmd.Flags().StringVar(&workDir, "work-dir", defaultWorkDir(), "base directory for job workspaces")
	cmd.Flags().IntVar(&concurrentJobs, "concurrent-jobs", 1, "max parallel jobs")

	return cmd
}

// buildMessageHandler returns a connection.Handler that dispatches inbound WS messages.
func buildMessageHandler(
	jobExec *executor.JobExecutor,
	log *zap.Logger,
	ctx context.Context,
) connection.Handler {
	return func(msg protocol.InboundMessage) {
		switch msg.Type {
		case protocol.TypePing:
			log.Debug("ping")

		case protocol.TypeJobAssigned:
			var payload protocol.JobAssignedPayload
			if err := json.Unmarshal(msg.Data, &payload); err != nil {
				log.Error("failed to unmarshal JOB_ASSIGNED", zap.Error(err))
				return
			}
			log.Info("job assigned", zap.String("jobId", payload.Job.ID))
			jobExec.Execute(ctx, payload.Job)

		case protocol.TypeJobCancelled:
			var payload protocol.JobCancelledPayload
			if err := json.Unmarshal(msg.Data, &payload); err != nil {
				log.Warn("failed to unmarshal JOB_CANCELLED", zap.Error(err))
				return
			}
			log.Info("job cancelled", zap.String("jobId", payload.JobID))

		default:
			log.Warn("unknown message type", zap.String("type", string(msg.Type)))
		}
	}
}

func buildConfig(
	file, serverURL, token, name, labels, executorType, workDir string,
	concurrentJobs int,
) (*config.Config, error) {
	cfg := &config.Config{}

	if file != "" {
		loaded, err := config.Load(file)
		if err != nil && !os.IsNotExist(err) {
			return nil, fmt.Errorf("load config %s: %w", file, err)
		}
		if loaded != nil {
			cfg = loaded
		}
	}

	if serverURL != "" {
		cfg.ServerURL = serverURL
	}
	if token != "" {
		cfg.Token = token
	}
	if name != "" {
		cfg.Name = name
	}
	if labels != "" {
		cfg.Labels = strings.Split(labels, ",")
	}
	if executorType != "" {
		cfg.Executor = executorType
	}
	if workDir != "" {
		cfg.WorkDir = workDir
	}
	if concurrentJobs > 0 {
		cfg.ConcurrentJobs = concurrentJobs
	}

	if cfg.Name == "" {
		cfg.Name = defaultName()
	}
	if len(cfg.Labels) == 0 {
		cfg.Labels = []string{"self-hosted"}
	}
	if cfg.Executor == "" {
		cfg.Executor = "shell"
	}
	if cfg.ConcurrentJobs == 0 {
		cfg.ConcurrentJobs = 1
	}

	if cfg.ServerURL == "" {
		return nil, fmt.Errorf("--server-url is required")
	}
	if !cfg.Registered() && cfg.Token == "" {
		return nil, fmt.Errorf("--token is required for initial registration")
	}

	return cfg, nil
}

func defaultName() string {
	h, _ := os.Hostname()
	return h + "-" + runtime.GOOS
}

func defaultWorkDir() string {
	home, _ := os.UserHomeDir()
	return home + "/.gitnode-runner/work"
}
