package dev.custom.gboardpatches.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Theme Builder: edits the keyboard colours and corner radius applied by the patch runtime. */
public class KeyboardThemeStudioActivity extends Activity {

    private SharedPreferences prefs;

    private Switch enabledSwitch;
    private EditText accentField;
    private EditText backgroundField;
    private EditText textField;
    private SeekBar radiusSlider;
    private TextView radiusLabel;
    private TextView previewQ;
    private TextView previewW;
    private TextView previewE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(KeyboardThemeRuntime.PREFS, MODE_PRIVATE);
        setContentView(buildScreen());
        loadValues();
        renderPreview();
    }

    private View buildScreen() {
        int padding = dp(20);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Theme Builder");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24f);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Colours apply to Gboard keys while the theme is enabled.");
        subtitle.setTextColor(Color.parseColor("#B0B0B0"));
        subtitle.setTextSize(14f);
        subtitle.setPadding(0, dp(4), 0, dp(16));
        root.addView(subtitle);

        enabledSwitch = new Switch(this);
        enabledSwitch.setText("Enable custom key theme");
        enabledSwitch.setTextColor(Color.WHITE);
        enabledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                renderPreview();
            }
        });
        root.addView(enabledSwitch);

        accentField = addColorField(root, "Accent colour", "#4285F4");
        backgroundField = addColorField(root, "Key background", "#202124");
        textField = addColorField(root, "Key text", "#FFFFFF");

        radiusLabel = new TextView(this);
        radiusLabel.setTextColor(Color.WHITE);
        radiusLabel.setTextSize(14f);
        radiusLabel.setPadding(0, dp(12), 0, 0);
        root.addView(radiusLabel);

        radiusSlider = new SeekBar(this);
        radiusSlider.setMax(32);
        radiusSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                renderPreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        root.addView(radiusSlider);

        TextView previewLabel = new TextView(this);
        previewLabel.setText("Preview");
        previewLabel.setTextColor(Color.parseColor("#B0B0B0"));
        previewLabel.setTextSize(14f);
        previewLabel.setPadding(0, dp(16), 0, dp(8));
        root.addView(previewLabel);

        LinearLayout previewRow = new LinearLayout(this);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewQ = addPreviewKey(previewRow, "Q");
        previewW = addPreviewKey(previewRow, "W");
        previewE = addPreviewKey(previewRow, "E");
        root.addView(previewRow);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(24), 0, 0);

        Button save = new Button(this);
        save.setText("Save & apply");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveValues();
            }
        });
        actions.addView(save, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button reset = new Button(this);
        reset.setText("Reset");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetValues();
            }
        });
        actions.addView(reset, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(actions);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private EditText addColorField(LinearLayout parent, String label, String initial) {
        TextView caption = new TextView(this);
        caption.setText(label + " (#AARRGGBB)");
        caption.setTextColor(Color.parseColor("#B0B0B0"));
        caption.setTextSize(13f);
        caption.setPadding(0, dp(12), 0, dp(2));
        parent.addView(caption);

        EditText field = new EditText(this);
        field.setText(initial);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setTextColor(Color.WHITE);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderPreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        parent.addView(field);
        return field;
    }

    private TextView addPreviewKey(LinearLayout row, String label) {
        TextView key = new TextView(this);
        key.setText(label);
        key.setGravity(Gravity.CENTER);
        key.setTextSize(18f);
        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(0, dp(56), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        row.addView(key, params);
        return key;
    }

    private void loadValues() {
        enabledSwitch.setChecked(prefs.getBoolean(KeyboardThemeRuntime.KEY_ENABLED, false));
        accentField.setText(toHex(prefs.getInt(
            KeyboardThemeRuntime.KEY_ACCENT, KeyboardThemeRuntime.DEFAULT_ACCENT)));
        backgroundField.setText(toHex(prefs.getInt(
            KeyboardThemeRuntime.KEY_KEY_BG, KeyboardThemeRuntime.DEFAULT_KEY_BG)));
        textField.setText(toHex(prefs.getInt(
            KeyboardThemeRuntime.KEY_KEY_TEXT, KeyboardThemeRuntime.DEFAULT_KEY_TEXT)));
        radiusSlider.setProgress(prefs.getInt(
            KeyboardThemeRuntime.KEY_RADIUS, KeyboardThemeRuntime.DEFAULT_RADIUS));
    }

    private void saveValues() {
        Integer accent = parseColor(accentField);
        Integer background = parseColor(backgroundField);
        Integer text = parseColor(textField);
        if (accent == null || background == null || text == null) {
            Toast.makeText(this, "Use colours like #FF4285F4", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit()
            .putBoolean(KeyboardThemeRuntime.KEY_ENABLED, enabledSwitch.isChecked())
            .putInt(KeyboardThemeRuntime.KEY_ACCENT, accent)
            .putInt(KeyboardThemeRuntime.KEY_KEY_BG, background)
            .putInt(KeyboardThemeRuntime.KEY_KEY_TEXT, text)
            .putInt(KeyboardThemeRuntime.KEY_RADIUS, radiusSlider.getProgress())
            .apply();
        Toast.makeText(this, "Theme saved", Toast.LENGTH_SHORT).show();
        renderPreview();
        finish();
    }

    private void resetValues() {
        prefs.edit()
            .putBoolean(KeyboardThemeRuntime.KEY_ENABLED, false)
            .putInt(KeyboardThemeRuntime.KEY_ACCENT, KeyboardThemeRuntime.DEFAULT_ACCENT)
            .putInt(KeyboardThemeRuntime.KEY_KEY_BG, KeyboardThemeRuntime.DEFAULT_KEY_BG)
            .putInt(KeyboardThemeRuntime.KEY_KEY_TEXT, KeyboardThemeRuntime.DEFAULT_KEY_TEXT)
            .putInt(KeyboardThemeRuntime.KEY_RADIUS, KeyboardThemeRuntime.DEFAULT_RADIUS)
            .apply();
        loadValues();
        renderPreview();
    }

    private void renderPreview() {
        int accent = colorOrDefault(accentField, KeyboardThemeRuntime.DEFAULT_ACCENT);
        int background = colorOrDefault(backgroundField, KeyboardThemeRuntime.DEFAULT_KEY_BG);
        int text = colorOrDefault(textField, KeyboardThemeRuntime.DEFAULT_KEY_TEXT);
        int radius = radiusSlider == null ? KeyboardThemeRuntime.DEFAULT_RADIUS : radiusSlider.getProgress();

        boolean on = enabledSwitch != null && enabledSwitch.isChecked();
        int shownBackground = on ? background : Color.parseColor("#3C4043");
        int shownText = on ? text : Color.WHITE;

        if (radiusLabel != null) {
            radiusLabel.setText("Corner radius: " + radius + "dp");
        }
        styleKey(previewQ, shownBackground, shownText, accent, radius, on);
        styleKey(previewW, shownBackground, shownText, accent, radius, on);
        styleKey(previewE, shownBackground, shownText, accent, radius, on);
    }

    private void styleKey(TextView key, int background, int text, int accent, int radius, boolean on) {
        if (key == null) {
            return;
        }
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(radius));
        if (on) {
            shape.setStroke(dp(2), accent);
        }
        key.setBackgroundDrawable(shape);
        key.setTextColor(text);
    }

    private int colorOrDefault(EditText field, int fallback) {
        Integer parsed = parseColor(field);
        return parsed == null ? fallback : parsed;
    }

    private Integer parseColor(EditText field) {
        if (field == null) {
            return null;
        }
        String value = field.getText().toString().trim();
        if (!value.startsWith("#")) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.substring(1), 16);
            if (value.length() == 7) {
                return (int) (0xFF000000L | parsed);
            }
            if (value.length() == 9) {
                return (int) parsed;
            }
            return null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static String toHex(int color) {
        return String.format("#%08X", color);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
