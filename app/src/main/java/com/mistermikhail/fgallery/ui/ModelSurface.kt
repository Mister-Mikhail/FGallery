package com.mistermikhail.fgallery.ui

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.Viewport
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

internal data class ModelDimensions(val width: Double, val height: Double, val depth: Double)

internal class ModelSurface(
    context: Context,
    data: ByteBuffer,
    onFrame: (ModelDimensions) -> Unit,
    onError: () -> Unit,
) : SurfaceView(context), SurfaceHolder.Callback {
    private val worker = ModelRenderThread.handler
    private val session = RenderSession(data, { bounds -> post { if (!released) onFrame(bounds) } }) {
        post { if (!released) onError() }
    }
    private var released = false

    init {
        holder.addCallback(this)
        contentDescription = "Модель в 3D"
    }

    fun setRenderingActive(active: Boolean) {
        worker.post { session.setActive(active) }
    }

    fun changeCamera(dragX: Float, dragY: Float, zoom: Float, pan: Boolean) {
        worker.post { session.changeCamera(dragX, dragY, zoom, pan) }
    }

    fun fit(resetAngles: Boolean) {
        worker.post { session.fit(resetAngles) }
    }

    fun release() {
        if (released) return
        released = true
        worker.post { session.close() }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val window = holder.surface
        val width = holder.surfaceFrame.width()
        val height = holder.surfaceFrame.height()
        worker.post { session.attach(window, width, height) }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        worker.post { session.resize(width, height) }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        worker.post { session.detach() }
    }

}

private object ModelRenderThread {
    val handler by lazy { Handler(HandlerThread("FGallery 3D").apply { start() }.looper) }
}

/** Owns all Filament calls, including asset parsing and renderer teardown, on the render thread. */
private class RenderSession(
    private var model: ByteBuffer?,
    private val onFrame: (ModelDimensions) -> Unit,
    private val onError: () -> Unit,
) {
    private var engine: Engine? = null
    private var objects: RenderObjects? = null
    private var swapChain: SwapChain? = null
    private var surface: Surface? = null
    private var closed = false
    private var active = false
    private var framePending = false
    private var firstFrame = true
    private var width = 0
    private var height = 0
    private val orbit = OrbitCamera()
    private val frameCallback = Choreographer.FrameCallback { timestamp ->
        framePending = false
        guarded { draw(timestamp) }
    }

    fun attach(window: Surface, width: Int, height: Int) = guarded {
        detach()
        if (!window.isValid) return@guarded
        if (engine == null) {
            Gltfio.init()
            checkNotNull(model)
            val created = Engine.create(Engine.Backend.OPENGL)
            engine = created
            val content = RenderObjects(created)
            objects = content
            content.load(checkNotNull(model))
            model = null
        }
        if (!window.isValid) return@guarded
        surface = window
        swapChain = checkNotNull(engine).createSwapChain(window)
        resize(width, height)
    }

    fun resize(width: Int, height: Int) = guarded {
        if (width <= 0 || height <= 0) return@guarded
        this.width = width
        this.height = height
        objects?.view?.viewport = Viewport(0, 0, width, height)
        invalidate()
    }

    fun setActive(value: Boolean) {
        if (closed || active == value) return
        active = value
        if (active) invalidate() else cancelFrame()
    }

    fun changeCamera(dragX: Float, dragY: Float, zoom: Float, pan: Boolean) {
        if (closed || height <= 0) return
        if (pan) orbit.pan(dragX / height, dragY / height, zoom)
        else orbit.rotate(dragX, dragY)
        invalidate()
    }

    fun fit(resetAngles: Boolean) {
        if (closed) return
        orbit.fit(resetAngles)
        invalidate()
    }

    private fun invalidate() {
        if (closed || !active || swapChain == null || framePending || width <= 0 || height <= 0) return
        framePending = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun cancelFrame() {
        if (!framePending) return
        framePending = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun draw(timestamp: Long) {
        if (!active || surface?.isValid != true) return
        val chain = swapChain ?: return
        val view = objects ?: return
        if (!view.updateResources()) {
            invalidate()
            return
        }
        orbit.apply(view.camera, width.toDouble() / height)
        if (!view.renderer.beginFrame(chain, timestamp)) {
            invalidate()
            return
        }
        view.renderer.render(view.view)
        view.renderer.endFrame()
        if (firstFrame) {
            firstFrame = false
            onFrame(checkNotNull(view.dimensions))
            // Render once more after the initial uploads reach the swap chain.
            invalidate()
        }
    }

    fun detach() {
        cancelFrame()
        swapChain?.let { chain ->
            engine?.destroySwapChain(chain)
            engine?.flushAndWait()
        }
        swapChain = null
        // SurfaceView owns this window and releases it when its holder is destroyed.
        surface = null
    }

    fun close() {
        if (closed) return
        closed = true
        detach()
        try {
            objects?.destroy()
        } finally {
            engine?.destroy()
            objects = null
            engine = null
            model = null
        }
    }

    private inline fun guarded(action: () -> Unit) {
        if (closed) return
        try {
            action()
        } catch (failure: Exception) {
            android.util.Log.w("FGallery3D", "Model rendering failed", failure)
            try {
                close()
            } finally {
                onError()
            }
        } catch (failure: LinkageError) {
            android.util.Log.w("FGallery3D", "Renderer unavailable for this ABI", failure)
            try {
                close()
            } finally {
                onError()
            }
        }
    }
}

private class RenderObjects(private val engine: Engine) {
    val renderer = engine.createRenderer().apply {
        setClearOptions(Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(0.07, 0.08, 0.10, 1.0)
        })
    }
    private val scene = engine.createScene()
    private val entities = engine.entityManager
    private val cameraEntity = entities.create()
    val camera = engine.createCamera(cameraEntity)
    val view = engine.createView().apply {
        setCamera(this@RenderObjects.camera)
        setScene(this@RenderObjects.scene)
        setShadowingEnabled(false)
        setPostProcessingEnabled(false)
    }
    private val materials = UbershaderProvider(engine)
    private val assets = AssetLoader(engine, materials, entities)
    private val resources = ResourceLoader(engine)
    private var asset: FilamentAsset? = null
    private var loading = false
    var dimensions: ModelDimensions? = null
        private set
    private var fill: IndirectLight? = null
    private val lights = mutableListOf<Int>()

    fun load(model: ByteBuffer) {
        val loaded = assets.createAsset(model) ?: error("Invalid GLB asset")
        asset = loaded
        check(loaded.resourceUris.isEmpty() && loaded.renderableEntities.isNotEmpty())
        val bounds = loaded.boundingBox
        val (x, y, z) = bounds.center
        val (w, h, d) = bounds.halfExtent.map(Float::toDouble)
        val radius = sqrt(w * w + h * h + d * d)
        require(listOf(w, h, d).all { it.isFinite() && it >= 0 } && radius in 1e-16..1e16)
        require(listOf(x, y, z).all { it.isFinite() && kotlin.math.abs(it / radius) <= 1e8 })
        dimensions = ModelDimensions(w * 2, h * 2, d * 2)
        // Fit a unit sphere for a stable camera without changing the reported source dimensions.
        val scale = (1.0 / radius).toFloat()
        val transforms = engine.transformManager
        transforms.setTransform(
            transforms.getInstance(loaded.root),
            floatArrayOf(
                scale, 0f, 0f, 0f, 0f, scale, 0f, 0f, 0f, 0f, scale, 0f,
                -x * scale, -y * scale, -z * scale, 1f,
            ),
        )
        fill = IndirectLight.Builder().irradiance(1, floatArrayOf(0.65f, 0.65f, 0.65f))
            .intensity(24_000f).build(engine)
        scene.indirectLight = fill
        light(-0.6f, -0.9f, -0.5f, 75_000f)
        light(0.7f, 0.2f, 0.9f, 35_000f)
        check(resources.asyncBeginLoad(loaded)) { "Could not load the asset resources" }
        loading = true
    }

    private fun light(x: Float, y: Float, z: Float, lux: Float) {
        val entity = entities.create()
        lights += entity
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .direction(x, y, z).intensity(lux).castShadows(false).build(engine, entity)
        scene.addEntity(entity)
    }

    fun updateResources(): Boolean {
        if (loading) {
            resources.asyncUpdateLoad()
            if (resources.asyncGetLoadProgress() < 1f) return false
            val ready = checkNotNull(asset)
            scene.addEntities(ready.renderableEntities)
            ready.releaseSourceData()
            loading = false
        }
        return true
    }

    fun destroy() {
        if (loading) resources.asyncCancelLoad()
        asset?.let {
            scene.removeEntities(it.renderableEntities)
            assets.destroyAsset(it)
        }
        resources.destroy()
        assets.destroy()
        materials.destroyMaterials()
        materials.destroy()
        engine.destroyView(view)
        engine.destroyScene(scene)
        fill?.let(engine::destroyIndirectLight)
        lights.forEach { entity ->
            engine.destroyEntity(entity)
            entities.destroy(entity)
        }
        engine.destroyCameraComponent(cameraEntity)
        entities.destroy(cameraEntity)
        engine.destroyRenderer(renderer)
    }
}

private class OrbitCamera {
    private var yaw = 0.48
    private var pitch = 0.25
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var offsetZ = 0.0
    private var scale = 1.0
    private var distance = 3.0
    private val fov = 38.0

    fun rotate(x: Float, y: Float) {
        yaw = (yaw - x * 0.009) % (2 * PI)
        pitch = (pitch + y * 0.009).coerceIn(-1.52, 1.52)
    }

    fun pan(x: Float, y: Float, zoom: Float) {
        if (!x.isFinite() || !y.isFinite() || !zoom.isFinite() || zoom <= 0) return
        val visibleHeight = 2 * distance * tan(fov * PI / 360)
        val horizontal = -x * visibleHeight
        val vertical = y * visibleHeight
        offsetX = (offsetX + horizontal * cos(yaw) - vertical * sin(pitch) * sin(yaw)).coerceIn(-30.0, 30.0)
        offsetY = (offsetY + vertical * cos(pitch)).coerceIn(-30.0, 30.0)
        offsetZ = (offsetZ - horizontal * sin(yaw) - vertical * sin(pitch) * cos(yaw)).coerceIn(-30.0, 30.0)
        scale = (scale / zoom).coerceIn(0.12, 20.0)
    }

    fun fit(resetAngles: Boolean) {
        offsetX = 0.0
        offsetY = 0.0
        offsetZ = 0.0
        scale = 1.0
        if (resetAngles) {
            yaw = 0.48
            pitch = 0.25
        }
    }

    fun apply(camera: Camera, aspect: Double) {
        val vertical = fov * PI / 360
        val horizontal = atan(tan(vertical) * aspect)
        distance = 1.08 * scale / sin(minOf(vertical, horizontal))
        camera.setProjection(fov, aspect, 0.02, maxOf(100.0, distance + 100), Camera.Fov.VERTICAL)
        camera.lookAt(
            offsetX + distance * cos(pitch) * sin(yaw),
            offsetY + distance * sin(pitch),
            offsetZ + distance * cos(pitch) * cos(yaw),
            offsetX, offsetY, offsetZ, 0.0, 1.0, 0.0,
        )
    }
}
