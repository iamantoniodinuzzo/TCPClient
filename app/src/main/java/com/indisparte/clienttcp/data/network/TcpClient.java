package com.indisparte.clienttcp.data.network;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.indisparte.clienttcp.BuildConfig;
import com.indisparte.clienttcp.UserPreferenceManager;
import com.indisparte.clienttcp.data.model.Pothole;
import com.indisparte.clienttcp.util.ServerCommand;
import com.indisparte.clienttcp.util.ServerCommand.CommandType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Antonio Di Nuzzo (Indisparte)
 */
public class TcpClient {
    private static final String TAG = TcpClient.class.getSimpleName();
    public static final int SO_TIMEOUT = 6000;
    public static final double DEFAULT_THRESHOLD = 20.0;
    private static TcpClient instance = null;
    private static final String HOST_NAME = BuildConfig.SERVER_ADDRESS;
    private static final int HOST_PORT = Integer.parseInt(BuildConfig.SERVER_PORT);
    private Socket mSocket;
    private UserPreferenceManager preferenceManager;
    private BufferedReader mReader;
    private OutputStream mStream;


    private TcpClient() {
        preferenceManager = UserPreferenceManager.getInstance();
    }

    public static TcpClient getInstance() {
        if (instance == null)
            instance = new TcpClient();
        return instance;
    }

    public void openConnection() throws IOException {
        mSocket = new Socket(HOST_NAME, HOST_PORT);
        mSocket.setSoTimeout(SO_TIMEOUT);
        mReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
        mStream = mSocket.getOutputStream();
    }

    public void closeConnection() throws IOException {
        mSocket.close();
    }

    public boolean isOpen() {
        return mSocket != null && !mSocket.isClosed();
    }

    /**
     * Write on the socket
     *
     * @param command {@link ServerCommand} that describe this type of write
     * @throws IOException
     */
    private void write(@NonNull ServerCommand command) throws IOException {
        synchronized (mStream) {
            final String complete_msg = command.getFormattedRequest();
            mStream.write((complete_msg + "\n").getBytes());
            mStream.flush();
            Log.d(TAG, "write: " + complete_msg);
        }
    }

    private String readLine() throws IOException {
        return readLine(0);
    }

    private String readLine(int timeout) throws IOException {
        synchronized (mReader) {
            int prevTimeout = mSocket.getSoTimeout();
            try {
                Log.d(TAG, "Start readLine");
                mSocket.setSoTimeout(timeout);
                final String msg = mReader.readLine();
                if (msg != null) {
                    mSocket.setSoTimeout(prevTimeout);
                    Log.d(TAG, "Received: " + msg);
                    return msg;
                }

            } catch (SocketTimeoutException e) {
                Log.e(TAG, "ReadLine Timeout: " + e.getMessage());
                mSocket.setSoTimeout(prevTimeout);
            }
        }
        return null;
    }

    public List<Pothole> getAllPotholes() throws IOException {
        String result;
        List<Pothole> resultList = new ArrayList<>();

        write(new ServerCommand(ServerCommand.CommandType.HOLE_LIST));
        while ((result = readLine(SO_TIMEOUT)) != null) {
            String[] tokens = result.split(";");
            resultList.add(0, new Pothole(Double.valueOf(tokens[0]), Double.valueOf(tokens[1]), Double.valueOf(tokens[2])));

        }
        return resultList;
    }

    private List<Pothole> getAllPotholes(int from, int to, String name) throws IOException {

        String msg = from + " " + to + " [" + name + "]";
        ArrayList<Pothole> list = new ArrayList<>();

        write(new ServerCommand(CommandType.HOLE_LIST));

        int counter = Integer.parseInt(readLine(5 * 1000).substring(2).trim());

        String pothole;
        int i = 0;
        while (i < counter && (pothole = readLine(5 * 1000)) != null) {
            Log.d(TAG, "Pothole: " + pothole);
            //TODO get pothole details
           /* Scanner potholeScanner = new Scanner(pothole.substring(1));

            long id = potholeScanner.nextLong();
            long usersCount = potholeScanner.nextLong();
            int roomColor = potholeScanner.nextInt();
            String potholeName = stringInside(pothole, "[", "]");

            Room r = new Room(potholeName, id, usersCount, roomColor);
            list.add(r);
            Log.d(TAG, "Room Added: " + r);
            ++i;*/
        }
        return list;
    }

    public List<Pothole> getAllPotholesByRange(int range, double latitude, double longitude) throws IOException {
        String result ;
        List<Pothole> resultList = new ArrayList<>();

        write(new ServerCommand(CommandType.HOLE_LIST_BY_RANGE,
                        String.valueOf(latitude),
                        String.valueOf(longitude),
                        String.valueOf(range)
                )
        );

        if ((result = readLine(SO_TIMEOUT)) != null) {
            Log.d(TAG, "getAllPotholesByRange: "+result);
            // TODO: 14/12/2022 Convertire la stringa json in un array di potholes 
            //String[] tokens = result.split(";");
            //resultList.add(0, new Pothole(Double.valueOf(tokens[0]), Double.valueOf(tokens[1]), Double.valueOf(tokens[2])));
        }

        return resultList;
    }

    public Double getThreshold() throws IOException {
        double threshold = DEFAULT_THRESHOLD;
        String result;
        write(new ServerCommand(CommandType.THRESHOLD));

        if ((result = readLine(SO_TIMEOUT)) != null) {
            threshold = Double.parseDouble(result);
        }

        return threshold;
    }

    public void addPothole(@NonNull Pothole pothole) throws IOException {
        write(new ServerCommand(CommandType.NEW_HOLE,
                String.valueOf(pothole.getLatitude()),
                String.valueOf(pothole.getLongitude()),
                String.valueOf(pothole.getVariation()))
        );
    }

    public void setUsername(@NonNull String username) throws IOException {
        write(new ServerCommand(CommandType.SET_USERNAME, username));
    }

    private String stringInside(String s, String left, String right) {
        Log.d(TAG, "String to split: " + s);
        if (s == null || s.length() < 3 || !s.contains(left) || !s.contains(right)) return "???";
        else return s.split(Pattern.quote(left))[1].split(Pattern.quote(right))[0];
    }
}
