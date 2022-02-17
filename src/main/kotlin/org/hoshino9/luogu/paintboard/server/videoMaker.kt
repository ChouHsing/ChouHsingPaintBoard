package org.hoshino9.luogu.paintboard.server

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.jcodec.api.awt.AWTSequenceEncoder
import org.jcodec.scale.AWTUtil
import java.awt.image.BufferedImage
import java.io.File

const val speed = 200
const val fps = 25
const val outputFile = "video/output.mp4"
const val id = 0

fun main() {
    loadConfig()
    connectMongoDB()

    val encoder = AWTSequenceEncoder.createSequenceEncoder(File(outputFile), fps)
    val recordList = mongo.getCollection<PaintRecord>("paintboard$id")
        .find().toFlow()
    val pic = AWTUtil.fromBufferedImageRGB(initImages[0] ?: BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB))
    val data = pic.getPlaneData(0)
    runBlocking {
        var timeTo = 1644903000000L
        recordList.collect {
            val time = it.time
            while (timeTo <= time) {
                encoder.encodeNativeFrame(pic)
                if (timeTo == 0L) timeTo = time
                timeTo += 1000 * speed / fps
            }
            val offset = (it.y * 800 + it.x) * 3
            data[offset] = ((it.color shr 16 and 0xff) - 128).toByte()
            data[offset + 1] = ((it.color shr 8 and 0xff) - 128).toByte()
            data[offset + 2] = ((it.color and 0xff) - 128).toByte()
        }
    }
    encoder.encodeNativeFrame(pic)
    encoder.finish()
}
