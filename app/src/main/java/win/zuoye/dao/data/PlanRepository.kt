package win.zuoye.dao.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object PlanDocumentSerializer : Serializer<PlanDocument> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue: PlanDocument = PlanDocument()

    override suspend fun readFrom(input: InputStream): PlanDocument =
        try {
            json.decodeFromString(PlanDocument.serializer(), input.readBytes().decodeToString())
        } catch (error: SerializationException) {
            throw CorruptionException("Unable to decode plan.json", error)
        }

    override suspend fun writeTo(t: PlanDocument, output: OutputStream) {
        output.write(json.encodeToString(PlanDocument.serializer(), t).encodeToByteArray())
    }
}

/** ViewModel 依赖的最小存储边界；测试可注入内存 fake。 */
interface PlanStore {
    val document: Flow<PlanDocument>
    val corruptionRecovery: StateFlow<String?>
    suspend fun update(transform: (PlanDocument) -> PlanDocument)
    fun acknowledgeCorruptionRecovery()
}

/** 单例仓库：全量文档读写，DataStore 保证原子落盘。 */
class PlanRepository private constructor(context: Context) : PlanStore {
    private val planFile = File(context.filesDir, PLAN_FILE_NAME)
    private val _corruptionRecovery = MutableStateFlow<String?>(null)

    private val store: DataStore<PlanDocument> = DataStoreFactory.create(
        serializer = PlanDocumentSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler {
            val backup = backupCorruptPlan(planFile)
            _corruptionRecovery.value = backup?.name.orEmpty()
            PlanDocumentSerializer.defaultValue
        },
    ) { planFile }

    override val document: Flow<PlanDocument> = store.data
    override val corruptionRecovery: StateFlow<String?> = _corruptionRecovery.asStateFlow()

    override suspend fun update(transform: (PlanDocument) -> PlanDocument) {
        store.updateData(transform)
    }

    override fun acknowledgeCorruptionRecovery() {
        _corruptionRecovery.value = null
    }

    companion object {
        private const val PLAN_FILE_NAME = "plan.json"

        @Volatile
        private var instance: PlanRepository? = null

        fun get(context: Context): PlanRepository =
            instance ?: synchronized(this) {
                instance ?: PlanRepository(context.applicationContext).also { instance = it }
            }
    }
}

internal fun backupCorruptPlan(planFile: File, now: Long = System.currentTimeMillis()): File? {
    if (!planFile.isFile) return null
    val backup = File(planFile.parentFile, "plan.corrupt-$now.json")
    return runCatching { planFile.copyTo(backup, overwrite = false) }.getOrNull()
}
