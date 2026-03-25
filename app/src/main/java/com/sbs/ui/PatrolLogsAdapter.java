package com.sbs.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.sbs.R;
import com.sbs.data.AppSettingsManager;
import com.sbs.data.PatrolLogRecord;
import com.sbs.data.PatrolLogStore;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PatrolLogsAdapter extends RecyclerView.Adapter<PatrolLogsAdapter.LogViewHolder> {

    private final List<PatrolLogRecord> logs = new ArrayList<>();
    private final AppSettingsManager appSettingsManager;
    private final LogActionListener actionListener;

    public interface LogActionListener {
        void onSyncNow(PatrolLogRecord record);
        void onDelete(PatrolLogRecord record);
        void onEdit(PatrolLogRecord record);
    }

    public PatrolLogsAdapter(AppSettingsManager appSettingsManager, LogActionListener actionListener) {
        this.appSettingsManager = appSettingsManager;
        this.actionListener = actionListener;
    }

    public void submitList(List<PatrolLogRecord> records) {
        logs.clear();
        if (records != null) logs.addAll(records);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_patrol_log_item, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        holder.bind(logs.get(position));
    }

    @Override
    public int getItemCount() { return logs.size(); }

    class LogViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle, tvAuthor, tvNotes, tvTimestamp, tvStatus;
        private final View layoutActions;
        private final Button btnSyncNow, btnDelete, btnEdit;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvLogTitle);
            tvAuthor = itemView.findViewById(R.id.tvLogAuthor);
            tvNotes = itemView.findViewById(R.id.tvLogNotes);
            tvTimestamp = itemView.findViewById(R.id.tvLogTimestamp);
            tvStatus = itemView.findViewById(R.id.tvLogStatus);
            layoutActions = itemView.findViewById(R.id.layoutLogActions);
            btnSyncNow = itemView.findViewById(R.id.btnLogSyncNow);
            btnDelete = itemView.findViewById(R.id.btnLogDelete);
            btnEdit = itemView.findViewById(R.id.btnLogEdit);
        }

        void bind(PatrolLogRecord record) {
            tvTitle.setText(record.title);
            tvAuthor.setText("By: " + (record.authorName != null ? record.authorName : "Unknown"));
            tvNotes.setText(record.notes == null || record.notes.isEmpty() ? "No notes" : record.notes);
            tvTimestamp.setText(DateFormat.getDateTimeInstance().format(new Date(record.timestamp)));
            tvStatus.setText(formatStatus(record.syncStatus));

            String currentUserId = FirebaseAuth.getInstance().getUid();
            boolean isAuthor = currentUserId != null && currentUserId.equals(record.authorId);

            if (isAuthor) {
                layoutActions.setVisibility(View.VISIBLE);
                btnSyncNow.setVisibility(!PatrolLogStore.STATUS_SYNCED.equals(record.syncStatus) 
                        && !appSettingsManager.isAutoSyncEnabled() ? View.VISIBLE : View.GONE);
                
                btnSyncNow.setOnClickListener(v -> actionListener.onSyncNow(record));
                btnDelete.setOnClickListener(v -> actionListener.onDelete(record));
                btnEdit.setOnClickListener(v -> actionListener.onEdit(record));
            } else {
                layoutActions.setVisibility(View.GONE);
            }
        }

        private String formatStatus(String status) {
            if (PatrolLogStore.STATUS_SYNCED.equals(status)) return "Synced";
            if (PatrolLogStore.STATUS_FAILED.equals(status)) return "Sync Failed";
            return "Not Synced";
        }
    }
}
