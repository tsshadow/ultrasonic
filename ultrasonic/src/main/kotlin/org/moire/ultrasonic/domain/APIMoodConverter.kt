// Collection of functions to convert api Mood entity to domain entity
@file:JvmName("ApiMoodConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Mood as APIMood

fun APIMood.toDomainEntity(): Mood = Mood(
    name = this@toDomainEntity.name,
    index = this@toDomainEntity.name.substring(0, 1)
)

fun List<APIMood>.toDomainEntityList(): List<Mood> = this.map { it.toDomainEntity() }
