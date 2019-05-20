package com.example.inspiringapps.Controler;

import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import com.example.inspiringapps.DataBase.DataBase;
import com.example.inspiringapps.InteractionListener;
import com.example.inspiringapps.Model.Entry;
import com.example.inspiringapps.Model.Sequence;
import com.example.inspiringapps.Network.ApiInterface;
import com.example.inspiringapps.Network.ApiUtils;
import com.example.inspiringapps.R;
import com.example.inspiringapps.View.SequenceFragment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity implements InteractionListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String SEQUENCE_FRAGMENT = "sequenceFragment";

    ApiInterface apiInterface;
    ResponseBody response;
    DataBase db;
    FragmentManager fragmentManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Set up database
        db = new DataBase(this);
        db.clearTable();

        //Make network call
        apiInterface = ApiUtils.getFile();

        //Get the fragment manager
        fragmentManager = getSupportFragmentManager();

        //Display login fragment
        SequenceFragment sequenceFragment = new SequenceFragment();
        fragmentManager.beginTransaction().replace(R.id.root, sequenceFragment, SEQUENCE_FRAGMENT).commit();
    }


    @Override
    public void onDownloadButtonPress(boolean click) {

        if(click) {
            apiInterface.downloadFile().subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Observer<ResponseBody>() {
                @Override
                public void onSubscribe(Disposable d) {
                    Log.d(TAG, "Downloading...");
                }

                @Override
                public void onNext(ResponseBody responseBody) {
                    response = responseBody;
                }

                @Override
                public void onError(Throwable e) {
                    Log.d(TAG, "Error" + e.getMessage());
                }

                @Override
                public void onComplete() {
                    Log.d(TAG, "Download Completed!");
                    storeData(response);
                }
            });
        }
    }

    public void storeData(ResponseBody body) {

        InputStream inputStream = null;
        ArrayList<String> lines = new ArrayList<>();

        try {

            //Put the response body of the network call in a inputstream
            inputStream = body.byteStream();
            //Create a reader and pass the inputstream
            final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            //Iterate every line of the inputstream as long as the line is not null
            for (String line; (line = reader.readLine()) != null; ) {
                lines.add(line);
            }

            //Create observable to detect the when data has been loaded to database
            Observable.fromIterable(lines).subscribe(new Observer<String>() {
                @Override
                public void onSubscribe(Disposable d) {
                    Log.d(TAG,"Loading data to database...");
                }

                @Override
                public void onNext(String line) {
                    Entry entry = createEntry(line);
                    db.insertEntry(entry);
                }

                @Override
                public void onError(Throwable e) {
                    Log.d(TAG,"Error" + e.getMessage());
                }

                @Override
                public void onComplete() {
                    Log.d(TAG,"Database loaded with data!");

                    ArrayList<String> pages = db.getEntries();

                    Hashtable<String, Integer> unsortMap = getOccurances(pages);

                    Map<String, Integer> sortedMap = sortByValue(unsortMap);

                    ArrayList<Sequence> sortedList = convertToList(sortedMap);

                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    SequenceFragment sequenceFragment = SequenceFragment.newInstance(sortedList);
                    fragmentManager.beginTransaction().replace(R.id.root, sequenceFragment, SEQUENCE_FRAGMENT).commit();
                }
            });

        } catch (IOException e) {
            e.getMessage();
        }
    }

    public Entry createEntry(String readstring){
        String splitStr = readstring.split("\"-\"")[0];
        String ipaddress = splitStr.split("- -")[0];
        String timestamp = splitStr.split("[\\[\\]]")[1];
        String str = splitStr.split("GET ")[1];
        String webpage = str.split("HTTP")[0];
        return new Entry(timestamp,ipaddress,webpage);
    }


    public Hashtable<String, Integer> getOccurances(ArrayList<String> pages){
        Hashtable<String, Integer> unsortMap = new Hashtable<String, Integer>();
        for(String page:pages){
            int occurrences = Collections.frequency(pages, page);
            unsortMap.put(page,occurrences);
        }
        return unsortMap;
    }

    public Map<String, Integer> sortByValue(Map<String, Integer> unsortMap) {

        //Convert Map to List of Map
        List<Map.Entry<String, Integer>> list = new LinkedList<Map.Entry<String, Integer>>(unsortMap.entrySet());

        /*Sort list with Collections.sort(), provide a custom Comparator
            ACS: o2.getValue()).compareTo(o1.getValue()
            DES: o1.getValue()).compareTo(o2.getValue()*/
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            public int compare(Map.Entry<String, Integer> o1,
                               Map.Entry<String, Integer> o2) {
                return (o2.getValue()).compareTo(o1.getValue());
            }
        });

        //Loop the sorted list and put it into a new insertion order Map LinkedHashMap
        Map<String, Integer> sortedMap = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

    public ArrayList<Sequence> convertToList(Map<String, Integer> sortedMap){
        ArrayList<Sequence> sequences = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : sortedMap.entrySet()) {
            String sequence = entry.getKey();
            String occurence = entry.getValue().toString();
            sequences.add(new Sequence(sequence,occurence));
        }
        return sequences;
    }
}