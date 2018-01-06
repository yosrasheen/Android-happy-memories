package csce6231.happymemories;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.affectiva.android.affdex.sdk.Frame;
import com.affectiva.android.affdex.sdk.detector.CameraDetector;
import com.affectiva.android.affdex.sdk.detector.Detector;
import com.affectiva.android.affdex.sdk.detector.Face;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.Serializable;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Happy Memories";
    private static final List<String> REACTIONS = Collections.unmodifiableList(Arrays.asList("LIKE", "LOVE", "HAHA"));

    private static final float SADNESS_THRESHOLD = 0.5f;
    private static final long SADNESS_DURATION = 5;
    private static final long MIN_TIME_BEFORE_NEXT_CHECK = 30;

    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 100;

    private CallbackManager callbackManager;

    private CameraDetector cameraDetector;
    private SurfaceView cameraPreview;

    private TextView faceStatus;

    private long sadnessStartTime = 0;
    private long lastCheckTime = 0;

    private List<String> facebookPhotos = new ArrayList<>();

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Print application key hash
        try {
            PackageInfo info = getPackageManager().getPackageInfo("csce6231.happymemories", PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                Log.i(TAG, "KeyHash: " + Base64.encodeToString(md.digest(), Base64.DEFAULT));
            }

        } catch (Exception e) {}

        // Initialize callback manager
        callbackManager = CallbackManager.Factory.create();

        // Initialize Affectiva camera detector
        cameraPreview = (SurfaceView) findViewById(R.id.camera_preview);

        cameraDetector = new CameraDetector(this, CameraDetector.CameraType.CAMERA_FRONT, cameraPreview);
        cameraDetector.setDetectSadness(true);
        cameraDetector.setDetectSmile(true);
        cameraDetector.setMaxProcessRate(1);
        cameraDetector.setImageListener(new Detector.ImageListener() {
                @Override
                public void onImageResults(List<Face> list, Frame frame, float v) { handleFaces(list);
                }
            });

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startDetector();

        } else {
            Log.i(TAG, "No camera permission!!!");
            ActivityCompat.requestPermissions(MainActivity.this, new String[] { Manifest.permission.CAMERA }, MY_PERMISSIONS_REQUEST_CAMERA);
        }

        faceStatus = (TextView) findViewById(R.id.face_status);

        // Initialize Facebook login
        LoginButton loginButton = (LoginButton) this.findViewById(R.id.login_button);
        loginButton.setReadPermissions("user_friends", "user_likes", "user_location", "user_photos", "user_posts", "user_status", "user_tagged_places", "user_videos");
        loginButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
                @Override
                public void onSuccess(LoginResult loginResult) {
                    loadFacebookPosts(loginResult.getAccessToken());
                }

                @Override
                public void onCancel() {}

                @Override
                public void onError(FacebookException exception) {}
            });

        if (AccessToken.getCurrentAccessToken() != null) { loadFacebookPosts(AccessToken.getCurrentAccessToken()); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraDetector != null && cameraDetector.isRunning()) {
            cameraDetector.stop();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
        case MY_PERMISSIONS_REQUEST_CAMERA:
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Camera permission granted!!!");
                startDetector();

            } else {
                Log.i(TAG, "Camera permission denied!!!");
            }

            break;
        }
    }

    private void startDetector() {
        if (cameraDetector != null && !cameraDetector.isRunning()) {
            cameraDetector.start();
        }
    }

    private void handleFaces(List<Face> faces) {
        if (faces.isEmpty()) {
            faceStatus.setText("No Face");
            sadnessStartTime = 0;

        } else {
            if (faces.get(0).emotions.getSadness() > SADNESS_THRESHOLD) {
                faceStatus.setText("Sad");

                long currentTime = System.currentTimeMillis();

                // Mark sadness start time
                if (sadnessStartTime == 0) {
                    sadnessStartTime = currentTime;

                } else {
                    // If sadness duration exceeds threshold
                    if (currentTime - sadnessStartTime >= (SADNESS_DURATION * 1000)) {
                        // If there has been enough time since last check
                        if (currentTime - lastCheckTime >= MIN_TIME_BEFORE_NEXT_CHECK * 1000) {
                            if (isFacebookReady()) {
                                lastCheckTime = currentTime;

                                Toast.makeText(this, "You seem to be sad. Do you remember this?", Toast.LENGTH_LONG).show();
                                showRandomImage();
                            }
                        }
                    }
                }

            } else {
                faceStatus.setText("Not Sad");
                sadnessStartTime = 0;
            }
        }
    }

    private boolean isFacebookReady() {
        return (facebookPhotos != null && !facebookPhotos.isEmpty());
    }

    private void loadFacebookPosts(final AccessToken accessToken) {
        Log.i(TAG, "Facebook login successful");

        GraphRequest request = GraphRequest.newMeRequest(accessToken, new GraphRequest.GraphJSONObjectCallback() {
            @Override
            public void onCompleted(JSONObject object, GraphResponse response) {
                populateFacebookPosts(accessToken, object);
            }
        });

        Bundle parameters = new Bundle();
        parameters.putString("fields", "id,name,feed.type(photo).limit(1000){id,link,object_id,reactions.limit(1000){id,name,type}}");
        request.setParameters(parameters);

        request.executeAsync();
    }

    private void populateFacebookPosts(AccessToken accessToken, JSONObject object) {
        try {
            Log.i(TAG, "Loading Facebook images");
            facebookPhotos = new ArrayList<>();

            String userName = object.getString("name");

            JSONObject posts = object.optJSONObject("feed");
            if (posts != null) {
                JSONArray postsData = posts.optJSONArray("data");
                if (postsData != null) {
                    for (int postIndex = 0; postIndex < postsData.length(); postIndex++) {
                        JSONObject post = postsData.getJSONObject(postIndex);

                        String objectId = post.optString("object_id");
                        if (objectId != null && !objectId.isEmpty()) {
                            JSONObject reactions = post.optJSONObject("reactions");
                            if (reactions != null) {
                                JSONArray reactionsData = reactions.getJSONArray("data");
                                for (int reactionIndex = 0; reactionIndex < reactionsData.length(); reactionIndex++) {
                                    JSONObject reaction = reactionsData.getJSONObject(reactionIndex);
                                    if (reaction.getString("name").equals(userName) && REACTIONS.contains(reaction.getString("type"))) {
                                        facebookPhotos.add(objectId);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void showRandomImage() {
        Log.i(TAG, "Showing random image");
        Log.i(TAG, "Facebook images loaded " + facebookPhotos.size());

        try {
            final String objectId;

            synchronized (facebookPhotos) {
                if (facebookPhotos == null || facebookPhotos.isEmpty()) {
                    return;
                }

                int randomIndex = (int) Math.floor(Math.random() * facebookPhotos.size());
                objectId = facebookPhotos.get(randomIndex);
            }

            Log.i(TAG, "object_id: " + objectId);

            Bundle photoParameters = new Bundle();
            photoParameters.putString("fields", "images{source}");

            GraphRequest photoRequest = new GraphRequest(AccessToken.getCurrentAccessToken(), objectId, photoParameters, null, new GraphRequest.Callback() {
                @Override
                public void onCompleted(final GraphResponse photoResponse) {
                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                String imageUrl = photoResponse.getJSONObject().getJSONArray("images").getJSONObject(0).getString("source");
                                final Bitmap image = BitmapFactory.decodeStream(new URL(imageUrl).openConnection().getInputStream());
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ImageView iv = (ImageView) findViewById(R.id.photo_viewer);
                                        iv.setImageBitmap(image);
                                    }
                                });

                            } catch (Exception e) {
                                Log.e(TAG, e + "");
                            }
                        }
                    }.start();
                }
            });

            photoRequest.executeAsync();

        } catch (Exception e) {
            Log.e(TAG, e + "");
        }
    }
}
