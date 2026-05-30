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

import { Component, input, computed } from '@angular/core';
import type { LanguageStats } from '../../../domain/language/models/language-stats.model';

const LANGUAGE_COLORS: Record<string, string> = {
  java: '#b07219',
  typescript: '#3178c6',
  javascript: '#f1e05a',
  python: '#3572A5',
  go: '#00ADD8',
  rust: '#dea584',
  kotlin: '#A97BFF',
  markdown: '#083fa1',
  yaml: '#cb171e',
  json: '#292929',
  xml: '#0060ac',
  html: '#e34c26',
  css: '#563d7c',
  bash: '#89e051',
  sql: '#e38c00',
  dockerfile: '#384d54',
  toml: '#9c4221',
  properties: '#aaaaaa',
  c: '#555555',
  cpp: '#f34b7d',
  csharp: '#239120',
  ruby: '#701516',
  php: '#4F5D95',
  swift: '#F05138',
  dart: '#00B4AB',
  scala: '#c22d40',
  clojure: '#db5855',
  groovy: '#4298b8',
  lua: '#000080',
  r: '#198CE7',
  haskell: '#5e5086',
  elixir: '#6e4a7e',
  erlang: '#B83998',
  julia: '#a270ba',
  fsharp: '#b845fc',
  ocaml: '#3be133',
  nim: '#ffc200',
  crystal: '#000100',
  zig: '#ec915c',
  hcl: '#844FBA',
  powershell: '#012456',
  nix: '#7e7eff',
  vue: '#41b883',
  svelte: '#ff3e00',
  astro: '#ff5a03',
  graphql: '#e10098',
  protobuf: '#4a90e2',
  thrift: '#D12127',
  less: '#1d365d',
  stylus: '#ff6347',
  coffeescript: '#244776',
  elm: '#60B5CC',
  purescript: '#1D222D',
  reason: '#ff5847',
  v: '#5d87bf',
  vb: '#945db7',
  pascal: '#E3F171',
  asm: '#6E4C13',
  fortran: '#4d41b1',
  perl: '#0298c3',
  tcl: '#e4cc98',
  objectivec: '#438eff',
  batch: '#C1F12E',
  latex: '#3D6117',
  bibtex: '#778899',
  jupyter: '#DA5B0B',
  csv: '#89e051',
  ini: '#d1dbe0',
  nginx: '#009639',
  apache: '#d22128',
  rst: '#141414',
  asciidoc: '#74cfa1',
  webassembly: '#04133b',
  cmake: '#DA3434',
  makefile: '#427819',
};

const DEFAULT_COLOR = '#8b949e';

@Component({
  selector: 'app-language-bar',
  standalone: true,
  templateUrl: './language-bar.component.html',
})
export class LanguageBarComponent {
  readonly languages = input<LanguageStats[]>([]);

  readonly enriched = computed(() =>
    this.languages().map((l) => ({
      ...l,
      color: LANGUAGE_COLORS[l.language.toLowerCase()] ?? DEFAULT_COLOR,
      displayName: l.language.charAt(0).toUpperCase() + l.language.slice(1),
    })),
  );
}
