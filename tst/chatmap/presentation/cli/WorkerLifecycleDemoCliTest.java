package chatmap.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import chatmap.domain.WorkerLifecycleChain;
import chatmap.domain.WorkerLifecycleState;
import ch.qos.logback.classic.LoggerContext;

class WorkerLifecycleDemoCliTest {

    @TempDir
    Path tempDir;

    @Test
    void demoExercisesCompleteVerticalSlice() throws Exception {
        Path home = tempDir.resolve("home");
        WorkerLifecycleChain chain = WorkerLifecycleDemoCli.execute(
                CliBootstrap.parse(new String[] {"--home", home.toString()}));
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.stop();
        }

        assertEquals(2, chain.records().size());
        assertEquals(WorkerLifecycleState.RETIRED, chain.records().get(0).session().lifecycleState());
        assertEquals(WorkerLifecycleState.WORKING, chain.records().get(1).session().lifecycleState());
        assertTrue(chain.records().get(0).handoff().isPresent());
        assertTrue(Files.isRegularFile(home.resolve("chatmap.db")));
    }
}
