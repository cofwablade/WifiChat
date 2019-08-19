package com.example.wifichat

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler

class MainActivity : AppCompatActivity() {

    lateinit var mWifiP2p : PfWifiP2p

    fun fmentConnectSetup()
    {
        mWifiP2p.disconnect()

        val fragment = ConnectFragment( mWifiP2p )

        val fm = getSupportFragmentManager()

        val ft = fm.beginTransaction()

        ft.replace( R.id.container, fragment )
          .commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mWifiP2p = PfWifiP2p( applicationContext )

        mWifiP2p.setOnDisconnectListener {

            Handler( mainLooper ).post {

                fmentConnectSetup()
            }
        }

        fmentConnectSetup()
    }

    override fun onResume() {
        super.onResume()

        mWifiP2p.onResume()
    }

    override fun onPause() {
        super.onPause()

        mWifiP2p.onPause()
    }

    override fun onStop() {
        super.onStop()

        mWifiP2p.disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
