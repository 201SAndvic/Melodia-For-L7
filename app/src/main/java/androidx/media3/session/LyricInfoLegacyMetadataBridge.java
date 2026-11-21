package androidx.media3.session;

import android.os.Bundle;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.legacy.MediaMetadataCompat;
import androidx.media3.session.legacy.MediaSessionCompat;
import java.util.Objects;

/**
 * Narrow, version-pinned workaround for Media3 1.11.1's legacy metadata deduplication:
 * MediaMetadata.equals compares extras presence, not its contents. The existing
 * legacy adapter therefore drops same-track lyricInfo updates.
 *
 * Package placement permits compile-time access to the library's package-private
 * legacy stub/session access, with no reflection, fabricated song fields, extra sessions,
 * playlist mutation, audio restart or repeated polling. Re-evaluate when upgrading
 * Media3; keep this bridge isolated rather than forking its session implementation.
 */
@UnstableApi
public final class LyricInfoLegacyMetadataBridge {
    private LyricInfoLegacyMetadataBridge() {}

    public static boolean refresh(MediaSession session) {
        Player player = session.getPlayer();
        MediaItem item = player.getCurrentMediaItem();
        if (item == null) return false;
        MediaMetadata source = player.getMediaMetadata();
        Bundle extras = source.extras;
        String lyric = extras == null ? null : extras.getString("lyricInfo");
        MediaSessionCompat compat = session.getImpl().getMediaSessionLegacyStub().getSessionCompat();
        MediaMetadataCompat existing = compat.getController().getMetadata();
        // Do not copy the previous song's art/title into a newly switched track.
        // If the normal adapter has not published that track yet, build its snapshot
        // through Media3's own converter instead of reimplementing the metadata map.
        if (existing == null || !Objects.equals(item.mediaId,
                existing.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID))) {
            if (lyric == null) return false;
            existing = LegacyConversions.convertToMediaMetadataCompat(
                    source, item.mediaId,
                    item.localConfiguration == null ? null : item.localConfiguration.uri,
                    player.isCurrentMediaItemLive() ? androidx.media3.common.C.TIME_UNSET : player.getDuration(),
                    null);
        } else if (Objects.equals(lyric, existing.getString("lyricInfo"))) {
            return false;
        }
        // Copy preserves all already-published artwork, duration, title and artist.
        // Media3 1.11 removed the compat Builder copy constructor. Copy via the
        // public framework builder, retaining every published key and cached artwork.
        MediaMetadataCompat updated = Objects.requireNonNull(MediaMetadataCompat.fromMediaMetadata(
                new android.media.MediaMetadata.Builder(existing.getMediaMetadata())
                        .putString("lyricInfo", lyric).build()));
        updated.preserveArtworkBitmapData(existing);
        compat.setMetadata(updated);
        return true;
    }
}
