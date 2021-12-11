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
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.SceneView
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.ux.ArFragment
import com.gorisse.thomas.sceneform.scene.await


class MainFragment : Fragment(R.layout.fragment_main) {

    private lateinit var arFragment: ArFragment
    private val arSceneView get() = arFragment.arSceneView
    private val scene get() = arSceneView.scene
    private val camera get() = scene.camera

    private val ballAsset: String = "https://drive.google.com/uc?export=download&id=1Stbo-zW3crIAzT4LTPOFt3YIuuZVeOsB"
    private val portalAsset: String = "https://drive.google.com/uc?export=download&id=1IeCl7_idk_HwzWL62j9mMIRzpdA-qT-h"

    private var portalModel : Renderable? = null
    private var ballModel: Renderable? = null

    private var ballModelPlaced = false
    private var portalCount = 0
    private val maxPortalCount = 4
    private val maxPortalCountPerPlane: Int = 2

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arFragment = (childFragmentManager.findFragmentById(R.id.arFragment) as ArFragment).apply {
            setOnSessionConfigurationListener { session, config ->
                // Modify the AR session configuration here
            }
            setOnViewCreatedListener { arSceneView ->
                arSceneView.setFrameRateFactor(SceneView.FrameRate.FULL)
            }
            setOnTapArPlaneListener(::onTapPlane)

        }

        lifecycleScope.launchWhenCreated {
            loadModels()
            arFragment.arSceneView.scene.addOnUpdateListener(::sceneUpdate)
        }
    }

    private fun sceneUpdate(updatedTime: FrameTime){
        // Let the fragment update its state first
        arFragment.onUpdate(updatedTime);

        // Stop when no frame
        val frame: Frame = arSceneView.arFrame ?: return

        // Ensure the camera is tracking to avoid errors
        if(frame.camera.trackingState == TrackingState.TRACKING){
            if (ballModel == null) {
                Toast.makeText(context, "Loading...", Toast.LENGTH_SHORT).show()
            }
            else if(!ballModelPlaced) {

                attachModelToCamera(ballModel, Vector3(0.1f, 0.1f, 0.1f))
                ballModelPlaced = true
            }

            if(portalCount < maxPortalCount){

                val planes = frame.getUpdatedTrackables(Plane::class.java)
                putPortalOnPlane(planes, frame)
            }
        }


        logCamChildPositions()
    }

    // https://stackoverflow.com/questions/51673733/how-to-place-a-object-without-tapping-on-the-screen
    private fun putPortalOnPlane(
        planes: MutableCollection<Plane>,
        frame: Frame
    ) {
        if (portalModel == null) {
            Toast.makeText(context, "Loading...", Toast.LENGTH_SHORT).show()
        }

        for(plane in planes){
            if (plane.trackingState == TrackingState.TRACKING && plane.anchors.size < maxPortalCountPerPlane) {
                val hitTest = frame.hitTest(frame.screenCenter().x, frame.screenCenter().y)
                if (hitTest.isNotEmpty()) {
                    val hitResult = hitTest.iterator().next()
                    val portalAnchor = plane.createAnchor(hitResult.hitPose)
                    addModelToScene(portalModel, portalAnchor)
                    portalCount += 1
                }
            }
        }
    }

    // https://stackoverflow.com/questions/51673733/how-to-place-a-object-without-tapping-on-the-screen
    private fun Frame.screenCenter(): Vector3 {
        return Vector3(arSceneView.width / 2f, arSceneView.height / 2f, 0f)
    }

    private fun logCamChildPositions() {
        Log.d("sceneUpdate", "Camera position: " + camera.worldPosition)
        for (child in camera.children) {
            Log.d("sceneUpdate", "Child position: " + child.worldPosition)
        }
    }

    private suspend fun loadModels() {
        ballModel = ModelRenderable.builder()
            .setSource(context, Uri.parse(ballAsset))
            .setIsFilamentGltf(true)
            .await()
        portalModel = ModelRenderable.builder()
            .setSource(context, Uri.parse(portalAsset))
            .setIsFilamentGltf(true)
            .await()

    }

    private fun onTapPlane(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent) {
        if (portalModel == null) {
            Toast.makeText(context, "Loading...", Toast.LENGTH_SHORT).show()
            return
        }

        // Create the Anchor on a tap position
        addModelToScene(portalModel, hitResult.createAnchor(), Vector3(0.05f, 0.05f, 0.05f))
    }

    private fun addModelToScene(model: Renderable?, anchor: Anchor?, scale: Vector3 = Vector3(0.05f, 0.05f, 0.05f)) {
        scene.addChild(AnchorNode(anchor).apply {
            // Create the transformable model and add it to the anchor.
            addChild(Node().apply {
                localScale = scale
                renderable = model
                renderableInstance.animate(true).start()
            })
        })
    }

    private fun attachModelToCamera(model: Renderable?, scale: Vector3 = Vector3(0.1f, 0.1f, 0.1f)) {
        // https://stackoverflow.com/questions/59107303/placing-a-static-object-in-the-corner-of-a-screen-with-arcore
        camera.addChild(Node().apply {
                localPosition = Vector3(0.0f, -0.4f, -1.0f)
                localScale = scale
                renderable = model
                renderableInstance.animate(true).start()
            })
    }

}