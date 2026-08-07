package chatmap.service;

import java.util.Objects;

public record BackendDescriptor(String id, String label) {
    public BackendDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
    }
}
