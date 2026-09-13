package site.mcrelicworld.relicprison.gui;

import java.util.Objects;
import java.util.function.Predicate;

/** Central permission policy shared by every admin editor entry point. */
public final class AdminEditorAccessPolicy {
    private AdminEditorAccessPolicy() { }

    public static boolean allowed(Predicate<String> permissionCheck) {
        return Objects.requireNonNull(permissionCheck).test("relicprison.admin");
    }
}
