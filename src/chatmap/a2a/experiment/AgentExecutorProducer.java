package chatmap.a2a.experiment;

import java.util.List;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.TextPart;

import chatmap.infrastructure.llm.OllamaProvider;

@ApplicationScoped
public class AgentExecutorProducer {
    @Produces
    public AgentExecutor agentExecutor() {
        return new AgentExecutor() {
            @Override
            public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
                FakeWorker.Result result = worker.apply(context.getUserInput("\n"));

                switch (result.status()) {
                    case COMPLETED -> {
                        emitter.startWork();
                        emitter.addArtifact(
                                List.of(new TextPart(result.text())),
                                "worker-result",
                                "Worker result",
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

    private static Function<String, FakeWorker.Result> createWorker() {
        if (A2aExperimentSettings.modelBacked()) {
            ModelWorker modelWorker = new ModelWorker(
                    new OllamaProvider(),
                    A2aExperimentSettings.modelTarget());
            return modelWorker::execute;
        }
        FakeWorker fakeWorker = new FakeWorker();
        return fakeWorker::execute;
    }

    private final Function<String, FakeWorker.Result> worker = createWorker();
}
