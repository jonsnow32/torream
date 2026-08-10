---
sidebar_position: 7
---

# Network Shares (SMB/FTP/WebDAV)

Torream can connect directly to file shares on your local network or a remote server — SMB (Windows/NAS shares), FTP, and WebDAV — and play video straight from them without downloading first.

## Overview

Network share features include:
- Three protocols: SMB, FTP, WebDAV (with optional TLS)
- Save multiple shares and switch between them
- Folder browsing with breadcrumb-style back navigation
- File-type icons (video, audio, other)
- Encrypted credential storage
- Direct playback — no manual download step

[Learn more about the Media Library →](./media-library.md) for how played network files relate to your local library and watch history.

## Getting Started

### Prerequisites

**Requirements**:
- The share server (NAS, PC, or remote server) and your Android device reachable on the same network (or a server reachable over the internet for FTP/WebDAV)
- Host address, port, and credentials for the share

### Adding a Share

1. Open the **Browse** tab (bottom navigation)
2. Tap **+** in the toolbar
3. Choose the protocol: **SMB**, **FTP**, or **WebDAV**
4. Fill in:
   - **Name** — a label shown in your share list
   - **Host / IP address**
   - **Port** — pre-filled with the protocol default (SMB 445, FTP 21, WebDAV 80/443), editable
   - **Path** — for SMB this is `ShareName/optional/sub/folder`; for FTP/WebDAV it's the folder path on the server
   - **Username / Password** — leave blank for anonymous access where supported
   - **Use HTTPS/TLS** — WebDAV only, switches between `http://` and `https://`
5. Tap **Save** — Torream connects and lists the root folder before saving, so a wrong host or bad credentials shows an inline error immediately instead of saving a broken entry

## Browsing & Playback

### Navigating

- Tap a saved share to open it
- Tap a folder to go deeper
- Tap the **back arrow** in the toolbar (or use the system back gesture/button) to go up one folder at a time, eventually returning to your share list
- Files show a type-specific icon: a play icon for video files, a music note for audio files, a generic file icon for everything else

### Playing a File

Tap any video file to start playback immediately in the regular Torream player — subtitles, resume position, and playback controls all work the same as local files.

## How Playback Works Per Protocol

Torream uses each server's native protocol wherever possible, and only proxies traffic locally when a protocol has no direct player support:

- **FTP** — played directly; the FTP protocol is built into the app's media engine
- **WebDAV** — played directly over `http(s)://`, with your credentials sent as a standard `Authorization` header
- **SMB** — Android's media engine has no built-in SMB support, so Torream connects to the SMB share itself and relays the file to the player over a local, on-device connection (never leaving your phone) — this is why the very first SMB file you play may take a moment longer to start than FTP/WebDAV

## Security

- Saved passwords are encrypted on-device using Android's hardware-backed Keystore (AES/GCM) before being stored — never saved in plain text
- Credentials are only decrypted in memory when actively connecting, and are never logged
- All connections are direct between your device and the share's server — nothing passes through a third-party server

## Limitations

- **SMB**: uses the modern SMB2/3 protocol (no legacy SMB1 support); each browse/play action opens a fresh connection rather than keeping one open in the background
- **FTP**: passive mode only; FTPS (FTP over TLS) is not yet supported — use WebDAV with TLS instead if you need an encrypted connection
- **WebDAV**: authentication is Basic Auth only (no Digest or OAuth-based WebDAV servers)
- Folder listings are fetched live on every visit — there's no offline cache of a network share's contents

## Troubleshooting

### "Couldn't connect" when saving a share

**Check**:
- Host/IP and port are correct and reachable from your device (try pinging or browsing to it from another app first)
- Your device and the share's server are on the same network (for local SMB/FTP shares)
- Username/password are correct, or the server allows anonymous access if left blank
- The **Path** field matches the server's actual folder structure — for SMB, the first segment must be the exact share name

### Files list but won't play

**Check**:
- The file format is supported by Torream's player (see [Video Player](./video-player.md))
- For SMB, confirm the share user has read permission on that specific file, not just the folder

### Slow to start playing (SMB)

This is expected for the first file in a session — Torream is opening a fresh connection to the share. Subsequent seeks within the same file use range requests and should be smooth.

## Summary

Torream's network share support provides:

✅ SMB, FTP, and WebDAV in one place
✅ Encrypted, on-device credential storage
✅ Direct playback — no manual downloads
✅ Folder browsing with back navigation
✅ File-type aware icons

Making it easy to watch video stored on your NAS, PC, or remote server without copying it to your phone first.
