#!/bin/bash

# 用法: ./release.sh -v <版本号> [-m <提交信息>] [-d] [-t] [-p] [-r]
#   -v  版本号（必填）
#   -m  提交信息（可选，默认为 "release <版本号>"）
#   -d  删除模式（配合 -t 删除tag，配合 -r 删除release，不指定则删除全部）
#   -t  执行tag操作（提交、打tag、合并到master、推送）
#   -p  执行打包操作（Maven打包、各平台打包）
#   -r  执行release操作（创建GitHub Release并上传产物）
#   不指定 -t/-p/-r 时默认执行全部（删除模式下删除tag+release）
#
# 平台包说明（JDK 8 兼容）:
#   macOS  - 可执行JAR + 启动脚本，打包为 .zip
#   Windows - 可执行JAR + 启动脚本，打包为 .zip
#   Linux  - 可执行JAR + 启动脚本，打包为 .tar.gz
#
# 示例:
#   ./release.sh -v 1.0.1                            # 默认执行tag+package+release
#   ./release.sh -v 1.0.1 -t                         # 仅打tag并推送
#   ./release.sh -v 1.0.1 -p                         # 仅打包
#   ./release.sh -v 1.0.1 -r                         # 仅创建release（需先打包）
#   ./release.sh -v 1.0.1 -tp                        # 打tag + 打包
#   ./release.sh -v 1.0.1 -pr                        # 打包 + 创建release
#   ./release.sh -v 1.0.1 -m "优化界面交互" -tpr     # 指定信息，执行全部
#   ./release.sh -v 1.0.1 -d                         # 删除tag+release
#   ./release.sh -v 1.0.1 -d -t                      # 仅删除tag
#   ./release.sh -v 1.0.1 -d -r                      # 仅删除release
#   ./release.sh -v 1.0.1 -d -tr                     # 删除tag+release

set -e

VERSION=""
COMMIT_MSG=""
DO_TAG=false
DO_PACKAGE=false
DO_RELEASE=false
DO_DELETE=false
APP_NAME="ZookeeperVisualizer"
JAR_NAME="zookeeper-visualizer.jar"
JAR_PATH="bin/$JAR_NAME"
DIST_DIR="dist"

usage() {
    echo "用法: ./release.sh -v <版本号> [-m <提交信息>] [-d] [-t] [-p] [-r]"
    echo "  -v  版本号（必填）"
    echo "  -m  提交信息（可选，默认为 \"release <版本号>\"）"
    echo "  -d  删除模式（配合 -t 删除tag，配合 -r 删除release，不指定则删除全部）"
    echo "  -t  执行tag操作"
    echo "  -p  执行打包操作（Maven打包 + 各平台打包）"
    echo "  -r  执行release操作（创建GitHub Release并上传）"
    echo "  不指定 -t/-p/-r 时默认执行全部"
    echo ""
    echo "示例:"
    echo "  ./release.sh -v 1.0.1"
    echo "  ./release.sh -v 1.0.1 -t"
    echo "  ./release.sh -v 1.0.1 -p"
    echo "  ./release.sh -v 1.0.1 -r"
    echo "  ./release.sh -v 1.0.1 -tpr"
    echo "  ./release.sh -v 1.0.1 -m \"优化界面交互\" -tpr"
    echo "  ./release.sh -v 1.0.1 -d"
    echo "  ./release.sh -v 1.0.1 -d -t"
    echo "  ./release.sh -v 1.0.1 -d -r"
    echo "  ./release.sh -v 1.0.1 -d -tr"
    exit 1
}

while getopts "v:m:dtprh" opt; do
    case $opt in
        v) VERSION="$OPTARG" ;;
        m) COMMIT_MSG="$OPTARG" ;;
        d) DO_DELETE=true ;;
        t) DO_TAG=true ;;
        p) DO_PACKAGE=true ;;
        r) DO_RELEASE=true ;;
        h) usage ;;
        *) usage ;;
    esac
done

if [ -z "$VERSION" ]; then
    echo "错误: 请提供版本号"
    usage
fi

# 删除模式下，未指定 -t/-r 时默认删除全部
if [ "$DO_DELETE" = true ]; then
    if [ "$DO_TAG" = false ] && [ "$DO_RELEASE" = false ]; then
        DO_TAG=true
        DO_RELEASE=true
    fi
    # 删除模式不需要 -p
    DO_PACKAGE=false
fi

# 非删除模式下，未指定任何动作时，默认执行全部
if [ "$DO_DELETE" = false ] && [ "$DO_TAG" = false ] && [ "$DO_PACKAGE" = false ] && [ "$DO_RELEASE" = false ]; then
    DO_TAG=true
    DO_PACKAGE=true
    DO_RELEASE=true
fi

COMMIT_MSG=${COMMIT_MSG:-"release $VERSION"}
CURRENT_BRANCH=$(git branch --show-current)

# 生成release说明: 优先 -m 参数，其次 CHANGELOG.md 对应版本内容，最后用默认值
RELEASE_NOTES_FILE="$DIST_DIR/release_notes.txt"
generate_release_notes() {
    mkdir -p "$DIST_DIR"
    if [ -n "$COMMIT_MSG" ] && [ "$COMMIT_MSG" != "release $VERSION" ]; then
        echo "$COMMIT_MSG" > "$RELEASE_NOTES_FILE"
    elif [ -f "CHANGELOG.md" ]; then
        sed -n "/^## $VERSION$/,/^## /{ /^## $VERSION$/d; /^## /d; p; }" CHANGELOG.md > "$RELEASE_NOTES_FILE"
        if [ ! -s "$RELEASE_NOTES_FILE" ]; then
            echo "$COMMIT_MSG" > "$RELEASE_NOTES_FILE"
        fi
    else
        echo "$COMMIT_MSG" > "$RELEASE_NOTES_FILE"
    fi
}

MODE=""
[ "$DO_DELETE" = true ] && MODE="删除" || MODE="创建"
ACTIONS=""
[ "$DO_TAG" = true ] && ACTIONS="tag"
[ "$DO_PACKAGE" = true ] && ACTIONS="$ACTIONS package"
[ "$DO_RELEASE" = true ] && ACTIONS="$ACTIONS release"

echo "=========================================="
echo "  版本号:   $VERSION"
echo "  模式:     $MODE"
echo "  提交信息: $COMMIT_MSG"
echo "  执行动作: $ACTIONS"
echo "  当前分支: $CURRENT_BRANCH"
echo "=========================================="

# ========== 删除操作 ==========

do_delete_tag() {
    echo ">>> [DELETE] 删除本地tag: $VERSION"
    git tag -d "$VERSION" || echo "本地tag不存在"

    echo ">>> [DELETE] 删除远程tag: $VERSION"
    git push origin ":refs/tags/$VERSION" || echo "远程tag不存在"

    echo ">>> [DELETE] tag $VERSION 已删除"
}

do_delete_release() {
    echo ">>> [DELETE] 删除GitHub Release: $VERSION"
    gh release delete "$VERSION" --yes || echo "Release不存在"

    echo ">>> [DELETE] Release $VERSION 已删除"
}

# ========== 创建操作 ==========

do_tag() {
    # 提交当前更改
    echo ">>> [TAG] 提交当前更改..."
    git add -A
    git commit -m "$COMMIT_MSG" || echo "没有需要提交的更改"

    # 打tag
    echo ">>> [TAG] 打tag: $VERSION"
    git tag -a "$VERSION" -m "$COMMIT_MSG"

    # 切换到master并合并
    echo ">>> [TAG] 切换到master分支..."
    git checkout master

    echo ">>> [TAG] 合并 $CURRENT_BRANCH 到 master..."
    git merge "$CURRENT_BRANCH"

    # 推送master和tag到远程
    echo ">>> [TAG] 推送master和tag到远程..."
    git push origin master
    git push origin "$VERSION"

    # 切回原分支
    echo ">>> [TAG] 切回 $CURRENT_BRANCH 分支..."
    git checkout "$CURRENT_BRANCH"

    echo ">>> [TAG] tag $VERSION 已创建并推送到远程"
}

# 生成README
generate_readme() {
    local target_dir="$1"
    local platform="$2"
    local build_time=$(date "+%Y-%m-%d %H:%M:%S")
    local java_version=$(java -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/')

    cat > "$target_dir/README.txt" << EOF
Zookeeper Visualizer
====================

版本号:   $VERSION
平台:     $platform
构建时间: $build_time
JDK版本:  $java_version
要求JDK:  1.8+

启动方式:
  macOS/Linux:  ./ZookeeperVisualizer.sh
  Windows:      ZookeeperVisualizer.bat

项目主页: https://github.com/zippoy/zookeeper-visualizer
EOF
}

# 打包 macOS .zip
package_macos() {
    echo ">>> [PACKAGE] 打包 macOS..."
    local macos_dir="$DIST_DIR/${APP_NAME}-${VERSION}-macos"
    mkdir -p "$macos_dir"

    cp "$JAR_PATH" "$macos_dir/"

    cat > "$macos_dir/${APP_NAME}.sh" << 'LAUNCH_EOF'
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
java -jar "$DIR/zookeeper-visualizer.jar"
LAUNCH_EOF
    chmod +x "$macos_dir/${APP_NAME}.sh"

    generate_readme "$macos_dir" "macOS"

    local zip_name="${APP_NAME}-${VERSION}-macos.zip"
    cd "$DIST_DIR"
    zip -r -q "$zip_name" "$(basename "$macos_dir")"
    cd - > /dev/null
    rm -rf "$macos_dir"

    echo ">>> [PACKAGE] macOS包已生成: $DIST_DIR/$zip_name"
}

# 打包 Windows .zip
package_windows() {
    echo ">>> [PACKAGE] 打包 Windows..."
    local win_dir="$DIST_DIR/${APP_NAME}-${VERSION}-windows"
    mkdir -p "$win_dir"

    cp "$JAR_PATH" "$win_dir/"

    cat > "$win_dir/${APP_NAME}.bat" << 'LAUNCH_EOF'
@echo off
java -jar "%~dp0zookeeper-visualizer.jar"
pause
LAUNCH_EOF

    generate_readme "$win_dir" "Windows"

    local zip_name="${APP_NAME}-${VERSION}-windows.zip"
    cd "$DIST_DIR"
    zip -r -q "$zip_name" "$(basename "$win_dir")"
    cd - > /dev/null
    rm -rf "$win_dir"

    echo ">>> [PACKAGE] Windows包已生成: $DIST_DIR/$zip_name"
}

# 打包 Linux .tar.gz
package_linux() {
    echo ">>> [PACKAGE] 打包 Linux..."
    local linux_dir="$DIST_DIR/${APP_NAME}-${VERSION}-linux"
    mkdir -p "$linux_dir"

    cp "$JAR_PATH" "$linux_dir/"

    cat > "$linux_dir/${APP_NAME}.sh" << 'LAUNCH_EOF'
#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
java -jar "$DIR/zookeeper-visualizer.jar"
LAUNCH_EOF
    chmod +x "$linux_dir/${APP_NAME}.sh"

    generate_readme "$linux_dir" "Linux"

    local tar_name="${APP_NAME}-${VERSION}-linux.tar.gz"
    cd "$DIST_DIR"
    tar -czf "$tar_name" "$(basename "$linux_dir")"
    cd - > /dev/null
    rm -rf "$linux_dir"

    echo ">>> [PACKAGE] Linux包已生成: $DIST_DIR/$tar_name"
}

do_package() {
    echo ">>> [PACKAGE] Maven打包..."
    mvn clean package -DskipTests

    if [ ! -f "$JAR_PATH" ]; then
        echo "错误: 打包失败，未找到 $JAR_PATH"
        exit 1
    fi
    echo ">>> [PACKAGE] JAR打包成功: $JAR_PATH"

    rm -rf "$DIST_DIR"
    mkdir -p "$DIST_DIR"

    package_macos
    package_windows
    package_linux

    echo ">>> [PACKAGE] 产物列表:"
    for f in "$DIST_DIR"/*.zip "$DIST_DIR"/*.tar.gz; do
        if [ -f "$f" ]; then
            echo "  $(basename "$f") ($(du -h "$f" | cut -f1))"
        fi
    done
}

do_release() {
    generate_release_notes

    local artifacts=()
    for f in "$DIST_DIR"/*.zip "$DIST_DIR"/*.tar.gz; do
        if [ -f "$f" ]; then
            artifacts+=("$f")
        fi
    done

    if [ ${#artifacts[@]} -eq 0 ]; then
        echo "错误: 未找到打包产物，请先执行 -p 打包"
        exit 1
    fi

    echo ">>> [RELEASE] 创建GitHub Release并上传..."
    echo ">>> [RELEASE] 说明内容:"
    cat "$RELEASE_NOTES_FILE"
    gh release create "$VERSION" "${artifacts[@]}" \
        --title "$VERSION" \
        --notes-file "$RELEASE_NOTES_FILE"

    echo ">>> [RELEASE] Release $VERSION 已发布，所有平台包已上传"
}

# ========== 执行 ==========

if [ "$DO_DELETE" = true ]; then
    if [ "$DO_TAG" = true ]; then
        do_delete_tag
    fi
    if [ "$DO_RELEASE" = true ]; then
        do_delete_release
    fi
else
    if [ "$DO_TAG" = true ]; then
        do_tag
    fi
    if [ "$DO_PACKAGE" = true ]; then
        do_package
    fi
    if [ "$DO_RELEASE" = true ]; then
        do_release
    fi
fi

echo ">>> 完成!"

