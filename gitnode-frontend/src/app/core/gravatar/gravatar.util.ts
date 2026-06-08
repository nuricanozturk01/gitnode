///
/// Copyright 2026 the original author or authors.
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///      https://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import md5 from 'md5';

export function gravatarUrl(
  avatarUrl: string | null | undefined,
  email?: string | null,
  username?: string | null,
  size = 80,
): string | null {
  if (avatarUrl?.trim()) return avatarUrl;
  const input = email?.includes('@') ? email.toLowerCase() : username?.toLowerCase();
  if (!input?.trim()) return null;
  const hash = md5(input.trim());
  return `https://www.gravatar.com/avatar/${hash}?s=${size}&d=identicon`;
}
