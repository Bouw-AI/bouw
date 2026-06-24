package com.example.agent.sandbox;

/**
 * A single entry returned by {@link SandboxRuntime#listFiles(String, String)}.
 *
 * @param name      the entry's file name (last path segment)
 * @param path      the entry's path relative to the listed directory
 * @param directory whether the entry is a directory
 * @param size      the file size in bytes (0 for directories)
 */
public record FileEntry(String name, String path, boolean directory, long size) {

    public static FileEntry file(String name, String path, long size) {
        return new FileEntry(name, path, false, size);
    }

    public static FileEntry directory(String name, String path) {
        return new FileEntry(name, path, true, 0L);
    }
}
