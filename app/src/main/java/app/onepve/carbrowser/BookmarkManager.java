package app.onepve.carbrowser;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class BookmarkManager {
    private static final String PREF_NAME = "car_browser_bookmarks";
    private static final String KEY_CUSTOM = "custom_bookmarks";
    private final SharedPreferences prefs;

    public BookmarkManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public List<BookmarkItem> getPresetBookmarks() {
        List<BookmarkItem> list = new ArrayList<>();
        // 影音娱乐
        list.add(new BookmarkItem("p1", "哔哩哔哩", "https://www.bilibili.com", "video", "哔", true));
        list.add(new BookmarkItem("p2", "抖音网页版", "https://www.douyin.com", "video", "抖", true));
        list.add(new BookmarkItem("p3", "爱奇艺", "https://www.iqiyi.com", "video", "爱", true));
        list.add(new BookmarkItem("p4", "腾讯视频", "https://v.qq.com", "video", "腾", true));
        list.add(new BookmarkItem("p5", "优酷视频", "https://www.youku.com", "video", "酷", true));

        // 听书阅读
        list.add(new BookmarkItem("p6", "微信读书", "https://weread.qq.com", "audio", "微", true));
        list.add(new BookmarkItem("p7", "QQ阅读", "https://yuedu.qq.com", "audio", "阅", true));
        list.add(new BookmarkItem("p8", "喜马拉雅", "https://www.ximalaya.com", "audio", "喜", true));
        list.add(new BookmarkItem("p9", "网易云音乐", "https://music.163.com", "audio", "云", true));
        list.add(new BookmarkItem("p10", "QQ音乐", "https://y.qq.com", "audio", "Ｑ", true));

        // 搜索与AI
        list.add(new BookmarkItem("p11", "百度搜索", "https://www.baidu.com", "search", "度", true));
        list.add(new BookmarkItem("p12", "必应搜索", "https://cn.bing.com", "search", "应", true));
        list.add(new BookmarkItem("p13", "DeepSeek", "https://chat.deepseek.com", "search", "Ｄ", true));
        list.add(new BookmarkItem("p14", "Kimi 智能", "https://kimi.ai", "search", "Ｋ", true));

        // 汽车与社区资讯
        list.add(new BookmarkItem("p15", "懂车帝", "https://www.dongchedi.com", "car", "懂", true));
        list.add(new BookmarkItem("p16", "汽车之家", "https://www.autohome.com.cn", "car", "家", true));
        list.add(new BookmarkItem("p17", "知乎", "https://www.zhihu.com", "car", "知", true));
        list.add(new BookmarkItem("p18", "新浪微博", "https://weibo.com", "car", "博", true));

        return list;
    }

    public List<BookmarkItem> getCustomBookmarks() {
        List<BookmarkItem> list = new ArrayList<>();
        String json = prefs.getString(KEY_CUSTOM, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                list.add(new BookmarkItem(
                    obj.getString("id"),
                    obj.getString("title"),
                    obj.getString("url"),
                    obj.optString("category", "custom"),
                    obj.optString("badge", "★"),
                    false
                ));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void addCustomBookmark(String title, String url) {
        List<BookmarkItem> custom = getCustomBookmarks();
        String badge = title.length() > 0 ? title.substring(0, 1) : "★";
        String id = "c_" + System.currentTimeMillis();
        custom.add(0, new BookmarkItem(id, title, url, "custom", badge, false));
        saveCustom(custom);
    }

    public void removeCustomBookmark(String id) {
        List<BookmarkItem> custom = getCustomBookmarks();
        List<BookmarkItem> updated = new ArrayList<>();
        for (BookmarkItem item : custom) {
            if (!item.id.equals(id)) {
                updated.add(item);
            }
        }
        saveCustom(updated);
    }

    private void saveCustom(List<BookmarkItem> list) {
        try {
            JSONArray arr = new JSONArray();
            for (BookmarkItem item : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("title", item.title);
                obj.put("url", item.url);
                obj.put("category", item.category);
                obj.put("badge", item.badge);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_CUSTOM, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public List<BookmarkItem> getAllBookmarks(String categoryFilter) {
        List<BookmarkItem> result = new ArrayList<>();
        // 自定义项最优先
        for (BookmarkItem item : getCustomBookmarks()) {
            if (categoryFilter.equals("all") || categoryFilter.equals(item.category)) {
                result.add(item);
            }
        }
        // 预置项
        for (BookmarkItem item : getPresetBookmarks()) {
            if (categoryFilter.equals("all") || categoryFilter.equals(item.category)) {
                result.add(item);
            }
        }
        return result;
    }
}
