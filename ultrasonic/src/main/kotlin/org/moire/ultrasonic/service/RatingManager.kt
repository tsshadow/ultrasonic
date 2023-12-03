/*
 * RatingManager.kt
 * Copyright (C) 2009-2023 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import androidx.media3.common.HeartRating
import androidx.media3.common.StarRating
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.data.RatingUpdate
import org.moire.ultrasonic.service.MusicServiceFactory.getMusicService
import timber.log.Timber

/*
* This class subscribes to RatingEvents and submits them to the server.
* In the future it could be extended to store the ratings when offline
* and submit them when back online.
* Only the manager should publish RatingSubmitted events
 */
class RatingManager : CoroutineScope by CoroutineScope(Dispatchers.Default) {
    private val rxBusSubscription: CompositeDisposable = CompositeDisposable()

    var lastUpdate: RatingUpdate? = null

    init {
        rxBusSubscription += RxBus.ratingSubmitterObservable.subscribe {
            submitRating(it)
        }
    }

    internal fun submitRating(update: RatingUpdate) {
        // Don't submit the same rating twice
        if (update.id == lastUpdate?.id && update.rating == lastUpdate?.rating) return

        val service = getMusicService()
        val id = update.id

        Timber.i("Submitting rating to server: ${update.rating} for $id")

        if (update.rating is HeartRating) {
            launch {
                var success = false
                withContext(Dispatchers.IO) {
                    try {
                        if (update.rating.isHeart) {
                            service.star(id)
                        } else {
                            service.unstar(id)
                        }
                        success = true
                    } catch (all: Exception) {
                        Timber.e(all)
                    }
                }
                RxBus.ratingPublished.onNext(
                    update.copy(success = success)
                )
            }
        } else if (update.rating is StarRating) {
            launch {
                var success = false
                withContext(Dispatchers.IO) {
                    try {
                        getMusicService().setRating(id, update.rating.starRating.toInt())
                        success = true
                    } catch (all: Exception) {
                        Timber.e(all)
                    }
                }
                RxBus.ratingPublished.onNext(
                    update.copy(success = success)
                )
            }
        }
        lastUpdate = update
    }

    companion object {
        val instance: RatingManager by lazy {
            RatingManager()
        }
    }
}
