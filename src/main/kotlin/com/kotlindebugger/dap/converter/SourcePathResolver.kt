package com.kotlindebugger.dap.converter

import java.io.File

/**
 * 源路径解析器
 * 负责将 JDI 返回的源文件名转换为完整路径
 */
class SourcePathResolver {
    
    private val sourcePaths = mutableListOf<String>()
    private val fileCache = mutableMapOf<String, String?>()
    
    /**
     * 添加源代码搜索路径
     */
    fun addSourcePaths(paths: List<String>) {
        sourcePaths.addAll(paths)
        fileCache.clear() // 清除缓存，因为路径已更改
    }
    
    /**
     * 设置源代码搜索路径（替换现有的）
     */
    fun setSourcePaths(paths: List<String>) {
        sourcePaths.clear()
        sourcePaths.addAll(paths)
        fileCache.clear()
    }
    
    /**
     * 获取当前源路径列表
     */
    fun getSourcePaths(): List<String> = sourcePaths.toList()
    
    /**
     * 解析源文件路径
     * 
     * @param sourceName JDI 返回的源文件名（如 "Main.kt" 或 "com/example/Main.kt"）
     * @return 完整文件路径，如果找不到则返回原始文件名
     */
    fun resolveSourcePath(sourceName: String?): String? {
        if (sourceName == null) return null
        
        // 检查缓存
        fileCache[sourceName]?.let { return it }
        
        // 如果已经是绝对路径且文件存在，直接返回
        if (File(sourceName).isAbsolute && File(sourceName).exists()) {
            fileCache[sourceName] = sourceName
            return sourceName
        }
        
        // 在源路径中搜索
        for (basePath in sourcePaths) {
            val resolvedPath = findSourceFile(basePath, sourceName)
            if (resolvedPath != null) {
                fileCache[sourceName] = resolvedPath
                return resolvedPath
            }
        }
        
        // 如果没找到，返回原始名称
        fileCache[sourceName] = sourceName
        return sourceName
    }
    
    /**
     * 在指定基础路径下搜索源文件
     */
    private fun findSourceFile(basePath: String, sourceName: String): String? {
        val baseDir = File(basePath)
        if (!baseDir.exists() || !baseDir.isDirectory) return null
        
        // 提取文件名（处理可能的包路径）
        val fileName = sourceName.substringAfterLast('/')
        
        // 首先尝试直接拼接
        val directPath = File(basePath, sourceName)
        if (directPath.exists() && directPath.isFile) {
            return directPath.absolutePath
        }
        
        // 递归搜索匹配的文件
        return searchFile(baseDir, fileName)?.absolutePath
    }
    
    /**
     * 递归搜索文件
     */
    private fun searchFile(dir: File, fileName: String): File? {
        val files = dir.listFiles() ?: return null
        
        for (file in files) {
            if (file.isDirectory) {
                val found = searchFile(file, fileName)
                if (found != null) return found
            } else if (file.name == fileName) {
                return file
            }
        }
        return null
    }
    
    /**
     * 将完整路径转换为相对路径（用于断点设置）
     */
    fun toRelativePath(absolutePath: String): String {
        // 提取文件名
        return File(absolutePath).name
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        fileCache.clear()
    }
}
