package com.example.portaldestroyer

import android.net.Uri
import android.os.Bundle
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
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.gorisse.thomas.sceneform.scene.await

class MainFragment : Fragment(R.layout.fragment_main) {

    private lateinit var arFragment: ArFragment
    private val arSceneView get() = arFragment.arSceneView
    private val scene get() = arSceneView.scene
    private val camera get() = scene.camera
    private val ballAsset: String = "https://drive.google.com/uc?export=download&id=1Stbo-zW3crIAzT4LTPOFt3YIuuZVeOsB"

    private var modelBall: Renderable? = null
    private var modelPlaced = false
    private var modelView: ViewRenderable? = null

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

    private fun createBallAnchor(){
        val pos = floatArrayOf(0.0f, 0.0f, 1.0f)
        val rot = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)

        val anchor = arSceneView.session?.createAnchor(Pose(pos, rot))
        createCameraAnchor(modelBall, anchor)
    }

    private fun sceneUpdate(updatedTime: FrameTime){
        // Let the fragment update its state first
        arFragment.onUpdate(updatedTime);

        // Stop when no frame
        val frame: Frame = arSceneView.arFrame ?: return
//        val plane = frame.getUpdatedTrackables(Plane::class.java)

        if(!modelPlaced and (frame.camera.trackingState == TrackingState.TRACKING)) {
            createBallAnchor()
            modelPlaced = true
        }
    }

    private suspend fun loadModels() {
        modelBall = ModelRenderable.builder()
            .setSource(context, Uri.parse(ballAsset))
            .setIsFilamentGltf(true)
            .await()
        modelView = ViewRenderable.builder()
            .setView(context, R.layout.view_renderable_infos)
            .await()
    }

    private fun onTapPlane(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent) {
        if (modelBall == null || modelView == null) {
            Toast.makeText(context, "Loading...", Toast.LENGTH_SHORT).show()
            return
        }

        // Create the Anchor on a tap position
        createSceneAnchor(modelBall, hitResult.createAnchor())
    }

    private fun createSceneAnchor(model: Renderable?, anchor: Anchor?, scale: Vector3 = Vector3(0.05f, 0.05f, 0.05f)) {
        scene.addChild(AnchorNode(anchor).apply {
            // Create the transformable model and add it to the anchor.
            addChild(Node().apply {
                localScale = scale
                renderable = model
                renderableInstance.animate(true).start()
            })
        })
    }

    private fun createCameraAnchor(model: Renderable?, anchor: Anchor?, scale: Vector3 = Vector3(0.05f, 0.05f, 0.05f)) {
        camera.addChild(AnchorNode(anchor).apply {
            // Create the transformable model and add it to the anchor.
            addChild(Node().apply {
                localScale = scale
                renderable = model
                renderableInstance.animate(true).start()
            })
        })
    }

}