package com.sbs.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.sbs.R;
import com.sbs.data.SightingRecord;
import com.sbs.data.SyncState;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public final class SightingsAdapter extends ListAdapter<SightingRecord, SightingsAdapter.SightingViewHolder> {

    public interface SightingActionListener {
        void onOpen(SightingRecord record);
        void onDelete(SightingRecord record);
        void onEdit(SightingRecord record);
    }

    private static final DiffUtil.ItemCallback<SightingRecord> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<SightingRecord>() {
                @Override
                public boolean areItemsTheSame(@NonNull SightingRecord oldItem, @NonNull SightingRecord newItem) {
                    return oldItem.localId.equals(newItem.localId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull SightingRecord oldItem, @NonNull SightingRecord newItem) {
                    return oldItem.timestamp == newItem.timestamp
                            && oldItem.lastSyncAttempt == newItem.lastSyncAttempt
                            && oldItem.lat == newItem.lat
                            && oldItem.lng == newItem.lng
                            && oldItem.radius == newItem.radius
                            && sameText(oldItem.title, newItem.title)
                            && sameText(oldItem.notes, newItem.notes)
                            && sameText(oldItem.syncStatus, newItem.syncStatus);
                }
            };

    private final SightingActionListener actionListener;

    public SightingsAdapter(SightingActionListener actionListener) {
        super(DIFF_CALLBACK);
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public SightingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SightingViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_sighting_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull SightingViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    final class SightingViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView author;
        private final TextView coords;
        private final TextView notes;
        private final TextView timestamp;
        private final TextView status;
        private final View actions;
        private final Button edit;
        private final Button delete;

        SightingViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvSightingTitle);
            author = itemView.findViewById(R.id.tvSightingAuthor);
            coords = itemView.findViewById(R.id.tvSightingCoords);
            notes = itemView.findViewById(R.id.tvSightingNotes);
            timestamp = itemView.findViewById(R.id.tvSightingTimestamp);
            status = itemView.findViewById(R.id.tvSightingStatus);
            actions = itemView.findViewById(R.id.layoutActions);
            edit = itemView.findViewById(R.id.btnEdit);
            delete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(SightingRecord record) {
            title.setText(record.title);
            author.setText("By: " + (record.authorName == null ? "Unknown" : record.authorName));
            coords.setText(String.format(Locale.US, "Lat %.5f, Lng %.5f", record.lat, record.lng));
            notes.setText(record.notes == null || record.notes.isEmpty() ? itemView.getContext().getString(R.string.no_notes) : record.notes);
            timestamp.setText(DateFormat.getDateTimeInstance().format(new Date(record.timestamp)));
            status.setText(formatStatus(record.syncStatus));

            itemView.setOnClickListener(v -> actionListener.onOpen(record));

            boolean canEdit = record.rangerId != null && record.rangerId.equals(FirebaseAuth.getInstance().getUid());
            actions.setVisibility(canEdit ? View.VISIBLE : View.GONE);
            edit.setOnClickListener(v -> actionListener.onEdit(record));
            delete.setOnClickListener(v -> actionListener.onDelete(record));
        }
    }

    private static String formatStatus(String value) {
        if (SyncState.SYNCED.equals(value)) return "Synced";
        if (SyncState.SYNCING.equals(value)) return "Syncing";
        if (SyncState.FAILED.equals(value)) return "Failed";
        return "Pending";
    }

    private static boolean sameText(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
