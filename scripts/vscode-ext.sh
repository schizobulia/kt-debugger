#!/bin/bash

# VSCode Kotlin Debug 扩展打包脚本
# 功能: 安装、打包、发布 VSCode 扩展

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 打印函数
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

print_header() {
    echo -e "\n${CYAN}========================================${NC}"
    echo -e "${CYAN}  $1${NC}"
    echo -e "${CYAN}========================================${NC}\n"
}

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VSCODE_EXT_DIR="$PROJECT_ROOT/vscode-kotlin-debug"
JAR_SOURCE="$PROJECT_ROOT/build/libs/kotlin-debugger-1.0-SNAPSHOT-all.jar"
JAR_TARGET="$VSCODE_EXT_DIR/kotlin-debugger.jar"

# 使用说明
usage() {
    echo "用法: $0 [命令] [选项]"
    echo ""
    echo "命令:"
    echo "  build           构建并打包 VSCode 扩展 (默认)"
    echo "  install         本地安装扩展到 VSCode"
    echo "  publish         发布扩展到 VSCode 市场"
    echo "  version         更新版本号"
    echo "  clean           清理构建产物"
    echo "  help            显示帮助信息"
    echo ""
    echo "选项:"
    echo "  --skip-jar      跳过 JAR 构建 (使用已有的 JAR)"
    echo "  --pre-release   发布为预发布版本"
    echo "  --major         版本号升级: 主版本 (1.0.0 -> 2.0.0)"
    echo "  --minor         版本号升级: 次版本 (1.0.0 -> 1.1.0)"
    echo "  --patch         版本号升级: 补丁版本 (1.0.0 -> 1.0.1) [默认]"
    echo "  --version=X.Y.Z 设置指定版本号"
    echo ""
    echo "示例:"
    echo "  $0 build                    # 构建扩展"
    echo "  $0 install                  # 本地安装"
    echo "  $0 version --minor          # 升级次版本号"
    echo "  $0 version --version=1.2.0  # 设置指定版本"
    echo "  $0 publish                  # 发布到市场"
    echo "  $0 publish --pre-release    # 发布预发布版"
}

# 检查依赖
check_dependencies() {
    print_info "检查依赖..."
    
    # 检查 Node.js
    if ! command -v node &> /dev/null; then
        print_error "Node.js 未安装，请先安装 Node.js"
        exit 1
    fi
    print_success "Node.js: $(node --version)"
    
    # 检查 npm
    if ! command -v npm &> /dev/null; then
        print_error "npm 未安装"
        exit 1
    fi
    print_success "npm: $(npm --version)"
    
    # 检查 vsce
    if ! command -v vsce &> /dev/null; then
        print_warning "vsce 未安装，正在安装..."
        npm install -g @vscode/vsce
    fi
    print_success "vsce: $(vsce --version)"
    
    # 检查 Java (用于构建 JAR)
    if ! command -v java &> /dev/null; then
        print_error "Java 未安装，请先安装 Java 17+"
        exit 1
    fi
    print_success "Java: $(java -version 2>&1 | head -n 1)"
}

# 构建调试器 JAR
build_jar() {
    print_header "构建调试器 JAR"
    
    cd "$PROJECT_ROOT"
    
    if [ ! -f "$JAR_SOURCE" ] || [ "$FORCE_BUILD_JAR" = true ]; then
        print_info "运行 Gradle 构建..."
        ./gradlew fatJar
        
        if [ ! -f "$JAR_SOURCE" ]; then
            print_error "JAR 构建失败: $JAR_SOURCE 不存在"
            exit 1
        fi
    else
        print_info "使用已存在的 JAR: $JAR_SOURCE"
    fi
    
    # 复制 JAR 到扩展目录
    print_info "复制 JAR 到扩展目录..."
    cp "$JAR_SOURCE" "$JAR_TARGET"
    print_success "JAR 已复制到: $JAR_TARGET"
}

# 安装 npm 依赖
install_npm_deps() {
    print_header "安装 npm 依赖"
    
    cd "$VSCODE_EXT_DIR"
    
    if [ ! -d "node_modules" ]; then
        print_info "安装依赖..."
        npm install
    else
        print_info "依赖已存在，跳过安装"
    fi
    
    print_success "npm 依赖安装完成"
}

# 编译 TypeScript
compile_typescript() {
    print_header "编译 TypeScript"
    
    cd "$VSCODE_EXT_DIR"
    npm run compile
    
    print_success "TypeScript 编译完成"
}

# 打包扩展
package_extension() {
    print_header "打包 VSCode 扩展"
    
    cd "$VSCODE_EXT_DIR"
    
    # 确保 JAR 存在
    if [ ! -f "$JAR_TARGET" ]; then
        print_error "JAR 文件不存在: $JAR_TARGET"
        print_info "请先运行: $0 build"
        exit 1
    fi
    
    # 打包
    VSIX_FILE=$(vsce package --out ./dist/ 2>&1 | grep -oE 'kotlin-debug-[0-9]+\.[0-9]+\.[0-9]+\.vsix')
    
    if [ -z "$VSIX_FILE" ]; then
        VSIX_FILE=$(ls -1t ./dist/*.vsix 2>/dev/null | head -n1)
    fi
    
    if [ -n "$VSIX_FILE" ]; then
        print_success "扩展已打包: $VSIX_FILE"
    else
        print_success "扩展打包完成，请查看 dist 目录"
    fi
    
    # 显示包内容
    print_info "包大小:"
    ls -lh ./dist/*.vsix 2>/dev/null || true
}

# 本地安装扩展
install_extension() {
    print_header "安装扩展到 VSCode"
    
    cd "$VSCODE_EXT_DIR"
    
    # 查找最新的 vsix 文件
    VSIX_FILE=$(ls -1t ./dist/*.vsix 2>/dev/null | head -n1)
    
    if [ -z "$VSIX_FILE" ]; then
        print_error "未找到 .vsix 文件，请先运行: $0 build"
        exit 1
    fi
    
    print_info "安装扩展: $VSIX_FILE"
    
    # 检查 code 命令是否可用
    if command -v code &> /dev/null; then
        code --install-extension "$VSIX_FILE" --force
        print_success "扩展已安装到 VSCode"
    else
        print_warning "未找到 'code' 命令"
        print_info "请手动安装: 在 VSCode 中打开 $VSIX_FILE"
    fi
}

# 发布扩展到市场
publish_extension() {
    print_header "发布扩展到 VSCode 市场"
    
    cd "$VSCODE_EXT_DIR"
    
    # 检查 token
    if [ -z "$VSCE_PAT" ]; then
        print_error "未设置 VSCE_PAT 环境变量"
        print_info "请设置 Personal Access Token:"
        print_info "  export VSCE_PAT=your-token-here"
        print_info ""
        print_info "获取 Token: https://dev.azure.com/<your-org>/_usersSettings/tokens"
        print_info "所需权限: Marketplace (Read & Publish)"
        exit 1
    fi
    
    # 确保 JAR 存在
    if [ ! -f "$JAR_TARGET" ]; then
        print_error "JAR 文件不存在: $JAR_TARGET"
        print_info "请先运行: $0 build"
        exit 1
    fi
    
    # 发布
    if [ "$PRE_RELEASE" = true ]; then
        print_info "发布预发布版本..."
        vsce publish --pre-release -p "$VSCE_PAT"
    else
        print_info "发布正式版本..."
        vsce publish -p "$VSCE_PAT"
    fi
    
    print_success "扩展发布成功!"
}

# 更新版本号
update_version() {
    print_header "更新版本号"
    
    cd "$VSCODE_EXT_DIR"
    
    # 获取当前版本
    CURRENT_VERSION=$(node -p "require('./package.json').version")
    print_info "当前版本: $CURRENT_VERSION"
    
    if [ -n "$SPECIFIC_VERSION" ]; then
        # 使用指定版本
        NEW_VERSION="$SPECIFIC_VERSION"
    else
        # 解析版本号
        IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION"
        
        case "$VERSION_BUMP" in
            major)
                NEW_VERSION="$((MAJOR + 1)).0.0"
                ;;
            minor)
                NEW_VERSION="$MAJOR.$((MINOR + 1)).0"
                ;;
            patch|*)
                NEW_VERSION="$MAJOR.$MINOR.$((PATCH + 1))"
                ;;
        esac
    fi
    
    print_info "新版本: $NEW_VERSION"
    
    # 更新 package.json
    npm version "$NEW_VERSION" --no-git-tag-version
    
    # 同步更新主项目的 release 版本
    echo "$NEW_VERSION" > "$PROJECT_ROOT/release/VERSION.txt"
    
    print_success "版本已更新为 $NEW_VERSION"
    
    # 提示用户
    print_info ""
    print_info "下一步操作:"
    print_info "  1. 提交更改: git add -A && git commit -m 'Bump version to $NEW_VERSION'"
    print_info "  2. 创建标签: git tag v$NEW_VERSION"
    print_info "  3. 推送: git push && git push --tags"
}

# 清理构建产物
clean_build() {
    print_header "清理构建产物"
    
    cd "$VSCODE_EXT_DIR"
    
    # 清理
    rm -rf ./out
    rm -rf ./dist
    rm -f ./kotlin-debugger.jar
    rm -rf ./node_modules
    
    print_success "构建产物已清理"
}

# 完整构建流程
full_build() {
    print_header "完整构建 VSCode 扩展"
    
    check_dependencies
    
    if [ "$SKIP_JAR" != true ]; then
        build_jar
    else
        # 检查 JAR 是否存在
        if [ -f "$JAR_SOURCE" ]; then
            print_info "复制已存在的 JAR..."
            cp "$JAR_SOURCE" "$JAR_TARGET"
        elif [ ! -f "$JAR_TARGET" ]; then
            print_error "未找到 JAR 文件，请先构建或不要使用 --skip-jar"
            exit 1
        fi
    fi
    
    install_npm_deps
    compile_typescript
    
    # 创建 dist 目录
    mkdir -p "$VSCODE_EXT_DIR/dist"
    
    package_extension
    
    print_header "构建完成"
    print_info "生成的文件:"
    ls -lh "$VSCODE_EXT_DIR/dist/"*.vsix 2>/dev/null || print_warning "未找到 vsix 文件"
}

# 解析参数
COMMAND="${1:-build}"
shift || true

SKIP_JAR=false
PRE_RELEASE=false
VERSION_BUMP="patch"
SPECIFIC_VERSION=""
FORCE_BUILD_JAR=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-jar)
            SKIP_JAR=true
            shift
            ;;
        --pre-release)
            PRE_RELEASE=true
            shift
            ;;
        --major)
            VERSION_BUMP="major"
            shift
            ;;
        --minor)
            VERSION_BUMP="minor"
            shift
            ;;
        --patch)
            VERSION_BUMP="patch"
            shift
            ;;
        --version=*)
            SPECIFIC_VERSION="${1#*=}"
            shift
            ;;
        --force)
            FORCE_BUILD_JAR=true
            shift
            ;;
        *)
            print_error "未知选项: $1"
            usage
            exit 1
            ;;
    esac
done

# 执行命令
case "$COMMAND" in
    build)
        full_build
        ;;
    install)
        full_build
        install_extension
        ;;
    publish)
        full_build
        publish_extension
        ;;
    version)
        update_version
        ;;
    clean)
        clean_build
        ;;
    help|--help|-h)
        usage
        ;;
    *)
        print_error "未知命令: $COMMAND"
        usage
        exit 1
        ;;
esac
