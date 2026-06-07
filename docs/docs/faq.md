---
sidebar_position: 10
---

# Frequently Asked Questions (FAQ)

Common questions and answers about Torream.

## General Questions

### What is Torream?

Torream is a feature-rich video player and download manager for Android devices and Android TV. It supports multiple video formats, download protocols (HTTP, HLS, Torrent), and includes advanced features like Chromecast support and subtitle customization.

### Is Torream free?

Yes, Torream is free to use with ad-supported features. A pro version with no ads and additional features may be available.

### What Android versions are supported?

Torream requires Android 6.0 (API 23) or higher. It's optimized for Android 6.0 through Android 14+.

### Does it work on Android TV?

Yes! Torream is fully optimized for Android TV with a leanback UI and D-pad navigation.

## Installation & Setup

### How do I install Torream?

1. Download the APK from the releases page
2. Enable "Install from Unknown Sources" in settings
3. Open the downloaded APK file
4. Follow the installation prompts

### Which APK variant should I download?

- **arm64**: For most modern devices (recommended)
- **arm32**: For older 32-bit devices
- **universal**: Works on all devices (larger size)
- **x86/x86-64**: For Intel-based devices (rare)

Check your device's CPU architecture in Settings > About Phone.

### Do I need to grant storage permissions?

Yes, Torream needs storage access to:
- Save downloaded files
- Access local video files
- Store thumbnails and cache

On Android 13+, you'll be asked for specific media permissions.

## Playback Questions

### What video formats are supported?

Torream supports:
- **Containers**: MP4, MKV, AVI, WebM, FLV, MOV
- **Video Codecs**: H.264, H.265, VP9, AV1
- **Audio Codecs**: AAC, MP3, Opus, AC3, DTS

### Why won't my video play?

Common reasons:
1. **Unsupported codec**: Very rare, but some exotic codecs may not work
2. **Corrupted file**: Try playing in another app to verify
3. **Missing permissions**: Grant storage access
4. **Insufficient space**: Free up storage

**Solution**: Check file format, update app, or report the issue.

### How do I add subtitles?

1. Tap the subtitle button during playback
2. Select "Load subtitle file"
3. Browse and select your subtitle file (.srt, .ass, .vtt)
4. Adjust timing if needed

### Can I change playback speed?

Yes! Tap the speed button (1x) and select from 0.25x to 2.0x speed. Great for lectures or slow-motion viewing.

### How do I cast to Chromecast?

1. Ensure your phone and Chromecast are on the same WiFi
2. Tap the Cast button in the player
3. Select your Chromecast device
4. Control playback from your phone

## Download Questions

### How do I download videos?

1. Tap the download button on any video
2. Or paste URL in the "Add Download" dialog
3. Select quality and location
4. Download starts automatically

### What download types are supported?

- **HTTP/HTTPS**: Direct downloads
- **HLS (.m3u8)**: Segmented streaming videos
- **Torrents**: Magnet links and .torrent files

### Can I resume interrupted downloads?

Yes! Torream automatically resumes downloads when:
- Network is restored
- App is reopened
- Device is restarted

### Where are downloads saved?

Default location: `Internal Storage/Torream/Downloads`

You can change this in Settings > Downloads > Download Location.

### Can I download multiple files at once?

Yes! Torream supports parallel downloads (default: 3 concurrent downloads).

### Do downloads work in background?

Yes, downloads continue even when:
- App is in background
- Screen is off
- Using other apps

Note: Battery optimization may affect background downloads. Disable battery optimization for Torream in system settings.

## Torrent Questions

### How do I use torrents?

1. Copy magnet link or download .torrent file
2. Paste in "Add Download" or open .torrent file
3. Torream will start downloading
4. Watch while downloading (streaming mode)

### Can I stream torrents before download completes?

Yes! Torream uses sequential downloading and local HTTP streaming to enable playback while downloading.

### Is torrent downloading safe?

Torrent downloading itself is safe, but be cautious about:
- Copyright laws in your country
- Downloading from trusted sources only
- Using VPN if needed

Torream is just a tool; users are responsible for legal use.

### How do I control torrent speed?

Settings > Downloads > Torrent Settings:
- Set upload/download speed limits
- Enable/disable DHT
- Configure max connections

## Android TV Questions

### How do I navigate on TV?

Use your TV remote:
- **D-pad**: Navigate menus
- **OK/Enter**: Select
- **Back**: Go back
- **Play/Pause**: Control playback

### Can I use voice search?

Yes, if your Android TV supports Google Assistant, you can use voice commands to search for content.

### Is mouse/keyboard supported?

Yes! Connect a mouse or keyboard via Bluetooth or USB for easier navigation.

## Library & Organization

### How do I create playlists?

1. Go to Library tab
2. Tap "+" button
3. Name your playlist
4. Add videos by long-pressing and selecting "Add to playlist"

### Can I organize videos into folders?

Torream automatically organizes by:
- All Media
- Recently Added
- Favorites
- Playlists

Files remain in their original locations on storage.

### How do I mark favorites?

Long-press any video and tap the star icon, or tap the star during playback.

## Troubleshooting

### App crashes on startup

**Solutions**:
1. Clear app cache: Settings > Apps > Torream > Clear Cache
2. Clear app data (resets settings)
3. Reinstall the app
4. Update to latest version

### Videos are choppy/laggy

**Solutions**:
1. Lower video quality
2. Enable hardware acceleration: Settings > Player > Hardware Acceleration
3. Close other apps
4. Clear app cache
5. Free up device storage

### Downloads keep failing

**Solutions**:
1. Check internet connection
2. Verify storage space available
3. Disable battery optimization for Torream
4. Try different network (WiFi vs cellular)
5. Check if URL is still valid

### Subtitles not syncing

**Solution**:
- Use subtitle delay adjustment: Tap subtitle button > Delay > Adjust timing
- Positive values delay subtitles
- Negative values advance subtitles

### High battery usage

**Solutions**:
1. Enable battery saver in player settings
2. Lower video quality
3. Reduce brightness during playback
4. Close background downloads when not needed

## Privacy & Security

### Does Torream collect my data?

Torream is privacy-focused:
- No account required
- No tracking of viewing habits
- Data stays on your device
- Ad networks may collect anonymized data (standard practice)

### Can I disable ads?

Ads support development. A pro version without ads may be available through in-app purchase.

### Is my download history private?

Yes, all download history is stored locally on your device. You can clear it anytime in Settings > Downloads > Clear History.

## Updates & Support

### How do I update Torream?

**Manual Update**:
1. Download latest APK from releases
2. Install over existing app (data preserved)

**Future**: Auto-update may be added.

### How do I report bugs?

1. Open GitHub Issues
2. Provide:
   - Device model and Android version
   - App version
   - Steps to reproduce
   - Screenshots if possible

### How do I request features?

Open a feature request on GitHub Issues or join community discussions.

### Where can I get help?

- **Documentation**: Read these docs thoroughly
- **GitHub Issues**: Search existing issues
- **Community**: Join discussions on GitHub
- **Email**: Contact developer (if provided)

## Performance & Storage

### How much storage does the app use?

App size: 15-40 MB (depending on variant)

Additional space used by:
- Downloaded videos (user-controlled)
- Thumbnails cache (~50-200 MB)
- App data (~10-50 MB)

### How do I free up space?

Settings > Storage:
- Clear thumbnail cache
- Delete completed downloads
- Clear temporary files

### Why is the app slow?

**Common causes**:
1. Low device storage &lt;500 MB free
2. Too many cached thumbnails
3. Large download queue
4. Old Android version

**Solutions**:
- Free up storage
- Clear cache
- Close unused apps
- Restart device

## Advanced Questions

### Can I change the download location?

Yes, in Settings > Downloads > Download Location. You can select any folder accessible via Storage Access Framework (SAF).

### Does it support external storage/SD card?

Yes, you can download to SD card on devices that support it (Android 10 and below have better support).

### Can I use Torream as default video player?

Yes! When you open a video file, Android will ask which app to use. Select Torream and choose "Always" to set as default.

### Is there an API or automation support?

Currently, Torream doesn't expose a public API. This may be added in future versions.

## Still Have Questions?

If your question isn't answered here:

1. Check the documentation sections for detailed information
2. Search GitHub Issues for similar questions
3. Open a new issue with your question
4. Join community discussions

---

**Last Updated**: Build 119 (Version 1.1.9)