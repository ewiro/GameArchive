<div align="center">

<a href="README.md">
  <img src="https://img.shields.io/badge/中文-简体-1976D2?logoColor=white" alt="中文" />
</a>
<a href="README_EN.md">
  <img src="https://img.shields.io/badge/English-EN-546E7A?logoColor=white" alt="English" />
</a>

</div>
<br>

# Game Archive 

> 基于 MIUI X 主题的现代化 Steam 库存管理与特惠查询工具，由 Compose 构建。

![Platform](https://img.shields.io/badge/Platform-Android-green.svg)
![Language](https://img.shields.io/badge/Language-Kotlin-orange.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)


---

##  截图预览 

| 登录 | 游戏页 | 游戏详情 | 特惠页 | 排序 | 动漫页 | 动漫详情 | 记录页 | 设置 |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| <img src="screenshots/login.jpg" width="200"/> | <img src="screenshots/game.jpg" width="200"/> | <img src="screenshots/detail_game.jpg" width="200"/> | <img src="screenshots/specials.jpg" width="200"/> |<img src="screenshots/sequence.jpg" width="200"/> |<img src="screenshots/anime.jpg" width="200"/> |<img src="screenshots/detail_anime.jpg" width="200"/> |<img src="screenshots/activity.jpg" width="200"/> | <img src="screenshots/setting.jpg" width="200"/> |



---

##  核心功能 

###  个人库存
*   **视觉**：基于 **MIUI X** 主题，支持深色/浅色/跟随系统。
*   **统计**：顶部展示个人头像、等级、游戏总数及总时长。
*   **卡片**：列表卡片颜色根据游玩时长动态分级（由浅入深），直观展示你的”肝度”。
*   **标记**：支持 8 种游玩状态标记，每种状态独特色系，一眼分辨。

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
*   支持自定义个人资料背景图、头像及**头像挂件**（支持 GIF 动图）。
*   库存支持分组显示（近期活跃 / 已玩 / 堆积库存）与多种排序方式。
*   主题支持浅色 / 深色 / 跟随系统，状态栏颜色自动适配。
*   **自定义标签**：为游戏添加个性化标签，支持增删改查。


---

##  隐私与安全

我们深知 Steam 账号安全的重要性，因此：

1.  **本地保存**：Steam ID、Web API Key 和 Bangumi OAuth Token 保存在应用私有的本地存储中，不会写入软件的手动备份文件。
2.  **Steam 凭证边界**：应用不会获取或保存 Steam 密码、Steam Guard 验证码和登录 Cookie。Web API Key 不是 Steam 登录凭证，但仍属于需要保密的敏感信息。
3.  **代理说明**：为保证中国大陆地区的可用性，Steam API 与 Bangumi API 请求会经过默认的 Cloudflare Worker。代理不建立用户数据库，也不主动持久化凭证，但在转发时能够接触 Steam Web API Key 和 Bangumi Access Token。默认代理的 Cloudflare 账号已启用通行密钥和 2FA。
4.  **Bangumi 授权边界**：Bangumi Access Token 可用于读取和修改授权范围内的收藏与章节数据；如怀疑代理或设备失守，请及时撤销授权。
5.  **开源透明**：客户端及代理脚本均开源，可自行审计或部署独立 Worker。

---

##  部署与使用

### 1. 下载安装
前往 [Releases 页面](https://github.com/ewiro/GameArchive/releases) 下载最新版本的 APK。
*   现代手机推荐下载 `arm64` 版本。

### 2. 获取必要信息
首次使用需要输入您的 Steam 信息：
*   **ID**: 您的数字ID，非账户名（可在个人资料链接中找到）。
*   **API**: 前往 [Steam 开发者页面](https://steamcommunity.com/dev/apikey) 免费申请（**域名可随意填写**）。

### 3. (进阶) 自建代理服务
为了保证在中国大陆地区的稳定访问，App 默认使用内置的 Cloudflare Worker 代理（地址统一配置于 `AppConfig.kt` 中的 `PROXY_URL` 常量）。
如果希望减少对默认公共代理的信任，或需要独立控制日志和部署权限，可以自行部署 Worker：
1. 在 [Cloudflare Workers](https://workers.cloudflare.com/) 创建新 Worker
2. 将项目根目录的 `cloudflare_worker.js` 内容复制进去
3. 将 `AppConfig.kt` 中的 `PROXY_URL` 改为你的 Worker 地址
4. 重新编译即可

---

##  技术栈
本项目采用现代 Android 开发标准构建：

*   **语言**: [Kotlin](https://kotlinlang.org/)
*   **架构**: MVVM (Compose + HorizontalPager + ViewModel + LiveData)
*   **网络**: [Retrofit](https://square.github.io/retrofit/) + [OkHttp](https://square.github.io/okhttp/) + [GSON](https://github.com/google/gson)
*   **图片加载**: [Coil](https://coil-kt.github.io/coil/) (针对 GIF 动图及内存缓存优化)
*   **UI 组件**: Compose Multiplatform + MIUI X (miuix-ui 0.9.2)
*   **异步处理**: Kotlin Coroutines (Async/Await 并发请求)
*   **后端/代理**: Cloudflare Worker (反爬虫伪装 + CORS 跨域处理)

---

##  许可证

本项目基于 MIT 许可证开源 - 详见 [LICENSE](LICENSE) 文件。
