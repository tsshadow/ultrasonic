package org.moire.ultrasonic.service

import android.os.Looper
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import org.moire.ultrasonic.domain.Track

class RxBus {

    companion object {

        private fun mainThread() = AndroidSchedulers.from(Looper.getMainLooper())

        var activeServerChangePublisher: PublishSubject<Int> =
            PublishSubject.create()
        var activeServerChangeObservable: Observable<Int> =
            activeServerChangePublisher.observeOn(mainThread())

        val themeChangedEventPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val themeChangedEventObservable: Observable<Unit> =
            themeChangedEventPublisher.observeOn(mainThread())

        val musicFolderChangedEventPublisher: PublishSubject<Folder> =
            PublishSubject.create()
        val musicFolderChangedEventObservable: Observable<Folder> =
            musicFolderChangedEventPublisher.observeOn(mainThread())

        val playerStatePublisher: PublishSubject<StateWithTrack> =
            PublishSubject.create()
        val playerStateObservable: Observable<StateWithTrack> =
            playerStatePublisher.observeOn(mainThread())
                .replay(1)
                .autoConnect(0)
        val throttledPlayerStateObservable: Observable<StateWithTrack> =
            playerStatePublisher.observeOn(mainThread())
                .replay(1)
                .autoConnect(0)
                .throttleLatest(300, TimeUnit.MILLISECONDS)

        val playlistPublisher: PublishSubject<List<Track>> =
            PublishSubject.create()
        val playlistObservable: Observable<List<Track>> =
            playlistPublisher.observeOn(mainThread())
                .replay(1)
                .autoConnect(0)
        val throttledPlaylistObservable: Observable<List<Track>> =
            playlistPublisher.observeOn(mainThread())
                .replay(1)
                .autoConnect(0)
                .throttleLatest(300, TimeUnit.MILLISECONDS)

        val trackDownloadStatePublisher: PublishSubject<TrackDownloadState> =
            PublishSubject.create()
        val trackDownloadStateObservable: Observable<TrackDownloadState> =
            trackDownloadStatePublisher.observeOn(mainThread())

        // Commands
        val dismissNowPlayingCommandPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val dismissNowPlayingCommandObservable: Observable<Unit> =
            dismissNowPlayingCommandPublisher.observeOn(mainThread())

        val createServiceCommandPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val createServiceCommandObservable: Observable<Unit> =
            createServiceCommandPublisher.observeOn(mainThread())

        val stopServiceCommandPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val stopServiceCommandObservable: Observable<Unit> =
            stopServiceCommandPublisher.observeOn(mainThread())

        val shutdownCommandPublisher: PublishSubject<Unit> =
            PublishSubject.create()
        val shutdownCommandObservable: Observable<Unit> =
            shutdownCommandPublisher.observeOn(mainThread())
    }

    data class StateWithTrack(
        val track: Track?,
        val index: Int = -1,
        val isPlaying: Boolean = false,
        val state: Int
    )

    data class TrackDownloadState(
        val id: String,
        val state: DownloadState,
        val progress: Int?
    )

    data class Folder(
        val id: String?
    )
}

operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
    this.add(disposable)
}
