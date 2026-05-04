package com.example.poseexercise

import android.graphics.PointF
import com.example.poseexercise.posedetector.logic.JointAngleCalculator
import com.google.mlkit.vision.pose.PoseLandmark
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test for JointAngleCalculator.
 * Verifies the mathematical accuracy of the angle calculation engine.
 */
class JointAngleCalculatorTest {

    @Test
    fun `calculateAngle returns 90 degrees for right angle`() {
        // Point A (0, 1), Point B (0, 0), Point C (1, 0) -> 90 degrees
        val angle = JointAngleCalculator.calculateAngle(0f, 1f, 0f, 0f, 1f, 0f)
        assertEquals(90.0, angle, 0.01)
    }

    @Test
    fun `calculateAngle returns 180 degrees for straight line`() {
        // Point A (-1, 0), Point B (0, 0), Point C (1, 0) -> 180 degrees
        val angle = JointAngleCalculator.calculateAngle(-1f, 0f, 0f, 0f, 1f, 0f)
        assertEquals(180.0, angle, 0.01)
    }

    @Test
    fun `calculateAngle returns 45 degrees`() {
        // Point A (1, 1), Point B (0, 0), Point C (1, 0) -> 45 degrees
        val angle = JointAngleCalculator.calculateAngle(1f, 1f, 0f, 0f, 1f, 0f)
        assertEquals(45.0, angle, 0.01)
    }
}
