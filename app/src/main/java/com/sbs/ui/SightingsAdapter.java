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
import com.sbs.data.SightingRecord;
import com.sbs.data.SightingStore;
import com.sbs.data.SightingSyncManager;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SightingsAdapter extends RecyclerView.Adapter<SightingsAdapter.SightingViewHolder> {

    private final List<SightingRecord> sightings = new ArrayList<>();
    private final AppSettingsManager appSettingsManager;
    private final SightingActionListener actionListener;

    public interface SightingActionListener {
        void onSyncNow(SightingRecord record);
        void onDelete(SightingRecord record);
        void onEdit(SightingRecord record);
    }

    public SightingsAdapter(AppSettingsManager appSettingsManager, SightingActionListener actionListener) {
        this.appSettingsManager = appSettingsManager;
        this.actionListener = actionListener;
    }

    public void submitList(List<SightingRecord> records) {
        sightings.clear();
        if (records != null) sightings.addAll(records);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SightingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_sighting_item, parent, false);
        return new SightingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SightingViewHolder holder, int position) {
        holder.bind(sightings.get(position));
    }

    @Override
    public int getItemCount() { return sightings.size(); }

    class SightingViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle, tvAuthor, tvCoords, tvNotes, tvTimestamp, tvStatus;
        private final View layoutActions;
        private final Button btnSyncNow, btnDelete, btnEdit;

        SightingViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSightingTitle);
            tvAuthor = itemView.findViewById(R.id.tvSightingAuthor);
            tvCoords = itemView.findViewById(R.id.tvSightingCoords);
            tvNotes = itemView.findViewById(R.id.tvSightingNotes);
            tvTimestamp = itemView.findViewById(R.id.tvSightingTimestamp);
            tvStatus = itemView.findViewById(R.id.tvSightingStatus);
            layoutActions = itemView.findViewById(R.id.layoutActions);
            btnSyncNow = itemView.findViewById(R.id.btnSyncNow);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnEdit = itemView.findViewById(R.id.btnEdit);
        }

        void bind(SightingRecord record) {
            tvTitle.setText(record.title);
            tvAuthor.setText("By: " + (record.authorName != null ? record.authorName : "Unknown"));
            tvCoords.setText(String.format(Locale.US, "Lat %.5f, Lng %.5f (Radius: %.1fm)", record.lat, record.lng, record.radius));
            tvNotes.setText(record.notes == null || record.notes.isEmpty() ? "No notes" : record.notes);
            tvTimestamp.setText(DateFormat.getDateTimeInstance().format(new Date(record.timestamp)));
            tvStatus.setText(formatStatus(record.syncStatus));

            String currentUserId = FirebaseAuth.getInstance().getUid();
            boolean isAuthor = currentUserId != null && currentUserId.equals(record.authorId);

            if (isAuthor) {
                layoutActions.setVisibility(View.VISIBLE);
                btnSyncNow.setVisibility(!SightingStore.STATUS_SYNCED.equals(record.syncStatus) 
                        && !appSettingsManager.isAutoSyncEnabled() ? View.VISIBLE : View.GONE);
                
                btnSyncNow.setOnClickListener(v -> actionListener.onSyncNow(record));
                btnDelete.setOnClickListener(v -> actionListener.onDelete(record));
                btnEdit.setOnClickListener(v -> actionListener.onEdit(record));
            } else {
                layoutActions.setVisibility(View.GONE);
            }
        }

        private String formatStatus(String status) {
            if (SightingStore.STATUS_SYNCED.equals(status)) return "Synced";
            if (SightingStore.STATUS_FAILED.equals(status)) return "Sync Failed";
            return "Not Synced";
        }
    }
}
