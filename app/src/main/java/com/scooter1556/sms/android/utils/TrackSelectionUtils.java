package com.scooter1556.sms.android.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.RandomTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.util.MimeTypes;
import com.scooter1556.sms.android.R;

import java.util.Arrays;
import java.util.Locale;

/**
 * Utility class for displaying track selection dialogs.
 */

public final class TrackSelectionUtils implements View.OnClickListener, DialogInterface.OnClickListener {
    private final Context ctx;
    private final DefaultTrackSelector selector;

    private MappedTrackInfo trackInfo;
    private int rendererIndex;
    private TrackGroupArray trackGroups;
    private boolean[] trackGroupsAdaptive;
    private boolean isDisabled;
    private SelectionOverride override;

    private CheckedTextView disableView;
    private CheckedTextView defaultView;
    private CheckedTextView[][] trackViews;

    /**
     * @param selector The track selector.
     */
    public TrackSelectionUtils(Context ctx, DefaultTrackSelector selector) {
        this.ctx = ctx;
        this.selector = selector;
    }

    /**
     * Shows the selection dialog for a given renderer.
     *
     * @param activity The parent activity.
     * @param trackInfo The current track information.
     * @param rendererIndex The index of the renderer.
     */
    public void showSelectionDialog(Activity activity, MappedTrackInfo trackInfo, int rendererIndex) {
        this.trackInfo = trackInfo;
        this.rendererIndex = rendererIndex;

        trackGroups = trackInfo.getTrackGroups(rendererIndex);
        trackGroupsAdaptive = new boolean[trackGroups.length];
        for (int i = 0; i < trackGroups.length; i++) {
            trackGroupsAdaptive[i] = trackInfo.getAdaptiveSupport(rendererIndex, i, false)
                    != RendererCapabilities.ADAPTIVE_NOT_SUPPORTED
                    && trackGroups.get(i).length > 1;
        }
        isDisabled = selector.getParameters().getRendererDisabled(rendererIndex);
        override = selector.getParameters().getSelectionOverride(rendererIndex, trackGroups);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder
                .setView(buildView(builder.getContext()))
                .setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }

    @SuppressLint("InflateParams")
    private View buildView(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.dialog_tv_track_selection, null);
        ViewGroup root = (ViewGroup) view.findViewById(R.id.root);

        TypedArray attributeArray = context.getTheme().obtainStyledAttributes(
                new int[] {android.R.attr.selectableItemBackground});
        int selectableItemBackgroundResourceId = attributeArray.getResourceId(0, 0);
        attributeArray.recycle();

        // View for disabling the renderer.
        disableView = (CheckedTextView) inflater.inflate(
                android.R.layout.simple_list_item_single_choice, root, false);
        disableView.setBackgroundResource(selectableItemBackgroundResourceId);
        disableView.setText(R.string.selection_disabled);
        disableView.setFocusable(true);
        disableView.setOnClickListener(this);
        root.addView(disableView);

        // View for clearing the override to allow the selector to use its default selection logic.
        defaultView = (CheckedTextView) inflater.inflate(
                android.R.layout.simple_list_item_single_choice, root, false);
        defaultView.setBackgroundResource(selectableItemBackgroundResourceId);
        defaultView.setText(R.string.selection_default);
        defaultView.setFocusable(true);
        defaultView.setOnClickListener(this);
        root.addView(inflater.inflate(R.layout.list_divider, root, false));
        root.addView(defaultView);

        // Per-track views.
        boolean haveSupportedTracks = false;
        trackViews = new CheckedTextView[trackGroups.length][];
        for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
            TrackGroup group = trackGroups.get(groupIndex);
            boolean groupIsAdaptive = trackGroupsAdaptive[groupIndex];
            trackViews[groupIndex] = new CheckedTextView[group.length];
            for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                if (trackIndex == 0) {
                    root.addView(inflater.inflate(R.layout.list_divider, root, false));
                }
                int trackViewLayoutId = groupIsAdaptive ? android.R.layout.simple_list_item_multiple_choice
                        : android.R.layout.simple_list_item_single_choice;
                CheckedTextView trackView = (CheckedTextView) inflater.inflate(
                        trackViewLayoutId, root, false);
                trackView.setBackgroundResource(selectableItemBackgroundResourceId);
                trackView.setText(buildTrackName(group.getFormat(trackIndex)));
                if (trackInfo.getTrackFormatSupport(rendererIndex, groupIndex, trackIndex)
                        == RendererCapabilities.FORMAT_HANDLED) {
                    trackView.setFocusable(true);
                    trackView.setTag(Pair.create(groupIndex, trackIndex));
                    trackView.setOnClickListener(this);
                    haveSupportedTracks = true;
                } else {
                    trackView.setFocusable(false);
                    trackView.setEnabled(false);
                }
                trackViews[groupIndex][trackIndex] = trackView;
                root.addView(trackView);
            }
        }

        if (!haveSupportedTracks) {
            // Indicate that the default selection will be nothing.
            defaultView.setText(R.string.selection_default_none);
        }

        updateViews();
        return view;
    }

    private void updateViews() {
        disableView.setChecked(isDisabled);
        defaultView.setChecked(!isDisabled && override == null);
        for (int i = 0; i < trackViews.length; i++) {
            for (int j = 0; j < trackViews[i].length; j++) {
                trackViews[i][j].setChecked(override != null && override.groupIndex == i
                        && override.containsTrack(j));
            }
        }
    }

    // DialogInterface.OnClickListener

    @Override
    public void onClick(DialogInterface dialog, int which) {
        DefaultTrackSelector.ParametersBuilder paramsBuilder = new DefaultTrackSelector.ParametersBuilder(ctx);

        paramsBuilder.setRendererDisabled(rendererIndex, isDisabled);
        if (override != null) {
            paramsBuilder.setSelectionOverride(rendererIndex, trackGroups, override);
        } else {
            paramsBuilder.clearSelectionOverrides(rendererIndex);
        }

        selector.setParameters(paramsBuilder.build());
    }

    // View.OnClickListener

    @Override
    public void onClick(View view) {
        if (view == disableView) {
            isDisabled = true;
            override = null;
        } else if (view == defaultView) {
            isDisabled = false;
            override = null;
        } else {
            isDisabled = false;
            @SuppressWarnings("unchecked")
            Pair<Integer, Integer> tag = (Pair<Integer, Integer>) view.getTag();
            int groupIndex = tag.first;
            int trackIndex = tag.second;
            if (!trackGroupsAdaptive[groupIndex] || override == null
                    || override.groupIndex != groupIndex) {
                override = new SelectionOverride(groupIndex, trackIndex);
            } else {
                // The group being modified is adaptive and we already have a non-null override.
                boolean isEnabled = ((CheckedTextView) view).isChecked();
                int overrideLength = override.length;
                if (isEnabled) {
                    // Remove the track from the override.
                    if (overrideLength == 1) {
                        // The last track is being removed, so the override becomes empty.
                        override = null;
                        isDisabled = true;
                    } else {
                        setOverride(groupIndex, getTracksRemoving(override, trackIndex));
                    }
                } else {
                    // Add the track to the override.
                    setOverride(groupIndex, getTracksAdding(override, trackIndex));
                }
            }
        }
        // Update the views with the new state.
        updateViews();
    }

    private void setOverride(int group, int[] tracks) {
        override = new SelectionOverride(group, tracks);
    }

    // Track array manipulation.

    private static int[] getTracksAdding(SelectionOverride override, int addedTrack) {
        int[] tracks = override.tracks;
        tracks = Arrays.copyOf(tracks, tracks.length + 1);
        tracks[tracks.length - 1] = addedTrack;
        return tracks;
    }

    private static int[] getTracksRemoving(SelectionOverride override, int removedTrack) {
        int[] tracks = new int[override.length - 1];
        int trackCount = 0;
        for (int i = 0; i < tracks.length + 1; i++) {
            int track = override.tracks[i];
            if (track != removedTrack) {
                tracks[trackCount++] = track;
            }
        }
        return tracks;
    }

    /**
     * Builds a track name for display.
     *
     * @param format {@link Format} of the track.
     * @return a generated name specific to the track.
     */
    private static String buildTrackName(Format format) {
        String trackName;

        if (MimeTypes.isVideo(format.sampleMimeType)) {
            trackName = buildResolutionString(format) + " (" + buildBitrateString(format) + ")";
        } else if (MimeTypes.isAudio(format.sampleMimeType)) {
            trackName = buildTrackIdString(format) + " (" + buildLanguageString(format) + ")";
        } else {
            trackName = buildTrackIdString(format) + " (" + buildLanguageString(format) + ")";
        }

        return trackName.length() == 0 ? "unknown" : trackName;
    }

    private static String buildResolutionString(Format format) {
        return format.width == Format.NO_VALUE || format.height == Format.NO_VALUE
                ? "" : format.width + "x" + format.height;
    }

    private static String buildAudioChannelString(Format format) {
        return format.channelCount == Format.NO_VALUE || format.sampleRate == Format.NO_VALUE
                ? "" : format.channelCount + "ch, " + format.sampleRate + "Hz";
    }

    private static String buildLanguageString(Format format) {
        return TextUtils.isEmpty(format.language) || "und".equals(format.language) ? ""
                : format.language;
    }

    private static String buildBitrateString(Format format) {
        return format.bitrate == Format.NO_VALUE ? ""
                : String.format(Locale.UK, "%.2fMbit", format.bitrate / 1000000f);
    }

    private static String buildTrackIdString(Format format) {
        return format.id == null ? "" : format.id;
    }

    private static String buildSampleMimeTypeString(Format format) {
        return format.sampleMimeType == null ? "" : format.sampleMimeType;
    }

}