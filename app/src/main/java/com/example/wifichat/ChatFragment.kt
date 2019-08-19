package com.example.wifichat


import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.selfchat.PfSocket
import com.example.selfchat.PfSocketClient
import com.example.selfchat.PfSocketServer
import kotlinx.android.synthetic.main.fragment_chat.*
import java.net.InetAddress


/**
 * A simple [Fragment] subclass.
 *
 */
class ChatFragment( server : Boolean, server_address : InetAddress? ) : Fragment() {

    inner class Param()
    {
        var mServer = false
        var mConnecting  = false
        var mConnected = false
        var mServerAddress : InetAddress? = null

        init {

        }

        fun save( bundle : Bundle ) {

        }

        fun restore( bundle : Bundle ) {

        }
    }

    var mServer = PfSocketServer()

    var mClient = PfSocketClient()

    var mSocket : PfSocket? = null

    val mParam = Param()

    // プライマリコンストラクタ
    init {

        mParam.mServer = server
        mParam.mServerAddress = server_address
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        savedInstanceState?.let {
            mParam.restore( it )
        }

        // Inflate the layout for this fragment
        val ret = inflater.inflate(R.layout.fragment_chat, container, false)

        return ret
    }

    override fun onStart() {

        super.onStart()

        // Connectボタンが押下された場合の処理
        buttonConnect.setOnClickListener {

            if( mParam.mServer == false ) {

                buttonConnect.isEnabled = false

                // クラアントのソケットの新規生成
                mClient = PfSocketClient()

                // サーバーに接続できた場合の処理
                mClient.setOnConnectedListener {

                    if( it )
                    {
                        setupSocket( mClient )
                    }
                }

                // サーバーのアドレスを取得
                val hostname = mParam.mServerAddress?.hostAddress

                // サーバーへ接続
                mClient.connect( 8888, hostname, 3000 )
            }
        }

        // Disconnectボタンが押下された場合の処理
        buttonDisconnect.setOnClickListener {

            mSocket?.close()

            buttonDisconnect.isEnabled = false
        }

        // Close P2pボタンが押下された場合の処理
        buttonReturn.setOnClickListener {

            mSocket?.close()

            mServer.close()

            // val intent = Intent( activity,  MainActivity::class.java )

            val parent = activity as MainActivity

            parent.fmentConnectSetup()
        }

        // Sendボタンが押下された場合の処理
        buttonSend.setOnClickListener {

            val data = editText.text.toString().toByteArray()

            mSocket?.send( data )

            editText.text.clear()
        }

        buttonConnect.isEnabled = false
        buttonDisconnect.isEnabled = false
        buttonSend.isEnabled = false

        // サーバーの場合の設定処理
        if( mParam.mServer )
        {
            setupAsServer()
        }
        // クライアントの場合の設定処理
        else
        {
            setupAsClient()
        }
    }

    override fun onDetach() {
        super.onDetach()

        mSocket?.close()

        mSocket = null
    }

    private fun setupAsServer()
    {
        // ポートのopen処理が終了した場合
        mServer.setOnOpenedListener {

        }

        // ポートのclose処理が終了た場合
        mServer.setOnClosedListener {

        }

        // ポートにクライアントが接続してきた場合の処理
        mServer.setOnConnectedListener {

            if( mSocket == null ) {

                setupSocket(it)
            }
            else {

                it.close()
            }
        }

        // PortをOpen
        mServer.open( 8888 )
    }

    private fun setupAsClient()
    {
        buttonConnect.isEnabled = true
    }

    // 通信ソケットの設定処理
    private fun setupSocket( socket : PfSocket )  {

        mSocket = socket

        activity?.let {

            Handler( it.mainLooper ).post {

                buttonDisconnect.isEnabled = true
                buttonSend.isEnabled = true
            }
        }

        // データを受信した場合の処理
        mSocket?.setOnReceivedListener {

            var data = ByteArray(128)

            it.recv( data, timeout_msec = 100 )

            val str = String( data )

            activity?.let {

                Handler( it.mainLooper ).post {
                    textViewChat.text = textViewChat.text.toString() + str + "\n"
                }
            }
        }

        // 通信ソケットが切断された場合
        mSocket?.setOnDisconnectedListener {

            mSocket?.close()

            mSocket = null

            activity?.let {

                Handler( it.mainLooper ).post {

                    buttonDisconnect.isEnabled = false

                    buttonSend.isEnabled = false

                    if( mParam.mServer == false ) {

                        buttonConnect.isEnabled = true
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

    }

}
