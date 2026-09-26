package app.onepve.carbrowser;

public class BookmarkItem {
    public String id;
    public String title;
    public String url;
    public String category; // "video", "audio", "search", "car", "custom"
    public String badge;
    public boolean isPreset;

    public BookmarkItem(String id, String title, String url, String category, String badge, boolean isPreset) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.category = category;
        this.badge = badge;
        this.isPreset = isPreset;
    }
}
