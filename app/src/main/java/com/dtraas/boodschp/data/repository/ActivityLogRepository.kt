package com.dtraas.boodschp.data.repository

import com.dtraas.boodschp.data.local.dao.ActivityLogDao
import com.dtraas.boodschp.data.local.dao.ActivityLogWithProduct
import com.dtraas.boodschp.data.local.entity.ActivityLogEntity
import com.dtraas.boodschp.data.model.ActivityType
import kotlinx.coroutines.flow.Flow

class ActivityLogRepository(
    private val activityLogDao: ActivityLogDao,
) {
    fun observeRecent(limit: Int = 200): Flow<List<ActivityLogWithProduct>> = activityLogDao.observeRecent(limit)

    suspend fun log(barcode: String, type: ActivityType, detail: String) {
        activityLogDao.insert(
            ActivityLogEntity(
                barcode = barcode,
                type = type.storageKey,
                detail = detail,
                timestamp = System.currentTimeMillis(),
            )
        )
    }
}
