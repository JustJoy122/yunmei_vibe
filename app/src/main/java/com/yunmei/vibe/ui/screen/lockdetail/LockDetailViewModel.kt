package com.yunmei.vibe.ui.screen.lockdetail

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.model.Lock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 门锁详情页状态。
 *
 * [loaded] 用于区分「尚未读取」与「读取后确认不存在」：页面只在前者不满足且 [lock] 为空时才返回，
 * 避免页面异步化后首帧就把用户弹回上一页。
 */
@Immutable
data class LockDetailUiState(
    val lock: Lock? = null,
    val isDefault: Boolean = false,
    val accountSaved: Boolean = false,
    val loaded: Boolean = false,
)

/**
 * 门锁详情页 ViewModel：把原先散在 Composable 里的 LockStore / AccountStore 读取收敛到此处。
 *
 * 刷新时机与原实现保持一致（页面进入 + 每次回到前台由 LifecycleResumeEffect 调用 [refresh]）；
 * 所有存储读取都在 IO 线程执行，组合期不再触碰数据层。
 */
class LockDetailViewModel : ViewModel() {

    private val container get() = YunMeiApp.app.container

    private val _uiState = MutableStateFlow(LockDetailUiState())
    val uiState: StateFlow<LockDetailUiState> = _uiState.asStateFlow()

    /** 群组成员验证结果：成功是原始 JSON，失败是异常文本，供页面弹窗直接展示与复制。 */
    private val _memberDumpText = MutableStateFlow<String?>(null)
    val memberDumpText: StateFlow<String?> = _memberDumpText.asStateFlow()

    private var label: String = ""

    /** 自动触发只跑一次，避免每次重组都发请求。 */
    private var autoDumped = false

    /** 按 label 读取当前锁 / 是否默认 / 账号是否已保存。 */
    fun refresh(label: String) {
        this.label = label
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val lock = container.lockStore.getAll().firstOrNull { it.label == label }
                val isDefault = container.lockStore.getDefault()?.label == lock?.label
                val saved = lock?.let { container.accountStore.getByUsernameMd5(it.usernameMd5) != null } ?: false
                Triple(lock, isDefault, saved)
            }
            _uiState.update {
                it.copy(
                    lock = snapshot.first,
                    isDefault = snapshot.second,
                    accountSaved = snapshot.third,
                    loaded = true,
                )
            }
        }
    }

    /** 设为默认 / 取消默认（IO 完成后刷新默认态）。 */
    fun setDefault(value: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.lock ?: return@launch
            withContext(Dispatchers.IO) {
                container.lockStore.setDefault(if (value) current else null)
            }
            refresh(label)
        }
    }

    /**
     * 删除当前门锁。刻意做成 suspend：由页面在"确认后"的同一协程里调用，
     * 与改动前的执行时机一致（删除完成后页面才 pop），避免 VM 随页面销毁而取消删除。
     */
    suspend fun removeLock() {
        val current = _uiState.value.lock ?: return
        withContext(Dispatchers.IO) { container.lockStore.remove(current.label) }
    }
    /**
     * 临时验证入口：把门锁所在宿舍的成员原始 JSON 打到 Logcat，并回填给页面弹窗。
     *
     * 服务端真实层级（是否包 `data`、字段名是否与官方前端一致）尚未实测，
     * 先用这段最小实现取一次真样本，确认后再做列表 UI，避免照前端字段名猜结构。
     * 日志用 info 级：部分 ROM（实测 ColorOS）会丢弃第三方应用的 Debug 级日志。
     */
    /** 进入详情页自动跑一次：真机上手动点击的入口可能被 ROM 拦掉，自动触发更可靠。 */
    fun autoDumpOnce() {
        if (autoDumped) return
        autoDumped = true
        dumpDormMembers()
    }

    fun dumpDormMembers() {
        val current = _uiState.value.lock
        if (current == null) {
            _memberDumpText.value = "当前门锁为空，未发起请求"
            viewModelScope.launch { dumpToFile("当前门锁为空，未发起请求") }
            return
        }
        viewModelScope.launch {
            dumpToFile("START lock=" + current.label + " schoolNo=" + current.schoolNo + " lockNo=" + current.lockNo)
            val result = try {
                container.repository.getDormMembers(current).toString()
            } catch (error: CancellationException) {
                // 协程取消不是业务失败，必须原样抛出，不能当成接口报错写进结果。
                throw error
            } catch (error: Exception) {
                Log.e(DORM_MEMBER_TAG, current.label + " failed", error)
                buildFailureText(error)
            }
            Log.i(DORM_MEMBER_TAG, current.label + " -> " + result)
            _memberDumpText.value = result
            dumpToFile(result)
        }
    }

    /**
     * 把结果同时写到外部私有目录。
     *
     * 真机上弹窗与 Logcat 都可能被 ROM 拦掉（实测 ColorOS 丢弃第三方 Debug 日志），
     * 落盘是唯一能稳定取回的通道：文件在 `/sdcard/Android/data/<包名>/files/dorm_debug.txt`。
     */
    private suspend fun dumpToFile(text: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                // 外部私有目录优先（adb pull 直接可取）；拿不到时退回内部 files 目录，避免结果丢失。
                val dir = YunMeiApp.app.getExternalFilesDir(null) ?: YunMeiApp.app.filesDir
                val file = File(dir, DUMP_FILE_NAME)
                file.appendText(buildString {
                    append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date()))
                    append('\n')
                    append(text)
                })
            }.onFailure { error -> Log.e(DORM_MEMBER_TAG, "写文件失败", error) }
        }
    }

    /** 异常文本带上类型与堆栈首行，方便直接判断 401/404/超时/解析失败。 */
    private fun buildFailureText(error: Exception): String = buildString {
        append("EXCEPTION: ").append(error.javaClass.name).append('\n')
        append("MESSAGE: ").append(error.message.orEmpty()).append('\n')
        append("CAUSE: ").append(error.cause?.javaClass?.name.orEmpty()).append(' ').append(error.cause?.message.orEmpty())
    }

    fun clearDormMemberDump() {
        _memberDumpText.value = null
    }

    /**
     * 保存 AES 门锁的密钥（32 位十六进制）。
     *
     * 只在本机保存、不上传：官方把这对 Key/IV 硬编码在客户端里，本应用改成由用户自行填写。
     * 传空串表示清除，之后开门会被 [com.yunmei.vibe.data.ble.UnlockManager] 拦下并提示填写。
     */
    fun setCustomAesKey(value: String) {
        viewModelScope.launch {
            val current = _uiState.value.lock ?: return@launch
            withContext(Dispatchers.IO) { container.lockStore.setCustomAesKey(current.label, value) }
            refresh(label)
        }
    }

    private companion object {
        /** 验证用日志 tag：真机 `adb logcat -s DormMemberTest` 可直接看到原始 JSON。 */
        const val DORM_MEMBER_TAG = "DormMemberTest"

        /** 结果落盘文件名：外部私有目录下，可用 adb pull 直接取回。 */
        const val DUMP_FILE_NAME = "dorm_debug.txt"
    }
}
