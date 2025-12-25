# GameArchive 🎮

一个基于 Material Design 3 的 Steam 库存与特惠查询工具。

## ✨ 功能特点
- **沉浸式体验**：无缝拼接的游戏详情页，自适应画廊。
- **库存管理**：查看游戏时长、分类统计。
- **特惠查询**：支持按好评率、折扣、现价排序，一键过滤已拥有游戏。
- **隐私安全**：支持 Cloudflare Worker 代理，无需本地存储敏感 Key。

## 📸 截图 (Screenshots)

<table>
  <tr>
    <td><img src="screenshots/login.jpg" width="200"/></td>
    <td><img src="screenshots/library.jpg" width="200"/></td>
    <td><img src="screenshots/setting.jpg" width="200"/></td>
    <td><img src="screenshots/specials.jpg" width="200"/></td>
    <td><img src="screenshots/detail.jpg" width="200"/></td>
    <td><img src="screenshots/sequence.jpg" width="200"/></td>
  </tr>
</table>

## 🛠️ 技术栈
- Kotlin
- MVVM (Fragment + ViewPager2)
- Retrofit + OkHttp
- Coil (图片加载)
- Material Components

## 📄 许可证
MIT License

## 🔒 隐私安全说明
1. 数据本地化：您的 Steam ID 和 API Key 仅保存在您手机的本地存储中。本应用不设后端服务器，不会收集您的任何个人信息。
2. 代理透明化：本应用通过 Cloudflare Worker 进行网络加速。代理脚本已在仓库中公开（cloudflare_worker.js），您可以检查其逻辑，确保数据仅用于转发至 Steam 官方接口。
3. 自建建议：如果您对默认代理不放心，本项目强烈建议您参考文档[自建 Cloudflare Worker]，在设置中填入您自己的代理域名，实现完全的数据自主掌控。
