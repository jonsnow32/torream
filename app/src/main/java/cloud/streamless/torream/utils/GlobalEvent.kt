package cloud.streamless.torream.utils

object GlobalEvent {

  val onColorSelectedEvent = Event<Pair<Int, Int>>()
  val onDialogDismissedEvent = Event<Int>()

}
