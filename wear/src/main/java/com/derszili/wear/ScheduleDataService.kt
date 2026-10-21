package com.derszili.wear

import com.derszili.shared.AlarmScheduler
import com.derszili.shared.ScheduleStore
import com.derszili.shared.scheduleFromJson
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class ScheduleDataService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            if (event.dataItem.uri.path != PATH) return@forEach
            val json = DataMapItem.fromDataItem(event.dataItem).dataMap.getString(KEY_JSON)
            val schedule = scheduleFromJson(json)
            ScheduleStore.save(this, schedule)
            AlarmScheduler.schedule(this, schedule)
        }
    }

    companion object {
        const val PATH = "/school_schedule"
        const val KEY_JSON = "schedule_json"
    }
}
