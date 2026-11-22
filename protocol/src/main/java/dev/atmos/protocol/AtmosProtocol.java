package dev.atmos.protocol;

/** 协议全局常量。两端共享，任何改动必须同步 protocol 版本。 */
public final class AtmosProtocol {
    /** 当前协议版本（白名单） */
    public static final byte PROTO_VERSION = 1;
    /** 支持的协议版本白名单 */
    public static final byte[] SUPPORTED_VERSIONS = {1};
    /** 魔数 "ATMS" */
    public static final byte[] MAGIC = {'A', 'T', 'M', 'S'};
    /** 单包硬上限：未注册通道 32767 字节；我们留余量按 30720（30 KiB） */
    public static final int MAX_PAYLOAD = 30 * 1024;
    /** 通道命名空间 */
    public static final String NAMESPACE = "atmos";

    public static String channel(String name) {
        return NAMESPACE + ":" + name;
    }

    public static boolean isSupportedVersion(byte v) {
        for (byte s : SUPPORTED_VERSIONS) if (s == v) return true;
        return false;
    }

    private AtmosProtocol() {}
}
