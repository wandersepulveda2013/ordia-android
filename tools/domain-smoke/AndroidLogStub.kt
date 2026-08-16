package android.util

/**
 * Stub de android.util.Log para tests JVM puros (sin Android SDK).
 * Permite compilar fuentes que llaman Log.w/d/i/e en el paquete intelligence
 * sin tocar su código. Equivalente en espíritu a RoomStubs/PreferenceStubs.
 */
object Log {
    fun w(tag: String, msg: String): Int = 0
    fun d(tag: String, msg: String): Int = 0
    fun i(tag: String, msg: String): Int = 0
    fun e(tag: String, msg: String): Int = 0
    fun e(tag: String, msg: String, tr: Throwable): Int = 0
}
