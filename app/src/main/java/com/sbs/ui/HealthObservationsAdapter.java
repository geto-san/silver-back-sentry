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
import com.sbs.data.HealthObservationRecord;
import com.sbs.data.SyncState;

import java.text.DateFormat;
import java.util.Date;

public final class HealthObservationsAdapter extends ListAdapter<HealthObservationRecord, HealthObservationsAdapter.HealthViewHolder> {

    public interface HealthActionListener {
        void onOpen(HealthObservationRecord record);
        void onEdit(HealthObservationRecord record);
        void onDelete(HealthObservationRecord record);
    }

    private static final DiffUtil.ItemCallback<HealthObservationRecord> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<HealthObservationRecord>() {
                @Override
                public boolean areItemsTheSame(@NonNull HealthObservationRecord oldItem, @NonNull HealthObservationRecord newItem) {
                    return oldItem.localId.equals(newItem.localId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull HealthObservationRecord oldItem, @NonNull HealthObservationRecord newItem) {
                    return oldItem.timestamp == newItem.timestamp
                            && sameText(oldItem.title, newItem.title)
                            && sameText(oldItem.notes, newItem.notes)
                            && sameText(oldItem.syncStatus, newItem.syncStatus);
                }
            };

    private final HealthActionListener actionListener;

    public HealthObservationsAdapter(HealthActionListener actionListener) {
        super(DIFF_CALLBACK);
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public HealthViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new HealthViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_health_observation_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull HealthViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    final class HealthViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView author;
        private final TextView notes;
        private final TextView timestamp;
        private final TextView status;
        private final View actions;
        private final Button edit;
        private final Button delete;

        HealthViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvHealthTitle);
            author = itemView.findViewById(R.id.tvHealthAuthor);
            notes = itemView.findViewById(R.id.tvHealthNotes);
            timestamp = itemView.findViewById(R.id.tvHealthTimestamp);
            status = itemView.findViewById(R.id.tvHealthStatus);
            actions = itemView.findViewById(R.id.layoutHealthActions);
            edit = itemView.findViewById(R.id.btnHealthEdit);
            delete = itemView.findViewById(R.id.btnHealthDelete);
        }

        void bind(HealthObservationRecord record) {
            title.setText(record.title);
            author.setText("By: " + (record.authorName == null ? "Unknown" : record.authorName));
            notes.setText(record.notes == null || record.notes.isEmpty() ? itemView.getContext().getString(R.string.no_notes) : record.notes);
            timestamp.setText(DateFormat.getDateTimeInstance().format(new Date(record.timestamp)));
            status.setText(formatStatus(record.syncStatus));
            itemView.setOnClickListener(v -> actionListener.onOpen(record));

            boolean canEdit = record.authorId != null && record.authorId.equals(FirebaseAuth.getInstance().getUid());
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
