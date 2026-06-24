package com.example.agent.sandbox;

/**
 * The result of reading a file from inside a sandbox via {@link SandboxRuntime#readFile(String, String)}.
 *
 * @param path      the path that was read (relative to the workspace, as requested)
 * @param content   the file's UTF-8 content (empty when the file is missing)
 * @param exists    whether the file existed and was readable
 * @param truncated whether {@code content} was truncated to a size limit
 */
public record FileResult(String path, String content, boolean exists, boolean truncated) {

    public static FileResult missing(String path) {
        return new FileResult(path, "", false, false);
    }

    public static FileResult of(String path, String content, boolean truncated) {
        return new FileResult(path, content, true, truncated);
    }
}
