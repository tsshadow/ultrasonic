package org.moire.ultrasonic.api.subsonic

import org.junit.Before
import org.junit.Rule
import org.moire.ultrasonic.api.subsonic.rules.MockWebServerRule

/**
 * Base class for integration tests for [SubsonicAPIClient] class.
 */
abstract class SubsonicAPIClientTest {
    @JvmField @Rule
    val mockWebServerRule = MockWebServerRule()

    protected lateinit var config: SubsonicClientConfiguration
    protected lateinit var client: SubsonicAPIClient

    @Before
    open fun setUp() {
        config = SubsonicClientConfiguration(
            mockWebServerRule.mockWebServer.url("/").toString(),
            USERNAME,
            PASSWORD,
            CLIENT_VERSION,
            CLIENT_ID
        )
        client = SubsonicAPIClient(config)
    }
}
