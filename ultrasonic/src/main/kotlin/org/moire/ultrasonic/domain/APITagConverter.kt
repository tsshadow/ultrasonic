// Collection of functions to convert api Tag entity to domain entity
@file:JvmName("ApiTagConverter")

package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Tag as APITag

fun APITag.toDomainEntity(): Tag = Tag(
    name = this@toDomainEntity.name,
    songCount = this@toDomainEntity.songCount,
    index = this@toDomainEntity.name.substring(0, 1)
)

fun List<APITag>.toDomainEntityList(): List<Tag> = this.map { it.toDomainEntity() }
