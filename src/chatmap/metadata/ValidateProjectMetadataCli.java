package chatmap.metadata;

import java.nio.file.Path;

import chatmap.metadata.ProjectMetadataValidator.Result;

/** Runs the metadata validator against a repository root and reports PASS or the failures. */
public final class ValidateProjectMetadataCli {

    private ValidateProjectMetadataCli() {
    }

    public static void main(String[] args) {
        Path repoRoot = Path.of(args.length > 0 ? args[0] : ".");
        try {
            Result result = new ProjectMetadataValidator(repoRoot).validate();
            if (result.passed()) {
                System.out.println("PASS: project metadata valid ("
                        + result.pilotDocumentCount() + " pilot documents, "
                        + result.requiredDocumentCount() + " required paths)");
                return;
            }
            System.err.println("FAIL: " + result.failures().size() + " problem(s) found");
            for (String failure : result.failures()) {
                System.err.println("  - " + failure);
            }
            System.exit(1);
        } catch (Exception exception) {
            System.err.println("Could not validate project metadata: " + exception.getMessage());
            System.exit(1);
        }
    }
}
