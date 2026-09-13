package site.mcrelicworld.relicprison.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AdminEditorAccessPolicyTest {
    @Test
    void rejectsMissingPermissionAndAcceptsAdminPermission() {
        assertFalse(AdminEditorAccessPolicy.allowed(permission -> false));
        assertTrue(AdminEditorAccessPolicy.allowed(permission -> permission.equals("relicprison.admin")));
    }
}
