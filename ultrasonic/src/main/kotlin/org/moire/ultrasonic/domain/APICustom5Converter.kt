// Collection of functions to convert api custom5 entity to domain entity
@file:JvmName("ApiCustom5Converter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Custom5 as APICustom5

fun APICustom5.toDomainEntity(): Custom5 = Custom5(
    name = this@toDomainEntity.name,
    index = this@toDomainEntity.name.substring(0, 5)
)

fun List<APICustom5>.toDomainEntityList(): List<Custom5> = this.map { it.toDomainEntity() }
