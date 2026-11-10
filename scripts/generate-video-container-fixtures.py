#!/usr/bin/env python3
"""Generate deterministic, *real* AVI/M4V container samples for manual Android16 QA.

Requires ffmpeg and ffprobe on the host. No copyrighted input files.
This is fixture preparation, NOT a playback or Android16 test.
"""
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys

OUT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "build/video-container-fixtures")
for binary in ("ffmpeg", "ffprobe"):
    if not shutil.which(binary):
        sys.exit(f"Missing {binary}; install FFmpeg before generating fixtures")
OUT.mkdir(parents=True, exist_ok=True)

expected_codecs = {
    "avi-mjpeg-pcm.avi": {"video": "mjpeg", "audio": "pcm_s16le"},
    "avi-mpeg4-mp3.avi": {"video": "mpeg4", "audio": "mp3"},
    "m4v-h264-aac.m4v": {"video": "h264", "audio": "aac"},
    "m4v-hevc-aac.m4v": {"video": "hevc", "audio": "aac"},
}

samples = {
    "avi-mjpeg-pcm.avi": ["-c:v", "mjpeg", "-q:v", "5", "-c:a", "pcm_s16le", "-f", "avi"],
    "avi-mpeg4-mp3.avi": ["-c:v", "mpeg4", "-q:v", "5", "-c:a", "libmp3lame", "-b:a", "96k", "-f", "avi"],
    "m4v-h264-aac.m4v": ["-c:v", "libx264", "-pix_fmt", "yuv420p", "-preset", "ultrafast", "-c:a", "aac", "-b:a", "96k", "-f", "mp4"],
    "m4v-hevc-aac.m4v": ["-c:v", "libx265", "-pix_fmt", "yuv420p", "-preset", "ultrafast", "-x265-params", "log-level=error", "-c:a", "aac", "-b:a", "96k", "-f", "mp4"],
}
manifest = {}
for name, encoding in samples.items():
    path = OUT / name
    cmd = [
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-f", "lavfi", "-i", "testsrc2=size=160x120:rate=12",
        "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100",
        "-t", "2", "-shortest", "-threads", "1",
        *encoding, str(path),
    ]
    subprocess.run(cmd, check=True)
    probe = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries",
         "format=format_name:stream=index,codec_type,codec_name,codec_tag_string",
         "-of", "json", str(path)],
        check=True, capture_output=True, text=True,
    )
    metadata = json.loads(probe.stdout)
    actual_codecs = {stream["codec_type"]: stream["codec_name"]
                     for stream in metadata.get("streams", [])
                     if stream.get("codec_type") in ("video", "audio")}
    if actual_codecs != expected_codecs[name]:
        raise RuntimeError(
            f"{name}: unexpected encoded streams {actual_codecs}; "
            f"expected {expected_codecs[name]}"
        )
    if not path.stat().st_size:
        raise RuntimeError(f"{name}: empty fixture")
    # Validate container bytes independently of the extension and codec names.
    with path.open("rb") as sample:
        header = sample.read(12)
    formats = metadata.get("format", {}).get("format_name", "").split(",")
    if name.endswith(".avi"):
        if header[:4] != b"RIFF" or header[8:12] != b"AVI " or "avi" not in formats:
            raise RuntimeError(f"{name}: expected a genuine RIFF AVI container")
    else:
        if header[4:8] != b"ftyp" or not ({"mov", "mp4"} & set(formats)):
            raise RuntimeError(f"{name}: expected an ISO BMFF MP4-family container")
    if len(metadata.get("streams", [])) != 2:
        raise RuntimeError(f"{name}: expected exactly one video and one audio stream")
    manifest[name] = {
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "bytes": path.stat().st_size,
        "ffprobe": metadata,
    }
(OUT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
print(f"Generated {len(manifest)} samples and manifest in {OUT}")
