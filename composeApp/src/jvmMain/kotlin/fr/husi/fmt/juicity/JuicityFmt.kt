package fr.husi.fmt.juicity

import fr.husi.database.DataStore
import fr.husi.database.preference.getBlocking
import fr.husi.fmt.LOCALHOST4
import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.protectPath
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.parseBoolean
import fr.husi.ktx.toJsonStringKxs
import io.github.xchacha20_poly1305.kpuri.Url
import io.github.xchacha20_poly1305.kpuri.UrlOptions
import io.github.xchacha20_poly1305.kpuri.buildUrl
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 允许 URL 端口位出现范围，如 50000-50050 */
private val juicityUrlOptions = UrlOptions(validatePort = false)

// https://github.com/juicity/juicity/blob/4af4f68b405a6b86560ebb16963d133a7196af5c/README.md
fun parseJuicity(link: String): JuicityBean {
    val url = Url.parse(link, juicityUrlOptions)
    return JuicityBean().apply {
        name = url.fragment.orEmpty()
        uuid = url.username.orEmpty()
        password = url.password.orEmpty()
        serverAddress = url.host.orEmpty()

        val portPart = url.port.orEmpty().ifBlank { "443" }
        serverPorts = portPart
        serverPort = portPart.split(',', '-', ' ')
            .mapNotNull { it.trim().toIntOrNull() }
            .firstOrNull() ?: 443

        // url.queryParameter("congestion_control")
        sni = url.queryParameter("sni").orEmpty()
        allowInsecure = url.parseBoolean("allow_insecure")
        pinSHA256 = url.queryParameter("pinned_certchain_sha256").orEmpty()
        url.queryParameter("port_hop_interval")?.toIntOrNull()?.let {
            portHopInterval = it.coerceAtLeast(0)
        }
        // 兼容 mport（部分客户端习惯）
        url.queryParameter("mport")?.takeIf { it.isNotBlank() }?.let {
            serverPorts = it
            serverPort = it.split(',', '-', ' ')
                .mapNotNull { p -> p.trim().toIntOrNull() }
                .firstOrNull() ?: serverPort
        }
    }
}

fun JuicityBean.toUri(): String = buildUrl("juicity", juicityUrlOptions) {
    username = uuid
    password = this@toUri.password
    host = serverAddress
    port = serverPorts.ifBlank { serverPort.toString() }

    addQueryParameter("congestion_control", "bbr")
    if (sni.isNotBlank()) addQueryParameter("sni", sni)
    if (allowInsecure) addQueryParameter("allow_insecure", "1")
    if (pinSHA256.isNotBlank()) addQueryParameter("pinned_certchain_sha256", pinSHA256)
    if (portHopInterval > 0) addQueryParameter("port_hop_interval", portHopInterval.toString())
}.toString()

/**
 * 写给 juicity 插件进程的配置。
 * 要求插件内 juicity-client 已支持 server 为 host:port 或 host:min-max，以及 port_range / port_hop_interval。
 */
fun JuicityBean.buildJuicityConfig(port: Int, shouldProtect: Boolean): String {
    val ports = serverPorts.ifBlank { serverPort.toString() }
    // 插件拨号地址：优先带范围
    val serverField = if (ports.contains('-') || ports.contains(',')) {
        // IPv6 需要方括号
        if (serverAddress.contains(':') && !serverAddress.startsWith('[')) {
            "[$finalAddress]:$ports"
        } else {
            "$finalAddress:$ports"
        }
    } else {
        pluginDialAddress()
    }

    return buildJsonObject {
        put("listen", "$LOCALHOST4:$port")
        put("server", serverField)
        // 显式 port_range，便于客户端解析（与 server 内嵌范围二选一即可）
        if (ports.contains('-') || ports.contains(',')) {
            put("port_range", ports)
        }
        if (portHopInterval > 0) {
            put("port_hop_interval", portHopInterval)
        }
        put("uuid", uuid)
        put("password", password)
        put("sni", pluginServerName(sni))
        if (allowInsecure) {
            put("allow_insecure", true)
        }
        put("congestion_control", "bbr")
        pinSHA256.blankAsNull()?.let {
            put("pinned_certchain_sha256", it)
        }
        if (shouldProtect) {
            put("protect_path", protectPath)
        }
        put(
            "log_level",
            if (DataStore.logLevel.getBlocking() > 0) {
                "debug"
            } else {
                "error"
            },
        )
    }.toJsonStringKxs()
}

fun buildSingBoxOutboundJuicityBean(bean: JuicityBean): SingBoxOptions.Outbound_JuicityOptions {
    // sing-box / 内核 juicity 出站目前仍是单端口；范围由插件路径实现。
    val firstPort = bean.serverPorts.ifBlank { bean.serverPort.toString() }
        .split(',', '-', ' ')
        .mapNotNull { it.trim().toIntOrNull() }
        .firstOrNull() ?: bean.serverPort

    return SingBoxOptions.Outbound_JuicityOptions().apply {
        type = SingBoxOptions.TYPE_JUICITY
        server = bean.serverAddress
        server_port = firstPort
        uuid = bean.uuid
        password = bean.password
        pin_cert_sha256 = bean.pinSHA256.blankAsNull()
        tls = SingBoxOptions.OutboundTLSOptions().apply {
            enabled = true
            server_name = bean.sni.blankAsNull()
            insecure = bean.allowInsecure || pin_cert_sha256 != null
        }
    }
}
