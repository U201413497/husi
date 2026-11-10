package fr.husi.fmt.juicity

import kotlinx.serialization.Serializable as KxsSerializable
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.BeanConverters
import fr.husi.fmt.ValidateResult
import fr.husi.io.BinaryInput
import fr.husi.io.BinaryOutput
import fr.husi.platform.PlatformInfo
import fr.husi.resources.Res
import fr.husi.resources.warn_insecure

@KxsSerializable
class JuicityBean : AbstractBean() {

    companion object {
        @JvmField
        val CREATOR = object : CREATOR<JuicityBean>() {
            override fun newInstance(): JuicityBean {
                return JuicityBean()
            }

            override fun newArray(size: Int): Array<JuicityBean?> {
                return arrayOfNulls(size)
            }
        }
    }

    var uuid: String = ""
    var password: String = ""
    var sni: String = ""
    var allowInsecure: Boolean = false

    // Only BBR???
    // https://github.com/daeuniverse/softwind/blob/6daa40f6b7a5cb9a0c44ea252e86fcb3440a7a0e/protocol/tuic/common/congestion.go#L15
    // public String congestionControl;
    var pinSHA256: String = ""

    /**
     * 端口或端口范围，与 Hysteria 的 serverPorts 类似。
     * 例： "443" / "50000-50050" / "443,50000-50050"
     * 为空时回退到 [serverPort]。
     */
    var serverPorts: String = ""

    /** 端口跳跃间隔（秒）。0 表示不写 hop interval，仅由客户端在新建连接时随机端口。 */
    var portHopInterval: Int = 0

    override val canSelfProtect get() = PlatformInfo.isAndroid || PlatformInfo.isLinux

    override fun isInsecure(): ValidateResult {
        val result = super.isInsecure()
        if (shouldReturnFromInsecureCheck(result)) return result

        if (allowInsecure) return ValidateResult.Insecure(Res.string.warn_insecure)
        return ValidateResult.Secure.Continue
    }

    override fun initializeDefaultValues() {
        super.initializeDefaultValues()
        if (serverPorts.isBlank()) {
            serverPorts = serverPort.toString()
        }
        // 从 serverPorts 同步一个代表性 serverPort（取第一个数字），便于列表展示等
        val first = serverPorts.split(',', '-', ' ')
            .mapNotNull { it.trim().toIntOrNull() }
            .firstOrNull()
        if (first != null && first in 1..65535) {
            serverPort = first
            finalPort = first
        }
        if (portHopInterval < 0) portHopInterval = 0
    }

    override fun displayAddress(): String {
        val ports = serverPorts.ifBlank { serverPort.toString() }
        return if (serverAddress.contains(':')) {
            "[$serverAddress]:$ports"
        } else {
            "$serverAddress:$ports"
        }
    }

    override fun serialize(output: BinaryOutput) {
        // version bump: 0 -> 1 (serverPorts + portHopInterval)
        output.writeInt(1)
        super.serialize(output)
        output.writeString(uuid)
        output.writeString(password)
        output.writeString(sni)
        output.writeBoolean(allowInsecure)
        output.writeString(pinSHA256)
        output.writeString(serverPorts)
        output.writeInt(portHopInterval)
    }

    override fun deserialize(input: BinaryInput) {
        val version = input.readInt()
        super.deserialize(input)
        uuid = input.readString()
        password = input.readString()
        sni = input.readString()
        allowInsecure = input.readBoolean()
        pinSHA256 = input.readString()
        if (version >= 1) {
            serverPorts = input.readString()
            portHopInterval = input.readInt()
        } else {
            serverPorts = serverPort.toString()
            portHopInterval = 0
        }
    }

    override fun applyFeatureSettings(other: AbstractBean) {
        if (other !is JuicityBean) return
        other.allowInsecure = allowInsecure
        if (other.serverPorts.isBlank()) other.serverPorts = serverPorts
        if (other.portHopInterval == 0) other.portHopInterval = portHopInterval
    }

    override val defaultPort get() = 443
    override val canTCPing get() = false

    override fun clone(): JuicityBean {
        return BeanConverters.deserialize(JuicityBean(), BeanConverters.serialize(this))
    }

}
