package com.kotlindebugger.core.breakpoint

import com.kotlindebugger.dap.Logger
import com.sun.jdi.VirtualMachine
import com.sun.jdi.request.ExceptionRequest
import java.util.concurrent.ConcurrentHashMap

/**
 * 异常断点管理器
 * 负责异常断点的创建和管理
 */
class ExceptionBreakpointManager(private val vm: VirtualMachine) {

    // 当前的异常请求
    private val exceptionRequests = ConcurrentHashMap<String, ExceptionRequest>()

    // 是否捕获已捕获的异常
    private var catchCaught = false

    // 是否捕获未捕获的异常
    private var catchUncaught = false

    /**
     * 设置异常断点
     * @param filters 过滤器列表，如 ["caught", "uncaught"]
     * @return 设置的断点列表
     */
    fun setExceptionBreakpoints(filters: List<String>): List<ExceptionBreakpointResult> {
        Logger.debug("Setting exception breakpoints with filters: $filters")

        // 清除现有的异常请求
        clearExceptionRequests()

        // 更新设置
        catchCaught = filters.contains("caught")
        catchUncaught = filters.contains("uncaught")

        val results = mutableListOf<ExceptionBreakpointResult>()

        // 如果需要捕获异常，创建异常请求
        if (catchCaught || catchUncaught) {
            try {
                // 创建一个全局异常请求，捕获所有异常
                val request = vm.eventRequestManager().createExceptionRequest(
                    null,  // 捕获所有异常类型
                    catchCaught,   // 是否捕获已捕获的异常
                    catchUncaught  // 是否捕获未捕获的异常
                )
                request.setSuspendPolicy(com.sun.jdi.request.EventRequest.SUSPEND_ALL)
                request.enable()

                exceptionRequests["all"] = request
                Logger.debug("Created exception request: caught=$catchCaught, uncaught=$catchUncaught")

                // 为每个过滤器返回一个成功的断点
                for (filter in filters) {
                    results.add(ExceptionBreakpointResult(
                        verified = true,
                        message = null
                    ))
                }
            } catch (e: Exception) {
                Logger.error("Failed to create exception request", e)
                // 返回失败的断点
                for (filter in filters) {
                    results.add(ExceptionBreakpointResult(
                        verified = false,
                        message = "Failed to set exception breakpoint: ${e.message}"
                    ))
                }
            }
        }

        return results
    }

    /**
     * 清除所有异常请求
     */
    fun clearExceptionRequests() {
        exceptionRequests.values.forEach { request ->
            try {
                request.disable()
                vm.eventRequestManager().deleteEventRequest(request)
            } catch (e: Exception) {
                // 忽略清理时的错误
            }
        }
        exceptionRequests.clear()
        catchCaught = false
        catchUncaught = false
    }

    /**
     * 检查是否启用了异常断点
     */
    fun isEnabled(): Boolean = catchCaught || catchUncaught

    /**
     * 检查是否应该在此异常处暂停
     * @param isCaught 异常是否被捕获
     */
    fun shouldStopOnException(isCaught: Boolean): Boolean {
        return if (isCaught) catchCaught else catchUncaught
    }

    /**
     * 获取当前的过滤器设置
     */
    fun getActiveFilters(): List<String> {
        val filters = mutableListOf<String>()
        if (catchCaught) filters.add("caught")
        if (catchUncaught) filters.add("uncaught")
        return filters
    }
}

/**
 * 异常断点设置结果
 */
data class ExceptionBreakpointResult(
    val verified: Boolean,
    val message: String?
)
