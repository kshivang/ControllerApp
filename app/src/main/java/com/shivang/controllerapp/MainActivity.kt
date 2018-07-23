package com.shivang.controllerapp

import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.support.annotation.StringRes
import android.support.v4.view.ViewCompat.generateViewId
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import com.github.nisrulz.sensey.RotationAngleDetector
import com.github.nisrulz.sensey.Sensey
import com.github.nisrulz.sensey.TiltDirectionDetector
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.jsonObject
import com.google.gson.Gson
import com.shivang.controllerapp.model.UserMessage
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*
import java.net.URISyntaxException



class MainActivity : AppCompatActivity() {

    private var mSocket: Socket? = null
    private var dialog: DialogInterface? = null

    companion object {
        const val UP = "UP"
        const val DOWN = "DOWN"
        const val RIGHT = "RIGHT"
        const val LEFT = "LEFT"
        const val SELECT = "SELECT"

        const val TEST = 0
        const val DIRECTION = 1
        const val TILT = 2
        const val ROTATION = 3
        const val TILT_ROT = 4
        const val JOYSTICK = 5
        const val MESSAGE = 6
    }

    private var onNewMessage = Emitter.Listener { it ->
        runOnUiThread {
            val gson = Gson()
            val userMessage = gson.fromJson<UserMessage>(it[0] as String)
            val username = userMessage.from.name
            val message = userMessage.content.message
//            toast("UserName: $username, Message: $message")
        }
    }

    private var xTilt = 0
    private var yTilt = 0
    private var zTilt = 0
    private var xAngle = 0f
    private var yAngle = 0f
    private var zAngle = 0f
    private var angle = 0
    private var strength = 0

    private var tiltOn = false
    private var rotOn = false

    private var message = "Message"

    private val repeatUpdateHandler = Handler()

    private val tiltDirectionListener = object: TiltDirectionDetector.TiltDirectionListener {
        override fun onTiltInAxisX(direction: Int) {
            // Do something with tilt direction on xTilt-axis
            xTilt = direction
            send(TILT)
        }

        override fun onTiltInAxisY(direction: Int) {
            // Do something with tilt direction on yTilt-axis
            yTilt = direction
            send(TILT)
        }

        override fun onTiltInAxisZ(direction: Int) {
            // Do something with tilt direction on zTilt-axis
            zTilt = direction
            send(TILT)
        }
    }

    private val rotationAngleListener = RotationAngleDetector.RotationAngleListener {
        angleX, angleY, angleZ ->
        xAngle = angleX
        yAngle = angleY
        zAngle = angleZ

        send(ROTATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Sensey.getInstance().init(this)
        inputDialog(R.string.default_uri, "Connect") { input ->
            initSocket(input)
        }

        btTest.setOnClickListener {
            send(TEST)
        }

        jsV.setOnMoveListener { angle, strength ->
            this.angle = angle
            this.strength = strength
            send(JOYSTICK)
        }

        btMessage.setOnClickListener {
            message = "Message"
            inputDialog(R.string.message, "Send") { input ->
                message = input
                send(MESSAGE)
            }
        }

        ibSelect.setOnClickListener {
            direction = SELECT
            send(DIRECTION)
        }
//
//        ibUp.setOnClickListener{
//            direction = UP
//            send(DIRECTION)
//        }

        ibUp.setOnLongClickListener {
            direction = UP
            repeatUpdateHandler.post(RptUpdater())
            false
        }

        ibUp.setOnTouchListener{ _, event ->
            if ((event.action == MotionEvent.ACTION_UP
                    || event.action == MotionEvent.ACTION_CANCEL) && direction == UP) {
                direction = ""
            }
            false
        }
//
//        ibRight.setOnClickListener{
//            direction = RIGHT
//            send(DIRECTION)
//        }

        ibRight.setOnLongClickListener {
            direction = RIGHT
            repeatUpdateHandler.post(RptUpdater())
            false
        }

        ibRight.setOnTouchListener{ _, event ->
            if ((event.action == MotionEvent.ACTION_UP
                    || event.action == MotionEvent.ACTION_CANCEL) && direction == RIGHT) {
                direction = ""
            }
            false
        }

//        ibLeft.setOnClickListener{
//            direction = LEFT
//            send(DIRECTION)
//        }

        ibLeft.setOnLongClickListener {
            direction = LEFT
            repeatUpdateHandler.post(RptUpdater())
            false
        }

        ibLeft.setOnTouchListener{ _, event ->
            if ((event.action == MotionEvent.ACTION_UP
                    || event.action == MotionEvent.ACTION_CANCEL) && direction == LEFT) {
                direction = ""
            }
            false
        }

//        ibDown.setOnClickListener{
//            direction = DOWN
//            send(DIRECTION)
//        }

        ibDown.setOnLongClickListener {
            direction = DOWN
            repeatUpdateHandler.post(RptUpdater())
            false
        }

        ibDown.setOnTouchListener{ _, event ->
            if ((event.action == MotionEvent.ACTION_UP
                    || event.action == MotionEvent.ACTION_CANCEL) && direction == DOWN) {
                direction = ""
            }
            false
        }

        swTilt.setOnCheckedChangeListener { _, isChecked ->
            tiltOn = isChecked
            if (isChecked) {
                Sensey.getInstance().startTiltDirectionDetection(tiltDirectionListener)
            } else {
                Sensey.getInstance().stopTiltDirectionDetection(tiltDirectionListener)
            }
        }

        swRotation.setOnCheckedChangeListener{ _, isChecked ->
            rotOn = isChecked
            if (isChecked) {
                Sensey.getInstance().startRotationAngleDetection(rotationAngleListener)
            } else {
                Sensey.getInstance().stopRotationAngleDetection(rotationAngleListener)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Sensey.getInstance().stop()
        mSocket?.disconnect()
        mSocket?.off("message", onNewMessage)
    }

    private fun initSocket(uri: String) {
        try {
            mSocket = IO.socket(uri)
        } catch (e: URISyntaxException) {
            longToast("Error occurred!: ${e.message}")
            e.printStackTrace()
        }
        mSocket?.on("message", onNewMessage)
        mSocket?.connect()
    }

    private fun send(mType: Int) {
        var type = mType
        if (type == TILT || type == ROTATION) {
            if (tiltOn && rotOn) {
                type = TILT_ROT
            }
        }
        val messageJSON = jsonObject(
                fromBody(),
                "content" to jsonObject(
                        "type" to type, when (type) {
                    TEST -> {
                        "message" to "test"
                    }
                    DIRECTION -> {
                        "direction" to direction
                    }
                    TILT_ROT,
                    TILT -> {
                        tilt()
                    }
                    ROTATION -> {
                        rotation()
                    }
                    JOYSTICK -> {
                        joystick()
                    }
                    else -> {
                        "message" to message
                    }
                }, if (type == TILT_ROT) rotation() else "type" to type)
        )

        Log.e("Message Sent", "" + messageJSON.toString())

        mSocket?.emit("message", messageJSON)
    }

    private fun joystick() = "joystick" to jsonObject(
            "angle" to angle,
            "strength" to strength
    )

    private fun tilt() = "tilt" to jsonObject(
            "xTilt" to xTilt,
            "yTilt" to yTilt,
            "zTilt" to zTilt
    )

    private fun rotation() = "rotation" to jsonObject(
            "xAngle" to xAngle,
            "yAngle" to yAngle,
            "zAngle" to zAngle
    )

    private fun fromBody() = "from" to jsonObject(
            "name" to "Shivang"
    )

    private fun AppCompatActivity.inputDialog(@StringRes textHint: Int, btText: String,
                                              buttonClick: (inputText: String) -> Unit ) {
        dialog = alert {
            customView {
                relativeLayout {
                    val et = editText{
                        id = generateViewId()
                    }.lparams(width = matchParent, height = wrapContent) {
                        leftMargin = dip(24)
                        rightMargin = dip(24)
                    }
                    val bt = button(btText) {
                        id = generateViewId()
                    }.lparams(height = wrapContent, width = wrapContent) {
                        below(et)
                        topMargin = dip(24)
                        centerHorizontally()
                    }
                    et.setText(getString(textHint))
                    bt.setOnClickListener {
                        buttonClick(et.text.toString())
                        dialog?.dismiss()
                    }
                }
            }
        }.show()
    }

    private var direction = ""
    private val delay = 50L

    internal inner class RptUpdater : Runnable {
        override fun run() {
            if (!TextUtils.isEmpty(direction)) {
                send(DIRECTION)
                repeatUpdateHandler.postDelayed(RptUpdater(), delay)
            }
        }
    }
}