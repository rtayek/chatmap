package chatmap.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProjectTest {

    @Test
    void validatesStableIdentityAndDisplayName() {
        assertThrows(IllegalArgumentException.class,
                () -> new Project(-1, "Foo", null, null, "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z"));
        assertThrows(IllegalArgumentException.class,
                () -> new Project(0, " ", null, null, "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z"));
    }

    @Test
    void normalizesOptionalRepositoryPath() {
        Project missing = new Project(0, "Foo", null, " ", "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z");
        Project present = new Project(0, "Foo", null, " C:/work/foo ", "2026-08-20T00:00:00Z",
                "2026-08-20T00:00:00Z");

        assertEquals(null, missing.repositoryPath());
        assertEquals("C:/work/foo", present.repositoryPath());
    }
}
