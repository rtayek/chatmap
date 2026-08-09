package chatmap.backend;

import java.util.Objects;

public record BackendId(String value) {
    public BackendId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
