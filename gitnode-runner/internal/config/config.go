package config

import (
	"os"

	"gopkg.in/yaml.v3"
)

type Config struct {
	ServerURL      string   `yaml:"server_url"`
	Token          string   `yaml:"token"`
	Name           string   `yaml:"name"`
	Labels         []string `yaml:"labels"`
	Executor       string   `yaml:"executor"`
	WorkDir        string   `yaml:"work_dir"`
	ConcurrentJobs int      `yaml:"concurrent_jobs"`

	// Populated after registration — persisted to config file.
	RunnerID    string `yaml:"runner_id,omitempty"`
	RunnerToken string `yaml:"runner_token,omitempty"`
}

func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var cfg Config
	return &cfg, yaml.Unmarshal(data, &cfg)
}

func (c *Config) Save(path string) error {
	data, err := yaml.Marshal(c)
	if err != nil {
		return err
	}
	return os.WriteFile(path, data, 0600)
}

func (c *Config) Registered() bool {
	return c.RunnerID != "" && c.RunnerToken != ""
}
