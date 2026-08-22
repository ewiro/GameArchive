package com.example.gamearchive

import android.content.Context
import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal data class CoverMotion(
    val horizontal: Float = 0f,
    val vertical: Float = 0f,
    val impulseX: Float = 0f,
    val impulseY: Float = 0f,
    val depth: Float = 0f
)

@Composable
internal fun rememberCoverMotion(enabled: Boolean): State<CoverMotion> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val motion = remember { mutableStateOf(CoverMotion()) }

    DisposableEffect(context, lifecycleOwner, enabled) {
        val motionEnabled = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
        if (!enabled || !motionEnabled) {
            motion.value = CoverMotion()
            return@DisposableEffect onDispose { }
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (sensor == null) {
            motion.value = CoverMotion()
            return@DisposableEffect onDispose { }
        }

        val rotationMatrix = FloatArray(9)
        val adjustedMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var baselinePitch: Float? = null
        var baselineRoll: Float? = null
        var filteredHorizontal = 0f
        var filteredVertical = 0f
        var filteredImpulseX = 0f
        var filteredImpulseY = 0f
        var filteredDepth = 0f
        var registered = false

        fun angleDelta(value: Float, baseline: Float): Float =
            atan2(sin(value - baseline), cos(value - baseline))

        fun normalizedAcceleration(value: Float): Float {
            val magnitude = abs(value)
            if (magnitude <= ACCELERATION_DEAD_ZONE) return 0f
            val normalized = (
                (magnitude - ACCELERATION_DEAD_ZONE) /
                    (ACCELERATION_RANGE - ACCELERATION_DEAD_ZONE)
                ).coerceIn(0f, 1f)
            return if (value < 0f) -normalized else normalized
        }

        fun publishMotion() {
            val current = motion.value
            if (
                abs(current.horizontal - filteredHorizontal) >= MOTION_UPDATE_THRESHOLD ||
                abs(current.vertical - filteredVertical) >= MOTION_UPDATE_THRESHOLD ||
                abs(current.impulseX - filteredImpulseX) >= ACCELERATION_UPDATE_THRESHOLD ||
                abs(current.impulseY - filteredImpulseY) >= ACCELERATION_UPDATE_THRESHOLD ||
                abs(current.depth - filteredDepth) >= ACCELERATION_UPDATE_THRESHOLD
            ) {
                motion.value = CoverMotion(
                    horizontal = filteredHorizontal,
                    vertical = filteredVertical,
                    impulseX = filteredImpulseX,
                    impulseY = filteredImpulseY,
                    depth = filteredDepth
                )
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    @Suppress("DEPRECATION")
                    val displayRotation = (context as? Activity)
                        ?.windowManager
                        ?.defaultDisplay
                        ?.rotation
                        ?: Surface.ROTATION_0
                    val rawX = event.values[0]
                    val rawY = event.values[1]
                    val (screenX, screenY) = when (displayRotation) {
                        Surface.ROTATION_90 -> rawY to -rawX
                        Surface.ROTATION_180 -> -rawX to -rawY
                        Surface.ROTATION_270 -> -rawY to rawX
                        else -> rawX to rawY
                    }
                    val targetImpulseX = normalizedAcceleration(screenX)
                    val targetImpulseY = normalizedAcceleration(-screenY)
                    val targetDepth = normalizedAcceleration(event.values[2])
                    filteredImpulseX +=
                        (targetImpulseX - filteredImpulseX) * ACCELERATION_SMOOTHING
                    filteredImpulseY +=
                        (targetImpulseY - filteredImpulseY) * ACCELERATION_SMOOTHING
                    filteredDepth +=
                        (targetDepth - filteredDepth) * ACCELERATION_SMOOTHING
                    publishMotion()
                    return
                }

                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                @Suppress("DEPRECATION")
                val displayRotation = (context as? Activity)
                    ?.windowManager
                    ?.defaultDisplay
                    ?.rotation
                    ?: Surface.ROTATION_0
                val matrix = when (displayRotation) {
                    Surface.ROTATION_90 -> {
                        SensorManager.remapCoordinateSystem(
                            rotationMatrix,
                            SensorManager.AXIS_Y,
                            SensorManager.AXIS_MINUS_X,
                            adjustedMatrix
                        )
                        adjustedMatrix
                    }
                    Surface.ROTATION_180 -> {
                        SensorManager.remapCoordinateSystem(
                            rotationMatrix,
                            SensorManager.AXIS_MINUS_X,
                            SensorManager.AXIS_MINUS_Y,
                            adjustedMatrix
                        )
                        adjustedMatrix
                    }
                    Surface.ROTATION_270 -> {
                        SensorManager.remapCoordinateSystem(
                            rotationMatrix,
                            SensorManager.AXIS_MINUS_Y,
                            SensorManager.AXIS_X,
                            adjustedMatrix
                        )
                        adjustedMatrix
                    }
                    else -> rotationMatrix
                }
                SensorManager.getOrientation(matrix, orientation)
                val pitch = orientation[1]
                val roll = orientation[2]
                if (baselinePitch == null || baselineRoll == null) {
                    baselinePitch = pitch
                    baselineRoll = roll
                    return
                }

                val targetHorizontal = (
                    angleDelta(roll, baselineRoll!!) / MOTION_RANGE_RADIANS
                ).coerceIn(-1f, 1f)
                val targetVertical = (
                    angleDelta(pitch, baselinePitch!!) / MOTION_RANGE_RADIANS
                ).coerceIn(-1f, 1f)
                filteredHorizontal += (targetHorizontal - filteredHorizontal) * MOTION_SMOOTHING
                filteredVertical += (targetVertical - filteredVertical) * MOTION_SMOOTHING
                publishMotion()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun registerSensor() {
            if (registered) return
            baselinePitch = null
            baselineRoll = null
            filteredHorizontal = 0f
            filteredVertical = 0f
            filteredImpulseX = 0f
            filteredImpulseY = 0f
            filteredDepth = 0f
            motion.value = CoverMotion()
            registered = sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            if (registered && accelerationSensor != null) {
                sensorManager.registerListener(
                    listener,
                    accelerationSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
        }

        fun unregisterSensor() {
            if (!registered) return
            sensorManager.unregisterListener(listener)
            registered = false
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> registerSensor()
                Lifecycle.Event.ON_STOP -> unregisterSensor()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            registerSensor()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregisterSensor()
        }
    }

    return motion
}

@Composable
internal fun Modifier.metallicCoverTilt(
    motion: State<CoverMotion>,
    shape: Shape
): Modifier {
    val density = LocalDensity.current.density
    return graphicsLayer {
        val value = motion.value
        rotationX = -value.vertical * MAX_TILT_DEGREES
        rotationY = value.horizontal * MAX_TILT_DEGREES
        cameraDistance = 10f * density
        this.shape = shape
    }
}

@Composable
internal fun Modifier.profileGravityBackground(
    motion: State<CoverMotion>
): Modifier {
    val density = LocalDensity.current.density
    return graphicsLayer {
        val value = motion.value
        translationX = value.horizontal * PROFILE_BACKGROUND_OFFSET_DP * density
        translationY = -value.vertical * PROFILE_BACKGROUND_OFFSET_DP * density
        scaleX = 1f + abs(value.horizontal) * PROFILE_BACKGROUND_SCALE_X
        scaleY = 1f + abs(value.vertical) * PROFILE_BACKGROUND_SCALE_Y
    }
}

@Composable
internal fun Modifier.profileGravityForeground(
    motion: State<CoverMotion>
): Modifier {
    val density = LocalDensity.current.density
    return graphicsLayer {
        val value = motion.value
        translationX = (
            value.horizontal * MAX_SHADOW_OFFSET_DP +
                value.impulseX * PROFILE_IMPULSE_OFFSET_DP
            ) * density
        translationY = (
            -value.vertical * MAX_SHADOW_OFFSET_DP +
                value.impulseY * PROFILE_IMPULSE_OFFSET_DP
            ) * density
        rotationX = -value.vertical * MAX_TILT_DEGREES
        rotationY = value.horizontal * MAX_TILT_DEGREES
        cameraDistance = 10f * density
        val depthScale = 1f + value.depth * PROFILE_DEPTH_SCALE
        scaleX = depthScale
        scaleY = depthScale
    }
}

@Composable
internal fun Modifier.metallicCoverShadow(
    motion: State<CoverMotion>,
    shape: Shape
): Modifier {
    val density = LocalDensity.current.density
    val value = motion.value
    val tiltAmount = (abs(value.horizontal) + abs(value.vertical)).coerceIn(0f, 1f)
    return this
        .graphicsLayer {
            translationX = value.horizontal * MAX_SHADOW_OFFSET_DP * density
            translationY = -value.vertical * MAX_SHADOW_OFFSET_DP * density +
                BASE_SHADOW_OFFSET_DP * density
            scaleX = 0.94f + tiltAmount * 0.05f
            scaleY = 0.97f + tiltAmount * 0.03f
        }
        .blur(
            radius = (BASE_SHADOW_BLUR_DP + tiltAmount * EXTRA_SHADOW_BLUR_DP).dp,
            edgeTreatment = BlurredEdgeTreatment.Unbounded
        )
        .background(
            color = Color.Black.copy(alpha = BASE_SHADOW_ALPHA + tiltAmount * EXTRA_SHADOW_ALPHA),
            shape = shape
        )
}

@Composable
internal fun MetallicCoverOverlay(
    motion: State<CoverMotion>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val value = motion.value
        val raisedLeft = (-value.horizontal).coerceAtLeast(0f)
        val raisedRight = value.horizontal.coerceAtLeast(0f)
        val raisedTop = value.vertical.coerceAtLeast(0f)
        val raisedBottom = (-value.vertical).coerceAtLeast(0f)

        // Brushed metal sheen stays attached to the plate instead of sweeping over it.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.09f),
                0.18f to Color.Transparent,
                0.56f to Color.White.copy(alpha = 0.025f),
                0.82f to Color.Black.copy(alpha = 0.06f),
                1f to Color.White.copy(alpha = 0.04f)
            )
        )

        val lightShift = value.horizontal * 0.16f - value.vertical * 0.10f
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    (0.32f + lightShift).coerceIn(0.18f, 0.46f) to Color.Transparent,
                    (0.49f + lightShift).coerceIn(0.35f, 0.63f) to Color.White.copy(alpha = 0.11f),
                    (0.60f + lightShift).coerceIn(0.46f, 0.74f) to Color.Transparent,
                    1f to Color.Transparent
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )

        // Edge lighting changes with the raised and lowered sides, without a drawn border.
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0.16f + raisedLeft * 0.38f),
                0.035f to Color.White.copy(alpha = raisedLeft * 0.08f),
                0.965f to Color.Black.copy(alpha = raisedRight * 0.10f),
                1f to Color.Black.copy(alpha = 0.16f + raisedRight * 0.42f)
            )
        )
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.14f + raisedTop * 0.36f),
                0.035f to Color.White.copy(alpha = raisedTop * 0.07f),
                0.965f to Color.Black.copy(alpha = raisedBottom * 0.10f),
                1f to Color.Black.copy(alpha = 0.18f + raisedBottom * 0.42f)
            )
        )
    }
}

private const val MOTION_RANGE_RADIANS = 0.38f
private const val MOTION_SMOOTHING = 0.20f
private const val MOTION_UPDATE_THRESHOLD = 0.004f
private const val ACCELERATION_RANGE = 5f
private const val ACCELERATION_DEAD_ZONE = 0.25f
private const val ACCELERATION_SMOOTHING = 0.28f
private const val ACCELERATION_UPDATE_THRESHOLD = 0.008f
private const val MAX_TILT_DEGREES = 12f
private const val MAX_SHADOW_OFFSET_DP = 7f
private const val BASE_SHADOW_OFFSET_DP = 2f
private const val BASE_SHADOW_BLUR_DP = 4f
private const val EXTRA_SHADOW_BLUR_DP = 5f
private const val BASE_SHADOW_ALPHA = 0.20f
private const val EXTRA_SHADOW_ALPHA = 0.22f
private const val PROFILE_BACKGROUND_OFFSET_DP = 3f
private const val PROFILE_BACKGROUND_SCALE_X = 0.02f
private const val PROFILE_BACKGROUND_SCALE_Y = 0.04f
private const val PROFILE_IMPULSE_OFFSET_DP = 8f
private const val PROFILE_DEPTH_SCALE = 0.08f
