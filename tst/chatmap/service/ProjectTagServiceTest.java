package chatmap.service;

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
import chatmap.domain.Tag;
import chatmap.storage.ChatRepository;
import chatmap.storage.Database;
import chatmap.storage.ProjectRepository;
import chatmap.storage.TagRepository;

class ProjectTagServiceTest {

    private Connection conn;
    private ChatRepository chatRepository;
    private ProjectService projectService;
    private TagService tagService;

    @BeforeEach
    void setUp() throws Exception {
        conn = new Database("jdbc:sqlite::memory:").openAndInitialize();
        ProjectRepository projectRepository = new ProjectRepository(conn);
        TagRepository tagRepository = new TagRepository(conn);
        chatRepository = new ChatRepository(conn);
        projectService = new ProjectService(projectRepository, chatRepository);
        tagService = new TagService(tagRepository, chatRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    void managesProjectCrud() throws Exception {
        Project created = projectService.create(new Project(0, "Client Work", "Initial",
                "2026-07-06T00:00:00Z", "2026-07-06T00:00:00Z"));

        assertEquals("Client Work", projectService.findById(created.id()).orElseThrow().name());

        projectService.update(new Project(created.id(), "Research", "Updated",
                created.createdAt(), "2026-07-06T01:00:00Z"));

        Project updated = projectService.findById(created.id()).orElseThrow();
        assertEquals("Research", updated.name());
        assertEquals("Updated", updated.description());

        projectService.delete(created.id());

        assertTrue(projectService.findById(created.id()).isEmpty());
    }

    @Test
    void assignsRemovesAndListsChatsByProject() throws Exception {
        Project project = projectService.create(new Project(0, "Project", null,
                "2026-07-06T00:00:00Z", "2026-07-06T00:00:00Z"));
        Chat first = insertChat("First", "2026-07-06T00:00:00Z");
        Chat second = insertChat("Second", "2026-07-06T00:01:00Z");
        Chat outside = insertChat("Outside", "2026-07-06T00:02:00Z");

        projectService.assignChat(second.id(), project.id());
        projectService.assignChat(first.id(), project.id());

        Chat firstAssigned = new Chat(first.id(), project.id(), first.source(), first.title(),
                first.createdAt(), first.updatedAt(), first.importedAt(), first.archived());
        Chat secondAssigned = new Chat(second.id(), project.id(), second.source(), second.title(),
                second.createdAt(), second.updatedAt(), second.importedAt(), second.archived());

        assertEquals(List.of(firstAssigned, secondAssigned), projectService.listChats(project.id()));

        projectService.removeChat(first.id());

        assertEquals(List.of(secondAssigned), projectService.listChats(project.id()));
        assertEquals(null, chatRepository.findById(first.id()).orElseThrow().projectId());
        assertEquals(null, chatRepository.findById(outside.id()).orElseThrow().projectId());
    }

    @Test
    void managesTagCrud() throws Exception {
        Tag created = tagService.create(new Tag(0, "MVP"));

        assertEquals(created, tagService.findByName("mvp").orElseThrow());

        tagService.update(new Tag(created.id(), "Search"));

        assertEquals("Search", tagService.findById(created.id()).orElseThrow().name());
        assertEquals(created.id(), tagService.findByName("search").orElseThrow().id());

        tagService.delete(created.id());

        assertTrue(tagService.findById(created.id()).isEmpty());
    }

    @Test
    void addsRemovesListsTagsAndListsChatsByTag() throws Exception {
        Chat first = insertChat("First Tagged", "2026-07-06T00:00:00Z");
        Chat second = insertChat("Second Tagged", "2026-07-06T00:01:00Z");
        Chat outside = insertChat("Outside", "2026-07-06T00:02:00Z");
        Tag mvp = tagService.create(new Tag(0, "MVP"));
        Tag search = tagService.create(new Tag(0, "Search"));

        tagService.addToChat(second.id(), mvp.id());
        tagService.addToChat(first.id(), search.id());
        tagService.addToChat(first.id(), mvp.id());
        tagService.addToChat(first.id(), mvp.id());

        assertEquals(List.of(mvp, search), tagService.listForChat(first.id()));
        assertEquals(List.of(first, second), tagService.listChats(mvp.id()));

        tagService.removeFromChat(first.id(), mvp.id());

        assertEquals(List.of(search), tagService.listForChat(first.id()));
        assertEquals(List.of(second), tagService.listChats(mvp.id()));
        assertTrue(tagService.listForChat(outside.id()).isEmpty());
    }

    private Chat insertChat(String title, String importedAt) throws Exception {
        return chatRepository.insert(new Chat(0, null, Source.plainText, title,
                null, null, importedAt, false));
    }
}
