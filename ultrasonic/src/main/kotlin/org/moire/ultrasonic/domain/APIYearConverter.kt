// Collection of functions to convert api Year entity to domain entity
@file:JvmName("ApiYearConverter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Year as APIYear

fun APIYear.toDomainEntity(): Year = Year(
    name = this@toDomainEntity.name,
    index = this@toDomainEntity.name.substring(0, 1)
)

fun List<APIYear>.toDomainEntityList(): List<Year> = this.map { it.toDomainEntity() }
