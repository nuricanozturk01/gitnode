import type { LoginInfo } from '@helpers/types';

export interface ScenarioUser extends LoginInfo {
  email: string;
  password: string;
  authorization: string;
}

export interface ScenarioRepo {
  name: string;
  id: string;
  isPrivate: boolean;
  defaultBranch: string;
}
