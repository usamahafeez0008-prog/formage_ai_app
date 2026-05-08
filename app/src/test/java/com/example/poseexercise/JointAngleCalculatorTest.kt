package com.example.poseexercise

import com.example.poseexercise.posedetector.logic.JointAngleCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test for [JointAngleCalculator] (requirements §5).
 */
class JointAngleCalculatorTest {

    @Test
    fun `calculateAngleDegrees returns 90 degrees for right angle`() {
        val angle = JointAngleCalculator.calculateAngleDegrees(
            0f, 1f, 0f,
            0f, 0f, 0f,
            1f, 0f, 0f
        )
        assertEquals(90.0, angle, 0.01)
    }

    @Test
    fun `calculateAngleDegrees returns 180 degrees for straight line`() {
        val angle = JointAngleCalculator.calculateAngleDegrees(
            -1f, 0f, 0f,
            0f, 0f, 0f,
            1f, 0f, 0f
        )
        assertEquals(180.0, angle, 0.01)
    }

    @Test
    fun `calculateAngleDegrees returns 45 degrees`() {
        val angle = JointAngleCalculator.calculateAngleDegrees(
            1f, 1f, 0f,
            0f, 0f, 0f,
            1f, 0f, 0f
        )
        assertEquals(45.0, angle, 0.01)
    }
}
