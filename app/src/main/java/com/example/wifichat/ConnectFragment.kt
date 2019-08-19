package com.example.wifichat


import android.content.Context
import android.net.wifi.p2p.WifiP2pDeviceList
import android.os.Bundle
import android.os.Handler
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.fragment_connect.*
import java.net.InetAddress


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 *
 */
class ConnectFragment( val mWifiP2p : PfWifiP2p ) : Fragment() {

    var listPeersName = mutableListOf<String>()
    var listPeersAddr = mutableListOf<String>()

    var mScanning : Boolean = false

    var mContext : Context? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_connect, container, false)
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        mContext = context
    }

    override fun onStart()
    {
        super.onStart()

        buttonScan.setOnClickListener {

            textViewStat.text = "scanning..."

            mScanning = true

            mWifiP2p.scan()
        }

        buttonDisconnect.setOnClickListener {

            textViewStat.text = "disconnect wifi p2p"

            mWifiP2p.disconnect()

            buttonDisconnect.isEnabled = false
            listView.isEnabled = true
        }

        mWifiP2p.setOnScanListener { success : Boolean, list : WifiP2pDeviceList? ->

            listPeersName.clear()
            listPeersAddr.clear()

            list?.let {

                for( dev in it.deviceList )  {
                    listPeersName.add( dev.deviceName )
                    listPeersAddr.add( dev.deviceAddress )
                }
            }

            activity?.let {

                Handler( it.mainLooper ).post {

                    if( mScanning ) {

                        mScanning = false

                        if (success == true) {

                            textViewStat.text = "scan is done successfully."
                        } else {

                            textViewStat.text = "scan error."
                        }
                    }

                    val context = mContext ?: it.applicationContext

                    val adapter = ArrayAdapter( context, android.R.layout.simple_list_item_1, listPeersName )

                    listView.adapter = adapter

                    listView.isEnabled = true
                }
            }
        }

        mWifiP2p.setOnConnectListener{ success : Boolean, owner : Boolean, owner_address : InetAddress? ->

            activity?.let {

                Handler(it.mainLooper).post {

                    if (success) {

                        buttonDisconnect.isEnabled = true

                        listView.isEnabled = false

                        val fm = fragmentManager

                        fm?.apply {

                            val ft = beginTransaction()

                            val fragment = ChatFragment(owner, owner_address)

                            ft.replace(R.id.container, fragment)
                                .commit()
                        }

                    } else {

                        listView.isEnabled = true

                        textViewStat.text = "connect error"
                    }
                }
            }
        }

        listView.setOnItemClickListener { adptv : AdapterView<*>?, v : View?, pos: Int, id : Long ->

            listView.isEnabled = false

            textViewStat.text = "connecting to " + listPeersName[pos].toString()

            mWifiP2p.connect( listPeersAddr[pos] )
        }
    }
}
