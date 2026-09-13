package site.mcrelicworld.relicprison.gui;

public final class GuiLayout {
    public static final PageLayout PAGED_54 = new PageLayout(45, 48, 49, 50, 53, 36);
    public static final ConfirmationLayout CONFIRM_27 = new ConfirmationLayout(10, 13, 16);

    private GuiLayout() { }

    public static int pages(int itemCount, int pageSize) {
        if (pageSize < 1) throw new IllegalArgumentException("Page size must be positive");
        return Math.max(1, (Math.max(0, itemCount) + pageSize - 1) / pageSize);
    }

    public static int boundedPage(int requestedPage, int pages) {
        return Math.max(0, Math.min(requestedPage, Math.max(1, pages) - 1));
    }

    public record PageLayout(int previous, int pageStatus, int back, int close, int next, int contentSize) { }
    public record ConfirmationLayout(int cancel, int information, int confirm) { }
}
