package world.verifi.auto_connect

data class AccessPoint(
  val ssid: String,
  val password: String,
  val isWEP: Boolean
)
