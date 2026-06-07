---
sidebar_position: 4
---

# Torrent Support

Torream includes a full-featured torrent client powered by LibTorrent4j, enabling you to download and stream torrent content directly within the app.

## Overview

Torrent features include:
- Magnet link support
- .torrent file support
- Sequential downloading
- Streaming while downloading
- DHT, PEX, and LSD
- Speed controls
- Multi-architecture support

## Getting Started

### Adding Torrents

**Method 1: Magnet Link**
1. Copy magnet link
2. Open Torream
3. Paste in "Add Download"
4. Download starts automatically

**Method 2: Torrent File**
1. Download .torrent file
2. Open with Torream
3. Or browse from app
4. Select and start download

**Method 3: URL**
1. Paste torrent URL
2. App downloads .torrent file
3. Automatically starts download

### Selecting Files

For multi-file torrents:
1. View torrent contents
2. Select files to download
3. Uncheck unwanted files
4. Start download

## Torrent Streaming

### Watch While Downloading

**How It Works**:
1. Enable sequential download
2. Prioritize first/last pieces
3. Local HTTP server serves content
4. Player streams from localhost

**Advantages**:
- No waiting for complete download
- Preview content quality
- Cancel if not desired
- Save bandwidth

**Requirements**:
- At least 5-10% downloaded
- Stable peer connections
- Sufficient download speed

### Starting Streaming

1. Open active torrent download
2. Tap "Stream" button
3. Wait for buffering (few seconds)
4. Playback begins

**Controls While Streaming**:
- Seek (with buffering)
- Quality matches torrent
- Subtitles (if available)
- Audio tracks (if available)

## Torrent Settings

### Connection Settings

**Max Connections**:
- Per torrent: 50-200
- Total: 200-500
- More = potentially faster, but uses more resources

**Port Configuration**:
- Default: 6881-6889
- Custom port range
- UPnP/NAT-PMP support

### Speed Limits

**Download Limit**:
- Unlimited (default)
- Custom KB/s or MB/s
- Useful on metered connections

**Upload Limit**:
- Unlimited (default)
- Custom limit
- Set to 0 to disable uploading (not recommended)

**Seeding**:
- Continue after download
- Stop at ratio (e.g., 1.0)
- Time limit (e.g., 24 hours)

### Protocol Settings

**DHT (Distributed Hash Table)**:
- Enabled by default
- Finds peers without tracker
- Recommended ON

**PEX (Peer Exchange)**:
- Enabled by default
- Peers share other peers
- Recommended ON

**LSD (Local Service Discovery)**:
- Find peers on local network
- Faster for local torrents
- Recommended ON

## Advanced Features

### Sequential Downloading

**What It Does**:
- Downloads pieces in order
- Enables streaming
- Slightly slower overall

**When to Use**:
- When planning to stream
- For previewing content
- For video files

**When to Disable**:
- Normal downloads
- Multiple file torrents
- Maximizing speed

### Piece Prioritization

**Smart Prioritization**:
- First pieces (for streaming start)
- Last pieces (for file verification)
- User-selected files

**Manual Priority**:
- High: Download first
- Normal: Standard priority
- Low: Download last
- Skip: Don't download

### Torrent Health

**Indicators**:
- Seeds: Complete copies available
- Peers: Partial copies downloading
- Ratio: Seeds/Peers ratio

**Health Status**:
- Excellent: 10+ seeds
- Good: 3-10 seeds
- Fair: 1-3 seeds
- Poor: 0 seeds (peers only)

### Encryption

**Connection Encryption**:
- Enabled by default
- RC4 encryption
- Helps avoid ISP throttling
- May reduce available peers

**Options**:
- Forced: Only encrypted connections
- Enabled: Prefer encrypted
- Disabled: Allow plaintext

## Monitoring Torrents

### Download Information

**Stats Display**:
- Download speed (real-time)
- Upload speed
- ETA (estimated time)
- Downloaded/Total size
- Progress percentage
- Number of peers
- Seeds available

**Detailed Info**:
- Torrent hash
- Tracker status
- Piece size
- Pieces downloaded
- DHT nodes

### Peer Information

**Peer List**:
- IP address (if not anonymous)
- Client name
- Download/upload speed
- Progress percentage
- Flags (encrypted, seed, etc.)

## Managing Torrents

### Torrent Queue

**Queue System**:
- Active downloads (limit: 3)
- Queued torrents
- Seeding torrents
- Paused torrents

**Queue Priority**:
- Move up/down
- Force start
- Auto-managed

### Torrent Actions

**Per-Torrent Controls**:
- Pause: Stop temporarily
- Resume: Continue download
- Force Resume: Skip queue
- Remove: Delete from list
- Remove + Delete: Delete files too

**Advanced Actions**:
- Force Re-check: Verify pieces
- Force Re-announce: Contact trackers
- Copy Magnet: Get magnet link
- Open Location: View files

## Storage & Cache

### Storage Location

**Default**:
- Internal storage/Torream/Torrents
- Customizable per-torrent
- SD card support

**Partial Downloads**:
- Stored in temp folder
- Moved on completion
- Resumed after restart

### Cache Settings

**Disk Cache**:
- Size: 64-512 MB
- Reduces disk writes
- Improves performance

**Memory Cache**:
- For active torrents
- Faster piece access
- Limited by device RAM

## Troubleshooting

### Slow Download Speed

**Causes**:
- Few seeds/peers
- Slow peers
- ISP throttling
- Upload limit too low

**Solutions**:
- Choose healthy torrents
- Enable encryption
- Disable upload limit temporarily
- Try different network
- Enable DHT/PEX

### Cannot Connect to Peers

**Causes**:
- Firewall blocking
- Router NAT issues
- No seeds available
- Tracker offline

**Solutions**:
- Enable UPnP in settings
- Port forwarding on router
- Enable DHT
- Try different torrent

### Streaming Buffers Frequently

**Causes**:
- Slow download speed
- Not enough downloaded
- Poor peer connections

**Solutions**:
- Wait for more download
- Enable sequential download
- Increase connections limit
- Prioritize first pieces

### Torrent Stuck at 99%

**Causes**:
- Missing pieces
- No seeds with last pieces
- Corrupted pieces

**Solutions**:
- Wait for seeds
- Force re-check
- Try different torrent
- Download from alternative source

## Legal Considerations

### Responsible Use

**Important Notes**:
- BitTorrent protocol is legal
- Downloading copyrighted content without permission is illegal
- Users are responsible for their actions
- Know your local laws

**Best Practices**:
- Download legal content only
- Public domain media
- Creative Commons licensed
- Own backups
- Use VPN if concerned about privacy

### Privacy

**Peer Visibility**:
- Your IP visible to peers
- Trackers log IPs
- ISP can see torrent traffic

**Protection Options**:
- VPN services
- Proxy servers
- Encryption (partial protection)
- Private trackers

## Performance Tips

### Optimize Speed

1. **Choose Healthy Torrents**: High seed count
2. **Enable All Protocols**: DHT, PEX, LSD
3. **Increase Connections**: More peers = more speed
4. **Remove Upload Limit**: For better peers
5. **Use Wired Connection**: More stable
6. **Close Other Apps**: Free bandwidth

### Optimize Battery

1. **Limit Connections**: Fewer peers
2. **Enable Upload Limit**: Reduce activity
3. **Use WiFi**: More efficient than cellular
4. **Disable Background Sync**: Only while active

### Optimize Storage

1. **Pre-allocate Space**: Faster writing
2. **Larger Piece Size**: Fewer pieces to manage
3. **Move to SD Card**: If available
4. **Clear Completed**: Free up space

## Integration

### With Player

- Stream directly from torrent
- Access downloaded files in Library
- Automatic metadata detection
- Thumbnail generation

### With Download Manager

- Unified download queue
- Progress tracking
- Notification integration
- Storage management

## Summary

Torream's torrent support provides:

✅ Full-featured torrent client
✅ Magnet link & .torrent file support
✅ Streaming while downloading
✅ DHT, PEX, LSD protocols
✅ Sequential downloading
✅ Speed controls
✅ Peer management
✅ Multi-file selection
✅ Queue management
✅ Encryption support

Making it easy to download and stream torrent content safely and efficiently.