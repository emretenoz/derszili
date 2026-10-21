package com.derszili.mobile

import android.content.Context
import com.derszili.shared.SchoolSchedule
import com.derszili.shared.toJson
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object ScheduleSync {
    const val PATH = "/school_schedule"
    const val KEY_JSON = "schedule_json"

    fun send(context: Context, schedule: SchoolSchedule, onResult: (Boolean) -> Unit = {}) {
        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putString(KEY_JSON, schedule.toJson())
            // Değişiklik yokken bile kullanıcının elle eşitleyebilmesi için benzersiz değer.
            dataMap.putLong("sent_at", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context)
            .putDataItem(request)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}
