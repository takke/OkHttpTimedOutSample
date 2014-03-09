package jp.takke.okhttptimedoutsample;

import java.lang.reflect.Field;

import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.internal.http.HttpClientWrapper;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.squareup.okhttp.ConnectionPool;
import com.squareup.okhttp.OkHttpClient;

public class MainActivity extends Activity {

    private Button runButton;
    private TextView resultText;
    private EditText countEdit;
    private Twitter twitter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        runButton = (Button) findViewById(R.id.runButton);
        runButton.setOnClickListener(new View.OnClickListener() {
            
            @Override
            public void onClick(View v) {
                
                new MyTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });
        
        resultText = (TextView) findViewById(R.id.resultText);
        countEdit = (EditText) findViewById(R.id.countEdit);
        
        ApplicationInfo ai = null;
        try {
            ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            
            twitter = TwitterFactory.getSingleton();
            twitter.setOAuthConsumer(ai.metaData.getString("consumerKey"), ai.metaData.getString("consumerSecret"));
            twitter.setOAuthAccessToken(new AccessToken(ai.metaData.getString("accessToken"), ai.metaData.getString("accessTokenSecret")));
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    class MyTask extends AsyncTask<Void, Void, String> {

        protected void onPreExecute() {
            
            runButton.setEnabled(false);
            resultText.setText("running...");
        }
        
        @Override
        protected String doInBackground(Void... params) {
            
            final StringBuilder sb = new StringBuilder();
            
            
            sb.append("java: " + System.getProperty("java.runtime.version") + " "
                    + System.getProperty("java.version") + " " + System.getProperty("java.vm.version") + "\n");
            
            sb.append("start\n");
            
            twitter4j.internal.http.alternative.HttpClientImpl.sPreferSpdy = true;
            twitter4j.internal.http.alternative.HttpClientImpl.sPreferHttp2 = true;
            
            final int count = Integer.parseInt(countEdit.getText().toString());
            
            final long startTick = System.currentTimeMillis();
            try {
                final Paging paging = new Paging();
                paging.setCount(count);
                
                final ResponseList<twitter4j.Status> result = twitter.getHomeTimeline(paging);

                final long elapsed = System.currentTimeMillis() - startTick;
                sb.append(" elapsed: " + elapsed + "ms\n");
                
                // dump
                sb.append(" " + result.size() + " results\n");
                int i=0;
                for (twitter4j.Status s : result) {
                    final String t = s.getText();
                    sb.append(" [" + s.getUser().getScreenName() + "][" + (t.length() <= 10 ? t : t.substring(0, 10)) + "]\n");
                    if (++i >= 10) {
                        sb.append(" ...\n");
                        break;
                    }
                }
                
                // SPDY,HTTP/2.0 info
                sb.append("\n");
                final twitter4j.internal.http.alternative.HttpClientImpl http = getHttpClientImpl(twitter);
                final ConnectionPool p = getSpdyConnectionPool(http);
                if (p == null) {
                    sb.append("HTTP/2.0 : Disabled\n");
                } else {
                    sb.append("HTTP/2.0 Connections: [" + p.getSpdyConnectionCount() + "/" + p.getConnectionCount() + "]\n");

                    sb.append("Protocol: [" + http.getLastRequestProtocol() + "]\n");
                }

            } catch (Exception e) {
                e.printStackTrace();
                sb.append("Failed: " + e.getMessage());
            }
            
            return sb.toString();
        }
        
        protected void onPostExecute(String result) {
            
            runButton.setEnabled(true);
            resultText.setText(result);
        }
        
    }
    
    
    //--------------------------------------------------
    // Utility Methods
    //--------------------------------------------------
    
    public static ConnectionPool getSpdyConnectionPool(final Twitter twitter) {
        
        try {
            final twitter4j.internal.http.alternative.HttpClientImpl http = getHttpClientImpl(twitter);
            return getSpdyConnectionPool(http);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static ConnectionPool getSpdyConnectionPool(final twitter4j.internal.http.alternative.HttpClientImpl http) {
        
        try {
            final Field f3 = http.getClass().getDeclaredField("client");
            f3.setAccessible(true);
            
            // client = http.client
            final OkHttpClient client = (OkHttpClient) f3.get(http);
            
            if (client != null) {
                final ConnectionPool p = client.getConnectionPool();
                return p;
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static twitter4j.internal.http.alternative.HttpClientImpl getHttpClientImpl(
            final Twitter twitter) throws ClassNotFoundException,
            NoSuchFieldException, IllegalAccessException {
        
        final Class<?> clazz = Class.forName("twitter4j.TwitterBaseImpl");
        final Field f1 = clazz.getDeclaredField("http");
        f1.setAccessible(true);
        
        // wrapper = twitter.http
        final HttpClientWrapper wrapper = (HttpClientWrapper) f1.get(twitter);
        final Field f2 = HttpClientWrapper.class.getDeclaredField("http");
        f2.setAccessible(true);
        
        // http = wrapper.http
        return (twitter4j.internal.http.alternative.HttpClientImpl) f2.get(wrapper);
    }
}
