package com.derszili.wear

import com.derszili.shared.AlarmScheduler
import com.derszili.shared.ScheduleStore
import com.derszili.shared.SchoolSchedule
import com.derszili.shared.scheduleFromJsonOrNull
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class ScheduleDataService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val candidates = mutableListOf<SchoolSchedule>()
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != PATH) return@forEach
            val json = DataMapItem.fromDataItem(event.dataItem).dataMap.getString(KEY_JSON)
            scheduleFromJsonOrNull(json)?.let(candidates::add)
        }

        val latest = candidates.maxByOrNull { it.updatedAt } ?: return
        if (latest.updatedAt > ScheduleStore.load(this).updatedAt) {
            ScheduleStore.save(this, latest)
            AlarmScheduler.schedule(this, latest)
        }
    }

    companion object {
        const val PATH = "/school_schedule"
        const val KEY_JSON = "schedule_json"
    }
}
