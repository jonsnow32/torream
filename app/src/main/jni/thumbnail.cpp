#include <stdlib.h>
#include <string>

#include <jni.h>
#include <android/bitmap.h>
#include <mpv/client.h>

extern "C" {
    #include <libavcodec/avcodec.h>
    #include <libavformat/avformat.h>
    #include <libswscale/swscale.h>
    #include <libavutil/imgutils.h>
};

#include "jni_utils.h"
#include "globals.h"
#include "log.h"

extern "C" {
    jni_func(jobject, grabThumbnail, jint dimension);
    jni_func(jobject, extractVideoThumbnail, jstring uriString, jint dimension);
};

jni_func(jobject, grabThumbnail, jint dimension) {
    ALOGV("grabbing thumbnail\n");

    mpv_node result;
    {
        mpv_node c, c_arg0, c_arg1;
        mpv_node c_args[2];
        mpv_node_list c_array;
        c_arg0.format = MPV_FORMAT_STRING;
        c_arg0.u.string = (char*) "screenshot-raw";
        c_args[0] = c_arg0;
        c_arg1.format = MPV_FORMAT_STRING;
        c_arg1.u.string = (char*) "video";
        c_args[1] = c_arg1;
        c_array.num = 2;
        c_array.values = c_args;
        c.format = MPV_FORMAT_NODE_ARRAY;
        c.u.list = &c_array;
        if (mpv_command_node(g_mpv, &c, &result) < 0)
            return NULL;
    }

    // extract relevant property data from the node map mpv returns
    int w, h, stride;
    w = h = stride = 0;
    struct mpv_byte_array *data = NULL;
    {
        if (result.format != MPV_FORMAT_NODE_MAP)
            return NULL;
        for (int i = 0; i < result.u.list->num; i++) {
            std::string key(result.u.list->keys[i]);
            const mpv_node *val = &result.u.list->values[i];
            if (key == "w" || key == "h" || key == "stride") {
                if (val->format != MPV_FORMAT_INT64)
                    return NULL;
                if (key == "w")
                    w = val->u.int64;
                else if (key == "h")
                    h = val->u.int64;
                else
                    stride = val->u.int64;
            } else if (key == "format") {
                if (val->format != MPV_FORMAT_STRING)
                    return NULL;
                // check that format equals BGR0
                if (strcmp(val->u.string, "bgr0") != 0)
                    return NULL;
            } else if (key == "data") {
                if (val->format != MPV_FORMAT_BYTE_ARRAY)
                    return NULL;
                data = val->u.ba;
            }
        }
    }
    if (!w || !h || !stride || !data) {
        mpv_free_node_contents(&result);
        return NULL;
    }
    ALOGV("screenshot w:%d h:%d stride:%d\n", w, h, stride);

    // crop to square
    int crop_left = 0, crop_top = 0;
    int new_w = w, new_h = h;
    if (w > h) {
        crop_left = (w - h) / 2;
        new_w = h;
    } else {
        crop_top = (h - w) / 2;
        new_h = w;
    }
    ALOGV("cropped w:%u h:%u\n", new_w, new_h);

    uint8_t *new_data = (uint8_t*) data->data;
    new_data += crop_left * sizeof(uint32_t); // move begin rightwards
    new_data += stride * crop_top; // move begin downwards

    // convert & scale to appropriate size
    struct SwsContext *ctx = sws_getContext(
        new_w, new_h, AV_PIX_FMT_BGR0,
        dimension, dimension, AV_PIX_FMT_RGB32,
        SWS_BICUBIC, NULL, NULL, NULL);
    if (!ctx) {
        mpv_free_node_contents(&result);
        return NULL;
    }

    jintArray arr = env->NewIntArray(dimension * dimension);
    jint *scaled = env->GetIntArrayElements(arr, NULL);

    uint8_t *src_p[4] = { new_data }, *dst_p[4] = { (uint8_t*) scaled };
    int src_stride[4] = { stride },
        dst_stride[4] = { (int) sizeof(jint) * dimension };
    sws_scale(ctx, src_p, src_stride, 0, new_h, dst_p, dst_stride);
    sws_freeContext(ctx);

    mpv_free_node_contents(&result); // frees data->data

    // create android.graphics.Bitmap
    env->ReleaseIntArrayElements(arr, scaled, 0);

    jobject bitmap_config =
        env->GetStaticObjectField(android_graphics_Bitmap_Config, android_graphics_Bitmap_Config_ARGB_8888);
    jobject bitmap =
        env->CallStaticObjectMethod(android_graphics_Bitmap, android_graphics_Bitmap_createBitmap,
        arr, dimension, dimension, bitmap_config);
    env->DeleteLocalRef(arr);
    env->DeleteLocalRef(bitmap_config);

    return bitmap;
}

jni_func(jobject, extractVideoThumbnail, jstring uriString, jint dimension) {
    ALOGV("extracting video thumbnail\n");

    // Initialize JNI method cache
    init_methods_cache(env);

    const char *uri = env->GetStringUTFChars(uriString, NULL);
    if (!uri) {
        ALOGE("Failed to get URI string");
        return NULL;
    }

    // Initialize FFmpeg
    AVFormatContext *format_ctx = NULL;
    AVCodecContext *codec_ctx = NULL;
    const AVCodec *codec = NULL;
    AVFrame *frame = NULL;
    AVFrame *rgb_frame = NULL;
    uint8_t *buffer = NULL;
    struct SwsContext *sws_ctx = NULL;
    int video_stream_index = -1;

    // Open video file
    if (avformat_open_input(&format_ctx, uri, NULL, NULL) != 0) {
        ALOGE("Could not open video file: %s", uri);
        env->ReleaseStringUTFChars(uriString, uri);
        return NULL;
    }

    // Find video stream
    if (avformat_find_stream_info(format_ctx, NULL) < 0) {
        ALOGE("Could not find stream info");
        avformat_close_input(&format_ctx);
        env->ReleaseStringUTFChars(uriString, uri);
        return NULL;
    }

    for (int i = 0; i < format_ctx->nb_streams; i++) {
        if (format_ctx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            video_stream_index = i;
            break;
        }
    }

    if (video_stream_index == -1) {
        ALOGE("No video stream found");
        avformat_close_input(&format_ctx);
        env->ReleaseStringUTFChars(uriString, uri);
        return NULL;
    }

    // Find decoder
    AVCodecParameters *codec_par = format_ctx->streams[video_stream_index]->codecpar;
    codec = avcodec_find_decoder(codec_par->codec_id);
    if (!codec) {
        ALOGE("Unsupported codec");
        avformat_close_input(&format_ctx);
        env->ReleaseStringUTFChars(uriString, uri);
        return NULL;
    }

    codec_ctx = avcodec_alloc_context3(codec);
    if (!codec_ctx) {
        ALOGE("Could not allocate codec context");
        avformat_close_input(&format_ctx);
        env->ReleaseStringUTFChars(uriString, uri);
        return NULL;
    }

    if (avcodec_parameters_to_context(codec_ctx, codec_par) < 0) {
        ALOGE("Could not copy codec parameters");
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&format_ctx);
        env->ReleaseStringUTFChars(uriString, uri);
        return NULL;
    }

    if (avcodec_open2(codec_ctx, codec, NULL) < 0) {
        ALOGE("Could not open codec");
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&format_ctx);
        env->ReleaseStringUTFChars(uriString, uri);
        return NULL;
    }

    // Calculate duration and seek to 10% into the video (more reliable than seeking to 1 second)
    int64_t duration = format_ctx->duration;
    int64_t seek_pos = duration > 0 ? duration / 10 : 10 * AV_TIME_BASE; // 10% or 10 seconds

    // Seek to target position
    int64_t seek_target = av_rescale_q(seek_pos, AV_TIME_BASE_Q, format_ctx->streams[video_stream_index]->time_base);
    av_seek_frame(format_ctx, video_stream_index, seek_target, AVSEEK_FLAG_BACKWARD);

    // Flush codec buffers after seeking
    avcodec_flush_buffers(codec_ctx);

    // Decode frame
    AVPacket *packet = av_packet_alloc();
    frame = av_frame_alloc();
    rgb_frame = av_frame_alloc();

    if (!packet || !frame || !rgb_frame) {
        ALOGE("Could not allocate frames");
        if (packet) av_packet_free(&packet);
        if (frame) av_frame_free(&frame);
        if (rgb_frame) av_frame_free(&rgb_frame);
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&format_ctx);
        env->ReleaseStringUTFChars(uriString, uri);
        return NULL;
    }

    bool got_frame = false;
    int frames_read = 0;
    const int max_frames_to_read = 10; // Read up to 10 frames to skip potential black frames

    while (av_read_frame(format_ctx, packet) >= 0 && frames_read < max_frames_to_read) {
        if (packet->stream_index == video_stream_index) {
            int ret = avcodec_send_packet(codec_ctx, packet);
            if (ret < 0) {
                av_packet_unref(packet);
                continue;
            }

            while (ret >= 0) {
                ret = avcodec_receive_frame(codec_ctx, frame);
                if (ret == 0) {
                    frames_read++;
                    // Use the 3rd frame if available (often more reliable than the first)
                    if (frames_read >= 3 || frames_read >= max_frames_to_read) {
                        got_frame = true;
                        break;
                    }
                } else if (ret == AVERROR(EAGAIN)) {
                    break;
                } else {
                    break;
                }
            }

            if (got_frame) {
                av_packet_unref(packet);
                break;
            }
        }
        av_packet_unref(packet);
    }

    av_packet_free(&packet);

    if (!got_frame) {
        ALOGE("Could not decode frame");
        av_frame_free(&frame);
        av_frame_free(&rgb_frame);
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&format_ctx);
        env->ReleaseStringUTFChars(uriString, uri);
        return NULL;
    }

    // Convert to RGBA (correct format for Android)
    sws_ctx = sws_getContext(
        codec_ctx->width, codec_ctx->height, codec_ctx->pix_fmt,
        dimension, dimension, AV_PIX_FMT_RGBA,
        SWS_BICUBIC, NULL, NULL, NULL);

    if (!sws_ctx) {
        ALOGE("Could not create sws context");
        av_frame_free(&frame);
        av_frame_free(&rgb_frame);
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&format_ctx);
        env->ReleaseStringUTFChars(uriString, uri);
        return NULL;
    }

    buffer = (uint8_t*) av_malloc(dimension * dimension * 4);
    if (!buffer) {
        ALOGE("Could not allocate buffer");
        sws_freeContext(sws_ctx);
        av_frame_free(&frame);
        av_frame_free(&rgb_frame);
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&format_ctx);
        env->ReleaseStringUTFChars(uriString, uri);
        return NULL;
    }

    av_image_fill_arrays(rgb_frame->data, rgb_frame->linesize, buffer, AV_PIX_FMT_RGBA, dimension, dimension, 1);

    sws_scale(sws_ctx, frame->data, frame->linesize, 0, codec_ctx->height, rgb_frame->data, rgb_frame->linesize);

    // Create Android Bitmap
    env->ReleaseStringUTFChars(uriString, uri);

    jintArray arr = env->NewIntArray(dimension * dimension);
    jint *scaled = env->GetIntArrayElements(arr, NULL);

    // Copy RGBA data to int array (Android ARGB_8888 format: 0xAARRGGBB)
    for (int i = 0; i < dimension * dimension; i++) {
        uint8_t r = buffer[i * 4 + 0];  // Red
        uint8_t g = buffer[i * 4 + 1];  // Green
        uint8_t b = buffer[i * 4 + 2];  // Blue
        uint8_t a = buffer[i * 4 + 3];  // Alpha (usually 255)
        // Android Bitmap.Config.ARGB_8888 expects: 0xAARRGGBB
        scaled[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }

    env->ReleaseIntArrayElements(arr, scaled, 0);

    // Create Bitmap
    jobject bitmap_config = env->GetStaticObjectField(android_graphics_Bitmap_Config, android_graphics_Bitmap_Config_ARGB_8888);
    jobject bitmap = env->CallStaticObjectMethod(android_graphics_Bitmap, android_graphics_Bitmap_createBitmap,
        arr, dimension, dimension, bitmap_config);

    env->DeleteLocalRef(arr);
    env->DeleteLocalRef(bitmap_config);

    // Cleanup
    av_free(buffer);
    sws_freeContext(sws_ctx);
    av_frame_free(&frame);
    av_frame_free(&rgb_frame);
    avcodec_free_context(&codec_ctx);
    avformat_close_input(&format_ctx);

    return bitmap;
}
