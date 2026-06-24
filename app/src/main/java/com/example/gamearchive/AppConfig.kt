package com.example.gamearchive

/**
 * 全局配置。
 *
 * 代理服务器地址统一放在这里：以后想换自己部署的 Worker，只改这一处即可，
 * 不用再到 MainActivity、SpecialsFragment 等多个文件里逐个替换。
 */
object AppConfig {
    // Steam 数据代理地址（Cloudflare Worker）。末尾的斜杠不要省。
    const val PROXY_URL = "https://api.steam-tracker-proxy.cyou/"
}
