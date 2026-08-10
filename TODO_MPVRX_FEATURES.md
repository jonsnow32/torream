# Feature backlog — tham khảo từ mpvRx

Nguồn tham khảo: [`Riteshp2001/mpvRx`](https://github.com/Riteshp2001/mpvRx) (fork của [`marlboro-advance/mpvEx`](https://github.com/marlboro-advance/mpvEx)).

## ⚠️ License — đọc trước khi implement bất kỳ mục nào

- **mpvRx** dùng license **CC BY-NC 4.0 (non-commercial)**.
- **mpvEx** (repo gốc mà mpvRx fork từ) dùng **Apache-2.0**.
- Torream là app thương mại (có Ads waterfall — AdMob/Unity/Vungle...). **Không được copy code từ mpvRx** vì vi phạm điều khoản non-commercial.
- Nếu cần đối chiếu implementation cụ thể, chỉ tham khảo `mpvEx` (Apache-2.0). Với mpvRx: chỉ lấy **ý tưởng/kiến trúc**, tự viết lại hoàn toàn theo style Torream.


## Đã có tương đương ở Torream (không cần làm lại)

- Cast → `CastHelper`, `LocalFileStreamServer`
- Background playback → `BackgroundPlaybackService`
- PiP → `PipActionManager`
- Subtitle search đa nguồn → 3-provider parallel (OpenSubtitles, SubDL, SubSource)

## Feature mới — theo effort thấp → cao

| # | Feature | Effort | Touchpoint / ghi chú |
|---|---|---|---|
| 1 | ✅ **Theming đa dạng** (Material You, AMOLED Black, Catppuccin, Nord, Dracula, Tokyo Night, Gruvbox, Solarized) | Thấp | Done — `utils/AppColorTheme.kt`, `Theme.*` blocks in `values(-night)/styles.xml`, `ColorThemeDialog`. 8 theme thay vì 25+ (xem PR), thêm theme mới sau này chỉ cần 1 style block + 1 enum entry |
| 2 | ✅ **Stats overlay** (File/Display/Video/Audio dump, GPU frame timings, network sparkline, battery) | Thấp | Done — `ui/player/stats/`, `getPropertyNode` (JNI, `vo-passes`), toggle ở Settings > Player |
| 3 | ✅ **SMB/FTP/WebDAV client** | Trung bình | Done — `ui/browse/` (tab mới), `NetworkShareRepository`, SMB qua smbj (pure-Java) + `SmbStreamServer` proxy thay vì native libsmbclient |
| 4 | **AI tools** (dịch subtitle, đổi tên file hàng loạt qua OpenAI/Anthropic/Groq/OpenRouter...) | Trung bình | Cần provider abstraction + settings UI cho API key — tham khảo pattern multi-provider của `AdWaterfallManager` |
| 5 | **Audio FFT visualizer** | Thấp/Trung bình | Cần OpenGL ES rendering |
| 6 | **Dual subtitle + speech-to-subtitle (Whisper)** | Trung bình/Cao | Mở rộng `SubtitleHelper` / `MPVSubtitleFragment` |
| 7 | **Syncplay** (đồng bộ xem chung nhiều người qua room) | Cao | Cần network client mới (WebSocket/TCP theo Syncplay protocol) + hook 2 chiều vào `PlayerMediaManager` / `MPVFragment` |
| 8 | **yt-dlp streaming** (YouTube/
Twitch/Bilibili, chọn codec/res/HDR) | Cao | Cần Python bridge hoặc native binding |
| 9 | **HDR shader pipeline + Anime4K upscaling** | Cao | Đụng native MPV/JNI layer (`ui/player/mpv/`), rủi ro hiệu năng/nhiệt trên thiết bị yếu — cần thermal/battery-aware throttling nếu làm |
| 10 | **Lua/JS scripting engine + code editor** | Cao | Tính năng lớn, ít người dùng phổ thông cần — ưu tiên thấp trừ khi có yêu cầu cụ thể |

## Gợi ý thứ tự làm

Theming → Stats overlay → SMB/FTP/WebDAV → AI tools → Audio visualizer → Dual subtitle → (Syncplay / yt-dlp / HDR pipeline / scripting — mỗi mục cần plan riêng khi bắt tay vào, do phức tạp và rủi ro kiến trúc cao).
