package com.example.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StartupAnnouncementServiceTest {

    @TempDir
    Path tmp;

    private StartupAnnouncementService service(String filename) {
        return new StartupAnnouncementService(tmp.resolve(filename).toString());
    }

    @Test
    void consumeReturnsEmptyWhenNoFileExists() {
        assertThat(service("missing").consume()).isEmpty();
    }

    @Test
    void setWritesFileToDisk() {
        var svc = service("ann");
        svc.set("hello");
        assertThat(Files.exists(tmp.resolve("ann"))).isTrue();
    }

    @Test
    void loadReadsFileAndDeletesIt() throws Exception {
        Path file = tmp.resolve("ann");
        Files.writeString(file, "version 1.2.3");

        var svc = new StartupAnnouncementService(file.toString());
        svc.load();

        assertThat(Files.exists(file)).isFalse();
        assertThat(svc.consume()).contains("version 1.2.3");
    }

    @Test
    void consumeDeliversMessageExactlyOnce() throws Exception {
        Path file = tmp.resolve("ann");
        Files.writeString(file, "v2");

        var svc = new StartupAnnouncementService(file.toString());
        svc.load();

        assertThat(svc.consume()).isPresent();
        assertThat(svc.consume()).isEmpty();
    }

    @Test
    void setThenLoadRoundTrips() {
        var writer = service("ann");
        writer.set("Self-update completed. Version: 0.9.0");

        var reader = new StartupAnnouncementService(tmp.resolve("ann").toString());
        reader.load();
        assertThat(reader.consume()).hasValueSatisfying(v -> assertThat(v).contains("0.9.0"));
    }

    @Test
    void loadIsNoOpWhenFileAbsent() {
        var svc = service("absent");
        svc.load();
        assertThat(svc.consume()).isEmpty();
    }

    @Test
    void loadIgnoresBlankFile() throws Exception {
        Path file = tmp.resolve("ann");
        Files.writeString(file, "   \n  ");

        var svc = new StartupAnnouncementService(file.toString());
        svc.load();
        assertThat(svc.consume()).isEmpty();
    }
}
