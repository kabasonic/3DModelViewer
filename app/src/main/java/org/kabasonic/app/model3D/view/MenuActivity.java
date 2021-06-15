package org.kabasonic.app.model3D.view;

import android.Manifest;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.kabasonic.android_3d_engine.services.collada.ColladaLoader;
import org.kabasonic.android_3d_engine.services.wavefront.WavefrontLoader;
import org.kabasonic.dddmodel2.R;
import org.kabasonic.util.android.AndroidUtils;
import org.kabasonic.util.android.ContentUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MenuActivity extends ListActivity {
    //example repo link
    private static final String REPO_URL = "https://raw.githubusercontent.com/the3deers/android-3D-model-viewer/master/models/index";
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1000;
    private static final int REQUEST_INTERNET_ACCESS = 1001;
    private static final int REQUEST_READ_CONTENT_PROVIDER = 1002;

    private static final int REQUEST_CODE_LOAD_MODEL = 1101;
    private static final int REQUEST_CODE_OPEN_MATERIAL = 1102;
    private static final int REQUEST_CODE_OPEN_TEXTURE = 1103;
    private static final int REQUEST_CODE_ADD_FILES = 1200;
    private static final String SUPPORTED_FILE_TYPES_REGEX = "(?i).*\\.(obj|stl|dae)";


    private enum Action {
        LOAD_MODEL, UNKNOWN
    }

    /**
     * Load file user data
     */
    private Map<String, Object> loadModelParameters = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);


        setListAdapter(new ArrayAdapter<>(this, R.layout.activity_menu_item,
                getResources().getStringArray(R.array.menu_items)));
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        String selectedItem = (String) getListView().getItemAtPosition(position);
        String selectedAction = selectedItem.replace(' ', '_').toUpperCase(Locale.getDefault());
        Action action = Action.UNKNOWN;
        try {
            action = Action.valueOf(selectedAction);
        } catch (IllegalArgumentException ex) {
            Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
        }
        try {
            switch (action) {
                case LOAD_MODEL:
                    loadModel();
                    break;
                case UNKNOWN:
                    Toast.makeText(getApplicationContext(), "Unrecognized action '" + selectedAction + "'",
                            Toast.LENGTH_LONG).show();
                    break;
            }
        } catch (Exception ex) {
            Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
        }

    }

    private void loadModel() {
        ContentUtils.showListDialog(this, "File Provider",
                new String[]{"Repository","Android SD"}, (DialogInterface dialog, int which) -> {
              if (which == 0) {
                loadModelFromRepository();
            }else {
                loadModelFromContentProvider();
            }
        });
    }

    private void loadModelFromRepository() {
        if (AndroidUtils.checkPermission(this, Manifest.permission.INTERNET, REQUEST_INTERNET_ACCESS)) {
            new LoadRepoIndexTask().execute();
        }
    }

    class LoadRepoIndexTask extends AsyncTask<Void, Integer, List<String>> {

        private final ProgressDialog dialog;

        public LoadRepoIndexTask() {
            this.dialog = new ProgressDialog(MenuActivity.this);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            this.dialog.setMessage("Loading...");
            this.dialog.setCancelable(false);
            this.dialog.show();
        }

        @Override
        protected List<String> doInBackground(Void... voids) {
            return ContentUtils.getIndex(REPO_URL);
        }

        @Override
        protected void onPostExecute(List<String> strings) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            if (strings == null) {
                Toast.makeText(MenuActivity.this, "Couldn't load repo index", Toast.LENGTH_LONG).show();
                return;
            }
            ContentUtils.createChooserDialog(MenuActivity.this, "Select file", null,
                    strings, SUPPORTED_FILE_TYPES_REGEX,
                    (String file) -> {
                        if (file != null) {
                            launchModelRendererActivity(Uri.parse(file));
                        }
                    });
        }
    }

    private void loadModelFromContentProvider() {
        // check permission starting from android API 23 - Marshmallow
        if (AndroidUtils.checkPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE, REQUEST_READ_CONTENT_PROVIDER)) {
            loadModelParameters.clear();
            ContentUtils.clearDocumentsProvided();
            ContentUtils.setCurrentDir(null);
            askForFile(REQUEST_CODE_LOAD_MODEL, "*/*");
        }
    }

    private void askForFile(int requestCode, String mimeType) {
        Intent target = ContentUtils.createGetContentIntent(mimeType);
        Intent intent = Intent.createChooser(target, "Select file");
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Error. Please install a file content provider", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        ContentUtils.setThreadActivity(this);
        try {
            switch (requestCode) {
                case REQUEST_READ_CONTENT_PROVIDER:
                    loadModelFromContentProvider();
                    break;
                case REQUEST_INTERNET_ACCESS:
                    loadModelFromRepository();
                    break;
                case REQUEST_CODE_LOAD_MODEL:
                    if (resultCode != RESULT_OK) {
                        return;
                    }
                    final Uri uri = data.getData();
                    if (uri == null) {
                        return;
                    }
                    onLoadModel(uri);
                    break;
                case REQUEST_CODE_OPEN_MATERIAL:
                    if (resultCode != RESULT_OK || data.getData() == null) {
                        launchModelRendererActivity(getUserSelectedModel());
                        break;
                    }
                    String filename = (String) loadModelParameters.get("file");
                    ContentUtils.addUri(filename, data.getData());
                    // check if material references texture file
                    String textureFile = WavefrontLoader.getTextureFile(data.getData());
                    if (textureFile == null) {
                        launchModelRendererActivity(getUserSelectedModel());
                        break;
                    }
                    ContentUtils.showDialog(this, "Select texture file", "This model references a " +
                                    "texture file (" + textureFile + "). Please select it", "OK",
                            "Cancel", (DialogInterface dialog, int which) -> {
                                switch (which) {
                                    case DialogInterface.BUTTON_NEGATIVE:
                                        launchModelRendererActivity(getUserSelectedModel());
                                        break;
                                    case DialogInterface.BUTTON_POSITIVE:
                                        loadModelParameters.put("file", textureFile);
                                        askForFile(REQUEST_CODE_OPEN_TEXTURE, "image/*");
                                }
                            });
                    break;
                case REQUEST_CODE_OPEN_TEXTURE:
                    if (resultCode != RESULT_OK || data.getData() == null) {
                        launchModelRendererActivity(getUserSelectedModel());
                        break;
                    }
                    String textureFilename = (String) loadModelParameters.get("file");
                    ContentUtils.addUri(textureFilename, data.getData());
                    launchModelRendererActivity(getUserSelectedModel());
                    break;
                case REQUEST_CODE_ADD_FILES:

                    // get list of files to prompt to user
                    List<String> files = (List<String>) loadModelParameters.get("files");
                    if (files == null || files.isEmpty()) {
                        launchModelRendererActivity(getUserSelectedModel());
                        break;
                    }

                    // save picked up file
                    final String current = files.remove(0);
                    ContentUtils.addUri(current, data.getData());

                    // no more files then load model...
                    if (files.isEmpty()) {
                        launchModelRendererActivity(getUserSelectedModel());
                        break;
                    }
                    ;

                    final String next = files.get(0);
                    ContentUtils.showDialog(this, "Select file", "Please select file " + next, "OK",
                            "Cancel", (DialogInterface dialog, int which) -> {
                                switch (which) {
                                    case DialogInterface.BUTTON_NEGATIVE:
                                        launchModelRendererActivity(getUserSelectedModel());
                                        break;
                                    case DialogInterface.BUTTON_POSITIVE:
                                        askForFile(REQUEST_CODE_ADD_FILES, "image/*");
                                }
                            });

                    break;
            }
        } catch (Exception ex) {
            Log.e("MenuActivity", ex.getMessage(), ex);
            Toast.makeText(this, "Unexpected exception: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void onLoadModel(Uri uri) throws IOException {

        // save user selected model
        loadModelParameters.put("model", uri);

        // detect model type
        if (uri.toString().toLowerCase().endsWith(".obj")) {
            askForRelatedFiles(0);
        } else if (uri.toString().toLowerCase().endsWith(".stl")) {
            askForRelatedFiles(1);
        } else if (uri.toString().toLowerCase().endsWith(".dae")) {
            askForRelatedFiles(2);
        } else {
            // no model type from filename, ask user...
            ContentUtils.showListDialog(this, "Select type", new String[]{"Wavefront (*.obj)", "Stereolithography (*" +
                    ".stl)", "Collada (*.dae)"}, (dialog, which) -> {
                try {
                    askForRelatedFiles(which);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private Uri getUserSelectedModel() {
        return (Uri) loadModelParameters.get("model");
    }

    private void askForRelatedFiles(int modelType) throws IOException {
        loadModelParameters.put("type", modelType);
        switch (modelType) {
            case 0: // obj
                // check if model references material file
                String materialFile = WavefrontLoader.getMaterialLib(getUserSelectedModel());
                if (materialFile == null) {
                    launchModelRendererActivity(getUserSelectedModel());
                    break;
                }
                ContentUtils.showDialog(this, "Select material file", "This model references a " +
                                "material file (" + materialFile + "). Please select it", "OK",
                        "Cancel", (DialogInterface dialog, int which) -> {
                            switch (which) {
                                case DialogInterface.BUTTON_NEGATIVE:
                                    launchModelRendererActivity(getUserSelectedModel());
                                    break;
                                case DialogInterface.BUTTON_POSITIVE:
                                    loadModelParameters.put("file", materialFile);
                                    askForFile(REQUEST_CODE_OPEN_MATERIAL, "*/*");
                            }
                        });
                break;
            case 1: // stl
                launchModelRendererActivity(getUserSelectedModel());
                break;
            case 2: // dae
                final List<String> images = ColladaLoader.getImages(ContentUtils.getInputStream(getUserSelectedModel()));
                if (images == null || images.isEmpty()) {
                    launchModelRendererActivity(getUserSelectedModel());
                } else {

                    Log.i("MenuActivity", "Prompting user to choose files from picker...");

                    loadModelParameters.put("files", images);
                    String file = images.get(0);

                    ContentUtils.showDialog(this, "Select texture", "This model references a " +
                                    " file (" + file + "). Please select it", "OK",
                            "Cancel", (DialogInterface dialog, int which) -> {
                                switch (which) {
                                    case DialogInterface.BUTTON_NEGATIVE:
                                        launchModelRendererActivity(getUserSelectedModel());
                                        break;
                                    case DialogInterface.BUTTON_POSITIVE:
                                        askForFile(REQUEST_CODE_ADD_FILES, "*/*");
                                }
                            });
                }
                break;
        }
    }

    private void launchModelRendererActivity(Uri uri) {
        Log.i("Menu", "Launching renderer for '" + uri + "'");
        Intent intent = new Intent(getApplicationContext(), ModelActivity.class);
        try {
            URI.create(uri.toString());
            intent.putExtra("uri", uri.toString());
        } catch (Exception e) {
            // info: filesystem url may contain spaces, therefore we re-encode URI
            try {
                intent.putExtra("uri", new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString());
            } catch (URISyntaxException ex) {
                Toast.makeText(this, "Error: " + uri.toString(), Toast.LENGTH_LONG).show();
                return;
            }
        }
        intent.putExtra("immersiveMode", "false");

        // content provider case
        if (!loadModelParameters.isEmpty()) {
            intent.putExtra("type", loadModelParameters.get("type").toString());
            loadModelParameters.clear();
        }

        startActivity(intent);
    }
}
