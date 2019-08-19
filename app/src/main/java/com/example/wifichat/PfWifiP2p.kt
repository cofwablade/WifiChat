package com.example.wifichat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat.getSystemService
import java.net.InetAddress
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PfWifiP2p ( private val mContext : Context)
{
    inner class BcastRecv() : BroadcastReceiver()
    {
        init {

        }

        override fun onReceive( context: Context, intent: Intent)
        {
            val action : String? = intent.action

            when( action ) {

                // --------------------------------------------------------------------------------
                // peerの列挙結果
                // --------------------------------------------------------------------------------
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {

                    mWifiMgr.requestPeers( mWifiCh ) {

                         mListener.scan?.invoke( true, it )
                    }
                }

                // --------------------------------------------------------------------------------
                // 指定されたpeerへの接続/切断通知
                // --------------------------------------------------------------------------------
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {

                    if( true /*mConnecting*/ ) {

                        mConnecting = false

                        if ( mNetworkAvailable ) {

                            mWifiMgr.requestConnectionInfo(mWifiCh) {

                                it?.apply {

                                    if (groupFormed) {

                                        mConnected = true
                                        mGroupOwner = isGroupOwner
                                        mGroupOwnerAddr = groupOwnerAddress

                                        mListener.connect?.invoke(true, isGroupOwner, mGroupOwnerAddr )

                                    } else {

                                        if( mConnected ) {

                                            mConnected = false

                                            mListener.disconnect?.invoke()
                                        }
                                    }
                                }
                            }

                        } else {

                            mConnected = false

                            mListener.connect?.invoke(false, mGroupOwner, null )
                        }
                    }
                }

                // --------------------------------------------------------------------------------
                // ????
                // --------------------------------------------------------------------------------
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {

                }
            }
        }
    }

    private val mLock = ReentrantLock()

    private val mBcastRecv = BcastRecv()
    private val mBcastRecvIntentFilter : IntentFilter

    private val mHThread = HandlerThread( "PfWifiP2p" )

    private val mWifiMgr : WifiP2pManager
    private val mWifiCh  : WifiP2pManager.Channel

    private val mConnMgr : ConnectivityManager
    private val mNetworkRequest : NetworkRequest

    private var mNetworkAvailable = false

    private var mConnecting = false
    private var mConnected  = false
    private var mGroupOwner = false
    private var mGroupOwnerAddr : InetAddress? = null

    init {

        mHThread.start()

        mConnMgr = mContext.getSystemService( Context.CONNECTIVITY_SERVICE ) as ConnectivityManager

        val NetReqBuild = NetworkRequest.Builder()

        // NetReqBuild.addCapability( NetworkCapabilities.NET_CAPABILITY_WIFI_P2P  )
        // NetReqBuild.addCapability( NetworkCapabilities.NET_CAPABILITY_VALIDATED  )
        NetReqBuild.addTransportType( NetworkCapabilities.TRANSPORT_WIFI )
        mNetworkRequest = NetReqBuild.build()

        mWifiMgr = mContext.getSystemService( Context.WIFI_P2P_SERVICE ) as WifiP2pManager

        mWifiCh  = mWifiMgr.initialize( mContext, mHThread.looper, null)

        mBcastRecvIntentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
    }

    private val mConnNetworkCallback = object : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            super.onAvailable(network)

            mNetworkAvailable = true
        }

        override fun onLost(network: Network) {
            super.onLost(network)

            mNetworkAvailable = false
        }
    }

    fun onResume()
    {
        mLock.withLock {
            mConnMgr.registerNetworkCallback( mNetworkRequest, mConnNetworkCallback )
            mContext.registerReceiver( mBcastRecv, mBcastRecvIntentFilter )
        }
    }

    fun onPause()
    {
        mLock.withLock {
            mContext.unregisterReceiver( mBcastRecv )
            mConnMgr.unregisterNetworkCallback( mConnNetworkCallback )
        }
    }

    inner class Listener()
    {
        init {

        }

        var scan       : ( ( Boolean, WifiP2pDeviceList? ) -> Unit )? = null
        var connect    : ( ( Boolean, Boolean, InetAddress? ) -> Unit )? = null
        var disconnect : ( () ->Unit )? = null
    }

    private val mListener = Listener()

    fun scan()
    {
        mLock.withLock {

            mWifiMgr.discoverPeers( mWifiCh, object: WifiP2pManager.ActionListener {

                override fun onSuccess()
                {
                }

                override fun onFailure( rc : Int )
                {
                    mListener.scan?.invoke( false, null )
                }
            })
        }
    }

    fun setOnScanListener( listener : (Boolean, WifiP2pDeviceList?) -> Unit )
    {
        mLock.withLock {

            mListener.scan = listener
        }
    }

    fun connect( deviceAddress : String ) : Boolean
    {
        mLock.withLock {

            if( mConnected || mConnecting ) {
                return false
            }

            val cfg = WifiP2pConfig()

            cfg.deviceAddress = deviceAddress

            mConnecting = true

            mWifiMgr.connect( mWifiCh, cfg, object : WifiP2pManager.ActionListener {

                override fun onSuccess() {
                }

                override fun onFailure( rc : Int ) {
                    mConnecting = false
                }
            })
        }

        return true
    }

    fun disconnect()
    {
        mLock.withLock {

            if( !mConnected ) {
                return
            }

            mWifiMgr.removeGroup( mWifiCh, object : WifiP2pManager.ActionListener {

                override fun onSuccess() {
                    mConnected = false
                }

                override fun onFailure( rc : Int ) {
                    mConnected = false
                }
            })
        }
    }

    fun setOnConnectListener( listener : ( success : Boolean, owner : Boolean, owner_address : InetAddress? ) -> Unit )
    {
        mLock.withLock {

            mListener.connect = listener
        }
    }

    fun isConnected() : Boolean
    {
        return mConnected
    }

    fun setOnDisconnectListener( listener : () -> Unit )
    {
        mLock.withLock {

            mListener.disconnect = listener
        }
    }
}
