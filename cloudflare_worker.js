// cloudflare_worker.js - Game Archive Proxy Script
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // --- 1. 智能路由逻辑 ---
    // 涉及商店页面的请求转发到 store.steampowered.com
    if (url.pathname.includes('/appdetails') ||
        url.pathname.includes('/appreviews') ||
        url.pathname.includes('/wishlist') ||
        url.pathname.includes('/api/storesearch') ||
        url.pathname.includes('/search/results')) {

      url.hostname = "store.steampowered.com";
      // 强制国区中文
      url.searchParams.set("cc", "cn");
      url.searchParams.set("l", "schinese");
    } else {
      // 其他请求 (如 IPlayerService) 转发到 api.steampowered.com
      url.hostname = "api.steampowered.com";
    }

    // --- 2. 伪装 Header (核心反爬虫对抗) ---
    const newHeaders = new Headers(request.headers);
    newHeaders.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
    newHeaders.set("Referer", "https://store.steampowered.com/");
    newHeaders.set("X-Requested-With", "XMLHttpRequest");

    // 注入 Cookie 绕过年龄验证和跳转
    newHeaders.set("Cookie", "birthtime=0; lastagecheckage=1-0-1990; wants_mature_content=1; steamCountry=CN%7Cb8a8a3da46a6c324d507194661729399;");

    newHeaders.delete("Host");

    const newRequest = new Request(url, {
        method: request.method,
        headers: newHeaders,
        body: request.body,
        redirect: 'follow'
    });

    const response = await fetch(newRequest);

    // --- 3. 处理 CORS (允许跨域) ---
    const newResponse = new Response(response.body, response);
    newResponse.headers.set("Access-Control-Allow-Origin", "*");
    newResponse.headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    newResponse.headers.set("Access-Control-Allow-Headers", "*");

    return newResponse;
  },
};