package dev.custom.gboardpatches.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;

public final class KeyboardIconPackImportActivity extends Activity {
    private static final int REQUEST_PICK_ICON_PACK = 7402;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) return;

        Intent picker = new Intent("android.intent.action.OPEN_DOCUMENT");
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(
            "android.intent.extra.MIME_TYPES",
            new String[] { "application/zip", "application/x-zip-compressed", "application/octet-stream" }
        );
        try {
            startActivityForResult(picker, REQUEST_PICK_ICON_PACK);
        } catch (android.content.ActivityNotFoundException exception) {
            Toast.makeText(this, "No file picker is available.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_ICON_PACK) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            finish();
            return;
        }

        Uri uri = data.getData();
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("The selected file could not be opened.");
            String result = KeyboardIconPackRuntime.importPack(this, input);
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        } catch (IOException | SecurityException exception) {
            String message = exception.getMessage();
            Toast.makeText(
                this,
                message == null ? "The icon pack could not be imported." : message,
                Toast.LENGTH_LONG
            ).show();
        } finally {
            finish();
        }
    }
}
