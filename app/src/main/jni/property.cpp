#include <jni.h>
#include <stdlib.h>

#include <mpv/client.h>

#include "jni_utils.h"
#include "log.h"
#include "globals.h"

extern "C" {
    jni_func(jint, setOptionString, jstring option, jstring value);

    jni_func(jobject, getPropertyInt, jstring property);
    jni_func(void, setPropertyInt, jstring property, jobject value);
    jni_func(jobject, getPropertyDouble, jstring property);
    jni_func(void, setPropertyDouble, jstring property, jobject value);
    jni_func(jobject, getPropertyBoolean, jstring property);
    jni_func(void, setPropertyBoolean, jstring property, jobject value);
    jni_func(jstring, getPropertyString, jstring jproperty);
    jni_func(void, setPropertyString, jstring jproperty, jstring jvalue);
    jni_func(jobject, getPropertyNode, jstring jproperty);

    jni_func(void, observeProperty, jstring property, jint format);
}

jni_func(jint, setOptionString, jstring joption, jstring jvalue) {
    if (!g_mpv)
        die("mpv is not initialized");

    const char *option = env->GetStringUTFChars(joption, NULL);
    const char *value = env->GetStringUTFChars(jvalue, NULL);

    int result = mpv_set_option_string(g_mpv, option, value);

    env->ReleaseStringUTFChars(joption, option);
    env->ReleaseStringUTFChars(jvalue, value);

    return result;
}

static int common_get_property(JNIEnv *env, jstring jproperty, mpv_format format, void *output) {
    if (!g_mpv)
    {
        ALOGE("common_get_property(%s) format %d returned error set_property called but mpv is not initialized", env->GetStringUTFChars(jproperty, NULL), format);
        die("get_property called but mpv is not initialized");
    }

    const char *prop = env->GetStringUTFChars(jproperty, NULL);
    int result = mpv_get_property(g_mpv, prop, format, output);
    if (result < 0)
        ALOGE("mpv_get_property(%s) format %d returned error %s", prop, format, mpv_error_string(result));
    env->ReleaseStringUTFChars(jproperty, prop);

    return result;
}

static int common_set_property(JNIEnv *env, jstring jproperty, mpv_format format, void *value) {
    if (!g_mpv)
    {
        ALOGE("common_set_property(%s) format %d returned error set_property called but mpv is not initialized", env->GetStringUTFChars(jproperty, NULL), format);
        die("set_property called but mpv is not initialized");
    }

    const char *prop = env->GetStringUTFChars(jproperty, NULL);
    int result = mpv_set_property(g_mpv, prop, format, value);
    if (result < 0)
        ALOGE("mpv_set_property(%s, %p) format %d returned error %s", prop, value, format, mpv_error_string(result));
    env->ReleaseStringUTFChars(jproperty, prop);

    return result;
}

jni_func(jobject, getPropertyInt, jstring jproperty) {
    int64_t value = 0;
    if (common_get_property(env, jproperty, MPV_FORMAT_INT64, &value) < 0)
        return NULL;
    return env->NewObject(java_Integer, java_Integer_init, (jint)value);
}

jni_func(jobject, getPropertyDouble, jstring jproperty) {
    double value = 0;
    if (common_get_property(env, jproperty, MPV_FORMAT_DOUBLE, &value) < 0)
        return NULL;
    return env->NewObject(java_Double, java_Double_init, (jdouble)value);
}

jni_func(jobject, getPropertyBoolean, jstring jproperty) {
    int value = 0;
    if (common_get_property(env, jproperty, MPV_FORMAT_FLAG, &value) < 0)
        return NULL;
    return env->NewObject(java_Boolean, java_Boolean_init, (jboolean)value);
}

jni_func(jstring, getPropertyString, jstring jproperty) {
    char *value;
    if (common_get_property(env, jproperty, MPV_FORMAT_STRING, &value) < 0)
        return NULL;
    jstring jvalue = env->NewStringUTF(value);
    mpv_free(value);
    return jvalue;
}

jni_func(void, setPropertyInt, jstring jproperty, jobject jvalue) {
    int64_t value = env->CallIntMethod(jvalue, java_Integer_intValue);
    common_set_property(env, jproperty, MPV_FORMAT_INT64, &value);
}

jni_func(void, setPropertyDouble, jstring jproperty, jobject jvalue) {
    double value = env->CallDoubleMethod(jvalue, java_Double_doubleValue);
    common_set_property(env, jproperty, MPV_FORMAT_DOUBLE, &value);
}

jni_func(void, setPropertyBoolean, jstring jproperty, jobject jvalue) {
    int value = env->CallBooleanMethod(jvalue, java_Boolean_booleanValue);
    common_set_property(env, jproperty, MPV_FORMAT_FLAG, &value);
}

jni_func(void, setPropertyString, jstring jproperty, jstring jvalue) {
    const char *value = env->GetStringUTFChars(jvalue, NULL);
    common_set_property(env, jproperty, MPV_FORMAT_STRING, &value);
    env->ReleaseStringUTFChars(jvalue, value);
}

// Recursively converts an mpv_node into a Java String/Boolean/Long/Double/ArrayList/HashMap.
static jobject node_to_java(JNIEnv *env, mpv_node *node) {
    switch (node->format) {
        case MPV_FORMAT_STRING:
            return env->NewStringUTF(node->u.string);
        case MPV_FORMAT_FLAG:
            return env->NewObject(java_Boolean, java_Boolean_init, (jboolean)node->u.flag);
        case MPV_FORMAT_INT64:
            return env->NewObject(java_Long, java_Long_init, (jlong)node->u.int64);
        case MPV_FORMAT_DOUBLE:
            return env->NewObject(java_Double, java_Double_init, (jdouble)node->u.double_);
        case MPV_FORMAT_NODE_ARRAY: {
            jobject list = env->NewObject(java_ArrayList, java_ArrayList_init);
            mpv_node_list *node_list = node->u.list;
            for (int i = 0; i < node_list->num; i++) {
                jobject value = node_to_java(env, &node_list->values[i]);
                env->CallBooleanMethod(list, java_ArrayList_add, value);
                if (value) env->DeleteLocalRef(value);
            }
            return list;
        }
        case MPV_FORMAT_NODE_MAP: {
            jobject map = env->NewObject(java_HashMap, java_HashMap_init);
            mpv_node_list *node_list = node->u.list;
            for (int i = 0; i < node_list->num; i++) {
                jstring key = env->NewStringUTF(node_list->keys[i]);
                jobject value = node_to_java(env, &node_list->values[i]);
                env->CallObjectMethod(map, java_HashMap_put, key, value);
                env->DeleteLocalRef(key);
                if (value) env->DeleteLocalRef(value);
            }
            return map;
        }
        default:
            return NULL;
    }
}

jni_func(jobject, getPropertyNode, jstring jproperty) {
    if (!g_mpv)
        die("mpv is not initialized");

    const char *prop = env->GetStringUTFChars(jproperty, NULL);
    mpv_node node;
    int result = mpv_get_property(g_mpv, prop, MPV_FORMAT_NODE, &node);
    if (result < 0)
        ALOGE("mpv_get_property(%s) format NODE returned error %s", prop, mpv_error_string(result));
    env->ReleaseStringUTFChars(jproperty, prop);

    if (result < 0)
        return NULL;

    jobject java_obj = node_to_java(env, &node);
    mpv_free_node_contents(&node);
    return java_obj;
}

jni_func(void, observeProperty, jstring property, jint format) {
    if (!g_mpv)
        die("mpv is not initialized");
    const char *prop = env->GetStringUTFChars(property, NULL);
    int result = mpv_observe_property(g_mpv, 0, prop, (mpv_format)format);
    if (result < 0)
        ALOGE("mpv_observe_property(%s) format %d returned error %s", prop, format, mpv_error_string(result));
    env->ReleaseStringUTFChars(property, prop);
}
