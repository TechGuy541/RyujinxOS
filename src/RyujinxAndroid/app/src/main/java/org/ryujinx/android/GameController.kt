package org.ryujinx.android

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.swordfish.radialgamepad.library.RadialGamePad
import com.swordfish.radialgamepad.library.config.ButtonConfig
import com.swordfish.radialgamepad.library.config.CrossConfig
import com.swordfish.radialgamepad.library.config.CrossContentDescription
import com.swordfish.radialgamepad.library.config.PrimaryDialConfig
import com.swordfish.radialgamepad.library.config.RadialGamePadConfig
import com.swordfish.radialgamepad.library.config.SecondaryDialConfig
import com.swordfish.radialgamepad.library.event.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.ryujinx.android.viewmodels.MainViewModel
import org.ryujinx.android.viewmodels.QuickSettings

typealias GamePad = RadialGamePad
typealias GamePadConfig = RadialGamePadConfig

class GameController(var activity: Activity) {

    companion object{
        private fun Create(context: Context, activity: Activity, controller: GameController) : View
        {
            val inflator = LayoutInflater.from(context)
            val view = inflator.inflate(R.layout.game_layout, null)
            view.findViewById<FrameLayout>(R.id.leftcontainer)!!.addView(controller.leftGamePad)
            view.findViewById<FrameLayout>(R.id.rightcontainer)!!.addView(controller.rightGamePad)

            return view
        }
        @Composable
        fun Compose(viewModel: MainViewModel) : Unit {
            AndroidView(
                modifier = Modifier.fillMaxSize(), factory = { context ->
                    val controller = GameController(viewModel.activity)
                    val c = Create(context, viewModel.activity, controller)
                    viewModel.activity.lifecycleScope.apply {
                        viewModel.activity.lifecycleScope.launch {
                            val events = merge(
                                controller.leftGamePad.events(),
                                controller.rightGamePad.events()
                            )
                                .shareIn(viewModel.activity.lifecycleScope, SharingStarted.Lazily)
                            events.safeCollect {
                                controller.handleEvent(it)
                            }
                        }
                    }
                    controller.controllerView = c
                    viewModel.setGameController(controller)
                    controller.setVisible(QuickSettings(viewModel.activity).useVirtualController)
                    c
                })
        }
    }

    private var ryujinxNative: RyujinxNative
    private var controllerView: View? = null
    var leftGamePad: GamePad
    var rightGamePad: GamePad
    var controllerId: Int = -1
    val isVisible : Boolean
        get() {
            controllerView?.apply {
                return this.isVisible
            }

            return false
        }

    init {
        leftGamePad = GamePad(generateConfig(true), 16f, activity)
        rightGamePad = GamePad(generateConfig(false), 16f, activity)

        leftGamePad.primaryDialMaxSizeDp = 200f
        rightGamePad.primaryDialMaxSizeDp = 200f

        leftGamePad.gravityX = -1f
        leftGamePad.gravityY = 1f
        rightGamePad.gravityX = 1f
        rightGamePad.gravityY = 1f

        ryujinxNative = RyujinxNative.instance
    }

    fun setVisible(isVisible: Boolean){
        controllerView?.apply {
            this.isVisible = isVisible

            if(isVisible)
                connect()
        }
    }

    fun connect(){
        if(controllerId == -1)
            controllerId = RyujinxNative.instance.inputConnectGamepad(0)
    }

    private fun handleEvent(ev: Event) {
        if(controllerId == -1)
            controllerId = ryujinxNative.inputConnectGamepad(0)

        controllerId.apply {
            when (ev) {
                is Event.Button -> {
                    val action = ev.action
                    when (action) {
                        KeyEvent.ACTION_UP -> {
                            ryujinxNative.inputSetButtonReleased(ev.id, this)
                        }

                        KeyEvent.ACTION_DOWN -> {
                            ryujinxNative.inputSetButtonPressed(ev.id, this)
                        }
                    }
                }

                is Event.Direction -> {
                    val direction = ev.id

                    when(direction) {
                        GamePadButtonInputId.DpadUp.ordinal -> {
                            if (ev.xAxis > 0) {
                                ryujinxNative.inputSetButtonPressed(GamePadButtonInputId.DpadRight.ordinal, this)
                                ryujinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadLeft.ordinal, this)
                            } else if (ev.xAxis < 0) {
                                ryujinxNative.inputSetButtonPressed(GamePadButtonInputId.DpadLeft.ordinal, this)
                                ryujinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadRight.ordinal, this)
                            } else {
                                ryujinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadLeft.ordinal, this)
                                ryujinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadRight.ordinal, this)
                            }
                            if (ev.yAxis < 0) {
                                ryujinxNative.inputSetButtonPressed(GamePadButtonInputId.DpadUp.ordinal, this)
                                ryujinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadDown.ordinal, this)
                            } else if (ev.yAxis > 0) {
                                ryujinxNative.inputSetButtonPressed(GamePadButtonInputId.DpadDown.ordinal, this)
                                ryujinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadUp.ordinal, this)
                            } else {
                                ryujinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadDown.ordinal, this)
                                ryujinxNative.inputSetButtonReleased(GamePadButtonInputId.DpadUp.ordinal, this)
                            }
                        }

                        GamePadButtonInputId.LeftStick.ordinal -> {
                            ryujinxNative.inputSetStickAxis(1, ev.xAxis, -ev.yAxis ,this)
                        }

                        GamePadButtonInputId.RightStick.ordinal -> {
                            ryujinxNative.inputSetStickAxis(2, ev.xAxis, -ev.yAxis ,this)
                        }
                    }
                }
            }
        }
    }
}

suspend fun <T> Flow<T>.safeCollect(
    block: suspend (T) -> Unit
) {
    this.catch {}
        .collect {
            block(it)
        }
}

private fun generateConfig(isLeft: Boolean): GamePadConfig {
    val distance = 0.05f

    if (isLeft) {
        return GamePadConfig(
            12,
            PrimaryDialConfig.Stick(
                GamePadButtonInputId.LeftStick.ordinal,
                GamePadButtonInputId.LeftStickButton.ordinal,
                setOf(),
                "LeftStick",
                null
            ),
            listOf(
                SecondaryDialConfig.Cross(
                    9,
                    3,
                    1.8f,
                    distance,
                    CrossConfig(
                        GamePadButtonInputId.DpadUp.ordinal,
                        CrossConfig.Shape.STANDARD,
                        null,
                        setOf(),
                        CrossContentDescription(),
                        true,
                        null
                    ),
                    SecondaryDialConfig.RotationProcessor()
                ),
                SecondaryDialConfig.SingleButton(
                    0,
                    1f,
                    0.05f,
                    ButtonConfig(
                        GamePadButtonInputId.Minus.ordinal,
                        "-",
                        true,
                        null,
                        "Minus",
                        setOf(),
                        true,
                        null
                    ),
                    null,
                    SecondaryDialConfig.RotationProcessor()
                ),
                SecondaryDialConfig.DoubleButton(
                    2,
                    0.05f,
                    ButtonConfig(
                        GamePadButtonInputId.LeftShoulder.ordinal,
                        "L",
                        true,
                        null,
                        "LeftBumper",
                        setOf(),
                        true,
                        null
                    ),
                    null,
                    SecondaryDialConfig.RotationProcessor()
                ),
                SecondaryDialConfig.SingleButton(
                    8,
                    1f,
                    0.05f,
                    ButtonConfig(
                        GamePadButtonInputId.LeftTrigger.ordinal,
                        "ZL",
                        true,
                        null,
                        "LeftTrigger",
                        setOf(),
                        true,
                        null
                    ),
                    null,
                    SecondaryDialConfig.RotationProcessor()
                ),
            )
        )
    } else {
        return GamePadConfig(
            12,
            PrimaryDialConfig.PrimaryButtons(
                listOf(
                    ButtonConfig(
                        GamePadButtonInputId.A.ordinal,
                        "A",
                        true,
                        null,
                        "A",
                        setOf(),
                        true,
                        null
                    ),
                    ButtonConfig(
                        GamePadButtonInputId.X.ordinal,
                        "X",
                        true,
                        null,
                        "X",
                        setOf(),
                        true,
                        null
                    ),
                    ButtonConfig(
                        GamePadButtonInputId.Y.ordinal,
                        "Y",
                        true,
                        null,
                        "Y",
                        setOf(),
                        true,
                        null
                    ),
                    ButtonConfig(
                        GamePadButtonInputId.B.ordinal,
                        "B",
                        true,
                        null,
                        "B",
                        setOf(),
                        true,
                        null
                    )
                ),
                null,
                0f,
                true,
                null
            ),
            listOf(
                SecondaryDialConfig.Stick(
                    7,
                    2,
                    3f,
                    0.05f,
                    GamePadButtonInputId.RightStick.ordinal,
                    GamePadButtonInputId.RightStickButton.ordinal,
                    null,
                    setOf(),
                    "RightStick",
                    SecondaryDialConfig.RotationProcessor()
                ),
                SecondaryDialConfig.SingleButton(
                    6,
                    1f,
                    0.05f,
                    ButtonConfig(
                        GamePadButtonInputId.Plus.ordinal,
                        "+",
                        true,
                        null,
                        "Plus",
                        setOf(),
                        true,
                        null
                    ),
                    null,
                    SecondaryDialConfig.RotationProcessor()
                ),
                SecondaryDialConfig.DoubleButton(
                    3,
                    0.05f,
                    ButtonConfig(
                        GamePadButtonInputId.RightShoulder.ordinal,
                        "R",
                        true,
                        null,
                        "RightBumper",
                        setOf(),
                        true,
                        null
                    ),
                    null,
                    SecondaryDialConfig.RotationProcessor()
                ),
                SecondaryDialConfig.SingleButton(
                    9,
                    1f,
                    0.05f,
                    ButtonConfig(
                        GamePadButtonInputId.RightTrigger.ordinal,
                        "ZR",
                        true,
                        null,
                        "RightTrigger",
                        setOf(),
                        true,
                        null
                    ),
                    null,
                    SecondaryDialConfig.RotationProcessor()
                )
            )
        )
    }
}
