package chatmap.infrastructure.persistence.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Project;
import chatmap.domain.Source;

class RelatedProjectRepositoryTest {

    private Connection conn;
    private ChatRepository chats;
    private ProjectRepository projects;
    private RelatedProjectRepository relatedProjects;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        chats = new ChatRepository(conn);
        projects = new ProjectRepository(conn);
        relatedProjects = new RelatedProjectRepository(conn);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void addsRemovesListsByChatAndListsChatsByProject() throws Exception {
        Chat first = insertChat("First", "2026-08-21T00:00:00Z");
        Chat second = insertChat("Second", "2026-08-21T00:01:00Z");
        Project alpha = insertProject("Alpha");
        Project beta = insertProject("Beta");

        relatedProjects.assignToChat(second.id(), alpha.id());
        relatedProjects.assignToChat(first.id(), beta.id());
        relatedProjects.assignToChat(first.id(), alpha.id());
        relatedProjects.assignToChat(first.id(), alpha.id());

        assertEquals(List.of(alpha, beta), relatedProjects.findByChat(first.id()));
        assertEquals(List.of(first.id(), second.id()), relatedProjects.findChatsByProject(alpha.id())
                .stream().map(Chat::id).toList());

        relatedProjects.removeFromChat(first.id(), alpha.id());

        assertEquals(List.of(beta), relatedProjects.findByChat(first.id()));
        assertEquals(List.of(second.id()), relatedProjects.findChatsByProject(alpha.id())
                .stream().map(Chat::id).toList());
    }

    @Test
    void chatWithoutMainProjectCanHaveRelatedProjects() throws Exception {
        Chat chat = insertChat("No Main", "2026-08-21T00:00:00Z");
        Project project = insertProject("Related");

        relatedProjects.assignToChat(chat.id(), project.id());

        assertEquals(null, chats.findById(chat.id()).orElseThrow().projectId());
        assertEquals(List.of(project), relatedProjects.findByChat(chat.id()));
        assertTrue(chats.findById(chat.id()).isPresent());
    }

    private Project insertProject(String name) throws Exception {
        return projects.insert(new Project(0, name, null,
                "2026-08-21T00:00:00Z", "2026-08-21T00:00:00Z"));
    }

    private Chat insertChat(String title, String importedAt) throws Exception {
        return chats.insert(Chat.builder()
                .id(0)
                .projectId(null)
                .source(Source.plainText)
                .title(title)
                .importedAt(importedAt)
                .archived(false)
                .build());
    }
}
