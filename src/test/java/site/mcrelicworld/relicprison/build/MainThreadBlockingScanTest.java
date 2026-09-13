package site.mcrelicworld.relicprison.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class MainThreadBlockingScanTest {
    @Test
    void enablePathDoesNotUseFutureJoinOrGet() throws Exception {
        String source = Files.readString(Path.of("src/main/java/site/mcrelicworld/relicprison/RelicPrisonPlugin.java"));
        int start = source.indexOf("@Override public void onEnable()");
        int end = source.indexOf("private void finishEnableAfterDatabase", start);
        String onEnable = source.substring(start, end);
        assertFalse(onEnable.contains(".join()"), "onEnable must not join futures on the server thread");
        assertFalse(onEnable.contains(".get()"), "onEnable must not block on futures on the server thread");
    }
}
