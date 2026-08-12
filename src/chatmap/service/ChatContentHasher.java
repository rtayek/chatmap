package chatmap.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import chatmap.domain.Message;

/**
 * SHA-256 over ordered message role and text only.
 *
 * Encoding is unambiguous: each UTF-8 field is prefixed by its byte length as a
 * 32-bit integer. Message timestamps, raw JSON, import timestamps, titles, and
 * local paths are deliberately excluded.
 */
final class ChatContentHasher {

    private ChatContentHasher() {
    }

    static String hash(List<Message> messages) {
        MessageDigest digest = sha256();
        messages.stream()
                .sorted(Comparator.comparingInt(Message::sequence))
                .forEach(message -> {
                    addField(digest, normalize(message.role().dbValue()));
                    addField(digest, normalize(message.text()));
                });
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void addField(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
