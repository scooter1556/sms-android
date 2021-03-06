package com.scooter1556.sms.android.fragment.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;
import androidx.core.content.ContextCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.scooter1556.sms.android.R;
import com.scooter1556.sms.android.activity.tv.TvDirectoryDetailsActivity;
import com.scooter1556.sms.android.activity.tv.TvMediaGridActivity;
import com.scooter1556.sms.android.presenter.MediaItemPresenter;
import com.scooter1556.sms.android.service.MediaService;
import com.scooter1556.sms.android.service.RESTService;
import com.scooter1556.sms.android.utils.MediaUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class TvMediaFolderFragment extends TvGridFragment {
    private static final String TAG = "TvMediaFolderFragment";

    private static final int BACKGROUND_UPDATE_DELAY = 300;
    private static final int NUM_COLUMNS = 5;

    private ArrayObjectAdapter adapter;
    private String mediaId;
    private List<MediaBrowserCompat.MediaItem> mediaItems;
    private MediaBrowserCompat mediaBrowser;

    // Background
    private final Handler handler = new Handler();
    private Drawable defaultBackground;
    private Timer backgroundTimer;
    private String backgroundURI;

    private final MediaBrowserCompat.SubscriptionCallback subscriptionCallback =
            new MediaBrowserCompat.SubscriptionCallback() {
                @Override
                public void onChildrenLoaded(@NonNull String parentId,
                                             @NonNull List<MediaBrowserCompat.MediaItem> children) {
                    Log.d(TAG, "onChildrenLoaded() parentId=" + parentId);

                    if(children.isEmpty()) {
                        Log.d(TAG, "Result for " + parentId + " is empty");
                        return;
                    }

                    mediaItems.clear();
                    mediaItems.addAll(children);

                    setGrid();
                }

                @Override
                public void onError(@NonNull String id) {
                    Log.e(TAG, "Media subscription error: " + id);
                }
            };

    private MediaBrowserCompat.ConnectionCallback connectionCallback =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    Log.d(TAG, "onConnected()");

                    if (isDetached()) {
                        return;
                    }

                    // Subscribe to media browser event
                    if(mediaId != null) {
                        mediaBrowser.subscribe(mediaId, subscriptionCallback);
                    }
                }

                @Override
                public void onConnectionFailed() {
                    Log.d(TAG, "onConnectionFailed");
                }

                @Override
                public void onConnectionSuspended() {
                    Log.d(TAG, "onConnectionSuspended");
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String id = null;

        // Set media folder
        Bundle bundle = this.getArguments();

        if (bundle != null) {
            id = bundle.getString(MediaUtils.EXTRA_MEDIA_ID, null);
        }

        Log.d(TAG, id == null ? "NOT SET" : id);

        if(id != null) {
            mediaId = MediaUtils.MEDIA_ID_FOLDER + MediaUtils.SEPARATOR + id;
        }

        // Initialise variables
        mediaItems = new ArrayList<>();
        adapter = new ArrayObjectAdapter(new MediaItemPresenter());

        // Get default background
        defaultBackground = ContextCompat.getDrawable(getActivity(), R.drawable.default_background);

        // Initialise interface
        VerticalGridPresenter gridPresenter = new VerticalGridPresenter();
        gridPresenter.setNumberOfColumns(NUM_COLUMNS);
        setGridPresenter(gridPresenter);

        setOnItemViewClickedListener(new TvMediaFolderFragment.ItemViewClickedListener());
        setOnItemViewSelectedListener(new TvMediaFolderFragment.ItemViewSelectedListener());

        getMainFragmentAdapter().getFragmentHost().notifyDataReady(getMainFragmentAdapter());

        // Subscribe to relevant media service callbacks
        mediaBrowser = new MediaBrowserCompat(getActivity(),
                new ComponentName(getActivity(), MediaService.class),
                connectionCallback, null);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if (backgroundTimer != null) {
            backgroundTimer.cancel();
            backgroundTimer = null;
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        mediaBrowser.connect();
    }

    @Override
    public void onStop() {
        mediaBrowser.disconnect();

        super.onStop();
    }

    private void setGrid() {
        adapter.clear();

        if(!mediaItems.isEmpty()) {
            // Add media elements to grid
            for (MediaBrowserCompat.MediaItem item : mediaItems) {
                adapter.add(item);
            }
        }

        setAdapter(adapter);
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof MediaBrowserCompat.MediaItem) {
                MediaBrowserCompat.MediaItem mediaItem = (MediaBrowserCompat.MediaItem) item;

                if (mediaItem.isPlayable()) {
                    MediaControllerCompat.getMediaController(getActivity()).getTransportControls().prepareFromMediaId(mediaItem.getMediaId(), null);
                } else if (mediaItem.isBrowsable()) {
                    Intent intent = null;

                    switch (MediaUtils.parseMediaId(mediaItem.getMediaId()).get(0)) {
                        case MediaUtils.MEDIA_ID_FOLDER:
                        case MediaUtils.MEDIA_ID_DIRECTORY:
                            intent = new Intent(getActivity(), TvMediaGridActivity.class);
                            break;

                        case MediaUtils.MEDIA_ID_DIRECTORY_AUDIO:
                        case MediaUtils.MEDIA_ID_DIRECTORY_VIDEO:
                            intent = new Intent(getActivity(), TvDirectoryDetailsActivity.class);
                            break;
                    }

                    if (intent != null) {
                        intent.putExtra(MediaUtils.EXTRA_MEDIA_ID, mediaItem.getMediaId());
                        intent.putExtra(MediaUtils.EXTRA_MEDIA_ITEM, mediaItem);
                        intent.putExtra(MediaUtils.EXTRA_MEDIA_TITLE, mediaItem.getDescription().getTitle());
                        getActivity().startActivity(intent);
                    }
                } else {
                    Log.w(TAG, "Ignoring MediaItem that is neither browsable nor playable: mediaID=" + mediaItem.getMediaId());
                }
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof MediaBrowserCompat.MediaItem) {
                MediaBrowserCompat.MediaItem mediaItem = (MediaBrowserCompat.MediaItem) item;
                List<String> id = MediaUtils.parseMediaId(mediaItem.getMediaId());

                if(id.size() > 1) {
                    backgroundURI = RESTService.getInstance().getConnection().getUrl() + "/image/" + id.get(1) + "/fanart?scale=" + getResources().getDisplayMetrics().widthPixels;
                    startBackgroundTimer();
                }
            }
        }
    }

    //
    // Background
    //
    private void updateBackground() {
        if(getActivity() == null) {
            return;
        }

        final Activity activity = getActivity();

        // Get screen size
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;

        Glide.with(getActivity())
                .asBitmap()
                .load(backgroundURI)
                .into(new SimpleTarget<Bitmap>(width, height) {
                    @Override
                    public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                        BackgroundManager.getInstance(activity).setBitmap(resource);
                    }

                    @Override public void onLoadFailed(Drawable errorDrawable) {
                        BackgroundManager.getInstance(activity).setDrawable(defaultBackground);
                    }
                });

        backgroundTimer.cancel();
    }

    private void startBackgroundTimer() {
        if (backgroundTimer != null) {
            backgroundTimer.cancel();
        }

        backgroundTimer = new Timer();
        backgroundTimer.schedule(new TvMediaFolderFragment.UpdateBackgroundTask(), BACKGROUND_UPDATE_DELAY);
    }

    private class UpdateBackgroundTask extends TimerTask {

        @Override
        public void run() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (backgroundURI != null) {
                        updateBackground();
                    }
                }
            });
        }
    }
}
