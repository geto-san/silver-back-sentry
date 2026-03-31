package com.sbs.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.sbs.R;
import com.sbs.data.AppNotificationRecord;
import com.sbs.data.AppRepository;
import com.sbs.data.RealtimeSyncManager;

public final class NotificationsActivity extends BaseActivity implements NotificationsAdapter.NotificationClickListener {

    private AppRepository repository;
    private String userId;
    private NotificationsAdapter adapter;
    private View progress;
    private View state;
    private View retry;
    private View markAllRead;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);
        applyWindowInsets(findViewById(R.id.toolbar).getRootView());

        repository = AppRepository.getInstance(this);
        userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        progress = findViewById(R.id.progressNotifications);
        state = findViewById(R.id.tvNotificationsState);
        retry = findViewById(R.id.btnRetryNotifications);
        markAllRead = findViewById(R.id.btnMarkAllRead);

        adapter = new NotificationsAdapter(this);
        androidx.recyclerview.widget.RecyclerView recycler = findViewById(R.id.recyclerNotifications);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        retry.setOnClickListener(v -> RealtimeSyncManager.getInstance(this).start());
        markAllRead.setOnClickListener(v -> repository.markAllNotificationsRead(userId));

        progress.setVisibility(View.VISIBLE);
        state.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);

        repository.observeNotifications(userId).observe(this, records -> {
            progress.setVisibility(View.GONE);
            adapter.submitList(records);
            boolean empty = records == null || records.isEmpty();
            state.setVisibility(empty ? View.VISIBLE : View.GONE);
            ((android.widget.TextView) state).setText(empty ? R.string.no_notifications_yet : R.string.notifications_error);
            markAllRead.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    @Override
    public void onOpen(AppNotificationRecord record) {
        repository.markNotificationRead(userId, record.notificationId);
        Intent intent = new Intent(this, RecordDetailActivity.class);
        intent.putExtra("record_id", record.recordId);
        intent.putExtra("record_type", record.recordType);
        startActivity(intent);
    }
}
