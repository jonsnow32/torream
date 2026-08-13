---
sidebar_position: 2
---

# Video Player

Torream's video player is the heart of the application, built on Media3 (ExoPlayer) with extensive customizations for an exceptional viewing experience.

## Playback Features

### Supported Formats

**Video Codecs**:
- H.264/AVC (most common)
- H.265/HEVC (4K videos)
- VP9 (YouTube, web videos)
- AV1 (next-gen codec)
- MPEG-4

**Audio Codecs**:
- AAC
- MP3
- Opus
- Vorbis
- AC3/E-AC3 (surround sound)
- DTS

**Container Formats**:
- MP4 (.mp4)
- Matroska (.mkv)
- WebM (.webm)
- AVI (.avi)
- FLV (.flv)
- 3GP (.3gp)
- MOV (.mov)

**Streaming Protocols**:
- HTTP/HTTPS direct
- HLS (.m3u8)
- DASH (.mpd)
- Local files (file://)
- Torrent streaming (http://127.0.0.1)

### Player Controls

#### Basic Controls
- **Play/Pause**: Tap center or use button
- **Seek**: Drag seek bar or swipe horizontally
- **Volume**: Swipe up/down on right side
- **Brightness**: Swipe up/down on left side
- **Fullscreen**: Toggle button or rotate device

#### Advanced Controls
- **Double tap left**: Rewind 10 seconds
- **Double tap right**: Forward 10 seconds
- **Pinch to zoom**: Adjust aspect ratio
- **Lock button**: Prevent accidental touches
- **Playback speed**: 0.25x, 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 1.75x, 2.0x

### Quality Selection

**Adaptive Streaming**:
- Auto quality based on network
- Manual quality selection
- Supported resolutions: 144p to 4K

**Settings**:
- Preferred quality
- Auto-switch on network change
- Data saver mode

### Subtitle Support

**Supported Formats**:
- SubRip (.srt)
- Advanced SubStation Alpha (.ass)
- WebVTT (.vtt)
- SubStation Alpha (.ssa)

**Features**:
- Load external subtitle files
- Select from embedded subtitles
- Multiple subtitle tracks
- Subtitle synchronization (delay adjustment)

**Customization**:
- Font size: Small, Normal, Large, Extra Large
- Font color: White, Yellow, Red, Green, Blue
- Background: Transparent, Semi-transparent, Solid
- Position: Bottom, Top
- Encoding: Auto-detect or manual selection

### Audio Tracks

**Features**:
- Multiple audio track support
- Language selection
- Audio delay adjustment
- Audio boost (volume amplification)

### Picture-in-Picture (PiP)

**Android 8.0+ (API 26+)**:
- Continue watching while browsing
- Automatic aspect ratio
- Resize and position window
- Play/pause control

**Usage**:
- Press home button during playback
- Or tap PiP button in controls

### Gesture Controls

**Seek Gestures**:
- **Swipe right**: Fast forward
- **Swipe left**: Rewind
- **Double tap sides**: Skip 10 seconds

**Brightness & Volume**:
- **Swipe up (left)**: Increase brightness
- **Swipe down (left)**: Decrease brightness
- **Swipe up (right)**: Increase volume
- **Swipe down (right)**: Decrease volume

**Zoom**:
- **Pinch out**: Zoom in (crop)
- **Pinch in**: Zoom out (fit)
- **Double tap center**: Toggle fit/crop

### Preview Thumbnails

Seek bar shows video thumbnails:
- Hover over seek bar
- Preview frame at position
- Fast visual navigation

## Player Settings

### Playback Preferences

**Default Behavior**:
- Auto-play next video
- Resume last position
- Remember playback speed
- Remember subtitle selection

**Advanced Options**:
- Hardware acceleration
- Audio output: Speaker, Bluetooth, Wired
- Video scale mode: Fit, Crop, Stretch
- Background playback (audio only)

### Performance Settings

**Buffer Configuration**:
- Buffer size: Small, Medium, Large
- Preload duration
- Rebuffer threshold

**Battery Optimization**:
- Reduce frame rate on battery
- Lower quality in power saver
- Dim screen during playback

## Advanced Features

### Chromecast Casting

Stream to your TV:
1. Tap Cast button
2. Select Chromecast device
3. Control playback from phone

**Features**:
- Queue management
- Subtitle support
- Audio track selection
- Background casting

### Background Playback

Continue listening:
- Audio continues when screen off
- Lock screen controls
- Notification controls
- Bluetooth controls

### Lua Scripting

Torream's player is built on native mpv, which exposes mpv's own Lua scripting API. Settings → Player → **Lua Scripts** gives you an on-device manager for it:

- **Built-in editor**: write scripts directly on your device, with syntax highlighting for comments, strings, numbers, and keywords
- **Enable/disable**: toggle a script off without deleting it
- **Multiple scripts**: create, edit, and remove as many as you like
- Scripts are loaded into mpv on player startup — restart playback after editing to apply changes

This is the same scripting environment as desktop mpv, so existing mpv Lua scripts (key bindings, OSD customization, automation) generally work unmodified.

### Playlist Playback

**Auto-Play Next**:
- Automatically play next video
- Queue videos in playlist
- Shuffle mode
- Repeat options: Off, One, All

### Watch History

**Automatic Tracking**:
- Resume position saved
- Recently watched list
- Viewing statistics
- Clear history option

## Troubleshooting

### Common Issues

**Video won't play**:
- Check file format compatibility
- Try different quality
- Clear app cache
- Update app

**Subtitles not showing**:
- Check subtitle file encoding
- Adjust subtitle sync
- Verify file format

**Buffering issues**:
- Check network connection
- Lower quality setting
- Clear cache
- Increase buffer size

**Audio out of sync**:
- Use audio delay adjustment
- Try different audio track
- Check source file

## Keyboard Shortcuts

**Playback**:
- `Space`: Play/Pause
- `←` / `→`: Seek -10s / +10s
- `↑` / `↓`: Volume up/down
- `F`: Fullscreen
- `M`: Mute

**Speed**:
- `[` / `]`: Decrease/increase speed
- `0-9`: Jump to percentage

## Tips & Tricks

1. **Double tap controls**: Quick skip 10 seconds
2. **Gesture seek**: Swipe for precise seeking
3. **Lock button**: Prevent pocket controls
4. **Zoom gestures**: Perfect video framing
5. **Subtitle sync**: Adjust timing on the fly
6. **Audio boost**: Amplify quiet videos
7. **Speed control**: Watch faster or slower
8. **PiP mode**: Multitask while watching

## Summary

Torream's video player provides:

✅ Wide format support
✅ Intuitive gesture controls
✅ Advanced subtitle options
✅ Picture-in-Picture mode
✅ Chromecast integration
✅ Customizable playback
✅ Performance optimization
✅ Rich feature set