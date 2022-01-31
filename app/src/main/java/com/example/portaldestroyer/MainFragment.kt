package com.example.portaldestroyer

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.*
import com.google.ar.sceneform.*
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import com.gorisse.thomas.sceneform.scene.await


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
//            loadModels()
            arFragment.arSceneView.scene.addOnUpdateListener(::sceneUpdate)
        }
    }

    private fun loadStringAssets() {
        ballAsset = getString(R.string.ballAssetUrl)
        portalAsset = getString(R.string.ballAssetUrl)
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

    private fun sceneUpdate(updatedTime: FrameTime) {
        // Let the fragment update its state first
        arFragment.onUpdate(updatedTime)

        // Stop when no frame
        val frame: Frame = arSceneView.arFrame ?: return

        // Ensure the camera is tracking to avoid errors
        if (frame.camera.trackingState == TrackingState.TRACKING) {
            placeBallModelOnStart()
            placePortalsOnNewPlanes(frame)
        }

//        logCamChildPositions()
    }

    private fun placePortalsOnNewPlanes(frame: Frame) {
        if (portalCount < maxPortalCount) {
            val planes = frame.getUpdatedTrackables(Plane::class.java)
            putPortalOnPlane(planes, frame)
        }
    }

    private fun selectPortalColorUsingFrame(frame: Frame, pose: Pose): Color {
        val image = frame.acquireCameraImage()
        // image.format == ImageFormat.YUV_420_888, so:
        val y = image.planes[0].buffer
        val u = image.planes[1].buffer
        val v = image.planes[2].buffer
        // https://stackoverflow.com/questions/52726002/camera2-captured-picture-conversion-from-yuv-420-888-to-nv21/52740776#52740776
        // https://stackoverflow.com/questions/40090681/android-camera2-api-yuv-420-888-to-jpeg
        // https://developer.android.com/reference/android/media/Image.Plane

        image.close()
        return Color(0f, 0f, 255f)
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
    private fun putPortalOnPlane(
        planes: MutableCollection<Plane>,
        frame: Frame
    ) {
        if (portalModel == null) {
            Toast.makeText(context, "Loading...", Toast.LENGTH_SHORT).show()
        }

        for (plane in planes) {
            if (plane.trackingState == TrackingState.TRACKING && plane.anchors.size < maxPortalCountPerPlane) {
                val hitTest = frame.hitTest(frame.screenCenter().x, frame.screenCenter().y)
                if (hitTest.isNotEmpty()) {
                    val hitResult = hitTest.last()
                    val portalAnchor = plane.createAnchor(hitResult.hitPose)
                    val color: Color = selectPortalColorUsingFrame(frame, hitResult.hitPose)

                    val newRenderable = getRenderableWithNewColor(portalModel, color)
                    addModelToScene(newRenderable, portalAnchor)

                    portalCount += 1
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
        val newRenderable = getRenderableWithNewColor(portalModel, Color(255.0f, 0.0f, 0.0f))
        addModelToScene(newRenderable, hitResult.createAnchor(), Vector3(0.05f, 0.05f, 0.05f))
    }

    private fun getRenderableWithNewColor(renderable: Renderable?, newColor: Color): Renderable? {
        val newRenderable = renderable?.makeCopy()
        newRenderable?.material?.setFloat3("color", newColor)
        return newRenderable
    }
    private fun removeAnchorNode(nodeToremove: AnchorNode) {
        // Based on:
        // https://stackoverflow.com/questions/58124052/how-to-remove-3d-objects-which-are-placed-on-ar-screen
        var nodeToremove: AnchorNode? = nodeToremove
        if (nodeToremove != null) {
            arFragment.arSceneView.scene.removeChild(nodeToremove)
//            anchorNodeList.remove(nodeToremove)
            nodeToremove.anchor!!.detach()
            nodeToremove.parent = null
            nodeToremove = null
        } else {
            // no anchor intersecting with a touch
        }
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

                    Log.d("PORTAL_TOUCHED","handleOnTouch");
                    // First call ArFragment's listener to handle TransformableNodes.
                    arFragment.onPeekTouch(hitTestResult, motionEvent);
                    // Check for touching a Sceneform node
                    if (hitTestResult.node != null) {
                        Log.d("PORTAL_TOUCHED", "handleOnTouch hitTestResult.getNode() != null")
                        val hitNode = hitTestResult.node
                        removeAnchorNode(hitNode as AnchorNode)
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