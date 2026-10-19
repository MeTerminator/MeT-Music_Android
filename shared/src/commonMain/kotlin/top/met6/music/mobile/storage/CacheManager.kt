package top.met6.music.mobile.storage

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.call.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.met6.music.mobile.api.httpClient
import top.met6.music.mobile.models.Playlist
import top.met6.music.mobile.models.Song
import top.met6.music.mobile.models.LyricResponse

class CacheManager(private val storage: PlatformStorage) {
    private val downloadScope = CoroutineScope(Dispatchers.Default)
    private val activeDownloadsMutex = Mutex()
    private val activeDownloads = mutableMapOf<String, Deferred<String?>>()

    private val remoteUrlCache = mutableMapOf<String, Pair<String, Long>>()
    private val remoteUrlMutex = Mutex()

    suspend fun getCachedRemoteUrl(songId: String, quality: String): String? {
        val key = "$songId-$quality"
        remoteUrlMutex.withLock {
            val entry = remoteUrlCache[key] ?: return null
            val now = getCurrentTimeMs()
            // 1.5 hours = 5400000 ms
            if (now - entry.second < 5400000L) {
                return entry.first
            } else {
                remoteUrlCache.remove(key)
                return null
            }
        }
    }

    suspend fun saveRemoteUrlToCache(songId: String, quality: String, url: String) {
        val key = "$songId-$quality"
        remoteUrlMutex.withLock {
            remoteUrlCache[key] = Pair(url, getCurrentTimeMs())
        }
    }

    suspend fun saveSongMetadata(song: Song, quality: String) {
        try {
            val meta = CachedSongMetadata(song, quality, getCurrentTimeMs())
            val json = Json.encodeToString(meta)
            storage.saveCacheFile("songs_metadata", song.audioKey(quality), json.encodeToByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getSongMetadata(songId: String): CachedSongMetadata? {
        val bytes = storage.getCacheFile("songs_metadata", songId) ?: return null
        val str = bytes.decodeToString()
        return try {
            Json.decodeFromString<CachedSongMetadata>(str)
        } catch (e: Exception) {
            try {
                val song = Json.decodeFromString<Song>(str)
                CachedSongMetadata(song, "unknown", 0L)
            } catch (ex: Exception) {
                null
            }
        }
    }

    suspend fun getCachedSongs(): List<CachedSongEntry> {
        val songIds = storage.listCacheFiles("songs")
        val result = mutableListOf<CachedSongEntry>()
        for (songId in songIds) {
            val size = storage.getCacheFileSize("songs", songId)
            val meta = getSongMetadata(songId.removeSuffix(".mp3"))
            val song = meta?.song ?: Song(id = songId, name = "未知歌曲 (ID: $songId)")
            val quality = meta?.quality ?: "unknown"
            result.add(CachedSongEntry(song, quality, size, songId))
        }
        return result
    }

    suspend fun deleteCachedSong(songId: String): Boolean {
        val successSong = storage.deleteCacheFile("songs", songId)
        // Also clean up metadata
        storage.deleteCacheFile("songs_metadata", songId.removeSuffix(".mp3"))
        return successSong
    }

    suspend fun savePlaylistsToCache(uid: String, playlists: List<Playlist>) {
        try {
            val json = Json.encodeToString(playlists)
            storage.saveCacheFile("playlists", "playlists_$uid.json", json.encodeToByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getPlaylistsFromCache(uid: String): List<Playlist>? {
        val bytes = storage.getCacheFile("playlists", "playlists_$uid.json") ?: return null
        return try {
            val json = bytes.decodeToString()
            Json.decodeFromString<List<Playlist>>(json)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun savePlaylistTracksToCache(playlistId: String, songs: List<Song>) {
        try {
            val json = Json.encodeToString(songs)
            storage.saveCacheFile("playlists", "tracks_$playlistId.json", json.encodeToByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getPlaylistTracksFromCache(playlistId: String): List<Song>? {
        val bytes = storage.getCacheFile("playlists", "tracks_$playlistId.json") ?: return null
        return try {
            val json = bytes.decodeToString()
            Json.decodeFromString<List<Song>>(json)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCoverImageUrl(songId: String, remoteUrl: String): String {
        if (remoteUrl.isEmpty()) return ""
        val cachedUrl = storage.getCacheUrl("covers", songId)
        if (cachedUrl != null) {
            return cachedUrl
        }
        
        try {
            val response: HttpResponse = httpClient.get(remoteUrl)
            val bytes = response.body<ByteArray>()
            storage.saveCacheFile("covers", songId, bytes)
            return storage.getCacheUrl("covers", songId) ?: remoteUrl
        } catch (e: Exception) {
            e.printStackTrace()
            return remoteUrl
        }
    }

    suspend fun getCoverImageBytes(songId: String, remoteUrl: String): ByteArray? {
        if (remoteUrl.isEmpty()) return null
        val secureUrl = if (remoteUrl.startsWith("http://")) {
            remoteUrl.replace("http://", "https://")
        } else {
            remoteUrl
        }
        val cachedBytes = storage.getCacheFile("covers", songId)
        if (cachedBytes != null) {
            return cachedBytes
        }
        try {
            val response: HttpResponse = httpClient.get(secureUrl)
            val bytes = response.body<ByteArray>()
            storage.saveCacheFile("covers", songId, bytes)
            return bytes
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun getCachedSongUrl(songId: String): String? {
        return storage.getCacheUrl("songs", "$songId.mp3")
    }

    /**
     * Returns a source according to the playback mode.
     *
     * Standalone playback uses the remote URL immediately and only caches in the
     * background. Listen Together waits for the shared download and returns a local
     * file so every participant seeks against a fully indexed asset.
     */
    suspend fun getSongAudioUrl(
        songId: String,
        remoteUrl: String,
        waitForCache: Boolean,
    ): String {
        if (remoteUrl.isEmpty()) return ""
        if (!waitForCache) {
            // Streaming must never wait for disk or an existing download. The remote
            // URL remains the playback source even when a cached copy already exists.
            downloadScope.launch { cacheSong(songId, remoteUrl) }
            return remoteUrl
        }

        return cacheSong(songId, remoteUrl)
            ?: error("Unable to cache song $songId before synchronized playback")
    }

    private suspend fun cacheSong(songId: String, remoteUrl: String): String? {
        storage.getCacheUrl("songs", "$songId.mp3")?.let { return it }

        val download = activeDownloadsMutex.withLock {
            activeDownloads[songId] ?: downloadScope.async {
                try {
                    val response: HttpResponse = httpClient.get(remoteUrl)
                    val bytes = response.body<ByteArray>()
                    storage.saveCacheFile("songs", "$songId.mp3", bytes)
                    storage.getCacheUrl("songs", "$songId.mp3")
                } catch (error: Exception) {
                    error.printStackTrace()
                    null
                }
            }.also { activeDownloads[songId] = it }
        }

        return try {
            download.await()
        } finally {
            activeDownloadsMutex.withLock {
                if (activeDownloads[songId] === download) activeDownloads.remove(songId)
            }
        }
    }

    suspend fun saveLyricToCache(songId: String, lyric: LyricResponse) {
        try {
            val json = Json.encodeToString(lyric)
            storage.saveCacheFile("lyrics", "$songId.json", json.encodeToByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getLyricFromCache(songId: String): LyricResponse? {
        try {
            val bytes = storage.getCacheFile("lyrics", "$songId.json") ?: return null
            val json = bytes.decodeToString()
            return Json.decodeFromString<LyricResponse>(json)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun getCacheSizeMb(category: String): Double {
        val bytes = storage.getCacheSize(category)
        return bytes.toDouble() / (1024.0 * 1024.0)
    }

    suspend fun clearCacheCategory(category: String) {
        storage.clearCache(category)
    }
}

@Serializable
data class CachedSongMetadata(
    val song: Song,
    val quality: String,
    val cachedTimeMs: Long
)

data class CachedSongEntry(val song: Song, val quality: String, val size: Long, val cacheFile: String)
