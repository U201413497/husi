package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import fr.husi.fmt.juicity.JuicityBean
import fr.husi.ktx.applyDefaultValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@Immutable
internal data class JuicityUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val address: String = "127.0.0.1",
    /** 端口或范围，如 443 / 50000-50050 */
    val serverPorts: String = "443",
    val portHopInterval: Int = 0,
    val uuid: String = "",
    val password: String = "",
    val sni: String = "",
    val allowInsecure: Boolean = false,
    val pinSha256: String = "",
) : ProfileEditorUiState

@Stable
internal class JuicitySettingsViewModel : ProfileEditorViewModel<JuicityBean>() {
    override fun createBean() = JuicityBean().applyDefaultValues()

    override val uiState: StateFlow<JuicityUiState>
        field = MutableStateFlow(JuicityUiState())

    override suspend fun JuicityBean.writeToUiState() {
        uiState.update {
            it.copy(
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                name = name,
                address = serverAddress,
                serverPorts = serverPorts.ifBlank { serverPort.toString() },
                portHopInterval = portHopInterval,
                uuid = uuid,
                password = password,
                sni = sni,
                allowInsecure = allowInsecure,
                pinSha256 = pinSHA256,
            )
        }
    }

    override fun JuicityBean.loadFromUiState() {
        val state = uiState.value

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        serverAddress = state.address
        serverPorts = state.serverPorts.ifBlank { "443" }
        serverPort = serverPorts.split(',', '-', ' ')
            .mapNotNull { it.trim().toIntOrNull() }
            .firstOrNull() ?: 443
        portHopInterval = state.portHopInterval.coerceAtLeast(0)
        uuid = state.uuid
        password = state.password
        sni = state.sni
        allowInsecure = state.allowInsecure
        pinSHA256 = state.pinSha256
    }

    override fun setCustomConfig(config: String) {
        uiState.update { it.copy(customConfig = config) }
    }

    override fun setCustomOutbound(outbound: String) {
        uiState.update { it.copy(customOutbound = outbound) }
    }

    fun setName(name: String) {
        uiState.update { it.copy(name = name) }
    }

    fun setAddress(address: String) {
        uiState.update { it.copy(address = address) }
    }

    fun setServerPorts(ports: String) {
        uiState.update { it.copy(serverPorts = ports) }
    }

    fun setPortHopInterval(interval: Int) {
        uiState.update { it.copy(portHopInterval = interval.coerceAtLeast(0)) }
    }

    fun setUuid(uuid: String) {
        uiState.update { it.copy(uuid = uuid) }
    }

    fun setPassword(password: String) {
        uiState.update { it.copy(password = password) }
    }

    fun setSni(sni: String) {
        uiState.update { it.copy(sni = sni) }
    }

    fun setAllowInsecure(allow: Boolean) {
        uiState.update { it.copy(allowInsecure = allow) }
    }

    fun setPinSha256(sha256: String) {
        uiState.update { it.copy(pinSha256 = sha256) }
    }

}
