#include <jni.h>
#include <dlfcn.h>
#include <cstdint>
#include <vector>
#include <algorithm>
#include <memory>

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
