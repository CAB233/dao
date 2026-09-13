package win.zuoye.dao.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import kotlinx.coroutines.flow.Flow
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
        } catch (e: Exception) {
            defaultValue
        }

    override suspend fun writeTo(t: PlanDocument, output: OutputStream) {
        output.write(json.encodeToString(PlanDocument.serializer(), t).encodeToByteArray())
    }
}

/** 单例仓库：全量文档读写，DataStore 保证原子落盘。 */
class PlanRepository private constructor(context: Context) {

    private val store: DataStore<PlanDocument> = DataStoreFactory.create(
        serializer = PlanDocumentSerializer,
    ) { File(context.filesDir, "plan.json") }

    val document: Flow<PlanDocument> = store.data

    suspend fun update(transform: (PlanDocument) -> PlanDocument) {
        store.updateData(transform)
    }

    companion object {
        @Volatile
        private var instance: PlanRepository? = null

        fun get(context: Context): PlanRepository =
            instance ?: synchronized(this) {
                instance ?: PlanRepository(context.applicationContext).also { instance = it }
            }
    }
}
