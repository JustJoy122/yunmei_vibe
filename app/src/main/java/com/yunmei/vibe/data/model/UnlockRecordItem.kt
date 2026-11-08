package com.yunmei.vibe.data.model

/**
 * 服务端返回的一条开门记录（官方 `lockrecord/getrecordbyschool`）。
 *
 * 官方「开门记录」页只渲染三块：楼栋 + 房间、开门人姓名、开门时间，
 * 因此这里也只保留这几个字段；接口里其余字段（id、token、电参等）当前页面用不到，
 * 拿不到就留空，不做猜测性解析。
 *
 * 注意与 [UnlockRecord] 区分：[UnlockRecord] 是本机上报时组装的请求体。
 */
data class UnlockRecordItem(
    /** 楼栋名称，例如「D9东」；服务端没给时为空串。 */
    val buildName: String = "",
    /** 房间名称，例如「215」；服务端没给时为空串。 */
    val dormName: String = "",
    /** 开门人姓名（官方模板里的 realName）。 */
    val realName: String = "",
    /** 开门时间，服务端已是 `yyyy-MM-dd HH:mm:ss` 文本，原样展示。 */
    val lockTime: String = "",
) {

    /** 列表主标题：楼栋 + 房间，两者都空时退化为占位文案由调用方处理。 */
    val location: String
        get() = buildName + dormName
}
