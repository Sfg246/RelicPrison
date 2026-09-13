package site.mcrelicworld.relicprison.api.model;

public record BackupView(String id, String type, long createdAt, long sizeBytes, String checksum) {}
