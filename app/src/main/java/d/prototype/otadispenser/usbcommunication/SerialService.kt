package d.prototype.otadispenser.usbcommunication


import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.ArrayDeque

class SerialService : Service(), SerialListener {
    internal inner class SerialBinder : Binder() {
        val service: SerialService
            get() = this@SerialService
    }

    private enum class QueueType {
        Connect, ConnectError, Read, IoError
    }

    private class QueueItem {
        var type: QueueType
        var datas: ArrayDeque<ByteArray>? = null
        var e: Exception? = null

        constructor(type: QueueType) {
            this.type = type
            if (type == QueueType.Read) init()
        }

        constructor(type: QueueType, e: Exception?) {
            this.type = type
            this.e = e
        }

        constructor(type: QueueType, datas: ArrayDeque<ByteArray>?) {
            this.type = type
            this.datas = datas
        }

        fun init() {
            datas = ArrayDeque()
        }

        fun add(data: ByteArray) {
            datas!!.add(data)
        }
    }

    private val mainLooper = Handler(Looper.getMainLooper())
    private val binder: IBinder = SerialBinder()
    private val queue1 = ArrayDeque<QueueItem>()
    private val queue2 = ArrayDeque<QueueItem>()
    private val lastRead = QueueItem(QueueType.Read)

    private var socket: SerialSocket? = null
    private var listener: SerialListener? = null
    private var connected = false

    override fun onDestroy() {
        cancelNotification()
        disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }



    fun disconnect() {
        connected = false
        cancelNotification()
        socket?.disconnect()
        socket = null
    }

    private fun cancelNotification() {
        stopForeground(true)
    }

    override fun onSerialConnect() {
        if (connected) {
            synchronized(this) {
                if (listener != null) {
                    mainLooper.post {
                        listener?.onSerialConnect() ?: queue1.add(QueueItem(QueueType.Connect))
                    }
                } else {
                    queue2.add(QueueItem(QueueType.Connect))
                }
            }
        }
    }

    override fun onSerialConnectError(e: Exception?) {
        if (connected) {
            synchronized(this) {
                if (listener != null) {
                    mainLooper.post {
                        listener?.onSerialConnectError(e) ?: run {
                            queue1.add(QueueItem(QueueType.ConnectError, e))
                            disconnect()
                        }
                    }
                } else {
                    queue2.add(QueueItem(QueueType.ConnectError, e))
                    disconnect()
                }
            }
        }
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray>?) {
        if (connected) {
            synchronized(this) {
                listener?.let {
                    mainLooper.post {
                        it.onSerialRead(datas) ?: queue1.add(QueueItem(QueueType.Read, datas))
                    }
                } ?: queue2.add(QueueItem(QueueType.Read, datas))
            }
        }
    }

    override fun onSerialRead(data: ByteArray?) {
        if (connected) {
            synchronized(this) {
                listener?.let {
                    val first: Boolean
                    synchronized(lastRead) {
                        first = lastRead.datas!!.isEmpty()
                        data?.let { lastRead.add(it) }
                    }
                    if (first) {
                        mainLooper.post {
                            synchronized(lastRead) {
                                val datas = lastRead.datas
                                lastRead.init()
                                it.onSerialRead(datas)
                            }
                        }
                    }
                } ?: run {
                    if (queue2.isEmpty() || queue2.last.type != QueueType.Read) queue2.add(
                        QueueItem(
                            QueueType.Read
                        )
                    )
                    data?.let { queue2.last.add(it) }
                }
            }
        }
    }

    override fun onSerialIoError(e: Exception?) {
        if (connected) {
            synchronized(this) {
                listener?.let {
                    mainLooper.post {
                        it.onSerialIoError(e) ?: run {
                            queue1.add(QueueItem(QueueType.IoError, e))
                            disconnect()
                        }
                    }
                } ?: run {
                    queue2.add(QueueItem(QueueType.IoError, e))
                    disconnect()
                }
            }
        }
    }
}
