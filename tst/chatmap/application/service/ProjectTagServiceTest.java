package chatmap.application.service;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import chatmap.domain.Chat;
import chatmap.domain.Project;
import chatmap.domain.Source;
import chatmap.domain.Tag;
import chatmap.infrastructure.persistence.sqlite.ChatRepository;
import chatmap.infrastructure.persistence.sqlite.Database;
import chatmap.infrastructure.persistence.sqlite.ProjectRepository;
import chatmap.infrastructure.persistence.sqlite.TagRepository;

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
        Project alpha = projectService.create(new Project(0, "Alpha", null,
                "2026-07-06T00:00:00Z", "2026-07-06T00:00:00Z"));

        assertEquals("Client Work", projectService.findById(created.id()).orElseThrow().name());
        assertEquals(List.of(alpha, created), projectService.listAll());

        projectService.update(new Project(created.id(), "Research", "Updated",
                created.createdAt(), "2026-07-06T01:00:00Z"));

        Project updated = projectService.findById(created.id()).orElseThrow();
        assertEquals("Research", updated.name());
        assertEquals("Updated", updated.description());

        projectService.delete(created.id());

        assertTrue(projectService.findById(created.id()).isEmpty());
    }

    @Test
    void createRejectsADuplicateNameInsteadOfSilentlyCreatingOne() throws Exception {
        projectService.create(new Project(0, "Foo", null, "2026-08-10T00:00:00Z", "2026-08-10T00:00:00Z"));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> projectService.create(new Project(0, "Foo", null,
                        "2026-08-10T00:00:00Z", "2026-08-10T00:00:00Z")));

        assertTrue(thrown.getMessage().contains("Foo"));
        assertEquals(1, projectService.listAll().size());
    }

    @Test
    void findOrCreateReturnsTheExistingProjectInsteadOfDuplicating() throws Exception {
        Project first = projectService.findOrCreate("Consolidator Project", "desc",
                "2026-08-10T00:00:00Z");
        Project second = projectService.findOrCreate("Consolidator Project", "desc",
                "2026-08-10T00:01:00Z");

        assertEquals(first.id(), second.id());
        assertEquals(1, projectService.listAll().size());
    }

    @Test
    void findOrCreateMatchesNonAsciiCaseVariantsInJava() throws Exception {
        Project created = projectService.findOrCreate("München Notes", "desc", "2026-08-10T00:00:00Z");

        Project found = projectService.findOrCreate("MÜNCHEN NOTES", "desc", "2026-08-10T00:01:00Z");

        assertEquals(created.id(), found.id());
        assertEquals(1, projectService.listAll().size());
    }

    @Test
    void findOrCreateNeverProducesDuplicatesUnderConcurrentCallers() throws Exception {
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Project>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return projectService.findOrCreate("Racing Project", "desc", "2026-08-10T00:00:00Z");
                }));
            }
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            go.countDown();

            Set<Long> ids = new HashSet<>();
            for (Future<Project> future : futures) {
                ids.add(future.get(5, TimeUnit.SECONDS).id());
            }

            assertEquals(1, ids.size(), "every caller must agree on the same project id");
            assertEquals(1, projectService.listAll().stream()
                    .filter(p -> p.name().equals("Racing Project"))
                    .count());
        } finally {
            pool.shutdownNow();
        }
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
        Tag alpha = tagService.create(new Tag(0, "Alpha"));

        assertEquals(created, tagService.findByName("mvp").orElseThrow());
        assertEquals(List.of(alpha, created), tagService.listAll());

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
