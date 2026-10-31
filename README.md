# NoShort-RedNote

网页版小红书，调整到适合手机的比例来使用。

- **Android**：一个轻量 App，打开小红书网页版（xiaohongshu.com），按手机屏幕比例显示。
- **iPhone / iPad**：在 Safari 里用 Userscripts 扩展加载同一套脚本。

## 功能

- 首页瀑布流、搜索、笔记详情（图文、视频）、评论
- 扫码登录、手机号验证码登录；登录状态在重启和更新后都会保留
- 全面屏手势返回，内容不会被状态栏和手势条挡住
- 应用内更新

### 设置

**长按页面右上角约 1 秒**打开设置：

| 开关 | 作用 |
|---|---|
| 只留搜索 | 首页只显示搜索 |
| 只看图文 | 推荐流只显示图文笔记 |
| 隐藏猜你想搜 | 笔记正文下方不显示搜索推荐 |
| 页面内调试工具 | 加载 [eruda](https://github.com/liriliri/eruda)，在手机上查看网页结构（仅 Android） |

## 安装

### Android

在 [Releases](../../releases/latest) 下载 `xhs-lite.apk`，允许「安装未知来源应用」后安装。需要 Android 10 及以上。

之后 App 启动时会检查新版本，有更新会提示，点一下就能安装；设置里也可以手动「检查更新」。

### iPhone / iPad（Safari）

1. 在 App Store 安装 [Userscripts](https://apps.apple.com/app/userscripts/id1463298887)。
2. 打开 **设置 → App → Safari → 扩展 → Userscripts**，启用它，并允许它在 `xiaohongshu.com` 上运行。
3. 用 Safari 打开 [`noshort-rednote.user.js`](https://github.com/Cohenjikan/NoShort-RedNote/releases/latest/download/noshort-rednote.user.js)，点地址栏的扩展按钮 → Userscripts → 安装。之后会自动更新。
4. 打开 `https://www.xiaohongshu.com/explore`，在地址栏菜单里选择 **请求桌面网站**。
5. 如果要添加到主屏幕，记得关掉「作为网页 App 打开」，否则扩展不会运行。

## 开发

```bash
./gradlew assembleRelease
```

Kotlin，单 Activity + WebView。页面适配相关的代码都在 [`inject.js`](app/src/main/assets/inject.js) 和 [`inject.css`](app/src/main/assets/inject.css) 两个文件里，Android 和 iPhone 共用。`tools/probe` 可以在电脑上模拟手机比例来预览效果。

推送到 `main` 后，GitHub Actions 会构建 APK 和 iPhone 脚本，并发布到 Releases。

## 许可证

[MIT](LICENSE)。内置的 eruda 同样是 MIT 许可证。

## 关于代码

本项目的代码由 [Claude](https://claude.ai)（Anthropic 的 AI）编写，需求和真机测试由 [@Cohenjikan](https://github.com/Cohenjikan) 完成。
