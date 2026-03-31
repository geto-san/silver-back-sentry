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
import com.sbs.data.PatrolLogRecord;
import com.sbs.data.SyncState;

import java.text.DateFormat;
import java.util.Date;

public final class PatrolLogsAdapter extends ListAdapter<PatrolLogRecord, PatrolLogsAdapter.LogViewHolder> {

    public interface LogActionListener {
        void onOpen(PatrolLogRecord record);
        void onDelete(PatrolLogRecord record);
        void onEdit(PatrolLogRecord record);
    }

    private static final DiffUtil.ItemCallback<PatrolLogRecord> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<PatrolLogRecord>() {
                @Override
                public boolean areItemsTheSame(@NonNull PatrolLogRecord oldItem, @NonNull PatrolLogRecord newItem) {
                    return oldItem.localId.equals(newItem.localId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull PatrolLogRecord oldItem, @NonNull PatrolLogRecord newItem) {
                    return oldItem.timestamp == newItem.timestamp
                            && sameText(oldItem.title, newItem.title)
                            && sameText(oldItem.notes, newItem.notes)
                            && sameText(oldItem.syncStatus, newItem.syncStatus);
                }
            };

    private final LogActionListener actionListener;

    public PatrolLogsAdapter(LogActionListener actionListener) {
        super(DIFF_CALLBACK);
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new LogViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_patrol_log_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    final class LogViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView author;
        private final TextView notes;
        private final TextView timestamp;
        private final TextView status;
        private final View actions;
        private final Button edit;
        private final Button delete;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvLogTitle);
            author = itemView.findViewById(R.id.tvLogAuthor);
            notes = itemView.findViewById(R.id.tvLogNotes);
            timestamp = itemView.findViewById(R.id.tvLogTimestamp);
            status = itemView.findViewById(R.id.tvLogStatus);
            actions = itemView.findViewById(R.id.layoutLogActions);
            edit = itemView.findViewById(R.id.btnLogEdit);
            delete = itemView.findViewById(R.id.btnLogDelete);
        }

        void bind(PatrolLogRecord record) {
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
