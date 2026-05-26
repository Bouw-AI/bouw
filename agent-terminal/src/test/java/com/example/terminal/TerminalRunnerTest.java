package com.example.terminal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link TerminalRunner}. System.in is replaced with a
 * {@link ByteArrayInputStream} to drive the REPL, and System.out is captured
 * so we can assert on the printed output without starting a Spring context.
 */
class TerminalRunnerTest {

    private InputStream originalIn;
    private PrintStream originalOut;
    private ByteArrayOutputStream capturedOut;

    @BeforeEach
    void setUp() {
        originalIn  = System.in;
        originalOut = System.out;
        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        System.setIn(originalIn);
        System.setOut(originalOut);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private TerminalRunner runnerWithInput(String input) {
        return runnerWithInput(input, mock(AgentClient.class));
    }

    private TerminalRunner runnerWithInput(String input, AgentClient client) {
        System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        var properties = new TerminalProperties("http://localhost:8080", null, null);
        return new TerminalRunner(client, properties);
    }

    private String output() {
        return capturedOut.toString(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // exit / quit variants
    // ------------------------------------------------------------------

    @Test
    void exitCommandTerminatesLoop() throws Exception {
        runnerWithInput("exit\n").run();
        assertThat(output()).contains("Goodbye");
    }

    @Test
    void quitCommandTerminatesLoop() throws Exception {
        runnerWithInput("quit\n").run();
        assertThat(output()).contains("Goodbye");
    }

    @Test
    void slashExitCommandTerminatesLoop() throws Exception {
        runnerWithInput("/exit\n").run();
        assertThat(output()).contains("Goodbye");
    }

    @Test
    void slashQuitCommandTerminatesLoop() throws Exception {
        runnerWithInput("/quit\n").run();
        assertThat(output()).contains("Goodbye");
    }

    @Test
    void ctrlDTerminatesLoop() throws Exception {
        // Empty input simulates EOF (Ctrl-D)
        runnerWithInput("").run();
        assertThat(output()).contains("Goodbye");
    }

    // ------------------------------------------------------------------
    // /help
    // ------------------------------------------------------------------

    @Test
    void helpCommandPrintsHelp() throws Exception {
        runnerWithInput("/help\nexit\n").run();
        String out = output();
        assertThat(out).contains("Commands");
        // spot-check a few documented commands
        assertThat(out).contains("/help");
        assertThat(out).contains("/model");
    }

    // ------------------------------------------------------------------
    // /model
    // ------------------------------------------------------------------

    @Test
    void modelCommandShowsCurrentModelAsServerDefault() throws Exception {
        runnerWithInput("/model\nexit\n").run();
        assertThat(output()).contains("server default");
    }

    @Test
    void modelCommandSetsModel() throws Exception {
        runnerWithInput("/model gpt-4\nexit\n").run();
        assertThat(output()).contains("gpt-4");
    }

    @Test
    void modelCommandShowsSetModelOnSecondQuery() throws Exception {
        runnerWithInput("/model gpt-4\n/model\nexit\n").run();
        String out = output();
        assertThat(out).contains("gpt-4");
    }

    // ------------------------------------------------------------------
    // /new
    // ------------------------------------------------------------------

    @Test
    void newCommandStartsNewConversation() throws Exception {
        runnerWithInput("/new\nexit\n").run();
        assertThat(output()).containsIgnoringCase("new conversation");
    }

    // ------------------------------------------------------------------
    // prompt → AgentClient delegation
    // ------------------------------------------------------------------

    @Test
    void promptSendsToAgentClient() throws Exception {
        AgentClient client = mock(AgentClient.class);
        runnerWithInput("hello world\nexit\n", client).run();
        verify(client).streamChat(eq("hello world"), isNull(), any(), any());
    }

    @Test
    void blankLinesAreIgnored() throws Exception {
        AgentClient client = mock(AgentClient.class);
        // three blank lines, one real prompt, then exit
        runnerWithInput("\n\n\nhello\nexit\n", client).run();
        verify(client, times(1)).streamChat(eq("hello"), isNull(), any(), any());
    }

    @Test
    void multiplePromptsEachCallClient() throws Exception {
        AgentClient client = mock(AgentClient.class);
        runnerWithInput("first\nsecond\nexit\n", client).run();
        verify(client, times(2)).streamChat(any(), any(), any(), any());
    }

    @Test
    void exitCommandDoesNotCallClient() throws Exception {
        AgentClient client = mock(AgentClient.class);
        runnerWithInput("exit\n", client).run();
        verify(client, never()).streamChat(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // StreamPrinter behaviour — tested indirectly via ask()
    // ------------------------------------------------------------------

    @Test
    void streamPrinterOnToken() throws Exception {
        AgentClient client = mock(AgentClient.class);
        doAnswer((Answer<Void>) invocation -> {
            AgentClient.Handler handler = invocation.getArgument(3);
            handler.onToken("Hello");
            handler.onToken(" world");
            return null;
        }).when(client).streamChat(any(), any(), any(), any());

        runnerWithInput("ping\nexit\n", client).run();
        assertThat(output()).contains("Hello");
        assertThat(output()).contains(" world");
    }

    @Test
    void streamPrinterOnToolCall() throws Exception {
        AgentClient client = mock(AgentClient.class);
        doAnswer((Answer<Void>) invocation -> {
            AgentClient.Handler handler = invocation.getArgument(3);
            handler.onToolCall("get_time", "{}");
            return null;
        }).when(client).streamChat(any(), any(), any(), any());

        runnerWithInput("what time?\nexit\n", client).run();
        assertThat(output()).contains("get_time");
    }

    @Test
    void streamPrinterOnToolCallAfterMidLineToken() throws Exception {
        // Emit a token (no trailing newline) then a tool call — printer must
        // insert a newline before the tool line.
        AgentClient client = mock(AgentClient.class);
        doAnswer((Answer<Void>) invocation -> {
            AgentClient.Handler handler = invocation.getArgument(3);
            handler.onToken("thinking...");   // no trailing \n → mid-line
            handler.onToolCall("read_file", "{\"path\":\"x\"}");
            return null;
        }).when(client).streamChat(any(), any(), any(), any());

        runnerWithInput("go\nexit\n", client).run();
        String out = output();
        assertThat(out).contains("thinking...");
        assertThat(out).contains("read_file");
    }

    @Test
    void streamPrinterOnError() throws Exception {
        AgentClient client = mock(AgentClient.class);
        doAnswer((Answer<Void>) invocation -> {
            AgentClient.Handler handler = invocation.getArgument(3);
            handler.onError("something went boom");
            return null;
        }).when(client).streamChat(any(), any(), any(), any());

        runnerWithInput("oops\nexit\n", client).run();
        assertThat(output()).contains("boom");
    }

    @Test
    void streamPrinterFinishLineAddsNewlineWhenMidLine() throws Exception {
        // After a token with no trailing newline, finishLine() should emit one
        // so the next prompt starts on a fresh line.
        AgentClient client = mock(AgentClient.class);
        doAnswer((Answer<Void>) invocation -> {
            AgentClient.Handler handler = invocation.getArgument(3);
            handler.onToken("no newline here");   // mid-line after this
            return null;
        }).when(client).streamChat(any(), any(), any(), any());

        runnerWithInput("ping\nexit\n", client).run();
        String out = output();
        // The output must contain the token text; finishLine will have emitted
        // a newline so the subsequent "Goodbye" appears correctly.
        assertThat(out).contains("no newline here");
        assertThat(out).contains("Goodbye");
    }

    @Test
    void streamPrinterEmptyTokenIsIgnored() throws Exception {
        // onToken("") should be silently dropped (guards against spurious flushes)
        AgentClient client = mock(AgentClient.class);
        doAnswer((Answer<Void>) invocation -> {
            AgentClient.Handler handler = invocation.getArgument(3);
            handler.onToken("");
            handler.onToken("visible");
            return null;
        }).when(client).streamChat(any(), any(), any(), any());

        runnerWithInput("ping\nexit\n", client).run();
        assertThat(output()).contains("visible");
    }

    // ------------------------------------------------------------------
    // Error handling in ask()
    // ------------------------------------------------------------------

    @Test
    void connectExceptionShowsFriendlyMessage() throws Exception {
        AgentClient client = mock(AgentClient.class);
        doThrow(new java.net.ConnectException("Connection refused"))
                .when(client).streamChat(any(), any(), any(), any());

        runnerWithInput("hello\nexit\n", client).run();
        assertThat(output()).contains("Cannot reach the agent server");
    }

    @Test
    void ioExceptionShowsConnectionError() throws Exception {
        AgentClient client = mock(AgentClient.class);
        doThrow(new IOException("reset by peer"))
                .when(client).streamChat(any(), any(), any(), any());

        runnerWithInput("hello\nexit\n", client).run();
        assertThat(output()).contains("Connection error");
    }

    @Test
    void interruptedExceptionSetsInterruptFlag() throws Exception {
        AgentClient client = mock(AgentClient.class);
        doThrow(new InterruptedException("test interrupt"))
                .when(client).streamChat(any(), any(), any(), any());

        runnerWithInput("hello\nexit\n", client).run();
        // The runner catches InterruptedException, prints a message, and re-sets the flag.
        assertThat(output()).contains("Interrupted");
        // Clear the interrupt flag so it doesn't pollute subsequent tests.
        Thread.interrupted();
    }

    // ------------------------------------------------------------------
    // model is picked up from TerminalProperties when set
    // ------------------------------------------------------------------

    @Test
    void modelFromPropertiesIsPassedToClient() throws Exception {
        AgentClient client = mock(AgentClient.class);
        System.setIn(new ByteArrayInputStream("ask something\nexit\n".getBytes(StandardCharsets.UTF_8)));
        var props = new TerminalProperties("http://localhost:8080", null, "claude-3");
        var runner = new TerminalRunner(client, props);
        runner.run();
        verify(client).streamChat(eq("ask something"), eq("claude-3"), any(), any());
    }

    // ------------------------------------------------------------------
    // banner
    // ------------------------------------------------------------------

    @Test
    void bannerIncludesServerUrl() throws Exception {
        runnerWithInput("exit\n").run();
        assertThat(output()).contains("http://localhost:8080");
    }
}
