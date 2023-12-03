// Collection of functions to convert api Genre entity to domain entity
@file:JvmName("ApiGenreConverter")

package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Genre as APIGenre

fun APIGenre.toDomainEntity(): Genre = Genre(
    name = this@toDomainEntity.name,
    index = this@toDomainEntity.name.substring(0, 1)
)

fun List<APIGenre>.toDomainEntityList(): List<Genre> = this.map { it.toDomainEntity() }
