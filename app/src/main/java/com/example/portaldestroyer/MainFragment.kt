package com.example.portaldestroyer

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.graphics.Bitmap
import android.media.Image
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.android.camera.utils.YuvToRgbConverter
import com.google.ar.core.*
import com.google.ar.sceneform.*
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import com.gorisse.thomas.sceneform.scene.await
import org.w3c.dom.Text
import java.time.Instant
import java.time.format.DateTimeFormatter

class MainFragment : Fragment(R.layout.fragment_main) {

    private lateinit var arFragment: ArFragment
    private val arSceneView get() = arFragment.arSceneView
    private val scene get() = arSceneView.scene
    private val camera get() = scene.camera

    private lateinit var ballAsset: String
    private lateinit var portalAsset: String

    private var portalModel: Renderable? = null
    private var ballModel: Renderable? = null

    private var ballModelPlaced = false
    private var portalCount = 0
    private val maxPortalCount = 20
    private val maxPortalCountPerPlane: Int = 2

    private var destroyedPortalCount = 0
    private lateinit var textViewScoreInt: TextView

    private var lastPlaneCreatedTimestamp: Long = 0  // in nanoseconds
    private var maxTimeBetweenPlaneCreation = 0.1 * 1000000000  // in nanoseconds

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arFragment = (childFragmentManager.findFragmentById(R.id.arFragment) as ArFragment).apply {
            setOnSessionConfigurationListener { session, config ->
            }
            setOnViewCreatedListener { arSceneView ->
                arSceneView.setFrameRateFactor(SceneView.FrameRate.FULL)
                // change to false on deploy
                arSceneView.planeRenderer.isVisible = true

            }
            setOnTapArPlaneListener(::onTapPlane)
        }

        lifecycleScope.launchWhenCreated {
            loadStringAssets()
            loadModels()
            textViewScoreInt = view.findViewById(R.id.textViewScoreInt)
            arFragment.arSceneView.scene.addOnUpdateListener(::sceneUpdate)
        }
    }

    private fun loadStringAssets() {
        ballAsset = getString(R.string.ballAssetUrl)
        portalAsset = getString(R.string.portalAssetUrl)
    }

    private suspend fun loadModels() {
        createCylinder(Color(0.0f, 0.0f, 255.0f))
        ballModel = ModelRenderable.builder()
            .setSource(context, Uri.parse(ballAsset))
            .setIsFilamentGltf(true)
            .await()
    }

    private fun createCylinder(color: Color) {
        MaterialFactory.makeOpaqueWithColor(arFragment.context, color)
            .thenAccept {
                portalModel = ShapeFactory.makeCylinder(2.0f, 0.1f, Vector3(0.0f, 0.0f, 0.0f), it)
            }
    }

    @SuppressLint("NewApi")
    private fun sceneUpdate(updatedTime: FrameTime) {
        // Let the fragment update its state first
        arFragment.onUpdate(updatedTime)

        // Stop when no frame
        val frame: Frame = arSceneView.arFrame ?: return

        // Ensure the camera is tracking to avoid errors
        if (frame.camera.trackingState == TrackingState.TRACKING) {
            placeBallModelOnStart()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val image = frame.acquireCameraImage()
                selectPortalColorUsingFrame(image, 0, 0)
            }
            placePortalsOnNewPlanes(frame)
        }

        // display current score
        textViewScoreInt.text = destroyedPortalCount.toString()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun placePortalsOnNewPlanes(frame: Frame) {
        if (portalCount < maxPortalCount) {
            val planes = frame.getUpdatedTrackables(Plane::class.java)
            putPortalOnPlane(planes, frame)
        }
    }

    @SuppressLint("NewApi")
    private fun selectPortalColorUsingFrame(image: Image, x: Int, y: Int): android.graphics.Color {
        // image.format == ImageFormat.YUV_420_888, so:
        val yuvToRgbConverter = YuvToRgbConverter(requireContext())
        val bmp = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        yuvToRgbConverter.yuvToRgb(image, bmp)

        Log.d("selectPortalColor", "X=$x, Y=$y")
        val rgb_color = bmp.getColor(x, y)
        Log.d("Color on screen", rgb_color.toString())
        image.close()
        return rgb_color
    }

    private fun placeBallModelOnStart() {
        if (ballModel == null) {
            Toast.makeText(context, "Loading...", Toast.LENGTH_SHORT).show()
        } else if (!ballModelPlaced) {
            attachModelToCamera(ballModel, Vector3(0.1f, 0.1f, 0.1f))
            ballModelPlaced = true
        }
    }

    // https://stackoverflow.com/questions/51673733/how-to-place-a-object-without-tapping-on-the-creen
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun putPortalOnPlane(
        planes: MutableCollection<Plane>,
        frame: Frame
    ) {
        if (portalModel == null) {
            Toast.makeText(context, "Loading...", Toast.LENGTH_SHORT).show()
        }
        if(planes.isEmpty()){
            Log.d("putPortalOnPlane","updatetrackables returned 0 planes")
        }

        for (plane in planes) {
            if (plane.trackingState == TrackingState.TRACKING && plane.anchors.size < maxPortalCountPerPlane) {
                val hitTest = frame.hitTest(frame.screenCenter().x, frame.screenCenter().y)
                if (hitTest.isNotEmpty()) {
                    val newTimestamp = frame.timestamp
                    if(newTimestamp - lastPlaneCreatedTimestamp > maxTimeBetweenPlaneCreation){
                        val hitResult = hitTest.last()
                        val portalAnchor = plane.createAnchor(hitResult.hitPose)
                        val image = frame.acquireCameraImage()
                        val color = selectPortalColorUsingFrame(image, image.width/2, image.height/2)
                        // convert Color object
                        val sceneformColor = Color(color.toArgb())
                        val newRenderable = getRenderableWithNewColor(portalModel, sceneformColor)
                        addModelToScene(newRenderable, portalAnchor)
                        portalCount += 1
                    }
                    lastPlaneCreatedTimestamp = newTimestamp
                }
            }
        }
    }

    // https://stackoverflow.com/questions/51673733/how-to-place-a-object-without-tapping-on-the-screen
    @Suppress("unused")
    private fun Frame.screenCenter(): Vector3 {
        return Vector3(arSceneView.width / 2f, arSceneView.height / 2f, 0f)
    }

    private fun logCamChildPositions() {
        Log.d("sceneUpdate", "Camera position: " + camera.worldPosition)
        for (child in camera.children) {
            Log.d("sceneUpdate", "Child position: " + child.worldPosition)
        }
    }


    @Suppress("UNUSED_PARAMETER")
    private fun onTapPlane(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent) {
        if (portalModel == null) {
            Toast.makeText(context, "Loading...", Toast.LENGTH_SHORT).show()
            return
        }

        // Create the Anchor on a tap position
//        val newRenderable = getRenderableWithNewColor(portalModel, Color(255.0f, 0.0f, 0.0f))
//        addModelToScene(newRenderable, hitResult.createAnchor(), Vector3(0.05f, 0.05f, 0.05f))
    }

    private fun getRenderableWithNewColor(renderable: Renderable?, newColor: Color): Renderable? {
        val newRenderable = renderable?.makeCopy()
        newRenderable?.material?.setFloat3("color", newColor)
        return newRenderable
    }

    private fun addModelToScene(
        model: Renderable?,
        anchor: Anchor?,
        scale: Vector3 = Vector3(0.05f, 0.05f, 0.05f)
    ) {
        scene.addChild(AnchorNode(anchor).apply {
            // Create the transformable model and add it to the anchor.
            addChild(Node().apply {
                localScale = scale
                renderable = model
                renderableInstance.animate(true).start()
                setOnTouchListener { hitTestResult, motionEvent ->
                    arFragment.onPeekTouch(hitTestResult, motionEvent);
                    if (hitTestResult.node != null) {
                        val hitNode = hitTestResult.node
                        hitNode?.parent = null
                        destroyedPortalCount += 1
                        portalCount -= 1
                        Log.d("portalTouched", "Destroyed portal count: $destroyedPortalCount")
                    }
                    return@setOnTouchListener true
                }
            })
        })
    }

    private fun attachModelToCamera(
        model: Renderable?,
        scale: Vector3 = Vector3(0.1f, 0.1f, 0.1f)
    ) {
        // https://stackoverflow.com/questions/59107303/placing-a-static-object-in-the-corner-of-a-screen-with-arcore
        camera.addChild(Node().apply {
            localPosition = Vector3(0.0f, -0.4f, -1.0f)
            localScale = scale
            renderable = model
            renderableInstance.animate(true).start()
            setOnTouchListener { hitTestResult, motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_DOWN || motionEvent.action == MotionEvent.ACTION_MOVE) {
                    val screenHitPointRay = camera.screenPointToRay(motionEvent.x, motionEvent.y)
                    val rayToLocal = camera.worldToLocalPoint(screenHitPointRay.origin)
                    localPosition = Vector3(rayToLocal.x*100, rayToLocal.y*100, -1f)
                }
                else if(motionEvent.action == MotionEvent.ACTION_UP){
                    localPosition = Vector3(0f, -0.4f, -1f)
                }
                return@setOnTouchListener true
            }
        })
    }
}