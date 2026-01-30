package com.kotlindebugger.core.coroutine

import com.sun.jdi.*

/**
 * 协程调试相关的工具函数
 * 参考 IntelliJ Community 的 CoroutineUtils.kt 实现
 */
object CoroutineUtils {

    // 协程相关类名常量
    private const val CONTINUATION_INTERFACE = "kotlin.coroutines.Continuation"
    private const val BASE_CONTINUATION_IMPL = "kotlin.coroutines.jvm.internal.BaseContinuationImpl"
    private const val COROUTINE_SCOPE = "kotlinx.coroutines.CoroutineScope"
    private const val DEBUG_PROBES_IMPL = "kotlinx.coroutines.debug.internal.DebugProbesImpl"
    private const val JOB_INTERFACE = "kotlinx.coroutines.Job"
    private const val DEFERRED_INTERFACE = "kotlinx.coroutines.Deferred"
    
    // SuspendLambda 相关类
    private val SUSPEND_LAMBDA_CLASSES = listOf(
        "kotlin.coroutines.jvm.internal.SuspendLambda",
        "kotlin.coroutines.jvm.internal.RestrictedSuspendLambda"
    )

    // 协程方法名常量
    private const val INVOKE_SUSPEND_METHOD = "invokeSuspend"
    private const val INVOKE_METHOD = "invoke"
    private const val RESUME_WITH_METHOD = "resumeWith"

    /**
     * 创建栈帧分隔符 (用于区分创建栈)
     */
    const val CREATION_STACK_TRACE_SEPARATOR = "\b\b\b"
    const val CREATION_CLASS_NAME = "_COROUTINE._CREATION"

    /**
     * 检查方法是否是 invokeSuspend 方法
     */
    fun Method.isInvokeSuspend(): Boolean {
        return name() == INVOKE_SUSPEND_METHOD && 
               signature() == "(Ljava/lang/Object;)Ljava/lang/Object;"
    }

    /**
     * 检查方法是否是 invoke 方法（可能是协程入口）
     */
    fun Method.isInvoke(): Boolean {
        return name() == INVOKE_METHOD && 
               signature().contains("Ljava/lang/Object;)Ljava/lang/Object;")
    }

    /**
     * 检查方法是否是 SuspendLambda 的 invokeSuspend
     */
    fun Method.isSuspendLambda(): Boolean {
        return isInvokeSuspend() && declaringType().isSuspendLambda()
    }

    /**
     * 检查方法是否有 Continuation 参数
     */
    fun Method.hasContinuationParameter(): Boolean {
        return signature().contains("Lkotlin/coroutines/Continuation;)")
    }

    /**
     * 检查方法是否是 resumeWith 方法
     */
    fun Method.isResumeWith(): Boolean {
        return name() == RESUME_WITH_METHOD
    }

    /**
     * 检查类型是否是 SuspendLambda
     */
    fun ReferenceType.isSuspendLambda(): Boolean {
        return SUSPEND_LAMBDA_CLASSES.any { isSubtype(it) }
    }

    /**
     * 检查类型是否是 BaseContinuationImpl
     */
    fun Type.isBaseContinuationImpl(): Boolean {
        return isSubtype(BASE_CONTINUATION_IMPL)
    }

    /**
     * 检查类型是否是 CoroutineScope
     */
    fun Type.isCoroutineScope(): Boolean {
        return isSubtype(COROUTINE_SCOPE)
    }

    /**
     * 检查类型是否是 Continuation
     */
    fun Type.isContinuation(): Boolean {
        return isSubtype(CONTINUATION_INTERFACE)
    }

    /**
     * 检查类型是否是 Job
     */
    fun Type.isJob(): Boolean {
        return isSubtype(JOB_INTERFACE)
    }

    /**
     * 检查类型是否是指定类型的子类型或相同类型
     */
    fun Type.isSubTypeOrSame(className: String): Boolean {
        return name() == className || isSubtype(className)
    }

    /**
     * 检查类型是否是指定类型的子类型
     */
    fun Type.isSubtype(className: String): Boolean {
        return when (this) {
            is ClassType -> {
                if (name() == className) return true
                // 检查父类
                superclass()?.isSubtype(className) == true ||
                // 检查接口
                interfaces().any { it.isSubtype(className) }
            }
            is InterfaceType -> {
                if (name() == className) return true
                superinterfaces().any { it.isSubtype(className) }
            }
            else -> false
        }
    }

    /**
     * 获取 Location 的挂起退出模式
     */
    fun Location.getSuspendExitMode(): SuspendExitMode {
        val method = safeMethod() ?: return SuspendExitMode.NONE
        return when {
            method.isSuspendLambda() -> SuspendExitMode.SUSPEND_LAMBDA
            method.hasContinuationParameter() -> SuspendExitMode.SUSPEND_METHOD_PARAMETER
            (method.isInvokeSuspend() || method.isInvoke()) && isCoroutineExitPoint() -> 
                SuspendExitMode.SUSPEND_METHOD
            else -> SuspendExitMode.NONE
        }
    }

    /**
     * 检查 Location 是否是协程退出点
     */
    fun Location.isCoroutineExitPoint(): Boolean {
        return try {
            lineNumber() == -1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查 Location 是否是 invokeSuspend 方法且行号为负数
     */
    fun Location.isInvokeSuspendWithNegativeLineNumber(): Boolean {
        return isInvokeSuspend() && safeLineNumber() < 0
    }

    /**
     * 检查 Location 是否是 invokeSuspend 方法
     */
    fun Location.isInvokeSuspend(): Boolean {
        return safeMethod()?.isInvokeSuspend() == true
    }

    /**
     * 安全获取 Location 的方法
     */
    fun Location.safeMethod(): Method? {
        return try {
            method()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 安全获取 Location 的行号
     */
    fun Location.safeLineNumber(): Int {
        return try {
            lineNumber()
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * 检查线程名是否是协程相关线程
     */
    fun isCoroutineThread(threadName: String): Boolean {
        return threadName.contains("coroutine") ||
               threadName.contains("DefaultDispatcher") ||
               threadName.contains("CommonPool") ||
               threadName.startsWith("kotlinx.coroutines")
    }

    /**
     * 检查类名是否是协程生成的类
     */
    fun isCoroutineGeneratedClass(className: String): Boolean {
        return className.contains("\$\$suspended") ||
               className.contains("ContinuationImpl") ||
               className.contains("BaseContinuationImpl") ||
               className.contains("SuspendLambda") ||
               className.contains("\$suspendImpl\$") ||
               className.contains("\$invokeSuspend\$")
    }

    /**
     * 检查方法是否是协程内部方法（应该跳过的方法）
     */
    fun isCoroutineInternalMethod(methodName: String): Boolean {
        return methodName == "invokeSuspend" ||
               methodName == "resumeWith" ||
               methodName == "invoke" ||
               methodName.startsWith("access\$") ||
               methodName == "create" ||
               methodName == "<init>"
    }

    /**
     * 从 Continuation 变量名列表中检测 Continuation
     */
    private val CONTINUATION_VARIABLE_NAMES = listOf(
        "\$continuation",
        "\$completion",
        "continuation",
        "completion"
    )

    /**
     * 检查变量名是否是 Continuation 变量
     */
    fun isContinuationVariable(variableName: String): Boolean {
        return CONTINUATION_VARIABLE_NAMES.any { 
            variableName == it || variableName.endsWith(it) 
        }
    }
}
