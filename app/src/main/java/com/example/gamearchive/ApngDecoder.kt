package com.example.gamearchive

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.linecorp.apng.ApngDrawable
import kotlinx.coroutines.runInterruptible
import okio.ByteString.Companion.decodeHex

internal class ApngDecoder(
    private val source: ImageSource
) : Decoder {

    override suspend fun decode(): DecodeResult = runInterruptible {
        val drawable = source.source().inputStream().use(ApngDrawable::decode)
        DecodeResult(
            image = drawable.asImage(),
            isSampled = false
        )
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            val source = result.source.source()
            if (!source.rangeEquals(0, PNG_HEADER)) return null
            val isApng = source.peek().inputStream().use(ApngDrawable::isApng)
            return if (isApng) ApngDecoder(result.source) else null
        }

        private companion object {
            val PNG_HEADER = "89504e470d0a1a0a".decodeHex()
        }
    }
}
