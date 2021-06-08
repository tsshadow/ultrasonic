// Collection of functions to convert api custom4 entity to domain entity
@file:JvmName("ApiCustom4Converter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Custom4 as APICustom4

fun APICustom4.toDomainEntity(): Custom4 = Custom4(
    name = this@toDomainEntity.name,
    index = this@toDomainEntity.name.substring(0, 4)
)

fun List<APICustom4>.toDomainEntityList(): List<Custom4> = this.map { it.toDomainEntity() }
