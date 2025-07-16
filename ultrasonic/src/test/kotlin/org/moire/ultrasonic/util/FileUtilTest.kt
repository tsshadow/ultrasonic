//package org.moire.ultrasonic.util
//
//import androidx.media3.datasource.DataSpec
//import androidx.media3.datasource.FileDataSource
//import androidx.media3.datasource.DataSource
//import android.net.Uri
//import org.amshove.kluent.shouldNotBeEqualTo
//import org.amshove.kluent.shouldBeEqualTo
//import org.junit.Test
//import org.junit.runner.RunWith
//import org.robolectric.RobolectricTestRunner
//import org.robolectric.annotation.Config
//import org.moire.ultrasonic.domain.Track
//
//@RunWith(RobolectricTestRunner::class)
//@Config(manifest = Config.NONE)
//class FileUtilTest {
//    @Test
//    fun `tracks with same album and title but different artists get unique paths`() {
//        val t1 = Track(
//            id = "1",
//            serverId = 1,
//            title = "Song",
//            album = "Album",
//            artist = "Artist A",
//            path = "Album/Song.mp3",
//            suffix = "mp3"
//        )
//        val t2 = t1.copy(id = "2", artist = "Artist B")
//
//        val p1 = FileUtil.getSongFile(t1)
//        val p2 = FileUtil.getSongFile(t2)
//
//        p1 shouldNotBeEqualTo p2
//    }
//
//    @Test
//    fun `song file path uses hash when artist not in path`() {
//        val track = Track(
//            id = "99",
//            serverId = 3,
//            title = "Song",
//            album = "Album",
//            artist = "Artist A",
//            path = "Album/Song.mp3",
//            suffix = "mp3"
//        )
//
//        val expectedHash = Util.md5Hex("${track.serverId}_${track.id}")!!.substring(0, 8)
//        val expectedPath = "${FileUtil.musicDirectory.path}/Album/Song-$expectedHash.mp3"
//
//        FileUtil.getSongFile(track) shouldBeEqualTo expectedPath
//    }
//
//    @Test
//    fun `cached data source reads from correct file`() {
//        val t1 = Track(
//            id = "11",
//            serverId = 1,
//            title = "Song",
//            album = "Album",
//            artist = "Artist A",
//            path = "Album/Song.mp3",
//            suffix = "mp3"
//        )
//        val t2 = t1.copy(id = "22", artist = "Artist B")
//
//        val p1 = FileUtil.getSongFile(t1)
//        val p2 = FileUtil.getSongFile(t2)
//
//        FileUtil.createDirectoryForParent(p1)
//        FileUtil.createDirectoryForParent(p2)
//
//        java.io.File(p1).writeText("a")
//        java.io.File(p2).writeText("b")
//
//        val factory = CachedDataSource.Factory(DataSource.Factory { FileDataSource() })
//        val ds = factory.createDataSource()
//
//        ds.open(DataSpec(Uri.parse("id|0|$p1")))
//        val buffer1 = ByteArray(1)
//        ds.read(buffer1, 0, 1)
//        buffer1[0].toInt().toChar() shouldBeEqualTo 'a'
//        ds.close()
//
//        ds.open(DataSpec(Uri.parse("id|0|$p2")))
//        val buffer2 = ByteArray(1)
//        ds.read(buffer2, 0, 1)
//        buffer2[0].toInt().toChar() shouldBeEqualTo 'b'
//        ds.close()
//    }
//}
