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

    /*
    * TODO: mainThread() seems to be not equal to the "normal" main Thread, so it causes
    * a lot of often unnecessary thread switching. It looks like observeOn can actually
    * be removed in many cases
    */
    companion object {

        private fun mainThread() = AndroidSchedulers.from(Looper.getMainLooper())

        var activeServerChangingPublisher: PublishSubject<Int> =
            PublishSubject.create()

        // Subscribers should be called synchronously, not on another thread
        var activeServerChangingObservable: Observable<Int> =
            activeServerChangingPublisher

        var activeServerChangedPublisher: PublishSubject<Int> =
            PublishSubject.create()
        var activeServerChangedObservable: Observable<Int> =
            activeServerChangedPublisher.observeOn(mainThread())

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
            playerStatePublisher
                .replay(1)
                .autoConnect(0)
        val throttledPlayerStateObservable: Observable<StateWithTrack> =
            playerStatePublisher
                .replay(1)
                .autoConnect(0)
                .throttleLatest(300, TimeUnit.MILLISECONDS)

        val playlistPublisher: PublishSubject<List<Track>> =
            PublishSubject.create()
        val playlistObservable: Observable<List<Track>> =
            playlistPublisher
                .replay(1)
                .autoConnect(0)
        val throttledPlaylistObservable: Observable<List<Track>> =
            playlistPublisher
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
