package com.indisparte.clienttcp.data.network;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.indisparte.clienttcp.BuildConfig;
import com.indisparte.clienttcp.util.UserPreferenceManager;
import com.indisparte.clienttcp.data.model.Pothole;
import com.indisparte.clienttcp.util.ServerCommand;
import com.indisparte.clienttcp.util.ServerCommand.CommandType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

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


    public List<Pothole> getAllPotholesByRange(int range, double latitude, double longitude) throws IOException {
        String result;
        List<Pothole> potholes = new ArrayList<>();

        write(new ServerCommand(CommandType.HOLE_LIST_BY_RANGE,
                        String.valueOf(latitude),
                        String.valueOf(longitude),
                        String.valueOf(range)
                )
        );

        if ((result = readLine(SO_TIMEOUT)) != null) {
            Log.d(TAG, "getAllPotholesByRange: " + result);
            //Converting jsonData string into JSON object
            try {
                JSONObject jsonObject = new JSONObject(result);
                //Getting potholes JSON array from the JSON object
                JSONArray jsonArray = jsonObject.getJSONArray("potholes");
                //Iterating JSON array
                for (int i = 0; i < jsonArray.length(); i++) {
                    //get each value in string format: user,lat,lng,var
                    String jsonElem = jsonArray.getString(i);
                    Log.d(TAG, "getAllPotholesByRange, jsonElem: "+jsonElem);
                    Pothole newPothole = new Gson().fromJson(String.valueOf(jsonElem), Pothole.class);
                    Log.d(TAG, "getAllPotholesByRange, new pothole: "+newPothole);
                    potholes.add(newPothole);

                }
            } catch (JSONException e) {
                Log.e(TAG, "getAllPotholesByRange: " + e.getMessage());
            }
        }

        return potholes;
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
                String.valueOf(pothole.getLat()),
                String.valueOf(pothole.getLon()),
                String.valueOf(pothole.getVar()))
        );
    }

    public void setUsername(@NonNull String username) throws IOException {
        write(new ServerCommand(CommandType.SET_USERNAME, username));
    }

}
