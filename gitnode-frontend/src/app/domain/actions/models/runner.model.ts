export interface RunnerInfo {
  id: string;
  name: string;
  labels: string[];
  status: string;
  executorType: string;
  os: string | null;
  arch: string | null;
  version: string | null;
  lastHeartbeat: string | null;
  createdAt: string;
}

export interface RegistrationToken {
  token: string;
  expiresAt: string;
}
