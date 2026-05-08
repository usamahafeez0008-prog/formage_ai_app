from __future__ import annotations

import csv
from pathlib import Path

import cv2
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision


ASSETS_DIR = Path("app/src/main/assets")
OUTPUT_CSV = ASSETS_DIR / "pose" / "bicep_curl.csv"
CLASS_NAME = "bicep_curl"
MODEL_PATH = Path("tools/models/pose_landmarker_lite.task")


def iter_input_videos() -> list[Path]:
    return sorted(ASSETS_DIR.glob("*.mp4"))


def main() -> None:
    videos = iter_input_videos()
    if not videos:
        raise SystemExit("No MP4 files found in app/src/main/assets")

    OUTPUT_CSV.parent.mkdir(parents=True, exist_ok=True)

    if not MODEL_PATH.exists():
        raise SystemExit(f"Model file not found: {MODEL_PATH}")

    base_options = python.BaseOptions(model_asset_path=str(MODEL_PATH))
    options = vision.PoseLandmarkerOptions(
        base_options=base_options,
        running_mode=vision.RunningMode.VIDEO,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    landmarker = vision.PoseLandmarker.create_from_options(options)

    rows: list[list[str]] = []
    global_timestamp_ms = 0
    for video in videos:
        cap = cv2.VideoCapture(str(video))
        if not cap.isOpened():
            print(f"Skipping unreadable video: {video}")
            continue

        safe_stem = video.stem.encode("ascii", errors="ignore").decode().strip().replace(" ", "_")
        if not safe_stem:
            safe_stem = "video"
        frame_idx = 0
        kept = 0
        video_stem = video.stem
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            frame_idx += 1
            # Downsample to keep dataset size manageable.
            if frame_idx % 3 != 0:
                continue

            h, w = frame.shape[:2]
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
            global_timestamp_ms += 33
            result = landmarker.detect_for_video(mp_image, global_timestamp_ms)
            if not result.pose_landmarks:
                continue

            sample_name = f"frame__{safe_stem}__{frame_idx:07d}.jpg"
            values: list[str] = [sample_name, CLASS_NAME]
            for lm in result.pose_landmarks[0]:
                x = lm.x * w
                y = lm.y * h
                z = lm.z * w
                values.extend((f"{x:.6f}", f"{y:.6f}", f"{z:.6f}"))
            rows.append(values)
            kept += 1

        cap.release()
        safe_name = video.name.encode("ascii", errors="ignore").decode() or "video.mp4"
        print(f"{safe_name}: kept {kept} frames")

    landmarker.close()

    if not rows:
        raise SystemExit("No landmarks extracted from input videos.")

    with OUTPUT_CSV.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerows(rows)

    print(f"Wrote {len(rows)} samples to {OUTPUT_CSV}")


if __name__ == "__main__":
    main()
