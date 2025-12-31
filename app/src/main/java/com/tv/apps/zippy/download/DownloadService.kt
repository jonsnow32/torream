package com.tv.apps.zippy.download

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DownloadService : Service()
{
  override fun onBind(p0: Intent?): IBinder? {
    TODO("Not yet implemented")
  }

}
