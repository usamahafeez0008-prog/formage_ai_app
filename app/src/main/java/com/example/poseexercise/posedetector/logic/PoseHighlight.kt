package com.example.poseexercise.posedetector.logic

/**
 * Body regions used to tint skeleton segments green (OK) vs red (form issue).
 * Expandable for future exercises / rehab modes.
 */
enum class PoseHighlight {
    LEFT_UPPER_ARM,
    LEFT_FOREARM,
    RIGHT_UPPER_ARM,
    RIGHT_FOREARM,
    LEFT_THIGH,
    LEFT_SHIN,
    RIGHT_THIGH,
    RIGHT_SHIN,
    LEFT_FOOT,
    RIGHT_FOOT,
    MID_SHOULDERS,
    MID_HIPS,
    LEFT_TRUNK,
    RIGHT_TRUNK,
    NECK,
}
