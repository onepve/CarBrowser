package app.onepve.carbrowser;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.net.URI;
import java.util.List;

public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.ViewHolder> {

    public interface OnBookmarkClickListener {
        void onBookmarkClick(BookmarkItem item);
        void onBookmarkLongClick(BookmarkItem item);
    }

    private List<BookmarkItem> list;
    private final OnBookmarkClickListener listener;

    public BookmarkAdapter(List<BookmarkItem> list, OnBookmarkClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    public void setData(List<BookmarkItem> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bookmark, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BookmarkItem item = list.get(position);
        holder.tvTitle.setText(item.title);
        holder.tvBadge.setText(item.badge);

        try {
            URI uri = new URI(item.url);
            String host = uri.getHost();
            holder.tvUrl.setText(host != null ? host : item.url);
        } catch (Exception e) {
            holder.tvUrl.setText(item.url);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onBookmarkClick(item);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onBookmarkLongClick(item);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvBadge;
        TextView tvTitle;
        TextView tvUrl;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBadge = itemView.findViewById(R.id.tv_badge);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvUrl = itemView.findViewById(R.id.tv_url);
        }
    }
}
