#include <jni.h>
#include <android/bitmap.h>
#include <dlfcn.h>
#include <cstdint>
#include <vector>
#include <algorithm>
#include <memory>
#include <cstdio>
#include <setjmp.h>

struct PngApi {
    void* library = dlopen("libtiffconverter.so", RTLD_NOW | RTLD_LOCAL);
    const char* (*version)(void*) = symbol<decltype(version)>("png_get_libpng_ver");
    void* (*create)(const char*, void*, void*, void*) = symbol<decltype(create)>("png_create_write_struct");
    void* (*info)(void*) = symbol<decltype(info)>("png_create_info_struct");
    void (*init)(void*, FILE*) = symbol<decltype(init)>("png_init_io");
    void (*header)(void*, void*, uint32_t, uint32_t, int, int, int, int, int) = symbol<decltype(header)>("png_set_IHDR");
    void (*compression)(void*, int) = symbol<decltype(compression)>("png_set_compression_level");
    void (*filter)(void*, int, int) = symbol<decltype(filter)>("png_set_filter");
    void (*writeInfo)(void*, void*) = symbol<decltype(writeInfo)>("png_write_info");
    void (*row)(void*, const unsigned char*) = symbol<decltype(row)>("png_write_row");
    void (*end)(void*, void*) = symbol<decltype(end)>("png_write_end");
    void (*destroy)(void**, void**) = symbol<decltype(destroy)>("png_destroy_write_struct");
    jmp_buf* (*jump)(void*, void (*)(jmp_buf, int), size_t) = symbol<decltype(jump)>("png_set_longjmp_fn");
    template<class T> T symbol(const char* name) { return library ? reinterpret_cast<T>(dlsym(library, name)) : nullptr; }
    bool valid() const { return version && create && info && init && header && writeInfo && row && end && destroy && jump; }
};

// Use libtiff directly. The third-party Android wrapper installs process-wide
// SIGSEGV handlers that conflict with ART; none of those entry points are used.
struct TiffApi {
    void* library = dlopen("libtiff.so", RTLD_NOW | RTLD_LOCAL);
    void* (*open)(const char*, const char*) = symbol<decltype(open)>("TIFFOpen");
    void (*close)(void*) = symbol<decltype(close)>("TIFFClose");
    int (*get)(void*, uint32_t, ...) = symbol<decltype(get)>("TIFFGetField");
    int (*set)(void*, uint32_t, ...) = symbol<decltype(set)>("TIFFSetField");
    int (*tiled)(void*) = symbol<decltype(tiled)>("TIFFIsTiled");
    int (*strip)(void*, uint32_t, uint32_t*) = symbol<decltype(strip)>("TIFFReadRGBAStrip");
    int (*tile)(void*, uint32_t, uint32_t, uint32_t*) = symbol<decltype(tile)>("TIFFReadRGBATile");
    template<class T> T symbol(const char* name) { return library ? reinterpret_cast<T>(dlsym(library, name)) : nullptr; }
    bool valid() const { return open && close && get && set && tiled && strip && tile; }
};

extern "C" JNIEXPORT jintArray JNICALL
Java_com_mistermikhail_fgallery_data_TiffNative_decode(JNIEnv* env, jobject, jstring path, jint edge) {
    static TiffApi api;
    if (!api.valid() || edge <= 0) return nullptr;
    const char* text = env->GetStringUTFChars(path, nullptr);
    if (!text) return nullptr;
    void* image = api.open(text, "r");
    env->ReleaseStringUTFChars(path, text);
    if (!image) return nullptr;
    std::unique_ptr<void, decltype(api.close)> owner(image, api.close);
    uint32_t width = 0, height = 0;
    uint16_t orientation = 1;
    api.get(image, 256, &width); api.get(image, 257, &height);
    api.get(image, 274, &orientation);
    if (!width || !height || width > 100000 || height > 100000) return nullptr;
    // Normalize storage orientation while reading, then apply the original tag in Kotlin.
    api.set(image, 274, 1);
    uint32_t sample = 1;
    while ((std::max(width, height) + sample - 1) / sample > static_cast<uint32_t>(edge) ||
           uint64_t((width + sample - 1) / sample) * ((height + sample - 1) / sample) > 6000000) sample *= 2;
    const uint32_t outWidth = (width + sample - 1) / sample;
    const uint32_t outHeight = (height + sample - 1) / sample;
    try {
        std::vector<jint> pixels(size_t(outWidth) * outHeight + 3);
        pixels[0] = outWidth; pixels[1] = outHeight; pixels[2] = orientation;
        auto copyPixel = [&](uint32_t x, uint32_t y, uint32_t rgba) {
            pixels[3 + size_t(y / sample) * outWidth + x / sample] =
                (rgba & 0xff000000) | ((rgba & 0xff) << 16) | (rgba & 0xff00) | ((rgba >> 16) & 0xff);
        };
        if (api.tiled(image)) {
            uint32_t tw = 0, th = 0;
            api.get(image, 322, &tw); api.get(image, 323, &th);
            if (!tw || !th || uint64_t(tw) * th > 12000000) return nullptr;
            std::vector<uint32_t> raster(size_t(tw) * th);
            for (uint32_t y = 0; y < height; y += th) for (uint32_t x = 0; x < width; x += tw) {
                if (!api.tile(image, x, y, raster.data())) return nullptr;
                for (uint32_t row = (sample - y % sample) % sample; row < std::min(th, height - y); row += sample)
                    for (uint32_t col = (sample - x % sample) % sample; col < std::min(tw, width - x); col += sample)
                        copyPixel(x + col, y + row, raster[size_t(th - 1 - row) * tw + col]);
            }
        } else {
            uint32_t rows = height;
            api.get(image, 278, &rows);
            rows = std::min(rows, height);
            if (!rows || uint64_t(width) * rows > 12000000) return nullptr;
            std::vector<uint32_t> raster(size_t(width) * rows);
            for (uint32_t y = 0; y < height; y += rows) {
                if (!api.strip(image, y, raster.data())) return nullptr;
                uint32_t actualRows = std::min(rows, height - y);
                for (uint32_t row = (sample - y % sample) % sample; row < actualRows; row += sample)
                    for (uint32_t col = 0; col < width; col += sample)
                        copyPixel(col, y + row, raster[size_t(actualRows - 1 - row) * width + col]);
            }
        }
        auto output = env->NewIntArray(static_cast<jsize>(pixels.size()));
        if (output) env->SetIntArrayRegion(output, 0, static_cast<jsize>(pixels.size()), pixels.data());
        return output;
    } catch (...) { return nullptr; }
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_mistermikhail_fgallery_data_TiffNative_metadata(JNIEnv* env, jobject, jstring path) {
    static TiffApi api;
    if (!api.valid()) return nullptr;
    const char* text = env->GetStringUTFChars(path, nullptr);
    if (!text) return nullptr;
    void* image = api.open(text, "r");
    env->ReleaseStringUTFChars(path, text);
    if (!image) return nullptr;
    std::unique_ptr<void, decltype(api.close)> owner(image, api.close);
    uint32_t width = 0, height = 0; uint16_t orientation = 1;
    api.get(image, 256, &width); api.get(image, 257, &height); api.get(image, 274, &orientation);
    jint values[] = {static_cast<jint>(width), static_cast<jint>(height), orientation};
    auto output = env->NewIntArray(3);
    if (output) env->SetIntArrayRegion(output, 0, 3, values);
    return output;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mistermikhail_fgallery_data_TiffNative_writePng(JNIEnv* env, jobject, jstring source, jstring destination) {
    static TiffApi tiff; static PngApi png;
    if (!tiff.valid() || !png.valid()) return JNI_FALSE;
    const char* input = env->GetStringUTFChars(source, nullptr);
    if (!input) return JNI_FALSE;
    void* image = tiff.open(input, "r");
    env->ReleaseStringUTFChars(source, input);
    if (!image) return JNI_FALSE;
    std::unique_ptr<void, decltype(tiff.close)> owner(image, tiff.close);
    uint32_t width = 0, height = 0;
    tiff.get(image, 256, &width); tiff.get(image, 257, &height);
    if (!width || !height || width > 100000 || height > 100000) return JNI_FALSE;
    tiff.set(image, 274, 1); // The original orientation is preserved as PNG EXIF by Kotlin.
    const char* output = env->GetStringUTFChars(destination, nullptr);
    if (!output) return JNI_FALSE;
    FILE* file = fopen(output, "wb");
    env->ReleaseStringUTFChars(destination, output);
    if (!file) return JNI_FALSE;
    void* writer = png.create(png.version(nullptr), nullptr, nullptr, nullptr);
    void* info = writer ? png.info(writer) : nullptr;
    if (!writer || !info) { if (writer) png.destroy(&writer, &info); fclose(file); return JNI_FALSE; }
    std::vector<uint32_t> block, band;
    auto* jump = png.jump(writer, longjmp, sizeof(jmp_buf));
    if (!jump || setjmp(*jump)) { png.destroy(&writer, &info); fclose(file); return JNI_FALSE; }
    bool success = false;
    try {
        png.init(writer, file);
        if (png.compression) png.compression(writer, 0);
        if (png.filter) png.filter(writer, 0, 8);
        png.header(writer, info, width, height, 8, 6, 0, 0, 0);
        png.writeInfo(writer, info);
        if (tiff.tiled(image)) {
            uint32_t tw = 0, th = 0;
            tiff.get(image, 322, &tw); tiff.get(image, 323, &th);
            if (tw && th && uint64_t(width) * th + uint64_t(tw) * th <= 12000000) {
                block.resize(size_t(tw) * th); band.resize(size_t(width) * th);
                success = true;
                for (uint32_t y = 0; y < height && success; y += th) {
                    uint32_t rows = std::min(th, height - y);
                    for (uint32_t x = 0; x < width && success; x += tw) {
                        if (!tiff.tile(image, x, y, block.data())) { success = false; break; }
                        uint32_t cols = std::min(tw, width - x);
                        for (uint32_t row = 0; row < rows; ++row)
                            std::copy_n(block.data() + size_t(th - 1 - row) * tw, cols, band.data() + size_t(row) * width + x);
                    }
                    if (success) for (uint32_t row = 0; row < rows; ++row)
                        png.row(writer, reinterpret_cast<unsigned char*>(band.data() + size_t(row) * width));
                }
            }
        } else {
            uint32_t rows = height;
            tiff.get(image, 278, &rows); rows = std::min(rows, height);
            if (rows && uint64_t(width) * rows <= 12000000) {
                block.resize(size_t(width) * rows);
                success = true;
                for (uint32_t y = 0; y < height && success; y += rows) {
                    if (!tiff.strip(image, y, block.data())) { success = false; break; }
                    uint32_t actual = std::min(rows, height - y);
                    for (uint32_t row = 0; row < actual; ++row)
                        png.row(writer, reinterpret_cast<unsigned char*>(block.data() + size_t(actual - 1 - row) * width));
                }
            }
        }
        if (success) png.end(writer, info);
    } catch (...) { success = false; }
    png.destroy(&writer, &info); fclose(file);
    return success ? JNI_TRUE : JNI_FALSE;
}

// ARGB_8888 is RGBA in Android's native little-endian bitmap memory.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_mistermikhail_fgallery_data_TiffNative_writeBitmapPng(JNIEnv* env, jobject, jobject bitmap, jstring destination, jint compressionLevel) {
    static PngApi png;
    AndroidBitmapInfo dimensions{};
    if (!png.valid() || AndroidBitmap_getInfo(env, bitmap, &dimensions) != 0 ||
        dimensions.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(destination, nullptr);
    if (!path) return JNI_FALSE;
    FILE* file = fopen(path, "wb"); env->ReleaseStringUTFChars(destination, path);
    if (!file) return JNI_FALSE;
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != 0) { fclose(file); return JNI_FALSE; }
    void* writer = png.create(png.version(nullptr), nullptr, nullptr, nullptr);
    void* info = writer ? png.info(writer) : nullptr;
    auto* jump = writer ? png.jump(writer, longjmp, sizeof(jmp_buf)) : nullptr;
    bool success = false;
    std::vector<unsigned char> straight(size_t(dimensions.width) * 4);
    if (info && jump && !setjmp(*jump)) {
        png.init(writer, file);
        if (png.compression) png.compression(writer, std::clamp<int>(compressionLevel, 0, 9));
        if (png.filter) png.filter(writer, 0, 8);
        png.header(writer, info, dimensions.width, dimensions.height, 8, 6, 0, 0, 0);
        png.writeInfo(writer, info);
        for (uint32_t row = 0; row < dimensions.height; ++row) {
            auto* source = static_cast<unsigned char*>(pixels) + size_t(row) * dimensions.stride;
            if ((dimensions.flags & ANDROID_BITMAP_FLAGS_ALPHA_MASK) == ANDROID_BITMAP_FLAGS_ALPHA_PREMUL) {
                std::copy_n(source, straight.size(), straight.data());
                for (uint32_t col = 0; col < dimensions.width; ++col) {
                    auto* pixel = straight.data() + size_t(col) * 4;
                    const unsigned int alpha = pixel[3];
                    if (alpha && alpha < 255) for (int channel = 0; channel < 3; ++channel)
                        pixel[channel] = std::min(255u, (unsigned(pixel[channel]) * 255 + alpha / 2) / alpha);
                }
                png.row(writer, straight.data());
            } else png.row(writer, source);
        }
        png.end(writer, info); success = true;
    }
    if (writer) png.destroy(&writer, &info);
    AndroidBitmap_unlockPixels(env, bitmap); fclose(file);
    return success ? JNI_TRUE : JNI_FALSE;
}
