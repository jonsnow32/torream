package cloud.app.csplayer.utils

object GlobalEvent {

  val onColorSelectedEvent = Event<Pair<Int, Int>>()
  val onDialogDismissedEvent = Event<Int>()

}
