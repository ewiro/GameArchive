# ☁️ 自建 Cloudflare Worker 教程

为了保证 **Game Archive** 能够在中国大陆地区正常访问 Steam 数据（如库存、特惠、愿望单），App 需要经过一个代理服务器中转。

为了数据安全和稳定性，建议您免费部署一个属于自己的 Cloudflare Worker。

## 步骤 1：注册/登录 Cloudflare
访问 [Cloudflare 官网](https://dash.cloudflare.com/) 并登录账号。

## 步骤 2：创建 Worker
1. 在左侧菜单栏点击 **Workers & Pages**。
2. 点击 **Create Application** -> **Create Worker**。
3. 随便起个名字（例如 `steam-proxy`），点击 **Deploy**。

## 步骤 3：部署代码
1. 点击 **Edit code** 进入代码编辑器。
2. 删除编辑器里现有的所有代码。
3. 复制本项目根目录下的 `cloudflare_worker.js` 文件中的全部代码。
4. 粘贴到 Cloudflare 编辑器中。
5. 点击右上角的 **Deploy** (Save and Deploy)。

## 步骤 4：获取链接
1. 部署成功后，你会获得一个类似 `https://steam-proxy.xxxx.workers.dev` 的链接。
2. **注意**：`workers.dev` 域名在国内通常是被墙的，建议直接买个域名，具体操作请自行百度，不在此做过多介绍。
3. 强烈建议在 Cloudflare 后台为这个 Worker 绑定一个你自己的**自定义域名** (Custom Domain)，或者使用支持优选 IP 的域名。

## 步骤 5：在 App 中配置
*(此功能需自行修改源码中的 `BASE_URL` 或等待 App 后续更新支持自定义代理设置)*

目前版本请确保您的网络环境可以访问 Worker 域名，或者在编译前修改 `SteamApi.kt` 中的 `BASE_URL`。