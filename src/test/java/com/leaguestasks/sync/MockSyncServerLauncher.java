package com.leaguestasks.sync;

import java.io.IOException;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Dev-only local HTTP listener (not shipped in the plugin jar) that logs
 * every request it receives and returns 200 OK -- lets the sync buttons
 * be exercised against something real without a live website.
 *
 * <p>Run via {@code ./gradlew runMockSyncServer} in one terminal and
 * {@code ./gradlew run} in another, then set the plugin's {@code Server URL}
 * to {@code http://localhost:8090} and any placeholder {@code API Token}.
 */
public final class MockSyncServerLauncher
{
    private static final int PORT = 8090;

    private MockSyncServerLauncher()
    {
    }

    public static void main(String[] args) throws IOException
    {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
            {
                System.out.println("=== " + request.getMethod() + " " + request.getPath() + " ===");
                String authorization = request.getHeader("Authorization");
                System.out.println("Authorization: " + (authorization == null ? "(none)" : "Bearer <redacted>"));
                System.out.println(request.getBody().readUtf8());
                System.out.println();
                return new MockResponse().setResponseCode(200);
            }
        });
        server.start(PORT);
        System.out.println("Mock sync server listening on http://localhost:" + PORT);
        System.out.println("Point the plugin's Server URL setting at the line above. Ctrl+C here to stop.");
    }
}
