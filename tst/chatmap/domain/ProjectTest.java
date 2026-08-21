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
    void normalizesOptionalLocalAndRemotePaths() {
        Project missing = new Project(0, "Foo", null, " ", "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z");
        Project present = new Project(0, "Foo", null, " C:/work/foo ", " https://github.com/rtayek/foo ",
                "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z");

        assertEquals(null, missing.repositoryPath());
        assertEquals(null, missing.localPath());
        assertEquals(null, missing.remoteUrl());
        assertEquals("C:/work/foo", present.repositoryPath());
        assertEquals("C:/work/foo", present.localPath());
        assertEquals("https://github.com/rtayek/foo", present.remoteUrl());
    }
}
