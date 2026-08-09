const CORS_METHODS = 'GET, HEAD, POST, PATCH, OPTIONS';
const CORS_HEADERS = 'Authorization, Content-Type, Range, X-Requested-With';

export default {
  async fetch(request) {
    if (request.method === 'OPTIONS') {
      return withCors(new Response(null, { status: 204 }));
    }

    const incomingUrl = new URL(request.url);
    let upstreamUrl = new URL(incomingUrl);
    let route = 'steam-api';

    const miniProfileMatch = incomingUrl.pathname.match(
      /^\/community\/miniprofile\/(\d+)\/json\/?$/
    );
    if (miniProfileMatch) {
      if (request.method !== 'GET') return methodNotAllowed('GET');
      upstreamUrl.hostname = 'steamcommunity.com';
      upstreamUrl.pathname = `/miniprofile/${miniProfileMatch[1]}/json/`;
      upstreamUrl.search = '';
      route = 'steam-public';
    } else if (incomingUrl.pathname.startsWith('/community/miniprofile/')) {
      return withCors(new Response('Invalid Steam account ID', { status: 400 }));
    } else if (incomingUrl.pathname === '/steam-media') {
      if (!['GET', 'HEAD'].includes(request.method)) return methodNotAllowed('GET, HEAD');
      const mediaUrl = incomingUrl.searchParams.get('url');
      if (!mediaUrl) return withCors(new Response('Missing media URL', { status: 400 }));
      try {
        upstreamUrl = new URL(mediaUrl);
      } catch {
        return withCors(new Response('Invalid media URL', { status: 400 }));
      }
      if (!isAllowedSteamMediaUrl(upstreamUrl)) {
        return withCors(new Response('Steam media host is not allowed', { status: 403 }));
      }
      stripPublicCredentials(upstreamUrl);
      route = 'steam-public';
    } else if (incomingUrl.pathname === '/bangumi-oauth/access_token') {
      if (request.method !== 'POST') return methodNotAllowed('POST', true);
      upstreamUrl.hostname = 'bgm.tv';
      upstreamUrl.pathname = '/oauth/access_token';
      upstreamUrl.search = '';
      route = 'bangumi-oauth';
    } else if (incomingUrl.pathname.startsWith('/bangumi/')) {
      if (!['GET', 'POST', 'PATCH'].includes(request.method)) {
        return methodNotAllowed('GET, POST, PATCH');
      }
      upstreamUrl.hostname = 'api.bgm.tv';
      upstreamUrl.pathname = incomingUrl.pathname.slice('/bangumi'.length);
      route = 'bangumi-api';
    } else if (isSteamStoreRoute(incomingUrl.pathname)) {
      if (!['GET', 'HEAD'].includes(request.method)) return methodNotAllowed('GET, HEAD');
      upstreamUrl.hostname = 'store.steampowered.com';
      if (!upstreamUrl.searchParams.has('l')) upstreamUrl.searchParams.set('l', 'schinese');
      upstreamUrl.searchParams.set('cc', 'cn');
      route = 'steam-store';
    } else {
      if (!['GET', 'HEAD'].includes(request.method)) return methodNotAllowed('GET, HEAD');
      upstreamUrl.hostname = 'api.steampowered.com';
    }

    const headers = buildUpstreamHeaders(request, route);
    const upstreamRequest = new Request(upstreamUrl, {
      method: request.method,
      headers,
      body: ['GET', 'HEAD'].includes(request.method) ? undefined : request.body,
      redirect: route === 'steam-public' ? 'manual' : 'follow',
    });
    const response = route === 'steam-public'
      ? await fetchSteamPublicResource(upstreamRequest)
      : await fetch(upstreamRequest);
    return withCors(response, route === 'bangumi-oauth');
  },
};

function isSteamStoreRoute(pathname) {
  return /^\/api\/appdetails\/?$/.test(pathname) ||
    /^\/appreviews\/\d+\/?$/.test(pathname) ||
    /^\/api\/storesearch\/?$/.test(pathname) ||
    /^\/search\/results\/?$/.test(pathname) ||
    /^\/wishlist(?:\/[^/]+)*\/?$/.test(pathname);
}

function buildUpstreamHeaders(request, route) {
  const headers = new Headers();
  if (route === 'steam-public') {
    for (const name of ['Accept', 'Accept-Encoding', 'Range']) {
      const value = request.headers.get(name);
      if (value) headers.set(name, value);
    }
  } else {
    for (const [name, value] of request.headers) headers.set(name, value);
  }
  headers.delete('Host');
  headers.delete('Content-Length');
  headers.set(
    'User-Agent',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36'
  );
  headers.set('X-Requested-With', 'XMLHttpRequest');
  headers.set(
    'Referer',
    route === 'bangumi-oauth' || route === 'bangumi-api'
      ? 'https://bgm.tv/'
      : route === 'steam-public'
        ? 'https://steamcommunity.com/'
        : 'https://store.steampowered.com/'
  );
  if (route === 'steam-store') {
    headers.set(
      'Cookie',
      'birthtime=0; lastagecheckage=1-0-1990; wants_mature_content=1; steamCountry=CN%7Cb8a8a3da46a6c324d507194661729399;'
    );
  }
  return headers;
}

function isAllowedSteamMediaUrl(url) {
  const host = url.hostname.toLowerCase();
  return url.protocol === 'https:' &&
    (host === 'steamstatic.com' || host.endsWith('.steamstatic.com'));
}

function stripPublicCredentials(url) {
  for (const name of [
    'key',
    'api_key',
    'access_token',
    'client_secret',
    'code',
    'refresh_token',
  ]) {
    url.searchParams.delete(name);
  }
}

async function fetchSteamPublicResource(initialRequest) {
  let request = initialRequest;
  for (let redirectCount = 0; redirectCount <= 3; redirectCount++) {
    const response = await fetch(request);
    if (response.status < 300 || response.status >= 400) return response;
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
      redirect: 'manual',
    });
  }
  return new Response('Too many Steam redirects', { status: 508 });
}

function withCors(response, noStore = false) {
  const result = new Response(response.body, response);
  result.headers.set('Access-Control-Allow-Origin', '*');
  result.headers.set('Access-Control-Allow-Methods', CORS_METHODS);
  result.headers.set('Access-Control-Allow-Headers', CORS_HEADERS);
  result.headers.append('Vary', 'Origin');
  if (noStore) {
    result.headers.set('Cache-Control', 'no-store');
    result.headers.set('Pragma', 'no-cache');
  }
  return result;
}

function methodNotAllowed(allow, noStore = false) {
  return withCors(new Response('Method not allowed', {
    status: 405,
    headers: { Allow: allow },
  }), noStore);
}
