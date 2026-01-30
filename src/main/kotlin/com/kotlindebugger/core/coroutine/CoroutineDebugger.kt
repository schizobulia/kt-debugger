package com.kotlindebugger.core.coroutine

import com.kotlindebugger.common.model.SourcePosition
import com.kotlindebugger.common.util.JdiUtils.safeLineNumber
import com.kotlindebugger.common.util.JdiUtils.safeSourceName
import com.kotlindebugger.core.coroutine.CoroutineUtils.isBaseContinuationImpl
import com.kotlindebugger.core.coroutine.CoroutineUtils.isSuspendLambda
import com.sun.jdi.*

/**
 * 协程调试器
 * 负责从目标 JVM 中提取协程信息
 * 参考 IntelliJ Community 的 CoroutineDebugProbesProxy 和 CoroutineInfoProvider 实现
 */
class CoroutineDebugger(private val vm: VirtualMachine) {

    // 缓存 DebugProbesImpl 类引用
    private var debugProbesImplClass: ClassType? = null

    companion object {
        private const val DEBUG_PROBES_IMPL_CLASS = "kotlinx.coroutines.debug.internal.DebugProbesImpl"
        private const val DEBUG_PROBES_IMPL_INSTALLED_FIELD = "INSTANCE"
        
        // 协程信息方法
        private const val DUMP_COROUTINES_INFO_METHOD = "dumpCoroutinesInfo"
        
        // DebugCoroutineInfo 字段
        private const val COROUTINE_INFO_FIELD_STATE = "state"
        private const val COROUTINE_INFO_FIELD_CONTEXT = "context"
        private const val COROUTINE_INFO_FIELD_LAST_OBSERVED_THREAD = "lastObservedThread"
        private const val COROUTINE_INFO_FIELD_LAST_OBSERVED_FRAME = "lastObservedFrame"
        private const val COROUTINE_INFO_FIELD_SEQUENCE_NUMBER = "sequenceNumber"
    }

    /**
     * 检查协程调试探针是否已安装
     */
    fun isCoroutineDebugProbesInstalled(): Boolean {
        return try {
            getDebugProbesImplClass() != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取所有协程信息
     */
    fun getAllCoroutines(): List<CoroutineInfo> {
        val probesClass = getDebugProbesImplClass() ?: return extractCoroutinesFromThreads()
        
        return try {
            extractCoroutinesFromDebugProbes(probesClass)
        } catch (e: Exception) {
            // 如果通过 DebugProbes 获取失败，尝试从线程中提取
            extractCoroutinesFromThreads()
        }
    }

    /**
     * 通过 DebugProbes 提取协程信息
     */
    private fun extractCoroutinesFromDebugProbes(probesClass: ClassType): List<CoroutineInfo> {
        val coroutines = mutableListOf<CoroutineInfo>()
        
        val thread = findSuspendedThread() ?: return emptyList()
        
        try {
            // 获取 INSTANCE 字段
            val instanceField = probesClass.fieldByName(DEBUG_PROBES_IMPL_INSTALLED_FIELD)
            if (instanceField == null) return extractCoroutinesFromThreads()
            
            val instance = probesClass.getValue(instanceField) as? ObjectReference
                ?: return extractCoroutinesFromThreads()
            
            // 调用 dumpCoroutinesInfo 方法
            val dumpMethod = probesClass.methodsByName(DUMP_COROUTINES_INFO_METHOD).firstOrNull()
            if (dumpMethod == null) return extractCoroutinesFromThreads()
            
            val result = instance.invokeMethod(
                thread,
                dumpMethod,
                emptyList(),
                ObjectReference.INVOKE_SINGLE_THREADED
            ) as? ObjectReference ?: return extractCoroutinesFromThreads()
            
            // 解析结果列表
            coroutines.addAll(parseCoroutineInfoList(result, thread))
            
        } catch (e: Exception) {
            // 发生异常时回退到线程提取
            return extractCoroutinesFromThreads()
        }
        
        return coroutines
    }

    /**
     * 解析协程信息列表
     */
    private fun parseCoroutineInfoList(listRef: ObjectReference, thread: ThreadReference): List<CoroutineInfo> {
        val coroutines = mutableListOf<CoroutineInfo>()
        
        try {
            val listType = listRef.referenceType()
            
            // 获取 size
            val sizeMethod = listType.methodsByName("size").firstOrNull { 
                it.argumentTypes().isEmpty() 
            } ?: return emptyList()
            
            val sizeValue = listRef.invokeMethod(
                thread, sizeMethod, emptyList(), ObjectReference.INVOKE_SINGLE_THREADED
            ) as? IntegerValue ?: return emptyList()
            
            val size = sizeValue.value()
            
            // 获取 get 方法
            val getMethod = listType.methodsByName("get").firstOrNull { 
                it.argumentTypes().size == 1 
            } ?: return emptyList()
            
            // 遍历列表
            for (i in 0 until size) {
                try {
                    val indexValue = vm.mirrorOf(i)
                    val item = listRef.invokeMethod(
                        thread, getMethod, listOf(indexValue), ObjectReference.INVOKE_SINGLE_THREADED
                    ) as? ObjectReference ?: continue
                    
                    val coroutineInfo = parseCoroutineInfo(item, thread)
                    if (coroutineInfo != null) {
                        coroutines.add(coroutineInfo)
                    }
                } catch (e: Exception) {
                    // 跳过单个解析失败的协程
                    continue
                }
            }
        } catch (e: Exception) {
            // 忽略解析错误
        }
        
        return coroutines
    }

    /**
     * 解析单个协程信息
     */
    private fun parseCoroutineInfo(infoRef: ObjectReference, thread: ThreadReference): CoroutineInfo? {
        return try {
            val infoType = infoRef.referenceType()
            
            // 获取序列号（作为 ID）
            val sequenceNumberField = infoType.fieldByName(COROUTINE_INFO_FIELD_SEQUENCE_NUMBER)
            val id = if (sequenceNumberField != null) {
                (infoRef.getValue(sequenceNumberField) as? LongValue)?.value()
            } else null
            
            // 获取状态
            val stateField = infoType.fieldByName(COROUTINE_INFO_FIELD_STATE)
            val stateObj = stateField?.let { infoRef.getValue(it) as? ObjectReference }
            val stateStr = stateObj?.let { getEnumName(it) } ?: "SUSPENDED"
            val state = CoroutineState.fromString(stateStr)
            
            // 获取最后观察到的线程
            val lastThreadField = infoType.fieldByName(COROUTINE_INFO_FIELD_LAST_OBSERVED_THREAD)
            val lastThread = lastThreadField?.let { 
                infoRef.getValue(it) as? ThreadReference 
            }
            
            // 获取最后观察到的帧
            val lastFrameField = infoType.fieldByName(COROUTINE_INFO_FIELD_LAST_OBSERVED_FRAME)
            val lastFrame = lastFrameField?.let { 
                infoRef.getValue(it) as? ObjectReference 
            }
            
            // 获取协程上下文和名称
            val contextField = infoType.fieldByName(COROUTINE_INFO_FIELD_CONTEXT)
            val context = contextField?.let { infoRef.getValue(it) as? ObjectReference }
            val name = extractCoroutineName(context, thread, id)
            val dispatcher = extractDispatcherName(context, thread)
            
            CoroutineInfo(
                id = id,
                name = name,
                state = state,
                dispatcher = dispatcher,
                lastObservedThread = lastThread,
                lastObservedFrame = lastFrame
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取枚举值的名称
     */
    private fun getEnumName(enumRef: ObjectReference): String? {
        return try {
            val nameMethod = enumRef.referenceType().methodsByName("name").firstOrNull {
                it.argumentTypes().isEmpty()
            }
            if (nameMethod != null) {
                val thread = findSuspendedThread() ?: return null
                val result = enumRef.invokeMethod(
                    thread, nameMethod, emptyList(), ObjectReference.INVOKE_SINGLE_THREADED
                ) as? StringReference
                result?.value()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从协程上下文提取协程名称
     */
    private fun extractCoroutineName(context: ObjectReference?, thread: ThreadReference, id: Long?): String {
        if (context == null) return CoroutineInfo.DEFAULT_COROUTINE_NAME
        
        return try {
            // 尝试获取 CoroutineName 元素
            val getMethod = context.referenceType().methodsByName("get").firstOrNull { 
                it.argumentTypes().size == 1 
            }
            if (getMethod != null) {
                // 查找 CoroutineName.Key
                val keyClass = vm.classesByName("kotlinx.coroutines.CoroutineName\$Key").firstOrNull()
                if (keyClass != null) {
                    val instanceField = keyClass.fieldByName("INSTANCE")
                    val keyInstance = instanceField?.let { keyClass.getValue(it) as? ObjectReference }
                    if (keyInstance != null) {
                        val nameElement = context.invokeMethod(
                            thread, getMethod, listOf(keyInstance), ObjectReference.INVOKE_SINGLE_THREADED
                        ) as? ObjectReference
                        
                        if (nameElement != null) {
                            val nameField = nameElement.referenceType().fieldByName("name")
                            val nameValue = nameField?.let { nameElement.getValue(it) as? StringReference }
                            if (nameValue != null) {
                                return nameValue.value()
                            }
                        }
                    }
                }
            }
            "${CoroutineInfo.DEFAULT_COROUTINE_NAME}#${id ?: "?"}"
        } catch (e: Exception) {
            "${CoroutineInfo.DEFAULT_COROUTINE_NAME}#${id ?: "?"}"
        }
    }

    /**
     * 从协程上下文提取调度器名称
     */
    private fun extractDispatcherName(context: ObjectReference?, thread: ThreadReference): String? {
        if (context == null) return null
        
        return try {
            // 尝试获取 ContinuationInterceptor 元素（即 Dispatcher）
            val getMethod = context.referenceType().methodsByName("get").firstOrNull { 
                it.argumentTypes().size == 1 
            }
            if (getMethod != null) {
                val keyClass = vm.classesByName("kotlin.coroutines.ContinuationInterceptor\$Key").firstOrNull()
                if (keyClass != null) {
                    val instanceField = keyClass.fieldByName("INSTANCE")
                    val keyInstance = instanceField?.let { keyClass.getValue(it) as? ObjectReference }
                    if (keyInstance != null) {
                        val dispatcher = context.invokeMethod(
                            thread, getMethod, listOf(keyInstance), ObjectReference.INVOKE_SINGLE_THREADED
                        ) as? ObjectReference
                        
                        if (dispatcher != null) {
                            // 获取 dispatcher 的 toString
                            val toStringMethod = dispatcher.referenceType().methodsByName("toString").firstOrNull {
                                it.argumentTypes().isEmpty()
                            }
                            if (toStringMethod != null) {
                                val result = dispatcher.invokeMethod(
                                    thread, toStringMethod, emptyList(), ObjectReference.INVOKE_SINGLE_THREADED
                                ) as? StringReference
                                return result?.value()
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从线程中提取协程（备选方案）
     * 当 DebugProbes 不可用时使用
     */
    private fun extractCoroutinesFromThreads(): List<CoroutineInfo> {
        val coroutines = mutableListOf<CoroutineInfo>()
        var idCounter = 1L
        
        for (thread in vm.allThreads()) {
            if (!thread.isSuspended) continue
            
            try {
                val frames = thread.frames()
                for (frame in frames) {
                    val location = frame.location()
                    val method = location.method()
                    val declaringType = location.declaringType()
                    
                    // 检查是否是 SuspendLambda 或 BaseContinuationImpl
                    if (declaringType.isSuspendLambda() || declaringType.isBaseContinuationImpl()) {
                        val coroutineInfo = extractCoroutineFromFrame(frame, thread, idCounter++)
                        if (coroutineInfo != null) {
                            coroutines.add(coroutineInfo)
                        }
                    }
                }
            } catch (e: Exception) {
                // 跳过无法检查的线程
                continue
            }
        }
        
        return coroutines.distinctBy { it.id }
    }

    /**
     * 从栈帧提取协程信息
     */
    private fun extractCoroutineFromFrame(
        frame: StackFrame, 
        thread: ThreadReference, 
        defaultId: Long
    ): CoroutineInfo? {
        return try {
            val location = frame.location()
            val declaringType = location.declaringType()
            
            // 尝试获取 this 对象（即 Continuation）
            val thisObject = frame.thisObject()
            
            // 从类名提取协程名称
            val className = declaringType.name()
            val simpleName = className.substringAfterLast('.').substringBefore('$')
            
            // 确定状态：如果当前正在运行则为 RUNNING，否则为 SUSPENDED
            val state = if (frame == thread.frames().firstOrNull()) {
                CoroutineState.RUNNING
            } else {
                CoroutineState.SUSPENDED
            }
            
            // 构建栈帧列表
            val stackFrames = buildCoroutineStackFrames(frame, thread)
            
            CoroutineInfo(
                id = defaultId,
                name = simpleName,
                state = state,
                dispatcher = null,
                lastObservedThread = thread,
                lastObservedFrame = thisObject,
                continuationStackFrames = stackFrames
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 构建协程栈帧列表
     */
    private fun buildCoroutineStackFrames(
        startFrame: StackFrame, 
        thread: ThreadReference
    ): List<CoroutineStackFrameItem> {
        val frames = mutableListOf<CoroutineStackFrameItem>()
        
        try {
            val allFrames = thread.frames()
            val startIndex = allFrames.indexOf(startFrame)
            
            for (i in startIndex until allFrames.size) {
                val frame = allFrames[i]
                val location = frame.location()
                
                val className = location.declaringType().name()
                val methodName = location.method().name()
                val sourceName = location.safeSourceName()
                val lineNumber = location.safeLineNumber()
                
                val sourcePosition = if (sourceName != null && lineNumber > 0) {
                    SourcePosition(sourceName, lineNumber)
                } else null
                
                frames.add(CoroutineStackFrameItem(
                    className = className,
                    methodName = methodName,
                    location = sourcePosition
                ))
            }
        } catch (e: Exception) {
            // 忽略错误
        }
        
        return frames
    }

    /**
     * 获取 DebugProbesImpl 类
     */
    private fun getDebugProbesImplClass(): ClassType? {
        if (debugProbesImplClass != null) return debugProbesImplClass
        
        val classes = vm.classesByName(DEBUG_PROBES_IMPL_CLASS)
        debugProbesImplClass = classes.firstOrNull() as? ClassType
        return debugProbesImplClass
    }

    /**
     * 查找第一个暂停的线程
     */
    private fun findSuspendedThread(): ThreadReference? {
        return vm.allThreads().find { thread ->
            thread.isSuspended && try {
                thread.frames().isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        debugProbesImplClass = null
    }

    /**
     * 获取协程调试是否可用的状态描述
     */
    fun getDebugStatusDescription(): String {
        return when {
            isCoroutineDebugProbesInstalled() -> 
                "Coroutine debug probes are installed. Full coroutine debugging is available."
            else -> 
                "Coroutine debug probes are not installed. " +
                "Limited coroutine debugging available (based on thread analysis). " +
                "Add -Dkotlinx.coroutines.debug to enable full debugging."
        }
    }
}
