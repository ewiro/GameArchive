<div align="center">
  <a href="README.md">
    <img src="https://img.shields.io/badge/中文-Active-red?style=for-the-badge" alt="Chinese">
  </a>
  <a href="README_EN.md">
    <img src="https://img.shields.io/badge/English-Click_Here-grey?style=for-the-badge" alt="English">
  </a>
</div>

<br>
# Game Archive 

> 一个基于 Material Design 3 设计的现代化 Steam 库存管理与特惠查询工具。

![Platform](https://img.shields.io/badge/Platform-Android-green.svg)
![Language](https://img.shields.io/badge/Language-Kotlin-orange.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)


---

##  截图预览 

| 登录与设置 | 个人库存  | 特惠查询  | 沉浸式详情  | 设置界面 | 排序界面 |
|:---:|:---:|:---:|:---:|:---:|:---:|
| <img src="screenshots/login.jpg" width="200"/> | <img src="screenshots/library.jpg" width="200"/> | <img src="screenshots/specials.jpg" width="200"/> | <img src="screenshots/detail.jpg" width="200"/> |<img src="screenshots/setting.jpg" width="200"/> |<img src="screenshots/sequence.jpg" width="200"/> |



---

##  核心功能 

###  个人库存
*   **视觉**：完全适配 **Material You**，UI 颜色随壁纸动态变化。支持深色/纯黑模式。
*   **统计**：顶部展示个人头像、等级、游戏总数及总时长。
*   **卡片**：列表卡片颜色根据游玩时长动态分级（由浅入深），直观展示你的“肝度”。

###  特惠查询
*   **体验**：内置过滤，自动剔除 DLC、原声带、季票等干扰项，只展示**游戏本体**。
*   **数据**：采用并发抓取技术，一次性加载数百个打折游戏。
*   **排序**：支持按 **好评率**、**现价**、**折扣力度**、**销量** 进行多维度排序。
*   **好评**：列表直接展示好评率（如 "95% 好评"），并以不同颜色区分评价等级。

###  沉浸式详情 
*   **画廊**：横向滑动的 16:9 自适应画廊，无缝拼接视频与截图，无黑边体验。
*   **评论**：直接集成 Steam 玩家评论区，支持查看长评，拒绝截断。
*   **排版**：通过 JS/CSS 注入技术，重排 Steam 杂乱的简介 HTML，去除多余间距，实现优美的图文混排。
*   **封面**：自动修复不规范的活动宣传图，优先展示 Steam 标准封面。

###  个性化
*   支持自定义个人资料背景图、头像及**头像挂件**。
*   支持自定义 Cloudflare Worker 代理地址。


---

##  隐私与安全

我们深知 Steam 账号安全的重要性，因此：

1.  **本地存储**：所有的 Steam ID 和 Web API Key 仅保存在您手机的本地加密存储 (`SharedPreferences`) 中。
2.  **零收集**：本项目**没有**任何后端服务器，不会收集、上传您的任何个人信息。
3.  **开源透明**：所有代码（包括代理服务器脚本）均完全开源，您可以随时审计。

---

##  部署与使用

### 1. 下载安装
前往 [Releases 页面](https://github.com/你的用户名/GameArchive/releases) 下载最新版本的 APK。
*   现代手机推荐下载 `arm64` 版本。

### 2. 获取必要信息
首次使用需要输入您的 Steam 信息：
*   **Steam ID**: 您的 64 位数字 ID（可在个人资料链接中找到）。
*   **Web API Key**: 前往 [Steam 开发者页面](https://steamcommunity.com/dev/apikey) 免费申请（域名可随意填写）。

### 3. (进阶) 自建代理服务
为了保证在中国大陆地区的稳定访问，App 默认使用内置的 Cloudflare Worker 代理。
为了数据绝对安全和更快的速度，**强烈建议您部署自己的 Worker**。

 **[点击查看：自建 Cloudflare Worker 教程](WORKER_SETUP.md)**

---

##  技术栈
本项目采用现代 Android 开发标准构建：

*   **语言**: [Kotlin](https://kotlinlang.org/)
*   **架构**: MVVM (Fragment + ViewPager2)
*   **网络**: [Retrofit](https://square.github.io/retrofit/) + [OkHttp](https://square.github.io/okhttp/) + [GSON](https://github.com/google/gson)
*   **图片加载**: [Coil](https://coil-kt.github.io/coil/) (针对 GIF 动图及内存缓存优化)
*   **UI 组件**: Material Design 3, ConstraintLayout, CoordinatorLayout
*   **异步处理**: Kotlin Coroutines (Async/Await 并发请求)
*   **后端/代理**
  
---

##  许可证

本项目基于 MIT 许可证开源 - 详见 [LICENSE](LICENSE) 文件。
