package chatmap.importer;

import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

/** Strongly-typed DTOs for deserializing ChatGPT conversation JSON exports with Gson. */
public final class ChatGptDto {

    private ChatGptDto() {
    }

    public record ConversationDto(
            String id,
            @SerializedName("conversation_id") String conversationId,
            String title,
            @SerializedName("create_time") Double createTime,
            @SerializedName("update_time") Double updateTime,
            @SerializedName("current_node") String currentNode,
            Map<String, NodeDto> mapping) {

        public String externalId() {
            return conversationId != null && !conversationId.isBlank() ? conversationId : id;
        }
    }

    public record NodeDto(
            String id,
            String parent,
            List<String> children,
            MessageDto message) {
    }

    public record MessageDto(
            String id,
            AuthorDto author,
            @SerializedName("create_time") Double createTime,
            ContentDto content,
            String status) {
    }

    public record AuthorDto(String role, String name) {
    }

    public record ContentDto(
            @SerializedName("content_type") String contentType,
            List<Object> parts,
            String text) {
    }
}
