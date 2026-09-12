package chatmap.application.port.exporting;

import chatmap.application.model.ProjectHandoffModel;

/** Formats a hydrated project handoff for an external representation. */
public interface ProjectHandoffFormatter {
    String format(ProjectHandoffModel model);
}
