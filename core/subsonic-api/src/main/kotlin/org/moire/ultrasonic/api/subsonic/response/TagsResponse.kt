package org.moire.ultrasonic.api.subsonic.response

import com.fasterxml.jackson.annotation.JsonProperty
import org.moire.ultrasonic.api.subsonic.SubsonicAPIVersions
import org.moire.ultrasonic.api.subsonic.SubsonicError
import org.moire.ultrasonic.api.subsonic.models.Tag

class TagsResponse(
    status: Status,
    version: SubsonicAPIVersions,
    error: SubsonicError?
) : SubsonicResponse(status, version, error) {
    @JsonProperty("tags")
    private val tagsWrapper = TagsWrapper()
    val tagsList: List<Tag> get() = tagsWrapper.tagsList
}

internal class TagsWrapper(@JsonProperty("tag") val tagsList: List<Tag> = emptyList())
