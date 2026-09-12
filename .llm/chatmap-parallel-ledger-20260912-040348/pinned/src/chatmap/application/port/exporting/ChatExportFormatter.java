package chatmap.application.port.exporting;

import chatmap.application.model.ChatExportModel;

/** Formats a hydrated chat for an external representation. */
public interface ChatExportFormatter {
    String format(ChatExportModel model);
}
