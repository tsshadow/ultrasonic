// Collection of functions to convert api custom1 entity to domain entity
@file:JvmName("ApiCustom1Converter")
package org.moire.ultrasonic.domain

import org.moire.ultrasonic.api.subsonic.models.Custom1 as APICustom1

fun APICustom1.toDomainEntity(): Custom1 = Custom1(
    name = this@toDomainEntity.name,
    index = this@toDomainEntity.name.substring(0, 1)
)

fun List<APICustom1>.toDomainEntityList(): List<Custom1> = this.map { it.toDomainEntity() }
