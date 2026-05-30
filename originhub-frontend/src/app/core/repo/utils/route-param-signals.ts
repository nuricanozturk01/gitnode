///
/// Copyright 2026 the original author or authors.
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
///

import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

/**
 * Reacts to router param changes. Do not use `computed(() => route.snapshot…)` — it memoizes once
 * and never updates when the URL changes.
 */
export function paramMapSignal(route: ActivatedRoute) {
  return toSignal(route.paramMap, { initialValue: route.snapshot.paramMap });
}

export function parentParamMapSignal(route: ActivatedRoute) {
  const parent = route.parent;
  if (!parent) {
    return toSignal(route.paramMap, { initialValue: route.snapshot.paramMap });
  }
  return toSignal(parent.paramMap, { initialValue: parent.snapshot.paramMap });
}

export function grandParentParamMapSignal(route: ActivatedRoute) {
  const gp = route.parent?.parent;
  if (!gp) {
    return parentParamMapSignal(route);
  }
  return toSignal(gp.paramMap, { initialValue: gp.snapshot.paramMap });
}

export function queryParamMapSignal(route: ActivatedRoute) {
  return toSignal(route.queryParamMap, { initialValue: route.snapshot.queryParamMap });
}
