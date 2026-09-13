package site.mcrelicworld.relicprison.gang;

public record GangOperationResult(boolean success, String message) {
    public static GangOperationResult success(String message) {
        return new GangOperationResult(true, message);
    }

    public static GangOperationResult failure(String message) {
        return new GangOperationResult(false, message);
    }
}
