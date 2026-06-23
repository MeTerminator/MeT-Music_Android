package top.met6.music.mobile.storage

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.call.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val activeDownloads = mutableSetOf<String>()

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
            storage.saveCacheFile("songs_metadata", song.id, json.encodeToByteArray())
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

    suspend fun getCachedSongs(): List<Triple<Song, String, Long>> {
        val songIds = storage.listCacheFiles("songs")
        val result = mutableListOf<Triple<Song, String, Long>>()
        for (songId in songIds) {
            val size = storage.getCacheFileSize("songs", songId)
            val meta = getSongMetadata(songId)
            val song = meta?.song ?: Song(id = songId, name = "未知歌曲 (ID: $songId)")
            val quality = meta?.quality ?: "unknown"
            result.add(Triple(song, quality, size))
        }
        return result
    }

    suspend fun deleteCachedSong(songId: String): Boolean {
        val successSong = storage.deleteCacheFile("songs", songId)
        // Also clean up metadata
        storage.deleteCacheFile("songs_metadata", songId)
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

    suspend fun getSongAudioUrl(songId: String, remoteUrl: String): String {
        if (remoteUrl.isEmpty()) return ""
        val cachedUrl = storage.getCacheUrl("songs", "$songId.mp3")
        if (cachedUrl != null) {
            return cachedUrl
        }
        
        var shouldWaitSync = false
        var syncResultUrl: String? = null
        
        activeDownloadsMutex.withLock {
            if (!activeDownloads.contains(songId)) {
                activeDownloads.add(songId)
                
                try {
                    val headResponse: HttpResponse = httpClient.head(remoteUrl)
                    val contentLength = headResponse.headers["Content-Length"]?.toLongOrNull() ?: 0L
                    
                    if (contentLength <= 104_857_600L) { // 100MB
                        shouldWaitSync = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                
                if (shouldWaitSync) {
                    try {
                        val response: HttpResponse = httpClient.get(remoteUrl)
                        val bytes = response.body<ByteArray>()
                        storage.saveCacheFile("songs", "$songId.mp3", bytes)
                        syncResultUrl = storage.getCacheUrl("songs", "$songId.mp3")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        activeDownloads.remove(songId)
                    }
                } else {
                    downloadScope.launch {
                        try {
                            val response: HttpResponse = httpClient.get(remoteUrl)
                            val bytes = response.body<ByteArray>()
                            storage.saveCacheFile("songs", "$songId.mp3", bytes)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            activeDownloadsMutex.withLock {
                                activeDownloads.remove(songId)
                            }
                        }
                    }
                }
            } else {
                // If it's already downloading, we can't easily wait for it unless we use a CompletableFuture or Deferred.
                // For simplicity, we just fallback to remoteUrl if it's already downloading.
            }
        }
        
        return syncResultUrl ?: remoteUrl
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
