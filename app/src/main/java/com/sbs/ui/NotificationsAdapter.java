package com.sbs.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.sbs.R;
import com.sbs.data.AppNotificationRecord;

import java.text.DateFormat;
import java.util.Date;

public final class NotificationsAdapter extends ListAdapter<AppNotificationRecord, NotificationsAdapter.NotificationViewHolder> {

    public interface NotificationClickListener {
        void onOpen(AppNotificationRecord record);
    }

    private static final DiffUtil.ItemCallback<AppNotificationRecord> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AppNotificationRecord>() {
                @Override
                public boolean areItemsTheSame(@NonNull AppNotificationRecord oldItem, @NonNull AppNotificationRecord newItem) {
                    return oldItem.notificationId.equals(newItem.notificationId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull AppNotificationRecord oldItem, @NonNull AppNotificationRecord newItem) {
                    return oldItem.isRead == newItem.isRead
                            && oldItem.createdAt == newItem.createdAt
                            && oldItem.message.equals(newItem.message);
                }
            };

    private final NotificationClickListener listener;

    public NotificationsAdapter(NotificationClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new NotificationViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_notification_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    final class NotificationViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView message;
        private final TextView timestamp;
        private final View unreadDot;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvNotificationTitle);
            message = itemView.findViewById(R.id.tvNotificationMessage);
            timestamp = itemView.findViewById(R.id.tvNotificationTimestamp);
            unreadDot = itemView.findViewById(R.id.viewUnreadDot);
        }

        void bind(AppNotificationRecord record) {
            title.setText(record.title);
            message.setText(record.message);
            timestamp.setText(DateFormat.getDateTimeInstance().format(new Date(record.createdAt)));
            unreadDot.setVisibility(record.isRead ? View.INVISIBLE : View.VISIBLE);
            itemView.setAlpha(record.isRead ? 0.72f : 1f);
            itemView.setOnClickListener(v -> listener.onOpen(record));
        }
    }
}
