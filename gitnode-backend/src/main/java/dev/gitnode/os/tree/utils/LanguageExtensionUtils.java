/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.gitnode.os.tree.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@UtilityClass
@NullMarked
public class LanguageExtensionUtils {

  private static final Map<String, String> FILENAME_LANGUAGE_MAP;
  private static final Map<String, String> EXTENSION_LANGUAGE_MAP;
  private static final String DEFAULT_LANGUAGE = "plaintext";

  static {
    final HashMap<String, String> fn = new HashMap<>();
    fn.put("Dockerfile", "dockerfile");
    fn.put("Makefile", "makefile");
    fn.put("makefile", "makefile");
    fn.put("GNUmakefile", "makefile");
    fn.put("CMakeLists.txt", "cmake");
    fn.put("Gemfile", "ruby");
    fn.put("Rakefile", "ruby");
    fn.put("Guardfile", "ruby");
    fn.put("Podfile", "ruby");
    fn.put("Vagrantfile", "ruby");
    fn.put("Brewfile", "ruby");
    fn.put("Fastfile", "ruby");
    fn.put("Appfile", "ruby");
    fn.put("Matchfile", "ruby");
    fn.put("Dangerfile", "ruby");
    fn.put("Procfile", "plaintext");
    fn.put("Jenkinsfile", "groovy");
    fn.put("build.gradle", "groovy");
    fn.put("settings.gradle", "groovy");
    fn.put("pom.xml", "xml");
    fn.put("package.json", "json");
    fn.put("tsconfig.json", "json");
    fn.put(".gitignore", "plaintext");
    fn.put(".gitattributes", "plaintext");
    fn.put(".editorconfig", "ini");
    fn.put(".env", "plaintext");
    fn.put("nginx.conf", "nginx");
    FILENAME_LANGUAGE_MAP = Collections.unmodifiableMap(fn);
  }

  static {
    final HashMap<String, String> ext = new HashMap<>();
    // JVM
    ext.put("java", "java");
    ext.put("kt", "kotlin");
    ext.put("kts", "kotlin");
    ext.put("scala", "scala");
    ext.put("sc", "scala");
    ext.put("groovy", "groovy");
    ext.put("gvy", "groovy");
    ext.put("gy", "groovy");
    ext.put("gsh", "groovy");
    ext.put("gradle", "groovy");
    ext.put("clj", "clojure");
    ext.put("cljs", "clojure");
    ext.put("cljc", "clojure");
    ext.put("edn", "clojure");
    // Web / JS ecosystem
    ext.put("ts", "typescript");
    ext.put("tsx", "typescript");
    ext.put("cts", "typescript");
    ext.put("mts", "typescript");
    ext.put("js", "javascript");
    ext.put("jsx", "javascript");
    ext.put("cjs", "javascript");
    ext.put("mjs", "javascript");
    ext.put("coffee", "coffeescript");
    ext.put("litcoffee", "coffeescript");
    ext.put("vue", "vue");
    ext.put("svelte", "svelte");
    ext.put("astro", "astro");
    ext.put("elm", "elm");
    ext.put("purs", "purescript");
    // C family
    ext.put("c", "c");
    ext.put("h", "c");
    ext.put("cpp", "cpp");
    ext.put("cc", "cpp");
    ext.put("cxx", "cpp");
    ext.put("c++", "cpp");
    ext.put("hpp", "cpp");
    ext.put("hh", "cpp");
    ext.put("hxx", "cpp");
    ext.put("cs", "csharp");
    ext.put("m", "objectivec");
    ext.put("mm", "objectivec");
    // Scripting
    ext.put("py", "python");
    ext.put("rb", "ruby");
    ext.put("erb", "ruby");
    ext.put("rake", "ruby");
    ext.put("php", "php");
    ext.put("php3", "php");
    ext.put("php4", "php");
    ext.put("php5", "php");
    ext.put("phtml", "php");
    ext.put("lua", "lua");
    ext.put("pl", "perl");
    ext.put("pm", "perl");
    ext.put("pod", "perl");
    ext.put("tcl", "tcl");
    // Shell
    ext.put("sh", "bash");
    ext.put("bash", "bash");
    ext.put("ps1", "powershell");
    ext.put("psm1", "powershell");
    ext.put("psd1", "powershell");
    ext.put("bat", "batch");
    ext.put("cmd", "batch");
    // Systems / low-level
    ext.put("go", "go");
    ext.put("rs", "rust");
    ext.put("zig", "zig");
    ext.put("nim", "nim");
    ext.put("cr", "crystal");
    ext.put("v", "v");
    ext.put("asm", "asm");
    ext.put("s", "asm");
    // Functional
    ext.put("hs", "haskell");
    ext.put("lhs", "haskell");
    ext.put("ex", "elixir");
    ext.put("exs", "elixir");
    ext.put("erl", "erlang");
    ext.put("hrl", "erlang");
    ext.put("jl", "julia");
    ext.put("fs", "fsharp");
    ext.put("fsi", "fsharp");
    ext.put("fsx", "fsharp");
    ext.put("ml", "ocaml");
    ext.put("mli", "ocaml");
    ext.put("re", "reason");
    ext.put("rei", "reason");
    // Mobile / cross-platform
    ext.put("swift", "swift");
    ext.put("dart", "dart");
    // Other languages
    ext.put("r", "r");
    ext.put("rmd", "r");
    ext.put("r2", "r");
    ext.put("f", "fortran");
    ext.put("f90", "fortran");
    ext.put("f95", "fortran");
    ext.put("f03", "fortran");
    ext.put("vb", "vb");
    ext.put("pas", "pascal");
    ext.put("pp", "pascal");
    ext.put("ps", "postscript");
    ext.put("nix", "nix");
    // Data / config / infra
    ext.put("yml", "yaml");
    ext.put("yaml", "yaml");
    ext.put("json", "json");
    ext.put("jsonc", "json");
    ext.put("jsonl", "json");
    ext.put("ndjson", "json");
    ext.put("xml", "xml");
    ext.put("toml", "toml");
    ext.put("properties", "properties");
    ext.put("ini", "ini");
    ext.put("cfg", "ini");
    ext.put("conf", "ini");
    ext.put("env", "plaintext");
    ext.put("txt", "plaintext");
    ext.put("log", "plaintext");
    ext.put("csv", "csv");
    ext.put("tsv", "csv");
    ext.put("tf", "hcl");
    ext.put("tfvars", "hcl");
    ext.put("nginx", "nginx");
    ext.put("htaccess", "apache");
    // Web / markup
    ext.put("html", "html");
    ext.put("css", "css");
    ext.put("scss", "css");
    ext.put("sass", "css");
    ext.put("less", "less");
    ext.put("styl", "stylus");
    ext.put("graphql", "graphql");
    ext.put("gql", "graphql");
    // Documentation / markup
    ext.put("md", "markdown");
    ext.put("mdx", "markdown");
    ext.put("rst", "rst");
    ext.put("adoc", "asciidoc");
    ext.put("tex", "latex");
    ext.put("sty", "latex");
    ext.put("cls", "latex");
    ext.put("bib", "bibtex");
    // Schema / IDL
    ext.put("proto", "protobuf");
    ext.put("thrift", "thrift");
    ext.put("sql", "sql");
    // Binary / special
    ext.put("wasm", "webassembly");
    ext.put("wat", "webassembly");
    ext.put("ipynb", "jupyter");
    EXTENSION_LANGUAGE_MAP = Collections.unmodifiableMap(ext);
  }

  public static String detectLanguage(final @Nullable String fileName) {

    if (fileName == null) {
      return DEFAULT_LANGUAGE;
    }

    final String filenameResult = FILENAME_LANGUAGE_MAP.get(fileName);
    if (filenameResult != null) {
      return filenameResult;
    }

    final var lastDot = fileName.lastIndexOf('.');

    if (lastDot < 0 || lastDot == fileName.length() - 1) {
      return DEFAULT_LANGUAGE;
    }

    final var extension = fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);

    return EXTENSION_LANGUAGE_MAP.getOrDefault(extension, DEFAULT_LANGUAGE);
  }
}
