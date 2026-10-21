package com.derszili.mobile

import android.content.Context
import com.derszili.shared.SchoolSchedule
import com.derszili.shared.toJson
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object ScheduleSync {
    const val PATH = "/school_schedule"
    const val KEY_JSON = "schedule_json"

    enum class Result {
        SENT_TO_CONNECTED_NETWORK,
        QUEUED_UNTIL_CONNECTED,
        FAILED,
    }

    fun send(context: Context, schedule: SchoolSchedule, onResult: (Result) -> Unit = {}) {
        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putString(KEY_JSON, schedule.toJson())
            // Değişiklik yokken bile kullanıcının elle eşitleyebilmesi için benzersiz değer.
            dataMap.putLong("sent_at", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getNodeClient(context).connectedNodes.addOnCompleteListener { nodeTask ->
            val hasConnectedNode = nodeTask.isSuccessful && nodeTask.result.isNotEmpty()
            Wearable.getDataClient(context)
                .putDataItem(request)
                .addOnSuccessListener {
                    onResult(
                        if (hasConnectedNode) Result.SENT_TO_CONNECTED_NETWORK
                        else Result.QUEUED_UNTIL_CONNECTED
                    )
                }
                .addOnFailureListener { onResult(Result.FAILED) }
        }
    }
}
