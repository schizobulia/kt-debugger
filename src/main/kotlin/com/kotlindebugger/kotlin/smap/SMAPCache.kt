package com.kotlindebugger.kotlin.smap

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.util.concurrent.ConcurrentHashMap

/**
 * SMAP 缓存
 * 缓存已解析的 SMAP 信息，避免重复解析
 */
class SMAPCache {

    // 类名 -> SMAP 的缓存
    private val cache = ConcurrentHashMap<String, SMAP?>()

    // 类名 -> SMAP 字符串的缓存（用于懒解析）
    private val rawCache = ConcurrentHashMap<String, String?>()

    /**
     * 从类字节码中提取并缓存 SMAP
     */
    fun getOrExtract(className: String, classBytes: ByteArray): SMAP? {
        return cache.computeIfAbsent(className) {
            val smapString = extractSMAPString(classBytes)
            if (smapString != null) {
                SMAPParser.parse(smapString)
            } else {
                null
            }
        }
    }

    /**
     * 从 SMAP 字符串解析并缓存
     */
    fun getOrParse(className: String, smapString: String?): SMAP? {
        if (smapString == null) return null

        return cache.computeIfAbsent(className) {
            SMAPParser.parse(smapString)
        }
    }

    /**
     * 获取缓存的 SMAP
     */
    fun get(className: String): SMAP? {
        return cache[className]
    }

    /**
     * 缓存 SMAP
     */
    fun put(className: String, smap: SMAP?) {
        cache[className] = smap
    }

    /**
     * 清除缓存
     */
    fun clear() {
        cache.clear()
        rawCache.clear()
    }

    /**
     * 移除指定类的缓存
     */
    fun remove(className: String) {
        cache.remove(className)
        rawCache.remove(className)
    }

    /**
     * 获取缓存大小
     */
    fun size(): Int = cache.size

    /**
     * 从类字节码中提取 SMAP 字符串
     */
    private fun extractSMAPString(classBytes: ByteArray): String? {
        var smapString: String? = null

        try {
            val reader = ClassReader(classBytes)
            reader.accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitSource(source: String?, debug: String?) {
                    smapString = debug
                }
            }, ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES)
        } catch (e: Exception) {
            // 忽略解析错误
        }

        return smapString
    }

    companion object {
        /**
         * 从 ReferenceType 获取 SMAP 字符串
         * 使用 JDI 的 sourceDebugExtension() 方法
         */
        fun extractFromReferenceType(refType: com.sun.jdi.ReferenceType): String? {
            return try {
                refType.sourceDebugExtension()
            } catch (e: com.sun.jdi.AbsentInformationException) {
                null
            } catch (e: Exception) {
                null
            }
        }
    }
}
