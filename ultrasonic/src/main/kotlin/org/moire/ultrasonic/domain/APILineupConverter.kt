// Collection of functions to convert api Lineup entity to domain entity
@file:JvmName("ApiLineupConverter")

package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Lineup as APILineup

fun APILineup.toDomainEntity(): Lineup = Lineup(
    name = this@toDomainEntity.name,
    index = this@toDomainEntity.name.substring(0, 1)
)

fun List<APILineup>.toDomainEntityList(): List<Lineup> = this.map { it.toDomainEntity() }
