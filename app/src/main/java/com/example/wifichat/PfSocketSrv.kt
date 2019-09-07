package com.example.selfchat

import android.os.Handler
import android.os.HandlerThread
import java.io.InputStream
import java.net.Socket
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PfSocketSrv( val mSocket : Socket ) : PfSocket()
{
    companion object {
        // send用のスレッド( PfSocketSrvで共用 )
        val mHThreadMisc = HandlerThread( "PfSocketSrvMisc" )

        // send時に mHThreadToSendとの同期待ち用のフラグ
        val mFlagMisc = PfFlag()
    }

    // Locker
    val mLock = ReentrantLock()

    // 受信待ちのスレッド
    val mHThread = HandlerThread( "PfSocketSrv" )

    // mHThreadとの同期用
    val mFlag = PfFlag()

    // 入力Stream
    var mIStream : InputStream? = null

    // mHThreadでの受信待ちの1byteのバッファ
    val mReceived1stByte = ByteArray(1)

    var mReceived1st = false

    // 受信待ちのスレッド処理
    val threadProc = object : Runnable {

        override fun run() {

            // 接続している間繰り返す
            while( isAlive_inner() )
            {
                try {

                    // タイムアウトを永久待ちに
                    mSocket.soTimeout = 0

                    // サイズ0では待てないので、1byte待ちをする。
                    var nread = 0

                    if( mReceived1st == false ) {

                        mIStream?.run {
                            nread = read(mReceived1stByte, 0, mReceived1stByte.size)
                        }

                        if( nread > 0 ) {
                            mReceived1st = true
                        }
                    }

                    when
                    {
                        // データがあればリスナーを呼び出す
                        nread > 0 || mReceived1st -> {
                            // onReceived リスナーを呼び出す
                            mListener.onReceived?.invoke( this@PfSocketSrv as PfSocket )
                        }

                        // 0 以下の場合、相手から切断された可能性がある
                        else -> {
                            throw Exception()
                        }
                    }

                } catch (e: Exception) {

                    mSocket.close()
                }
            }

            // mIStreamを破棄する
            mLock.withLock {

                mIStream?.close()

                mIStream = null

                mReceived1st = false
            }

            // 切断されたらその旨をリスナーに通知し終了
            mListener.onDisconnected?.invoke( Unit )

            mFlag.set(1)
        }
    }

    init {

        // 送信用のスレッドが起動していなければ起動させる
        if( !mHThreadMisc.isAlive() )
        {
            mHThreadMisc.start()
        }

        // InputStreamを取得
        mIStream = mSocket.getInputStream()

        // 同期用のフラグをクリア
        mFlag.clear()

        // 受信待ちのスレッドを起動
        mHThread.start()

        // 受信待ちの処理を mHThreadにpostする
        val handler = Handler( mHThread.looper )

        handler.post( threadProc )
    }

    // 送信もメインスレッドからはNG
    override fun send( data : ByteArray, len : Int ) : Int
    {
        mLock.withLock {

            if ( !isAlive_inner() ) {
                return -1
            }

            mFlagMisc.clear()

            val handler = Handler(mHThreadMisc.looper)

            handler.post {

                try {

                    val ost = mSocket.getOutputStream()

                    when (len) {

                        0 -> {
                            ost.write(data)

                            mFlagMisc.set(data.size)
                        }

                        else -> {
                            ost.write(data, 0, len)

                            mFlagMisc.set(len)
                        }
                    }

                } catch (e: Exception) {

                    mFlagMisc.set(-1)
                }
            }

            return mFlagMisc.wait().toInt()
        }
    }

    // 受信はmHThreadにおいて、onReceiveからの呼び出しを想定するので、ここでは特別なケアはしない。
    override fun recv( data : ByteArray, len : Int, timeout_msec : Int ) : Int
    {
        try {

            // mIStream = mSocket.getInputStream()

            when {

                timeout_msec == 0 -> {
                    mSocket.soTimeout = 1
                }
                timeout_msec < 0 -> {
                    mSocket.soTimeout = 0
                }
                else -> {
                    mSocket.soTimeout = timeout_msec
                }
            }

            var n_read : Int = 0

            var n_to_read : Int = data.size

            if( len > 0 ) {
                n_to_read = len
            }

            if( n_to_read > data.size ) {
                n_to_read = data.size
            }

            var o_to_read = 0

            if( mReceived1st ) {

                mReceived1st = false
                data[0] = mReceived1stByte[0]
                --n_to_read
                ++o_to_read
            }

            if( n_to_read > 0 ) {
                mIStream?.run { n_read = read( data, o_to_read, n_to_read ) }
            }

            return n_read + o_to_read

        } catch( e : Exception ){

            return -1
        }
    }

    override fun close()
    {
        mLock.withLock {

            if( isAlive_inner() == false )
            {
                return
            }

            Handler(mHThreadMisc.looper).post {

                mSocket.close()

                mIStream?.close()

            }
        }

        // 受信スレッドの終了を待つ
        mFlag.wait()
    }

    private fun isAlive_inner() : Boolean
    {
        if( mSocket.isConnected() && !mSocket.isClosed() )
        {
            mIStream?.run { return true }
        }

        return false
    }

    override fun isAlive() : Boolean
    {
        mLock.withLock {

            return isAlive_inner()
        }
    }

}
