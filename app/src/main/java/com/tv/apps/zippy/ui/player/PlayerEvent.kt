package com.tv.apps.zippy.ui.player


enum class PlayerEventType(val value: Int) {
  //Stop(-1),
  Pause(0),
  Play(1),
  SeekForward(2),
  SeekBack(3),

  SkipCurrentChapter(4),
  NextEpisode(5),
  PrevEpisode(6),
  PlayPauseToggle(7),
  ToggleMute(8),
  Lock(9),
  ToggleHide(10),
  ShowSpeed(11),
  ShowMirrors(12),
  Resize(13),
  SearchSubtitlesOnline(14),
  SkipOp(15),
}

const val SUBTITLE_DELAY_BUNDLE_KEY = "subtitle_delay"
