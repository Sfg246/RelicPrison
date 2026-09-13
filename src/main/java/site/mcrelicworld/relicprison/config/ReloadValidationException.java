package site.mcrelicworld.relicprison.config;

import java.util.List;

public final class ReloadValidationException extends Exception {
    private static final long serialVersionUID = 1L;
    private final List<String> failures;

    public ReloadValidationException(List<String> failures) {
        super(String.join(System.lineSeparator(), failures));
        this.failures = List.copyOf(failures);
    }

    public List<String> failures() {
        return failures;
    }
}
