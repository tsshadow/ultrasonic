// Collection of functions to convert api custom2 entity to domain entity
@file:JvmName("ApiCustom2Converter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Custom2 as APICustom2

fun APICustom2.toDomainEntity(): Custom2 = Custom2(
    name = this@toDomainEntity.name,
    index = this@toDomainEntity.name.substring(0, 2)
)

fun List<APICustom2>.toDomainEntityList(): List<Custom2> = this.map { it.toDomainEntity() }
