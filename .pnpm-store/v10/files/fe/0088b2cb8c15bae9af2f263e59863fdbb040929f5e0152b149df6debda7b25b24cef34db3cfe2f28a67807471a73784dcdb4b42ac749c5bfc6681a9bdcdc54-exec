"use strict";
/**
 * @license
 * Copyright Google LLC All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.dev/license
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.toPosixPath = toPosixPath;
exports.isSubDirectory = isSubDirectory;
const node_path_1 = require("node:path");
const node_process_1 = require("node:process");
const WINDOWS_PATH_SEPERATOR_REGEXP = /\\/g;
/**
 * Converts a Windows-style file path to a POSIX-compliant path.
 *
 * This function replaces all backslashes (`\`) with forward slashes (`/`).
 * It is a no-op on POSIX systems (e.g., Linux, macOS), as the conversion
 * only runs on Windows (`win32`).
 *
 * @param path - The file path to convert.
 * @returns The POSIX-compliant file path.
 *
 * @example
 * ```ts
 * // On a Windows system:
 * toPosixPath('C:\\Users\\Test\\file.txt');
 * // => 'C:/Users/Test/file.txt'
 *
 * // On a POSIX system (Linux/macOS):
 * toPosixPath('/home/user/file.txt');
 * // => '/home/user/file.txt'
 * ```
 */
function toPosixPath(path) {
    return node_process_1.platform === 'win32' ? path.replace(WINDOWS_PATH_SEPERATOR_REGEXP, node_path_1.posix.sep) : path;
}
/**
 * Determines if a path is a subdirectory or file within a parent directory.
 *
 * @param parent - The parent directory path.
 * @param child - The child path to check.
 * @returns `true` if the child path is within the parent directory, `false` otherwise.
 */
function isSubDirectory(parent, child) {
    const normalizedParent = (0, node_path_1.normalize)(parent);
    const resolvedChild = (0, node_path_1.resolve)(parent, child);
    return resolvedChild.startsWith(normalizedParent);
}
//# sourceMappingURL=path.js.map