package org.moire.ultrasonic.imageloader

import android.net.Uri
import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.amshove.kluent.`should throw`
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import org.moire.ultrasonic.api.subsonic.SubsonicAPIClient
import org.moire.ultrasonic.api.subsonic.response.StreamResponse
import org.moire.ultrasonic.api.subsonic.toStreamResponse
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = org.moire.ultrasonic.app.UApp::class, manifest = Config.NONE)
class AvatarRequestHandlerTest {
    private val mockApiClient: SubsonicAPIClient = mock()
    private val mockApi: org.moire.ultrasonic.api.subsonic.SubsonicAPIDefinition = mock()
    private val mockCall: Call<ResponseBody> = mock()
    private val mockResponse: Response<ResponseBody> = mock()
    private val handler = AvatarRequestHandler(mockApiClient)

    @org.junit.Before
    fun setUpMocks() {
        doReturn(mockApi).whenever(mockApiClient).api
        doReturn(mockCall).whenever(mockApi).getAvatar(any())
        doReturn(mockResponse).whenever(mockCall).execute()
    }

    @Test
    fun `Should accept only cover art request`() {
        val requestUri = createLoadAvatarRequest("some-username")

        handler.canHandleRequest(requestUri.buildRequest()) shouldBeEqualTo true
    }

    @Test
    fun `Should not accept random request uri`() {
        val requestUri = Uri.Builder()
            .scheme(SCHEME)
            .appendPath("something")
            .build()

        handler.canHandleRequest(requestUri.buildRequest()) shouldBeEqualTo false
    }

    @Test
    fun `Should fail loading if uri doesn't contain username`() {
        var requestUri = createLoadAvatarRequest("some-username")
        requestUri = requestUri.buildUpon().clearQuery().build()

        val fail = {
            handler.load(requestUri.buildRequest(), 0)
        }

        fail `should throw` IllegalArgumentException::class
    }

    @Test
    fun `Should load avatar from network`() {
        val streamResponse = StreamResponse(
            loadResourceStream("Big_Buck_Bunny.jpeg"),
            apiError = null,
            responseHttpCode = 200
        )

        whenever(
            mockApiClient.toStreamResponse(any())
        ).thenReturn(streamResponse)

        val response = handler.load(
            createLoadAvatarRequest("some-username").buildRequest(),
            0
        )

        response.loadedFrom `should be equal to` Picasso.LoadedFrom.NETWORK
        response.source `should not be` null
    }

    private fun Uri.buildRequest() = Request.Builder(this).build()
}
