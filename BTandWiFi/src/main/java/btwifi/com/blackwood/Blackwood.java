package btwifi.com.blackwood;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Created by konduri on 6/14/2016.
 */

/**
 * Created by Pavan Konduri(email:pavan.k.konduri@gmail.com)
 * Blackwood is a SDK for android, one can use it to communicate with other android devices through
 * Bluetooth or WiFi if connected on same network.
 * Note: This is a java nested class so all the inner classes can be accessed only through the
 * Blackwood object
 */
public class Blackwood {
    public static final boolean BLUETOOTH=true;
    public static final boolean WIFI=false;
    private String UUID="38835d1c-2b6f-4bd8-b1cd-7dfc1afe2ea8";
    private boolean connectionType;
    private int PORT=2369;

    /**
     * Created by Pavan Konduri(email:pavan.k.konduri@gmail.com)
     * Blackwood is a SDK for android, one can use it to communicate with other android devices through
     * Bluetooth or WiFi if connected on same network.
     * Note: This is a java nested class so all the inner classes can be accessed only through the
     * Blackwood object
     *
     * @param uuid UUID uuid is used to uniquely identify the app over the network
     * @param connectionType boolean connectionType is used to select the way of communication
     *                       going to be used(BLUETOOTH,WIFI)
     */
    public Blackwood(UUID uuid,boolean connectionType) {
        this.UUID = uuid.toString();
        this.connectionType=connectionType;
    }

    /**
     *This class manages connection like starting server, accepting connections and attempting client side connections
     */
    public class ManageConnection{
        private final java.util.UUID GAME_UUID= java.util.UUID.fromString(UUID);
        private final String NAME="game";
        private Handler handler;
        private Context context;
        private ServerSocket serverSocket;
        private Socket socket;
        private int state=Constants.STATE_NONE;
        private Thread serverThread;
        private CLientThread cLientThread;
        private BluetoothAdapter mBluetoothAdapter;
        private BluetoothServerSocket mServerSocket;
        private ServerThreadBT serverThreadBT;
        private ClientAttemptThread clientAttemptThread;
        private BluetoothDevice mDevice;
        private BluetoothSocket msocket;

        /**
         * This class manages connection like starting server, accepting connections and attempting client side connections
         * @param handler
         * @param context
         */
        public ManageConnection(Handler handler, Context context) {
            this.handler = handler;
            this.context = context;
            if (connectionType==BLUETOOTH)
                mBluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
        }

        /**
         * starts a server that listens for the client connection attempts, accepts connections.
         * On successful establishment of connections the handler obtains a message Constants.CONNECTED
         * containing the connected Socket object
         */
        public void startServer(){
            if(connectionType==WIFI){
                serverThread=new Thread(new ServerThread());
                serverThread.start();
            }
            else
            if (connectionType==BLUETOOTH){
                serverThreadBT=new ServerThreadBT();
                serverThreadBT.start();
            }
        }

        /**
         * Client side attempt for connecting to server.
         * On successful establishment of connections the handler obtains a message Constants.CONNECTED
         * @param address Attempts a connection to connect to the device of InetAddress address and specified port
         * @param port
         */
        public void clientConnect(InetAddress address,int port){
            if (connectionType==WIFI){
                cLientThread=new CLientThread(address,port);
                cLientThread.start();
            }
        }

        /**
         * Client side attempt for connecting to server.
         * On successful establishment of connections the handler obtains a message Constants.CONNECTED
         * @param device Attempts a connection to connect to the BluetoothDevice device
         */
        public synchronized void clientConnect(BluetoothDevice device){
            if (connectionType==BLUETOOTH){
                if(clientAttemptThread!=null){
                    clientTearDown();
                    clientAttemptThread=null;
                }

                clientAttemptThread=new ClientAttemptThread(device);
                clientAttemptThread.start();
            }
        }

        /**
         * Stops the server and closes the serverSocket
         */
        public void serverTearDown(){
            if (connectionType==WIFI){
                serverThread.interrupt();
                try {
                    serverSocket.close();
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
            else
            if (connectionType==BLUETOOTH){
                serverThreadBT.interrupt();
                try {
                    mServerSocket.close();
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }

        }

        /**
         * closes the socket
         */
        public void clientTearDown(){
            if (connectionType==WIFI){
                cLientThread=null;
                try {
                    socket.close();
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
            else
            if (connectionType==BLUETOOTH){
                try {
                    msocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }

        /**
         * Stops the server and closes the serverSocket and closes the socket
         */
        public void destroy(){
            serverTearDown();
            clientTearDown();
        }

        private synchronized void setState (int state){
            this.state=state;
            handler.obtainMessage(Constants.STATUS,state).sendToTarget();
        }

        private Socket getSocket(){
            return socket;
        }

        private void setSocket(Socket socket){
            if(this.socket!=null){
                if (this.socket.isConnected()){
                    try {
                        this.socket.close();
                    }
                    catch (IOException e){
                        e.printStackTrace();
                    }
                }
            }
            this.socket=socket;
        }

        private class ServerThreadBT extends Thread{
            private ServerThreadBT(){
                BluetoothServerSocket temp=null;

                try {
                    temp=mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME,GAME_UUID);

                }
                catch (IOException e)
                {
                    //Toast.makeText(context, ""+e, Toast.LENGTH_SHORT).show();
                }
                mServerSocket=temp;
            }

            public void run(){
                setState(Constants.CONNECTING);
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                BluetoothSocket socket=null;
                while (state!=Constants.CONNECTED && !Thread.currentThread().isInterrupted())
                {
                    try {
                        try {
                            socket=mServerSocket.accept();

                        }
                        catch (IOException e){

//                    Toast.makeText(context, ""+e, Toast.LENGTH_SHORT).show();
                        }
                    }
                    catch (NullPointerException f){return;}


                    if(socket!=null){
                        synchronized (ManageConnection.this){
                            switch (state){
                                case Constants.STATE_LISTEN:
                                case Constants.CONNECTING:
                                    state=Constants.CONNECTED;
                                    handler.obtainMessage(Constants.CONNECTED,socket).sendToTarget();
                                    break;
                                case Constants.STATE_NONE:
                                case Constants.CONNECTED:
                                    try {
                                        socket.close();
                                    } catch (IOException e) {
                                        //  Toast.makeText(context, ""+e, Toast.LENGTH_SHORT).show();
                                    }
                                    break;
                            }
                        }
                    }
                }
                serverTearDown();
                return;
            }
        }

        private class ClientAttemptThread extends Thread{


            private ClientAttemptThread(BluetoothDevice device) {
                mDevice=device;
                BluetoothSocket temp=null;
                try {
                    temp=mDevice.createRfcommSocketToServiceRecord(GAME_UUID);
                }
                catch (IOException e){
                    //  Toast.makeText(context, ""+e, Toast.LENGTH_SHORT).show();
                }
                msocket=temp;
            }

            public void run(){
                //android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                if(msocket!=null){
                    setState(Constants.CONNECTING);
                    if(mBluetoothAdapter.isDiscovering())
                        mBluetoothAdapter.cancelDiscovery();
                    try {
                        msocket.connect();
                    }
                    catch (IOException e){
                        handler.obtainMessage(Constants.CONNECTION_FAILED).sendToTarget();
                        //Thread.currentThread().interrupt();
                        return;
                    }
                    state=Constants.CONNECTED;
                    handler.obtainMessage(Constants.CONNECTED,msocket).sendToTarget();
                    return;
                }}
        }

        private class ServerThread implements Runnable{
            @Override
            public void run() {
                try{
                    serverSocket=new ServerSocket(PORT);
                    while (!Thread.currentThread().isInterrupted()){
                        setSocket(serverSocket.accept());
                        if(socket!=null)
                        {
                            state=Constants.CONNECTED;
                            handler.obtainMessage(Constants.CONNECTED,getSocket()).sendToTarget();
                            //Send Message
                        }

                    }
                }
                catch (IOException e){
                    Log.e("IOexception",e.toString());
                }

            }


        }

        private class CLientThread extends Thread{
            InetAddress address;
            int port;
            public CLientThread(InetAddress address,int port) {
                this.address=address;
                this.port=port;
            }
            public void run() {
                try{
                    setSocket(new Socket(address,port));
                    state=Constants.CONNECTED;
                    handler.obtainMessage(Constants.CONNECTED,getSocket()).sendToTarget();
                    //handler message
                }
                catch (IOException e){
                    e.printStackTrace();
                }

            }
        }
    }

    /**
     * After successful connection, this class is used to communicate through the socket
     */
    public class ReadSend{
        private Context context;
        private Handler handler;
        private ReadThread readThread;
        private WriteThread writeThread;
        private ReadThreadBT readThreadBT;
        private WriteThreadBT writeThreadBT;
        private Socket socket;
        private BluetoothSocket socketBT;

        /**
         * After successful connection this class is used to communicate through the socket
         * @param context
         * @param handler
         * @param socket connected socket as a parameter
         */
        public ReadSend(Context context, Handler handler, Socket socket) {
            if (connectionType==WIFI){
                this.context = context;
                this.handler = handler;
                this.socket = socket;
            }
        }

        /**
         * After successful connection this class is used to communicate through the socket
         * @param context
         * @param handler
         * @param socketBT connected BluetoothSocket as a parameter
         */
        public ReadSend(Context context, Handler handler, BluetoothSocket socketBT) {
            if (connectionType==BLUETOOTH){
                this.context = context;
                this.handler = handler;
                this.socketBT = socketBT;
            }
        }

        /**
         * Starts a thread that reads from the socket through InputStream class
         * If data is read handler gets a message Constants.DATA_READ having the data as a byte array
         * of maximum length 1024bytes.
         * If disconnected handler gets a message Constants.DISCONNECTED
         */
        public void startReading(){
            if (connectionType==WIFI){
                readThread=new ReadThread();
                readThread.start();
            }
            else
            if (connectionType==BLUETOOTH){
                readThreadBT=new ReadThreadBT();
                readThreadBT.start();
            }

        }

        /**
         * Starts a thread that writes to the socket through OutputStream class.
         * Note: It is better to send the data as packets of byte array of size 1024
         * After successful sending of the data the handler obtains a message Constants.DATA_SENT containing the byte array
         * @param buffer buffer array to be send through the socket
         */
        public void startWriting(byte[] buffer){
            if (connectionType==WIFI){
                writeThread=new WriteThread(buffer);
                writeThread.start();
            }
            else
            if (connectionType==BLUETOOTH){
                writeThreadBT=new WriteThreadBT(buffer);
                writeThreadBT.start();
            }

        }

        /**
         * This method stops the reading thread of this class
         */
        public void stopReading(){
            if (connectionType==WIFI){
                if (readThread!=null)
                    readThread.interrupt();
            }
            else
            if (connectionType==BLUETOOTH){
                if (readThreadBT!=null)
                    readThreadBT.interrupt();
            }

        }

        /**
         * This method closes the socket that was given as a parameter while declaring this class
         */
        public void closeSocket(){
            if (connectionType==WIFI){
                try {
                    readThread.getSocket().close();
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
            else
            if (connectionType==BLUETOOTH){
                try {
                    readThreadBT.getSocket().close();
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }

        }

        private class WriteThread extends Thread{

            byte[] buffer;
            OutputStream outputStream;
            private WriteThread(byte[] buffer) {

                this.buffer=buffer;
                try {
                    outputStream=socket.getOutputStream();
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }

            public void run() {

                try {
                    outputStream.write(buffer);
                    handler.obtainMessage(Constants.DATA_SENT,buffer).sendToTarget();
                }
                catch (IOException e){
                    e.printStackTrace();
                    return;
                }

            }
        }
        private class ReadThread extends Thread{

            InputStream inputStream;

            public void run() {
                byte[] buffer=new byte[1024];
                int length;
                try {
                    inputStream=socket.getInputStream();

                }
                catch (IOException e){
                    e.printStackTrace();return;

                }
                while (!Thread.currentThread().isInterrupted()){
                    try {
                        length=inputStream.read(buffer);
                    }
                    catch (IOException e){
                        if (!Thread.currentThread().isInterrupted())
                            handler.obtainMessage(Constants.DISCONNECTED).sendToTarget();
                        return;
                    }
                    catch (Exception e){
                        if (!Thread.currentThread().isInterrupted())
                            handler.obtainMessage(Constants.DISCONNECTED).sendToTarget();
                        return;
                    }
                    if (!Thread.currentThread().isInterrupted())
                        handler.obtainMessage(Constants.DATA_READ,length,-1,buffer).sendToTarget();


                }

            }
            public Socket getSocket(){return socket;}

        }
        private class WriteThreadBT extends Thread{

            byte[] buffer;
            OutputStream outputStream;
            private WriteThreadBT(byte[] buffer) {

                this.buffer=buffer;
                try {
                    outputStream=socketBT.getOutputStream();
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }

            public void run() {

                try {
                    outputStream.write(buffer);
                    handler.obtainMessage(Constants.DATA_SENT,buffer).sendToTarget();
                }
                catch (IOException e){
                    e.printStackTrace();
                    return;
                }

            }
        }
        private class ReadThreadBT extends Thread{

            InputStream inputStream;

            public void run() {
                byte[] buffer=new byte[1024];
                int length;
                try {
                    inputStream=socketBT.getInputStream();

                }
                catch (IOException e){
                    e.printStackTrace();return;

                }
                while (!Thread.currentThread().isInterrupted()){
                    try {
                        length=inputStream.read(buffer);
                    }
                    catch (IOException e){
                        if (!Thread.currentThread().isInterrupted())
                            handler.obtainMessage(Constants.DISCONNECTED).sendToTarget();
                        return;
                    }
                    catch (Exception e){
                        if (!Thread.currentThread().isInterrupted())
                            handler.obtainMessage(Constants.DISCONNECTED).sendToTarget();
                        return;
                    }
                    if (!Thread.currentThread().isInterrupted())
                        handler.obtainMessage(Constants.DATA_READ,length,-1,buffer).sendToTarget();


                }
            }
            public BluetoothSocket getSocket(){
                return socketBT;
            }

        }
    }

    /**
     * This class is used to register a device in wifi network, discover devices with the same app UUID and getting devices
     * tearDown() has to be executed compulsory on app stopped below API 19 and app destroyed for above API 19
     */
    public class WiFibasic {

        public final String APP_NAME=UUID;
        private final String SERVICE_TYPE="_http._tcp.";
        public boolean registered =false,discovery=false;
        Context context;
        Handler handler;
        private NsdManager nsdManager;
        private NsdManager.DiscoveryListener discoveryListener;
        private NsdManager.RegistrationListener registrationListener;
        private String serviceName=APP_NAME;
        private Set<NsdServiceInfo> service;
        private Set<String> sevice_address;
        private boolean discovering=false;

        /**
         * This class is used to register a device in wifi network, discover devices with the same app UUID and getting devices
         * tearDown() has to be executed compulsory on app stopped below API 19 and app destroyed for above API 19
         * @param context
         * @param handler
         * @param serviceName serviceName contains name of the device that gets registered in the wifi network
         */
        public WiFibasic(Context context, Handler handler, String serviceName) {
            this.context=context;
            this.handler=handler;
            this.serviceName=this.serviceName+serviceName;
            nsdManager=(NsdManager) this.context.getSystemService(Context.NSD_SERVICE);
            service=new HashSet<>(10);
            sevice_address=new HashSet<>(10);
        }

        /**
         * This method initializes the registration listener of the device with the given ServiceName in the connected wifi network
         * and initializes discovery listener of the services with the same app UUID
         */

        public void startServices(){
            initializeRegistrationListener();
            initializeDiscoveryListener();
        }

        /**
         * Initializes registration listener of the device with the given ServiceName in the connected wifi network
         */
        public void initializeRegistrationListener(){
            registrationListener=new NsdManager.RegistrationListener() {
                @Override
                public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.e("Error at Registration",""+errorCode);
                    registered=false;
                }

                @Override
                public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.e("Error at Registration",""+errorCode);
                    //registered=false;
                }

                @Override
                public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                    serviceName=serviceInfo.getServiceName();
                    registered=true;
                }

                @Override
                public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                    registered=false;
                }
            };
        }

        /**
         * Initializes discovery listener of the services with the same app UUID
         */

        public void initializeDiscoveryListener(){

            discoveryListener=new NsdManager.DiscoveryListener() {
                @Override
                public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                    Log.e("Error at Discovery",""+errorCode);
                    discovery=false;
                    nsdManager.stopServiceDiscovery(this);
                }

                @Override
                public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                    Log.e("Error at Discovery",""+errorCode);
                    nsdManager.stopServiceDiscovery(this);
                    //discovery=false;
                    return;}

                @Override
                public void onDiscoveryStarted(String serviceType) {
                    Log.e("Message at Discovery","Discovery started "+serviceType);
                    discovery=true;
                }

                @Override
                public void onDiscoveryStopped(String serviceType) {
                    Log.e("Message at Discovery","Discovery Stopped "+serviceType);
                    discovery=false;
                }

                @Override
                public void onServiceFound(NsdServiceInfo serviceInfo) {
                    if(!serviceInfo.getServiceType().equals(SERVICE_TYPE))
                        Log.e("Message","Unknown service type");
                    else {
                        if (serviceInfo.getServiceName().equals(serviceName))
                            Log.e("Message","Found Same device");
                        else
                        if (serviceInfo.getServiceName().contains(APP_NAME))
                        {
                            nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                                @Override
                                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {

                                }

                                @Override
                                public void onServiceResolved(NsdServiceInfo serviceInfo) {
                                    if (serviceInfo.getServiceName().equals(serviceName))
                                    {
                                        Log.e("Same iP",serviceInfo.toString());
                                        return;
                                    }
                                    int some_random=sevice_address.size();
                                    if (sevice_address.add(serviceInfo.getHost().toString()))
                                    {
                                        service.add(serviceInfo);
                                        handler.obtainMessage(Constants.DEVICE_DISCOVERED,serviceInfo.getServiceName().replace(APP_NAME,"")+"\n"+serviceInfo.getHost()).sendToTarget();

                                    }

                                }
                            });
                        }
                    }


                }

                @Override
                public void onServiceLost(NsdServiceInfo serviceInfo) {
                    Log.e("Message at Service lost",""+serviceInfo.toString());
                    if(service!=null)
                        if(service.contains(serviceInfo))
                        {
                            service.remove(serviceInfo);
                            handler.obtainMessage(2,serviceInfo.getServiceName()+"\n"+serviceInfo.getHost()).sendToTarget();
                        }
                }
            };
        }

        /**
         * Registers Service with the specified port.
         * initializeRegistrationListener() is to be called before registering the port
         * @param port
         */

        public void registerService (int port){
            NsdServiceInfo nsdServiceInfo=new NsdServiceInfo();
            nsdServiceInfo.setServiceName(serviceName);
            nsdServiceInfo.setServiceType(SERVICE_TYPE);
            nsdServiceInfo.setPort(port);
            PORT=port;
            nsdManager.registerService(nsdServiceInfo,NsdManager.PROTOCOL_DNS_SD,registrationListener);
        }

        /**
         * Discover services with the same app UUID.
         * On discovery of a new devices handler obtains a message Constants.DEVICE_DISCOVERED
         * and the details of the device in a String as object
         * initializeDiscoveryListener() is to be called before starting discovery
         */

        public void discoverService(){
            service=new HashSet<>(10);
            sevice_address=new HashSet<>(10);
            nsdManager.discoverServices(SERVICE_TYPE,NsdManager.PROTOCOL_DNS_SD,discoveryListener);
            discovering=true;
        }

        /**
         * Checks and returns whether a device is discovering
         * true if discovering
         * false if not discovering
         * @return
         */
        public boolean isDiscovering(){return discovering;}

        /**
         * stops discovery if discovering
         */
        public void stopDiscovery(){
            if (isDiscovering())
                nsdManager.stopServiceDiscovery(discoveryListener);
            discovering=false;
        }

        /**
         *
         * @return Set<NsdServiceInfo> returns a set having NsdServiceInfo objects that are discovered
         */
        public Set<NsdServiceInfo> getService(){return service;}

        /**
         * Unregisters the registrationListener
         */
        public void tearDown(){nsdManager.unregisterService(registrationListener);}


    }

    /**
     * This class is used to get available bluetooth devices and paired devices
     */
    public class BTDevicesList {

        private BluetoothAdapter mBluetoothAdapter;
        private ArrayAdapter <String> scannedDevices,bondedDevices;
        private Context context;
        private String newName="";
        private int count=0;
        private Set<BluetoothDevice> bondedDevice;
        private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    Log.e("Message","Check");
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device.getBondState() != BluetoothDevice.BOND_BONDED)
                    {
                        String deviceAlias = device.getName();
                        try {
                            Method method = device.getClass().getMethod("getAliasName");
                            if(method != null) {
                                deviceAlias = (String)method.invoke(device);
                            }
                        } catch (NoSuchMethodException e) {
                            e.printStackTrace();
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                        scannedDevices.add(deviceAlias+" \n("+getDeviceClass(device.getBluetoothClass())+") "+"\n"+device.getAddress());
                    }
                } else {
                    if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                        if(bondedDevice.size()!=0){
                            BluetoothDevice device=(BluetoothDevice) bondedDevice.toArray()[count++];
                            device.fetchUuidsWithSdp();
                            IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_UUID);
                            context.registerReceiver(mReceiver,filter);
                        }

                        if (scannedDevices.isEmpty())
                        {
                            String noDevices = "None Found";
                            scannedDevices.add(noDevices);
                        }
                        //scannedDevices.add("Scan Completed");
                    }
                    else {
                        if (BluetoothDevice.ACTION_UUID.equals(action)){
                            boolean check=false;
                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            Parcelable[] parcelable=intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                            if (parcelable!=null){
                                for (Parcelable p:parcelable)
                                    if (p.toString().compareTo(UUID)==0){
                                        check=true;
                                        break;
                                    }
                            }
                            else
                                Log.e(device.getName(),"UUID is NULL");
                            if (check)
                            {
                                Log.e(device.getName(),"Have the game");
                                String deviceAlias = device.getName();
                                try {
                                    Method method = device.getClass().getMethod("getAliasName");
                                    if(method != null) {
                                        deviceAlias = (String)method.invoke(device);
                                    }
                                } catch (NoSuchMethodException e) {
                                    e.printStackTrace();
                                } catch (InvocationTargetException e) {
                                    e.printStackTrace();
                                } catch (IllegalAccessException e) {
                                    e.printStackTrace();
                                }
                                bondedDevices.remove(deviceAlias+" \n("+getDeviceClass(device.getBluetoothClass())+") "+"\n"+device.getAddress());
                                bondedDevices.remove(deviceAlias+" (Have Game)\n("+getDeviceClass(device.getBluetoothClass())+") "+"\n"+device.getAddress());
                                bondedDevices.insert(deviceAlias+" (Have Game)\n("+getDeviceClass(device.getBluetoothClass())+") "+"\n"+device.getAddress(),count-1);
                            }
                            else
                                Log.e(device.getName(),"Not having game");
                            if (bondedDevice.size()>count)
                            {
                                BluetoothDevice device2=(BluetoothDevice) bondedDevice.toArray()[count++];
                                device2.fetchUuidsWithSdp();
                                IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_UUID);
                                context.registerReceiver(mReceiver,filter);
                            }
                        }
                    }
                }
            }
        };


        /**
         *This class is used to get available bluetooth devices and paired devices
         * @param context Context of the activity is passed as a parameter for toasting messages
         */
        public BTDevicesList(Context context) {
            BluetoothManager bluetoothManager=(BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter=bluetoothManager.getAdapter();
            this.context=context;
            scannedDevices= new ArrayAdapter<String>(this.context,R.layout.device_name);
            bondedDevices=new ArrayAdapter<String>(this.context,R.layout.device_name);
        }

        /**
         * Returns an ArrayAdapter<String> containing scanned Devices around it
         * (This does not contain paired devices)
         * Note: If this method is used the activity on stop must execute unregisterReciever()
         * @return ArrayAdapter<String> Scanned Devices
         */
        public ArrayAdapter<String> getScannedDevices(){
            if (mBluetoothAdapter == null) {
                CharSequence text = "Device does not support Bluetooth.";
                int duration = Toast.LENGTH_SHORT;
                Toast toast = Toast.makeText(context, text, duration);
                toast.show();// Device does not support Bluetooth
            }

            else{
                if (!mBluetoothAdapter.isEnabled()) {
                    CharSequence text = "Please switch on Bluetooth";
                    int duration = Toast.LENGTH_SHORT;
                    Toast toast = Toast.makeText(context, text, duration);
                    toast.show();
                }
                else {

                    IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                    context.registerReceiver(mReceiver, filter);
                    filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                    context.registerReceiver(mReceiver, filter);
                    if (mBluetoothAdapter.isDiscovering())
                        mBluetoothAdapter.cancelDiscovery();
                    mBluetoothAdapter.startDiscovery();
                }

            }

            return scannedDevices;
        }

        /**
         * This method unregisters the receiver that has been dynamically registered while executing getScannedDevices() method.
         * This method has to be used only when getScannedDevices() is executed.
         */
        public void unregisterReciever(){
            try{context.unregisterReceiver(mReceiver);}catch (Exception e){}
        }

        /**
         * Returns an ArrayAdapter<String> containing paired Devices and device type
         * @return ArrayAdapter<String> Paired Devices
         */
        public ArrayAdapter<String> getBondedDevices(){
            if (mBluetoothAdapter == null) {
                CharSequence text = "Device does not support Bluetooth.";
                int duration = Toast.LENGTH_SHORT;
                Toast toast = Toast.makeText(context, text, duration);
                toast.show();// Device does not support Bluetooth
            }

            else{
                if (!mBluetoothAdapter.isEnabled()) {
                    CharSequence text = "Please switch on Bluetooth";
                    int duration = Toast.LENGTH_SHORT;
                    Toast toast = Toast.makeText(context, text, duration);
                    toast.show();
                }
                else {
                    bondedDevice =mBluetoothAdapter.getBondedDevices();
                    if(bondedDevice.size()==0)
                    {
                        bondedDevices.add("No paired devices");
                    }
                    else{
                        for(BluetoothDevice device:bondedDevice)
                        {
                            String deviceAlias = device.getName();
                            try {
                                Method method = device.getClass().getMethod("getAliasName");
                                if(method != null) {
                                    deviceAlias = (String)method.invoke(device);
                                }
                            } catch (NoSuchMethodException e) {
                                e.printStackTrace();
                            } catch (InvocationTargetException e) {
                                e.printStackTrace();
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            }
                            bondedDevices.add(deviceAlias+" \n("+getDeviceClass(device.getBluetoothClass())+") "+"\n"+device.getAddress());
                        }


                    }
                }
            }

            return bondedDevices;
        }

        /**
         * Starts an activity requesting for enabling discovery for 300 seconds.
         */
        public void enableDiscovery(){
            Intent discoverableIntent = new
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            context.startActivity(discoverableIntent);
        }

        /**
         * Renaming paired device alias name is possible using this method
         * @param arrayAdapter
         * @return AdapterView.OnOnItemLongClickListener
         */

        public AdapterView.OnItemLongClickListener getLongClick (ArrayAdapter<String> arrayAdapter){
            final ArrayAdapter<String> pairedDevicesArrayAdapter=arrayAdapter;
            AdapterView.OnItemLongClickListener listener=new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                    String info = ((TextView) view).getText().toString();
                    String address = info.substring(info.length() - 17);
                    final BluetoothDevice device=mBluetoothAdapter.getRemoteDevice(address);
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("Rename Device");
                    final EditText input = new EditText(context);
                    builder.setView(input);
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            newName = input.getText().toString();
                            if (newName.compareTo("")!=0){
                                try {
                                    String deviceAlias = device.getName();
                                    try {
                                        Method method = device.getClass().getMethod("getAliasName");
                                        if(method != null) {
                                            deviceAlias = (String)method.invoke(device);
                                        }
                                    } catch (NoSuchMethodException e) {
                                        e.printStackTrace();
                                    } catch (InvocationTargetException e) {
                                        e.printStackTrace();
                                    } catch (IllegalAccessException e) {
                                        e.printStackTrace();
                                    }
                                    String item=pairedDevicesArrayAdapter.getItem(position);
                                    pairedDevicesArrayAdapter.remove(item);
                                    try {
                                        item=item.replace(deviceAlias,"");
                                    }
                                    catch (NullPointerException e){
                                        item=item.substring(4);
                                    }

                                    Method method = device.getClass().getMethod("setAlias", String.class);
                                    if(method != null) {
                                        method.invoke(device, newName);
                                    }
                                    pairedDevicesArrayAdapter.insert(newName+item,position);
                                } catch (NoSuchMethodException e) {
                                    e.printStackTrace();
                                } catch (InvocationTargetException e) {
                                    e.printStackTrace();
                                } catch (IllegalAccessException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });
                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });

                    builder.show();
                    return true;
                }
            };
            return listener;
        }

        /**
         * By supplying a BluetoothClass object , returns a String having type of device
         * @param bluetoothClass
         * @return String containing Device type
         */

        public String getDeviceClass(BluetoothClass bluetoothClass){
            int classCode=bluetoothClass.getMajorDeviceClass();
            switch (classCode){
                case BluetoothClass.Device.Major.AUDIO_VIDEO:
                    return "Audio And Video device";
                case BluetoothClass.Device.Major.COMPUTER:
                    return "Computer";
                case BluetoothClass.Device.Major.HEALTH:
                    return "Health Monitoring device";
                case BluetoothClass.Device.Major.IMAGING:
                    return "Imaging device";
                case BluetoothClass.Device.Major.MISC:
                    return "Miscellaneous device";
                case BluetoothClass.Device.Major.NETWORKING:
                    return "Networking device";
                case BluetoothClass.Device.Major.PERIPHERAL:
                    return "Peripheral";
                case BluetoothClass.Device.Major.PHONE:
                    return "Mobile";
                case BluetoothClass.Device.Major.TOY:
                    return "Toy";
                case BluetoothClass.Device.Major.WEARABLE:
                    return "Wearable";
                default:
                    return "Uncategorized";

            }
        }

        /**
         * This method returns BluetoothDevice object if MAC address of the device is supplied
         * @param address
         * @return BluetoothDevice of the given address
         */
        public BluetoothDevice getBluetoothDevice(String address){
            return mBluetoothAdapter.getRemoteDevice(address);
        }


    }

}
