package net.dhleong.acl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;

import net.dhleong.acl.net.BaseArtemisPacket;
import net.dhleong.acl.net.GameMessagePacket;
import net.dhleong.acl.net.PacketParser;
import net.dhleong.acl.net.setup.ReadyPacket;
import net.dhleong.acl.net.setup.ReadyPacket2;
import net.dhleong.acl.util.Util;

public class ThreadedArtemisNetworkInterface implements ArtemisNetworkInterface {
    
    private static class SenderThread extends Thread implements OnPacketListener {

        private final Socket mSkt;
        private final Queue<ArtemisPacket> mQueue = new ArrayDeque<ArtemisPacket>(128);
        private boolean mRunning = true;
        
        private ArtemisPacket mCurrentPacket = null;
        private final OutputStream mOutput;
        private final ThreadedArtemisNetworkInterface mInterface;
        
        private boolean mConnected = false;
        private OnConnectedListener mOnConnectedListener;
        private boolean mStarted;

        public SenderThread(final ThreadedArtemisNetworkInterface net, final Socket skt) throws IOException {
            mInterface = net;
            mSkt = skt;
            
            mOutput = new BufferedOutputStream(mSkt.getOutputStream());
        }
        
        public boolean offer(final ArtemisPacket pkt) {
            //System.out.println(">> sending: " + pkt);
            return mQueue.offer(pkt);
        }

        @Override
        public void run() {
            mStarted = true;
            
            while (mRunning) {
                
                try {
                    Thread.sleep(5);
                } catch (final InterruptedException e) {}
                
                if (!mConnected )
                    continue;
                
                if (mCurrentPacket == null && (mCurrentPacket = mQueue.poll()) == null) {
                    // empty queue; loop back to wait
                    continue;
                }
            
                try {
//                    System.out.print(">> ");
////                    mCurrentPacket.write(System.out);
//                    System.out.println();
                    if (DEBUG) System.out.println(">> " + mCurrentPacket);
                    
                    if (mCurrentPacket.write(mOutput)) {
                        mCurrentPacket = null;
                        mOutput.flush();
                    }
                } catch (final IOException e) {
                    if (mRunning) {
                        e.printStackTrace();
                        mInterface.errorCode = OnConnectedListener.ERROR_IO;
                    }
                    break;
                }
            }
            
            mConnected = false;
            mInterface.stop();
            
            // close the socket here; this will
            //  allow us to send any closing
            //  packets needed before shutting
            //  down the pipes
            try {
                mSkt.close();
            } catch (final IOException e) {}
            
            if (mOnConnectedListener != null) {
                mOnConnectedListener.onDisconnected(mInterface.errorCode);
    
                // not interested in listening anymore
                mOnConnectedListener = null;
            }
        }

        public void end() {
            mRunning = false;
        }

        @Override
        public void onPacket(final ArtemisPacket pkt) {
            if (pkt.getType() == 0xe548e74a) { // server version

                final BaseArtemisPacket base = (BaseArtemisPacket) pkt;
                final byte[] data = base.getData();
                final float version = PacketParser.getLendFloat(data, 4);
                
                if (Util.findInArray(ArtemisNetworkInterface.SUPPORTED_VERSIONS, version) == -1) {
                    System.err.println(String
                            .format("Unsupported Artemis server version (%f)", version));
                    if (mOnConnectedListener != null) {
                        mOnConnectedListener.onDisconnected(
                                OnConnectedListener.ERROR_VERSION);
                    }
                    
                    // go ahead and end the receive thread NOW
                    mInterface.errorCode = OnConnectedListener.ERROR_VERSION;
                    mInterface.mReceiveThread.end();
                    end();
                }
                
            } else if (pkt.getType() == 0x19c6e2d4) { // onConnect
                
                final boolean wasConnected = mConnected;
                
                mConnected = true;
                
                // send a couple of these to prime the server
                offer(new ReadyPacket2());
                offer(new ReadyPacket2());
                
                if (!wasConnected && mOnConnectedListener != null) 
                    mOnConnectedListener.onConnected();
                
            } else if (pkt instanceof GameMessagePacket) {
                if (((GameMessagePacket) pkt).isGameOver()) {
                    offer(new ReadyPacket());
                }
            }
        }
    }

    private static class ReceiverThread extends Thread {

        // use CopyOnWrite so we can clear listeners from 
        //  other threads w/o interfering with normal process
        List<OnPacketListener> mListeners = new CopyOnWriteArrayList<OnPacketListener>();
        
        private boolean mRunning = true;
        private final BufferedInputStream mInput;
        private final ThreadedArtemisNetworkInterface mInterface;
        private PacketParser mParser;
        private boolean mStarted;
        
        public ReceiverThread(final ThreadedArtemisNetworkInterface net, final Socket skt) throws IOException {
            mInterface = net;
            mInput = new BufferedInputStream(skt.getInputStream());
            mParser = new PacketParser();
        }

        @Override
        public void run() {
            mStarted = true;
            
            while (mRunning) {
//                try {
//                    Thread.sleep(5);
//                } catch (InterruptedException e) {}
                
//                try {
                    // not sure this is actually useful
//                    if (mInput.available() < 8) {
//                        // nothing, or not enough to at least 
//                        //  figure out what we need
//                        continue;
//                    }
                    
                try {
                    // read packet
                    final ArtemisPacket pkt = mParser.readPacket(mInput);

                    // only bother with non-null packets; else,
                    //  they are just keepalive (I guess) for 
                    //  indicating that the server aliveness
                    //  and perhaps calculating latency
                    if (pkt != null && mRunning) {
                        // notify listeners
                        for (final OnPacketListener listener : mListeners) {
                            listener.onPacket(pkt);
                        }
                    }
                } catch (final ArtemisPacketException e) {
                    // TODO ?
                    if (mRunning) {
                        System.err.println("Exitting due to parseException");
                        e.printStackTrace();
                        mInterface.errorCode = OnConnectedListener.ERROR_PARSE;
                    }
                    break;
                } catch (final IOException e) {
                    if (mRunning) {
                        System.err.println("Exitting due to IOException");
                        e.printStackTrace();
                        mInterface.errorCode = OnConnectedListener.ERROR_IO;
                    }
                    break;
                }
            }
            
            mInterface.stop();
        }
        
        public void end() {
            mRunning = false;
            
            // also, not interested in listening anymore
            synchronized(mListeners) {
                mListeners.clear();
            }
        }

        public void addOnPacketListener(final OnPacketListener listener) {
            mListeners.add(listener);
        }
    }
    
    private static final boolean DEBUG = false;

    private final ReceiverThread mReceiveThread;
    private final SenderThread mSendThread;
    
    /** Error code, for when we disconnect */
    private int errorCode = OnConnectedListener.ERROR_NONE;

    public ThreadedArtemisNetworkInterface(final String tgtIp, final int tgtPort) 
            throws UnknownHostException, IOException {
        final Socket skt = new Socket(tgtIp, tgtPort);
        skt.setKeepAlive(true);
        
        mSendThread = new SenderThread(this, skt);
        mReceiveThread = new ReceiverThread(this, skt);
        mReceiveThread.addOnPacketListener(mSendThread);
    }

    @Override
    public void addOnPacketListener(final OnPacketListener listener) {
        mReceiveThread.addOnPacketListener(listener);
    }
    
    public boolean isConnected() {
        return mSendThread.mConnected;
    }

    @Override
    public void send(final ArtemisPacket pkt) {
        mSendThread.offer(pkt);
    }
    
    @Override
    public void stop() {
        mReceiveThread.end();
        mSendThread.end();
    }
    
    @Override
    public void start() {
        if (!mReceiveThread.mStarted)
            mReceiveThread.start();
        if (!mSendThread.mStarted)
            mSendThread.start();
    }

    public void setOnConnectedListener(final OnConnectedListener listener) {
        mSendThread.mOnConnectedListener = listener;
    }
    
    public void setPacketParser(final PacketParser parser) {
        mReceiveThread.mParser = parser;
    }

}
