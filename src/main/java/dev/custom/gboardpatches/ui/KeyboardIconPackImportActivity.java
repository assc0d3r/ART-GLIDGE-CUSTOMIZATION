package dev.custom.gboardpatches.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Picks a .wmicons.zip from storage and applies it to Gboard keys immediately. */
public class KeyboardIconPackImportActivity extends Activity {

    private TextView status;
    private TextView hint;
    private ListView packList;
    private final List<File> packs = new ArrayList<File>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildScreen());
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildScreen() {
        int padding = dp(20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Icon Pack Importer");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#B0B0B0"));
        status.setTextSize(14f);
        status.setPadding(0, dp(6), 0, dp(12));
        root.addView(status);

        hint = new TextView(this);
        hint.setTextColor(Color.parseColor("#F0A030"));
        hint.setTextSize(13f);
        hint.setPadding(0, 0, 0, dp(8));
        root.addView(hint);

        packList = new ListView(this);
        packList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < packs.size()) {
                    importPack(packs.get(position));
                }
            }
        });
        root.addView(packList, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(12), 0, 0);

        Button refreshButton = new Button(this);
        refreshButton.setText("Rescan");
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        actions.addView(refreshButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button clearButton = new Button(this);
        clearButton.setText("Remove pack");
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removePack();
            }
        });
        actions.addView(clearButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(actions);
        return root;
    }

    private void refresh() {
        packs.clear();
        packs.addAll(findPacks());
        Collections.sort(packs, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return right.lastModified() > left.lastModified() ? 1 : -1;
            }
        });

        List<String> labels = new ArrayList<String>();
        for (int i = 0; i < packs.size(); i++) {
            File file = packs.get(i);
            labels.add(file.getName() + "\n" + file.getParent() + "  ·  " + readableSize(file.length()));
        }

        packList.setAdapter(new ArrayAdapter<String>(
            this, android.R.layout.simple_list_item_1, labels));

        String active = KeyboardIconPackRuntime.activePackName();
        int count = KeyboardIconPackRuntime.iconCount();
        status.setText(active == null
            ? "No pack active (" + count + " icons loaded)"
            : "Active: " + active + " · " + count + " icons");
        hint.setText(packs.isEmpty()
            ? "No .wmicons.zip found. Copy one into " + KeyboardIconPackRuntime.packDirectory()
            : "Tap a pack to apply it.");
    }

    private List<File> findPacks() {
        List<File> found = new ArrayList<File>();
        File external = Environment.getExternalStorageDirectory();
        scan(external, found, 0);
        scan(KeyboardIconPackRuntime.packDirectory(), found, 0);
        return found;
    }

    private void scan(File directory, List<File> found, int depth) {
        if (directory == null || depth > 3 || found.size() > 200) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            String name = child.getName();
            if (name.startsWith(".")) {
                continue;
            }
            if (child.isFile()) {
                if (name.toLowerCase().endsWith(".zip") || name.endsWith(".wmicons")) {
                    found.add(child);
                }
            } else if (child.isDirectory() && depth < 3) {
                scan(child, found, depth + 1);
            }
        }
    }

    private void importPack(final File source) {
        status.setText("Importing " + source.getName() + "…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    File directory = KeyboardIconPackRuntime.packDirectory();
                    if (!directory.isDirectory() && !directory.mkdirs()) {
                        throw new java.io.IOException("Cannot create " + directory);
                    }
                    File target = new File(directory, KeyboardIconPackRuntime.ACTIVE_PACK_FILE);
                    copy(source, target);
                    KeyboardIconPackRuntime.reload();
                    int count = KeyboardIconPackRuntime.iconCount();
                    message = count == 0
                        ? "Copied, but no icons/*.png entries were found"
                        : "Applied " + count + " icons";
                } catch (Throwable failure) {
                    message = "Import failed: " + failure.getMessage();
                }
                final String result = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(
                            KeyboardIconPackImportActivity.this, result, Toast.LENGTH_LONG).show();
                        refresh();
                    }
                });
            }
        }).start();
    }

    private void removePack() {
        try {
            File target = new File(
                KeyboardIconPackRuntime.packDirectory(), KeyboardIconPackRuntime.ACTIVE_PACK_FILE);
            if (target.isFile() && !target.delete()) {
                Toast.makeText(this, "Could not delete active pack", Toast.LENGTH_SHORT).show();
                return;
            }
            KeyboardIconPackRuntime.reload();
            Toast.makeText(this, "Icon pack removed", Toast.LENGTH_SHORT).show();
        } catch (Throwable failure) {
            Toast.makeText(this, "Remove failed: " + failure.getMessage(), Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private static void copy(File source, File target) throws java.io.IOException {
        InputStream in = new FileInputStream(source);
        try {
            OutputStream out = new FileOutputStream(target);
            try {
                byte[] buffer = new byte[8192];
                int read = in.read(buffer);
                while (read > 0) {
                    out.write(buffer, 0, read);
                    read = in.read(buffer);
                }
                out.flush();
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private static String readableSize(long bytes) {
        if (bytes >= 1048576L) {
            return (bytes / 1048576L) + " MB";
        }
        if (bytes >= 1024L) {
            return (bytes / 1024L) + " KB";
        }
        return bytes + " B";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
