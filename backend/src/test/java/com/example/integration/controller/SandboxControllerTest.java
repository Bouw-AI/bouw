package com.example.integration.controller;

import com.example.agent.model.SandboxInfo;
import com.example.integration.github.GitHubAppService;
import com.example.integration.service.BugReportCatalogService;
import com.example.integration.service.DockerSandboxManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SandboxControllerTest {

    @Mock
    private DockerSandboxManager sandboxManager;

    @Mock
    private GitHubAppService github;

    @Mock
    private BugReportCatalogService bugReportCatalogService;

    @InjectMocks
    private SandboxController controller;

    @Test
    void createGitHubSandboxClonesSelectedBranch() throws Exception {
        SandboxInfo sandbox = new SandboxInfo(
                "sbx-1", "hugin-sbx-sbx-1", "ubuntu:24.04", SandboxInfo.RUNNING, Instant.now(), "/tmp/sbx-1/workspace");
        when(github.installationToken()).thenReturn(Optional.of("token-123"));
        when(github.cloneUrl("octocat/hello-world")).thenReturn("https://github.com/octocat/hello-world.git");
        when(sandboxManager.createGitHubRepoSandbox(
                eq(null), eq("https://github.com/octocat/hello-world.git"), eq("hello-world"), eq("develop"), eq("token-123"), eq(null)))
                .thenReturn(sandbox);

        var result = controller.createGitHubSandbox(
                new SandboxController.CreateGitHubSandboxRequest(null, "octocat/hello-world", "develop", null),
                null);

        assertThat(result.getStatusCodeValue()).isEqualTo(201);
        assertThat(result.getBody()).isEqualTo(sandbox);

        verify(github).installationToken();
        verify(github).cloneUrl("octocat/hello-world");
    }
}
