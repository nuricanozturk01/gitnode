import { HttpErrorResponse } from '@angular/common/http';

const BACKEND_ERROR_MESSAGES: Record<string, string> = {
  wrongPassword: 'Invalid username or password.',
  userNotExist: 'Invalid username or password.',
  userDisabled: 'This account has been disabled.',
  platformAdminRequired: 'Platform administrator access required.',
  validationError: 'Invalid request. Check username and password format.',
};

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) {
    return (error as Error).message ?? fallback;
  }

  const code = parseBackendErrorCode(error.error);
  if (code) {
    return BACKEND_ERROR_MESSAGES[code] ?? code;
  }

  if (error.status === 0) {
    return 'Cannot reach the API. Is the backend running?';
  }

  return error.message || error.statusText || fallback;
}

function parseBackendErrorCode(body: unknown): string | null {
  if (typeof body === 'string' && body.length > 0) {
    return body;
  }

  if (body && typeof body === 'object' && 'message' in body && typeof body.message === 'string') {
    return body.message;
  }

  return null;
}
