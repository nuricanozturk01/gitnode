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

import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

export interface BreadcrumbItem {
  name: string;
  path: string;
  isLast: boolean;
}

@Component({
  selector: 'app-repo-breadcrumb',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="breadcrumbs min-h-6 text-sm">
      <ul>
        <li>
          <a [routerLink]="treeRootLink()" class="text-primary hover:underline">
            {{ repoName() }}
          </a>
        </li>
        @for (crumb of items(); track crumb.path || crumb.name) {
          <li>
            @if (crumb.isLast) {
              <span class="text-base-content font-medium">{{ crumb.name }}</span>
            } @else {
              <a [routerLink]="crumbLink(crumb.path)" class="text-primary hover:underline">
                {{ crumb.name }}
              </a>
            }
          </li>
        }
      </ul>
    </div>
  `,
})
export class RepoBreadcrumbComponent {
  readonly owner = input.required<string>();
  readonly repoName = input.required<string>();
  readonly branch = input<string>('main');
  readonly path = input<string>('');
  readonly items = input.required<BreadcrumbItem[]>();

  protected treeRootLink(): string[] {
    return ['/', this.owner(), this.repoName(), 'tree', this.branch()];
  }

  protected crumbLink(path: string): string[] {
    const segments = path ? path.split('/').filter(Boolean) : [];
    return ['/', this.owner(), this.repoName(), 'tree', this.branch(), ...segments];
  }
}
