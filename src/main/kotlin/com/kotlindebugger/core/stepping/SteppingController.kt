package com.kotlindebugger.core.stepping

import com.kotlindebugger.common.model.DebugEvent
import com.kotlindebugger.common.model.StepType
import com.kotlindebugger.common.util.JdiUtils
import com.kotlindebugger.core.event.DebugEventListener
import com.kotlindebugger.core.event.EventHandler
import com.kotlindebugger.kotlin.position.KotlinPositionManager
import com.sun.jdi.*
import com.sun.jdi.event.StepEvent
import com.sun.jdi.request.StepRequest

/**
 * 单步执行控制器
 * 负责处理 Step Over/Into/Out 操作
 */
class SteppingController(
    private val vm: VirtualMachine,
    private val eventHandler: EventHandler,
    private val positionManager: KotlinPositionManager
) : DebugEventListener {

    private var currentStepRequest: StepRequest? = null
    private var stepListener: DebugEventListener? = null

    /**
     * 单步执行
     */
    fun step(
        thread: ThreadReference,
        stepType: StepType,
        listener: DebugEventListener? = null
    ) {
        // 清除之前的步骤请求
        clearCurrentStepRequest()

        this.stepListener = listener

        try {
            // 获取当前栈帧
            val frame = thread.frame(0)
            val currentLocation = frame.location()

            // 创建步骤请求
            val stepRequest = createStepRequest(thread, stepType, currentLocation)

            // StepRequest 已经在创建时与线程绑定，不需要额外设置过滤器

            // 启用请求
            stepRequest.enable()
            currentStepRequest = stepRequest

            // 注册监听器
            eventHandler.addListener(this)

            // 恢复线程执行
            thread.resume()

        } catch (e: Exception) {
            throw RuntimeException("Step execution failed: ${e.message}", e)
        }
    }

    /**
     * 创建步骤请求
     */
    private fun createStepRequest(
        thread: ThreadReference,
        stepType: StepType,
        currentLocation: Location
    ): StepRequest {
        return when (stepType) {
            StepType.STEP_OVER -> {
                // Step Over: 不进入方法调用
                vm.eventRequestManager().createStepRequest(
                    thread,
                    StepRequest.STEP_LINE,
                    StepRequest.STEP_OVER
                )
            }

            StepType.STEP_INTO -> {
                // Step Into: 进入方法调用
                vm.eventRequestManager().createStepRequest(
                    thread,
                    StepRequest.STEP_LINE,
                    StepRequest.STEP_INTO
                )
            }

            StepType.STEP_OUT -> {
                // Step Out: 跳出当前方法
                // 查找当前方法的所有行位置，然后创建 STEP_OUT 请求
                val method = currentLocation.method()
                val allLocations = JdiUtils.run { method.safeAllLineLocations() }
                val maxLine = allLocations.maxOfOrNull { JdiUtils.run { it.safeLineNumber() } } ?: Int.MAX_VALUE

                vm.eventRequestManager().createStepRequest(
                    thread,
                    StepRequest.STEP_LINE,
                    StepRequest.STEP_OUT
                )
            }
        }
    }

    /**
     * 清除当前的步骤请求
     */
    fun clearCurrentStepRequest() {
        currentStepRequest?.let { request ->
            vm.eventRequestManager().deleteEventRequest(request)
            currentStepRequest = null
        }
        eventHandler.removeListener(this)
        stepListener = null
    }

    /**
     * 检查是否正在单步执行
     */
    fun isStepping(): Boolean = currentStepRequest != null

    /**
     * 处理步骤事件
     */
    override fun onEvent(event: DebugEvent) {
        when (event) {
            is DebugEvent.StepCompleted -> {
                // 清除步骤请求
                clearCurrentStepRequest()

                // 通知监听器
                stepListener?.onEvent(event)
            }
            else -> {}
        }
    }
}