package me.him188.ani.app.torrent.torrent4j

import me.him188.ani.app.torrent.api.BlockDownloadingEvent
import me.him188.ani.app.torrent.api.EventListener
import me.him188.ani.app.torrent.api.PieceFinishedEvent
import me.him188.ani.app.torrent.api.StatsUpdateEvent
import me.him188.ani.app.torrent.api.TorrentAddEvent
import me.him188.ani.app.torrent.api.TorrentEvent
import me.him188.ani.app.torrent.api.TorrentFinishedEvent
import me.him188.ani.app.torrent.api.TorrentResumeEvent
import me.him188.ani.app.torrent.api.asAniTorrentHandle
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.PieceFinishedAlert
import org.libtorrent4j.alerts.TorrentAlert
import org.libtorrent4j.alerts.TorrentResumedAlert


// Typical event sequence:
/*
Alert: LISTEN_SUCCEEDED
Alert: LSD_ERROR
Alert: ADD_TORRENT
Alert: STATE_CHANGED
Alert: TORRENT_CHECKED
Alert: TORRENT_RESUMED
Alert: TRACKER_ANNOUNCE
Alert: TRACKER_ERROR
Alert: EXTERNAL_IP
Alert: PORTMAP
Alert: TRACKER_REPLY
Alert: PEER_CONNECT
Alert: DHT_GET_PEERS
Alert: BLOCK_DOWNLOADING
Alert: BLOCK_FINISHED
 */
//        override fun types(): IntArray? = null
//        override fun types(): IntArray = intArrayOf(
//            AlertType.ADD_TORRENT.swig(),
//            AlertType.PEER_CONNECT.swig(),
//            AlertType.PEER_DISCONNECTED.swig(),
//            AlertType.BLOCK_DOWNLOADING.swig(),
//            AlertType.PIECE_FINISHED.swig(),
//            AlertType.BLOCK_FINISHED.swig(),
//            AlertType.TORRENT_FINISHED.swig(),
//        )

internal fun EventListener.onAlert(
    alert: TorrentAlert<*>
) {
    val listener = this
    if (listener.torrentName != alert.torrentName()) {
        return
    }
    alert.toEventOrNull()?.let { event ->
        listener.onEvent(event)
    }
    listener.onEvent(alert.createStatsUpdateEvent())
    listener.onUpdate(alert.handle().asAniTorrentHandle())
}

internal fun TorrentAlert<*>.toEventOrNull(): TorrentEvent? {
    return when (this) {
        is AddTorrentAlert -> TorrentAddEvent(handle().asAniTorrentHandle())
        is TorrentResumedAlert -> TorrentResumeEvent(torrentName())
        is org.libtorrent4j.alerts.BlockDownloadingAlert -> BlockDownloadingEvent(torrentName(), pieceIndex())
        is PieceFinishedAlert -> PieceFinishedEvent(torrentName(), pieceIndex())
        is org.libtorrent4j.alerts.TorrentFinishedAlert -> TorrentFinishedEvent(torrentName())
        else -> null
    }
}

internal fun TorrentAlert<*>.createStatsUpdateEvent(): StatsUpdateEvent {
    val max = this.handle().status().totalDone()
    val curr = this.handle().status().totalWanted()
    return StatsUpdateEvent(
        torrentName = torrentName(),
        totalBytes = max,
        downloadedBytes = curr,
        uploadRate = this.handle().status().uploadRate().toUInt().toLong(),
        downloadRate = this.handle().status().downloadRate().toUInt().toLong()
    )
}