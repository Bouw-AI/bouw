package com.example.agent.sandbox;

/**
 * The repository a project-chat sandbox should check out inside its container.
 *
 * @param cloneUrl     the git clone URL (https)
 * @param repoFullName the {@code owner/repo} identity, used for naming and agent context
 * @param branch       the branch to check out (may be {@code null}/blank for the default branch)
 * @param accessToken  a short-lived token used to authenticate the in-container clone; never persisted
 */
public record RepositoryConfig(
        String cloneUrl,
        String repoFullName,
        String branch,
        String accessToken) {
}
