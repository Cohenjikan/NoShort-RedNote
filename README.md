# NoShort-RedNote · 小红书·轻

**没有无限刷视频的小红书。** 一个 Android 小 App：用 WebView 打开小红书网页版，只保留 **首页瀑布流、搜索、笔记详情**，去掉让人停不下来的上下滑刷视频。

不逆向小红书 App，不碰请求签名，不抓接口，只在网页层面隐藏和拦截。

## 为什么

小红书 App 的视频页可以上下滑一条接一条地刷，很难停下来。网页版是按需打开的：点一条看一条，看完关掉回到瀑布流。这个 App 把网页版装进一个干净的壳，平时找攻略、搜东西照常用，只是没有那条刷不完的视频流。

## 功能

- 打开就是 `xiaohongshu.com/explore`，2 列瀑布流
- 搜索（历史记录、联想、2 列结果）、看笔记详情（图文、视频）、评论
- 支持扫码登录和手机号验证码登录；登录状态重启 App 后保留，更新 App 也不丢
- 去掉的入口：底部导航的「直播」「点点(AI)」「发布」，各种引导气泡；`/livelist` 直接跳回首页
- 防止切到下一条：笔记里没有上一条/下一条按钮；万一出现「没点卡片却从一条笔记直接跳到另一条」（比如自动连播），会撤回到当前这条
- 「打开 App」「下载 App」之类的唤起链接和应用商店链接一律忽略，其他外部链接用系统浏览器打开
- 全面屏手势返回：网页能后退就后退，不能就退出；内容不会被状态栏和底部手势条挡住

### 隐藏设置

**长按页面右上角约 1 秒**打开设置（默认全关）：

| 开关 | 作用 |
|---|---|
| 只留搜索 | 首页不显示频道和推荐流，只剩搜索 |
| 隐藏猜你想搜 | 笔记正文下面的搜索推荐 |
| 页面内调试工具 | 注入 [eruda](https://github.com/liriliri/eruda)（网页版开发者工具），不用开开发者模式就能在手机上看页面 |

## 安装

在 [Releases](../../releases/latest) 下载 `xhs-lite.apk`，允许「安装未知来源应用」后安装。不需要开开发者模式，也不需要 adb。

之后不用再手动下载：App 启动时会检查 GitHub 上的最新版本（最多每 6 小时一次），有新版会提示，点一下就下载并调起系统安装；设置里也可以手动「检查更新」。第一次更新时系统会让你允许本应用安装应用。

每个版本都用同一把签名密钥，新版直接覆盖安装，登录状态不丢。

要求 Android 10+。开发时用的是小米 17 Pro Max（HyperOS），布局按 400dp 宽的屏幕调过，其他手机应该也能用。

## 原理

- WebView 用**最新版 Chrome 桌面 UA**（包括 Client Hints），因为小红书的移动版网页功能少、到处弹「打开 App」。
- 再把视口设成手机宽度。这时桌面版网页会自己切成窄屏布局：2 列瀑布流、顶部搜索、底部导航、单列笔记页。所以注入层几乎不改排版，主要是隐藏东西。
- 注入在页面开始加载、加载完成和 SPA 路由变化（`pushState` / `replaceState` / `popstate` + `MutationObserver`）时都会执行，可以重复执行。

实测（2026-11，登录状态，400×869）：手机宽度下，图文、视频、RED 视频频道的笔记都没有上一条/下一条控件，上下滑、滚轮、方向键都不会切到别的笔记，视频播完是循环播放。

## 小红书改版后怎么修

所有选择器只在两个文件里：

- [`app/src/main/assets/inject.js`](app/src/main/assets/inject.js)：`SEL` 和路由规则
- [`app/src/main/assets/inject.css`](app/src/main/assets/inject.css)：隐藏规则

`tools/probe` 在电脑上模拟这个 App（400×869、桌面 UA、同一套注入），截图并导出 DOM 骨架，用来对照新页面：

```bash
cd tools/probe
npm install
node probe.mjs login                                   # 弹出窗口，用手机扫码登录一次
node probe.mjs shot https://www.xiaohongshu.com/explore home --open-note
```

在手机上可以打开设置里的调试工具，在 eruda 的 Console 里运行 `__xl.probe()`，看每个选择器命中了几个元素。

## 构建

```bash
./gradlew assembleRelease
```

Kotlin，单 Activity + WebView，依赖只有 `androidx.core`、`androidx.activity`、`androidx.webkit`。更新器（`Updater.kt`）直接请求 GitHub Releases API，不用第三方库。

推送到 `main` 后，GitHub Actions（`.github/workflows/release.yml`）会构建签名 APK 并发布到 Releases。需要配置这几个仓库 Secrets：`XHS_KEYSTORE_BASE64`、`XHS_KEYSTORE_PASSWORD`、`XHS_KEY_ALIAS`、`XHS_KEY_PASSWORD`。没有配置密钥时，本地构建会退回用 debug 密钥签名。

## 声明

- 这是个人项目，和小红书（行吟信息科技）没有任何关系。内容都来自小红书官方网页，App 只是个浏览器壳，不收集、不上传任何数据。
- 本项目以 [MIT 许可证](LICENSE) 开源；内置的 eruda 同样是 MIT。

## 关于代码

这个项目的代码全部由 [Claude](https://claude.ai)（Anthropic 的 AI）编写。需求、取舍和真机测试来自 [@Cohenjikan](https://github.com/Cohenjikan)。
