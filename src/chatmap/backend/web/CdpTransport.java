package chatmap.backend.web;

import java.util.Map;

import com.google.gson.JsonObject;

public interface CdpTransport extends AutoCloseable {

    JsonObject send(String method, Map<String, ?> params);

    @Override
    void close();
}
