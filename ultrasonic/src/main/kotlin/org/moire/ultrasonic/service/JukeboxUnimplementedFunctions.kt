/*
 * JukeboxUnimplemented.kt
 * Copyright (C) 2009-2022 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import android.app.Service
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks

/**
 * This class helps to hide the unused (thus unimplemented) functions
 * of the crowded Player interface, so the JukeboxMediaPlayer class can be a bit clearer.
 */
@Suppress("TooManyFunctions")
abstract class JukeboxUnimplementedFunctions : Service(), Player {

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        TODO("Not yet implemented")
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ) {
        TODO("Not yet implemented")
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        TODO("Not yet implemented")
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        TODO("Not yet implemented")
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        TODO("Not yet implemented")
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        TODO("Not yet implemented")
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        TODO("Not yet implemented")
    }

    override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
        TODO("Not yet implemented")
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        TODO("Not yet implemented")
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        TODO("Not yet implemented")
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        TODO("Not yet implemented")
    }

    override fun seekToDefaultPosition() {
        TODO("Not yet implemented")
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        TODO("Not yet implemented")
    }

    override fun hasPrevious(): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasPreviousWindow(): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasPreviousMediaItem(): Boolean {
        TODO("Not yet implemented")
    }

    override fun previous() {
        TODO("Not yet implemented")
    }

    override fun seekToPreviousWindow() {
        TODO("Not yet implemented")
    }

    override fun seekToPreviousMediaItem() {
        TODO("Not yet implemented")
    }

    override fun hasNext(): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasNextWindow(): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasNextMediaItem(): Boolean {
        TODO("Not yet implemented")
    }

    override fun next() {
        TODO("Not yet implemented")
    }

    override fun seekToNextWindow() {
        TODO("Not yet implemented")
    }

    override fun seekToNextMediaItem() {
        TODO("Not yet implemented")
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        TODO("Not yet implemented")
    }

    override fun setPlaybackSpeed(speed: Float) {
        TODO("Not yet implemented")
    }

    override fun stop(reset: Boolean) {
        TODO("Not yet implemented")
    }

    override fun release() {
        TODO("Not yet implemented")
    }

    override fun getCurrentTracks(): Tracks {
        TODO("Not yet implemented")
    }

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        TODO("Not yet implemented")
    }

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {
        TODO("Not yet implemented")
    }

    override fun getCurrentManifest(): Any? {
        TODO("Not yet implemented")
    }

    override fun getCurrentWindowIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getNextWindowIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getNextMediaItemIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getPreviousWindowIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun getPreviousMediaItemIndex(): Int {
        TODO("Not yet implemented")
    }

    override fun isCurrentWindowDynamic(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isCurrentWindowLive(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isCurrentMediaItemLive(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isCurrentWindowSeekable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isCurrentMediaItemSeekable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurface() {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurface(surface: Surface?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurface(surface: Surface?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {
        TODO("Not yet implemented")
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        TODO("Not yet implemented")
    }

    override fun setVideoTextureView(textureView: TextureView?) {
        TODO("Not yet implemented")
    }

    override fun clearVideoTextureView(textureView: TextureView?) {
        TODO("Not yet implemented")
    }

    override fun setDeviceVolume(volume: Int) {
        TODO("Not yet implemented")
    }

    override fun increaseDeviceVolume() {
        TODO("Not yet implemented")
    }

    override fun decreaseDeviceVolume() {
        TODO("Not yet implemented")
    }

    override fun setDeviceMuted(muted: Boolean) {
        TODO("Not yet implemented")
    }
}
