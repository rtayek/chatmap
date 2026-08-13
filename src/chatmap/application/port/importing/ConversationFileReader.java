package chatmap.application.port.importing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import chatmap.application.model.ImportedChat;

/** Reads a local file and normalizes every conversation it contains. */
public interface ConversationFileReader {
    List<ImportedChat> read(Path file, String importedAt) throws IOException;
}
