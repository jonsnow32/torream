---
sidebar_position: 3
---

# Download Manager

ZippyPlayer features a powerful download manager that supports multiple protocols and provides reliable background downloading with advanced features.

## Overview

The download manager handles:
- HTTP/HTTPS direct downloads
- HLS segmented video downloads  
- Torrent downloads (magnet links and .torrent files)
- Resume/pause functionality
- Background downloading
- Queue management

## Starting Downloads

### From URL

1. Tap the "+" button in Downloads tab
2. Paste video URL
3. Select quality (if available)
4. Choose save location
5. Tap "Download"

### From Browser

1. Copy video URL from browser
2. Open ZippyPlayer
3. It will detect the URL and prompt to download

### From Video Player

1. While watching a video
2. Tap the download icon
3. Select quality
4. Download starts automatically

## Download Types

### HTTP Downloads

Direct file downloads via HTTP/HTTPS:
- Single-file downloads
- Resume support (if server supports Range headers)
- Progress tracking
- Speed monitoring

**Advantages**:
- Fast and simple
- Low resource usage
- Reliable

### HLS Downloads

HTTP Live Streaming (m3u8) downloads:
- Parses m3u8 manifest
- Downloads all segments
- Merges into single file
- Supports encryption

**Advantages**:
- Stream-quality videos
- Multiple quality options
- Widely used format

### Torrent Downloads

P2P downloads via BitTorrent protocol:
- Magnet links
- .torrent files
- DHT support
- Streaming while downloading

**Advantages**:
- Distributed downloads
- Faster for popular content
- Can stream before completion

## Managing Downloads

### Download Queue

**View All Downloads**:
- Active downloads
- Queued downloads
- Completed downloads
- Failed downloads

**Sort Options**:
- By date added
- By progress
- By file size
- By name

### Download Controls

**Per Download**:
- Pause: Stop download temporarily
- Resume: Continue paused download
- Cancel: Stop and remove
- Retry: Restart failed download
- Delete: Remove from list (keeps file)

**Bulk Actions**:
- Pause all
- Resume all
- Clear completed
- Delete failed

## Download Settings

### General Settings

**Location**:
- Default download folder
- Per-download location choice
- SD card support

**Behavior**:
- Auto-start downloads
- Concurrent downloads limit (1-5)
- Auto-retry on failure
- Notification preferences

### Network Settings

**Connection**:
- WiFi only mode
- Cellular data limit
- Metered connection handling
- Speed limits (upload/download)

**Optimization**:
- Chunk size for parallel downloads
- Timeout settings
- Retry attempts
- Buffer size

### Torrent Settings

**Connections**:
- Max connections per torrent
- Max total connections
- DHT (Distributed Hash Table)
- PEX (Peer Exchange)
- LSD (Local Service Discovery)

**Limits**:
- Upload speed limit
- Download speed limit
- Seed after download
- Seeding ratio limit

## Background Downloads

### How It Works

Downloads continue when:
- Screen is off
- Using other apps
- App is in background
- Device restarts (resumes automatically)

### Requirements

**Permissions**:
- Storage access
- Notification permission (Android 13+)
- Background restrictions disabled

**Battery Optimization**:
For reliable downloads, disable battery optimization:
1. Settings > Apps > ZippyPlayer
2. Battery > Unrestricted

### Notifications

**Download Progress**:
- Ongoing notification with progress bar
- Current speed
- Time remaining estimate
- Tap to open app

**Completion**:
- Notification when done
- Tap to play video
- Actions: Play, Delete, Share

## Advanced Features

### Auto-Retry

Automatically retries failed downloads:
- Network errors: 3 retries
- Server errors: 2 retries
- Exponential backoff delay
- Manual retry always available

### Network Monitoring

Intelligent network handling:
- Auto-pause on WiFi loss (if WiFi-only enabled)
- Auto-resume when network restored
- Bandwidth detection
- Connection quality adaptation

### Resume Support

Picks up interrupted downloads:
- HTTP: Uses Range headers
- HLS: Resumes from last segment
- Torrent: Resume from last piece
- Progress preserved across restarts

### Parallel Downloading

HTTP downloads use parallel chunks:
- Splits file into segments
- Downloads segments simultaneously
- Reassembles after completion
- Faster for large files

## Storage Management

### Storage Information

View storage usage:
- Total downloads size
- Available space
- Cache size
- Per-download size

### Cleanup Options

**Automatic**:
- Clear incomplete downloads after 7 days
- Delete failed downloads after 30 days
- Cache cleanup on low storage

**Manual**:
- Clear thumbnail cache
- Delete temporary files
- Remove incomplete downloads

## Tips & Best Practices

### For Faster Downloads

1. **Use WiFi**: Generally faster and unlimited
2. **Increase concurrent downloads**: If bandwidth allows
3. **Enable parallel chunks**: For HTTP downloads
4. **Close other apps**: Reduces competition for bandwidth

### For Reliable Downloads

1. **Disable battery optimization**: Prevents interruptions
2. **Ensure sufficient storage**: At least 2x file size
3. **Stable network**: Avoid moving/traveling during large downloads
4. **Enable auto-retry**: Handles temporary failures

### For Torrent Downloads

1. **Be patient**: Initial connection takes time
2. **Keep seeding**: Help others download
3. **Use popular torrents**: More peers = faster download
4. **Enable DHT/PEX**: Finds more peers

## Troubleshooting

### Download Stuck at 0%

**Causes**:
- Invalid URL
- Server unavailable
- Network issue

**Solutions**:
- Check URL validity
- Retry download
- Try different network
- Check server status

### Download Keeps Failing

**Causes**:
- Unstable network
- Insufficient storage
- Server limits

**Solutions**:
- Enable auto-retry
- Free up storage
- Try off-peak hours
- Check server limits

### Slow Download Speed

**Causes**:
- Slow network
- Server limits
- Too many concurrent downloads

**Solutions**:
- Check network speed
- Reduce concurrent downloads
- Try different time
- Change network (WiFi/cellular)

### Download Not Resuming

**Causes**:
- Server doesn't support resume
- File was deleted
- Database corruption

**Solutions**:
- Start new download
- Check storage location
- Clear app data (last resort)

## Download Statistics

Track your downloading:
- Total downloaded (MB/GB)
- Number of downloads
- Success rate
- Average speed
- Total time saved

## Security & Privacy

### Safe Downloading

**Best Practices**:
- Download from trusted sources
- Verify file sizes
- Scan files if suspicious
- Use VPN if needed

**Privacy**:
- Download history stored locally
- Can be cleared anytime
- No cloud sync (privacy-focused)

## Integration

### With Player

- Downloaded files appear in Library
- Tap to play immediately
- Resume playback position saved
- Automatic metadata

### With File Manager

- Downloads accessible via file manager
- Standard file operations
- Share downloaded files
- Move/rename files

## Summary

ZippyPlayer's download manager provides:

✅ Multi-protocol support (HTTP, HLS, Torrent)
✅ Reliable background downloading
✅ Resume/pause functionality
✅ Queue management
✅ Network awareness
✅ Auto-retry on failure
✅ Parallel downloads
✅ Storage management
✅ Detailed progress tracking

Making it easy to build your video library offline.
