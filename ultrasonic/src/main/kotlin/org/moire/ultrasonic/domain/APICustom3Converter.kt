// Collection of functions to convert api custom3 entity to domain entity
@file:JvmName("ApiCustom3Converter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Custom3 as APICustom3

fun APICustom3.toDomainEntity(): Custom3 = Custom3(
    name = this@toDomainEntity.name,
    index = this@toDomainEntity.name.substring(0, 3)
)

fun List<APICustom3>.toDomainEntityList(): List<Custom3> = this.map { it.toDomainEntity() }
