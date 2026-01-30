#!/bin/bash

# Kotlin调试器构建脚本
# 作者: Claude
# 描述: 用于构建Kotlin调试器的打包脚本

set -e  # 遇到错误立即退出

# 使用说明
usage() {
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -h, --help      显示帮助信息"
    echo "  -s, --skip-tests 跳过单元测试"
    echo "  -t, --test-only 仅运行测试"
    echo "  -c, --clean     仅清理构建"
    echo ""
    echo "示例:"
    echo "  $0              # 完整构建（包含测试）"
    echo "  $0 -s           # 跳过测试构建"
    echo "  $0 -t           # 仅运行测试"
}

# 解析命令行参数
SKIP_TESTS=false
TEST_ONLY=false
CLEAN_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            usage
            exit 0
            ;;
        -s|--skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        -t|--test-only)
            TEST_ONLY=true
            shift
            ;;
        -c|--clean)
            CLEAN_ONLY=true
            shift
            ;;
        *)
            print_error "未知选项: $1"
            usage
            exit 1
            ;;
    esac
done

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

print_info "开始构建Kotlin调试器..."
print_info "项目目录: $PROJECT_ROOT"

# 检查Java环境
if ! command -v java &> /dev/null; then
    print_error "Java未安装或不在PATH中"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    print_error "需要Java 17或更高版本，当前版本: $JAVA_VERSION"
    exit 1
fi

print_success "Java版本检查通过: $(java -version 2>&1 | head -n 1)"

# 检查Gradle Wrapper
if [ ! -f "./gradlew" ]; then
    print_error "未找到Gradle Wrapper (gradlew)"
    exit 1
fi

# 设置执行权限
chmod +x ./gradlew

# 仅清理模式
if [ "$CLEAN_ONLY" = true ]; then
    print_info "清理构建..."
    ./gradlew clean
    print_success "清理完成"
    exit 0
fi

# 仅测试模式
if [ "$TEST_ONLY" = true ]; then
    print_info "运行测试..."
    if ./gradlew test; then
        print_success "所有测试通过"
        exit 0
    else
        print_error "测试失败"
        exit 1
    fi
fi

# 清理之前的构建
print_info "清理之前的构建..."
./gradlew clean
rm -rf release/kotlin-debugger-1.0-SNAPSHOT-all.jar
rm -rf release/InteractiveTest.jar

# 根据参数决定是否运行测试
if [ "$SKIP_TESTS" = false ]; then
    print_info "运行单元测试..."
    if ./gradlew test; then
        print_success "所有测试通过"
    else
        print_error "测试失败"
        print_warning "可以使用 -s 选项跳过测试进行构建"
        exit 1
    fi
else
    print_warning "跳过单元测试"
fi

# 构建项目
print_info "构建项目..."
if [ "$SKIP_TESTS" = true ]; then
    # 跳过测试时，使用特定的任务组合
    if ./gradlew compileKotlin jar; then
        print_success "项目构建成功（跳过测试）"
    else
        print_error "项目构建失败"
        exit 1
    fi
else
    # 完整构建
    if ./gradlew build; then
        print_success "项目构建成功"
    else
        print_error "项目构建失败"
        exit 1
    fi
fi

# 创建Fat JAR (包含所有依赖)
print_info "创建发布包..."
if ./gradlew fatJar; then
    print_success "发布包创建成功"
else
    print_error "发布包创建失败"
    exit 1
fi

# 查找生成的JAR文件
JAR_FILE=$(find . -name "*-all.jar" -type f | head -n 1)
if [ -z "$JAR_FILE" ]; then
    print_error "未找到生成的JAR文件"
    exit 1
fi

JAR_NAME=$(basename "$JAR_FILE")
print_success "生成的JAR文件: $JAR_NAME"

# 创建发布目录
RELEASE_DIR="$PROJECT_ROOT/release"
mkdir -p "$RELEASE_DIR"

# 复制JAR到发布目录
cp "$JAR_FILE" "$RELEASE_DIR/"
print_success "JAR文件已复制到发布目录: $RELEASE_DIR"

# 生成版本信息
VERSION=$(grep "version =" build.gradle.kts | cut -d'"' -f2)
BUILD_TIME=$(date '+%Y-%m-%d %H:%M:%S')
GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")

cat > "$RELEASE_DIR/VERSION.txt" << EOF
Kotlin Debugger
Version: $VERSION
Build Time: $BUILD_TIME
Git Commit: $GIT_COMMIT
Git Branch: $GIT_BRANCH
EOF

print_success "版本信息已生成: $RELEASE_DIR/VERSION.txt"

# 创建启动脚本
cat > "$RELEASE_DIR/kotlin-debugger.sh" << 'EOF'
#!/bin/bash

# Kotlin调试器启动脚本

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE=$(find "$SCRIPT_DIR" -name "*-all.jar" -type f | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "[ERROR] 未找到调试器JAR文件"
    exit 1
fi

java -jar "$JAR_FILE" "$@"
EOF

chmod +x "$RELEASE_DIR/kotlin-debugger.sh"
print_success "启动脚本已创建: $RELEASE_DIR/kotlin-debugger.sh"

# 创建Windows启动脚本
cat > "$RELEASE_DIR/kotlin-debugger.bat" << 'EOF'
@echo off
setlocal

set SCRIPT_DIR=%~dp0
for %%f in ("%SCRIPT_DIR%*-all.jar") do set JAR_FILE=%%f

if "%JAR_FILE%"=="" (
    echo [ERROR] 未找到调试器JAR文件
    exit /b 1
)

java -jar "%JAR_FILE%" %*
EOF

print_success "Windows启动脚本已创建: $RELEASE_DIR/kotlin-debugger.bat"

# 显示构建结果
echo ""
print_success "构建完成！"
echo ""
echo "发布文件位置:"
echo "  - JAR文件: $RELEASE_DIR/$JAR_NAME"
echo "  - Linux/Mac启动脚本: $RELEASE_DIR/kotlin-debugger.sh"
echo "  - Windows启动脚本: $RELEASE_DIR/kotlin-debugger.bat"
echo "  - 版本信息: $RELEASE_DIR/VERSION.txt"
echo ""
print_info "使用方法:"
echo "  Linux/Mac: ./release/kotlin-debugger.sh [选项]"
echo "  Windows: .\\release\\kotlin-debugger.bat [选项]"
echo ""

# 检查test-program目录并打包测试程序
if [ -d "test-program" ]; then
    print_info "检测到test-program目录，准备打包测试程序..."

    # 检查InteractiveTest.kt文件
    if [ -f "test-program/InteractiveTest.kt" ]; then
        print_info "编译测试程序..."

        # 创建test-program的build目录
        mkdir -p test-program/build

        # 编译InteractiveTest.kt
        if kotlinc -cp "$JAR_FILE" test-program/InteractiveTest.kt -d test-program/build -include-runtime -d test-program/InteractiveTest.jar 2>/dev/null; then
            print_success "测试程序编译成功"
            cp test-program/InteractiveTest.jar "$RELEASE_DIR/"
            print_info "测试程序已复制到发布目录: $RELEASE_DIR/InteractiveTest.jar"
        else
            print_warning "测试程序编译失败，请确保kotlinc可用"
        fi
    fi
fi

# 构建 vscode-kotlin-debug/example/kt-debug-test 项目
KT_DEBUG_TEST_DIR="$PROJECT_ROOT/vscode-kotlin-debug/example/kt-debug-test"
if [ -d "$KT_DEBUG_TEST_DIR" ]; then
    print_info "检测到 kt-debug-test 项目目录，准备打包..."

    # 进入 kt-debug-test 目录
    cd "$KT_DEBUG_TEST_DIR"

    # 删除原有的构建目录，避免缓存问题
    print_info "清理 kt-debug-test 项目的旧构建文件..."
    rm -rf build/

    # 检查并设置 gradlew 执行权限
    if [ -f "./gradlew" ]; then
        chmod +x ./gradlew

        print_info "构建 kt-debug-test 项目..."
        if ./gradlew build jar; then
            print_success "kt-debug-test 项目构建成功"
            
            # 显示生成的 JAR 文件
            KT_DEBUG_JAR=$(find build/libs -name "*.jar" -type f 2>/dev/null | head -n 1)
            if [ -n "$KT_DEBUG_JAR" ]; then
                print_success "生成的 JAR 文件: $KT_DEBUG_JAR"
            fi
        else
            print_error "kt-debug-test 项目构建失败"
        fi
    else
        print_warning "kt-debug-test 项目未找到 gradlew，跳过构建"
    fi

    # 返回项目根目录
    cd "$PROJECT_ROOT"
else
    print_warning "未找到 kt-debug-test 项目目录: $KT_DEBUG_TEST_DIR"
fi

# 提示 VSCode 扩展构建
echo ""
print_info "如需构建 VSCode 扩展，请运行:"
echo "  bash scripts/vscode-ext.sh build"
echo ""

print_success "所有构建任务已完成！"