export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    
    // 1. 智能路由
    if (url.pathname.startsWith('/bangumi/')) {
      // Bangumi API 代理
      url.hostname = "api.bgm.tv";
      url.pathname = url.pathname.replace('/bangumi', '');
    } else if (url.pathname.includes('/appdetails') || 
        url.pathname.includes('/appreviews') ||
        url.pathname.includes('/wishlist') ||           
        url.pathname.includes('/api/storesearch') ||
        url.pathname.includes('/search/results')) {
      
      url.hostname = "store.steampowered.com";
      if (!url.searchParams.has("l")) {
        url.searchParams.set("l", "schinese");
      }
      
      url.searchParams.set("cc", "cn");  
    } else {
      url.hostname = "api.steampowered.com";
    }

    // 2. 伪装头 — 显式保留原始 Authorization 并追加伪装头
    const newHeaders = new Headers();
    for (const [k, v] of request.headers) {
        newHeaders.set(k, v);
    }
    newHeaders.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
    newHeaders.set("Referer", "https://store.steampowered.com/");
    newHeaders.set("X-Requested-With", "XMLHttpRequest");
    
    // Steam 相关请求注入 Cookie
    if (url.hostname.includes("steampowered")) {
      newHeaders.set("Cookie", "birthtime=0; lastagecheckage=1-0-1990; wants_mature_content=1; steamCountry=CN%7Cb8a8a3da46a6c324d507194661729399;");
    }
    
    newHeaders.delete("Host");

    const newRequest = new Request(url, {
        method: request.method,
        headers: newHeaders,
        body: request.body,
        redirect: 'follow'
    });

    const response = await fetch(newRequest);

    // 3. 允许跨域
    const newResponse = new Response(response.body, response);
    newResponse.headers.set("Access-Control-Allow-Origin", "*");
    newResponse.headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    
    return newResponse;
  },
};
