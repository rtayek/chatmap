package chatmap.domain;

import java.util.List;

/** Ordered chain from a starting session through successor assignments/sessions. */
public record WorkerLifecycleChain(List<WorkerLifecycleRecord> records) {
    public WorkerLifecycleChain {
        records = List.copyOf(records);
    }
}
