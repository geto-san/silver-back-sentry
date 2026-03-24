package com.sbs.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sbs.R;
import com.sbs.data.SightingRecord;
import com.sbs.data.SightingStore;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SightingsAdapter extends RecyclerView.Adapter<SightingsAdapter.SightingViewHolder> {

    private final List<SightingRecord> sightings = new ArrayList<>();

    public void submitList(List<SightingRecord> records) {
        sightings.clear();
        if (records != null) {
            sightings.addAll(records);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SightingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_sighting_item, parent, false);
        return new SightingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SightingViewHolder holder, int position) {
        holder.bind(sightings.get(position));
    }

    @Override
    public int getItemCount() {
        return sightings.size();
    }

    static class SightingViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvCoords;
        private final TextView tvNotes;
        private final TextView tvTimestamp;
        private final TextView tvStatus;

        SightingViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSightingTitle);
            tvCoords = itemView.findViewById(R.id.tvSightingCoords);
            tvNotes = itemView.findViewById(R.id.tvSightingNotes);
            tvTimestamp = itemView.findViewById(R.id.tvSightingTimestamp);
            tvStatus = itemView.findViewById(R.id.tvSightingStatus);
        }

        void bind(SightingRecord record) {
            tvTitle.setText(record.title);
            tvCoords.setText(String.format(Locale.US, "Lat %.5f, Lng %.5f", record.lat, record.lng));
            tvNotes.setText(record.notes == null || record.notes.isEmpty()
                    ? itemView.getContext().getString(R.string.no_notes)
                    : record.notes);
            String formattedDate = DateFormat.getDateTimeInstance().format(new Date(record.timestamp));
            tvTimestamp.setText(formattedDate);
            tvStatus.setText(formatStatus(record.syncStatus));
        }

        private String formatStatus(String status) {
            if (SightingStore.STATUS_SYNCED.equals(status)) {
                return itemView.getContext().getString(R.string.synced);
            }
            if (SightingStore.STATUS_FAILED.equals(status)) {
                return itemView.getContext().getString(R.string.sync_failed);
            }
            return itemView.getContext().getString(R.string.pending_sync);
        }
    }
}
