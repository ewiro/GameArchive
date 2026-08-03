export default {
  async fetch(request, env, ctx) {
    const incomingUrl = new URL(request.url);
    let url = new URL(incomingUrl);
    let publicSteamRoute = false;
    let bangumiOAuthRoute = false;
    
    // 1. 智能路由
    if (url.pathname.startsWith('/community/miniprofile/')) {
      if (request.method !== 'GET') {
        return methodNotAllowed('GET');
      }
      const match = url.pathname.match(/^\/community\/miniprofile\/(\d+)\/json\/?$/);
      if (!match) {
        return new Response('Invalid Steam account ID', { status: 400 });
      }
      url.hostname = 'steamcommunity.com';
      url.pathname = `/miniprofile/${match[1]}/json/`;
      url.search = '';
      publicSteamRoute = true;
    } else if (url.pathname === '/steam-media') {
      if (request.method !== 'GET' && request.method !== 'HEAD') {
        return methodNotAllowed('GET, HEAD');
      }
      const mediaUrl = incomingUrl.searchParams.get('url');
      if (!mediaUrl) {
        return new Response('Missing media URL', { status: 400 });
      }
      let parsedMediaUrl;
      try {
        parsedMediaUrl = new URL(mediaUrl);
      } catch {
        return new Response('Invalid media URL', { status: 400 });
      }
      if (!isAllowedSteamMediaUrl(parsedMediaUrl)) {
        return new Response('Steam media host is not allowed', { status: 403 });
      }
      url = parsedMediaUrl;
      publicSteamRoute = true;
    } else if (url.pathname === '/bangumi-oauth/access_token') {
      if (request.method !== 'POST') {
        return methodNotAllowed('POST');
      }
      url.hostname = 'bgm.tv';
      url.pathname = '/oauth/access_token';
      url.search = '';
      bangumiOAuthRoute = true;
    } else if (url.pathname.startsWith('/bangumi/')) {
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

    // 2. 伪装头。公开 Steam 装扮路由不转发任何账号凭证。
    const newHeaders = new Headers();
    if (!publicSteamRoute) {
      for (const [k, v] of request.headers) {
          newHeaders.set(k, v);
      }
    } else {
      const range = request.headers.get('Range');
      if (range) newHeaders.set('Range', range);
      const accept = request.headers.get('Accept');
      if (accept) newHeaders.set('Accept', accept);
    }
    newHeaders.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
    newHeaders.set(
      "Referer",
      publicSteamRoute
        ? "https://steamcommunity.com/"
        : bangumiOAuthRoute
          ? "https://bgm.tv/"
          : "https://store.steampowered.com/"
    );
    newHeaders.set("X-Requested-With", "XMLHttpRequest");
    
    // Steam 相关请求注入 Cookie
    if (url.hostname.includes("steampowered")) {
      newHeaders.set("Cookie", "birthtime=0; lastagecheckage=1-0-1990; wants_mature_content=1; steamCountry=CN%7Cb8a8a3da46a6c324d507194661729399;");
    }
    
    newHeaders.delete("Host");

    const newRequest = new Request(url, {
        method: request.method,
        headers: newHeaders,
        body: request.method === 'GET' || request.method === 'HEAD' ? undefined : request.body,
        redirect: publicSteamRoute ? 'manual' : 'follow'
    });

    const response = publicSteamRoute
      ? await fetchSteamPublicResource(newRequest)
      : await fetch(newRequest);

    // 3. 允许跨域
    const newResponse = new Response(response.body, response);
    newResponse.headers.set("Access-Control-Allow-Origin", "*");
    newResponse.headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    if (bangumiOAuthRoute) {
      newResponse.headers.set("Cache-Control", "no-store");
      newResponse.headers.set("Pragma", "no-cache");
    }
    
    return newResponse;
  },
};

function isAllowedSteamMediaUrl(url) {
  const host = url.hostname.toLowerCase();
  return url.protocol === 'https:' &&
    (host === 'steamstatic.com' || host.endsWith('.steamstatic.com'));
}

async function fetchSteamPublicResource(initialRequest) {
  let request = initialRequest;
  for (let redirectCount = 0; redirectCount <= 3; redirectCount++) {
    const response = await fetch(request);
    if (response.status < 300 || response.status >= 400) {
      return response;
    }
    const location = response.headers.get('Location');
    if (!location) return response;
    const nextUrl = new URL(location, request.url);
    const isCommunity = nextUrl.protocol === 'https:' &&
      (nextUrl.hostname === 'steamcommunity.com' ||
        nextUrl.hostname.endsWith('.steamcommunity.com'));
    if (!isCommunity && !isAllowedSteamMediaUrl(nextUrl)) {
      return new Response('Steam redirect is not allowed', { status: 403 });
    }
    request = new Request(nextUrl, {
      method: initialRequest.method,
      headers: initialRequest.headers,
      redirect: 'manual'
    });
  }
  return new Response('Too many Steam redirects', { status: 508 });
}

function methodNotAllowed(allow) {
  return new Response('Method not allowed', {
    status: 405,
    headers: { Allow: allow }
  });
}
