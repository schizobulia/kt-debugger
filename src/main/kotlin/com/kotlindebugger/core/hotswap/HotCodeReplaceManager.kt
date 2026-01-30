package com.kotlindebugger.core.hotswap

import com.sun.jdi.ReferenceType
import com.sun.jdi.VirtualMachine
import java.io.File
import java.io.IOException

/**
 * 热代码替换结果
 * Result of hot code replacement operation
 */
sealed class HotCodeReplaceResult {
    /**
     * 成功完成热代码替换
     */
    data class Success(
        val reloadedClasses: List<String>,
        val message: String = "Hot code replacement completed successfully"
    ) : HotCodeReplaceResult()

    /**
     * 热代码替换失败
     */
    data class Failure(
        val errorMessage: String,
        val failedClasses: List<String> = emptyList()
    ) : HotCodeReplaceResult()

    /**
     * 热代码替换不支持
     */
    data class NotSupported(
        val reason: String
    ) : HotCodeReplaceResult()
}

/**
 * 要重新定义的类信息
 * Class information for redefinition
 */
data class ClassToRedefine(
    val className: String,
    val classBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ClassToRedefine
        if (className != other.className) return false
        if (!classBytes.contentEquals(other.classBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = className.hashCode()
        result = 31 * result + classBytes.contentHashCode()
        return result
    }
}

/**
 * 热代码替换管理器
 * 负责通过 JDI 执行类重定义操作
 * 
 * Hot Code Replace Manager
 * Responsible for redefining classes via JDI
 * 
 * Reference: JetBrains/intellij-community HotSwapManager
 */
class HotCodeReplaceManager(private val vm: VirtualMachine) {

    /**
     * 检查虚拟机是否支持类重定义
     * Check if the VM supports class redefinition
     */
    fun canRedefineClasses(): Boolean {
        return vm.canRedefineClasses()
    }

    /**
     * 检查虚拟机是否支持添加方法
     * Check if the VM supports adding methods during redefinition
     */
    @Suppress("DEPRECATION")
    fun canAddMethod(): Boolean {
        return vm.canAddMethod()
    }

    /**
     * 检查虚拟机是否支持更改类的层次结构
     * Check if the VM supports unrestricted redefinition
     */
    @Suppress("DEPRECATION")
    fun canUnrestrictedlyRedefineClasses(): Boolean {
        return vm.canUnrestrictedlyRedefineClasses()
    }

    /**
     * 从字节数组重新定义类
     * Redefine classes from byte arrays
     *
     * @param classesToRedefine 要重新定义的类列表
     * @return 热代码替换结果
     */
    fun redefineClasses(classesToRedefine: List<ClassToRedefine>): HotCodeReplaceResult {
        if (!canRedefineClasses()) {
            return HotCodeReplaceResult.NotSupported(
                "The target VM does not support class redefinition. " +
                "Make sure you are running with a DCEVM-enabled JVM or using a JDK that supports HotSwap."
            )
        }

        if (classesToRedefine.isEmpty()) {
            return HotCodeReplaceResult.Success(
                reloadedClasses = emptyList(),
                message = "No classes to redefine"
            )
        }

        return try {
            val redefinitionMap = mutableMapOf<ReferenceType, ByteArray>()
            val failedClasses = mutableListOf<String>()
            val successClasses = mutableListOf<String>()

            for (classToRedefine in classesToRedefine) {
                val className = classToRedefine.className
                val refTypes = vm.classesByName(className)

                if (refTypes.isEmpty()) {
                    // 类尚未加载，无法重定义
                    failedClasses.add(className)
                    continue
                }

                // 取第一个匹配的类型（通常只有一个）
                val refType = refTypes.first()
                redefinitionMap[refType] = classToRedefine.classBytes
                successClasses.add(className)
            }

            if (redefinitionMap.isEmpty()) {
                return HotCodeReplaceResult.Failure(
                    errorMessage = "No loaded classes found to redefine",
                    failedClasses = failedClasses
                )
            }

            // 执行类重定义
            vm.redefineClasses(redefinitionMap)

            if (failedClasses.isEmpty()) {
                HotCodeReplaceResult.Success(
                    reloadedClasses = successClasses,
                    message = "Hot code replacement completed successfully. Reloaded ${successClasses.size} class(es)."
                )
            } else {
                HotCodeReplaceResult.Success(
                    reloadedClasses = successClasses,
                    message = "Hot code replacement partially completed. Reloaded ${successClasses.size} class(es). " +
                            "${failedClasses.size} class(es) were not loaded and could not be redefined: ${failedClasses.joinToString()}"
                )
            }
        } catch (e: UnsupportedOperationException) {
            HotCodeReplaceResult.NotSupported(
                "Class redefinition not supported: ${e.message}"
            )
        } catch (e: ClassNotLoadedException) {
            HotCodeReplaceResult.Failure(
                errorMessage = "One or more classes are not loaded: ${e.message}",
                failedClasses = classesToRedefine.map { it.className }
            )
        } catch (e: ClassFormatError) {
            HotCodeReplaceResult.Failure(
                errorMessage = "Invalid class format: ${e.message}",
                failedClasses = classesToRedefine.map { it.className }
            )
        } catch (e: ClassCircularityError) {
            HotCodeReplaceResult.Failure(
                errorMessage = "Class circularity error: ${e.message}",
                failedClasses = classesToRedefine.map { it.className }
            )
        } catch (e: VerifyError) {
            HotCodeReplaceResult.Failure(
                errorMessage = "Class verification failed: ${e.message}",
                failedClasses = classesToRedefine.map { it.className }
            )
        } catch (e: UnsupportedClassVersionError) {
            HotCodeReplaceResult.Failure(
                errorMessage = "Unsupported class version: ${e.message}",
                failedClasses = classesToRedefine.map { it.className }
            )
        } catch (e: Exception) {
            HotCodeReplaceResult.Failure(
                errorMessage = "Hot code replacement failed: ${e.message ?: e.javaClass.simpleName}",
                failedClasses = classesToRedefine.map { it.className }
            )
        }
    }

    /**
     * 从类文件重新定义类
     * Redefine classes from class files
     *
     * @param classFiles 类名到文件路径的映射
     * @return 热代码替换结果
     */
    fun redefineClassesFromFiles(classFiles: Map<String, String>): HotCodeReplaceResult {
        val classesToRedefine = mutableListOf<ClassToRedefine>()
        val failedToRead = mutableListOf<String>()

        for ((className, filePath) in classFiles) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    failedToRead.add("$className (file not found: $filePath)")
                    continue
                }
                val bytes = file.readBytes()
                classesToRedefine.add(ClassToRedefine(className, bytes))
            } catch (e: IOException) {
                failedToRead.add("$className (${e.message})")
            }
        }

        if (failedToRead.isNotEmpty() && classesToRedefine.isEmpty()) {
            return HotCodeReplaceResult.Failure(
                errorMessage = "Failed to read class files: ${failedToRead.joinToString()}",
                failedClasses = classFiles.keys.toList()
            )
        }

        val result = redefineClasses(classesToRedefine)

        // 如果有文件读取失败，在结果中添加说明
        if (failedToRead.isNotEmpty() && result is HotCodeReplaceResult.Success) {
            return HotCodeReplaceResult.Success(
                reloadedClasses = result.reloadedClasses,
                message = result.message + " Warning: Failed to read some class files: ${failedToRead.joinToString()}"
            )
        }

        return result
    }

    /**
     * 获取热代码替换能力信息
     * Get hot code replacement capabilities
     */
    fun getCapabilities(): HotCodeReplaceCapabilities {
        return HotCodeReplaceCapabilities(
            canRedefineClasses = canRedefineClasses(),
            canAddMethod = canAddMethod(),
            canUnrestrictedlyRedefineClasses = canUnrestrictedlyRedefineClasses()
        )
    }

    companion object {
        /**
         * ClassNotLoadedException for redefineClasses
         */
        class ClassNotLoadedException(message: String) : Exception(message)
    }
}

/**
 * 热代码替换能力信息
 * Hot code replacement capabilities
 */
data class HotCodeReplaceCapabilities(
    val canRedefineClasses: Boolean,
    val canAddMethod: Boolean,
    val canUnrestrictedlyRedefineClasses: Boolean
) {
    override fun toString(): String {
        return buildString {
            append("Hot Code Replace Capabilities:\n")
            append("  - Can redefine classes: $canRedefineClasses\n")
            append("  - Can add methods: $canAddMethod\n")
            append("  - Unrestricted redefinition: $canUnrestrictedlyRedefineClasses")
        }
    }
}
