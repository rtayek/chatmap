package chatmap.infrastructure.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import chatmap.application.port.document.ProjectDocumentReader;

/** Reads only the configured project's fixed working-context document. */
public final class FilesystemProjectDocumentReader implements ProjectDocumentReader {

    private static final Path relativePath = Path.of(".llm", "working-context.md");

    private final Path configuredRoot;

    public FilesystemProjectDocumentReader(Path configuredRoot) {
        this.configuredRoot = configuredRoot == null ? null : configuredRoot.toAbsolutePath().normalize();
    }

    @Override
    public DocumentResult readWorkingContext() throws IOException {
        if (configuredRoot == null) {
            return result(DocumentResult.Status.unavailable, null, null,
                    "No --project-root was configured.");
        }
        if (!Files.isDirectory(configuredRoot)) {
            return result(DocumentResult.Status.invalidConfiguration, null, null,
                    "Configured project root is not an existing directory.");
        }
        Path realRoot = configuredRoot.toRealPath();
        Path target = configuredRoot.resolve(relativePath).normalize();
        if (!Files.exists(target)) {
            return result(DocumentResult.Status.missing, null, null,
                    "The configured project has no .llm/working-context.md file.");
        }
        Path realTarget = target.toRealPath();
        if (!realTarget.startsWith(realRoot) || !Files.isRegularFile(realTarget)) {
            return result(DocumentResult.Status.invalidConfiguration, null, null,
                    "The fixed working-context target escapes the configured project root.");
        }
        byte[] bytes = Files.readAllBytes(realTarget);
        String text = new String(bytes, StandardCharsets.UTF_8);
        return new DocumentResult(DocumentResult.Status.available, relativePath.toString().replace('\\', '/'),
                text, sha256(bytes), null);
    }

    private static DocumentResult result(DocumentResult.Status status, String text, String hash, String detail) {
        return new DocumentResult(status, relativePath.toString().replace('\\', '/'), text, hash, detail);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
