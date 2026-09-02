package chatmap.a2a.experiment;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.TextPart;

@ApplicationScoped
public class AgentExecutorProducer {
    @Produces
    public AgentExecutor agentExecutor() {
        return new AgentExecutor() {
            @Override
            public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
                FakeWorker.Result result = worker.execute(context.getUserInput("\n"));

                switch (result.status()) {
                    case COMPLETED -> {
                        emitter.startWork();
                        emitter.addArtifact(
                                List.of(new TextPart(result.text())),
                                "fake-worker-result",
                                "Fake worker result",
                                null);
                        emitter.complete();
                    }
                    case INPUT_REQUIRED -> emitter.requiresInput(
                            emitter.newAgentMessage(
                                    List.of(new TextPart(result.text())),
                                    null));
                    case FAILED -> {
                        emitter.startWork();
                        emitter.fail(
                                emitter.newAgentMessage(
                                        List.of(new TextPart(result.text())),
                                        null));
                    }
                }
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
                emitter.cancel();
            }
        };
    }

    private final FakeWorker worker = new FakeWorker();
}
