package chatmap.infrastructure.importer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import chatmap.application.model.ImportedChat;
import chatmap.application.port.importing.ConversationFileReader;
import chatmap.domain.Chat;

/** File-system adapter selecting the existing parser by file extension. */
public final class DefaultConversationFileReader implements ConversationFileReader {

    @Override
    public List<ImportedChat> read(Path file, String importedAt) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        List<ImportedChat> imported = switch (extension(file)) {
            case "md", "markdown" -> List.of(new MarkdownImporter().importMarkdown(text, fileName(file), importedAt));
            case "json" -> new ChatGptJsonImporter().importAll(text, importedAt);
            default -> List.of(new PlainTextImporter().importText(fileName(file), text, importedAt));
        };
        if (imported.isEmpty()) {
            throw new IOException("No importable conversation found in " + fileName(file));
        }

        String baseUri = file.toAbsolutePath().normalize().toUri().toString();
        List<ImportedChat> withSourceUris = new ArrayList<>(imported.size());
        for (int index = 0; index < imported.size(); index++) {
            String sourceUri = imported.size() == 1 ? baseUri : baseUri + "#conversation=" + (index + 1);
            ImportedChat candidate = imported.get(index);
            Chat chat = candidate.chat().toBuilder().sourceUri(sourceUri).build();
            withSourceUris.add(new ImportedChat(chat, candidate.messages()));
        }
        return List.copyOf(withSourceUris);
    }

    private static String extension(Path file) {
        String name = fileName(file).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    private static String fileName(Path file) {
        Path name = file.getFileName();
        return name == null ? "Imported Chat" : name.toString();
    }
}
