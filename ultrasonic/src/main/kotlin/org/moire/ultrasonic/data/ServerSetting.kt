package org.moire.ultrasonic.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Contains the data model of a Server Setting
 * @param id: Unique Id of the Server Setting
 * @param index: The index of the Server Setting on the Selection Activity
 * @param name: The user readable Name of the Server
 * @param url: The Url of the server
 * @param userName: The UserName that can be used to connect to the server
 * @param password: The Password of the User
 * @param jukeboxByDefault: True if the JukeBox mode should be turned on for the server
 * @param allowSelfSignedCertificate: True if the server uses self-signed certificate
 * @param forcePlainTextPassword: True if the server authenticates the user using old Ldap-like way
 * @param musicFolderId: The Id of the MusicFolder to be used with the server
 *
 * TODO: forcePlainTextPassword is still using the old column name.
 * Could be updated on the next significant change to the DB scheme
 */
@Entity
data class ServerSetting(
    // Default ID is 0, which will trigger SQLite to generate a unique ID.
    @PrimaryKey(autoGenerate = true) var id: Int = 0,
    @ColumnInfo(name = "index") var index: Int,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "url") var url: String,
    @ColumnInfo(name = "color") var color: Int? = null,
    @ColumnInfo(name = "userName") var userName: String,
    @ColumnInfo(name = "password") var password: String,
    @ColumnInfo(name = "jukeboxByDefault") var jukeboxByDefault: Boolean,
    @ColumnInfo(name = "allowSelfSignedCertificate") var allowSelfSignedCertificate: Boolean,
    @ColumnInfo(name = "ldapSupport") var forcePlainTextPassword: Boolean,
    @ColumnInfo(name = "musicFolderId") var musicFolderId: String?,
    @ColumnInfo(name = "minimumApiVersion") var minimumApiVersion: String?,
    @ColumnInfo(name = "chatSupport") var chatSupport: Boolean? = null,
    @ColumnInfo(name = "bookmarkSupport") var bookmarkSupport: Boolean? = null,
    @ColumnInfo(name = "shareSupport") var shareSupport: Boolean? = null,
    @ColumnInfo(name = "podcastSupport") var podcastSupport: Boolean? = null
) {
    constructor() : this (
        0, 0, "", "", null, "", "", false, false, false, null, null
    )
}
